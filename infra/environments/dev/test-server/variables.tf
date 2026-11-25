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

# 자동 정지는 결정 항목 H-1(#233)로 승인된 예산 통제 수단이었다. 매일 03:00에
# 정지되지만 자동 시작이 없어 쓸 때마다 수동 시작이 필요한 운영 마찰 때문에
# 끄기로 재결정했다(#276). 상시 구동은 월 약 24.37 USD로 Issue #229의 월 10 USD
# 상한을 2.4배 초과하므로, 이 기본값을 false로 두는 것은 상한 재설정을 전제한다.
# 다시 켜려면 true로 되돌리고 apply하면 스케줄러 리소스가 재생성된다.
variable "enable_auto_stop" {
  description = "EventBridge Scheduler 자동 정지 활성화 여부. H-1(#233)로 승인되었다가 #276에서 비활성화로 재결정되었다."
  type        = bool
  default     = false
}

variable "auto_stop_schedule_expression" {
  description = "인스턴스를 정지할 cron 표현식. 결정 항목 H-1과 완료 조건은 매일 정지를 요구한다."
  type        = string
  default     = "cron(0 3 ? * * *)"
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

# D-5: sensitive만으로는 plan 입력 보존을 막지 못하므로 새 비밀은 ephemeral로 제한한다.
variable "push_token_current_key_id" {
  description = "현재 push token 암호화 key ID. 보호된 입력 경로로만 제공한다."
  type        = string
  sensitive   = true
  ephemeral   = true
}

variable "push_token_current_key_id_version" {
  description = "push_token_current_key_id 갱신 시 증가시키는 value_wo_version."
  type        = number
  default     = 1

  validation {
    condition     = var.push_token_current_key_id_version > 0 && floor(var.push_token_current_key_id_version) == var.push_token_current_key_id_version
    error_message = "push_token_current_key_id_version은 양의 정수여야 한다."
  }
}

variable "push_token_current_encryption_key_base64" {
  description = "현재 push token 암호화 key의 Base64 값. 보호된 입력 경로로만 제공한다."
  type        = string
  sensitive   = true
  ephemeral   = true
}

variable "push_token_current_encryption_key_base64_version" {
  description = "push_token_current_encryption_key_base64 갱신 시 증가시키는 value_wo_version."
  type        = number
  default     = 1

  validation {
    condition     = var.push_token_current_encryption_key_base64_version > 0 && floor(var.push_token_current_encryption_key_base64_version) == var.push_token_current_encryption_key_base64_version
    error_message = "push_token_current_encryption_key_base64_version은 양의 정수여야 한다."
  }
}

variable "push_token_fingerprint_key_base64" {
  description = "Push token fingerprint key의 Base64 값. 보호된 입력 경로로만 제공한다."
  type        = string
  sensitive   = true
  ephemeral   = true
}

variable "push_token_fingerprint_key_base64_version" {
  description = "push_token_fingerprint_key_base64 갱신 시 증가시키는 value_wo_version."
  type        = number
  default     = 1

  validation {
    condition     = var.push_token_fingerprint_key_base64_version > 0 && floor(var.push_token_fingerprint_key_base64_version) == var.push_token_fingerprint_key_base64_version
    error_message = "push_token_fingerprint_key_base64_version은 양의 정수여야 한다."
  }
}

variable "push_fcm_project_id" {
  description = "Dev FCM project ID. 보호된 입력 경로로만 제공한다."
  type        = string
  sensitive   = true
  ephemeral   = true
}

variable "push_fcm_project_id_version" {
  description = "push_fcm_project_id 갱신 시 증가시키는 value_wo_version."
  type        = number
  default     = 1

  validation {
    condition     = var.push_fcm_project_id_version > 0 && floor(var.push_fcm_project_id_version) == var.push_fcm_project_id_version
    error_message = "push_fcm_project_id_version은 양의 정수여야 한다."
  }
}

variable "push_fcm_credential_json" {
  description = "Dev FCM 서비스 계정 credential JSON. 보호된 입력 경로로만 제공한다."
  type        = string
  sensitive   = true
  ephemeral   = true
}

variable "push_fcm_credential_json_version" {
  description = "push_fcm_credential_json 갱신 시 증가시키는 value_wo_version."
  type        = number
  default     = 1

  validation {
    condition     = var.push_fcm_credential_json_version > 0 && floor(var.push_fcm_credential_json_version) == var.push_fcm_credential_json_version
    error_message = "push_fcm_credential_json_version은 양의 정수여야 한다."
  }
}

# Credential 크기만으로 tier를 추론하면 ephemeral 값이 영속 속성으로 전파된다.
# D-5 H-4에 따라 4 KiB 초과 시 credential만 검토된 구성에서 Advanced로 선택한다.
variable "push_fcm_credential_json_tier" {
  description = "FCM credential 파라미터 tier. Standard가 기본이며 4 KiB 초과 시 승인된 Advanced 구성을 사용한다."
  type        = string
  default     = "Standard"

  validation {
    condition     = contains(["Standard", "Advanced"], var.push_fcm_credential_json_tier)
    error_message = "push_fcm_credential_json_tier는 Standard 또는 Advanced여야 한다."
  }
}

variable "coordinate_current_key_id" {
  description = "현재 좌표 암호화 key ID. 보호된 Environment 입력으로만 제공한다."
  type        = string
  sensitive   = true
  ephemeral   = true
}

variable "coordinate_current_key_id_version" {
  description = "좌표 current key ID의 value_wo_version. 첫 회전 설계 승인 전에는 초기 버전만 허용한다."
  type        = number
  default     = 1

  validation {
    condition     = var.coordinate_current_key_id_version == 1
    error_message = "좌표 key ID version은 첫 회전 설계 승인 전까지 1이어야 한다."
  }
}

variable "coordinate_current_key_base64" {
  description = "Push key와 독립적인 좌표 AES-256 key의 standard Base64 값. 보호된 Environment 입력으로만 제공한다."
  type        = string
  sensitive   = true
  ephemeral   = true
}

variable "coordinate_current_key_base64_version" {
  description = "좌표 current key의 value_wo_version. 첫 회전 설계 승인 전에는 초기 버전만 허용한다."
  type        = number
  default     = 1

  validation {
    condition     = var.coordinate_current_key_base64_version == 1
    error_message = "좌표 key version은 첫 회전 설계 승인 전까지 1이어야 한다."
  }
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
