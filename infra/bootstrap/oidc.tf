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
    "6938fd4d98bab03faadb97b34396831e3780aea",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]
}

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = var.github_oidc_thumbprints

  tags = var.tags
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
