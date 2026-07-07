#!/bin/bash
# DB 서버 초기화 스크립트 (프라이빗 서브넷, NAT Instance 경유로 인터넷 접근)
# PostgreSQL + Redis + MongoDB + Kafka 를 Docker Hub 공식 이미지로 실행
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
mkdir -p /data/zookeeper

chown -R ubuntu:ubuntu /data

# ─── docker-compose.yml 생성 ──────────────────────────────────────────────────
mkdir -p /opt/ditto-db
chown ubuntu:ubuntu /opt/ditto-db

cat > /opt/ditto-db/docker-compose.yml << 'COMPOSEEOF'
services:
  postgresql:
    image: pgvector/pgvector:pg16
    container_name: postgresql
    environment:
      POSTGRES_USER: ditto
      POSTGRES_PASSWORD: change_me_in_prod
      POSTGRES_DB: ditto
    volumes:
      - /data/postgresql:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ditto"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: redis
    command: >
      redis-server
      --requirepass change_me_in_prod
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
      MONGO_INITDB_ROOT_USERNAME: ditto
      MONGO_INITDB_ROOT_PASSWORD: change_me_in_prod
      MONGO_INITDB_DATABASE: ditto_chat
    volumes:
      - /data/mongodb:/data/db
    ports:
      - "27017:27017"
    restart: unless-stopped

  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    volumes:
      - /data/zookeeper:/var/lib/zookeeper/data
    restart: unless-stopped

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    container_name: kafka
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://REPLACE_WITH_DB_PRIVATE_IP:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    volumes:
      - /data/kafka:/var/lib/kafka/data
    ports:
      - "9092:9092"
    restart: unless-stopped

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

chown ubuntu:ubuntu /opt/ditto-db/docker-compose.yml

echo "=== DB 서버 초기화 완료 ==="
echo "1. /opt/ditto-db/docker-compose.yml 의 REPLACE_WITH_DB_PRIVATE_IP 를 실제 IP로 교체"
echo "2. 패스워드를 실제 값으로 교체"
echo "3. cd /opt/ditto-db && docker compose up -d"