# Test Report: SCHEMA-REVISION-V7

> Created at: `2026-08-07T19:13:06+09:00`
> GitHub Issue: `#78`
> Branch: `feat/gh-78-schema-revision-v7`
> Commit: `7c8ea8c`

## 1. Executive summary

- Result: `PASS`
- Tested scope: `V7` 마이그레이션(answer_reaction 복합 PK·자격 트리거 교체, post_recipient/answer
  컬럼 추가와 백필, uq_answer_one_per_recipient 조건 축소), 이를 반영한 도메인·매핑
  갱신(PostRecipient, Answer, AnswerReaction JPA 매핑, JdbcPostRecipientRepository),
  실제 매칭 시점 inbound bearing/distance 계산 배선(PostGIS ST_Azimuth), 기존
  통합 테스트 6개 파일의 신규 NOT NULL 컬럼 회귀 수정
- Unverified scope: `docs/product/data-model` 문서 동기화(DBML·ERD byte-for-byte
  갱신, `schema-manifest.md` SHA-256 3곳)는 이 보고서 작성 직후 별도로 완료했다(§7
  참고) — 자동화된 JUnit 검증 대상이 아니라 사람이 확인한 항목이라 이 표에는
  scenario ID가 없다. 이번 이슈는 API 계층을 만들지 않으므로 controller/DTO/HTTP
  계약 검증도 범위 밖이다.
- Release recommendation: 단위·통합 테스트, 문서 동기화 전부 완료. PR 검토를
  진행해도 좋다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Gradle toolchain 21 (JDK 21) |
