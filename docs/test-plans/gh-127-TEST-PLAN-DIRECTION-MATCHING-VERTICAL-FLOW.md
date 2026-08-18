# Test Plan: TEST-PLAN-DIRECTION-MATCHING-VERTICAL-FLOW

> Created at: `2026-08-18T15:08:37+09:00`
> GitHub Issue: `#127`
> Status: Approved

## 1. Objective

`#116` 방향 매칭 수직 흐름의 마지막 하위 작업(M12)이다. 신규 기능을 구현하지 않고,
`#117`~`#126`이 각각 완성한 단계들이 **하나의 흐름으로 이어졌을 때에도** 계약을
유지하는지 실제 PostgreSQL/PostGIS 환경에서 독립 검증한다.

이 계획의 전제는 "기존 테스트가 부족하다"가 아니다. 단위 98건·통합 58건이 이미
존재하고 각 마일스톤은 자기 테스트 계획과 보고서를 소유한다. 문제는 **그 테스트들이
전부 단계-로컬**이라는 점이다. 모든 통합 테스트가 자기 단계 직전 상태를 픽스처로
직접 만들어 넣고 시작하므로, 앞 단계의 실제 산출물이 다음 단계의 입력으로 쓰였을 때
깨지는 종류의 결함은 구조적으로 관측 범위 밖에 있다.

검증할 사용자 가치:

- 질문글을 보낸 사람은 방향 조건에 맞는 실제 수신자에게 정확히 한 번 도달한다.
- 수신자는 상한(`qello.direction.receive-capacity`)에 영구히 묶이지 않는다.
- 정확 좌표는 흐름의 어느 지점에서도 외부로 새지 않는다.

실패 시 위험:

- **단계 경계 계약 불일치.** 매칭 워커가 만든 `RECIPIENTS_CONFIRMED` payload와
  fan-out 워커가 읽는 필드가 어긋나도 두 단계의 기존 테스트는 각자 자기 픽스처를
  쓰므로 통과한다. 운영에서만 알림이 통째로 누락된다.
- **슬롯 누수의 누적.** 슬롯 해제는 만료·넘김확정·차단·답변공개 네 경로에 흩어져
  있다. 각 경로는 개별 검증되었으나, 한 수신 항목이 흐름 전체를 지나 종료됐을 때
  `active_unhandled_count`가 정확히 원복되는지는 미검증이다. 누수는 조용히 쌓여
  수신자를 영구히 상한에 묶는다.
- **10,000명 규모에서의 인덱스 미사용.** 저장소 전체에 `EXPLAIN` 사용이 0건이다.
  `V1__create_direction_communication_schema.sql:1102-1103`의 부분 GIST 인덱스
  (`ON active_user_presence USING GIST (position) WHERE receive_allowed = TRUE`)를
  후보 조회가 실제로 타는지 확인된 적이 없다. 소규모 픽스처(후보 1~3명)에서는
  Seq Scan이 오히려 빨라 계획기가 인덱스를 무시해도 모든 테스트가 통과한다.
  preview는 사용자 대면 동기 경로이므로 미사용 시 즉시 체감 장애가 된다.
- **체인 종단 상태의 공백.** 단계별 임대 만료·재시도·`DEAD` 전환은 검증되었으나,
  매칭 outbox가 `DEAD`로 끝났을 때 질문글이 어떤 상태로 남는지, fan-out이 일부
  수신자에게만 실패했을 때 수신 자격이 어떻게 되는지는 아무도 확인하지 않았다.

## 2. Scope

### Included

- **Gap A — E2E 수직 흐름 관통.** presence 갱신 → preview → 멱등 제출 →
  matching outbox → `DirectionMatchingWorker` → `RECIPIENTS_CONFIRMED` →
  `RecipientNotificationFanOutWorker` → 수신함 조회·열람·넘김 → 답변 제출 →
  만료·넘김확정 sweep. 각 단계는 **앞 단계가 실제로 만든 행만** 입력으로 쓴다.
