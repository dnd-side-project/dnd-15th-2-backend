# Infrastructure Workspace

AWS 인프라 코드는 승인된 설계 PR 이후 이 디렉터리에 추가한다. Terraform 또는
AWS CDK만 기본 IaC로 사용하며 AWS SDK 호출을 대체 수단으로 사용하지 않는다.

## Default state

- apply/deploy 비활성
- 실제 계정, 주소, IAM ID, 토큰, `.env` 값 없음
- 설계와 plan을 PR에서 먼저 검토
- `@Byuntil`, `@tkv00` 두 명 승인 필요
- GitHub `infrastructure-apply` Environment의 수동 승인 필요

## Suggested layout

```text
infra/
├── modules/
└── environments/
    ├── dev/
    └── production/
```

환경별 실제 값은 GitHub Environment, OIDC, 안전한 변수 저장소에서 주입한다.
샘플 파일에는 실제 식별자를 넣지 않는다.

상세 설정은 `docs/harness/GITHUB_ENFORCEMENT.md`와
`templates/infrastructure-design-report.md`를 따른다.
