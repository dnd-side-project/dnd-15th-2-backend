output "state_bucket_name" {
  description = "Terraform State를 저장하는 S3 버킷 이름. 다른 스택의 backend 설정에서 참조한다."
  value       = aws_s3_bucket.terraform_state.id
}

output "state_access_log_bucket_name" {
  description = "State 버킷의 S3 서버 접근 로그가 저장되는 버킷 이름."
  value       = module.state_access_log_bucket.bucket_id
}

output "state_bucket_region" {
  description = "State 버킷이 위치한 Region."
  value       = var.aws_region
}

output "infra_plan_role_arn" {
  description = "PR에서 `terraform plan`을 실행할 때 assume하는 역할의 ARN."
  value       = aws_iam_role.infra_plan.arn
}

output "infra_apply_role_arn" {
  description = "보호된 infrastructure-apply Environment에서 `terraform apply`를 실행할 때 assume하는 역할의 ARN."
  value       = aws_iam_role.infra_apply.arn
}

output "github_oidc_provider_arn" {
  description = "GitHub Actions OIDC Provider ARN."
  value       = aws_iam_openid_connect_provider.github.arn
}