- 단계 경계에서만 관측 가능한 불변식: outbox payload의 정확 좌표 비노출,
  수신 자격과 푸시 전달 상태의 분리(`#116` 완료 조건), 흐름 종료 시
  `recipient_receive_state.active_unhandled_count`의 정확한 원복.
- **Gap B — 성능·규모.** 10,000명 합성 `active_user_presence`에서
  `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`으로 후보 조회의 실행계획을 확인하고,
  preview·matching 지연을 측정해 증거로 기록한다.
- **Gap C — 체인 종단 복구.** 매칭 outbox의 재시도 소진 후 `DEAD` 종료, fan-out
  부분 실패, 임대 만료 후 재처리가 체인 전체를 통과했을 때의 최종 상태.

### Excluded

각 항목은 **이미 다른 계획이 소유**하거나 이슈 본문이 명시적으로 제외한 것이다.
이 계획은 그 범위를 다시 쓰지 않는다. §3 매핑 표가 소유 관계를 기록한다.

- 단위 시나리오 신규 작성. fingerprint는 `TEST-PLAN-GH-122`가, 방향 경계·상태 전이는
  `TEST-PLAN-GH-39`·`GH-79`·`GH-93`·`GH-120`이 소유한다. 이 계획은 단위 시나리오를
  두지 않는다.
- 단계 **내부** 동시성. 동일 멱등키 경합, worker claim 경합, slot lock 경합은
  `TEST-PLAN-GH-119`·`GH-120`·`GH-122`·`GH-123`·`GH-126`이 소유한다.
- migration과 PostGIS 영속화 계약. `TEST-PLAN-GH-36`·`GH-39`가 소유한다.
- 방향 후보 집계·프라이버시 안전 미리보기의 계약 자체. `TEST-PLAN-GH-117`이 소유한다.
  이 계획은 preview를 흐름의 한 단계이자 성능 측정 대상으로만 다룬다.
- Outbox 임대·회수·재시도 기반 자체. `TEST-PLAN-GH-119`가 소유한다.
- 수신함·열람·넘김 API 계약. `TEST-PLAN-GH-124`가 소유한다.
- 답변 제출·공개 API 계약. `TEST-PLAN-GH-125`가 소유한다.
- 만료·넘김확정 sweep 실행기 계약. `TEST-PLAN-GH-126`이 소유한다.
- 실제 운영 부하 테스트(이슈 명시 제외).
- H3·Redis·Kafka 도입 검증(이슈 명시 제외).
- 외부 FCM/APNs 푸시 전달. `#116`이 별도 정책 확정 후로 미뤘다. `notification_delivery`
  행 생성까지만 관측하고 실제 전송은 미검증 범위로 보고한다.
- HTTP·인증 계층. 저장소 관례상 `src/integrationTest`는 application service 경계에서
  구동하고 MockMvc 계약은 `src/test`의 `*ApiMockMvcTest`가 소유한다. 이 계획도 그 관례를
  따른다.
- **발견한 구현 결함의 수정.** 이 이슈는 `type: test`다. 결함은 별도 Issue로 보고만 한다
  (`AGENTS.md` 2.3).
- `@Scheduled` 운영 주기 실행 활성화.

### 허용된 단 하나의 픽스처 seam

E2E의 원칙은 "앞 단계 산출물만 입력으로 쓴다"이지만, **콘텐츠 검열 게이트는 예외**로
둔다. `direction_post.moderation_status`를 `PASSED`로, 답변을 공개 상태로 만드는 경로는
필터링 수직(`#105`~`#112`)이 소유하는 별도 시스템이고, `filtering/package-info.java`가
"콜백/이벤트 계약으로만 연결한다"라고 경계를 명시했다. 선행 계획
`TEST-PLAN-GH-120`도 같은 이유로 직접 `UPDATE`를 쓴다.

따라서 검열 통과만 직접 `UPDATE`로 표현하고, 그 사실을 테스트 코드 주석과 보고서에
명시한다. 이 seam을 **다른 단계로 확대하지 않는다** — 그 외 모든 단계는 실제 산출물을
이어받아야 하며, 이것이 이 계획의 핵심 통제다.

## 3. Source requirements

