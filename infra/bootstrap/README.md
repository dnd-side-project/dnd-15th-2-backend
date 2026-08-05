# Terraform Bootstrap

이 스택은 저장소 최초의 AWS 인프라다. Terraform State S3 Backend와 그
State 버킷의 접근 로그 버킷, GitHub Actions OIDC Provider,
`infra-plan`/`infra-apply` 역할을 생성한다.
Infrastructure Design Report `docs/reports/infrastructure/gh-63-D-1.md`
(DESIGN-ID `D-1`, GitHub Issue #63)에서 사람이 승인한 설계를 구현한다.

State 접근 로그 버킷은 설계 보고서 §9에 열거되어 있지 않지만 AGENTS.md 4.6이
State Backend 조건으로 "감사 가능한 접근 로그"를 요구하므로 추가했다. 근거와
검토 요청 사항은 `docs/reports/infrastructure/gh-63-D-1-build.md`에 있다.

## 왜 이 스택만 로컬 State로 시작하는가

다른 모든 스택은 이 스택이 만드는 S3 Backend를 원격 State로 사용한다. 이
스택 자신은 그 Backend가 존재하기 전에 최초로 적용되어야 하므로, 최초 1회에
한해 로컬 State로 시작하는 예외가 필요하다(AGENTS.md 4.6 예외,
Infrastructure Design Report SEC-2에서 사람 승인 완료).

이 예외는 다음 조건에서만 유효하다.

- 최초 apply 1회에만 적용된다.
- apply 직후 아래 절차로 State를 원격으로 이전한다.
- 이 스택에 대한 이후 변경은 반드시 원격 State를 사용한다.

## 최초 적용 절차 (사람이 직접 수행, AI 에이전트가 실행하지 않음)

이 저장소의 정책상 `terraform apply`는 AI 에이전트/Claude Code 세션이
실행하지 않는다. 계정에 아직 어떤 GitHub OIDC 역할도 없으므로(이 스택이
그 역할을 만드는 대상이다) 최초 적용은 사람이 자신의 AWS 자격 증명으로
로컬에서 직접 실행해야 한다.

```bash
cd infra/bootstrap
terraform init
terraform plan -out=tfplan
# 사람이 plan을 직접 검토한 뒤에만 적용한다.
terraform apply tfplan
```

## 적용 직후: 원격 State로 이전

```bash
cat > backend_override.tf <<'EOF'
terraform {
  backend "s3" {
    bucket       = "<state_bucket_name output 값>"
    key          = "bootstrap/terraform.tfstate"
    region       = "<aws_region 변수 값>"
    use_lockfile = true
    encrypt      = true
  }
}
EOF

terraform init -migrate-state
rm backend_override.tf
```

`backend_override.tf`는 실제 버킷 이름을 담을 수 있으므로 커밋하지 않는다
(`.gitignore`에 이미 포함되어야 한다 — 없다면 추가한다). 이후 이 스택을
다시 변경할 때는 커밋된 `versions.tf`의 주석 대신, 이 이전된 원격 State를
그대로 사용한다.

## 변수

실제 계정 ID, ARN, 버킷 이름은 이 저장소에 커밋하지 않는다.
`state_bucket_name`과 `state_access_log_bucket_name`은 기본값이 없으므로
`terraform.tfvars` 또는 CI 변수로 전달한다(예시 파일에는 실제 값을 넣지
않는다). `state_access_log_bucket_name`은 `infra-apply` 역할의 권한 범위에
들어오도록 `project_prefix` 접두사로 시작해야 한다.

## 이 스택은 apply workflow로 적용하지 않는다

`.github/workflows/infrastructure-apply.yml`은 원격 backend 블록이 커밋된
스택만 적용한다. 이 스택의 backend는 최초 적용 이후 커밋되지 않는
`backend_override.tf`로 설정되므로, 대상 경로 검사에서 차단된다. 이 스택의
변경은 위 절차대로 사람이 직접 적용한다.
