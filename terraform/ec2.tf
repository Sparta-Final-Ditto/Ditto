data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical (Ubuntu 공식)

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

locals {
  ecr_registry = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
}

# ─── 앱 서버 (퍼블릭 서브넷) ──────────────────────────────────────────────────
# NGINX (API Gateway) + 전체 Spring Boot 서비스 + Python embedding-service
# 이미지는 ECR에서 pull, docker compose로 실행

resource "aws_instance" "app" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.app_instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.app.id]
  key_name               = var.key_pair_name
  iam_instance_profile   = aws_iam_instance_profile.app.name

  # ami/user_data는 다른 리소스(monitoring 등)의 -target apply에 의존성 때문에 딸려 들어와
  # 운영 중인 이 인스턴스가 실수로 재생성/재부팅되지 않도록 보호. 의도적으로 반영하고 싶을 때만
  # 이 lifecycle 블록을 잠깐 제거하고 apply할 것.
  lifecycle {
    ignore_changes = [ami, user_data]
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 30
    delete_on_termination = true
    encrypted             = true
  }

  user_data = base64encode(templatefile("${path.module}/scripts/app_userdata.sh", {
    aws_region   = var.aws_region
    ecr_registry = local.ecr_registry
    project_name = var.project_name
  }))

  tags = { Name = "${var.project_name}-app" }
}

resource "aws_eip" "app" {
  instance   = aws_instance.app.id
  domain     = "vpc"
  depends_on = [aws_internet_gateway.main]

  tags = { Name = "${var.project_name}-app-eip" }
}

# ─── DB 서버 (프라이빗 서브넷) ────────────────────────────────────────────────
# PostgreSQL + Redis + MongoDB + Kafka (docker compose)
# 인터넷 아웃바운드: NAT Instance 경유 (Docker Hub pull 용)
# 관리자 접근: SSM Session Manager (SSH 인바운드 불필요)

resource "aws_instance" "db" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.db_instance_type
  subnet_id              = aws_subnet.private[0].id
  private_ip             = var.db_private_ip
  vpc_security_group_ids = [aws_security_group.db.id]
  key_name               = var.key_pair_name
  iam_instance_profile   = aws_iam_instance_profile.db.name

  # user_data 변경 시 실제로 재생성되도록 강제 (기본값은 in-place 업데이트인데,
  # cloud-init은 최초 부팅에만 user_data를 실행하므로 재생성 없이는 변경 사항이 반영되지 않음)
  user_data_replace_on_change = true

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 50 # DB 데이터 볼륨 (PostgreSQL + MongoDB)
    delete_on_termination = true
    encrypted             = true
  }

  user_data = base64encode(templatefile("${path.module}/scripts/db_userdata.sh", {
    db_username    = var.db_username
    db_password    = var.db_password
    mongo_username = var.mongo_username
    mongo_password = var.mongo_password
    init_db_sql    = file("${path.module}/../scripts/init-db.sh")
  }))
  depends_on = [aws_instance.nat, aws_route_table_association.private]

  tags = { Name = "${var.project_name}-db" }
}

# ─── 모니터링 서버 (퍼블릭 서브넷) ───────────────────────────────────────────
# Prometheus + Grafana
# 퍼블릭 배치 이유: 팀원이 브라우저로 Grafana 대시보드 직접 접근 필요
# Prometheus는 VPC 내부 사설 IP로 프라이빗 서브넷(DB) 메트릭도 스크래핑 가능

resource "aws_instance" "monitoring" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.monitoring_instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.monitoring.id]
  key_name               = var.key_pair_name
  iam_instance_profile   = aws_iam_instance_profile.monitoring.name

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 20 # Prometheus TSDB 15일치 데이터 보관용
    delete_on_termination = true
    encrypted             = true
  }

  user_data = base64encode(templatefile("${path.module}/scripts/monitoring_userdata.sh", {
    app_private_ip = aws_instance.app.private_ip
    db_private_ip  = aws_instance.db.private_ip
  }))

  tags = { Name = "${var.project_name}-monitoring" }
}

resource "aws_eip" "monitoring" {
  instance   = aws_instance.monitoring.id
  domain     = "vpc"
  depends_on = [aws_internet_gateway.main]

  tags = { Name = "${var.project_name}-monitoring-eip" }
}