### 3.1 요구사항 매핑

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #127 | 통합: 수신자·슬롯·Outbox transaction을 실제 PostgreSQL/PostGIS에서 검증한다 |
| GitHub Issue #127 | 장애 복구: 임대 만료, 재시도, `DEAD` 전환, 부분 알림 실패 |
| GitHub Issue #127 | 성능: 10,000명 합성 데이터, `EXPLAIN ANALYZE`, preview·matching 지연 |
| GitHub Issue #127 | 권한: 수신 자격·차단·정확 좌표 비노출을 검증한다 |
| GitHub Issue #127 | 외부 연동: 외부 푸시는 mock 또는 미검증 범위를 명시한다 |
| GitHub Issue #127 | 테스트 클래스에 ISO 8601 생성 시각과 source scenario를 기록한다 |
| GitHub Issue #127 | 모든 테스트 메서드에 `@DisplayName`이 있다 |
| GitHub Issue #127 | 실행 결과를 `templates/test-report.md` 형식으로 기록하고 구현 문제와 테스트 환경 문제를 구분한다 |
| GitHub Issue #116 | 질문글 수신 자격과 푸시 전달 상태가 분리된다 |
| GitHub Issue #116 | 매칭·슬롯·Outbox 중복 실행이 데이터베이스 제약과 테스트로 보호된다 |
| Schema `V1__…sql:1102-1103` | `active_user_presence`의 부분 GIST 인덱스는 `receive_allowed = TRUE`에만 존재한다 |
| Schema `V12__…sql` | matching outbox는 `DIRECTION_POST` + `RECIPIENT_MATCH_REQUESTED`이며 round별로 유일하다 |
| `TASK.md` | 성능 스위트는 기본 게이트에서 분리하고, 판정은 실행계획으로 하며 지연은 증거로만 기록한다 |

### 3.2 기존 커버리지 매핑 (중복 작성 방지 통제)

이슈 본문의 5개 범위를 소유 계획으로 연결한다. **"기존 소유"로 표시된 항목은 이
계획에서 신규 작성하지 않는다.**

