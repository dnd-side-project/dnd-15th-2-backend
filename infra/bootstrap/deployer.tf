# 사람이 직접 사용하는 인프라 배포 Role
#
# `infra-apply`는 GitHub OIDC 신뢰 정책상 보호된 Environment의 workflow만
# assume할 수 있어 사람이 쓸 수 없다. 그런데 이 bootstrap 스택 자체의 최초
# 적용은 원격 State와 OIDC Provider가 생기기 전에 수행해야 하므로 사람이
# 직접 실행해야 한다. 그 작업을 계정 root 자격 증명으로 하지 않기 위한
# Role이다(설계 보고서 D-1 §16).
#
# 이 Role 자체도 이 스택이 만들기 때문에, 계정 최초 1회 적용만은 root로
# 수행할 수밖에 없다. 이후의 모든 수동 인프라 작업은 이 Role로 한다.

data "aws_iam_policy_document" "infra_deployer_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }

    # 계정 root를 신뢰하면 실제 허용 여부는 각 IAM 주체의 정책이 결정한다.
    # MFA를 조건으로 걸어 비밀번호만 유출된 상태에서는 assume할 수 없게 한다.
    condition {
      test     = "Bool"
      variable = "aws:MultiFactorAuthPresent"
      values   = ["true"]
    }
  }
}

resource "aws_iam_role" "infra_deployer" {
  name                 = "${var.project_prefix}-infra-deployer"
  assume_role_policy   = data.aws_iam_policy_document.infra_deployer_trust.json
  max_session_duration = 3600

  tags = var.tags
}

data "aws_iam_policy_document" "infra_deployer_permissions" {
  # AdministratorAccess를 부착하지 않는다(설계 D-1 §16). 이 저장소가 실제로
  # 만드는 리소스 종류로만 범위를 좁힌다.
  statement {
    sid    = "ManageProjectS3Buckets"
    effect = "Allow"
    actions = [
      "s3:CreateBucket",
      "s3:DeleteBucket",
      "s3:GetBucket*",
      "s3:GetEncryptionConfiguration",
      "s3:GetLifecycleConfiguration",
      "s3:ListBucket",
      "s3:PutBucketLogging",
      "s3:PutBucketPolicy",
      "s3:DeleteBucketPolicy",
      "s3:PutBucketPublicAccessBlock",
      "s3:PutBucketTagging",
      "s3:PutBucketVersioning",
      "s3:PutEncryptionConfiguration",
      "s3:PutLifecycleConfiguration",
    ]
    resources = [
      "arn:aws:s3:::${var.project_prefix}-*",
      "arn:aws:s3:::${var.project_prefix}-*/*",
    ]
  }

  # State 파일을 읽고 쓰려면 객체 수준 권한이 필요하다. State 버킷은
  # 접두사 규칙과 무관하게 지정될 수 있어 ARN으로 직접 지정한다.
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
    sid    = "ManageProjectKmsKeys"
    effect = "Allow"
    actions = [
      "kms:CreateAlias",
      "kms:DeleteAlias",
      "kms:UpdateAlias",
      "kms:DescribeKey",
      "kms:EnableKeyRotation",
      "kms:GetKeyPolicy",
      "kms:GetKeyRotationStatus",
      "kms:PutKeyPolicy",
      "kms:ScheduleKeyDeletion",
      "kms:CancelKeyDeletion",
      "kms:TagResource",
    ]
    resources = ["*"]

    condition {
      test     = "StringLike"
      variable = "aws:ResourceTag/Project"
      values   = ["qello"]
    }
  }

  statement {
    # AWS 제약: kms:CreateKey는 아직 존재하지 않는 Key를 대상으로 하므로
    # Resource 수준으로 좁힐 수 없다. 생성 이후 관리 action은 태그로 좁힌다.
    sid       = "CreateProjectKmsKeys"
    effect    = "Allow"
    actions   = ["kms:CreateKey"]
    resources = ["*"]
  }

  # 권한 상승 경로를 막기 위해 대상을 접두사로 한정하고, 관리형 정책을
  # 임의로 부착할 수 있는 iam:AttachRolePolicy/AttachUserPolicy는 주지 않는다.
  statement {
    sid    = "ManageProjectIamIdentities"
    effect = "Allow"
    actions = [
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:GetRole",
      "iam:ListRolePolicies",
      "iam:TagRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:GetRolePolicy",
      "iam:CreateUser",
      "iam:DeleteUser",
      "iam:GetUser",
      "iam:ListUserPolicies",
      "iam:TagUser",
      "iam:PutUserPolicy",
      "iam:DeleteUserPolicy",
      "iam:GetUserPolicy",
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

  statement {
    sid    = "ManageGithubOidcProvider"
    effect = "Allow"
    actions = [
      "iam:CreateOpenIDConnectProvider",
      "iam:DeleteOpenIDConnectProvider",
      "iam:GetOpenIDConnectProvider",
      "iam:TagOpenIDConnectProvider",
      "iam:UpdateOpenIDConnectProviderThumbprint",
    ]
    resources = ["arn:aws:iam::*:oidc-provider/token.actions.githubusercontent.com"]
  }

  # 장기 자격 증명을 만들 수 있으면 이 Role의 단기 세션 전제가 무너진다.
  # 허용 목록에 없더라도 이후 정책 변경으로 새어 나가지 않도록 명시적으로
  # 거부한다(AGENTS.md 4.9).
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

  statement {
    sid       = "AllowCallerIdentity"
    effect    = "Allow"
    actions   = ["sts:GetCallerIdentity"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "infra_deployer" {
  name   = "${var.project_prefix}-infra-deployer-permissions"
  role   = aws_iam_role.infra_deployer.id
  policy = data.aws_iam_policy_document.infra_deployer_permissions.json
}
