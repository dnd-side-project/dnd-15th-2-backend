# Terraform State Backend
#
# 이 저장소의 첫 AWS 리소스다. 이후 모든 Terraform 스택(이 스택 자신을 제외한)이
# 이 버킷을 원격 Backend로 사용하며, Terraform 1.10+의 네이티브 S3 Lockfile
# locking(`use_lockfile`)을 사용해 별도 DynamoDB Lock 테이블 없이 잠금을
# 구현한다(AGENTS.md 4.6).

resource "aws_s3_bucket" "terraform_state" {
  bucket = var.state_bucket_name

  tags = merge(var.tags, {
    Purpose = "terraform-state"
  })
}

resource "aws_s3_bucket_versioning" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# State 파일에는 리소스 속성이 평문으로 남을 수 있어(AGENTS.md 4.6, State를
# 민감정보로 취급) 오래된 버전을 무기한 보관하지 않는다.
resource "aws_s3_bucket_lifecycle_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    id     = "expire-noncurrent-state-versions"
    status = "Enabled"

    # S3 API는 lifecycle rule마다 Filter 또는 Prefix를 요구한다. 버킷 전체를
    # 대상으로 하려면 빈 filter를 명시해야 apply 시 MalformedXML을 피한다.
    filter {}

    # 중단된 State 업로드 조각이 저장 비용으로 누적되지 않도록 정리한다.
    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}

# AGENTS.md 4.6은 State Backend에 감사 가능한 접근 로그를 요구한다. State
# 버킷 자신을 로그 대상으로 삼으면 로그가 State와 같은 삭제 권한 경계에
# 놓이므로 전용 버킷으로 분리한다.
module "state_access_log_bucket" {
  source = "../modules/s3-private-bucket"

  bucket_name = var.state_access_log_bucket_name

  # AWS 제약: S3 서버 접근 로그 배달은 Customer-managed KMS Key로 암호화된
  # 대상 버킷을 지원하지 않는다. 로그 대상 버킷은 SSE-S3를 사용한다.
  sse_algorithm = "AES256"

  versioning_enabled                 = true
  lifecycle_expiration_days          = var.state_access_log_retention_days
  noncurrent_version_expiration_days = var.state_access_log_retention_days
  is_log_target                      = true
  log_source_bucket_arns             = ["arn:aws:s3:::${var.state_bucket_name}"]
  enforce_tls                        = true

  tags = merge(var.tags, {
    Purpose = "terraform-state-access-logs"
  })
}

resource "aws_s3_bucket_logging" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  target_bucket = module.state_access_log_bucket.bucket_id
  target_prefix = "terraform-state/"

  # 대상 버킷의 이름만 참조하면 정책 생성까지 기다리는 의존성이 그래프에
  # 생기지 않는다. S3는 PutBucketLogging 시점에 대상 버킷의 쓰기 권한을
  # 검증하므로 정책보다 먼저 실행되면 InvalidTargetBucketForLogging으로
  # 실패한다.
  depends_on = [module.state_access_log_bucket]
}

data "aws_iam_policy_document" "terraform_state_tls_only" {
  statement {
    sid    = "DenyInsecureTransport"
    effect = "Deny"

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.terraform_state.arn,
      "${aws_s3_bucket.terraform_state.arn}/*",
    ]

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  policy = data.aws_iam_policy_document.terraform_state_tls_only.json
}
