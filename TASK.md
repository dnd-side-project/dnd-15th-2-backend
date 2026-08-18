# GitHub Issue #127 Task Contract

> Generated at: `2026-08-18T15:06:51+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `방향 매칭 동시성·복구·성능 검증`
- GitHub Issue: `#127`
- Branch: `test/gh-127-direction-matching-vertical-flow`
- Base branch: `main`
- Test plan identifier: `TEST-PLAN-DIRECTION-MATCHING-VERTICAL-FLOW`
- Parent: `#116`(OPEN, 에픽). 선행 `#117`~`#126` 전부 CLOSED 확인.

## Objective

- `#116` 방향 매칭 수직 흐름의 마지막 하위 작업(M12)이다. 신규 기능을 구현하지
  않고, 이미 완성된 흐름을 실제 PostgreSQL/PostGIS 환경에서 독립 검증한다.
- 이 이슈의 가치는 "새 테스트를 많이 쓰는 것"이 아니라 **기존 58개 통합 테스트가
  구조적으로 볼 수 없는 것만 보는 것**이다. `#117`~`#126`이 각자 자기 테스트
  계획과 보고서를 이미 소유하고 있으므로, 그 범위를 다시 쓰지 않는다.

## Scope

기존 커버리지를 제외하고 남는 세 가지 공백만 신규 작성한다.

1. **Gap A — E2E 수직 흐름.** 현재 어떤 테스트도 제출부터 만료까지를 한 번에
   관통하지 않는다. 전부 단계-로컬 픽스처에서 시작하므로 단계 경계의 계약
   불일치를 아무도 잡지 못한다. 앞 단계 산출물만 다음 단계 입력으로 쓰는
   관통 테스트를 추가한다.

   ```text
   ActiveUserPresenceController (위치 갱신)
     → DirectionPostController preview
     → DirectionPostController submit (멱등 제출 → RECIPIENT_MATCH_REQUESTED)
     → DirectionMatchingWorker.processBatch (→ RECIPIENTS_CONFIRMED)
     → RecipientNotificationFanOutWorker
     → Inbox 조회·열람·넘김
     → 답변 제출·공개
     → RecipientExpirationSweepWorker / SkipConfirmationSweepWorker
   ```

2. **Gap B — 성능·규모.** 저장소 전체에 `EXPLAIN` 사용이 0건이다. 10,000명
   합성 `active_user_presence`에서 `V1__create_direction_communication_schema.sql:1102-1103`의
   부분 GIST 인덱스(`USING GIST (position) WHERE receive_allowed = TRUE`)를
   후보 조회가 실제로 사용하는지 확인된 적이 없다.

3. **Gap C — 체인 종단 복구.** 단계별 임대 만료·재시도·`DEAD` 전환은 검증되어
   있으나, 체인이 중간에 끊겼을 때 최종 상태(질문글 상태, 수신 자격, 슬롯 잔여)는
   미검증이다.

## Design decisions (구현 전 확정, 리뷰 필요)

1. **성능 스위트는 기본 게이트에서 분리한다.** `check`가 `integrationTest`에
   의존하므로 10,000행 적재를 기본 스위트에 넣으면 모든 로컬 커밋과 CI가
   느려진다. `@Tag("performance")`를 붙이고 `integrationTest`에서 제외한 뒤
   전용 `performanceTest` Gradle 태스크로만 실행한다. `build.gradle`을 수정한다.
2. **성능 판정 기준은 실행계획이고 지연은 증거다.** `EXPLAIN (ANALYZE, BUFFERS,
   FORMAT JSON)` 출력에서 부분 GIST 인덱스 사용과 `active_user_presence`의 Seq
   Scan 부재를 단언해 PASS/FAIL을 결정한다. preview·matching 지연(ms)은 보고서에
   수치로 기록하되 단언하지 않는다. Testcontainers의 머신 편차로 flaky해지는
   임계값 단언은 회귀 탐지력보다 잡음을 더 만든다.
3. **`performanceTest`는 `check`에 포함되지 않으므로 수동 실행이다.** 실행하지
   않은 검증으로 오인되지 않도록 실행 명령과 결과를 보고서와 PR에 명시한다.
4. **구현 결함을 발견해도 이 브랜치에서 고치지 않는다.** 이 이슈는
   `type: test`다. 인덱스 미사용이나 단계 경계 계약 불일치가 드러나면 수정하지
   않고 별도 Issue로 보고한다. 검증 에이전트가 검증을 통과시키려 소스를 고치는
   것은 `AGENTS.md` 2.3 위반이다.

## Explicit exclusions

