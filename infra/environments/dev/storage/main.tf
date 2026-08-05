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
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:ListBucket",
    ]
    resources = [
      module.post_image_bucket.bucket_arn,
      "${module.post_image_bucket.bucket_arn}/*",
    ]
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
