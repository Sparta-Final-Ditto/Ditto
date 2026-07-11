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

variable "db_private_ip" {
  description = "DB 서버 프라이빗 IP 고정값. 인스턴스 교체 후에도 앱(.env.prod)/모니터링 설정이 깨지지 않도록 고정한다. (terraform.tfvars에서 설정)"
  type        = string
}

variable "monitoring_instance_type" {
  description = "모니터링 서버 인스턴스 타입 (Prometheus + Grafana). t3.micro(1GB)는 OOM으로 다운된 이력이 있어 t3.small(2GB)로 상향."
  type        = string
  default     = "t3.small"
}

variable "key_pair_name" {
  description = "EC2 SSH 접근용 키 페어 이름 (AWS 콘솔에서 미리 생성 필요)"
  type        = string
}

variable "admin_cidr_blocks" {
  description = "SSH 및 Grafana/Prometheus UI 접근을 허용할 팀원 IP CIDR 목록"
  type        = list(string)
}

# ─── DB 자격증명 (ditto-db 인스턴스 compose에 주입) ────────────────────────────
# 앱이 실제로 연결하려면 CD 파이프라인의 ENV_PROD 시크릿에 설정하는
# DB_USERNAME/DB_PASSWORD/MONGO_USERNAME/MONGO_PASSWORD 와 반드시 동일한 값이어야 한다.

variable "db_username" {
  description = "PostgreSQL 사용자 이름 (ENV_PROD의 DB_USERNAME과 일치해야 함)"
  type        = string
  default     = "ditto"
}

variable "db_password" {
  description = "PostgreSQL 비밀번호 (ENV_PROD의 DB_PASSWORD와 일치해야 함)"
  type        = string
  sensitive   = true
}

variable "mongo_username" {
  description = "MongoDB 사용자 이름 (ENV_PROD의 MONGO_USERNAME과 일치해야 함)"
  type        = string
  default     = "ditto"
}

variable "mongo_password" {
  description = "MongoDB 비밀번호 (ENV_PROD의 MONGO_PASSWORD와 일치해야 함)"
  type        = string
  sensitive   = true
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
    "assistant-service",
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