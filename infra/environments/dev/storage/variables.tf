variable "aws_region" {
  description = "리소스를 생성할 AWS Region. Infrastructure Design Report D-1(GitHub Issue #63)에서 ap-northeast-2로 확정되었다."
  type        = string
  default     = "ap-northeast-2"
}

variable "project_prefix" {
  description = "infra/bootstrap의 infra-apply 역할이 관리 권한 범위로 사용하는 것과 동일한 접두사여야 한다."
  type        = string
  default     = "qello-dev"
}

variable "post_image_bucket_name" {
  description = "게시물 이미지 업로드 테스트에 사용할 S3 버킷 이름. 전역 고유값이 필요해 기본값을 두지 않는다."
  type        = string

  validation {
    condition     = can(regex("^${var.project_prefix}-", var.post_image_bucket_name))
    error_message = "post_image_bucket_name은 \"${var.project_prefix}-\"로 시작해야 infra-apply 역할의 IAM 권한 범위(접두사 기준)에 포함된다."
  }
}

variable "access_log_bucket_name" {
  description = "post_image_bucket의 S3 서버 접근 로그를 저장할 버킷 이름. 전역 고유값이 필요해 기본값을 두지 않는다."
  type        = string

  validation {
    condition     = can(regex("^${var.project_prefix}-", var.access_log_bucket_name))
    error_message = "access_log_bucket_name은 \"${var.project_prefix}-\"로 시작해야 한다."
  }
}

variable "s3_tester_user_name" {
  description = "팀원이 콘솔 로그인에 사용할 IAM User 이름. 이 User에는 Access Key를 발급하지 않으며 작업 권한은 assume한 Role에서만 나온다."
  type        = string
  default     = "qello-dev-s3-tester"

  validation {
    condition     = can(regex("^${var.project_prefix}-", var.s3_tester_user_name))
    error_message = "s3_tester_user_name은 \"${var.project_prefix}-\"로 시작해야 배포 역할의 IAM 권한 범위에 포함된다."
  }
}

variable "post_image_lifecycle_days" {
  description = "post_image_bucket 객체 자동 만료 기간(일). Infrastructure Design Report D-1에서 6개월(약 180일)로 확정되었다."
  type        = number
  default     = 180
}

variable "access_log_lifecycle_days" {
  description = "접근 로그 객체 자동 만료 기간(일). 원본 이미지 객체보다 짧게 유지해 로그 저장 비용을 낮춘다."
  type        = number
  default     = 90
}

variable "kms_key_deletion_window_days" {
  description = "KMS Key 삭제 대기 기간(일). AWS 최대값은 30일이다."
  type        = number
  default     = 30
}

variable "tags" {
  description = "이 스택이 생성하는 리소스에 공통으로 적용할 태그. infra-apply 역할의 KMS 권한 조건(aws:ResourceTag/Project)이 이 태그를 전제로 한다."
  type        = map(string)
  default = {
    Project     = "qello"
    ManagedBy   = "terraform"
    Environment = "dev"
  }
}
