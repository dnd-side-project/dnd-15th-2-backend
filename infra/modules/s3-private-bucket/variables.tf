variable "bucket_name" {
  description = "생성할 S3 버킷 이름. 전역적으로 고유해야 하므로 기본값을 두지 않는다."
  type        = string

  validation {
    condition     = length(var.bucket_name) > 0
    error_message = "bucket_name은 비어 있을 수 없다."
  }
}

variable "versioning_enabled" {
  description = "버킷 Versioning 활성화 여부. 실수로 인한 덮어쓰기/삭제를 완화한다."
  type        = bool
  default     = true
}

variable "sse_algorithm" {
  description = "기본 서버 측 암호화 알고리즘. \"AES256\"(SSE-S3) 또는 \"aws:kms\"(SSE-KMS)."
  type        = string
  default     = "AES256"

  validation {
    condition     = contains(["AES256", "aws:kms"], var.sse_algorithm)
    error_message = "sse_algorithm은 AES256 또는 aws:kms만 허용한다."
  }
}

variable "kms_key_arn" {
  description = "sse_algorithm이 \"aws:kms\"일 때 사용할 Customer-managed KMS Key ARN. AES256에서는 사용하지 않는다."
  type        = string
  default     = null

  validation {
    condition     = var.sse_algorithm != "aws:kms" || var.kms_key_arn != null
    error_message = "sse_algorithm이 aws:kms이면 kms_key_arn을 지정해야 한다."
  }
}

variable "lifecycle_expiration_days" {
  description = "현재 버전 객체를 자동 만료(삭제)할 기간(일). null이면 만료 규칙을 만들지 않는다."
  type        = number
  default     = null
}

variable "noncurrent_version_expiration_days" {
  description = "이전(non-current) 버전 객체를 자동 만료할 기간(일). null이면 규칙을 만들지 않는다."
  type        = number
  default     = null
}

variable "logging_target_bucket" {
  description = "S3 서버 접근 로그를 전달할 대상 버킷 이름. null이면 접근 로그를 비활성화한다."
  type        = string
  default     = null
}

variable "logging_target_prefix" {
  description = "로그 객체 Key 접두사."
  type        = string
  default     = "s3-access-logs/"
}

variable "is_log_target" {
  description = "이 버킷이 다른 버킷의 서버 접근 로그를 받는 대상인지 여부. true면 S3 로그 배달 서비스 principal에게 최소 권한 PutObject를 허용하는 버킷 정책 statement를 추가한다."
  type        = bool
  default     = false
}

variable "log_source_bucket_arns" {
  description = "is_log_target이 true일 때, 이 버킷으로 로그를 보낼 수 있는 원본 버킷 ARN 목록(aws:SourceArn 조건으로 범위를 좁힌다)."
  type        = list(string)
  default     = []
}

variable "enforce_tls" {
  description = "TLS가 아닌 요청을 버킷 정책으로 거부할지 여부."
  type        = bool
  default     = true
}

variable "tags" {
  description = "버킷에 적용할 태그."
  type        = map(string)
  default     = {}
}