| 이슈 본문 범위 | 소유 계획 / 테스트 | 이 계획의 처리 |
| --- | --- | --- |
| 단위: fingerprint | `TEST-PLAN-GH-122` / `DirectionRequestFingerprintTest` | 기존 소유 — 신규 없음 |
| 단위: 방향 경계 | `TEST-PLAN-GH-39`·`GH-120` / `DirectionDomainTest`, `DirectionPostMatchingTest` | 기존 소유 — 신규 없음 |
| 단위: 상태 전이 | `TEST-PLAN-GH-79`·`GH-93` / `DirectionDomainTest` | 기존 소유 — 신규 없음 |
| 통합: migration | `TEST-PLAN-GH-36` / `FlywayMigrationIntegrationTest` | 기존 소유 — 신규 없음 |
| 통합: PostGIS query | `TEST-PLAN-GH-39` / `DirectionPostgisPersistenceIntegrationTest` | 기존 소유 — 신규 없음 |
| 통합: 수신자·슬롯·Outbox transaction | `TEST-PLAN-GH-120`·`GH-93` / `DirectionMatchingWorkerIntegrationTest`, `ReceiveSlotReleaseIntegrationTest` | **단계 내부는 기존 소유. 단계 경계 관통만 신규(INT-001~005)** |
| 동시성: 동일 멱등 키 | `TEST-PLAN-GH-122` / `DirectionMatchingContractIntegrationTest` | 기존 소유 — 신규 없음 |
| 동시성: worker claim | `TEST-PLAN-GH-119`·`GH-120` / `OutboxLeaseIntegrationTest`, `DirectionMatchingWorkerConcurrencyIntegrationTest` | 기존 소유 — 신규 없음 |
| 동시성: 동시 발송 | `TEST-PLAN-GH-122` / `DirectionMatchingContractIntegrationTest` | 기존 소유 — 신규 없음 |
| 동시성: slot lock | `TEST-PLAN-GH-120`·`GH-94` / `DirectionMatchingWorkerConcurrencyIntegrationTest`, `ReceiveStateReservationIntegrationTest` | 기존 소유 — 신규 없음 |
| 장애 복구: 임대 만료 | `TEST-PLAN-GH-119` / `OutboxLeaseIntegrationTest` | **단계 내부는 기존 소유. 체인 관통 재처리만 신규(INT-008)** |
| 장애 복구: 재시도·`DEAD` 전환 | `TEST-PLAN-GH-119` / `OutboxLeaseIntegrationTest` | **outbox 자체는 기존 소유. `DEAD` 이후 질문글 종단 상태만 신규(INT-006)** |
| 장애 복구: 부분 알림 실패 | 없음 | **신규(INT-007)** |
| 성능: 10,000명·`EXPLAIN ANALYZE`·지연 | 없음 (저장소 `EXPLAIN` 사용 0건) | **신규(PERF-001~004)** |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 단계 경계 계약 불일치로 fan-out이 매칭 산출물을 읽지 못한다 | 알림 전면 누락. 단계별 테스트는 전부 통과하므로 배포 전 탐지 불가 | Medium | P0 | 픽스처 주입 없이 실제 `RECIPIENTS_CONFIRMED` 행으로 fan-out이 성공 (INT-001) |
| 흐름 종료 후 `active_unhandled_count`가 원복되지 않는다 | 수신자가 상한에 영구히 묶여 신규 수신 불가. 조용히 누적 | Medium | P0 | 답변·넘김·만료 세 종결 경로 각각에서 카운터 원복 확인 (INT-002~004) |
| 10,000명 규모에서 부분 GIST 인덱스를 타지 않는다 | preview는 동기 사용자 대면 경로. 즉시 체감 장애 | Medium | P0 | `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`에 인덱스 노드 존재, Seq Scan 부재 (PERF-001~002) |
| 정확 좌표가 outbox payload나 알림에 유출된다 | 프라이버시 사고. `#116` 핵심 제약 위반 | Low | P0 | 흐름 전체가 만든 모든 outbox·notification 행에서 좌표 부재 확인 (INT-005) |
| 매칭 outbox `DEAD` 후 질문글이 모호한 상태로 남는다 | 발신자에게 영원히 미확정. 수동 복구 경로 불명 | Medium | P1 | 재시도 소진 후 `direction_post` 상태와 슬롯 잔여 관측 (INT-006) |
| fan-out 부분 실패가 수신 자격을 훼손한다 | 푸시 실패가 수신 자격까지 박탈. `#116` 완료 조건 위반 | Medium | P1 | 일부 수신자 알림 실패 후에도 `post_recipient` 자격 유지 확인 (INT-007) |
| 임대 만료 후 재처리가 체인 전체에서 중복 수신자를 만든다 | 같은 질문글을 두 번 수신 | Low | P1 | 만료·회수·재처리 후 `post_recipient` 유일성 확인 (INT-008) |
| 10,000행 성능 스위트가 기본 게이트를 느리게 만든다 | 모든 커밋·CI 지연. 개발자가 게이트를 우회하기 시작함 | High | P0 | `integrationTest`가 `performance` 태그를 제외하고 `check` 소요가 유지됨 (PERF-004) |
| 성능 스위트 미실행이 통과로 오인된다 | 실행하지 않은 검증을 통과로 보고 — `AGENTS.md` 12 금지 행위 | Medium | P0 | 보고서에 `./gradlew performanceTest` 명령과 실행 시각·결과 명시 |

## 5. Unit scenarios

이 계획은 단위 시나리오를 신규로 두지 않는다.

이슈 본문의 단위 범위(fingerprint, 방향 경계, 상태 전이)는 §3.2 매핑 표대로
`TEST-PLAN-GH-39`·`GH-79`·`GH-93`·`GH-120`·`GH-122`가 이미 소유하며, 해당 단위
테스트는 `./harness check`의 `test` 태스크에서 계속 실행된다. 같은 대상을 다시
작성하면 소유권이 이중화되어 이후 계약 변경 시 어느 쪽을 고쳐야 하는지 모호해진다.

보고서에는 기존 단위 테스트의 실행 결과(총 건수·통과 여부)를 회귀 증거로 인용한다.

## 6. Integration scenarios

