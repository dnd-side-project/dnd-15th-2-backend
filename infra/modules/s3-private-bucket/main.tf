data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "this" {
  bucket = var.bucket_name

  tags = var.tags
}

# 이 모듈은 이름 그대로 "private" 버킷 전용이다. 공개 버킷이 필요하면 별도
# 모듈로 분리한다(변수로 이 4개 항목을 끌 수 있게 만들지 않는다 — 설정
# 실수로 인한 공개 노출을 원천 차단하기 위함).
resource "aws_s3_bucket_public_access_block" "this" {
  bucket = aws_s3_bucket.this.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "this" {
  count  = var.versioning_enabled ? 1 : 0
  bucket = aws_s3_bucket.this.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = var.sse_algorithm
      kms_master_key_id = var.sse_algorithm == "aws:kms" ? var.kms_key_arn : null
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    id     = "expiration"
    status = "Enabled"

    # S3 API는 lifecycle rule마다 Filter 또는 Prefix를 요구한다. 버킷 전체를
    # 대상으로 하려면 빈 filter를 명시해야 apply 시 MalformedXML을 피한다.
    filter {}

    # 중단된 멀티파트 업로드 조각은 객체 목록에 나타나지 않지만 저장 비용은
    # 계속 발생한다. 만료 규칙과 무관하게 항상 정리한다.
    abort_incomplete_multipart_upload {
      days_after_initiation = var.abort_incomplete_multipart_upload_days
    }

    dynamic "expiration" {
      for_each = var.lifecycle_expiration_days != null ? [var.lifecycle_expiration_days] : []
      content {
        days = expiration.value
      }
    }

    dynamic "noncurrent_version_expiration" {
      for_each = var.noncurrent_version_expiration_days != null ? [var.noncurrent_version_expiration_days] : []
      content {
        noncurrent_days = noncurrent_version_expiration.value
      }
    }
  }
}

resource "aws_s3_bucket_logging" "this" {
  count  = var.logging_target_bucket != null ? 1 : 0
  bucket = aws_s3_bucket.this.id

  target_bucket = var.logging_target_bucket
  target_prefix = var.logging_target_prefix
}

# --- 버킷 정책: TLS 강제 + (로그 대상인 경우) S3 로그 배달 서비스 허용 -----

data "aws_iam_policy_document" "deny_insecure_transport" {
  count = var.enforce_tls ? 1 : 0

  statement {
    sid    = "DenyInsecureTransport"
    effect = "Deny"

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.this.arn,
      "${aws_s3_bucket.this.arn}/*",
    ]

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

data "aws_iam_policy_document" "allow_log_delivery" {
  count = var.is_log_target ? 1 : 0

  statement {
    sid    = "AllowS3LogDelivery"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["logging.s3.amazonaws.com"]
    }

    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.this.arn}/*"]

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }

    dynamic "condition" {
      for_each = length(var.log_source_bucket_arns) > 0 ? [1] : []
      content {
        test     = "ArnLike"
        variable = "aws:SourceArn"
        values   = var.log_source_bucket_arns
      }
    }
  }
}

data "aws_iam_policy_document" "combined" {
  count = var.enforce_tls || var.is_log_target ? 1 : 0

  source_policy_documents = compact([
    var.enforce_tls ? data.aws_iam_policy_document.deny_insecure_transport[0].json : "",
    var.is_log_target ? data.aws_iam_policy_document.allow_log_delivery[0].json : "",
  ])
}

resource "aws_s3_bucket_policy" "this" {
  count  = var.enforce_tls || var.is_log_target ? 1 : 0
  bucket = aws_s3_bucket.this.id
  policy = data.aws_iam_policy_document.combined[0].json
}
