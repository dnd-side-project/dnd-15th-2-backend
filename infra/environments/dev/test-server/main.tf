locals {
  media_bucket_arn = "arn:aws:s3:::${var.media_bucket_name}"

  ssm_parameter_path_prefix        = "/${var.project_prefix}/${var.name}"
  db_password_parameter_name       = "${local.ssm_parameter_path_prefix}/db-password"
  auth_token_secret_parameter_name = "${local.ssm_parameter_path_prefix}/auth-token-secret"
}

# --- 네트워크 -----------------------------------------------------------------

module "network" {
  source = "../../../modules/network"

  project_prefix     = var.project_prefix
  name               = var.name
  vpc_cidr           = var.vpc_cidr
  public_subnet_cidr = var.public_subnet_cidr
  availability_zone  = var.availability_zone
  tags               = var.tags
}

# --- ECR ------------------------------------------------------------------

resource "aws_ecr_repository" "app" {
  name                 = "${var.project_prefix}-${var.name}"
  image_tag_mutability = var.ecr_image_tag_mutability

  image_scanning_configuration {
    scan_on_push = true
  }

  # 전용 CMK는 쓰지 않는다. 월 1 USD 고정 비용이 발생해 예산 여유
  # 4.5%(D-3 §6)를 넘어선다. 이미지는 이미 서명·스캔되고 리포지토리
  # 정책으로 접근이 인스턴스 Role 하나로 한정되어 있어 기본 AES256
  # 암호화로 충분하다고 판단했다.
  # checkov:skip=CKV_AWS_136:dev 테스트 서버 예산 제약으로 전용 CMK 대신 기본 AES256 암호화를 쓴다. D-3 §6 참고.
  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = var.tags
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "최근 이미지 ${var.ecr_max_image_count}개만 보존해 저장 비용을 고정한다(D-3 §6)."
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.ecr_max_image_count
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

# --- 비밀값 (SSM SecureString, value_wo) ---------------------------------------
# 명시적으로 key_id를 지정해 이 파라미터가 계정 기본 AWS 관리형 SSM 키
# (alias/aws/ssm)를 쓴다는 것을 코드로 드러낸다. 전용 CMK는 월 1 USD
# 고정 비용이 발생해 예산 여유 4.5%를 넘기므로 쓰지 않는다(D-3 §6).
resource "aws_ssm_parameter" "db_password" {
  name             = local.db_password_parameter_name
  type             = "SecureString"
  key_id           = "alias/aws/ssm"
  value_wo         = var.db_password
  value_wo_version = var.db_password_version

  tags = var.tags

  # 알려진 Terraform AWS Provider 제약(hashicorp/terraform-provider-aws#42849):
  # value_wo를 쓰는 SecureString도 apply 시 provider가 내부적으로
  # GetParameter(WithDecryption=true)를 호출해 kms:Decrypt 없이는 apply가
  # 실패한다. State에는 값이 저장되지 않으므로 원래 목표(State 평문 노출
  # 방지)는 유지된다(D-3 §5 S-5).
}

resource "aws_ssm_parameter" "auth_token_secret" {
  name             = local.auth_token_secret_parameter_name
  type             = "SecureString"
  key_id           = "alias/aws/ssm"
  value_wo         = var.auth_token_secret
  value_wo_version = var.auth_token_secret_version

  tags = var.tags
}

# --- 컴퓨트 --------------------------------------------------------------------

module "app_server" {
  source = "../../../modules/app-server"

  project_prefix = var.project_prefix
  name           = var.name

  vpc_id    = module.network.vpc_id
  subnet_id = module.network.public_subnet_id

  instance_type        = var.instance_type
  app_port             = var.app_port
  allowed_ingress_cidr = var.allowed_ingress_cidr
  root_volume_size     = var.root_volume_size
  data_volume_size     = var.data_volume_size

  ecr_repository_arn  = aws_ecr_repository.app.arn
  ecr_registry        = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
  ecr_repository_name = aws_ecr_repository.app.name
  app_image_tag       = var.app_image_tag
  compose_version     = var.compose_version

  ssm_parameter_arns = [
    aws_ssm_parameter.db_password.arn,
    aws_ssm_parameter.auth_token_secret.arn,
  ]
  ssm_kms_key_arn                  = data.aws_kms_alias.ssm_default.target_key_arn
  db_password_parameter_name       = local.db_password_parameter_name
  auth_token_secret_parameter_name = local.auth_token_secret_parameter_name

  image_bucket_arn         = local.media_bucket_arn
  image_bucket_kms_key_arn = var.media_bucket_kms_key_arn
  media_bucket_name        = var.media_bucket_name

  spring_profiles_active = var.spring_profiles_active

  enable_auto_stop              = var.enable_auto_stop
  auto_stop_schedule_expression = var.auto_stop_schedule_expression
  auto_stop_schedule_timezone   = var.auto_stop_schedule_timezone

  tags = var.tags
}

data "aws_kms_alias" "ssm_default" {
  name = "alias/aws/ssm"
}