- 단위 테스트 신규 작성. fingerprint(`DirectionRequestFingerprintTest`), 방향
  경계·상태 전이(`DirectionDomainTest`, `DirectionPostMatchingTest`)는 `#118`·`#120`이
  이미 소유한다.
- 단계별 통합·단계 내부 동시성 재작성. `DirectionMatchingContractIntegrationTest`,
  `DirectionMatchingWorkerConcurrencyIntegrationTest`, `OutboxLeaseIntegrationTest`,
  `RecipientNotificationFanOutWorkerConcurrencyIntegrationTest`,
  `RecipientSweepConcurrencyIntegrationTest`가 소유한다.
- migration·PostGIS 영속화 계약. `FlywayMigrationIntegrationTest`,
  `DirectionPostgisPersistenceIntegrationTest`가 소유한다.
- 실제 운영 부하 테스트(이슈 명시 제외).
- H3·Redis·Kafka 도입 검증(이슈 명시 제외).
- 외부 FCM/APNs 푸시 전달. `#116`이 별도 정책 확정 후로 미뤘다. mock 또는
  미검증 범위로 명시한다.
- 발견한 구현 결함의 수정. 별도 Issue로 보고만 한다.
- `@Scheduled` 운영 주기 실행 활성화.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| E2E 수직 흐름 통합 테스트, 체인 종단 복구 테스트 | Test executor | 단계 픽스처를 직접 주입해 경계 검증을 우회한 곳이 없는지, outbox payload에 정확 좌표가 없는지 |
| 성능 스위트와 `build.gradle` 태스크 분리 | Test executor | `check`의 기본 경로가 느려지지 않는지, `performanceTest` 미실행이 통과로 보고되지 않는지 |
| 테스트 계획·보고서 | Test executor | 기존 커버리지 매핑이 정확한지, 미검증 범위와 남은 위험이 명시되었는지 |

## Existing user-owned changes

- `origin/main`(357db91)에서 새로 분기했다. 분기 시점 `git status --short`는
  비어 있었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
./gradlew performanceTest
```

## Completion criteria

- [x] 테스트 계획 `docs/test-plans/gh-127-TEST-PLAN-DIRECTION-MATCHING-VERTICAL-FLOW.md`가
      존재하고 승인되었다. 계획서에 이슈 본문 5개 범위 각각을 소유 계획서로
      연결한 "기존 커버리지" 매핑 표가 포함된다.
- [x] E2E 테스트가 단계 픽스처 주입 없이 제출부터 만료까지를 관통하고, 체인
      종료 시 `recipient_receive_state.active_unhandled_count`가 정확히 원복된다.
- [x] outbox 이벤트 payload에 정확 좌표가 저장되지 않는다.
- [x] 수신 자격과 푸시 전달 상태가 분리되어 있음을 관통 경로에서 확인한다. (`#116`)
- [ ] **부분 미충족** — 10,000명 규모에서 `active_user_presence`에 Seq Scan은
      없음을 확인했으나(실측: PK 인덱스 `active_user_presence_pkey`로 nested-loop
      join), 부분 GIST 인덱스(`active_user_presence_position_gix`)는 이 규모·쿼리
      형태에서 전혀 선택되지 않았다. 직접 원인은 `user_account`와의 join에서
      두 테이블이 1:1이라 PK join이 더 싸다는 것이다.
      **다만 그 1:1 비율과 좁은 좌표 분포는 합성 데이터의 인공물이므로, 이 결과는
      "인덱스가 쓸모없다"가 아니라 "이 테스트가 인덱스 선택 조건을 만들지 못했다"로
      읽어야 한다.** 현실적 비율(계정 ≫ presence)로의 재측정이 후속 판단의 선행
      조건이다. 원인·한계·후속 조치는
      `docs/reports/tests/gh-127-TEST-PLAN-DIRECTION-MATCHING-VERTICAL-FLOW.md` §6을
      따른다. 이 브랜치에서 인덱스나 쿼리는 수정하지 않았다(결정 4).
- [x] preview·matching 지연이 보고서에 수치로 기록된다(단언 없음).
- [x] 매칭 outbox `DEAD` 종료와 fan-out 부분 실패 이후의 최종 상태가 확인된다.
- [x] 모든 테스트 클래스에 ISO 8601 생성 시각과 source scenario가 기록된다.
- [x] 모든 테스트 메서드에 `@DisplayName`이 있다.
- [x] 보고서가 `templates/test-report.md` 형식으로 존재하고, 구현 문제와 테스트
      환경 문제를 구분하며, 실행하지 못한 검증과 남은 위험을 명시한다.
