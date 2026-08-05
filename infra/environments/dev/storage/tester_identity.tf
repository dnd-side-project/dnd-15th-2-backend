# 팀원 콘솔 로그인용 IAM User와 그룹
#
# `dev-s3-tester` Role은 이미 있지만, 그 Role을 assume하려면 먼저 계정에
# 로그인할 신원이 있어야 한다. 그 신원이 없어 Role이 사용 불가 상태였다
# (설계 보고서 D-1 §16).
#
# 이 User 자체에는 작업 권한을 주지 않는다. 실제 S3/KMS 권한은 assume한
# Role의 단기 자격 증명에서만 나온다.

resource "aws_iam_user" "s3_tester" {
  # checkov:skip=CKV_AWS_273:IAM Identity Center(SSO)는 조직 단위 설정이 필요해 계정 1개·소수 인원 규모에서는 과잉이다. 설계 D-1 §16에서 대안으로 검토 후 탈락시켰다.
  name = var.s3_tester_user_name

  tags = var.tags
}

# 콘솔 비밀번호(aws_iam_user_login_profile)를 Terraform으로 만들지 않는다.
# 비밀번호가 State에 남기 때문이다(AGENTS.md 4.6: State는 민감정보).
# 계정 소유자가 콘솔에서 직접 설정하고 최초 로그인 시 변경하게 한다.

# 권한은 User가 아니라 Group에 붙인다. 테스터가 늘어도 그룹에 추가하기만
# 하면 되고, 개별 User에 권한이 흩어지지 않는다.
resource "aws_iam_group" "s3_testers" {
  name = "${var.project_prefix}-s3-testers"
}

resource "aws_iam_user_group_membership" "s3_tester" {
  user   = aws_iam_user.s3_tester.name
  groups = [aws_iam_group.s3_testers.name]
}

data "aws_iam_policy_document" "s3_testers_group" {
  # MFA를 등록하기 전에는 Role을 assume할 수 없으므로, 자기 MFA를 스스로
  # 등록할 수 있는 최소 권한만 먼저 연다. `aws:username` 정책 변수로 각자
  # 자기 리소스만 다루도록 제한한다.
  statement {
    sid    = "ManageOwnMfaDevice"
    effect = "Allow"
    actions = [
      "iam:CreateVirtualMFADevice",
      "iam:DeleteVirtualMFADevice",
      "iam:EnableMFADevice",
      "iam:ResyncMFADevice",
      "iam:ListMFADevices",
    ]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:mfa/$${aws:username}",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:user/$${aws:username}",
    ]
  }

  statement {
    sid    = "ManageOwnPassword"
    effect = "Allow"
    actions = [
      "iam:ChangePassword",
      "iam:GetUser",
    ]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:user/$${aws:username}",
    ]
  }

  # 비밀번호 정책 조회는 계정 단위 action이라 리소스를 좁힐 수 없다.
  # 콘솔이 비밀번호 변경 화면에서 요구한다.
  statement {
    sid       = "ReadAccountPasswordPolicy"
    effect    = "Allow"
    actions   = ["iam:GetAccountPasswordPolicy"]
    resources = ["*"]
  }

  statement {
    sid       = "AssumeTesterRole"
    effect    = "Allow"
    actions   = ["sts:AssumeRole"]
    resources = [aws_iam_role.dev_s3_tester.arn]

    condition {
      test     = "Bool"
      variable = "aws:MultiFactorAuthPresent"
      values   = ["true"]
    }
  }

  # 장기 Access Key 발급을 명시적으로 차단한다(AGENTS.md 4.9, 12절).
  # Terraform이 키를 만들지 않는 것만으로는 사용자가 콘솔에서 스스로
  # 발급하는 경로가 남는다.
  statement {
    sid    = "DenyOwnAccessKeyCreation"
    effect = "Deny"
    actions = [
      "iam:CreateAccessKey",
      "iam:UpdateAccessKey",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_group_policy" "s3_testers" {
  name   = "${var.project_prefix}-s3-testers-permissions"
  group  = aws_iam_group.s3_testers.name
  policy = data.aws_iam_policy_document.s3_testers_group.json
}
