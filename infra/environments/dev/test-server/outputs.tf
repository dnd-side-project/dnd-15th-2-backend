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

output "push_token_current_key_id_parameter_name" {
  description = "현재 push token 암호화 key ID의 SSM 파라미터 이름. 비밀값은 반환하지 않는다."
  value       = aws_ssm_parameter.push_token_current_key_id.name
}

output "push_token_current_encryption_key_parameter_name" {
  description = "현재 push token 암호화 key의 SSM 파라미터 이름. 비밀값은 반환하지 않는다."
  value       = aws_ssm_parameter.push_token_current_encryption_key.name
}

output "push_token_fingerprint_key_parameter_name" {
  description = "Push token fingerprint key의 SSM 파라미터 이름. 비밀값은 반환하지 않는다."
  value       = aws_ssm_parameter.push_token_fingerprint_key.name
}

output "push_fcm_project_id_parameter_name" {
  description = "Dev FCM project ID의 SSM 파라미터 이름. 비밀값은 반환하지 않는다."
  value       = aws_ssm_parameter.push_fcm_project_id.name
}

output "push_fcm_credential_json_parameter_name" {
  description = "Dev FCM credential JSON의 SSM 파라미터 이름. 비밀값은 반환하지 않는다."
  value       = aws_ssm_parameter.push_fcm_credential_json.name
}

output "coordinate_current_key_id_parameter_name" {
  description = "현재 좌표 암호화 key ID의 SSM 파라미터 이름. 비밀값은 반환하지 않는다."
  value       = aws_ssm_parameter.coordinate_current_key_id.name
}

output "coordinate_current_key_parameter_name" {
  description = "현재 좌표 암호화 key의 SSM 파라미터 이름. 비밀값은 반환하지 않는다."
  value       = aws_ssm_parameter.coordinate_current_key.name
}

output "ssm_parameter_path_prefix" {
  description = "배포 구성에 사용하는 SSM 파라미터 경로 접두사. 비밀값은 포함하지 않는다."
  value       = local.ssm_parameter_path_prefix
}
