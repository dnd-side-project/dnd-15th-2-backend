# GitHub Issue #276 Task Contract

> Generated at: `2026-09-17T23:23:25+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `테스트 서버 자동 정지 제거`
- GitHub Issue: `#276`
- Branch: `infra/gh-276-disable-auto-stop`
- Base branch: `main`

## Objective

- 테스트 서버 자동 정지를 비활성화한다. 매일 03:00에 정지되지만 자동 시작이
  없어 쓸 때마다 수동 시작이 필요한 운영 마찰을 없앤다.

## Scope

- `infra/environments/dev/test-server/variables.tf`의 `enable_auto_stop`
  기본값을 `false`로 바꾼다. 커밋된 tfvars가 없어 이 기본값이 실제 적용값이다.

## Explicit exclusions

- 자동 시작 스케줄은 추가하지 않는다(#276 대안 2는 채택하지 않았다).
- 인스턴스 타입, 볼륨 크기, EIP 구성은 바꾸지 않는다.
- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 비용·예산 상한 재결정 | `tkv00` | PR 승인(1인, #251 기준) |

## Existing user-owned changes

- 브랜치 생성 직전 `git status --short` 결과가 비어 있었다. 보존할 사용자
  변경이 없다.

## 고위험 변경

- 리소스 삭제 3건(AGENTS.md §6). plan으로 대상을 확인했다.
  - `module.app_server.aws_scheduler_schedule.auto_stop[0]`
  - `module.app_server.aws_iam_role_policy.scheduler[0]`
  - `module.app_server.aws_iam_role.scheduler[0]`
- 인스턴스, 볼륨, EIP, 데이터에는 영향이 없다.
- 복구: 기본값을 `true`로 되돌리고 apply하면 세 리소스가 재생성된다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] `enable_auto_stop` 기본값이 `false`이고 비용 근거가 주석에 남는다
- [x] plan이 스케줄러 3개 삭제만 포함한다(create 0 / update 0 / delete 3)
- [ ] 예산 상한 재결정을 사람이 승인한다 ← PR 승인으로 처리
- [x] `terraform fmt`/`tflint`/`harness pr-ready` 통과

## 참고

- 비용: 현재 160 h 구동 9.55 USD → 상시 구동 24.37 USD. Issue #229의 월
  10 USD 상한을 2.4배 초과한다. 단가 출처는 D-3 §6.
- 승인된 결정 항목 H-1(#233)을 뒤집는 변경이다.
- 관련 이슈: #229/#231/#233/#274.
