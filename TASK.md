# GitHub Issue #123 Task Contract

> Generated at: `2026-08-14T15:14:47+09:00`
> Refreshed against PR #140 main at: `2026-08-14T17:21:55+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `수신자 확정 인앱 알림 fan-out 워커`
- GitHub Issue: `#123`
- Branch: `feat/gh-123-direction-notification-fanout`
- Base branch: `main`
- Test plan: `TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT`
- Test plan approval: `APPROVED` — 사용자가 2026-08-14 구현을 승인했다.
- Confirmed policy: 사용자가 `DIRECTION_POST_RECEIVED` 알림을 비활성화하면
  `Notification`과 `notification_delivery`를 모두 생성하지 않고 source Outbox를
  정상 처리한다. 설정 행이 없으면 기본 활성으로 해석한다.
- Confirmed policy: #123은 ACTIVE PushDevice별 `PENDING notification_delivery`까지만
  생성하고 Provider 호출은 제외한다. 수신 상태별 판정표와 PostRecipient lock 기준의
  account/post/block/status 및 preference/device 조회 시점 snapshot 선형화를 적용한다.

## Objective

- #120이 수신자별로 생성한 `RECIPIENTS_CONFIRMED` Outbox를 소비해 수신 자격을
  인앱 `DIRECTION_POST_RECEIVED` 알림으로 변환한다.
- #140의 GLOBAL 미리보기·제출·매칭 변경은 upstream 수신자 선정으로만 취급하며,
  #123은 확정된 `PostRecipient`와 그 aggregate Outbox만 소비한다.
- 사용자 설정, 계정·질문글 상태, 만료, 차단과 `PostRecipient` 자격을 처리 직전에
  재확인하고, 알림·기기 전달 작업·source Outbox 완료를 event별 transaction으로
  원자적으로 반영한다.
- Push 기기 부재·전달 실패·재처리가 `PostRecipient`와 수신 슬롯 또는 인앱 알림
  자격에 영향을 주지 않게 한다.

## Scope

- `{RECIPIENTS_CONFIRMED}` event type 전용 batch claim과 event별 독립 transaction
- `aggregate_type=POST_RECIPIENT`, `aggregate_id=postRecipientId` 권위 계약 검증
- 사용자별 `DIRECTION_POST_RECEIVED` 알림 설정 조회와 기본 활성 계약
- 발신자·수신자 계정 ACTIVE, 질문글 ACTIVE·미삭제·미만료, 양방향 활성 차단 없음과
  `PostRecipient` 상태 재확인
- `PostRecipient` row lock을 fan-out 자격 판정의 선형화 지점으로 사용하고, 설정·기기
  상태는 조회 시점에 commit된 snapshot을 적용
- `(recipient_id, dedup_key)` 기반 Notification 멱등 생성
- ACTIVE PushDevice별 `(notification_id, push_device_id)` PENDING Delivery 멱등 생성
- notification·delivery 생성과 owner/generation/lease fenced source complete의 원자성
- retryable/permanent/DEAD/stale lease 분류와 batch 실패 격리
- 단위·PostgreSQL 통합·동시성·rollback·privacy 테스트와 테스트 보고서

## Explicit exclusions

