output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "app_public_ip" {
  description = "앱 서버 퍼블릭 IP (EIP)"
  value       = aws_eip.app.public_ip
}

output "app_private_ip" {
  description = "앱 서버 프라이빗 IP (VPC 내부 통신용)"
  value       = aws_instance.app.private_ip
}

output "db_private_ip" {
  description = "DB 서버 프라이빗 IP"
  value       = aws_instance.db.private_ip
}

output "nat_public_ip" {
  description = "NAT Instance 퍼블릭 IP (프라이빗 서브넷 아웃바운드 출발지)"
  value       = aws_eip.nat.public_ip
}

output "monitoring_public_ip" {
  description = "모니터링 서버 퍼블릭 IP"
  value       = aws_eip.monitoring.public_ip
}

output "grafana_url" {
  description = "Grafana 대시보드 URL (초기 admin/admin, 최초 로그인 후 변경)"
  value       = "http://${aws_eip.monitoring.public_ip}:3000"
}

output "ecr_repository_urls" {
  description = "서비스별 ECR 레포지토리 URL (docker push 대상)"
  value       = { for k, v in aws_ecr_repository.services : k => v.repository_url }
}

output "ecr_registry" {
  description = "ECR 레지스트리 URL (docker login 대상)"
  value       = local.ecr_registry
}

output "s3_media_bucket" {
  description = "미디어 S3 버킷 이름"
  value       = aws_s3_bucket.media.id
}

output "cloudfront_domain" {
  description = "CloudFront 도메인 (미디어 URL 접두사, 예: https://<domain>/feeds/uuid.mp4)"
  value       = "https://${aws_cloudfront_distribution.media.domain_name}"
}

output "ecr_login_command" {
  description = "ECR 로그인 명령어 (CI/CD 파이프라인 참고용)"
  value       = "aws ecr get-login-password --region ${var.aws_region} | docker login --username AWS --password-stdin ${local.ecr_registry}"
}