| Spring Boot | 3.5.16 |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine` (PostgreSQL 16 + PostGIS 3.5), 매 실행마다 새 컨테이너 |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| Unit (`./gradlew test`) | PASS | 123 tests, 0 failures, 0 errors, 0 skipped | ~3s | `build/test-results/test/*.xml` |
| Integration (`./gradlew integrationTest`) | PASS | 116 tests, 0 failures, 0 errors, 0 skipped | ~1m | `build/test-results/integrationTest/*.xml` |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `FlywayMigrationContractTest.migrationsMatchAcceptedContent`, `.v7MatchesAcceptedContent` | V7 파일명·순서·SHA-256 잠금 |
| UNIT-002 | PASS | `DirectionDomainTest.rejectsInboundBearingOutOfRange` | `[0,360)` 경계값 3종 |
| UNIT-003 | PASS | `DirectionDomainTest.rejectsNegativeDistanceM` | |
| UNIT-004 | PASS | `DirectionDomainTest.rejectsAnswersReadAtBeforeMatchedAt` | matchedAt 이전 시각 거절 |
| UNIT-005 | PASS | `AnswerPersistenceBoundaryTest.requiresEditCountAndEditedAtToAgree` | 도메인 레벨 동치 검증(TASK.md 완료 조건 대응) |
| UNIT-006 | PASS | `AnswerPersistenceBoundaryTest.rejectsNegativeDistanceM` | |
| INT-001 | PASS | `FlywayMigrationIntegrationTest.appliesAllMigrationsOnApplicationStartup`, `.catalogMatchesApprovedManifest` | 함수·트리거 개명, CHECK 개수 101→107, 복합 PK 컬럼 확인 |
| INT-002 | PASS | `SchemaRevisionMigrationIntegrationTest.v7BackfillsLegacyRowsWithConstraintSatisfyingPlaceholders`, `.v7FailsLoudlyWhenADeletedAnswerCoexistsWithALiveAnswer` | 백필 값 검증 + uq 조건 축소 loud-failure 시나리오 추가 |
| INT-003 | PASS | `ReactionPersistenceIntegrationTest.senderAndEligibleRecipientCanBothReactToTheSameAnswer` | 복합 PK로 두 사용자 공감 성공 |
| INT-004 | PASS | `ReactionPersistenceIntegrationTest.outsiderCannotReactToAnAnswer` | |
| INT-005 | PASS | `ReactionPersistenceIntegrationTest.theAnswerAuthorCannotReactToTheirOwnAnswer` | |
| INT-006 | PASS | `ReactionPersistenceIntegrationTest.cancellingAnAnswerReactionDeletesTheRow` | |
| INT-007 | PASS | `AnswerSafetyNotificationPersistenceIntegrationTest.deletedAnswerNoLongerFreesTheSlot` | |
| INT-008 | PASS | `AnswerSafetyNotificationPersistenceIntegrationTest.allowsOneLiveAnswerPerRecipient` | REJECTED는 여전히 허용(회귀 없음), 기존 테스트 재사용 |
| INT-009 | PASS | `AnswerSafetyNotificationPersistenceIntegrationTest.rejectsEditCountEditedAtViolationsAtTheDatabaseLevel` | 계획 승인 이후 발견한 공백을 메워 신규 추가(§7 참고) |
| INT-010 | PASS | `InboxSentPostWriteIntegrationTest`, `SentPostQueryIntegrationTest`, `InboxQueryIntegrationTest`, `AnswerSafetyNotificationPersistenceIntegrationTest` 전체 | SQL fixture에 신규 NOT NULL 컬럼 추가 후 순수 회귀 확인 |
| INT-011 | PASS | `InboxSentPostWriteIntegrationTest.togglesAnswerReaction`, `.nonSenderCannotReactToAnswer`, `.answerAuthorCannotReactToOwnAnswer` | `findByAnswerId`→`findByAnswerIdAndReactorId` 시그니처 교체 후 결과 불변 확인 |

## 5. Failures and diagnostics

구현 과정에서 발견해 수정한 실패이며, 최종 실행 결과에는 남아 있지 않다.

- **증상**: `SchemaRevisionMigrationIntegrationTest.v7BackfillsLegacyRowsWithConstraintSatisfyingPlaceholders`가
  `ERROR: cannot ALTER TABLE "answer" because it has pending trigger events` (SQL State `55006`)로
  실패.
- **원인**: `ct_answer_has_content`는 `post_recipient`의 두 constraint trigger와 달리 컬럼
  제한 없이 `AFTER INSERT OR UPDATE`에 걸린다. `V7`의 `answer` 백필 `UPDATE`가 이 deferred
  trigger의 미확정(pending) 이벤트를 같은 transaction 안에 큐잉했고, 바로 뒤따르는
  `ALTER TABLE answer ALTER COLUMN ... SET NOT NULL`이 PostgreSQL의 "pending trigger가
  있는 테이블은 ALTER할 수 없다" 제약에 걸렸다.
- **재현 조건**: 같은 transaction 안에서 (1) 컬럼 제한 없는 deferred constraint trigger가
  걸린 테이블에 `UPDATE`를 실행하고 (2) 그 직후 같은 테이블에 스키마를 바꾸는 `ALTER TABLE`을
  실행하면 재현된다.
- **조치**: 백필 `UPDATE` 직후 `SET CONSTRAINTS ct_answer_has_content IMMEDIATE;`를 추가해
  해당 trigger를 그 자리에서 즉시 평가하도록 했다. 이 백필은 `status`/`body_text`를 건드리지
  않으므로 즉시 평가해도 원래 commit 시점 평가와 결론이 같다.

## 6. Potential issues

### Application code

- **계획 대비 범위 확장**: 승인된 테스트 계획은 `DirectionCandidate`/
  `JdbcActiveUserPresenceRepository`/`ActiveUserPresenceRepository`를 Executor 소유
  파일로 지정하지 않았다. 구현 중 `PostRecipient.available()`이 `inboundBearingDegrees`를
  필수로 요구하는데, 이를 매칭 시점에 정확히 계산하는 `ST_Azimuth(recipient, sender)` 공식이
  기존 `findCandidates()` 쿼리에서 인자 순서만 바꾸면 되는 형태로 바로 옆에 있었다. 신규
  매칭 데이터에 알면서 부정확한 근사값(+180도)을 쓰는 대신 정확한 값을 배선했다 — 상세
  판단 근거는 커밋 메시지와 코드 주석 참고. 리뷰에서 이 범위 확장이 적절했는지 확인이
  필요하다.
- `PostRecipient`(18개)와 `Answer`(16개) 생성자가 positional parameter를 다수 갖게 됐다.
  이번 회차에서 인자 순서 실수를 테스트로 걸러냈지만, 향후 필드 추가 시 같은 위험이
  반복된다. 리팩터링(builder 또는 record 그룹화)은 이번 이슈 범위 밖으로 남겨둔다.
- `ANS-VAL-007`(INVALID_VALUE_RANGE), `ANS-DOM-010`(INVALID_EDIT_STATE) 오류 코드를
  신설하고 `docs/error-codes.md`에 반영했다. 아직 API 계층이 없어 HTTP 응답으로 노출되지
  않는다 — #79에서 controller가 생기면 계약 테스트 대상이 된다.

### Infrastructure and resource limits

- Testcontainers가 arm64 호스트에서 `postgis/postgis:16-3.5-alpine`(amd64 전용 이미지)을
  에뮬레이션으로 구동해 통합 테스트가 느리다는 경고가 매 실행마다 로그에 남는다. 이번
  이슈로 새로 생긴 문제는 아니며 기존 통합 테스트 스위트 전체에 해당한다.

### Database and migrations

- **백필 값은 정확하지 않다.** `distance_m=0`, `inbound_bearing_deg`(180도 근사 반전)는
  매칭 시점 좌표가 이미 사라진 레거시 행을 위한 자리표시자다. `distance_m=0`은 조회
  계층의 10km 하한 규칙이 항상 `distance_band`를 대신 노출하게 만들어 안전하지만,
  `inbound_bearing_deg` 근사값은 화면에 부정확한 방향으로 노출될 수 있다. 이 저장소가
  아직 프로덕션 사용자 데이터를 갖지 않는다는 전제로 채택했다 — 실제 서비스 데이터가
  쌓인 뒤에 같은 패턴을 다시 쓰면 안 된다.
- `SET CONSTRAINTS <trigger> IMMEDIATE`는 이번에 필요해서 추가했지만 일반적으로 안전한
  패턴은 아니다 — 그 UPDATE가 trigger의 불변식을 실제로 위반할 수 있는 경우라면 조기
  평가가 오히려 잘못된 시점에 실패를 일으킬 수 있다. 다음에 유사한 백필을 작성할 때마다
  개별적으로 재검토해야 한다(§5 참고).
- `schema-manifest.md`의 SHA-256 3곳(DBML·ERD·target DDL)과 vault 원본 문서(dbml/ERD)
  동기화를 완료했다. 추가로 `schema-manifest.md` §5~§12(baseline summary, table/
  function/trigger/index/FK/unique/check inventory)가 2026-08-05(V1+V2 상태) 이후로
  `V3`~`V6`(#48, #63, #71~#73, #77) 변경을 반영하지 않은 채 남아 있던 것을 발견해,
  V1~V7 전체 기준으로 다시 정리했다. 각 항목의 개수와 이름은
  `FlywayMigrationIntegrationTest`의 `EXPECTED_TABLES`/`EXPECTED_INDEXES`/
  `EXPECTED_FUNCTIONS`/`EXPECTED_TRIGGERS`, `countConstraints` assertion과 정확히
  일치하도록 대조했다(테이블 28→31, FK 48→50, unique 18→20, index 50→53(+2는 unique
  constraint 인덱스로 별도 분류), check 97→107, function/trigger는 개수 불변·1개 개명).

### Concurrency and idempotency

- `answer_reaction` 복합 PK 전환 이후에도 `AnswerReactionService.toggle()`의 진짜
  동시 요청(같은 사용자가 두 트랜잭션에서 동시에 toggle) 경합은 이번 회차에서 다루지
  않았다 — 자격 판정 로직 자체가 #79 범위라 서비스 계층을 건드리지 않았다.

### Transactions and event ordering

- `ct_answer_reaction_reactor_can_view`는 이전과 동일하게
  `DEFERRABLE INITIALLY DEFERRED`로 유지했다 — commit 시점까지 위반이 드러나지 않는
  기존 설계를 그대로 보존했고, `INT-003`~`INT-006`에서 `TransactionTemplate`로 실제
  commit을 트리거해 검증했다.

### External APIs

- 해당 없음. 이번 이슈는 외부 API 연동이 없다.

### Failure recovery and reconciliation

- `INT-002`(백필 성공)와 별개로 `v7FailsLoudlyWhenADeletedAnswerCoexistsWithALiveAnswer`가
  `uq_answer_one_per_recipient` 조건 축소가 충돌하는 레거시 데이터에서 `V7`이 조용히
  정리하지 않고 `flyway_schema_history`에 `success=false`만 남기고 시끄럽게 실패하는지
  확인했다.
- 백필된 placeholder 값(특히 `inbound_bearing_deg`)을 나중에 더 정확한 값으로 재계산할
  복구 경로는 이번 범위에 없다. 필요해지면 별도 데이터 보정 작업이 있어야 한다.

## 7. Regression and residual risk

- 기존 통합 테스트(#69, #70, #72 등에서 작성된) 전체가 여전히 통과한다 — `AnswerReaction`
  repository 시그니처 교체, `PostRecipient`/`Answer` 생성자 확장에도 불구하고 회귀 없음을
  확인했다.
- **완료**: `docs/product/data-model/direction_communication.dbml`,
  `DIRECTION_COMMUNICATION_ERD.md`, `schema-manifest.md` 동기화(TASK.md 완료 조건).
  `schema-manifest.md` §5~§12가 V3~V6 변경(#48, #63, #71~#73, #77)을 반영하지 않은 채
  뒤처져 있던 것을 발견해 V1~V7 전체 기준으로 함께 정리했다 — §6 Database and
  migrations 참고.
- **다음 이슈로 이연**: 답변 열람·공감 자격 판정 서비스 로직(#79), 방향 칩 집계(#80),
  답변 수정 쓰기 경로(이번 회차 명시적 제외).
- **낮은 우선순위**: `PostRecipient`/`Answer` positional 생성자의 유지보수 부담.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-78-SCHEMA-REVISION-V7.md`
- CI run: 로컬 실행만 수행. 저장소에 테스트 실행 CI workflow가 없다(`.github/workflows/`에는
  `harness-policy.yml`, `infrastructure-*.yml`, `label-policy.yml`만 존재).
- Related ADR: `docs/adr/0001-database-schema-ownership.md`(Flyway가 스키마 소유),
  `docs/adr/0002-jpa-jdbc-boundary.md`(post_recipient=JDBC, answer/answer_reaction=JPA)
- PR: 아직 생성하지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 — §1 Unverified scope, §7
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨 — `schema-manifest.md` §5~§12 소급 정리는
      이번에 완료해 후속 Issue가 필요 없다. 남은 항목(#79, #80, positional 생성자
      리팩터링)은 §7에 이미 연결돼 있다
- [ ] 실행 결과와 PR 설명이 일치함 — PR 생성 시 확인
