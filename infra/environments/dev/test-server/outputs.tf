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
  description = "DB 비밀번호 SSM 파라미터 이름. 값 입력은 사람이 콘솔 또는 CLI로 수행한다."
  value       = aws_ssm_parameter.db_password.name
}

output "auth_token_secret_parameter_name" {
  description = "QELLO_AUTH_ACCESS_TOKEN_SECRET SSM 파라미터 이름. 값 입력은 사람이 콘솔 또는 CLI로 수행한다."
  value       = aws_ssm_parameter.auth_token_secret.name
}
