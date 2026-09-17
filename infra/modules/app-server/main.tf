data "aws_region" "current" {}
data "aws_caller_identity" "current" {}

# --- Security Group ---------------------------------------------------------
# 인바운드는 app_port 하나만 연다. 22번 포트는 열지 않고 SSM Session Manager로만
# 접속한다(D-3 §5). 아웃바운드는 ECR·SSM·S3 호출에 필요한 443만 허용해
# 최소화한다(D-3 §5 "네트워크 제한").

resource "aws_security_group" "this" {
  name        = "${var.project_prefix}-${var.name}-sg"
  description = "Test server inbound app_port, outbound HTTPS only"
  vpc_id      = var.vpc_id

  tags = merge(var.tags, {
    Name = "${var.project_prefix}-${var.name}-sg"
  })
}

# SECURITY-EXCEPTION
# 이유: dev 테스트 서버이고 프론트 개발자의 송신 IP가 유동적이라 고정 허용
#       목록을 둘 수 없다. TLS 없이 평문 HTTP로 전체 공개한다.
# 범위: 이 인스턴스의 app_port
# 보완 통제: 테스트 계정과 테스트 데이터만 사용하고, 액세스 토큰 비밀값을
#            로컬·프로덕션과 분리하며 토큰 만료를 짧게 유지한다.
# 소유자: 백엔드팀(tkv00)
# 만료일: 2026-12-31
# 추적: #229 D-3 §5 S-1, #233
#
# AWS 제약: 보안 그룹 규칙의 description은 256자 미만이어야 하고 한글을
# 포함하지 않는 제한된 ASCII 집합만 허용한다. 예외 전문은 위 주석에 두고
# description에는 단문만 남긴다(#268 — 한글 설명으로 apply가 거부되어 발견).
resource "aws_vpc_security_group_ingress_rule" "app_port" {
  security_group_id = aws_security_group.this.id
  description       = "SECURITY-EXCEPTION: public plaintext HTTP to app_port (see #229 D-3 S-1, expires 2026-12-31)"
  from_port         = var.app_port
  to_port           = var.app_port
  ip_protocol       = "tcp"
  cidr_ipv4         = var.allowed_ingress_cidr
}

resource "aws_vpc_security_group_egress_rule" "https" {
  security_group_id = aws_security_group.this.id
  description       = "HTTPS to ECR, SSM and S3"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
  cidr_ipv4         = "0.0.0.0/0"
}

# --- AMI ---------------------------------------------------------------------
# AMI ID를 하드코딩하지 않는다(Issue #229/#233 완료 조건). postgis/postgis
# 이미지가 amd64 전용이라(D-3 §4) x86_64 AMI만 조회한다.

data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  # `al2023-ami-*-x86_64`는 ECS·Neuron 등 파생 변종까지 함께 잡아, most_recent와
  # 합쳐지면 apply 시점마다 다른 이미지가 선택된다. 실제로 루트 스냅샷이
  # 30GiB인 ECS Neuron 변종이 선택되어 8GiB 루트 볼륨과 충돌해 RunInstances가
  # 거부됐다(#268). 표준 기본 이미지 계열만 남겨 루트 스냅샷 크기(8GiB)를
  # 예측 가능하게 유지한다.
  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-kernel-6.1-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# --- IAM: 인스턴스 Role -------------------------------------------------------
# 최소 권한. SSM Session Manager 접속, 이 스택 소유 ECR 리포지토리 pull, 이
# 스택 소유 SSM 파라미터 조회, #63 dev 이미지 버킷 객체 권한만 허용한다
# (D-3 §5 "Runtime role").

data "aws_iam_policy_document" "instance_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "instance" {
  name               = "${var.project_prefix}-${var.name}-instance"
  assume_role_policy = data.aws_iam_policy_document.instance_trust.json

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "ssm_managed_core" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "instance_permissions" {
  # ECR 리포지토리 접두사 인증 토큰 발급은 리소스 수준으로 좁힐 수 없는
  # AWS 제약이다(공식 문서 명시). 이 계정에서 이 토큰만으로는 어떤 리포지토리도
  # pull할 수 없고, 아래 statement가 실제 pull 권한을 이 리포지토리 하나로
  # 한정한다.
  statement {
    sid       = "EcrAuth"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid    = "EcrPull"
    effect = "Allow"
    actions = [
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:BatchCheckLayerAvailability",
    ]
    resources = [var.ecr_repository_arn]
  }

  statement {
    sid       = "ReadOwnSsmParameters"
    effect    = "Allow"
    actions   = ["ssm:GetParameter", "ssm:GetParameters"]
    resources = var.ssm_parameter_arns
  }

  # SSM SecureString은 계정 기본 KMS Key(alias/aws/ssm)를 공유해서 쓴다(D-3
  # §11 비용 판단). Key 자체는 공유해도 SSM이 GetParameter 호출 시 요청에
  # 자동으로 붙이는 encryption context(PARAMETER_ARN)를 조건으로 걸어, 이
  # Role이 복호화할 수 있는 파라미터를 이 스택 소유 경로로만 한정한다(AWS
  # 문서: SSM Parameter Store와 KMS 암호화 컨텍스트).
  statement {
    sid       = "DecryptOwnSsmParameters"
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = [var.ssm_kms_key_arn]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${data.aws_region.current.region}.amazonaws.com"]
    }

    condition {
      test     = "ForAnyValue:StringLike"
      variable = "kms:EncryptionContext:PARAMETER_ARN"
      values   = var.ssm_parameter_arns
    }
  }

  statement {
    sid    = "ImageBucketObjectAccess"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:ListBucket",
    ]
    resources = [
      var.image_bucket_arn,
      "${var.image_bucket_arn}/*",
    ]
  }

  statement {
    sid       = "ImageBucketKmsAccess"
    effect    = "Allow"
    actions   = ["kms:Decrypt", "kms:GenerateDataKey*"]
    resources = [var.image_bucket_kms_key_arn]
  }
}