공통 전제: `@SpringBootTest`, `@ActiveProfiles("test")`,
`PostgisContainerIntegrationTestSupport` 상속, 고정 시계(`NOW`).

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| …-INT-001 | `DirectionPresenceService` → `DirectionPostApplicationService` → `DirectionMatchingWorker` → `RecipientNotificationFanOutWorker` | 발신자 1명, 방향 조건 충족 후보 3명, 8분할 scheme, ACTIVE 질문. 검열 seam으로만 `PASSED` 처리 | presence 갱신 → preview → submit → `processBatch` → fan-out `processBatch`. 각 단계는 앞 단계 산출 행만 입력으로 사용 | 전 단계가 픽스처 주입 없이 성공. `post_recipient` 3건, `RECIPIENTS_CONFIRMED` 3건이 모두 `PROCESSED`, `notification` 3건, `notification_delivery` 생성. 각 단계 사이에 수동 `INSERT`/`UPDATE`가 없음 | `@BeforeEach` 전체 삭제 |
| …-INT-002 | INT-001 체인 + `InboxApplicationService` + `AnswerSubmissionApplicationService` | INT-001 종료 상태 | 수신자가 수신함 목록·상세 조회 후 답변 제출, 검열 seam으로 공개 | 수신함에 질문글이 보이고 답변이 저장됨. 해당 수신자의 `active_unhandled_count`가 1 → 0으로 정확히 원복 | 동일 |
| …-INT-003 | INT-001 체인 + `InboxApplicationService.skip` + `SkipConfirmationSweepWorker` | INT-001 종료 상태 | 수신자가 넘김 → 유예 경과 시점으로 sweep 실행 | `SKIP_PENDING` → `SKIPPED` 전이. 카운터가 정확히 1 감소하고 재실행해도 추가 감소 없음 | 동일 |
| …-INT-004 | INT-001 체인 + `RecipientExpirationSweepWorker` | INT-001 종료 상태 | 미응답 상태로 만료 시점 경과 후 sweep 실행 | `EXPIRED` 전이. 카운터 정확히 1 감소. 흐름 종료 후 세 수신자 합계가 0 | 동일 |
| …-INT-005 | 전체 체인 | INT-001 종료 상태 | 흐름이 생성한 모든 `outbox_event.payload`와 `notification` 행을 조회 | 어떤 행에도 발신자·수신자의 정확 위경도가 포함되지 않음. 방향 segment key와 거리 band 등 파생값만 존재 | 동일 |
| …-INT-006 | `DirectionMatchingWorker` + `OutboxRetryPolicy` | 매칭이 반복 실패하도록 유도(후보 조회 대상 없음 등 재시도 유발 조건) | 재시도 한도까지 `processBatch` 반복 실행 | outbox가 `DEAD`로 종료. `direction_post`의 최종 상태와 슬롯 잔여를 관측해 기록. 잔여 슬롯이 점유된 채 남지 않음 | 동일 |
| …-INT-007 | `RecipientNotificationFanOutWorker` | INT-001의 확정 상태에서 수신자 3명 중 1명만 알림 실패 유도 | fan-out `processBatch` | 실패한 수신자의 `post_recipient` 수신 자격은 유지되고 `notification_delivery`만 실패로 남음. 나머지 2명은 정상. 수신 자격과 푸시 전달 상태의 분리 확인 | 동일 |
| …-INT-008 | `DirectionMatchingWorker` 임대 만료 + 재처리 | 매칭 워커가 임대를 잡은 뒤 만료되도록 시각 이동 | 다른 leaseOwner로 재claim 후 `processBatch` | stale 워커의 write는 반영되지 않고 재처리가 성공. `post_recipient`에 중복 수신자 없음. 카운터가 중복 증가하지 않음 | 동일 |

