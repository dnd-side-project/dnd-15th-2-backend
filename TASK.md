# GitHub Issue #179 Task Contract

> Generated at: `2026-08-24T18:38:52+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `푸시 토큰 등록과 발송 파이프라인`
- GitHub Issue: `#179`
- Branch: `feat/gh-179-push-delivery-pipeline`
- Base branch: `main`

## Objective

- 기존 `notification_delivery` 적재 흐름 뒤에 실제 FCM HTTP v1 발송 경계를 연결한다.
- 인증된 사용자가 자신의 FCM registration token을 등록·해지할 수 있게 하고, token 원문을
  저장·응답·log·예외·test fixture에 남기지 않는다.
- 발송 직전 최신 기기 상태, 알림 preference, 활성 차단과 대상 유효성을 다시 확인하고,
  provider 결과를 재시도 가능한 delivery 상태와 기기 상태에 안전하게 반영한다.
- `notification` 원장과 원래 콘텐츠 열람 자격은 push 성공·실패·억제와 독립적으로 보존한다.

## Scope

### Device API와 token 보호

- `POST /api/v1/notifications/devices`
  - 인증된 사용자의 FCM registration token을 등록한다.
  - 같은 사용자의 재등록은 새 ciphertext와 `last_seen_at`을 갱신한다.
  - 다른 사용자가 같은 ACTIVE fingerprint를 등록하면 기존 소유 행을 `REVOKED`로 바꾸고
    새 사용자에게 한 transaction과 lock으로 원자적 이전한다.
  - 이전 기기의 `PENDING`·`FAILED` delivery를 `CANCELLED` 처리한다.
- `POST /api/v1/notifications/devices/revoke`
  - request body의 `platform`, `token`으로 본인 기기를 식별한다.
  - token이 없거나 이미 해지됐어도 `204 No Content`로 멱등하게 끝낸다.
  - 대상 기기의 `PENDING`·`FAILED` delivery를 `CANCELLED` 처리한다.
- token 보호
  - AES-256-GCM ciphertext와 별도 key의 HMAC-SHA-256 fingerprint를 사용한다.
  - ciphertext envelope에 version과 key ID를 포함한다.
  - encryption key와 fingerprint key를 분리하고 rotation 가능한 읽기·쓰기 경계를 둔다.
  - token 값 객체와 모든 오류·log 경계는 원문을 redaction한다.

### Provider와 dispatch worker

- provider domain port와 FCM HTTP v1 adapter 하나를 추가한다.
  - Google Auth Library로 access token을 얻고 Spring `RestClient`로 호출한다.
  - FCM 오류를 `ACCEPTED`, `INVALID_TOKEN`, `RETRYABLE_FAILURE`,
    `PERMANENT_FAILURE`의 제한된 의미로 변환한다.
- due delivery를 batch claim하는 dispatch worker를 추가한다.
  - 기존 `attempt_count`를 fencing generation으로 사용한다.
  - 기존 `next_attempt_at`을 상태별 다음 시도 시각 또는 PROCESSING lease 만료 시각으로 사용한다.
  - 만료된 `PROCESSING`을 회수하고 stale generation의 terminal update를 거절한다.
  - provider 호출은 긴 DB transaction 밖에서 수행하고 claim·terminal update만 짧은
    transaction으로 처리한다.
- push delivery 전용 retry policy를 추가한다.
  - retryable failure는 backoff 후 `FAILED`로 전이한다.
  - 최대 시도 초과와 permanent failure는 `DEAD`로 전이한다.
  - invalid token은 현재 delivery를 terminal 처리하고 기기를 `INVALID`로 바꾸며 같은 기기의
    미발송 delivery를 `CANCELLED` 처리한다.

### 발송 직전 재검사와 payload

- provider 호출 직전에 다음 권위값을 다시 읽는다.
  - delivery·notification·device 존재와 현재 상태
  - device 소유자와 notification 수신자 일치
  - 최신 global/type preference
  - 알림 actor가 있을 때 양방향 활성 차단
  - 대상 콘텐츠의 현재 유효성
- preference OFF, 활성 차단, REVOKED/INVALID device, 회수된 notification 또는 대상은
  provider를 호출하지 않고 `CANCELLED`로 끝낸다.
