data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

# ─── 앱 서버 IAM (ECR Pull + S3 미디어 접근) ──────────────────────────────────

resource "aws_iam_role" "app" {
  name               = "${var.project_name}-app-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

resource "aws_iam_role_policy_attachment" "app_ecr" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy_attachment" "app_ssm" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# feed-service: Presigned URL 발급(PutObject), 객체 존재 확인(HeadObject), CloudFront 무효화 등
resource "aws_iam_policy" "app_s3" {
  name        = "${var.project_name}-app-s3-policy"
  description = "앱 서버의 미디어 S3 버킷 접근 권한"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "MediaBucketObjectAccess"
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:HeadObject",
          "s3:DeleteObject",
        ]
        Resource = "arn:aws:s3:::${var.s3_media_bucket_name}/*"
      },
      {
        Sid      = "MediaBucketListAccess"
        Effect   = "Allow"
        Action   = ["s3:ListBucket"]
        Resource = "arn:aws:s3:::${var.s3_media_bucket_name}"
      },
    ]
  })
}

resource "aws_iam_role_policy_attachment" "app_s3" {
  role       = aws_iam_role.app.name
  policy_arn = aws_iam_policy.app_s3.arn
}

resource "aws_iam_instance_profile" "app" {
  name = "${var.project_name}-app-profile"
  role = aws_iam_role.app.name
}

# ─── NAT Instance IAM (SSM 접근 + 최소 권한) ──────────────────────────────────
# SSH 없이 SSM Session Manager로 NAT 인스턴스 접근 가능

resource "aws_iam_role" "nat" {
  name               = "${var.project_name}-nat-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

resource "aws_iam_role_policy_attachment" "nat_ssm" {
  role       = aws_iam_role.nat.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "nat" {
  name = "${var.project_name}-nat-profile"
  role = aws_iam_role.nat.name
}

# ─── DB 서버 IAM (SSM만 허용, ECR 불필요: Docker Hub 공식 이미지 사용) ─────────

resource "aws_iam_role" "db" {
  name               = "${var.project_name}-db-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

resource "aws_iam_role_policy_attachment" "db_ssm" {
  role       = aws_iam_role.db.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "db" {
  name = "${var.project_name}-db-profile"
  role = aws_iam_role.db.name
}

# ─── 모니터링 서버 IAM (SSM 접근) ─────────────────────────────────────────────

resource "aws_iam_role" "monitoring" {
  name               = "${var.project_name}-monitoring-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

resource "aws_iam_role_policy_attachment" "monitoring_ssm" {
  role       = aws_iam_role.monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "monitoring" {
  name = "${var.project_name}-monitoring-profile"
  role = aws_iam_role.monitoring.name
}