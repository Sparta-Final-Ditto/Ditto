#!/bin/bash
# 앱 서버 초기화 스크립트 (Terraform templatefile 처리 후 cloud-init 실행)
# ${aws_region}, ${ecr_registry}, ${project_name} 은 Terraform이 실제 값으로 치환함
set -euo pipefail

# ─── 시스템 업데이트 ──────────────────────────────────────────────────────────
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl gnupg unzip

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

# ─── AWS CLI v2 설치 ──────────────────────────────────────────────────────────
curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
unzip -q /tmp/awscliv2.zip -d /tmp
/tmp/aws/install
rm -rf /tmp/awscliv2.zip /tmp/aws

# ─── ECR 로그인 (IAM 인스턴스 프로파일로 자격증명 자동 취득) ────────────────────
aws ecr get-login-password --region ${aws_region} \
  | docker login --username AWS --password-stdin ${ecr_registry}

# ECR 토큰은 12시간 유효 → 6시간마다 갱신
cat > /etc/cron.d/ecr-login << 'CRONEOF'
0 */6 * * * root /usr/local/bin/aws ecr get-login-password --region ${aws_region} | docker login --username AWS --password-stdin ${ecr_registry} >> /var/log/ecr-login.log 2>&1
CRONEOF

# ─── 애플리케이션 디렉토리 ────────────────────────────────────────────────────
# CD 파이프라인(cd.yml)이 SSH 접속 후 'cd ~/ditto'로 이동하므로 경로를 맞춘다
mkdir -p /home/ubuntu/ditto
chown ubuntu:ubuntu /home/ubuntu/ditto

cat >> /etc/environment << 'ENVEOF'
AWS_REGION=${aws_region}
ECR_REGISTRY=${ecr_registry}
ENVEOF

echo "=== 앱 서버 초기화 완료 ==="
echo "docker-compose.yml 을 /home/ubuntu/ditto/ 에 배치 후 'docker compose up -d' 실행"