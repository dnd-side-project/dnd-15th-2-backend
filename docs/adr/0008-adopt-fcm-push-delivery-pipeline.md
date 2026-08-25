---
id: ADR-0008
title: FCM HTTP v1과 데이터베이스 lease·fencing 기반 push delivery pipeline을 채택한다
status: accepted
category: ARCHITECTURE
date: 2026-08-25
tags:
  - notification
  - push
  - fcm
  - security
  - concurrency
related:
  - "#132"
  - "#179"
  - "#180"
  - "#182"
  - "ADR-0004"
---

# ADR-0008. FCM HTTP v1과 데이터베이스 lease·fencing 기반 push delivery pipeline을 채택한다

## 배경

알림 원장과 수신자별 `notification_delivery`는 이미 PostgreSQL에 저장되지만 실제
모바일 push provider로 전달하는 경계는 없었다. Issue #179는 Android와 iOS가 모두
FCM registration token을 사용한다는 모바일 계약을 전제로 다음 요구를 함께 해결해야
한다.

- 알림 생성 transaction과 외부 FCM 호출을 분리한다.
- worker crash와 중복 실행에서도 delivery를 안전하게 회수하고 stale 결과를 거절한다.
- 발송 직전에 최신 알림 설정, 차단, 기기와 대상 상태를 다시 확인한다.
- registration token 원문을 DB, 응답, 로그와 예외에 남기지 않는다.
- FCM이 수락한 메시지 식별자를 delivery 감사 정보로 보존한다.
- push 성공·실패·억제가 알림 원장과 콘텐츠 열람 자격을 변경하지 않게 한다.

초기 규모에서는 별도 broker를 운영할 근거가 부족하지만, 외부 호출을 요청 transaction
안에서 실행하면 응답 지연, 부분 실패와 재시도 정합성 문제가 생긴다. 기존 delivery
상태와 컬럼을 활용하면서도 다중 worker에 필요한 lease와 fencing을 명시해야 한다.

## 고려한 선택지

1. 알림 생성 요청의 DB transaction 안에서 FCM을 동기 호출한다.
2. SQS나 Kafka 같은 외부 queue를 추가하고 별도 consumer가 FCM을 호출한다.
3. 기존 PostgreSQL delivery 원장을 queue로 사용하고, 짧은 claim transaction과
   transaction 밖의 FCM 호출, generation-fenced terminal update로 분리한다.

token 저장 방식은 다음을 비교했다.

1. token 원문을 저장한다.
2. 단방향 hash만 저장한다.
3. 발송용 AES-256-GCM ciphertext와 검색·소유권 판정용 HMAC-SHA-256 fingerprint를
   분리해 저장한다.

provider 연동은 APNs 직접 adapter와 multi-provider routing을 추가하는 대신 Android와
iOS 모두 FCM HTTP v1을 사용하는 단일 adapter를 선택할 수 있다.

## 결정

delivery pipeline은 선택지 3을 채택한다. 기존 `notification_delivery`를 작업 원장으로
사용하고 FCM HTTP v1 adapter를 직접 연결한다. 별도 message broker와 APNs 직접
adapter는 추가하지 않는다.

### 기기 등록과 token 보호

- `POST /api/v1/notifications/devices`로 FCM registration token을 등록·갱신한다.
- `POST /api/v1/notifications/devices/revoke`로 본인 token을 멱등하게 해지한다. token이
  없거나 이미 해지됐어도 `204 No Content`를 반환한다.
- 같은 ACTIVE fingerprint를 다른 사용자가 등록하면 기존 소유 기기를 `REVOKED`로
  바꾸고 새 사용자에게 한 transaction과 lock으로 원자적으로 이전한다.
- 소유권 이전과 해지는 해당 기기의 `PENDING`·`FAILED` delivery를 `CANCELLED`로
  바꾼다.
- token은 자르지 않는다. 현재 서버 한도인 4096 bytes를 넘으면 거절한다.
- 발송 가능한 값은 AES-256-GCM으로 암호화한다. envelope에는 format version과 key ID를
  넣는다.
- 동일 token 판정에는 별도 key의 HMAC-SHA-256 fingerprint를 사용한다. encryption key와
  fingerprint key는 분리한다.
- encryption write는 current key만 사용하고 decrypt read는 current와 previous key를
  허용한다. current/previous key ID가 같거나 key 길이·pair 계약이 잘못되면 기동 단계에서
  실패한다.
- token과 key 설정 객체의 문자열 표현, 오류와 로그는 원문을 노출하지 않는다.

### claim, lease와 generation fencing

- due `PENDING`·`FAILED`와 lease가 만료된 `PROCESSING` delivery를 batch claim한다.
- 기존 `attempt_count`를 fencing generation으로 사용한다.
- 기존 `next_attempt_at`은 `PENDING`·`FAILED`에서는 다음 시도 시각, `PROCESSING`에서는
  lease 만료 시각으로 사용한다.
