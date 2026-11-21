# GitHub Issue #365 Task Contract

> Generated at: `2026-11-21T17:23:23+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `푸시 기기 등록 FID 전환과 dev token 보호·provider 선택`
- GitHub Issue: `#365`
- Branch: `feat/gh-365-push-fid-provider`
- Base branch: `main`

## Objective

- Issue #365에 따라 푸시 기기 등록·해지 요청 필드를 FCM registration token에서 Firebase Installation ID(FID)로 바꾸고, FCM HTTP v1 발송 대상 필드를 `fid`로 전환한다.
- dev 프로필에서 token protector가 등록되도록 하고, provider 선택을 프로필이 아니라 `qello.notification.push.provider`(`noop` | `fcm`, 기본값 `noop`) 속성으로 정한다.
- 선행: #364 apply(run #15)와 기존 test-server 주입 검증(deploy run #34)이 2026-11-21에 끝났다. test-server 컨테이너에는 `QELLO_NOTIFICATION_PUSH_TOKEN_*` 3개와 `QELLO_NOTIFICATION_PUSH_FCM_*` 2개가 주입돼 있다.
- Test plan: `docs/test-plans/gh-365-TEST-PLAN-GH-365-PUSH-FID-PROVIDER.md` — `APPROVED` — 아래 결정을 반영한 27개 시나리오, 승인자 @Byuntil, 2026-11-21. 승인 증거: https://github.com/dnd-side-project/dnd-15th-2-backend/issues/365#issuecomment-7060067551
- 결정(2026-11-21, @Byuntil):
  - provider 미지정 동작: `test`, `local`, `integration`, `dev`는 `noop`을 기본으로 한다. 그 밖의 프로필은 `qello.notification.push.provider`를 명시하지 않으면 기동에 실패한다. Issue 본문의 "기본값 `noop`"에서 바꾼 것으로, 설정 누락 시 실제 발송 없이 delivery가 SENT로 기록되는 위험을 막는다.
  - FID 오류 분류: 기존 `INVALID_ARGUMENT` + `message.token` field violation 조건을 유지하고, 같은 조건의 `message.fid` field violation도 무효 기기로 처리한다. 그 밖의 `INVALID_ARGUMENT`는 기존처럼 PermanentFailure로 둔다.
  - `noop`·`fcm` 밖의 provider 값은 기동 실패로 처리한다.

## Scope

- `PushDeviceRequest`: 요청 필드 `token`을 `fid`로 바꾸고 `@Schema` 설명을 FID 기준으로 고친다. `POST /api/v1/notifications/devices`와 `/devices/revoke`에 같이 적용한다. `toString` redaction을 유지한다.
- `FcmHttpV1PushProvider`: 발송 body의 대상 필드를 `token`에서 `fid`로 바꾸고 `android.priority`를 `HIGH`로 지정한다. data payload(`type`, `count`, `hasRemainingTime`)는 바꾸지 않는다. 무효 기기 처리는 기존 조건을 유지하고 위 결정에 따라 `message.fid` field violation을 추가한다.
- `PushConfiguration`: provider 선택을 `qello.notification.push.provider` 속성으로 정한다. `fcm`일 때만 `PushProperties` 검증과 FCM bean을 등록하고, `noop`이면 FCM 설정 없이 기동한다. 미지정 시 동작은 위 결정을 따른다.
- `PushConfiguration`: `AesGcmPushTokenProtector`와 `PushTokenProperties` 검증을 `dev`에도 적용한다. `test`, `local`, `integration`은 기존 동작을 유지한다.
- `application*.yml`: provider 속성과 기본값을 추가한다. FCM 자격 증명 placeholder는 `provider=fcm`일 때만 필수가 되게 한다.
- `docs/api/openapi.json`을 재생성한다.
- `docs/push-production-setup.md`와 `docs/adr/0008-adopt-fcm-push-delivery-pipeline.md`에 FID 전환, provider 속성, dev key 필수 조건을 반영한다.
- 승인된 테스트 계획에 따라 단위 테스트(`FcmHttpV1PushProviderTest`, `PushConfigurationTest`, 필요 시 `PushDeviceServiceTest`)와 요청 필드 변경의 영향을 받는 통합 테스트를 수정·추가한다.

## Explicit exclusions

- `push_device` 컬럼 이름 변경과 마이그레이션. FID는 기존 `token_ciphertext`·`token_fingerprint`에 같은 방식으로 저장한다.
- iOS용 `apns` 블록, 30일 이상 갱신되지 않은 기기 정리 job.
- dev에서 `provider=fcm` 지정과 worker scheduling 활성화(#366).
- test-server secret 주입 인프라(#364)와 `infra/**` 변경.
- 커밋, push, PR 생성은 실행 에이전트가 하지 않는다. 오케스트레이터가 사용자 승인 후 수행한다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `TASK.md`, 실행 지시, 독립 검증 | 오케스트레이터 | 사람의 범위 승인 |
| 테스트 계획 | 실행 에이전트(`/harness-test-plan`) | 사람의 계획 승인 |
| production Java, `application*.yml`, 문서, 테스트 | 실행 에이전트 | 오케스트레이터의 독립 검증 후 사람의 PR 승인 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 출력은 `TASK.md`(task-init) 하나였다.
- `gh`와 `harness`는 `env -u SSL_CERT_FILE -u SSL_CERT_DIR`로 시스템 CA를 사용한다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- `PushDeviceRequest`의 필드가 `fid`이고 `openapi.json` 재생성 diff에 반영된다.
- `FcmHttpV1PushProvider` 단위 테스트가 요청 body에 `message.fid`와 `message.android.priority=HIGH`가 있고 `message.token`이 없음을 검증한다.
- `PushConfigurationTest`가 `dev` + key 주입 시 `AesGcmPushTokenProtector` 등록, `dev` + key 누락 시 기동 실패, `provider=noop`이면 FCM 설정 없이 기동, `provider=fcm` + FCM 설정 누락 시 기동 실패를 검증한다.
- 기존 `UNREGISTERED`·`INVALID_ARGUMENT` 무효 기기 처리 테스트가 그대로 통과한다.
- `./harness check`, `./harness pr-ready --project-tests`, `git diff --check`가 통과한다.
- main 병합은 test-server 자동 배포를 시작하므로, 병합 후 배포 run의 성공과 dev 기동을 확인한다.
