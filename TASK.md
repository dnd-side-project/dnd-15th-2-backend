# GitHub Issue #366 Task Contract

> Generated at: `2026-11-21T20:35:49+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `dev 테스트 서버 알림 worker scheduling 활성화와 FCM 실발송 전환`
- GitHub Issue: `#366`
- Branch: `chore/gh-366-dev-worker-fcm`
- Base branch: `main`

## Objective

- Issue #366에 따라 dev 프로필에서 worker scheduling과 알림 생성·발송 worker를 켜고 push provider를 `fcm`으로 지정해, dev 테스트 서버에서 알림 생성부터 FCM 실발송까지 이어지게 한다.
- 선행 조건 충족(2026-11-21): #365 병합(PR #373, merge `32ab7b59`, test-server 배포 run #35 성공), #364 apply(run #15). test-server 컨테이너에는 `QELLO_NOTIFICATION_PUSH_FCM_PROJECT_ID`, `QELLO_NOTIFICATION_PUSH_FCM_CREDENTIAL_JSON`과 token 보호 key 3개가 주입돼 있다.
- 확인된 사실(오케스트레이터, 2026-11-21):
  - `AnswerModerationIntake` bean이 현재 등록되지 않아 답변 제출은 `MODERATION_INTAKE_NOT_CONFIGURED`로 실패한다. `ANSWER_*` 알림은 moderation verdict 이후에만 생성되며, `answer-moderation` worker는 이 Issue의 제외 범위다. 따라서 이번 작업 후 dev에서 실발송을 검증할 수 있는 알림 경로는 `direction-matching` → `recipient-notification-fan-out`(`DIRECTION_POST_RECEIVED`)과 `notification-fan-out`·`report-resolution-fan-out`이 처리하는 유형이다.
  - `application-dev.yml`의 `qello.worker.scheduling.enabled` 값은 `application.yml`의 `QELLO_WORKER_SCHEDULING_ENABLED` placeholder보다 우선한다. 롤백은 설정 변경 후 재배포다.
- Test plan: `docs/test-plans/gh-366-TEST-PLAN-GH-366-DEV-WORKER-FCM.md` — `APPROVED` — 단위 6개 + 통합 3개 + 병합 후 수동 3개, 승인자 @Byuntil, 2026-11-21. 승인 증거: https://github.com/dnd-side-project/dnd-15th-2-backend/issues/366#issuecomment-7062302661
- 결정(2026-11-21, @Byuntil):
  - dev fixture 값(dev 전용, 운영 권장값 아님): `pool-size: 5`. outbox 네 개는 `fixed-delay: PT5S`, `batch-size: 20`, `lease-duration: PT1M`, `retry.max-attempts: 5`, `retry.base-delay: PT5S`, `retry.max-delay: PT5M`. `push-delivery-dispatch`는 `fixed-delay: PT5S`, `batch-size: 10`, `lease-duration: PT2M`, `retry.max-attempts: 5`, `retry.base-backoff: PT10S`, `retry.backoff-cap: PT10M`. FCM 1건 최악 8s(connect 3s + read 5s) × 10건보다 push lease를 길게 둔다.
  - 롤백 권고: 발송 중지는 `qello.worker.scheduling.enabled: false`를 우선한다. `provider: noop`은 FCM 자격 증명 문제일 때만 쓰며, 대기 중 delivery가 실제 발송 없이 SENT로 처리된다는 경고를 문서에 적는다.

## Scope

- `src/main/resources/application-dev.yml`
  - `qello.worker.scheduling.enabled: true`와 활성 worker 수에 맞춘 `pool-size`.
  - `direction-matching`, `recipient-notification-fan-out`, `notification-fan-out`, `report-resolution-fan-out`: `enabled`, `fixed-delay`, `batch-size`, `lease-duration`, `retry.max-attempts`, `retry.base-delay`, `retry.max-delay`.
  - `push-delivery-dispatch`: `enabled`, `fixed-delay`, `batch-size`, `lease-duration`, `retry.max-attempts`, `retry.base-backoff`, `retry.backoff-cap`.
  - `qello.notification.push.provider: fcm`.
  - 값은 dev 전용 fixture이며 운영 권장값이 아니라는 주석을 남긴다.
- `docs/push-production-setup.md` 10절: dev fixture 값, 활성화 방법, 롤백 방법(`enabled: false` 또는 `provider: noop` 후 재배포).
- 승인된 테스트 계획에 따라 dev 프로필 기동·바인딩을 검증하는 테스트를 수정·추가한다. `DevProfileBootIntegrationTest`의 dev = `noop` 기대(#365 INT-006)는 `fcm` 기준으로 바뀐다.

## Explicit exclusions

- sweep 계열(`recipient-expiration-sweep`, `skip-confirmation-sweep`, `account-withdrawal-sweep`)과 `answer-moderation` worker 활성화.
- 운영 worker 수치 결정과 prod 활성화, metric exporter, 발송 실패 경보, 다중 instance lease owner 검증.
- production Java 코드 변경. 설정만으로 목표를 달성할 수 없으면 구현하지 않고 BLOCKED로 보고한다.
- `infra/**`, `.github/**`, secret 주입 경로 변경.
- 커밋, push, PR 생성은 실행 에이전트가 하지 않는다. 오케스트레이터가 사용자 승인 후 수행한다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값, 실기기 FID는 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `TASK.md`, 실행 지시, 독립 검증, 배포 후 DB 확인 | 오케스트레이터 | 사람의 범위 승인 |
| 테스트 계획 | 실행 에이전트(`/harness-test-plan`) | 사람의 계획 승인 |
| `application-dev.yml`, 문서, 테스트 | 실행 에이전트 | 오케스트레이터의 독립 검증 후 사람의 PR 승인 |
| Android 실기기 수신 확인 | 모바일 담당자 | 결과를 PR 또는 Issue에 기록 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 출력은 `TASK.md`(task-init) 하나였다.
- `gh`와 `harness`는 `env -u SSL_CERT_FILE -u SSL_CERT_DIR`로 시스템 CA를 사용한다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- dev 프로필 설정으로 application context가 기동하고 `WorkerSchedulingProperties` 검증 예외가 없다.
- dev 프로필에서 지정한 다섯 worker의 bean이 등록되고, 제외한 sweep·`answer-moderation` worker bean은 등록되지 않는다.
- dev 프로필에서 provider가 `fcm`이고 `FcmHttpV1PushProvider`가 등록된다.
- 롤백 방법이 `docs/push-production-setup.md`에 있다.
- `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check`가 통과한다.
- 병합 후(test-server 자동 배포): 배포 run 성공, 실기기 FID에 대한 `notification_delivery`가 `SENT`이고 `provider_message_id`가 저장됨, 같은 notification·기기 조합의 중복 발송 없음, Android foreground·background·앱 종료 수신 결과를 PR 또는 Issue 코멘트에 기록한다.