- FCM data payload는 `type`, `count`, `hasRemainingTime`만 허용한다.
  - #180 전까지 `count`는 1이다.
  - `notificationId`와 server `title/body`를 포함하지 않는다.
  - client가 일반 문구를 조립하고 tap 시 알림함 홈을 연다.

### 승인된 아키텍처 결정

- `DEC-179-001`: FCM HTTP v1 직접 호출
- `DEC-179-002`: 같은 ACTIVE token의 원자적 소유권 이전
- `DEC-179-003`: `POST /api/v1/notifications/devices/revoke`
- `DEC-179-004`: AES-256-GCM + HMAC-SHA-256 + key 분리·version·rotation
- `DEC-179-005`: 관리형 secret 저장소 + 최소 권한 runtime identity
- `DEC-179-006`: 기존 delivery 컬럼을 lease/generation으로 재사용
- `DEC-179-007`: 발송 억제 delivery는 `CANCELLED`
- `DEC-179-008`: 최소 세 필드 data payload + client 문구 + 알림함 홈
- `DEC-179-009`: FCM `Accepted(providerMessageId)` 결과를 `notification_delivery.provider_message_id`에 저장

상세 근거와 남은 확인 항목은 Git에서 제외되는
`docs/reports/private/notification/*.local.md`를 참고한다.

## Design and implementation gates

