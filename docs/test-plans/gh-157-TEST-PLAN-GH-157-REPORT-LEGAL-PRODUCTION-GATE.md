# Test Plan: TEST-PLAN-GH-157-REPORT-LEGAL-PRODUCTION-GATE

> Created at: `2026-08-21T18:50:01+09:00`
> GitHub Issue: `#157`
> Status: Draft

## 1. Objective

`#157`은 설계 문서(`docs/product/ANSWER_REPORT_DESIGN.md` §12)에 `BLOCKED`로
남아있던 법무·제품 정책을 사람의 결정(`TASK.md` Decisions 표)으로 채우고
production 설정으로 반영한다. 이 계획이 검증할 사용자 가치는:

1. 새 CRITICAL 하위 사유(자해·자살 위험)가 기존 CSAM/NCII/CREDIBLE_THREAT와
   동일하게 긴급 경로로 취급된다.
2. "CRITICAL 1건 즉시 전역 숨김"이 기능 플래그로 감싸여, 플래그가 꺼진
   기본 상태에서는 기존(#156) 동작(URGENT 라우팅만)을 절대 바꾸지 않는다 —
   이 계획이 놓치면 사람의 승인 없이 프로덕션에서 콘텐츠가 즉시 숨겨지는
   회귀가 생긴다.
3. CRITICAL 일일 신고 쿼터가 남용 통제로 실제 작동한다.
4. 증거 스냅샷의 `purge_after` 계산과, append-only 트리거에 새로 뚫는
   "media_object_keys만 비우는 예외 경로"가 **그 외의 모든 변경(본문,
   해시, legal_hold 행 포함)을 여전히 거부**한다 — 여기서 실패하면
   `INV-RPT-004`(증거 불변성)가 조용히 깨진다. 이 계획에서 가장 위험도가
   높은 영역이다.
5. 운영 기본값 변경(자동 숨김 임계값 5→3)이 기존 `#156` 테스트의 암묵적
   가정(정확히 5명 루프)과 충돌하지 않는다.

## 2. Scope

### Included

- `ReportSubReason.SELF_HARM_RISK` 추가와 `ck_report_sub_reason` CHECK,
  `ReportCaseSeverity.of(...)` 매핑.
- `qello.safety.report-case.auto-suppress.critical-enabled` 기능 플래그
  (기본값 `false`)와 `ReportCaseAutoSuppressionEvaluator`의 3번째 트리거
  조건(CRITICAL severity).
- `SafetyReportService.mergeCase(...)`가 `CaseMergeResult(caseId,
  resolvedSeverity)`를 반환하도록 바뀐 뒤에도 기존 승격(escalate) 동작이
  그대로 유지되는지.
- `CriticalReportQuotaPolicy`, `countCriticalReportsByReporterSince`,
  `SafetyErrorCode.CRITICAL_REPORT_DAILY_QUOTA_EXCEEDED`.
- `EvidenceRetentionPolicy`와 `ReportContentSnapshot.capture(...)`의
  `purgeAfter` 계산.
- Flyway `V27`: CHECK 확장 + `report_content_snapshot` 전용 트리거 함수
  교체(`report_case_event` 트리거는 불변).
- `ReportContentSnapshotRepository.findPurgeable`/`purgeMedia`와
  `ReportEvidencePurgeSweepWorker`(batch 처리기, `@Scheduled` 없음).
- `SafetyReportConfiguration` 운영값 변경(자동 숨김 임계값 3, SLA·rate
  limit 값 유지 확인).

### Excluded

- 국가별 신고 의무 분기, 이의제기 경로, 운영자 role 세분화, 계정 삭제
  증거 우선순위 신규 코드 — `TASK.md` Explicit exclusions과 동일.
- 실제 S3 등 오브젝트 스토리지에서 미디어 파일 삭제.
- `ReportEvidencePurgeSweepWorker`의 실제 주기 실행 배선(`@Scheduled`,
  운영 스케줄러).
- `critical-enabled` 플래그를 프로덕션에서 `true`로 켜는 것 자체 — 이
  계획은 플래그 OFF/ON 두 상태 모두의 **코드 동작**만 검증하고, 실제
  운영 활성화 결정은 검증하지 않는다.
- 허위 CRITICAL 신고 사후 제재 로직(이벤트 기록 재사용으로 충분,
  `TASK.md` 참고).

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #157 | 결정 항목 확정, 운영 기본값을 설정으로 주입, `legal_hold` 스냅샷·참조 미디어가 정리 배치에서 제외 |
| `TASK.md` Decisions #1,#3,#5,#8a,#8d,#9 | A안 즉시 숨김(플래그), SELF_HARM_RISK 신규, purge_after 180일, 임계값 3명, CRITICAL 일일 쿼터 5건, 트리거 예외 |
| `TASK.md` Completion criteria | 8개 체크리스트 항목 |
| `docs/product/ANSWER_REPORT_DESIGN.md` §4.1 | 남용 통제 (a) 일일 쿼터 |
| `docs/product/ANSWER_REPORT_DESIGN.md` §7, INV-RPT-004(코드 주석) | 증거 스냅샷 append-only, `legal_hold`면 정리 배치 제외 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| purge용 트리거 예외가 `body_text`/`content_hash`/`legal_hold` 등 다른 컬럼 변경이나 `legal_hold=true` 행의 purge를 허용해버림 | 매우 높음 — 증거 무결성(INV-RPT-004) 붕괴, 법적 증거능력 상실 | 낮음(신중히 구현하면) but 검증 안 하면 회귀 감지 불가 | P0 | 통합 테스트로 트리거를 직접 두드려 각 금지 케이스가 `23514`로 거부되는지 확인 |
| `critical-enabled` 기본값이 실수로 `true`가 되어 승인 없이 프로덕션에서 즉시 전역 숨김이 켜짐 | 높음 — 남용 시 무고한 콘텐츠 즉시 삭제 | 낮음 | P0 | 플래그 미지정 시 기본 컨텍스트에서 OFF 동작 확인하는 테스트 |
| `mergeCase` 반환 타입 변경이 기존 승격(escalate) 로직·이벤트 발행 순서를 깨뜨림 | 중간 — 기존 `#156` 계약(ESCALATED 이벤트) 회귀 | 중간(리팩터링 범위가 넓음) | P0 | 기존 승격 시나리오(NORMAL→CRITICAL) 재검증 + 신규 반환값 검증 |
| 자동 숨김 임계값 5→3 변경이 기존 `#156` 테스트의 암묵적 가정과 충돌 | 낮음(사전 조사로 충돌 없음 확인) | 낮음 | P1 | 기존 5-reporter/1-reporter 테스트 재실행 + 신규 3명 경계 테스트 |
| CRITICAL 일일 쿼터 카운트가 자정 경계로 계산되어 자정 직전/직후 우회 가능 | 중간 — 남용 통제 무력화 | 중간 | P1 | 롤링 24시간 윈도우로 구현하고 경계 케이스 테스트 |
| purge sweep이 이미 비워진 스냅샷을 재처리하거나, `purge_after`가 아직 안 지난 행을 잘못 포함 | 중간 — 불필요한 UPDATE 반복 또는 조기 purge | 낮음 | P1 | `findPurgeable` 쿼리 경계 테스트(정확히 `purge_after`, 이미 빈 `media_object_keys`) |
| 두 신고가 거의 동시에 같은 사건을 CRITICAL로 만들며 각각 자동 숨김을 트리거 | 낮음(기존 `resolveIfStillOpen`의 멱등 처리 재사용) | 낮음 | P2 | 동시성 테스트로 기존 멱등 스왈로우 경로가 CRITICAL 트리거에도 적용되는지 확인 |
| Flyway `V27`이 기존 데이터와 충돌(예: 이미 존재하는 `sub_reason_code` 값과 CHECK 불일치) | 낮음 — 순수 추가 조건이라 기존 행에 영향 없음 | 낮음 | P2 | 마이그레이션 적용 후 기존 통합 테스트 스위트(`*Flyway*`) 재실행 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-157-UNIT-001 | `ReportSubReason.SELF_HARM_RISK` | `ReportCaseSeverity.of(SELF_HARM_RISK)` 호출 | `CRITICAL` 반환 | P0 | Executor A |
| TEST-PLAN-GH-157-UNIT-002 | 기존 `CSAM`/`NCII`/`CREDIBLE_THREAT`/`null` | `ReportCaseSeverity.of(...)` 호출 | 기존 동작(각각 CRITICAL/CRITICAL/CRITICAL/NORMAL) 유지 — 회귀 방지 | P0 | Executor A |
| TEST-PLAN-GH-157-UNIT-003 | `CriticalReportQuotaPolicy(maxPerDay=5)` 생성 | `maxPerDay=0` 또는 음수로 생성 시도 | `SafetyException(REQUIRED_VALUE_MISSING)` | P1 | Executor A |
| TEST-PLAN-GH-157-UNIT-004 | `EvidenceRetentionPolicy(retentionPeriod=Duration.ofDays(180))` | `retentionPeriod`가 `null`/0/음수 | `SafetyException` | P1 | Executor A |
| TEST-PLAN-GH-157-UNIT-005 | `ReportContentSnapshot.capture(...)`에 `purgeAfter` 파라미터 추가 후 | `capturedAt`과 `retentionPeriod`로 스냅샷 생성 | `purgeAfter == capturedAt.plus(retentionPeriod)` | P0 | Executor A |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-157-INT-001 | `SafetyReportService`, `ReportCaseAutoSuppressionEvaluator` | `critical-enabled=true`(테스트 프로필 오버라이드), 답변 발행 | `SELF_HARM_RISK` 하위 사유로 답변 1건 신고 | 사건이 `CRITICAL`/`URGENT`로 열리고 즉시 `RESOLVED`+`ACTIONED`, 답변이 `HIDDEN` | 트랜잭션 롤백/스키마 초기화 |
| TEST-PLAN-GH-157-INT-002 | 위와 동일 | `critical-enabled=false`(기본값, 오버라이드 없음) | `CSAM` 하위 사유로 답변 1건 신고 | 사건이 `CRITICAL`/`URGENT`로 열리지만(`#156` 기존 동작) 상태는 `OPEN`으로 남고 답변은 `PUBLISHED` 유지 | 동일 |
| TEST-PLAN-GH-157-INT-003 | 위와 동일 | `critical-enabled=true`, `NORMAL`로 열린 기존 사건 | `CREDIBLE_THREAT`로 2번째 신고 부착(승격) | 사건이 승격되며 `ESCALATED` 이벤트 발행 후 즉시 `RESOLVED`+`ACTIONED` | 동일 |
| TEST-PLAN-GH-157-INT-004 | `SafetyReportService` | `CriticalReportQuotaPolicy(maxPerDay=5)` 주입 | 같은 계정이 24시간 내 CRITICAL 신고 6건째 시도(서로 다른 대상) | 6번째 요청이 `CRITICAL_REPORT_DAILY_QUOTA_EXCEEDED`(429)로 거부, 앞 5건은 성공 | 동일 |
| TEST-PLAN-GH-157-INT-005 | 위와 동일 | 5건 CRITICAL 신고가 25시간 전~1시간 전에 걸쳐 분산 | 새 CRITICAL 신고 시도(롤링 24시간 윈도우 기준 4건만 유효) | 쿼터 통과(거부되지 않음) — 자정 고정 경계가 아님을 증명 | 동일 |
| TEST-PLAN-GH-157-INT-006 | `ReportContentSnapshotRepository`(JDBC), Postgres | 스냅샷 저장(`legal_hold=false`, `purge_after=now-1day`, `media_object_keys=['k1']`) | `purgeMedia(reportId)` 호출 | `media_object_keys='{}'`로 UPDATE 성공, 나머지 컬럼 불변 | 동일 |
| TEST-PLAN-GH-157-INT-007 | 위와 동일 | `legal_hold=true`인 스냅샷 | `purgeMedia(reportId)` 호출 시도 | DB 예외(`23514`)로 거부 | 동일 |
| TEST-PLAN-GH-157-INT-008 | 위와 동일 | 임의 스냅샷 | `body_text`를 바꾸는 raw UPDATE 시도(JDBC 직접 SQL) | 예외(`23514`)로 거부 — 기존 `INV-RPT-004` 계약이 새 트리거 함수에서도 유지됨 | 동일 |
| TEST-PLAN-GH-157-INT-009 | 위와 동일 | 임의 스냅샷 | `media_object_keys`와 다른 컬럼(`edit_count` 등)을 **동시에** 바꾸는 UPDATE 시도 | 예외(`23514`)로 거부 — "오직 media_object_keys만" 조건 검증 | 동일 |
| TEST-PLAN-GH-157-INT-010 | 위와 동일 | 임의 스냅샷(legal_hold 무관) | `DELETE FROM report_content_snapshot` 시도 | 예외(`23514`)로 거부 — DELETE는 항상 금지 | 동일 |
| TEST-PLAN-GH-157-INT-011 | `report_case_event` 트리거 | 임의 이벤트 행 | UPDATE/DELETE 시도 | 예외(`23514`) — `report_content_snapshot` 전용 함수 교체가 이 테이블에 영향 없음을 확인(회귀 방지) | 동일 |
| TEST-PLAN-GH-157-INT-012 | `ReportEvidencePurgeSweepWorker` | 스냅샷 3건: (a) `purge_after` 지남+`legal_hold=false`+미디어 있음, (b) `purge_after` 안 지남, (c) `legal_hold=true`+`purge_after` 지남 | `processBatch(limit=10, now)` 호출 | (a)만 purge됨(미디어 비워짐), (b)(c)는 그대로, 배치 결과 카운트가 scanned=1/purged=1과 일치 | 동일 |
| TEST-PLAN-GH-157-INT-013 | 위와 동일 | 이미 `media_object_keys='{}'`인 만료 스냅샷 | `findPurgeable` 조회 | 후보에서 제외됨(멱등, 재처리 안 함) | 동일 |
| TEST-PLAN-GH-157-INT-014 | `ReportSubReason` CHECK | `reason_code='ILLEGAL_OR_DANGEROUS', sub_reason_code='SELF_HARM_RISK'` | INSERT 시도 | 성공 | 동일 |
| TEST-PLAN-GH-157-INT-015 | 위와 동일 | `reason_code='SEXUAL_CONTENT', sub_reason_code='SELF_HARM_RISK'`(잘못된 조합) | INSERT 시도 | CHECK 위반으로 거부 | 동일 |
| TEST-PLAN-GH-157-INT-016 | `SafetyReportConfiguration` | 설정값 오버라이드 없음(기본 프로필) | `AutoSuppressionPolicy` 빈 조회 | `distinctReporterThreshold() == 3` | 동일 |
| TEST-PLAN-GH-157-INT-017(회귀) | `ReportCaseSeverityIntegrationTest` 기존 스위트 | 기존 코드 그대로 | 기존 5-reporter/1-reporter 테스트 재실행 | 전부 통과(임계값 3으로도 5≥3, 1<3 성립 확인) | 기존 테스트, 신규 파일 아님 |

## 7. Cross-cutting scenarios

### Database and transactions

- INT-006~INT-011은 실제 PostgreSQL 트리거 동작이므로 H2/mock으로 대체
  불가 — `integrationTest` 소스셋에서만 실행한다.
- `purgeMedia`가 신고 접수 트랜잭션과 별개의 독립 트랜잭션으로 실행됨을
  확인한다(sweep worker는 `#158` 패턴처럼 자체 트랜잭션을 열지 않고
  호출되는 서비스 메서드의 `@Transactional`에 의존 — 한 행 실패가 다른
  행 커밋을 되돌리지 않아야 한다).

### Concurrency and idempotency

- TEST-PLAN-GH-157-INT-012의 변형으로, 같은 사건에 CRITICAL 신고 2건이
  동시에 도착할 때(`critical-enabled=true`) 기존 `resolveIfStillOpen`의
  `REPORT_CASE_ALREADY_RESOLVED` 스왈로우 경로가 예외 없이 동작하는지
  스레드 2개로 재현한다(선택 P2, 기존 `#156` 동시성 테스트 패턴 재사용).
- `findPurgeable`이 같은 배치를 두 워커 인스턴스가 동시에 스캔해도
  `purgeMedia`가 멱등(이미 빈 `media_object_keys`면 스킵)한지 확인한다.

### External APIs

- 해당 없음 — 이 이슈는 외부 API 연동을 추가하지 않는다.

### Failure recovery and reconciliation

- `purgeMedia` 호출 도중 예외가 발생해도(예: 존재하지 않는 reportId)
  다른 스냅샷 처리에 영향을 주지 않아야 한다 — `RecipientExpirationSweepWorker`와
  동일하게 행 단위 실패 격리를 확인한다.
- CRITICAL 일일 쿼터 초과로 신고가 거부된 뒤에도 `acquireReporterSubmissionLock`이
  정상 해제되어 다음 요청(비-CRITICAL)이 막히지 않는지 확인한다.

## 8. Test data and isolation

- Fixtures: 기존 `ReportCaseSeverityIntegrationTest`/`OperatorReportCaseIntegrationTest`의
  `publishedAnswer(...)`, `reporter(...)` 헬퍼 재사용. 신규 스냅샷 직접
  삽입이 필요한 트리거 테스트(INT-006~013)는 `JdbcReportContentSnapshotRepository`
  또는 원시 SQL로 준비.
- Database isolation: 기존 통합 테스트 컨벤션(트랜잭션 롤백 또는 스키마
  초기화) 그대로 따른다 — 신규 컨벤션 도입 없음.
- Clock/randomness: 고정 `Instant NOW` 상수 재사용(기존 파일 컨벤션),
  롤링 24시간 윈도우 테스트(INT-005)는 `NOW.minus(Duration.ofHours(25))`
  등 상대 시각으로 구성.
- External API doubles: 없음.
- Cleanup: 기존 통합 테스트와 동일한 후처리(각 파일의 `@AfterEach`/롤백).

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Executor A (domain/unit) | `src/test/java/com/dnd/qello/safety/ReportCaseSeverityTest.java`(신규 또는 기존 파일 확장), `CriticalReportQuotaPolicyTest.java`(신규), `EvidenceRetentionPolicyTest.java`(신규), `ReportContentSnapshotTest.java`(기존 확장) | UNIT-001~005 | `./gradlew test --tests "com.dnd.qello.safety.*"` |
| 2 | Executor B (신고 접수·자동숨김·쿼터 통합) | `src/integrationTest/java/com/dnd/qello/ReportCaseSeverityIntegrationTest.java`(기존 파일에 신규 테스트 추가, 기존 테스트는 수정 최소화) | INT-001~005, INT-016, INT-017(회귀 확인만) | `./gradlew integrationTest --tests "com.dnd.qello.ReportCaseSeverityIntegrationTest"` |
| 3 | Executor C (증거 스냅샷 트리거·purge 배치, Executor B와 파일 겹치지 않음) | `src/integrationTest/java/com/dnd/qello/ReportEvidencePurgeIntegrationTest.java`(신규), `src/integrationTest/java/com/dnd/qello/ReportContentSnapshotImmutabilityIntegrationTest.java`(신규 또는 기존 immutability 테스트 파일에 추가) | INT-006~015 | `./gradlew integrationTest --tests "com.dnd.qello.*ReportEvidencePurge*"`, `./gradlew integrationTest --tests "com.dnd.qello.*Snapshot*"` |
| 4 | Executor D (마이그레이션·설정) | `src/test/resources` 또는 기존 `*Flyway*` 테스트 확인(신규 테스트 파일 불필요, 실행만) | (마이그레이션 적용 확인) | `./gradlew integrationTest --tests "*Flyway*"` |

Executor B와 C는 서로 다른 파일을 소유하므로 병렬 실행 가능. Executor A는
두 파일 그룹 모두보다 선행(도메인 계약이 먼저 확정돼야 함).

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현 (UNIT-001,002,005 / INT-001,002,003,006,007,008,009,010)
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
      (`Created at`, `Source scenario: TEST-PLAN-GH-157-...`)
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석(특히 트리거 예외 범위, 플래그 기본값, 자정 경계)
- [ ] 테스트 보고서 생성(`docs/reports/tests/gh-157-...`)

## 11. Human approval

- Reviewer: tkv00
- Decision: APPROVED — "진행"
- Approved at: 2026-08-21
