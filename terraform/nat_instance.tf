# fck-nat: NAT Gateway($33/월) 대신 t4g.nano ARM64 기반 NAT Instance($3.8/월) 사용
# GitHub: https://github.com/AndrewGuenther/fck-nat
# Amazon Linux 2023 기반 AMI에 iptables MASQUERADE + ip_forward가 사전 구성되어 있음
#
# AMI 조회 실패 시: https://github.com/AndrewGuenther/fck-nat/releases 에서
# 리전별 최신 AMI ID를 확인하고 fck_nat_ami_override 변수로 직접 지정하세요.

variable "fck_nat_ami_override" {
  description = "fck-nat AMI ID 직접 지정 (비워두면 자동 검색). 자동 검색 실패 시 수동 지정."
  type        = string
  default     = ""
}

data "aws_ami" "fck_nat" {
  count       = var.fck_nat_ami_override == "" ? 1 : 0
  most_recent = true
  owners      = ["568608671756"] # fck-nat 공식 퍼블리셔 AWS 계정

  filter {
    name   = "name"
    values = ["fck-nat-al2023-*-arm64-ebs"]
  }

  filter {
    name   = "state"
    values = ["available"]
  }
}

locals {
  fck_nat_ami_id = var.fck_nat_ami_override != "" ? var.fck_nat_ami_override : data.aws_ami.fck_nat[0].id
}

resource "aws_instance" "nat" {
  ami           = local.fck_nat_ami_id
  instance_type = "t4g.nano" # ARM64 전용 인스턴스. t3.nano보다 저렴하고 fck-nat AMI와 아키텍처 일치

  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.nat.id]
  key_name               = var.key_pair_name
  iam_instance_profile   = aws_iam_instance_profile.nat.name

  # NAT 동작의 핵심: 자신 앞으로 오지 않은 패킷(= 프라이빗 서브넷 트래픽)도 버리지 않고 전달
  source_dest_check = false

  tags = { Name = "${var.project_name}-nat" }
}

# Elastic IP: 재시작해도 퍼블릭 IP 고정 (프라이빗 서브넷의 아웃바운드 출발지 IP)
resource "aws_eip" "nat" {
  instance   = aws_instance.nat.id
  domain     = "vpc"
  depends_on = [aws_internet_gateway.main]

  tags = { Name = "${var.project_name}-nat-eip" }
}