variable "project_prefix" {
  description = "리소스 이름 접두사. infra-apply 역할의 IAM 권한 범위(태그·이름 기반)와 일치해야 한다."
  type        = string
}

variable "name" {
  description = "이 VPC를 사용하는 스택을 식별하는 짧은 이름. 리소스 이름에 `$${project_prefix}-$${name}-*` 형태로 들어간다."
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR 블록."
  type        = string
  default     = "10.60.0.0/16"
}

variable "public_subnet_cidr" {
  description = "퍼블릭 서브넷 CIDR 블록. vpc_cidr에 포함되어야 한다."
  type        = string
  default     = "10.60.0.0/24"
}

variable "availability_zone" {
  description = "퍼블릭 서브넷을 생성할 가용 영역. 이 이슈는 단일 AZ best-effort 구성이라 서브넷을 하나만 만든다(Infrastructure Design Report D-3 §2)."
  type        = string
  default     = "ap-northeast-2a"
}

variable "tags" {
  description = "이 모듈이 생성하는 리소스에 공통으로 적용할 태그."
  type        = map(string)
  default     = {}
}