resource "aws_iam_role_policy" "instance" {
  name   = "${var.project_prefix}-${var.name}-instance-permissions"
  role   = aws_iam_role.instance.id
  policy = data.aws_iam_policy_document.instance_permissions.json
}

resource "aws_iam_instance_profile" "instance" {
  name = "${var.project_prefix}-${var.name}-instance"
  role = aws_iam_role.instance.name

  tags = var.tags
}

# --- EC2 인스턴스 --------------------------------------------------------------

resource "aws_instance" "this" {
  # 상세 모니터링(1분 주기 유료 CloudWatch 메트릭)은 켜지 않는다. 기본
  # 5분 주기 메트릭으로 충분하고, 월 10 USD 예산에서 이 항목을 추가할
  # 여유가 없다(D-3 §2 Observability).
  # 담당 팀: 백엔드팀(tkv00). 재검토: 2027-03-31 또는 프로덕션 전환 시점 중 먼저 오는
  # 시점. 추적: #229 D-3 §2, #233.
  # checkov:skip=CKV_AWS_126:dev 테스트 서버 예산 제약으로 상세 모니터링을 켜지 않는다. D-3 §2 참고.
  ami                    = data.aws_ami.al2023.id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [aws_security_group.this.id]
  iam_instance_profile   = aws_iam_instance_profile.instance.name
  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    aws_region                       = data.aws_region.current.region
    ecr_registry                     = var.ecr_registry
    ecr_repository_name              = var.ecr_repository_name
    app_image_tag                    = var.app_image_tag
    app_port                         = var.app_port
    db_password_parameter_name       = var.db_password_parameter_name
    auth_token_secret_parameter_name = var.auth_token_secret_parameter_name
    media_bucket_name                = var.media_bucket_name
    spring_profiles_active           = var.spring_profiles_active
    compose_version                  = var.compose_version
  })
  user_data_replace_on_change = false
  # t3 계열을 포함한 현재 세대 인스턴스는 기본으로 EBS 최적화가 적용되지만
  # 정적 검사가 명시적 선언을 요구해 값을 그대로 적는다. 추가 비용은 없다.
  ebs_optimized = true

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  root_block_device {
    volume_size           = var.root_volume_size
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  tags = merge(var.tags, {
    Name = "${var.project_prefix}-${var.name}"
  })

  lifecycle {
    # Amazon Linux 2023 AMI는 정기적으로 새 버전이 나온다. AMI 변경마다
    # 인스턴스를 교체하면 데이터 볼륨 재부착과 재기동이 필요해 의도치 않은
    # 다운타임이 생긴다. AMI 교체는 사람이 의도적으로 수행한다(D-3 §7
    # Patching).
    ignore_changes = [ami]
  }
}

# --- 데이터 EBS 볼륨 -----------------------------------------------------------
# PostGIS 데이터를 인스턴스 수명주기와 분리해 보관한다. 인스턴스를 재생성해도
# 데이터 볼륨은 남는다.

resource "aws_ebs_volume" "data" {
  # 전용 Customer-managed KMS Key는 쓰지 않는다. CMK는 사용량과 무관하게
  # 월 1 USD 고정 비용이 발생하고, 이미 4.5%뿐인 예산 여유(D-3 §6)를
  # 넘어선다. 기본 AWS 관리형 EBS 암호화 키(alias/aws/ebs)로 암호화 요건은
  # 충족한다.
  # 담당 팀: 백엔드팀(tkv00). 재검토: 2027-03-31 또는 프로덕션 전환 시점 중 먼저 오는
  # 시점. 추적: #229 D-3 §6, #233.
  # checkov:skip=CKV_AWS_189:dev 테스트 서버 예산 제약으로 전용 CMK 대신 기본 AWS 관리형 키를 쓴다. D-3 §6 참고.
  availability_zone = aws_instance.this.availability_zone
  size              = var.data_volume_size
  type              = "gp3"
  encrypted         = true

  tags = merge(var.tags, {
    Name = "${var.project_prefix}-${var.name}-data"
  })

  lifecycle {
    # 테스트 데이터라도 실수로 인한 볼륨 삭제(config 오탈자, apply 대상 착오)를
    # Terraform 단계에서 막는다. 의도적 삭제는 이 lifecycle 블록을 지우고
    # 별도 PR로 진행한다(D-3 §5 S-8).
    prevent_destroy = true
  }
}