- Application task ID: `GH-179-PUSH-DELIVERY-PIPELINE`
- Infrastructure `DESIGN-ID`: `D-2` (GitHub Issue #132 / PR #134, #179 push secret addendum)
- Infrastructure design status: `APPROVED_FOR_BUILD`
- Approval evidence: D-2 PR #134의 `@Byuntil` 승인(2026-08-12)과 #179 작업 승인 결정(2026-08-24)
- Secret ownership: `@Byuntil`, `@tkv00`; FCM credential·AES key ring·HMAC key는 사람이 SSM
  SecureString에 사전 등록하고, ECS runtime identity는 해당 경로 read-only만 가진다. 회전은
  보호된 infrastructure workflow에서 current/previous key를 함께 유지하는 방식으로 수행하며,
  rollback은 이전 key version과 credential로 복귀한다. #179에서는 apply하지 않는다.
- D-2 보강 계약: FCM HTTP v1 credential, AES-256-GCM encryption key ring, HMAC-SHA-256
  fingerprint key를 서로 분리하고 version/key ID를 포함한다. 현재 key는 쓰기에만 사용하고
  이전 key는 복호화 기간 동안 읽기에만 사용한다.
- Task 8 runtime wiring assumption: 논리 prefix는 `qello.notification.push.token-protection`이다.
  AES/HMAC key material은 standard Base64로 인코딩한 정확히 32 bytes이며 current key ID,
  current AES key와 HMAC key는 필수다. previous key ID와 previous AES key는 함께 있을 때만
  read key ring에 추가하고, 암호화 write는 항상 current key만 사용한다. 운영 값은 placeholder를
  통해 관리형 secret 저장소에서 주입하며 실제 secret 값은 저장소에 기록하지 않는다.
- 모바일 FCM token 계약: Android/iOS 모두 FCM registration token을 전달한다.
  `platform`은 `ANDROID`/`IOS`이며 등록 정보 획득·갱신 시 `POST /devices`, 로그아웃 시
  `POST /devices/revoke`를 호출한다. 서버는 token을 truncate하지 않고, 설정된 최대 byte를
  초과하면 거절한다(현재 기본값 4096 bytes). FID 전환은 후속 이슈로 분리한다.
- Firebase project와 iOS APNs 연결 관리 주체: D-2 secret ownership이 관리하는 Firebase
  project. iOS는 APNs 연결이 된 Firebase project를 사용하며 APNs 직접 adapter는 추가하지 않는다.
- Test plan: `docs/test-plans/gh-179-TEST-PLAN-GH-179-PUSH-DELIVERY.md`
- Test plan status: `APPROVED`

다음 조건을 모두 충족하기 전에는 애플리케이션 구현을 시작하지 않는다.

1. 승인된 Infrastructure Design Report의 `DESIGN-ID`와 사람 승인 증거가 기록돼 있다.
2. 설계 상태가 `APPROVED_FOR_BUILD`이고 D-2 push secret 보강 계약이 기록돼 있다.
3. Android·iOS가 server에 FCM registration token을 전달하는 계약이 기록돼 있다.
4. 위험 기반 JUnit 5 시나리오가 승인됐고 P0/P1 범위를 유지한다.
5. 승인된 테스트 계획과 구현 파일 범위가 이 문서에 연결돼 있다.

## Explicit exclusions

- DELETE body와 공개 device handle
- APNs 직접 adapter와 multi-provider routing
- device 목록 조회·관리 UI
- 특정 notification deep link와 공개 notification 식별자
- 묶음, 일일 즉시 push 상한, 방해 금지 시간 처리 — #180
- worker scheduler 또는 polling 활성화 — #182
- DB schema migration. 성능 검증에서 필수로 판정되면 Issue와 설계를 다시 승인한다.
- `notification` 원장, 알림함 목록·읽음 의미와 콘텐츠 열람 자격 변경
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Device API·token protection | Application implementer | Security reviewer, API reviewer |
| Delivery claim·lease·retry | Application implementer | Independent concurrency/DB reviewer |
| FCM provider adapter | Application implementer | Security reviewer, failure-mapping reviewer |
| Dispatch eligibility·payload | Application implementer | Product/API reviewer, privacy review |
| Secret 저장·runtime identity | Infrastructure workflow | 승인된 Infrastructure Design Report와 사람 승인 |
| JUnit 5 scenarios·report | Test executor | 구현자와 독립된 test-plan/test-run 검토 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 clean이었다.
- 사용자가 작성·검토한 `docs/reports/private/notification/*.local.md` 6개는 `.gitignore` 대상이다.
  브랜치 전환 후에도 보존됐으며 구현 과정에서 삭제·이동·덮어쓰지 않는다.
- GitHub Issue #179 본문은 2026-08-24 승인된 DEC-179-001~008과 POST revoke 계약으로
  갱신됐다.
- Project `Qello Backend Roadmap`의 기존 Sprint `Week 7 · Interaction`, Priority `P2`,
  Work type `Feature`는 보존하고 Status만 `In Progress`로 변경했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

추가 필수 검증:

- 승인된 `/harness-test-plan`의 단위·통합·동시성·외부 API 실패 시나리오
- token 원문 비노출 정적·동적 검사
- 같은 fingerprint 동시 등록과 소유권 이전 transaction 검증
- lease 만료 회수와 stale generation fencing 검증
- preference·차단 변경 직후 provider 미호출 검증
- FCM invalid/retryable/permanent 응답 mapping 검증
- payload allowlist 검증
- 실제 Android/iOS의 foreground·background·종료·잠금 상태 검증은 모바일 팀 증거로 연결

## Completion criteria

- [x] 승인된 `DESIGN-ID`와 사람 승인 증거가 구현 시작 전 이 문서에 기록되어 있다.
- [x] 승인된 JUnit 5 테스트 계획과 구현 범위가 연결되어 있다.
- [x] 등록·재등록·소유권 이전·해지 API가 인증·멱등·동시성 계약을 지킨다.
- [x] token 원문이 DB, log, 응답, 예외 메시지에 남지 않으며 동적 redaction assertion으로 검증된다.
- [x] provider 실패가 분류되고 retryable failure는 backoff, 상한 초과는 `DEAD`로 전이한다.
- [x] worker crash 뒤 lease 만료 delivery가 회수되고 stale generation 갱신은 거절된다.
- [x] invalid token은 device `INVALID`와 같은 기기의 미발송 delivery 취소로 이어진다.
- [x] 발송 직전 preference·차단·기기·notification·대상 상태를 다시 확인한다.
- [x] 발송 억제와 실패가 `notification`과 열람 자격을 변경하지 않는다.
- [x] payload가 세 필드 allowlist만 사용하고 금지된 정보와 내부 식별자를 포함하지 않는다.
- [x] 기본 검증과 승인된 테스트 계획이 모두 통과했으며, live FCM/mobile·rotation drill과 origin/main sync 의존 항목은 남은 위험으로 명시된다.
