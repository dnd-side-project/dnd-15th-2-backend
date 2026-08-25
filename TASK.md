# GitHub Issue #180 Task Contract

> Generated at: `2026-08-25T12:35:29+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `묶음 발행과 푸시 예산 억제`
- GitHub Issue: `#180`
- Branch: `feat/gh-180-push-bundling-budget`
- Base branch: `main`
- Parent Issue: `#183` (`F07`)
- Application task ID: `GH-180-PUSH-BUNDLING-BUDGET`
- Application design ID: `APP-DESIGN-GH-180-001`
- Design status: `APPROVED_FOR_PLAN`
- Design approval: `@Byuntil`, 2026-08-25T12:48:40+09:00

## Objective

- 짧은 시간에 생긴 같은 종류의 알림을 실제 provider 호출에서만 한 건으로 묶는다.
- 사용자 단위 일일 즉시 push 예산을 원자적으로 집계하고 질문글 도착에 예약 우선권을 준다.
- 기존 `quietHours` 설정을 실제 발송 지연과 최대 지연 만료에 적용한다.
- 같은 질문 추천 주기에는 push를 한 번만 시도하고 추천 주기와 push 빈도를 분리한다.
- 모든 억제·지연·묶음 결과와 무관하게 `notification` 1행=1알림과 콘텐츠 열람 자격을 보존한다.

## Approved decisions

- `DEC-180-001`: 논리적 `push_dispatch_group`과 notification 단위 member를 별도 저장한다.
  `notification_delivery`는 기기별 provider 결과 원장으로 유지한다.
- `DEC-180-002`: `ANSWER_RECEIVED`와 `ANSWER_REACTED`만 설정된 묶음 창을 사용한다.
  두 종류는 서로 다른 group이며 다른 알림 종류는 즉시 group 하나에 notification 하나만 둔다.
- `DEC-180-003`: 일일 예산은 기기 수와 무관한 사용자 단위이며 `user_account.timezone`의
  local date를 기준으로 집계한다. 일반 알림은 질문글 도착용 예약량을 사용할 수 없고
  `DIRECTION_POST_RECEIVED`만 전체 상한 안에서 예약량을 사용할 수 있다.
- `DEC-180-004`: group 하나는 첫 provider 호출 직전에 예산을 한 번만 소비한다. 같은 group의
  다중 기기 호출과 retry는 추가 소비하지 않으며 실패해도 이미 소비한 예산을 복원하지 않는다.
- `DEC-180-005`: `pushEnabled=false`가 최우선이다. 전체 push를 꺼도 저장된 `quietHours`는
  지우지 않으며 다시 켜면 기존 일정이 적용된다. `quietHours=null`은 방해 금지 OFF다.
- `DEC-180-006`: quiet 시간 안에서는 예산을 소비하거나 provider를 호출하지 않고 종료 시각까지
  지연한다. 첫 notification 시각부터 최대 지연을 넘으면 미발송 delivery만 `CANCELLED` 처리한다.
- `DEC-180-007`: 질문 추천은 cycle ID가 같은 notification을 같은 논리 group에 넣는다.
  다른 cycle이라도 마지막 실제 push 시도부터 추천 알림 최소 간격 안이면 push만 억제한다.
- `DEC-180-008`: payload key는 `type`, `count`, `hasRemainingTime`만 유지한다. `count`는 해당
  기기에 발송 가능한 서로 다른 notification 개수이며 본문·닉네임·위치·내부 ID는 넣지 않는다.
- `DEC-180-009`: 자동 polling과 scheduler 활성화는 #182 범위다. #180 worker는 명시적 호출만 제공한다.
- `DEC-180-010`: 묶음 창, 최대 지연, 일일 상한, 질문글 예약량과 추천 최소 간격은 코드 상수가
  아닌 설정값이다. 운영 값은 `UNKNOWN`이며 승인 전 임의 기본값을 추가하지 않는다.

## Scope

### Dispatch grouping

- 미소속 `notification_delivery`가 가리키는 notification을 논리 group에 멱등하게 편입한다.
- group은 수신자·알림 종류를 공유하며 여러 ACTIVE 기기의 delivery를 포함할 수 있다.
- 묶음 창 종료 전 도착한 같은 종류 notification은 열린 group에 합류한다.
- provider 호출 전 member별 preference·차단·notification·대상 상태와 device 상태를 다시 확인한다.
- 유효하지 않은 member delivery만 취소하고 유효한 notification 수로 payload `count`를 계산한다.
- group claim, lease 회수와 terminal 반영은 generation fence를 사용한다.

### Daily budget and priority

- `(user_id, local_date)`별 예산 원장을 추가하고 동시 worker의 소비를 row lock/조건부 update로 직렬화한다.
- 일반 알림은 `daily-limit - direction-reserved`까지만 소비한다.
- `DIRECTION_POST_RECEIVED`는 `daily-limit`까지 소비할 수 있다.
- 상한 초과 group의 미발송 delivery만 `CANCELLED` 처리한다.

### Quiet hours and recommendation frequency

