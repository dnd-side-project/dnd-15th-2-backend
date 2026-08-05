locals {
  # `infra/bootstrap`이 만든 역할과 동일한 명명 규칙(`${project_prefix}-*`)으로
  # ARN을 재구성한다. 두 스택이 State를 공유하지 않아도 되도록 원격 State
  # 참조 대신 결정적인 이름 규칙을 사용한다.
  infra_apply_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${var.project_prefix}-infra-apply"
  account_root_arn     = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
}

# --- KMS: 게시물 이미지 전용 Customer-managed Key --------------------------
# 사람 결정(Infrastructure Design Report D-1, 2026-08-05)에 따라 SSE-S3 대신
# SSE-KMS를 사용해 감사 가능한 Key 사용 이력을 남긴다.

data "aws_iam_policy_document" "post_image_kms_key" {
  # 이 문서는 IAM identity 정책이 아니라 KMS Key 정책이다. Key 정책의
  # Resource는 정책이 부착된 Key 자신만을 가리키므로 "*" 외의 값을 쓸 수
  # 없다(AWS KMS 제약). 따라서 identity 정책을 전제로 Resource 범위를 검사하는
  # 아래 세 규칙은 이 문서에 적용할 수 없다. Principal은 계정 root, infra-apply,
  # dev-s3-tester로만 한정되어 실제 사용 범위가 좁혀져 있다.
  # checkov:skip=CKV_AWS_109:KMS Key 정책의 Resource는 Key 자신으로 고정되어 범위 축소가 불가능하다.
  # checkov:skip=CKV_AWS_111:동일 사유. 쓰기 권한은 Principal로 제한한다.
  # checkov:skip=CKV_AWS_356:동일 사유. Key 정책에서 "*"는 해당 Key만을 의미한다.

  # AWS 권장 사항: 계정 root에 전체 권한을 남겨 두지 않으면 IAM 정책 오류로
  # Key 관리가 영구히 불가능해질 수 있다(AWS KMS Key 정책 모범 사례).
  statement {
    sid    = "EnableAccountRootFullAccess"
    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = [local.account_root_arn]
    }

    actions   = ["kms:*"]
    resources = ["*"]
  }

  statement {
    sid    = "AllowKeyAdministrationByInfraApply"
    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = [local.infra_apply_role_arn]
    }

    actions = [
      "kms:Create*",
      "kms:Describe*",
      "kms:Enable*",
      "kms:List*",
      "kms:Put*",
      "kms:Update*",
      "kms:Revoke*",
      "kms:Disable*",
      "kms:Get*",
      "kms:Delete*",
      "kms:TagResource",
      "kms:UntagResource",
      "kms:ScheduleKeyDeletion",
      "kms:CancelKeyDeletion",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "AllowKeyUsageForS3"
    effect = "Allow"

    principals {
      type = "AWS"
      identifiers = [
        local.infra_apply_role_arn,
        aws_iam_role.dev_s3_tester.arn,
      ]
    }

    actions = [
      "kms:Decrypt",
      "kms:GenerateDataKey*",
      "kms:DescribeKey",
    ]
    resources = ["*"]
  }
}

resource "aws_kms_key" "post_images" {
  description             = "게시물 이미지 테스트 S3 버킷(${var.post_image_bucket_name}) 전용 암호화 Key"
  deletion_window_in_days = var.kms_key_deletion_window_days
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.post_image_kms_key.json

  tags = var.tags
}

resource "aws_kms_alias" "post_images" {
  name          = "alias/${var.project_prefix}-post-images"
  target_key_id = aws_kms_key.post_images.key_id
}

# --- S3: 접근 로그 대상 버킷 ------------------------------------------------
# 계정에 기존 CloudTrail Trail이 없어(첫 인프라) 전체 Trail 신설 대신 S3
# 서버 접근 로그만 수집한다(Infrastructure Design Report D-1, 사람 결정
# 2026-08-05: "S3 서버 접근 로그 이벤트만 수집").

module "access_log_bucket" {
  source = "../../../modules/s3-private-bucket"

  bucket_name                        = var.access_log_bucket_name
  versioning_enabled                 = true
  sse_algorithm                      = "AES256"
  lifecycle_expiration_days          = var.access_log_lifecycle_days
  noncurrent_version_expiration_days = var.access_log_lifecycle_days
  is_log_target                      = true
  log_source_bucket_arns             = ["arn:aws:s3:::${var.post_image_bucket_name}"]
  enforce_tls                        = true
  tags                               = var.tags
}

