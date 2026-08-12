# GitHub Issue #132 Task Contract

> Generated at: `2026-08-12T10:10:54+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `MVP AWS 아키텍처 전체 설계`
- GitHub Issue: `#132`
- Branch: `infra/gh-132-mvp-infra-design`
- Base branch: `main`
- DESIGN-ID: `D-2`
- Design report: `docs/reports/infrastructure/gh-132-D-2.md`
- Design status: `READY_FOR_DESIGN_REVIEW` — 사람 결정 6건 확정(2026-08-12).
  선택안 Option C, 월 약 125 USD 추정

## Objective

- Qello MVP를 실제로 구동할 AWS 컴퓨팅·데이터베이스·네트워크·배포·관측
  계층을 설계하고, Terraform 구현 전에 검토 가능한 Infrastructure Design
  Report를 만든다.
- D-1(#63)이 만든 State Backend·OIDC·S3 자산 위에 얹는 설계이며, 기존 자산을
  재설계하지 않는다.
- 설계만 수행한다. Terraform 구현과 apply는 이 이슈 범위 밖이다.

## Scope

1. 요구사항 intake — 확인된 값과 가정을 `CONFIRMED`/`ASSUMED`/`UNKNOWN`/
   `BLOCKED`로 분류한다.
2. 컴퓨팅·데이터베이스·네트워크 egress·비밀 관리 후보를 비교하고 탈락 이유를
   기록한다.
3. AWS Price List API의 공식 단가로 예산 구간별 월 비용을 산정한다.
4. IAM·네트워크·암호화·State 관점의 독립 보안 검토를 수행한다.
5. 변경 위험도, 실패 모드, 롤백·복구 절차를 기록한다.
6. Terraform 소유 파일 경계와 검증 계획을 정의한다.

## Explicit exclusions

- Terraform 코드 구현 — `/harness-infra-build`와 별도 이슈에서 수행한다.
- `terraform apply`, `terraform plan`(자격 증명 필요), 실제 AWS 리소스 변경.
- 애플리케이션 코드 변경. 특히 health 엔드포인트(actuator) 도입은 이 이슈
  범위 밖이며 별도 이슈로 분리해야 한다(보고서 §15-5).
- 배포 workflow(`.github/workflows/*deploy*`) 신규 작성.
- `infra/environments/dev/storage/**`와 D-1 소유 리소스의 재설계.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Infrastructure Design Report D-2(아키텍처 대안, 비용, 보안, 위험) | Infrastructure orchestrator | 대안 탈락 이유의 타당성, 공식 단가 근거, `infra-apply` 권한 확대 범위(SEC-A), RDS 자격증명 State 노출 방지(SEC-C), Option C 선택과 x86 채택 근거 |
| 사람 결정 6건 | `@Byuntil`, `@tkv00` | 2026-08-12 확정 완료 — 예산 B~C, prod 단일, 도메인 추후 구매, 장기 운영, actuator 도입, 이미지 아키텍처 위임 |

## Existing user-owned changes

- 격리된 worktree(`.claude/worktrees/gh-132-mvp-infra-design`)에서
  `origin/main`(commit `2d6aba2`) 기준으로 분기했다. 분기 시점
  `git status --short`는 비어 있었다.
- 같은 저장소의 `feat/gh-106-nickname-sync-filter` 브랜치에 있던 사용자
  변경(`TASK.md` 수정, `docs/test-plans/gh-106-*.md`)은 건드리지 않았다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

인프라 정적 검증(`terraform fmt`/`validate`/`tflint`/`checkov`)은 이 이슈가
Terraform 파일을 만들지 않으므로 대상이 없다. 빌드 이슈에서 수행한다.

## Completion criteria

- [x] `templates/infrastructure-design-report.md` 형식의 보고서를 생성하고
      `DESIGN-ID` `D-2`를 부여한다.
- [x] 컴퓨팅·데이터베이스 각각 최소 두 가지 대안과 탈락 이유를 기록한다.
- [x] `AGENTS.md` 4.5의 검토 영역을 모두 다룬다.
- [x] 비용을 AWS 공식 단가(Price List API, 조회일 기록)로 산정한다.
- [x] 독립 보안 검토 finding을 severity와 함께 기록한다.
- [x] 변경 위험도, 실패 모드, 롤백·복구 절차를 기록한다.
- [x] 사람 결정 6건이 확정된다(보고서 §15) — 2026-08-12.
- [x] 확정된 결정을 반영해 선택안을 하나로 확정한다(Option C).
- [ ] 설계 상태가 `APPROVED_FOR_BUILD`로 승인된다.
- [ ] `@Byuntil`, `@tkv00`의 PR 승인.

## 후속 이슈 (이 이슈 범위 밖, 보고서 §15.1)

- [ ] actuator 도입(애플리케이션 변경) — ALB health check의 선행 조건.
- [ ] 배포 workflow 작성(ECR push + ECS 서비스 갱신).
- [ ] 도메인 구매와 Route53 위임 — 실사용자 공개 전 필수.
- [ ] OpenAI API Key를 SSM SecureString에 사람이 사전 등록.