- 기존 `notification_user_setting.quiet_start`, `quiet_end`, `quiet_zone_id`를 재사용한다.
- overnight, DST gap/overlap과 설정 변경 후 재검사를 `Clock`과 `ZoneId` 기반 정책으로 처리한다.
- quiet 지연 중 global/type OFF, 차단, 대상 만료 또는 기기 해지가 생기면 provider 호출 없이 취소한다.
- 질문 추천 cycle 중복과 cycle 간 최소 발송 간격을 push group 이력으로 판정한다.

### Schema and configuration

- 현재 migration inventory 기준 `V28`에 `push_dispatch_group`, `push_dispatch_group_member`,
  `push_daily_budget`와 claim/lookup index를 추가한다. rebase에서 버전 충돌이 나면 기존 migration을
  보존하고 다음 빈 버전으로 roll-forward한다.
- 설정 prefix는 `qello.notification.push.policy`를 사용한다.
- 설정 항목은 `bundle-window`, `max-delay`, `daily-limit`, `direction-reserved`,
  `recommendation-min-interval`이다.
- production 값은 외부 주입하고 unit/integration test는 각 scenario에 값을 명시한다.

## Design and implementation gates

- Design spec: `docs/superpowers/specs/2026-08-25-push-bundling-budget-design.md`
- Test plan: `docs/test-plans/gh-180-TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET.md`
- Test plan status: `APPROVED`
- Test plan approval: `@Byuntil`, 2026-08-25T12:48:40+09:00
- Implementation plan: `docs/superpowers/plans/2026-08-25-push-bundling-budget.md`
- Implementation plan status: `APPROVED_FOR_BUILD`
- Implementation plan approval: `@Byuntil`, 2026-08-25T13:03:45+09:00
- 단계별 implementation plan은 design spec과 test plan의 사람 승인 뒤 작성한다.
- production code, migration과 test 구현은 implementation plan 승인 전 시작하지 않는다.

## Explicit exclusions

- `notification` 행 병합, 삭제 또는 알림함 목록·읽음·열람 자격 의미 변경
- 새로운 알림 설정 API, 별도 quiet toggle 또는 `quietHours` 저장 계약 변경
- 실제 운영 정책 숫자 확정과 코드 기본값 추가
- worker scheduler 또는 polling 활성화 — #182
- FCM/APNs provider 종류 추가, 모바일 UI, 잠금화면 문구와 deep link 변경
- 기존 `notification_delivery` 상태 원장 제거 또는 과거 migration 수정
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| group domain·policy properties | Application implementer | Product/policy reviewer |
| PostgreSQL group·budget adapter | Application implementer | Independent DB/concurrency reviewer |
| quiet·budget·recommendation policy | Application implementer | Product reviewer, timezone review |
| dispatch worker·FCM payload | Application implementer | Failure/retry reviewer, privacy review |
| JUnit 5 scenarios·report | Test executor | 구현자와 독립된 test-plan/test-run 검토 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 clean이었다.
- `main`과 `origin/main`은 `ab4ac81d579d7cb49c1e045bfe74ac646b68abb9`로 일치했다.
- GitHub Project의 Sprint `Week 7 · Interaction`, Priority `P2`, Work type `Feature`,
  Status `Todo`는 변경하지 않았다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

추가 필수 검증:

- 묶음 창 경계와 답변/공감 분리 단위 테스트
- 두 worker의 group 편입·claim·예산 소비 동시성 통합 테스트
- 다중 기기에서 예산 1회 소비와 기기별 provider 결과 분리 테스트
- global/type OFF와 quiet 보존·지연·최대 지연 취소 테스트
- overnight와 DST gap/overlap 테스트
- 질문 추천 동일 cycle 중복 및 cycle 간 최소 간격 테스트
- payload `count`와 privacy allowlist 테스트
- 원장·열람 자격 불변 회귀 테스트
- V28 migration contract, 실제 PostgreSQL migration과 query plan 테스트

## Completion criteria

- [x] 승인된 design spec과 JUnit 5 test plan이 연결돼 있다.
- [x] 같은 묶음 창의 같은 종류 notification이 기기별 provider 호출 한 번으로 나간다.
- [x] `ANSWER_RECEIVED`와 `ANSWER_REACTED`가 섞이지 않는다.
- [x] 사용자 단위 일일 예산과 질문글 예약 우선권이 동시성에서도 상한을 지킨다.
- [x] global OFF가 quiet보다 우선하며 저장된 quiet 값은 보존된다.
- [x] quiet 종료 후 최신 권위값을 다시 확인해 발송하거나 최대 지연 뒤 취소한다.
- [x] 같은 질문 추천 cycle에는 push를 한 번만 시도하고 cycle 간 최소 간격을 지킨다.
- [x] 다중 기기와 retry가 group 예산을 중복 소비하지 않는다.
- [x] payload는 세 필드 allowlist와 실제 유효 notification `count`를 지킨다.
- [x] push 억제·실패·취소가 notification 원장과 콘텐츠 열람 자격을 변경하지 않는다.
- [ ] 기본 검증과 승인된 테스트 계획이 통과하고 미검증 운영 값과 #182 의존성을 보고한다.
