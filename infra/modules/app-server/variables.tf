variable "project_prefix" {
  description = "리소스 이름 접두사. infra-apply 역할의 IAM 권한 범위(태그·이름 기반)와 일치해야 한다."
  type        = string
}

variable "name" {
  description = "이 인스턴스를 식별하는 짧은 이름. 리소스 이름에 project_prefix-name-* 형태로 들어간다."
  type        = string
}

variable "vpc_id" {
  description = "인스턴스를 배치할 VPC ID."
  type        = string
}

variable "subnet_id" {
  description = "인스턴스를 배치할 퍼블릭 서브넷 ID."
  type        = string
}

variable "instance_type" {
  description = "EC2 인스턴스 타입. Infrastructure Design Report D-3 §11에서 t3.small(x86_64)로 확정되었다."
  type        = string
  default     = "t3.small"
}

variable "app_port" {
  description = "백엔드 컨테이너가 수신하는 포트이자 인바운드 허용 포트. TLS 없이 평문 HTTP로 노출된다(D-3 §5 S-1, SECURITY-EXCEPTION 참고)."
  type        = number
  default     = 8080
}

variable "allowed_ingress_cidr" {
  description = "app_port 인바운드를 허용할 CIDR. 프론트 개발자 IP가 유동적이라 기본값은 전체 허용이다(D-3 §5 S-2)."
  type        = string
  default     = "0.0.0.0/0"
}

variable "root_volume_size" {
  description = "루트 EBS 볼륨 크기(GiB)."
  type        = number
  default     = 8
}

variable "data_volume_size" {
  description = "PostGIS 데이터 전용 EBS 볼륨 크기(GiB). Infrastructure Design Report D-3 §6에서 10 GiB로 확정되었다."
  type        = number
  default     = 10
}

variable "ecr_repository_arn" {
  description = "인스턴스 Role이 이미지를 pull할 ECR 리포지토리 ARN."
  type        = string
}

variable "ssm_parameter_arns" {
  description = "인스턴스 Role이 값을 읽을 SSM 파라미터 ARN 목록(DB 비밀번호, 인증 토큰 시크릿). 이 스택 소유 경로로만 한정한다."
  type        = list(string)
}

variable "ssm_kms_key_arn" {
  description = "위 SSM 파라미터를 암호화하는 KMS Key ARN. kms:Decrypt를 이 Key로만 한정한다."
  type        = string
}

variable "image_bucket_arn" {
  description = "인스턴스 Role에 객체 권한을 부여할 #63 dev 이미지 S3 버킷 ARN."
  type        = string
}

variable "image_bucket_kms_key_arn" {
  description = "위 이미지 버킷을 암호화하는 KMS Key ARN. kms:Decrypt/GenerateDataKey*를 이 Key로만 한정한다."
  type        = string
}

variable "ecr_registry" {
  description = "ECR 레지스트리 호스트명(계정ID.dkr.ecr.region.amazonaws.com). user_data가 docker login 대상으로 사용한다."
  type        = string
}

variable "ecr_repository_name" {
  description = "백엔드 이미지가 저장된 ECR 리포지토리 이름(URI가 아니라 이름만)."
  type        = string
}

variable "app_image_tag" {
  description = "user_data가 pull할 백엔드 이미지 태그. 배포 workflow(#231)가 새 태그를 밀어넣기 전까지는 고정값을 쓴다."
  type        = string
  default     = "latest"
}

variable "db_password_parameter_name" {
  description = "DB 비밀번호를 담은 SSM 파라미터 이름(ARN 아님). user_data가 부팅 시 조회한다."
  type        = string
}

variable "auth_token_secret_parameter_name" {
  description = "QELLO_AUTH_ACCESS_TOKEN_SECRET을 담은 SSM 파라미터 이름. user_data가 부팅 시 조회한다."
  type        = string
}

variable "media_bucket_name" {
  description = "애플리케이션이 접근하는 #63 dev 이미지 S3 버킷 이름."
  type        = string
}

variable "spring_profiles_active" {
  description = "컨테이너에 전달할 Spring 프로파일. CORS·헬스체크·알림 설정 완화는 #230 범위다."
  type        = string
  default     = "dev"
}

variable "compose_version" {
  description = "설치할 Docker Compose CLI plugin 버전(GitHub Release 태그). 버전을 고정해 검사 도구가 조용히 바뀌지 않게 한다(AGENTS.md 4.1)."
  type        = string
  default     = "v2.29.7"
}

variable "enable_auto_stop" {
  description = "EventBridge Scheduler로 인스턴스를 자동 정지할지 여부. Infrastructure Design Report D-3 결정 항목 H-1로 활성화가 승인되었다."
  type        = bool
  default     = true
}

variable "auto_stop_schedule_expression" {
  description = "인스턴스를 정지할 EventBridge Scheduler cron 표현식. 결정 항목 H-1과 완료 조건은 매일 정지를 요구해 기본값은 매일 새벽 3시(KST)다(스케줄 자체 시간대는 auto_stop_schedule_timezone으로 지정)."
  type        = string
  default     = "cron(0 3 ? * * *)"
}

variable "auto_stop_schedule_timezone" {
  description = "auto_stop_schedule_expression을 해석할 시간대."
  type        = string
  default     = "Asia/Seoul"
}

variable "tags" {
  description = "이 모듈이 생성하는 리소스에 공통으로 적용할 태그."
  type        = map(string)
  default     = {}
}
