output "post_image_bucket_name" {
  description = "게시물 이미지 테스트 S3 버킷 이름."
  value       = module.post_image_bucket.bucket_id
}

output "post_image_bucket_arn" {
  description = "게시물 이미지 테스트 S3 버킷 ARN."
  value       = module.post_image_bucket.bucket_arn
}

output "access_log_bucket_name" {
  description = "S3 서버 접근 로그 대상 버킷 이름."
  value       = module.access_log_bucket.bucket_id
}

output "post_image_kms_key_arn" {
  description = "게시물 이미지 버킷 암호화 전용 KMS Key ARN."
  value       = aws_kms_key.post_images.arn
}

output "dev_s3_tester_role_arn" {
  description = "팀원이 로컬에서 이 버킷을 테스트할 때 assume하는 IAM Role ARN(MFA 필수, 고정 Access Key 없음)."
  value       = aws_iam_role.dev_s3_tester.arn
}

output "s3_tester_user_name" {
  description = "팀원 콘솔 로그인용 IAM User 이름. 비밀번호와 MFA는 콘솔에서 사람이 설정한다."
  value       = aws_iam_user.s3_tester.name
}
