# GitHub Actions OIDC Provider
#
# 장기 AWS Access Key를 GitHub Secret으로 저장하지 않기 위해 OIDC 단기 자격
# 증명만 사용한다(AGENTS.md 4.9). Plan 역할과 Apply 역할을 분리해 PR에서
# 실행되는 `terraform plan`과, 보호된 Environment에서만 실행되는
# `terraform apply`의 권한을 다르게 제한한다.

variable "github_oidc_thumbprints" {
  description = "GitHub Actions OIDC(token.actions.githubusercontent.com) 루트 CA thumbprint 목록. AWS/GitHub가 공개한 값이며 비밀값이 아니다."
  type        = list(string)
  # TODO(#63, 2026-11-05): 실제 apply 전에 AWS의 GitHub OIDC 연동 가이드에서
  # 현재 thumbprint를 재확인한다. CA가 교체되면 이 목록을 갱신해야 신뢰
  # 관계가 끊어지지 않는다.
  default = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]
}

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = var.github_oidc_thumbprints

  tags = var.tags
}

# 프론트 테스트 서버 스택(D-3)의 SSM SecureString 파라미터가 쓰는 계정 기본
# 키. infra-apply 역할의 kms:Decrypt 범위를 이 Key로 한정하는 데 참조한다.
data "aws_kms_alias" "ssm_default" {
  name = "alias/aws/ssm"
}