resource "aws_volume_attachment" "data" {
  # AWS 제약: Nitro 기반 인스턴스(t3 계열 포함)는 EBS 볼륨을 NVMe 디바이스로
  # 노출한다. 다만 Amazon Linux 2023은 이 device_name을 요청 시 지정한 이름의
  # /dev/xvdf 심링크로 되돌려주는 udev 규칙을 포함하고 있어(AWS 공식 문서:
  # "Device names on Linux instances"), user_data에서 이 경로를 그대로 참조할
  # 수 있다.
  device_name = "/dev/xvdf"
  volume_id   = aws_ebs_volume.data.id
  instance_id = aws_instance.this.id
}

# --- Elastic IP ----------------------------------------------------------------

resource "aws_eip" "this" {
  domain = "vpc"

  tags = merge(var.tags, {
    Name = "${var.project_prefix}-${var.name}-eip"
  })

  lifecycle {
    # 프론트엔드가 이 고정 IP로 서버에 접근한다. Terraform이 속성 변경으로
    # EIP를 재생성하면 프론트 설정을 전면 갱신해야 하므로(D-3 §8 위험표),
    # 의도치 않은 재생성을 막는다.
    prevent_destroy = true
  }
}

resource "aws_eip_association" "this" {
  instance_id   = aws_instance.this.id
  allocation_id = aws_eip.this.id
}

# --- 자동 정지 가드레일 (EventBridge Scheduler) ---------------------------------
# 예산 여유가 4.5%뿐이라(D-3 §6) 인스턴스를 끄지 않으면 상시 구동 비용(월
# 24.37 USD)으로 예산을 2.4배 초과한다. 결정 항목 H-1 승인(#233)에 따라
# 자동 정지만 구현하고 자동 시작은 두지 않는다(수동 시작).

data "aws_iam_policy_document" "scheduler_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }

    # 다른 계정의 Scheduler가 이 Role을 가로채 assume하지 못하도록 호출
    # 주체를 이 계정으로 한정한다(AWS 문서: 교차 서비스 impersonation 방지
    # 권장 패턴).
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "scheduler" {
  count              = var.enable_auto_stop ? 1 : 0
  name               = "${var.project_prefix}-${var.name}-auto-stop"
  assume_role_policy = data.aws_iam_policy_document.scheduler_trust.json

  tags = var.tags
}

data "aws_iam_policy_document" "scheduler_permissions" {
  statement {
    sid       = "StopThisInstanceOnly"
    effect    = "Allow"
    actions   = ["ec2:StopInstances"]
    resources = ["arn:aws:ec2:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:instance/${aws_instance.this.id}"]
  }
}

resource "aws_iam_role_policy" "scheduler" {
  count  = var.enable_auto_stop ? 1 : 0
  name   = "${var.project_prefix}-${var.name}-auto-stop-permissions"
  role   = aws_iam_role.scheduler[0].id
  policy = data.aws_iam_policy_document.scheduler_permissions.json
}

resource "aws_scheduler_schedule" "auto_stop" {
  count = var.enable_auto_stop ? 1 : 0
  name  = "${var.project_prefix}-${var.name}-auto-stop"
  # 이 스케줄이 담는 값(인스턴스 ID, 정지 명령)은 비밀값이 아니다. 전용 CMK
  # 대신 EventBridge Scheduler 기본 AWS 관리형 키로 암호화해 월 1 USD 고정
  # 비용을 피한다(D-3 §6 예산 여유 4.5% 참고).
  # 담당 팀: 백엔드팀(tkv00). 재검토: 2027-03-31 또는 프로덕션 전환 시점 중 먼저 오는
  # 시점. 추적: #229 D-3 §6, #233.
  # checkov:skip=CKV_AWS_297:비밀값을 담지 않는 스케줄이라 전용 CMK 대신 기본 AWS 관리형 키를 쓴다. D-3 §6 참고.

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression          = var.auto_stop_schedule_expression
  schedule_expression_timezone = var.auto_stop_schedule_timezone

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:stopInstances"
    role_arn = aws_iam_role.scheduler[0].arn

    input = jsonencode({
      InstanceIds = [aws_instance.this.id]
    })
  }
}
