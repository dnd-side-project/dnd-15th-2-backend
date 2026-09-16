variable "aws_region" {
  description = "리소스를 생성할 AWS Region. Infrastructure Design Report D-3(GitHub Issue #229/#233)에서 ap-northeast-2로 확정되었다."
  type        = string
  default     = "ap-northeast-2"
}

variable "project_prefix" {
  description = "infra/bootstrap의 infra-apply 역할이 관리 권한 범위로 사용하는 것과 동일한 접두사여야 한다."
  type        = string
  default     = "qello-dev"
}

variable "name" {
  description = "이 스택이 만드는 리소스를 식별하는 짧은 이름."
  type        = string
  default     = "test-server"
}

# --- 네트워크 -----------------------------------------------------------------

variable "vpc_cidr" {
  description = "테스트 서버 전용 VPC CIDR."
  type        = string
  default     = "10.60.0.0/16"
}

variable "public_subnet_cidr" {
  description = "퍼블릭 서브넷 CIDR."
  type        = string
  default     = "10.60.0.0/24"
}

variable "availability_zone" {
  description = "단일 AZ 배치 대상 가용 영역."
  type        = string
  default     = "ap-northeast-2a"
}

# --- 컴퓨트 -------------------------------------------------------------------

variable "instance_type" {
  description = "EC2 인스턴스 타입. Infrastructure Design Report D-3 §11에서 t3.small로 확정되었다."
  type        = string
  default     = "t3.small"
}

variable "app_port" {
  description = "백엔드 컨테이너 포트이자 인바운드 허용 포트."
  type        = number
  default     = 8080
}

variable "allowed_ingress_cidr" {
  description = "app_port 인바운드를 허용할 CIDR."
  type        = string
  default     = "0.0.0.0/0"
}

variable "root_volume_size" {
  description = "루트 EBS 볼륨 크기(GiB)."
  type        = number
  default     = 8
}

variable "data_volume_size" {
  description = "PostGIS 데이터 전용 EBS 볼륨 크기(GiB). D-3 §6에서 10 GiB로 확정되었다."
  type        = number
  default     = 10
}

variable "spring_profiles_active" {
  description = "컨테이너에 전달할 Spring 프로파일. dev 프로파일 자체는 #230 범위다."
  type        = string
  default     = "dev"
}

variable "app_image_tag" {
  description = "user_data가 pull할 백엔드 이미지 태그. #231 배포 workflow가 새 태그를 밀어넣기 전까지 고정값을 쓴다."
  type        = string
  default     = "latest"
}

variable "compose_version" {
  description = "설치할 Docker Compose CLI plugin 버전(GitHub Release 태그)."
  type        = string
  default     = "v2.29.7"
}

# --- 자동 정지 가드레일 --------------------------------------------------------

variable "enable_auto_stop" {
  description = "EventBridge Scheduler 자동 정지 활성화 여부. 결정 항목 H-1로 승인되었다(#233)."
  type        = bool
  default     = true
}

variable "auto_stop_schedule_expression" {
  description = "인스턴스를 정지할 cron 표현식."
  type        = string
  default     = "cron(0 19 ? * MON-FRI *)"
}

variable "auto_stop_schedule_timezone" {
  description = "auto_stop_schedule_expression을 해석할 시간대."
  type        = string
  default     = "Asia/Seoul"
}

# --- ECR ----------------------------------------------------------------------

variable "ecr_image_tag_mutability" {
  description = "ECR 이미지 태그 변경 가능 여부. 배포 추적성을 위해 IMMUTABLE을 기본값으로 둔다."
  type        = string
  default     = "IMMUTABLE"
}

variable "ecr_max_image_count" {
  description = "ECR에 보존할 최근 이미지 개수. 저장 비용을 고정하기 위한 lifecycle policy 기준값이다(D-3 §6 비용 증가 요인)."
  type        = number
  default     = 5
}

# --- 이미지 버킷 (#63) ----------------------------------------------------------

variable "media_bucket_name" {
  description = "#63에서 생성한 dev 이미지 S3 버킷 이름. 인스턴스 Role에 이 버킷 객체 권한만 부여한다."
  type        = string
  default     = "qello-dev-post-images"
}

variable "media_bucket_kms_key_arn" {
  description = "위 버킷을 암호화하는 전용 KMS Key ARN. #63 storage 스택의 post_image_kms_key_arn output 값을 그대로 넣는다."
  type        = string
}

# --- 비밀값 (SSM SecureString, value_wo) ---------------------------------------
# 값 자체는 Terraform이 생성하지 않는다. apply 시 -var 또는 TF_VAR_* 환경
# 변수로 사람이 직접 제공하고, value_wo를 쓰므로 State에도 저장되지 않는다
# (D-3 §5 S-5, 결정 항목 H-9).

variable "db_password" {
  description = "PostGIS 컨테이너 DB 비밀번호. 커밋된 파일에 값을 남기지 않고 apply 시점에만 제공한다."
  type        = string
  sensitive   = true
}

variable "db_password_version" {
  description = "db_password 값을 갱신할 때 증가시키는 버전 번호(value_wo_version 요구사항)."
  type        = number
  default     = 1
}

variable "auth_token_secret" {
  description = "QELLO_AUTH_ACCESS_TOKEN_SECRET 값. 커밋된 파일에 값을 남기지 않고 apply 시점에만 제공한다."
  type        = string
  sensitive   = true
}

variable "auth_token_secret_version" {
  description = "auth_token_secret 값을 갱신할 때 증가시키는 버전 번호(value_wo_version 요구사항)."
  type        = number
  default     = 1
}

variable "tags" {
  description = "이 스택이 생성하는 리소스에 공통으로 적용할 태그. infra-apply 역할의 IAM 권한 조건(aws:ResourceTag/Project)이 이 태그를 전제로 한다."
  type        = map(string)
  default = {
    Project     = "qello"
    ManagedBy   = "terraform"
    Environment = "dev"
  }
}
