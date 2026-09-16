# GitHub Issue #237 Task Contract

> Generated at: `2026-09-16T23:45:23+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `PR #236 리뷰 발견사항 수정 (D-3)`
- GitHub Issue: `#237`
- Branch: `infra/gh-237-pr236-review-fixes`
- Base branch: `main`
- DESIGN-ID: `D-3`(참고). 설계는 이미 `APPROVED_FOR_BUILD`로 승인·구현·머지되었다
  (#233, PR #236). 이 이슈는 새 설계 결정이 아니라 그 구현의 리뷰 후속 조치다.

## Objective

- PR #236이 CodeRabbit 리뷰 완료 전에 머지되어 `main`에 남은 버그와 문서
  불일치를 고친다.
- 애플리케이션 런타임 동작, API 경로, 상태 코드, 요청·응답 구조를 변경하지 않는다.
- 실제 AWS 계정에는 아직 아무것도 apply되지 않았다(`qello-new` 계정 전환,
  bootstrap 미적용 — `gh-229-D-3-build.md` §6 참고).

## Scope

- `infra/bootstrap/oidc.tf`: EC2 action에 `aws:ResourceTag/Project` 조건
  추가, `ec2:CreateTags`를 생성/기존 리소스 재태깅으로 분리,
  `ec2:DescribeInstanceAttribute`/`DescribeVpcAttribute`/`DescribeNetworkAcls` 추가.
- `infra/modules/app-server/variables.tf`,
  `infra/environments/dev/test-server/variables.tf`: 자동 정지 cron을
  평일에서 매일 새벽 3시(KST)로 변경.
- `infra/modules/app-server/user_data.sh.tftpl`: EIP·EBS 볼륨 연결과
  user_data 실행 순서 경쟁 상태를 재시도 로직으로 해소.
- `infra/environments/dev/test-server/outputs.tf`: SSM 파라미터 값 갱신
  안내를 `value_wo` 방식에 맞게 수정.
- `infra/modules/network/main.tf`, `infra/modules/app-server/main.tf`,
  `infra/environments/dev/test-server/main.tf`: 비용 기반 checkov 예외에
  담당자·재검토일 추가.
- `docs/reports/infrastructure/gh-229-D-3.md`: §15/§16과 상단 승인 상태 동기화.
- `docs/reports/infrastructure/gh-229-D-3-build.md`: 변경 파일 범위 서술 정정.

## Explicit exclusions

- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- AWS CLI를 이용한 리소스 생성·변경·삭제.
- **`infra-deployer` Role의 권한 상승 경로(PassRole + 기존 광범위 IAM
  관리 권한 조합)는 이 이슈에서 다루지 않는다.** Role 네임스페이스
  재설계가 필요한 아키텍처 변경이라 D-1 bootstrap 설계 범위를 벗어난다.
  별도 이슈와 사람의 설계 결정이 필요하다(CodeRabbit PR #236 리뷰,
  `infra/bootstrap/deployer.tf:175` 코멘트).
- 애플리케이션 코드 변경. CORS, actuator 헬스체크, dev 프로파일은 #230 범위다.
- ECR 빌드·푸시 GitHub Actions workflow는 #231 범위다.
- `CLAUDE.md`, `AGENTS.md`, `.claude/**`, `agents/**`, `CODEOWNERS`,
  `.github/workflows/*apply*`, `scripts/guard-*` 수정.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 리뷰 수정 구현 (`infra/**`) | `tkv00` | `@Byuntil`, `@tkv00` PR 승인 |
| Apply | 사람 | `infrastructure-apply` Environment 승인과 workflow dispatch |

## Existing user-owned changes

- 브랜치 생성 직전 `git status --short` 결과가 비어 있었다. 보존할 사용자 변경이 없다.

## Validation

```bash
terraform fmt -check -recursive infra
terraform -chdir=infra/modules/network init -backend=false && terraform -chdir=infra/modules/network validate
terraform -chdir=infra/modules/app-server init -backend=false && terraform -chdir=infra/modules/app-server validate
terraform -chdir=infra/environments/dev/test-server init -backend=false && terraform -chdir=infra/environments/dev/test-server validate
terraform -chdir=infra/bootstrap init -backend=false && terraform -chdir=infra/bootstrap validate
tflint --recursive
checkov -d infra --framework terraform
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [ ] PR #236의 CodeRabbit 코멘트 중 `infra-deployer` 권한 상승 건을 제외한
      전부에 대응한다.
- [ ] 정적 검증(fmt, validate, tflint, checkov)이 통과하고, checkov 결과가
      기존과 동일하게 408 passed / 0 failed / 9 skipped를 유지한다.
- [ ] checkov `--skip-check` 목록을 늘리지 않는다.
- [ ] `./harness pr-ready --project-tests`가 통과한다.
- [ ] PR 본문에 `infra-deployer` 권한 상승 건이 이 PR 범위 밖이며 별도
      이슈가 필요하다는 점을 명시한다.

## 참고

- 원본 구현: #233, PR #236(머지됨, `main`으로 통합됨).
- 설계: `docs/reports/infrastructure/gh-229-D-3.md`(D-3, `APPROVED_FOR_BUILD`).
- 계정 전환: `docs/reports/infrastructure/gh-229-D-3-build.md` §6
  (`qello-new` 계정, bootstrap 재적용 필요).
