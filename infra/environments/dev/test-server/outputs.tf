output "public_ip" {
  description = "테스트 서버 Elastic IP 주소. 프론트엔드가 이 주소로 API를 호출한다."
  value       = module.app_server.public_ip
}

output "instance_id" {
  description = "EC2 인스턴스 ID. SSM Session Manager 접속에 사용한다."
  value       = module.app_server.instance_id
}

output "ecr_repository_url" {
  description = "백엔드 이미지를 push할 ECR 리포지토리 URL. #231 배포 workflow가 사용한다."
  value       = aws_ecr_repository.app.repository_url
}

output "db_password_parameter_name" {
  description = "DB 비밀번호 SSM 파라미터 이름. 값은 value_wo이므로 콘솔·CLI로 직접 수정하지 않는다. 갱신 시 TF_VAR_db_password와 db_password_version을 올려 terraform apply로 반영한다."
  value       = aws_ssm_parameter.db_password.name
}

output "auth_token_secret_parameter_name" {
  description = "QELLO_AUTH_ACCESS_TOKEN_SECRET SSM 파라미터 이름. 값은 value_wo이므로 콘솔·CLI로 직접 수정하지 않는다. 갱신 시 TF_VAR_auth_token_secret과 auth_token_secret_version을 올려 terraform apply로 반영한다."
  value       = aws_ssm_parameter.auth_token_secret.name
}
