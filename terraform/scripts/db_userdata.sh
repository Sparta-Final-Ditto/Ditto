#!/bin/bash
# DB 서버 초기화 스크립트 (프라이빗 서브넷, NAT Instance 경유로 인터넷 접근)
# PostgreSQL + Redis + MongoDB + Kafka 를 Docker Hub 공식 이미지로 실행
# $${db_username}, $${db_password}, $${mongo_username}, $${mongo_password}, $${init_db_sql}
# 은 Terraform templatefile이 실제 값으로 치환함 ($$는 Terraform 보간을 막는 이스케이프)
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

# ─── DB 데이터 디렉토리 생성 ──────────────────────────────────────────────────
mkdir -p /data/postgresql
mkdir -p /data/redis
mkdir -p /data/mongodb
mkdir -p /data/kafka

chown -R ubuntu:ubuntu /data

# ─── 자신의 프라이빗 IP 조회 (Kafka advertised.listeners 용, IMDSv2) ──────────
IMDS_TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" \
  -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
DB_PRIVATE_IP=$(curl -s -H "X-aws-ec2-metadata-token: $IMDS_TOKEN" \
  http://169.254.169.254/latest/meta-data/local-ipv4)

# ─── PostgreSQL 초기화 스크립트 (서비스별 DB 생성 + pgvector 확장) ────────────
# 내용은 repo의 scripts/init-db.sh 를 Terraform이 그대로 읽어 주입한 것 (단일 소스)
mkdir -p /opt/ditto-db
chown ubuntu:ubuntu /opt/ditto-db

cat > /opt/ditto-db/init-db.sh << 'INITDBEOF'
${init_db_sql}
INITDBEOF

chmod +x /opt/ditto-db/init-db.sh

# ─── docker-compose.yml 생성 ──────────────────────────────────────────────────
cat > /opt/ditto-db/docker-compose.yml << 'COMPOSEEOF'
services:
  postgresql:
    image: pgvector/pgvector:pg15
    container_name: postgresql
    environment:
      POSTGRES_USER: ${db_username}
      POSTGRES_PASSWORD: ${db_password}
      POSTGRES_DB: ditto_chat
    command: postgres -c max_connections=200 -c shared_buffers=256MB
    volumes:
      - /data/postgresql:/var/lib/postgresql/data
      - /opt/ditto-db/init-db.sh:/docker-entrypoint-initdb.d/init-db.sh
    ports:
      - "5432:5432"
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${db_username}"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: redis
    command: >
      redis-server
      --maxmemory 512mb
      --maxmemory-policy allkeys-lru
    volumes:
      - /data/redis:/data
    ports:
      - "6379:6379"
    restart: unless-stopped

  mongodb:
    image: mongo:7
    container_name: mongodb
    environment:
      MONGO_INITDB_ROOT_USERNAME: ${mongo_username}
      MONGO_INITDB_ROOT_PASSWORD: ${mongo_password}
      MONGO_INITDB_DATABASE: ditto_chat
    volumes:
      - /data/mongodb:/data/db
    ports:
      - "27017:27017"
    restart: unless-stopped

  kafka:
    image: apache/kafka:3.7.0
    container_name: kafka
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://DB_PRIVATE_IP_PLACEHOLDER:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    volumes:
      - /data/kafka:/var/lib/kafka/data
    ports:
      - "9092:9092"
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list"]
      interval: 15s
      timeout: 10s
      retries: 10

  node-exporter:
    image: prom/node-exporter:latest
    container_name: node-exporter
    command:
      - '--path.rootfs=/host'
    network_mode: host
    pid: host
    volumes:
      - '/:/host:ro,rslave'
    restart: unless-stopped
COMPOSEEOF

# Kafka advertised listener에 부팅 시점에 조회한 실제 프라이빗 IP 주입
sed -i "s/DB_PRIVATE_IP_PLACEHOLDER/$DB_PRIVATE_IP/" /opt/ditto-db/docker-compose.yml

chown -R ubuntu:ubuntu /opt/ditto-db

# ─── 스택 시작 ────────────────────────────────────────────────────────────────
cd /opt/ditto-db
docker compose up -d

echo "=== DB 서버 초기화 완료 ==="
echo "PostgreSQL: ditto_chat(기본) + init-db.sh로 ditto_user/feed/match/notification/embedding/assistant 자동 생성"
echo "Kafka advertised listener: $DB_PRIVATE_IP:9092"