### 성능 시나리오 (`@Tag("performance")`)

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| …-PERF-001 | `ActiveUserPresenceSql` 후보 조회 (preview 경로) | 10,000행 합성 presence를 `ST_Project`로 반경 내 분산 생성 후 `ANALYZE active_user_presence` | 후보 조회 SQL에 `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` 실행 | 실행계획에 `active_user_presence`에 대한 인덱스 스캔 노드가 존재하고 Seq Scan이 없음. **PASS/FAIL 판정 기준** | 성능 데이터 삭제 |
| …-PERF-002 | `ActiveUserPresenceSql` 매칭 후보 조회 (worker 경로) | PERF-001과 동일 데이터 | 매칭 후보 조회 SQL에 동일한 `EXPLAIN` 실행 | 동일 판정 | 동일 |
| …-PERF-003 | `DirectionPostApplicationService.preview` + `DirectionMatchingWorker` | PERF-001과 동일 데이터 | preview와 매칭 1회씩 실행하며 경과 시간 측정 | 지연(ms)을 수집해 보고서에 기록. **단언하지 않음.** Testcontainers 머신 편차로 임계값이 flaky해지면 회귀 탐지력보다 잡음이 커진다 | 동일 |
| …-PERF-004 | Gradle 빌드 구성 | — | `integrationTest`와 `performanceTest` 태스크 구성 확인 | `integrationTest`가 `performance` 태그를 제외하고 `check`는 `integrationTest`만 의존. 성능 스위트가 기본 게이트에 포함되지 않음 | — |

우선순위: INT-001~005·PERF-001~002·PERF-004는 P0, INT-006~008·PERF-003은 P1.

## 7. Cross-cutting scenarios

### Database and transactions

- 각 단계 사이에 수동 DML이 없음을 테스트 구조로 보장한다. E2E 테스트는 단계
  산출물을 조회하는 `SELECT`와 최종 단언 외의 쓰기를 하지 않으며, 유일한 예외는
  §2에 명시한 검열 seam이다.
- `active_unhandled_count`는 흐름 시작 전 0, 확정 후 1, 종결(답변·넘김·만료) 후
  다시 0이어야 한다. 이 왕복이 이 계획의 핵심 단언이다.
- 성능 데이터 적재는 단일 배치 `INSERT ... SELECT generate_series(...)`로 수행하고,
  `EXPLAIN` 실행 전 반드시 `ANALYZE`를 호출한다. 통계 없이 얻은 실행계획은 판정
  근거가 되지 못한다.

### Concurrency and idempotency

- 단계 내부 동시성은 이 계획의 범위가 아니다(§2, §3.2). 체인을 관통하는 재처리
  경로(INT-008)만 다룬다.
- INT-008은 별도 스레드를 띄우지 않고 임대 만료를 시각 이동으로 표현한다. 스레드
  경합 자체는 `DirectionMatchingWorkerConcurrencyIntegrationTest`가 소유한다.

### External APIs

- 외부 FCM/APNs 푸시는 이 시스템에 구현되어 있지 않다. `notification_delivery` 행
  생성까지만 관측하고, 실제 전송은 **미검증 범위**로 보고서에 명시한다.
- AWS S3(미디어)는 이 흐름의 필수 경로가 아니므로 답변은 텍스트로만 제출한다.
  `LocalStackContainerIntegrationTestSupport`를 사용하지 않는다.

### Failure recovery and reconciliation

- INT-006은 `DEAD` 종료 **이후의 상태**를 관측하는 시나리오다. 기대값을 미리
  단정하지 않고, 관측한 상태를 보고서에 기록한 뒤 그것이 운영상 복구 가능한
  상태인지 판정한다. 구현되지 않은 복구 동작을 있는 것처럼 가정하지 않는다
  (`agents/test-orchestrator.md` Guardrails).
- INT-007의 알림 실패 유도 방식은 실행 단계에서 실제 코드 경로를 확인해 결정한다.
  프로덕션 코드를 수정해 실패를 주입하지 않는다.

## 8. Test data and isolation

- **Fixtures:** `DirectionMatchingWorkerIntegrationTest`의 확립된 헬퍼 어휘를 따른다 —
  `account`, `activeQuestion`, `eightSegmentScheme`, `presence`, `presenceAtBearing`.
  좌표는 기존 테스트와 동일하게 원점 `(37.5000, 127.0000)` 기준 `ST_Project`로 생성한다.
