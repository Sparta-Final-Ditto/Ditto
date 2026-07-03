# ─── NAT Instance ─────────────────────────────────────────────────────────────

resource "aws_security_group" "nat" {
  name        = "${var.project_name}-nat-sg"
  description = "NAT Instance: allow private subnet outbound traffic"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "All traffic from private subnets (NAT role)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = var.private_subnet_cidrs
  }

  ingress {
    description = "Admin SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.admin_cidr_blocks
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-nat-sg" }
}

# ─── 앱 서버 (NGINX + 전체 Spring Boot 서비스) ────────────────────────────────

resource "aws_security_group" "app" {
  name        = "${var.project_name}-app-sg"
  description = "App server: HTTP/HTTPS inbound, service ports allowed within VPC"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Prometheus가 Spring Boot Actuator 메트릭 수집 (각 서비스 포트)
  ingress {
    description     = "Prometheus metrics scraping (Actuator)"
    from_port       = 8080
    to_port         = 8090
    protocol        = "tcp"
    security_groups = [aws_security_group.monitoring.id]
  }

  # node_exporter (OS 메트릭)
  ingress {
    description     = "node_exporter OS metrics"
    from_port       = 9100
    to_port         = 9100
    protocol        = "tcp"
    security_groups = [aws_security_group.monitoring.id]
  }

  ingress {
    description = "Admin SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.admin_cidr_blocks
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-app-sg" }
}

# ─── DB 서버 (PostgreSQL / Redis / MongoDB / Kafka) ───────────────────────────

resource "aws_security_group" "db" {
  name        = "${var.project_name}-db-sg"
  description = "DB server: allow access only from app server SG"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  ingress {
    description     = "MongoDB"
    from_port       = 27017
    to_port         = 27017
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  ingress {
    description     = "Redis"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  # Kafka 클라이언트 통신 (PLAINTEXT)
  ingress {
    description     = "Kafka broker"
    from_port       = 9092
    to_port         = 9092
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  # Kafka KRaft 컨트롤러 (Zookeeper 없는 모드 사용 시)
  ingress {
    description     = "Kafka KRaft Controller"
    from_port       = 9093
    to_port         = 9093
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  # node_exporter + DB exporter (Prometheus 수집용)
  ingress {
    description     = "Prometheus exporter"
    from_port       = 9100
    to_port         = 9100
    protocol        = "tcp"
    security_groups = [aws_security_group.monitoring.id]
  }

  # SSM Session Manager를 통한 접근은 아웃바운드만 있으면 되므로 SSH 인바운드 불필요
  # 긴급 접근이 필요한 경우에만 아래 룰을 임시 활성화
  # ingress {
  #   description = "Admin SSH (emergency)"
  #   from_port   = 22
  #   to_port     = 22
  #   protocol    = "tcp"
  #   cidr_blocks = var.admin_cidr_blocks
  # }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-db-sg" }
}

# ─── 모니터링 서버 (Prometheus + Grafana) ─────────────────────────────────────
# 퍼블릭 서브넷에 배치: Grafana 대시보드를 브라우저로 직접 접근하기 위함
# Prometheus는 VPC 내부 사설 IP로 프라이빗 서브넷 DB 메트릭도 수집 가능

resource "aws_security_group" "monitoring" {
  name        = "${var.project_name}-monitoring-sg"
  description = "Monitoring server: allow Grafana/Prometheus UI only from team IPs"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "Grafana dashboard"
    from_port   = 3000
    to_port     = 3000
    protocol    = "tcp"
    cidr_blocks = var.admin_cidr_blocks
  }

  ingress {
    description = "Prometheus UI"
    from_port   = 9090
    to_port     = 9090
    protocol    = "tcp"
    cidr_blocks = var.admin_cidr_blocks
  }

  ingress {
    description = "Admin SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.admin_cidr_blocks
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-monitoring-sg" }
}