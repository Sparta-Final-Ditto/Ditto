#!/bin/bash
# 모니터링 서버 초기화 스크립트 (Prometheus + Grafana)
# ${app_private_ip}, ${db_private_ip} 는 Terraform templatefile 이 실제 IP로 치환
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl gnupg

# ─── Docker CE 설치 ───────────────────────────────────────────────────────────
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | tee /etc/apt/sources.list.d/docker.list > /dev/null

apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable docker
systemctl start docker
usermod -aG docker ubuntu

# ─── 설정 디렉토리 ───────────────────────────────────────────────────────────
mkdir -p /opt/monitoring/prometheus
mkdir -p /opt/monitoring/grafana/provisioning/datasources
chown -R ubuntu:ubuntu /opt/monitoring

# ─── Prometheus 설정 ──────────────────────────────────────────────────────────
# Terraform이 ${app_private_ip}, ${db_private_ip} 를 실제 IP로 치환 후 스크립트를 실행
cat > /opt/monitoring/prometheus/prometheus.yml << 'PROMEOF'
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    project: 'ditto'
    env: 'prod'

scrape_configs:
  # Spring Boot Actuator 메트릭 (각 서비스 포트)
  - job_name: 'app-services'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - '${app_private_ip}:8081'  # user-service
          - '${app_private_ip}:8082'  # feed-service
          - '${app_private_ip}:8083'  # match-service
          - '${app_private_ip}:8084'  # chat-service
          - '${app_private_ip}:8085'  # notification-service
        labels:
          instance_group: 'spring-boot'

  # Python embedding-service (FastAPI + prometheus_fastapi_instrumentator)
  - job_name: 'embedding-service'
    metrics_path: '/metrics'
    static_configs:
      - targets: ['${app_private_ip}:8090']

  # OS 메트릭 - 앱 서버
  - job_name: 'node-app'
    static_configs:
      - targets: ['${app_private_ip}:9100']
        labels:
          instance: 'app-server'

  # OS 메트릭 - DB 서버
  - job_name: 'node-db'
    static_configs:
      - targets: ['${db_private_ip}:9100']
        labels:
          instance: 'db-server'
PROMEOF

# ─── Grafana Datasource 자동 프로비저닝 ───────────────────────────────────────
cat > /opt/monitoring/grafana/provisioning/datasources/prometheus.yml << 'GRAFANAEOF'
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    url: http://prometheus:9090
    isDefault: true
    access: proxy
GRAFANAEOF

# ─── docker-compose.yml ───────────────────────────────────────────────────────
cat > /opt/monitoring/docker-compose.yml << 'COMPOSEEOF'
services:
  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=15d'
      - '--web.enable-lifecycle'
    ports:
      - "9090:9090"
    restart: unless-stopped

  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    volumes:
      - grafana_data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
      - GF_SERVER_ROOT_URL=http://localhost:3000
    ports:
      - "3000:3000"
    restart: unless-stopped
    depends_on:
      - prometheus

volumes:
  prometheus_data:
  grafana_data:
COMPOSEEOF

chown -R ubuntu:ubuntu /opt/monitoring

# ─── 스택 시작 ────────────────────────────────────────────────────────────────
cd /opt/monitoring
docker compose up -d

echo "=== 모니터링 서버 초기화 완료 ==="
echo "Grafana: http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):3000"
echo "초기 로그인: admin / admin → 최초 로그인 후 반드시 패스워드 변경"