- **Database isolation:** 클래스별 고유 `coarse_region_code` 상수를 쓰고
  `@BeforeEach`에서 관련 테이블을 명시적 순서로 `DELETE`한다. 기존 통합 테스트와
  같은 방식이며, 컨테이너를 공유해도 서로 간섭하지 않는다.
- **Clock/randomness:** 고정 `Instant NOW`를 상수로 두고 모든 시각 인자를 그로부터
  파생시킨다. 만료·유예 경과는 시계를 옮기는 대신 `at` 인자를 이동시켜 표현한다.
  합성 데이터의 좌표 분산은 고정 seed 또는 결정적 산술로 생성해 실행 간 실행계획이
  흔들리지 않게 한다.
- **External API doubles:** 외부 푸시 호출 없음. 필요 시 `@TestConfiguration`으로
  `@Primary` 빈을 주입하며 프로덕션 코드는 수정하지 않는다.
- **Cleanup:** 성능 스위트는 10,000행을 남기지 않도록 `@AfterAll`에서 해당
  region의 presence를 삭제한다. `@DirtiesContext(AFTER_CLASS)`를 유지한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

파일 소유는 겹치지 않는다. 각 실행 단위는 자기 파일만 생성·수정한다.

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Test executor | `src/integrationTest/java/com/dnd/qello/DirectionMatchingVerticalFlowIntegrationTest.java` (신규) | INT-001 ~ INT-005 | `./gradlew integrationTest --tests '*DirectionMatchingVerticalFlow*'` |
| 2 | Test executor | `src/integrationTest/java/com/dnd/qello/DirectionMatchingVerticalFlowRecoveryIntegrationTest.java` (신규) | INT-006 ~ INT-008 | `./gradlew integrationTest --tests '*DirectionMatchingVerticalFlowRecovery*'` |
| 3 | Test executor | `build.gradle` (수정: 태그 제외 + `performanceTest` 태스크) | PERF-004 | `./gradlew tasks --group verification`, `./harness check` |
| 4 | Test executor | `src/integrationTest/java/com/dnd/qello/DirectionMatchingPerformanceIntegrationTest.java` (신규) | PERF-001 ~ PERF-003 | `./gradlew performanceTest` |
| 5 | Test executor | `docs/reports/tests/gh-127-TEST-PLAN-DIRECTION-MATCHING-VERTICAL-FLOW.md` (신규) | — | `templates/test-report.md` 형식 준수 |

순서 3은 4보다 반드시 앞선다. 태스크 분리 없이 성능 테스트를 추가하면 그 시점부터
모든 `./harness check`가 10,000행을 적재한다.

프로덕션 코드는 어느 실행 단위도 수정하지 않는다. `build.gradle`은 테스트 실행
구성 변경이며 애플리케이션 동작을 바꾸지 않는다.

### 완료 명령

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
./gradlew performanceTest
```

### 실패 판단 기준

- **구현 문제(FAIL):** 단계 경계에서 계약이 어긋남, 카운터 원복 실패, 좌표 유출,
  10,000행에서 인덱스 미사용.
- **테스트 환경 문제(BLOCKED):** Docker/Testcontainers 기동 실패, 이미지 pull 실패.
  이 경우 실패한 명령·오류 요약·재현 조건·미검증 범위·남은 위험을 기록한다.
- 구현 결함을 발견해도 이 브랜치에서 프로덕션 코드를 고치지 않는다. 별도 Issue로
  보고한다.
- `performanceTest`는 `check`에 포함되지 않으므로, 실행하지 않았다면 보고서에
  **미실행**으로 명시한다. 통과로 표현하지 않는다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현 (INT-001~005, PERF-001~002, PERF-004)
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과 (기존 회귀)
- [ ] 통합 테스트 통과
- [ ] `performanceTest` 실행 결과와 실행계획 증거 기록
- [ ] `check`의 기본 경로에 성능 스위트가 포함되지 않음을 확인
- [ ] 잠재 문제 분석 (애플리케이션·DB·동시성·트랜잭션·외부 API·장애 복구)
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer: Byuntil
- Decision: Approved
- Approved at: `2026-08-18T15:36:00+09:00`
