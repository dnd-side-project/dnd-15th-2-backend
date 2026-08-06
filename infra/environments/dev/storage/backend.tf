# `infra/bootstrap`이 생성한 State Backend를 사용한다(AGENTS.md 4.6). 버킷
# 이름은 계정별 값이라 여기 하드코딩하지 않고 `-backend-config`로 주입한다.
#
#   terraform init \
#     -backend-config="bucket=<bootstrap state_bucket_name output>" \
#     -backend-config="region=<aws_region>"
#
# 정적 검사(`terraform validate`)는 `-backend=false`로 이 backend 블록을
# 무시하고 실행한다(.github/workflows/infrastructure-static.yml과 동일 절차).
terraform {
  backend "s3" {
    key          = "environments/dev/storage/terraform.tfstate"
    use_lockfile = true
    encrypt      = true
  }
}
