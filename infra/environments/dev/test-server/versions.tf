terraform {
  # aws_ssm_parameter의 value_wo(write-only 인자)가 1.11.0부터 지원된다.
  # 이 스택만 올리고 다른 스택의 >= 1.10.0 제약은 그대로 둔다(D-3 §5 S-5,
  # §9).
  required_version = ">= 1.11.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "= 6.57.1"
    }
  }
}