- claim과 terminal update만 짧은 DB transaction에서 처리한다. token 복호화와 provider
  호출은 transaction 밖에서 수행한다.
- terminal update는 delivery ID와 claim generation이 모두 일치할 때만 적용한다. 만료된
  worker가 뒤늦게 반환한 stale 결과는 저장하지 않는다.
- retryable failure는 backoff 뒤 `FAILED`, permanent failure와 최대 시도 초과는 `DEAD`로
  전이한다.
- invalid token은 현재 delivery를 terminal 처리하고 기기를 `INVALID`로 바꾸며 같은
  기기의 미발송 delivery를 한 transaction에서 `CANCELLED` 처리한다.

### 발송 직전 정책과 provider 결과

provider 호출 직전에 delivery·notification·device 상태, 소유자와 수신자 일치, 최신
global/type preference, 양방향 활성 차단과 대상 콘텐츠 유효성을 다시 읽는다. 발송할 수
없으면 provider를 호출하지 않고 delivery를 `CANCELLED`로 끝낸다.

FCM data payload는 `type`, `count`, `hasRemainingTime`만 허용한다. 내부 notification ID,
사용자 식별자와 서버가 만든 title/body는 보내지 않는다. 클라이언트가 일반 문구를 만들고
tap 시 알림함 홈을 연다.

provider port는 결과를 다음 네 의미로 제한한다.

- `Accepted(providerMessageId)`
- `InvalidToken`
- `RetryableFailure`
- `PermanentFailure`

FCM 성공 응답의 `name`을 `providerMessageId`로 해석하고 기존
`notification_delivery.provider_message_id`에 저장한다. 성공 응답에서 식별자를 읽을 수
없으면 성공으로 기록하지 않고 안전한 permanent failure로 분류한다.

### 논리 group 계층과 group당 1회 예산 (Issue #180)

Issue #179의 token 보호, FCM HTTP v1 adapter, delivery claim·lease·generation fencing,
payload allowlist 결정은 그대로 둔다. `notification_delivery`는 기기별 provider 결과
원장으로 유지한다.

Issue #180은 그 위에 논리 `push_dispatch_group`과 notification 단위 member를 둔다.
알림함 `notification` 행은 합치지 않고, 짧은 시간의 같은 종류 알림만 실제 provider
호출에서 한 건으로 묶는다. `ANSWER_RECEIVED`와 `ANSWER_REACTED`만 묶음 창을 쓰며 서로
다른 group이다. 다른 종류는 notification별 singleton이고, 질문 추천은 같은 cycle만 한
group에 모은다.

일일 예산은 기기 수가 아니라 사용자 local date의 논리 group 수를 센다. group 하나는 첫
provider 호출 직전에 예산을 한 번만 소비한다. 같은 group의 다중 기기 호출과 retry는
추가 소비하지 않으며, provider 실패나 응답 유실 뒤에도 이미 소비한 예산을 복원하지
않는다. 일반 알림은 질문글 도착용 예약량을 쓸 수 없고 `DIRECTION_POST_RECEIVED`만 전체
상한 안에서 예약량을 쓸 수 있다.

quiet hours, global/type OFF, 질문 추천 빈도는 provider 호출 직전 최신 snapshot으로
적용한다. `pushEnabled=false`가 quiet보다 우선이고, 저장된 quiet 세 값은 지우지 않는다.
억제·지연·취소는 notification 원장과 콘텐츠 열람 자격을 바꾸지 않는다.

묶음 창, 최대 지연, 일일 상한, 질문글 예약량, 추천 최소 간격은
`qello.notification.push.policy`로 외부 주입한다. 운영 다섯 값은 `UNKNOWN`이며 이 ADR은
숫자를 확정하지 않는다. worker 자동 polling은 계속 Issue #182 범위다.

### secret과 인프라 경계

기존 D-2 ECS·SSM 설계를 확장한다. FCM credential, AES key ring과 HMAC key는 사람이
SSM SecureString에 사전 등록하고 ECS runtime identity는 필요한 경로의 read-only 권한만
가진다. 애플리케이션 설정에는 placeholder와 논리적인 key ID만 둔다.

논리 설정 prefix는 `qello.notification.push.token-protection`이다. AES와 HMAC key
material은 standard Base64로 인코딩한 32-byte 값이어야 한다. 이 ADR과 Issue #179는
Terraform apply, 운영 secret 생성과 배포를 수행하지 않는다.

## 선택 이유

- 기존 PostgreSQL 원장과 상태 전이를 재사용해 새로운 broker의 비용과 운영 부담을
  추가하지 않는다.
- 외부 호출을 DB transaction 밖으로 분리해 긴 lock과 provider 지연 전파를 피한다.
- lease와 generation fencing으로 worker crash 복구와 stale 결과 차단을 함께 보장한다.
- 암호화와 fingerprint key를 분리해 발송 가능성과 동일 token 검색을 최소 권한으로
  나눈다.
