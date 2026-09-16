output "vpc_id" {
  description = "생성된 VPC ID."
  value       = aws_vpc.this.id
}

output "public_subnet_id" {
  description = "퍼블릭 서브넷 ID."
  value       = aws_subnet.public.id
}

output "availability_zone" {
  description = "퍼블릭 서브넷이 위치한 가용 영역."
  value       = aws_subnet.public.availability_zone
}
