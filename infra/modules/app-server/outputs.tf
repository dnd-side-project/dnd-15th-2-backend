output "instance_id" {
  description = "생성된 EC2 인스턴스 ID."
  value       = aws_instance.this.id
}

output "instance_role_arn" {
  description = "인스턴스 Role ARN."
  value       = aws_iam_role.instance.arn
}

output "security_group_id" {
  description = "이 인스턴스의 보안 그룹 ID."
  value       = aws_security_group.this.id
}

output "public_ip" {
  description = "인스턴스에 연결된 Elastic IP 주소."
  value       = aws_eip.this.public_ip
}

output "data_volume_id" {
  description = "PostGIS 데이터 전용 EBS 볼륨 ID."
  value       = aws_ebs_volume.data.id
}
