# Infrastructure Workspace

AWS 인프라 코드는 승인된 설계 PR 이후 이 디렉터리에 추가한다. Terraform 또는
AWS CDK만 기본 IaC로 사용하며 AWS SDK 호출을 대체 수단으로 사용하지 않는다.

## Default state

- apply/deploy 비활성
- 실제 계정, 주소, IAM ID, 토큰, `.env` 값 없음
- 설계와 plan을 PR에서 먼저 검토
- `@Byuntil`, `@tkv00` 두 명 승인 필요
- GitHub `infrastructure-apply` Environment의 수동 승인 필요

## Layout

```text
infra/
├── bootstrap/                       Terraform State Backend + State 접근 로그
│                                    버킷 + GitHub OIDC
│                                    (최초 1회 로컬 State 예외, README.md 참고)
├── modules/
│   └── s3-private-bucket/           재사용 가능한 private S3 버킷 모듈
└── environments/
    └── dev/
        └── storage/                 게시물 이미지 테스트 S3 버킷(GitHub Issue #63)
```

환경별 실제 값(버킷 이름, State Backend 버킷 등)은 GitHub Environment, OIDC,
안전한 변수 저장소 또는 각 디렉터리의 `terraform.tfvars`(커밋되지 않음)에서
주입한다. `terraform.tfvars.example` 샘플 파일에는 실제 식별자를 넣지 않는다.

각 디렉터리는 해당 설계를 승인한 Infrastructure Design Report를 갖는다.
`infra/bootstrap`과 `infra/environments/dev/storage`는
`docs/reports/infrastructure/gh-63-D-1.md`(DESIGN-ID `D-1`, GitHub Issue #63,
상태 `APPROVED_FOR_BUILD`)를 따른다.

## Local verification

`.github/workflows/infrastructure-static.yml`과 동일한 검사를 로컬에서 실행할
수 있다. Checkov 제외 목록은 workflow의 `CHECKOV_SKIP_CHECKS` 주석에 사유가
기록되어 있으며 임의로 늘리지 않는다.

```bash
terraform fmt -check -recursive infra
terraform -chdir=<root> init -backend=false && terraform -chdir=<root> validate
tflint --chdir=<root>
checkov --directory infra --framework terraform \
  --skip-check CKV_AWS_144,CKV2_AWS_62,CKV_AWS_145 --compact --quiet
```

## Apply

실제 적용은 `.github/workflows/infrastructure-apply.yml`만 수행한다. 이
workflow는 `INFRA_APPLY_ENABLED` 저장소 변수, 확인 문구, 두 명의 정확한 head
승인, 승인된 commit SHA, 검토된 plan의 SHA-256, 보호된
`infrastructure-apply` Environment 승인을 모두 요구한다. `infra/bootstrap`은
원격 backend 블록이 커밋되지 않으므로 이 경로로 적용하지 않는다
(`infra/bootstrap/README.md` 참고).

상세 설정은 `docs/harness/GITHUB_ENFORCEMENT.md`와
`templates/infrastructure-design-report.md`를 따른다.