# --- 프론트 테스트 서버 스택(D-3, #229/#233) 공통 권한 -------------------------
# infra-apply(oidc.tf)와 infra-deployer(deployer.tf) 모두 이 스택을 apply할
# 수 있어야 해서 문서를 만들어 양쪽에 별도 인라인 정책으로 붙인다. 리소스
# ARN은 policy_sentry(AWS 서비스 권한 부여 참조 데이터 기반)로 각 action이
# 실제 지원하는 리소스 유형을 확인해 채웠다 — 대부분의 EC2 action은 "*"
# 없이도 리소스 수준으로 좁힐 수 있어, 이전 초안의 "AWS 제약" 주석은
# 부정확했다(PR #232 이후 자체 재검토).
#
# 네트워크(VPC·서브넷·IGW·라우트 테이블·보안 그룹), 태그(Tag*, ARN을
# 8개씩 나열해 유독 크다), 컴퓨팅·부속 리소스(인스턴스·볼륨·EIP·ECR·
# SSM·IAM·Scheduler) 세 문서로 나눈다. 합쳐서 하나의 인라인 정책으로
# 두면 AWS IAM의 Role당 인라인 정책 크기 한도(10,240바이트)를 초과해
# 실제 apply가 실패한다(#243) — network 문서 하나만으로도 그 한도를
# 넘어 세 개로 나눴다.
data "aws_iam_policy_document" "test_server_shared_permissions_network" {
  # apply도 내부적으로 refresh(조회)를 수행하므로 plan 역할과 동일한 조회
  # 권한이 필요하다. 이 스택이 실제로 만드는 리소스 유형의 조회 action만
  # 나열한다("ec2:Describe*" 와일드카드는 Client VPN·Spot Fleet 등 무관한
  # 하위 action까지 포함해 넓게 부여하는 문제가 있고, 그 중 일부(예:
  # DescribeInstanceAttribute)는 리소스 수준으로 좁힐 수 있는데도 "*"로
  # 남게 된다).
  statement {
    sid    = "ReadTestServerNetworkAndCompute"
    effect = "Allow"
    actions = [
      "ec2:DescribeVpcs",
      "ec2:DescribeSubnets",
      "ec2:DescribeInternetGateways",
      "ec2:DescribeRouteTables",
      "ec2:DescribeSecurityGroups",
      "ec2:DescribeSecurityGroupRules",
      "ec2:DescribeImages",
      "ec2:DescribeInstances",
      "ec2:DescribeInstanceTypes",
      "ec2:DescribeInstanceCreditSpecifications",
      "ec2:DescribeVolumes",
      "ec2:DescribeNetworkInterfaces",
      "ec2:DescribeAddresses",
      "ec2:DescribeAddressesAttribute",
      "ec2:DescribeTags",
      "ec2:DescribeAvailabilityZones",
      "ec2:DescribeAccountAttributes",
      "ec2:DescribeNetworkAcls",
    ]
    resources = ["*"]
  }

  # aws_instance와 aws_vpc의 refresh가 각각 DescribeInstanceAttribute와
  # DescribeVpcAttribute를 호출한다(AWS Provider 6.57.1, PR #236 리뷰
  # 확인). 이 두 action은 리소스 수준으로 좁힐 수 있어 위 목록과 분리한다.
  statement {
    sid    = "ReadTestServerAttributeDescriptions"
    effect = "Allow"
    actions = [
      "ec2:DescribeInstanceAttribute",
      "ec2:DescribeVpcAttribute",
    ]
    resources = [
      "arn:aws:ec2:*:*:instance/*",
      "arn:aws:ec2:*:*:vpc/*",
    ]
  }

  statement {
    sid       = "CreateTestServerVpc"
    effect    = "Allow"
    actions   = ["ec2:CreateVpc"]
    resources = [local.test_server_vpc_arn]

    condition {
      test     = "StringLike"
      variable = "aws:RequestTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid       = "ManageTestServerVpc"
    effect    = "Allow"
    actions   = ["ec2:DeleteVpc", "ec2:ModifyVpcAttribute"]
    resources = [local.test_server_vpc_arn]

    # test_server_vpc_arn은 리전·계정만 한정하고 리소스 ID는 와일드카드다.
    # 이 계정의 다른 VPC까지 관리하지 못하도록 태그로 이 프로젝트 소유
    # 리소스로 한정한다(PR #236 리뷰).
    condition {
      test     = "StringLike"
      variable = "aws:ResourceTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid       = "CreateTestServerSubnet"
    effect    = "Allow"
    actions   = ["ec2:CreateSubnet"]
    resources = [local.test_server_subnet_arn, local.test_server_vpc_arn]

    condition {
      test     = "StringLike"
      variable = "aws:RequestTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid       = "ManageTestServerSubnet"
    effect    = "Allow"
    actions   = ["ec2:DeleteSubnet", "ec2:ModifySubnetAttribute"]
    resources = [local.test_server_subnet_arn]

    condition {
      test     = "StringLike"
      variable = "aws:ResourceTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid       = "CreateTestServerInternetGateway"
    effect    = "Allow"
    actions   = ["ec2:CreateInternetGateway"]
    resources = [local.test_server_internet_gateway_arn]

    condition {
      test     = "StringLike"
      variable = "aws:RequestTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid    = "ManageTestServerInternetGateway"
    effect = "Allow"
    actions = [
      "ec2:DeleteInternetGateway",
      "ec2:AttachInternetGateway",
      "ec2:DetachInternetGateway",
    ]
    resources = [local.test_server_internet_gateway_arn, local.test_server_vpc_arn]

    condition {
      test     = "StringLike"
      variable = "aws:ResourceTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid       = "CreateTestServerRouteTable"
    effect    = "Allow"
    actions   = ["ec2:CreateRouteTable"]
    resources = [local.test_server_route_table_arn, local.test_server_vpc_arn]

    condition {
      test     = "StringLike"
      variable = "aws:RequestTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid    = "ManageTestServerRouteTable"
    effect = "Allow"
    actions = [
      "ec2:DeleteRouteTable",
      "ec2:CreateRoute",
      "ec2:DeleteRoute",
      "ec2:ReplaceRoute",
    ]
    resources = [local.test_server_route_table_arn]

    condition {
      test     = "StringLike"
      variable = "aws:ResourceTag/Project"
      values   = ["qello"]
    }
  }

  # 서브넷-라우트 테이블 연결만 다룬다(게이트웨이 라우트 테이블 연결은 이
  # 설계 범위 밖이라 internet-gateway/vpn-gateway 리소스는 포함하지 않는다).
  statement {
    sid    = "ManageTestServerRouteTableAssociation"
    effect = "Allow"
    actions = [
      "ec2:AssociateRouteTable",
      "ec2:DisassociateRouteTable",
      "ec2:ReplaceRouteTableAssociation",
    ]
    resources = [local.test_server_route_table_arn, local.test_server_subnet_arn]

    condition {
      test     = "StringLike"
      variable = "aws:ResourceTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid       = "CreateTestServerSecurityGroup"
    effect    = "Allow"
    actions   = ["ec2:CreateSecurityGroup"]
    resources = [local.test_server_security_group_arn, local.test_server_vpc_arn]

    condition {
      test     = "StringLike"
      variable = "aws:RequestTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid    = "ManageTestServerSecurityGroup"
    effect = "Allow"
    actions = [
      "ec2:DeleteSecurityGroup",
      "ec2:AuthorizeSecurityGroupIngress",
      "ec2:AuthorizeSecurityGroupEgress",
      "ec2:RevokeSecurityGroupIngress",
      "ec2:RevokeSecurityGroupEgress",
      "ec2:UpdateSecurityGroupRuleDescriptionsIngress",
      "ec2:UpdateSecurityGroupRuleDescriptionsEgress",
    ]
    resources = [local.test_server_security_group_arn]

    condition {
      test     = "StringLike"
      variable = "aws:ResourceTag/Project"
      values   = ["qello"]
    }
  }

}

# Tag* statement 두 개가 리소스 ARN을 8개씩 나열해 다른 statement보다
# 훨씬 커서, network 문서에 그대로 두면 그 문서 혼자서도 10,240바이트를
# 넘었다(#243). 별도 문서로 뗀다.
data "aws_iam_policy_document" "test_server_shared_permissions_tags" {
  # CreateTags/DeleteTags는 80개 넘는 리소스 유형을 지원하지만, 이 스택이
  # 실제로 태그를 붙이는 유형만 나열한다. 생성 시점 태깅(Terraform이 리소스
  # 생성 API 호출에 태그를 함께 보내는 경우)과 기존 리소스 재태깅을
  # 분리한다 — 전자는 ec2:CreateAction으로 "생성 호출에 한해서만" 허용해
  # 이미 존재하는 다른 프로젝트 리소스에 태그를 붙이는 데 쓰이지 않게
  # 막는다(PR #236 리뷰).
  statement {
    sid    = "TagTestServerResourcesOnCreate"
    effect = "Allow"
    actions = [
      "ec2:CreateTags",
    ]
    resources = [
      local.test_server_vpc_arn,
      local.test_server_subnet_arn,
      local.test_server_internet_gateway_arn,
      local.test_server_route_table_arn,
      local.test_server_security_group_arn,
      local.test_server_instance_arn,
      local.test_server_volume_arn,
      local.test_server_elastic_ip_arn,
    ]

    condition {
      test     = "StringLike"
      variable = "aws:RequestTag/Project"
      values   = ["qello"]
    }

    condition {
      test     = "StringEquals"
      variable = "ec2:CreateAction"
      values = [
        "CreateVpc", "CreateSubnet", "CreateInternetGateway", "CreateRouteTable",
        "CreateSecurityGroup", "RunInstances", "CreateVolume", "AllocateAddress",
      ]
    }
  }

  statement {
    sid    = "TagTestServerExistingResources"
    effect = "Allow"
    actions = [
      "ec2:CreateTags",
      "ec2:DeleteTags",
    ]
    resources = [
      local.test_server_vpc_arn,
      local.test_server_subnet_arn,
      local.test_server_internet_gateway_arn,
      local.test_server_route_table_arn,
      local.test_server_security_group_arn,
      local.test_server_instance_arn,
      local.test_server_volume_arn,
      local.test_server_elastic_ip_arn,
    ]

    condition {
      test     = "StringLike"
      variable = "aws:ResourceTag/Project"
      values   = ["qello"]
    }

    condition {
      test     = "ForAllValues:StringLike"
      variable = "aws:TagKeys"
      values   = ["Project", "ManagedBy", "Environment", "Name"]
    }
  }
}

data "aws_iam_policy_document" "test_server_shared_permissions_compute" {
  # RunInstances가 실제로 건드리는 리소스 유형(image·instance·network-
  # interface·security-group·subnet·volume)만 나열한다. key-pair, launch-
  # template 등 이 스택이 쓰지 않는 유형은 넣지 않는다.
  statement {
    sid    = "RunTestServerInstance"
    effect = "Allow"
    actions = [
      "ec2:RunInstances",
    ]
    resources = [
      local.test_server_image_arn,
      local.test_server_instance_arn,
      local.test_server_network_interface_arn,
      local.test_server_security_group_arn,
      local.test_server_subnet_arn,
      local.test_server_volume_arn,
    ]

    condition {
      test     = "StringLike"
      variable = "aws:RequestTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid    = "ManageTestServerInstanceLifecycle"
    effect = "Allow"
    actions = [
      "ec2:TerminateInstances",
      "ec2:StopInstances",
      "ec2:StartInstances",
      "ec2:ModifyInstanceAttribute",
      "ec2:ModifyInstanceMetadataOptions",
    ]
    resources = [
      local.test_server_instance_arn,
      local.test_server_security_group_arn,
      local.test_server_volume_arn,
    ]

    condition {
      test     = "StringLike"
      variable = "aws:ResourceTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid       = "CreateTestServerVolume"
    effect    = "Allow"
    actions   = ["ec2:CreateVolume"]
    resources = [local.test_server_volume_arn]

    condition {
      test     = "StringLike"
      variable = "aws:RequestTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid    = "ManageTestServerVolume"
    effect = "Allow"
    actions = [
      "ec2:DeleteVolume",
      "ec2:ModifyVolume",
      "ec2:AttachVolume",
      "ec2:DetachVolume",
    ]
    resources = [local.test_server_volume_arn, local.test_server_instance_arn]

    condition {
      test     = "StringLike"
      variable = "aws:ResourceTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid       = "AllocateTestServerElasticIp"
    effect    = "Allow"
    actions   = ["ec2:AllocateAddress"]
    resources = [local.test_server_elastic_ip_arn]

    condition {
      test     = "StringLike"
      variable = "aws:RequestTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid    = "ManageTestServerElasticIp"
    effect = "Allow"
    actions = [
      "ec2:ReleaseAddress",
      "ec2:AssociateAddress",
      "ec2:DisassociateAddress",
    ]
    resources = [
      local.test_server_elastic_ip_arn,
      local.test_server_instance_arn,
      local.test_server_network_interface_arn,
    ]

    condition {
      test     = "StringLike"
      variable = "aws:ResourceTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid    = "ManageTestServerEcr"
    effect = "Allow"
    actions = [
      "ecr:CreateRepository",
      "ecr:DeleteRepository",
      "ecr:DescribeRepositories",
      "ecr:DescribeImages",
      "ecr:PutLifecyclePolicy",
      "ecr:DeleteLifecyclePolicy",
      "ecr:GetLifecyclePolicy",
      "ecr:PutImageScanningConfiguration",
      "ecr:PutImageTagMutability",
      "ecr:TagResource",
      "ecr:UntagResource",
      "ecr:ListTagsForResource",
    ]
    resources = ["arn:aws:ecr:*:*:repository/${var.project_prefix}-*"]
  }

  # 이 스택 소유 경로로만 한정한다. GetParameter/GetParameters는 apply
  # 자신의 refresh에 필요하지만 값을 반환하므로 plan 역할에는 주지 않는다
  # (PR #232 리뷰 반영, D-3 §5).
  statement {
    sid    = "ManageTestServerSsmParameters"
    effect = "Allow"
    actions = [
      "ssm:PutParameter",
      "ssm:DeleteParameter",
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:DescribeParameters",
      "ssm:AddTagsToResource",
      "ssm:RemoveTagsFromResource",
      "ssm:ListTagsForResource",
    ]
    resources = ["arn:aws:ssm:*:*:parameter/${var.project_prefix}/*"]
  }

  # 알려진 Terraform AWS Provider 제약(hashicorp/terraform-provider-aws#42849):
  # value_wo를 쓰는 SecureString 파라미터도 apply가 내부적으로
  # GetParameter(WithDecryption=true)를 호출해 kms:Decrypt 없이는 apply가
  # 실패한다. 계정 기본 SSM 키(alias/aws/ssm)는 다른 SecureString과
  # 공유되므로, SSM이 요청에 자동으로 붙이는 encryption context로 복호화
  # 대상을 이 스택 소유 파라미터 경로로 한정한다(AWS 문서: SSM Parameter
  # Store와 KMS 암호화 컨텍스트).
  statement {
    sid       = "DecryptTestServerSsmParameters"
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = [data.aws_kms_alias.ssm_default.target_key_arn]

    condition {
      test     = "StringLike"
      variable = "kms:EncryptionContext:PARAMETER_ARN"
      values   = ["arn:aws:ssm:*:*:parameter/${var.project_prefix}/*"]
    }
  }

  statement {
    sid    = "ManageTestServerInstanceProfiles"
    effect = "Allow"
    actions = [
      "iam:CreateInstanceProfile",
      "iam:DeleteInstanceProfile",
      "iam:GetInstanceProfile",
      "iam:TagInstanceProfile",
      "iam:AddRoleToInstanceProfile",
      "iam:RemoveRoleFromInstanceProfile",
    ]
    resources = ["arn:aws:iam::*:instance-profile/${var.project_prefix}-*"]
  }

  # PassRole은 이 스택이 만드는 두 Role(인스턴스 Role, Scheduler Role)로만
  # 한정하고, 각각 실제로 그 Role을 넘겨받는 서비스로 iam:PassedToService를
  # 제한해 다른 서비스가 가로채 assume하지 못하게 한다.
  statement {
    sid       = "PassTestServerInstanceRole"
    effect    = "Allow"
    actions   = ["iam:PassRole"]
    resources = ["arn:aws:iam::*:role/${var.project_prefix}-*-instance"]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ec2.amazonaws.com"]
    }
  }

  statement {
    sid       = "PassTestServerSchedulerRole"
    effect    = "Allow"
    actions   = ["iam:PassRole"]
    resources = ["arn:aws:iam::*:role/${var.project_prefix}-*-auto-stop"]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["scheduler.amazonaws.com"]
    }
  }

  statement {
    sid    = "ManageTestServerScheduler"
    effect = "Allow"
    actions = [
      "scheduler:CreateSchedule",
      "scheduler:UpdateSchedule",
      "scheduler:DeleteSchedule",
      "scheduler:GetSchedule",
      "scheduler:ListSchedules",
      "scheduler:TagResource",
      "scheduler:UntagResource",
      "scheduler:ListTagsForResource",
    ]
    resources = ["arn:aws:scheduler:*:*:schedule/default/${var.project_prefix}-*"]
  }
}

# 이 ARN들은 리소스 "유형"만 한정하고 리전·계정·리소스 ID는 와일드카드다.
# 이 계정 안의 다른 VPC·서브넷 등도 형식상 이 패턴에 일치한다. 실제로 이
# 프로젝트 리소스로만 범위를 좁히는 것은 각 statement에 붙인
# aws:RequestTag/Project(생성 시점)·aws:ResourceTag/Project(기존 리소스)
# 조건이다(PR #236 리뷰 이후 정정).
locals {
  test_server_vpc_arn               = "arn:aws:ec2:*:*:vpc/*"
  test_server_subnet_arn            = "arn:aws:ec2:*:*:subnet/*"
  test_server_internet_gateway_arn  = "arn:aws:ec2:*:*:internet-gateway/*"
  test_server_route_table_arn       = "arn:aws:ec2:*:*:route-table/*"
  test_server_security_group_arn    = "arn:aws:ec2:*:*:security-group/*"
  test_server_instance_arn          = "arn:aws:ec2:*:*:instance/*"
  test_server_volume_arn            = "arn:aws:ec2:*:*:volume/*"
  test_server_elastic_ip_arn        = "arn:aws:ec2:*:*:elastic-ip/*"
  test_server_network_interface_arn = "arn:aws:ec2:*:*:network-interface/*"
  test_server_image_arn             = "arn:aws:ec2:*::image/*"
}

# --- infra-plan role -------------------------------------------------------
# PR에서 실행되는 정적 검사·`terraform plan`이 사용한다. 쓰기 권한은 부여하지
# 않는다.

data "aws_iam_policy_document" "infra_plan_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # 어떤 branch/PR에서든 plan은 실행할 수 있어야 하므로 ref를 제한하지
    # 않고 저장소만 제한한다. 쓰기 권한이 없으므로 위험이 낮다.
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:*"]
    }
  }
}

resource "aws_iam_role" "infra_plan" {
  name                 = "${var.project_prefix}-infra-plan"
  assume_role_policy   = data.aws_iam_policy_document.infra_plan_trust.json
  max_session_duration = 3600

  tags = var.tags
}

data "aws_iam_policy_document" "infra_plan_permissions" {
  statement {
    sid    = "ReadTerraformState"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:ListBucket",
    ]
    resources = [
      aws_s3_bucket.terraform_state.arn,
      "${aws_s3_bucket.terraform_state.arn}/*",
    ]
  }

  statement {
    sid    = "ReadProjectResources"
    effect = "Allow"
    actions = [
      "s3:GetBucket*",
      "s3:ListBucket",
      "s3:GetObject",
      "s3:GetEncryptionConfiguration",
      "s3:GetLifecycleConfiguration",
      "kms:DescribeKey",
      "kms:GetKeyPolicy",
      "kms:GetKeyRotationStatus",
      "iam:GetRole",
      "iam:GetRolePolicy",
      "iam:ListRolePolicies",
      "iam:ListAttachedRolePolicies",
      "iam:GetUser",
      "iam:GetUserPolicy",
      "iam:ListUserPolicies",
      "iam:GetGroup",
      "iam:GetGroupPolicy",
      "iam:ListGroupPolicies",
      "iam:ListGroupsForUser",
    ]
    # plan은 아직 존재하지 않는 리소스(예: 이 스택이 처음 계획하는 dev
    # storage 버킷)도 읽어야 하므로 이름 접두사로만 범위를 좁힌다.
    resources = ["*"]

    condition {
      test     = "StringLike"
      variable = "aws:ResourceTag/Project"
      values   = ["qello"]
    }
  }

  # 프론트 테스트 서버 스택(D-3, #229/#233)이 계획할 EC2·VPC·ECR·SSM·
  # EventBridge Scheduler 리소스를 조회하는 권한. 쓰기 권한은 주지 않는다.
  # apply 역할과 동일한 근거로 와일드카드 대신 curated 목록을 쓴다(위
  # test_server_shared_permissions_network의 동일 이름 statement 주석 참고).
  statement {
    sid    = "ReadTestServerNetworkAndCompute"
    effect = "Allow"
    actions = [
      "ec2:DescribeVpcs",
      "ec2:DescribeSubnets",
      "ec2:DescribeInternetGateways",
      "ec2:DescribeRouteTables",
      "ec2:DescribeSecurityGroups",
      "ec2:DescribeSecurityGroupRules",
      "ec2:DescribeImages",
      "ec2:DescribeInstances",
      "ec2:DescribeInstanceTypes",
      "ec2:DescribeInstanceCreditSpecifications",
      "ec2:DescribeVolumes",
      "ec2:DescribeNetworkInterfaces",
      "ec2:DescribeAddresses",
      "ec2:DescribeAddressesAttribute",
      "ec2:DescribeTags",
      "ec2:DescribeAvailabilityZones",
      "ec2:DescribeAccountAttributes",
      "ec2:DescribeNetworkAcls",
    ]
    resources = ["*"]
  }

  # aws_instance와 aws_vpc의 refresh가 각각 DescribeInstanceAttribute와
  # DescribeVpcAttribute를 호출한다(AWS Provider 6.57.1, PR #236 리뷰
  # 확인). 이 두 action은 리소스 수준으로 좁힐 수 있어 위 목록과 분리한다.
  statement {
    sid    = "ReadTestServerAttributeDescriptions"
    effect = "Allow"
    actions = [
      "ec2:DescribeInstanceAttribute",
      "ec2:DescribeVpcAttribute",
    ]
    resources = [
      "arn:aws:ec2:*:*:instance/*",
      "arn:aws:ec2:*:*:vpc/*",
    ]
  }

  statement {
    sid    = "ReadTestServerEcr"
    effect = "Allow"
    actions = [
      "ecr:DescribeRepositories",
      "ecr:DescribeImages",
      "ecr:GetRepositoryPolicy",
      "ecr:GetLifecyclePolicy",
      "ecr:ListTagsForResource",
    ]
    resources = ["arn:aws:ecr:*:*:repository/${var.project_prefix}-*"]
  }

  # DescribeParameters는 파라미터 값이 아니라 메타데이터(이름·타입·버전)만
  # 반환한다. GetParameter*는 값을 반환할 수 있어 plan 역할에는 주지 않는다
  # (D-3 §5, PR #232 리뷰 반영).
  statement {
    sid    = "ReadTestServerSsmParameterMetadata"
    effect = "Allow"
    actions = [
      "ssm:DescribeParameters",
    ]
    # DescribeParameters는 리소스 수준 권한을 지원하지 않는 AWS 제약이 있다.
    resources = ["*"]
  }

  statement {
    sid    = "ReadTestServerInstanceProfiles"
    effect = "Allow"
    actions = [
      "iam:GetInstanceProfile",
      "iam:ListInstanceProfiles",
      "iam:ListInstanceProfilesForRole",
    ]
    resources = ["arn:aws:iam::*:instance-profile/${var.project_prefix}-*"]
  }

  statement {
    sid    = "ReadTestServerScheduler"
    effect = "Allow"
    actions = [
      "scheduler:GetSchedule",
      "scheduler:ListSchedules",
      "scheduler:ListTagsForResource",
    ]
    resources = ["arn:aws:scheduler:*:*:schedule/default/${var.project_prefix}-*"]
  }

  statement {
    sid       = "AllowCallerIdentity"
    effect    = "Allow"
    actions   = ["sts:GetCallerIdentity"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "infra_plan" {
  name   = "${var.project_prefix}-infra-plan-permissions"
  role   = aws_iam_role.infra_plan.id
  policy = data.aws_iam_policy_document.infra_plan_permissions.json
}

# --- infra-apply role -------------------------------------------------------
# 보호된 `infrastructure-apply` GitHub Environment에서만 assume 가능하다.
# `verify-infra-approvals.py`/`confirm-infra-apply.py`가 검증하는 두 명 승인은
# 이 신뢰 정책과 별개의 게이트이며, 이 조건은 최소한의 AWS 측 방어선이다.

data "aws_iam_policy_document" "infra_apply_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:environment:${var.github_apply_environment}"]
    }
  }
}

resource "aws_iam_role" "infra_apply" {
  name                 = "${var.project_prefix}-infra-apply"
  assume_role_policy   = data.aws_iam_policy_document.infra_apply_trust.json
  max_session_duration = 3600

  tags = var.tags
}

data "aws_iam_policy_document" "infra_apply_permissions" {
  statement {
    sid    = "ManageTerraformState"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:ListBucket",
    ]
    resources = [
      aws_s3_bucket.terraform_state.arn,
      "${aws_s3_bucket.terraform_state.arn}/*",
    ]
  }

  statement {
    sid    = "ManageProjectS3Buckets"
    effect = "Allow"
    actions = [
      "s3:CreateBucket",
      "s3:PutBucketVersioning",
      "s3:PutEncryptionConfiguration",
      "s3:PutBucketPublicAccessBlock",
      "s3:PutLifecycleConfiguration",
      "s3:PutBucketLogging",
      "s3:PutBucketPolicy",
      "s3:PutBucketTagging",
      "s3:GetBucket*",
      "s3:GetObject",
      "s3:GetEncryptionConfiguration",
      "s3:GetLifecycleConfiguration",
      "s3:ListBucket",
      "s3:DeleteBucketPolicy",
    ]
    # 버킷 이름은 `${var.project_prefix}-*` 규칙을 따른다는 전제로 접두사
    # 범위만 허용한다(개별 버킷은 이후 스택에서 생성되므로 ARN을 미리 알 수
    # 없다).
    resources = [
      "arn:aws:s3:::${var.project_prefix}-*",
      "arn:aws:s3:::${var.project_prefix}-*/*",
    ]
  }

  statement {
    sid    = "ManageProjectKmsKeys"
    effect = "Allow"
    actions = [
      "kms:DescribeKey",
      "kms:GetKeyPolicy",
      "kms:GetKeyRotationStatus",
      "kms:PutKeyPolicy",
      "kms:EnableKeyRotation",
      "kms:TagResource",
      "kms:CreateAlias",
      "kms:DeleteAlias",
      "kms:UpdateAlias",
      "kms:ScheduleKeyDeletion",
    ]
    resources = ["*"]

    condition {
      test     = "StringLike"
      variable = "aws:ResourceTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    # AWS 제약: kms:CreateKey는 아직 존재하지 않는 Key의 ARN을 대상으로 하는
    # 최초 생성 action이라 IAM에서 Resource 수준으로 범위를 좁힐 수 없다
    # (AWS 문서에 명시된 제약). 생성 이후 관리 action은 위 statement처럼
    # 태그로 범위를 좁힌다.
    sid       = "CreateProjectKmsKeys"
    effect    = "Allow"
    actions   = ["kms:CreateKey"]
    resources = ["*"]
  }

  statement {
    sid    = "ManageProjectIamIdentities"
    effect = "Allow"
    actions = [
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:GetRole",
      "iam:TagRole",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:GetRolePolicy",
      "iam:ListRolePolicies",
      "iam:UpdateAssumeRolePolicy",
      "iam:CreateUser",
      "iam:DeleteUser",
      "iam:GetUser",
      "iam:TagUser",
      "iam:PutUserPolicy",
      "iam:DeleteUserPolicy",
      "iam:GetUserPolicy",
      "iam:ListUserPolicies",
      "iam:ListGroupsForUser",
      "iam:CreateGroup",
      "iam:DeleteGroup",
      "iam:GetGroup",
      "iam:PutGroupPolicy",
      "iam:DeleteGroupPolicy",
      "iam:GetGroupPolicy",
      "iam:ListGroupPolicies",
      "iam:AddUserToGroup",
      "iam:RemoveUserFromGroup",
    ]
    resources = [
      "arn:aws:iam::*:role/${var.project_prefix}-*",
      "arn:aws:iam::*:user/${var.project_prefix}-*",
      "arn:aws:iam::*:group/${var.project_prefix}-*",
    ]
  }

  # CI가 장기 자격 증명을 만들 수 있으면 OIDC 단기 세션 전제가 무너진다
  # (AGENTS.md 4.9). 허용 목록에 없더라도 이후 정책 변경으로 새어 나가지
  # 않도록 명시적으로 거부한다.
  statement {
    sid    = "DenyLongLivedCredentials"
    effect = "Deny"
    actions = [
      "iam:CreateAccessKey",
      "iam:UpdateAccessKey",
      "iam:CreateLoginProfile",
      "iam:UpdateLoginProfile",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "infra_apply" {
  name   = "${var.project_prefix}-infra-apply-permissions"
  role   = aws_iam_role.infra_apply.id
  policy = data.aws_iam_policy_document.infra_apply_permissions.json
}

# 프론트 테스트 서버 스택(D-3, #229/#233)의 EC2·ECR·SSM·IAM·Scheduler
# 권한은 infra-deployer(deployer.tf)와 공유하는 문서를 쓴다. 이전에는
# infra_apply_permissions에 source_policy_documents로 합쳐 하나의
# 인라인 정책으로 두었으나, 합친 결과가 AWS IAM의 Role당 인라인 정책
# 크기 한도(10,240바이트)를 초과해 실제 apply가 실패했다(#243). 네트워크
# 권한 문서 자체도 단독으로 그 한도를 넘어, network/tags/compute 세
# 인라인 정책으로 나눈다.
resource "aws_iam_role_policy" "infra_apply_test_server_network" {
  name   = "${var.project_prefix}-infra-apply-test-server-network-permissions"
  role   = aws_iam_role.infra_apply.id
  policy = data.aws_iam_policy_document.test_server_shared_permissions_network.json
}

resource "aws_iam_role_policy" "infra_apply_test_server_compute" {
  name   = "${var.project_prefix}-infra-apply-test-server-compute-permissions"
  role   = aws_iam_role.infra_apply.id
  policy = data.aws_iam_policy_document.test_server_shared_permissions_compute.json
}

resource "aws_iam_role_policy" "infra_apply_test_server_tags" {
  name   = "${var.project_prefix}-infra-apply-test-server-tags-permissions"
  role   = aws_iam_role.infra_apply.id
  policy = data.aws_iam_policy_document.test_server_shared_permissions_tags.json
}

# --- test-server-deploy role -------------------------------------------------
# GitHub Actions가 #231 배포 workflow에서 ECR push와 EC2 SSM SendCommand를
# 수행할 때 assume한다(D-4, #240). infra-apply는 Terraform State·계정
# 전체 관리 권한을 가진 고권한 Role이라 애플리케이션 배포에 재사용하면
# 권한이 부당하게 넓어지고, infra-deployer는 사람의 MFA AssumeRole
# 전용이라 GitHub Actions OIDC로 assume할 수 없다(D-4 §4).

data "aws_iam_policy_document" "test_server_deploy_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # 이 Role은 쓰기 권한(ECR push, SSM 명령 실행)을 가지므로 infra-plan과
    # 달리 저장소 전체를 허용하지 않는다. main 브랜치 push와 그 브랜치
    # 기준 workflow_dispatch만 assume할 수 있다(D-4 §4/§15, 사람 확정
    # 2026-09-17).
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "test_server_deploy" {
  name                 = "${var.project_prefix}-test-server-deploy"
  assume_role_policy   = data.aws_iam_policy_document.test_server_deploy_trust.json
  max_session_duration = 3600

  tags = var.tags
}

data "aws_iam_policy_document" "test_server_deploy_permissions" {
  # ECR 인증 토큰 발급은 리소스 수준으로 좁힐 수 없는 AWS 제약이다
  # (policy_sentry 확인, D-4 §5). 실제 push 권한은 아래 별도 statement가
  # 리포지토리 하나로 한정한다.
  statement {
    sid       = "EcrAuth"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid    = "EcrPush"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
      "ecr:BatchGetImage",
      "ecr:DescribeRepositories",
    ]
    resources = ["arn:aws:ecr:*:*:repository/${var.project_prefix}-*"]
  }

  # SendCommand는 대상 인스턴스와 실행할 문서 양쪽에 대한 리소스 수준
  # 권한이 모두 필요하다(policy_sentry 확인, D-4 §5). 인스턴스는 이
  # 프로젝트 태그로, 문서는 AWS 관리형 AWS-RunShellScript 하나로
  # 한정한다.
  statement {
    sid       = "SendCommandToTestServerInstance"
    effect    = "Allow"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ec2:*:*:instance/*"]

    condition {
      test     = "StringLike"
      variable = "aws:ResourceTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    sid       = "SendCommandRunShellScriptDocument"
    effect    = "Allow"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ssm:*::document/AWS-RunShellScript"]
  }

  # GetCommandInvocation/ListCommandInvocations는 리소스 수준 권한을
  # 지원하지 않는 AWS 제약이다(policy_sentry 확인, D-4 §5 SEC-2).
  statement {
    sid    = "ReadCommandResults"
    effect = "Allow"
    actions = [
      "ssm:GetCommandInvocation",
      "ssm:ListCommandInvocations",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "test_server_deploy" {
  name   = "${var.project_prefix}-test-server-deploy-permissions"
  role   = aws_iam_role.test_server_deploy.id
  policy = data.aws_iam_policy_document.test_server_deploy_permissions.json
}