# --- S3: 게시물 이미지 테스트 버킷 -----------------------------------------

module "post_image_bucket" {
  source = "../../../modules/s3-private-bucket"

  bucket_name                        = var.post_image_bucket_name
  versioning_enabled                 = true
  sse_algorithm                      = "aws:kms"
  kms_key_arn                        = aws_kms_key.post_images.arn
  lifecycle_expiration_days          = var.post_image_lifecycle_days
  noncurrent_version_expiration_days = var.post_image_lifecycle_days
  logging_target_bucket              = module.access_log_bucket.bucket_id
  logging_target_prefix              = "post-images/"
  enforce_tls                        = true
  tags                               = var.tags

  # 이 모듈은 로그 대상 버킷의 이름만 참조하므로, 대상 버킷의 정책까지
  # 기다리는 의존성이 그래프에 생기지 않는다. S3는 PutBucketLogging 시점에
  # 대상 버킷의 쓰기 권한을 검증하므로 정책보다 먼저 실행되면
  # InvalidTargetBucketForLogging으로 실패한다.
  depends_on = [module.access_log_bucket]
}

# --- IAM: 팀원 수동 테스트용 Role(dev-s3-tester) ---------------------------
# 사람 결정(2026-08-05)에 따라 고정 Access Key IAM User 대신, 이 버킷과 Key로만
# 범위를 좁힌 Role을 만든다. 계정 내 관리자급 세션만 MFA를 갖춘 상태로
# assume할 수 있다(AGENTS.md 4.9: 장기 Access Key 미발급).

data "aws_iam_policy_document" "dev_s3_tester_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "AWS"
      identifiers = [local.account_root_arn]
    }

    condition {
      test     = "Bool"
      variable = "aws:MultiFactorAuthPresent"
      values   = ["true"]
    }
  }
}

resource "aws_iam_role" "dev_s3_tester" {
  name                 = "${var.project_prefix}-s3-tester"
  assume_role_policy   = data.aws_iam_policy_document.dev_s3_tester_trust.json
  max_session_duration = 3600

  tags = var.tags
}

data "aws_iam_policy_document" "dev_s3_tester_permissions" {
  statement {
    sid    = "PostImageBucketReadWrite"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:GetObjectVersion",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:DeleteObjectVersion",
      "s3:ListBucket",
      "s3:ListBucketVersions",
      "s3:AbortMultipartUpload",
      "s3:ListMultipartUploadParts",
    ]
    resources = [
      module.post_image_bucket.bucket_arn,
      "${module.post_image_bucket.bucket_arn}/*",
    ]
  }

  # 버킷 "관리"에 해당하는 부분은 설정 조회까지만 허용한다. 암호화, Public
  # Access Block, 수명주기 같은 보안 설정을 사람이 콘솔에서 바꾸면 Terraform
  # 상태와 어긋나므로 변경 권한은 주지 않는다.
  statement {
    sid    = "PostImageBucketReadConfiguration"
    effect = "Allow"
    actions = [
      "s3:GetBucketLocation",
      "s3:GetBucketVersioning",
      "s3:GetBucketPublicAccessBlock",
      "s3:GetEncryptionConfiguration",
      "s3:GetLifecycleConfiguration",
    ]
    resources = [module.post_image_bucket.bucket_arn]
  }

  # 콘솔에서 S3 화면을 열면 계정의 버킷 목록을 먼저 조회한다. 목록 조회는
  # 리소스를 좁힐 수 없는 계정 단위 action이며 버킷 내용은 노출하지 않는다.
  statement {
    sid       = "ListAccountBuckets"
    effect    = "Allow"
    actions   = ["s3:ListAllMyBuckets"]
    resources = ["*"]
  }

  statement {
    sid    = "PostImageKmsUsage"
    effect = "Allow"
    actions = [
      "kms:Decrypt",
      "kms:GenerateDataKey*",
      "kms:DescribeKey",
    ]
    resources = [aws_kms_key.post_images.arn]
  }
}

resource "aws_iam_role_policy" "dev_s3_tester" {
  name   = "${var.project_prefix}-s3-tester-permissions"
  role   = aws_iam_role.dev_s3_tester.id
  policy = data.aws_iam_policy_document.dev_s3_tester_permissions.json
}
