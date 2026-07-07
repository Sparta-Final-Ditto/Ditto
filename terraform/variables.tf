variable "aws_region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "project_name" {
  description = "프로젝트 이름 (리소스 네이밍 접두사)"
  type        = string
  default     = "sparta-ditto"
}

variable "environment" {
  description = "배포 환경"
  type        = string
  default     = "prod"

  validation {
    condition     = contains(["prod", "staging", "dev"], var.environment)
    error_message = "environment 는 prod / staging / dev 중 하나여야 합니다."
  }
}

# ─── 네트워크 ──────────────────────────────────────────────────────────────────

variable "vpc_cidr" {
  description = "VPC CIDR 블록"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "퍼블릭 서브넷 CIDR 목록 (가용 영역 순서와 일치)"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "프라이빗 서브넷 CIDR 목록 (가용 영역 순서와 일치)"
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.11.0/24"]
}

variable "availability_zones" {
  description = "사용할 가용 영역 목록"
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

# ─── EC2 인스턴스 ──────────────────────────────────────────────────────────────

variable "app_instance_type" {
  description = "앱 서버 인스턴스 타입 (NGINX + 전체 Spring Boot 서비스)"
  type        = string
  default     = "t3.large"
}

variable "db_instance_type" {
  description = "DB 서버 인스턴스 타입 (PostgreSQL + Redis + MongoDB + Kafka)"
  type        = string
  default     = "t3.medium"
}

variable "monitoring_instance_type" {
  description = "모니터링 서버 인스턴스 타입 (Prometheus + Grafana)"
  type        = string
  default     = "t3.micro"
}

variable "key_pair_name" {
  description = "EC2 SSH 접근용 키 페어 이름 (AWS 콘솔에서 미리 생성 필요)"
  type        = string
}

variable "admin_cidr_blocks" {
  description = "SSH 및 Grafana/Prometheus UI 접근을 허용할 팀원 IP CIDR 목록"
  type        = list(string)
}

# ─── ECR ───────────────────────────────────────────────────────────────────────

variable "ecr_service_names" {
  description = "ECR 레포지토리를 생성할 서비스 이름 목록"
  type        = list(string)
  default = [
    "api-gateway",
    "user-service",
    "feed-service",
    "match-service",
    "chat-service",
    "notification-service",
    "embedding-service",
    # "assistant_service"
  ]
}

# ─── S3 / CloudFront ───────────────────────────────────────────────────────────

variable "s3_media_bucket_name" {
  description = "미디어 파일 저장용 S3 버킷 이름 (AWS 전역 유니크 필수, 예: ditto-media-prod-abc123)"
  type        = string
}

variable "cloudfront_price_class" {
  description = "CloudFront 가격 등급 (PriceClass_100=미국·유럽, PriceClass_200=+아시아, PriceClass_All=전체)"
  type        = string
  default     = "PriceClass_200"
}