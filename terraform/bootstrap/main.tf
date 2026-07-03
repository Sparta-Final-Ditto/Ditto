# Terraform state 저장용 S3 버킷을 만드는 최초 1회용 구성입니다.
# 이 디렉토리 자체는 backend를 쓰지 않고 로컬 state로 관리합니다 (닭과 달걀 문제 회피).
# 락은 별도 DynamoDB 테이블 없이 Terraform 1.10+ 의 S3 네이티브 락(use_lockfile)을 사용합니다.
#
# 사용법:
#   cd terraform/bootstrap
#   terraform init
#   terraform apply
#
# 적용 후, terraform/main.tf 의 backend "s3" 블록 주석을 해제하고
# terraform/ 에서 `terraform init` 을 다시 실행하면 로컬 state를 S3로 마이그레이션할지 물어봅니다.

terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "ap-northeast-2"
}

resource "aws_s3_bucket" "tfstate" {
  bucket = "sparta-ditto-terraform-state"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}