- FCM/APNs Provider 호출, Push token 복호화와 provider 응답 처리
- `notification_delivery` dispatch worker와 실제 Push retry 실행
- Push 예산, rate limit, `Retry-After`, 조용한 시간대 정책
- 인앱 알림 목록·읽음·숨김 API와 수신함·열람·넘김 API(#124)
- #140의 preview·submit·media HTTP API와 GLOBAL 후보 선정 정책 변경
- 답변 알림, 답변 공감 알림과 범용 event-handler framework 확장
- 과거 Notification의 일괄 삭제·REVOKED 보정과 운영 reconciliation job
- scheduler/polling production activation과 운영 retry 숫자 결정
- 신규 인프라, 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Notification/Delivery 멱등 JDBC·PostRecipient lock 경계와 persistence 테스트 | Notification persistence executor | 기존 save 예외 계약 보존, unique·ACTIVE device query·설정 기본값·lock 리뷰 |
| confirmed event worker와 단위 테스트 | Notification worker executor | event 권위값, Clock, suppress/retry/permanent/stale 분류 리뷰 |
| event별 transaction·자격·부분 실패 통합 테스트 | Notification integration executor | preference·계정·post·만료·차단·상태 판정표와 rollback 리뷰 |
| 동시 worker·lease reclaim·설정/차단 경합 테스트 | Concurrency executor | barrier, timeout, fencing, 최종 row reconciliation 리뷰 |
| 기존 #119/#120/#140 및 수신함 권한 회귀 | Regression verifier | production 수정 없이 source/notification/recipient 분리와 GLOBAL matching 회귀 검증 |
| 전체 변경 | Independent reviewer | Issue/TASK/승인 계획과 diff·실행 증거 독립 검증 |

각 실행자는 승인된 테스트 계획의 비중복 소유 파일만 수정하고 다른 실행자나
사용자의 변경을 되돌리지 않는다. 파일 재배정이 필요하면 구현 전에 계획을 갱신한다.

## Existing user-owned changes

- 재개 시 `main`과 `origin/main`은 PR #140 병합 commit
  `a8e307d9b46ab9ba14fc1306dfa846fc7e371b32`에서 일치했고 작업 트리는 clean이었다.
- #123 브랜치는 기존 기준 `a7bab227cde90e22377141bd16b51d14d65ef69f`에서
  위 commit으로 순수 fast-forward했다.
- 기존 stash는 삭제하거나 전체 적용하지 않았다. #123의 `TASK.md`와 테스트 계획만
  선택 복구했고, stash에 함께 있던 별도 media 추가 작업은 작업 트리에 적용하지 않았다.
- 작업 시작 당시에는 이 `TASK.md`와 #123 테스트 계획 초안만 변경되어 있었으며,
  승인 후 구현·테스트·검증 보고서가 추가되었다.

## Validation

```bash
./gradlew test --tests "com.dnd.qello.notification.fanout.RecipientNotificationFanOutWorkerTest" --max-workers=1 --no-daemon
./gradlew test --tests "com.dnd.qello.direction.matching.DirectionMatchingWorkerTest" --max-workers=1 --no-daemon
./gradlew integrationTest --tests "com.dnd.qello.NotificationFanOutPersistenceIntegrationTest" --max-workers=1 --no-daemon --no-parallel --rerun-tasks
./gradlew integrationTest --tests "com.dnd.qello.RecipientNotificationFanOutWorkerIntegrationTest" --max-workers=1 --no-daemon --no-parallel --rerun-tasks
./gradlew integrationTest --tests "com.dnd.qello.RecipientNotificationFanOutWorkerConcurrencyIntegrationTest" --max-workers=1 --no-daemon --no-parallel --rerun-tasks
./gradlew integrationTest --tests "com.dnd.qello.OutboxLeaseIntegrationTest" --tests "com.dnd.qello.DirectionMatchingWorkerIntegrationTest" --tests "com.dnd.qello.DirectionMatchingWorkerConcurrencyIntegrationTest" --max-workers=1 --no-daemon --no-parallel --rerun-tasks
./gradlew integrationTest --tests "com.dnd.qello.AnswerSafetyNotificationPersistenceIntegrationTest" --tests "com.dnd.qello.InboxDetailScopeIntegrationTest" --max-workers=1 --no-daemon --no-parallel --rerun-tasks
./harness test-run --id TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] `RECIPIENTS_CONFIRMED`만 claim하고 payload가 아닌 `POST_RECIPIENT` aggregate를
  권위값으로 사용한다.
- [x] 설정이 없거나 활성인 자격 보유자에게 `DIRECTION_POST_RECEIVED/UNREAD`
  Notification이 수신자·질문글 조합당 한 번 생성된다.
- [x] 알림 설정 비활성, 비활성 발신자·수신자 계정, 비노출 질문글, 만료, 활성 차단,
  `ANSWERED/SKIPPED/EXPIRED/BLOCKED` 상태는
  Notification/Delivery 없이 source event를 성공 처리한다.
- [x] ACTIVE PushDevice별 PENDING Delivery가 한 번 생성되고 기기 0개는 인앱 알림 성공을
  막지 않는다.
- [x] Notification·Delivery 생성과 source complete는 전부 commit하거나 전부 rollback한다.
- [x] Push Delivery의 FAILED/DEAD가 Notification·PostRecipient·수신 슬롯을 변경하지 않는다.
- [x] Notification 존재나 상태가 `PostRecipient` 기반 수신함 조회 권한을 부여하지 않는다.
- [x] stale lease, replay, 동시 claim과 부분 실패에도 논리 중복이나 부분 반영이 없다.
- [x] 좌표·거리·방위·region·본문·Push token이 Outbox/결과/로그에 노출되지 않는다.
- [x] #140 GLOBAL matching으로 다른 표시 지역에 확정된 recipient도 #123이 지역·거리·방위를
  재계산하지 않고 `POST_RECIPIENT` aggregate 기준으로 fan-out한다.
- [x] 승인된 P0 테스트와 저장소 필수 검증이 통과하고 테스트 보고서가 남는다.
- [x] 실행하지 못한 Provider/production activation 검증과 남은 위험을 보고서에 기록한다.