- current-write·previous-read 경계가 무중단 key rotation과 rollback 기간을 제공한다.
- 최소 payload와 발송 직전 권위값 재검사로 privacy 노출과 오래된 설정에 따른 오발송을
  줄인다.
- FCM HTTP v1 단일 adapter는 승인된 Android/iOS 계약에 충분하며 provider 추상화를
  과도하게 확장하지 않는다.

## 결과

### 장점

- 알림 원장 저장 성공 여부가 FCM 가용성과 분리된다.
- 재시도, terminal 상태와 provider 메시지 식별자를 delivery 단위로 감사할 수 있다.
- 기기 해지·소유권 이전·invalid token 처리가 미발송 delivery와 원자적으로 연결된다.
- token 원문과 key material이 일반 DB 조회, 로그와 오류 경계에 노출되지 않는다.
- schema migration이나 신규 AWS resource 없이 현재 규모의 발송 worker를 도입할 수 있다.

### 단점

- FCM 수락 뒤 terminal update 전에 process가 종료되면 같은 push가 다시 발송될 수 있다.
  provider 호출과 DB update를 하나의 transaction으로 묶을 수 없으므로 이 pipeline은
  at-least-once 의미를 가진다.
- PostgreSQL이 원장과 queue 역할을 함께 맡아 발송량 증가 시 claim index와 row scan을
  다시 검토해야 한다.
- scheduler가 포함되지 않으므로 Issue #182 전에는 자동 polling이 활성화되지 않는다.
- 실제 Android/iOS foreground·background·종료·잠금 상태와 live FCM credential은 별도
  검증이 필요하다.
- previous key 제거 시점을 잘못 정하면 기존 ciphertext를 복호화할 수 없다.

## 배포와 복구

1. D-2의 보호된 infrastructure workflow에서 FCM credential, current AES key와 HMAC key를
   준비하고 ECS runtime identity의 read-only 접근을 검증한다.
2. secret 값이 아닌 placeholder가 주입된 task definition으로 애플리케이션을 배포한다.
   필수 credential이나 key 계약이 잘못되면 production profile은 fail-fast한다.
3. scheduler #182 전에는 dispatch worker를 자동 활성화하지 않는다. live FCM smoke test와
   Android/iOS 증거는 별도 승인된 환경에서 수행한다.
4. 회전 시 새 AES key를 current로 올리고 직전 key를 previous로 유지한다. 기존 ciphertext가
   모두 새 key로 갱신되거나 보존 기간이 끝나기 전에는 previous key를 제거하지 않는다.

애플리케이션 rollback 때도 기존 ciphertext를 읽을 수 있도록 승인된 current/previous key
set을 유지한다. credential 장애는 직전 credential version으로 복귀한다. key ID와 ciphertext
계약을 모르는 과거 애플리케이션으로 단독 rollback하지 않으며, 필요한 경우 이전 key를
current로 되돌린 뒤 검증된 버전을 배포한다.

worker crash는 lease 만료 후 다른 worker가 회수한다. FCM 수락 여부를 확정할 수 없는
delivery는 자동으로 성공 처리하지 않으며 at-least-once 재시도 위험을 운영 지표와 runbook에
명시한다. #179 범위의 schema 변경은 없으므로 그 시점의 migration rollback은 발생하지
않는다. Issue #180은 V28에 `push_dispatch_group`, `push_dispatch_group_member`,
`push_daily_budget`을 추가하며 기존 V1~V27과 delivery 컬럼은 수정하지 않는다.

## 범위 밖 결정

- Issue #180의 논리 group 계층과 group당 1회 예산 소비는 위 절에 기록한다. 운영 정책
  다섯 숫자, live FCM/mobile 실기기 검증과 production 주입 값은 이 ADR이 확정하지 않으며
  현재 `UNKNOWN`/unverified다.
- worker scheduler와 polling 활성화는 Issue #182에서 다룬다.
- Firebase Installation ID 전환, APNs 직접 adapter와 multi-provider routing은 후속 Issue로
  분리한다.
- 운영 규모에서 PostgreSQL claim plan이 허용되지 않으면 schema/index 변경 Issue와 설계를
  새로 승인한다.

## 관련 자료

- GitHub Issue: #179, #180
- Infrastructure 설계: Issue #132, PR #134, D-2 push secret addendum
- 관련 결정: `docs/adr/0004-adopt-terraform-for-aws-iac.md`
- 작업 계약: `TASK.md`
- 테스트 계획: `docs/test-plans/gh-179-TEST-PLAN-GH-179-PUSH-DELIVERY.md`,
  `docs/test-plans/gh-180-TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET.md`
- 테스트 보고서: `docs/reports/tests/gh-179-TEST-PLAN-GH-179-PUSH-DELIVERY.md`,
  `docs/reports/tests/gh-180-TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET.md`

## 승인

- 승인자: 사용자
- 승인 시각: 2026-08-25
