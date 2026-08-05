variable "aws_region" {
  description = "부트스트랩 리소스를 생성할 AWS Region. Infrastructure Design Report D-1(GitHub Issue #63)에서 ap-northeast-2로 확정되었다."
  type        = string
  default     = "ap-northeast-2"
}

variable "state_bucket_name" {
  description = "Terraform State를 저장할 S3 버킷 이름. S3 버킷명은 전역적으로 고유해야 하므로 기본값을 두지 않는다."
  type        = string

  validation {
    condition     = length(var.state_bucket_name) > 0
    error_message = "state_bucket_name은 비어 있을 수 없다."
  }
}

variable "project_prefix" {
  description = "이 프로젝트가 소유하는 AWS 리소스 이름/IAM 리소스 이름 접두사. infra-apply 역할의 IAM 권한 범위를 이 접두사로 한정한다."
  type        = string
  default     = "qello-dev"
}

variable "github_repository" {
  description = "GitHub OIDC 신뢰 정책의 sub claim에 사용할 \"owner/repo\" 형식 저장소 식별자."
  type        = string
  default     = "dnd-side-project/dnd-15th-2-backend"
}

variable "github_apply_environment" {
  description = "Terraform apply를 실행하는 보호된 GitHub Environment 이름. infra-apply 역할의 신뢰 조건을 이 Environment로 제한한다."
  type        = string
  default     = "infrastructure-apply"
}

variable "tags" {
  description = "모든 부트스트랩 리소스에 적용할 공통 태그."
  type        = map(string)
  default = {
    Project     = "qello"
    ManagedBy   = "terraform"
    Environment = "shared-bootstrap"
  }
}
