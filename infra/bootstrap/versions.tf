terraform {
  required_version = ">= 1.10.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "= 6.57.1"
    }
  }

  # 이 스택이 State Backend 자체를 만들기 때문에 자신의 State를 원격에 둘 수
  # 없다(ADR 필요 없는 표준 Terraform 부트스트랩 패턴). 최초 apply는 사람이
  # 로컬 State로 직접 실행하고, 완료 직후 `terraform init -migrate-state`로
  # 이 스택이 만든 S3 버킷으로 State를 옮긴다(README.md 절차 참고). 이후
  # 변경부터는 원격 State를 사용하므로 AGENTS.md 4.6 예외는 최초 1회에만
  # 적용된다.
}
