# Test Plan: TEST-PLAN-GH-156-REPORT-SEVERITY-OPERATOR-REVIEW

> Created at: `2026-08-20T21:07:44+09:00`
> GitHub Issue: `#156`
> Status: Draft

## 1. Objective

즉시 대응이 필요한 신고(CSAM·NCII·CREDIBLE_THREAT)가 실제로 `URGENT` 큐로
분리되고, 운영자가 그 사건을 검토·판정할 수 있는 API 경로가 계약대로
동작하는지 검증한다. 실패 시 위험은 세 갈래다 — (1) 심각도 산출·승격이
잘못돼 CRITICAL 신고가 일반 대기열에 묻히는 안전 문제, (2) 두 운영자가 같은
사건을 동시에 판정해 상태가 꼬이거나 알림이 중복되는 정합성 문제, (3) 자동
전역 숨김 조건이 잘못 트리거되어 정상 콘텐츠가 숨겨지거나, 반대로 이미
`filtering` 파이프라인이 차단한 콘텐츠가 신고 경로에서는 계속 노출되는
일관성 문제.

## 2. Scope

### Included

- 심각도 산출(`subReason → severity`)과 큐 라우팅(`severity → queue`) 순수
  함수.
- `ReportCase.escalate(at)`/`deescalate(at)`/`requestMoreInfo(at)` 신규 도메인
  전이. `ReportCase.open(...)` 시그니처 변경(초기 severity/queue 인자 추가)에
  따른 기존 `ReportCaseAndEvidenceTest`(`src/test/java/com/dnd/qello/safety/
  ReportCaseAndEvidenceTest.java`) 호출부 회귀.
- `SafetyReportService.mergeCase(...)`(`safety/service/
  SafetyReportService.java:159`) 확장 — 신규 사건은 산출된 severity/queue로
  열고, 기존 열린 사건에 더 높은 severity 신고가 붙으면 행 잠금 후 승격.
- SLA: `report_case.sla_due_at` 신규 컬럼(마이그레이션 버전은 구현 시점에
  `main`의 최신 번호+1로 확정 — 이 계획 작성 시점 기준 `V24`), 주입형
  `SlaPolicy`, 승격 시 재계산.
- 자동 전역 숨김: (a) 사건에 달린 신고의 distinct `reporter_id` 수가 주입된
  임계값 이상, (b) 같은 대상의 `filtering.ManualReviewCase`가 `OPEN`이거나
  `RESOLVED`+`resolvedVerdict=BLOCK`. 두 조건 모두 `SafetyCaseResolutionService.
  resolveCase(caseId, ACTIONED, now)`를 재사용해 실제 숨김을 수행한다(신규
  숨김 로직을 만들지 않는다). (b)는 `report_case.linked_manual_review_case_id`
  기록까지 포함. (운영자 조치에 의한 숨김은 `#155`가 이미 구현했으므로 이
  계획의 대상이 아니다.)
- `ManualReviewCaseRepository`(또는 대응 인터페이스)에 target 단독 조회
  신규 메서드 추가(현재 `findByTargetAndFilterReleaseId`만 있고 target만으로
  최신 사건을 찾는 방법이 없음을 확인했다).
- `/api/v1/operator/report-cases` 신규 REST API 5개 엔드포인트(대기열 조회,
  검토 시작, 판정, 추가 정보 요청, 복원) + `SecurityConfiguration` 신규
  세션 기반 `SecurityFilterChain`(`/api/v1/operator/**`, OPERATOR role).
- 두 운영자 동시 판정 방지 — `#155`가 이미 구현한 `findByIdForUpdate` 행
  잠금 계약의 동시성 재검증(신규 로직 아님, 새 API 경로를 통해 도달 가능한지
  확인).
- 신고자 경로(`findMyReports` 등)가 `moderation_review`를 조인하지 않는다는
  계약 확인(신규 assertion 필요, 재사용 가능한 기존 테스트 없음을 확인했다).

### Excluded

- `CRITICAL` 1건 즉시 전역 숨김(R04, 법무 결정) — 이슈 본문 명시 제외.
- 운영자 백오피스 화면 — 이슈 본문 명시 제외.
- 허위 신고자 제재 정책 — 이슈 본문 명시 제외.
- SLA 초과의 실제 알림 발송(push/Slack/이메일) — `overdue` 플래그 표시까지만
  (TASK.md Scope decision 4).
- `operator_action_audit`(V20) 확장 — TASK.md Scope decision 3.
- 신고 접수·병합·evidence 스냅샷 경로 자체(#154), 신고자 한정 숨김·전역
  숨김·결과 알림 fan-out 자체(#155) — 이미 구현·테스트됨, 회귀만 확인한다.
- `filter_job`/`ManualReviewCase`의 실제 판정 산출 로직(#103~#113) — 이
  계획은 이미 존재하는 상태를 "조회"만 하고, 그 상태를 만드는 필터링
  파이프라인 자체는 재검증하지 않는다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #156 | `CRITICAL` 하위 사유가 `URGENT` 대기열로 라우팅된다 |
| GitHub Issue #156 | `NORMAL` 사건에 `CRITICAL` 신고가 붙으면 승격되고 `ESCALATED` 이벤트가 남는다 |
| GitHub Issue #156 | 두 운영자가 같은 사건을 동시에 판정해도 판정은 한 번만 기록된다(PostgreSQL 동시성 테스트) |
| GitHub Issue #156 | 사건은 재개방되지 않고 재발은 새 사건이 된다(`INV-RPT-007`) |
| GitHub Issue #156 | 임계값·SLA가 전부 주입값이고 코드에 상수로 박히지 않았다 |
| GitHub Issue #156 | 운영자 응답에만 `internal_note`가 포함되고 신고자 경로에는 어떤 쿼리에서도 `moderation_review`를 조인하지 않는다 |
| GitHub Issue #156 | 자동 전역 숨김: 서로 다른 신고자 수 임계 도달 / 같은 대상의 filter_job 판정이 이미 숨김·수동검토 |
| TASK.md Scope decision 2 | `/api/v1/operator/report-cases`는 세션 기반 OPERATOR 인증이 필요하고 기존 `/api/**`(JWT bearer) 체인보다 우선 평가돼야 한다 |
| TASK.md Scope decision 4 | SLA 초과는 대기열 조회 API의 `overdue` 플래그로만 표시, 별도 발송 없음 |
| `V19__add_report_case_and_evidence_snapshot.sql` | `ck_report_case_resolution`: `decision=MORE_INFO_REQUIRED`+`resolved_at=NULL`이면서 `status`가 RESOLVED가 아닌 조합이 CHECK를 통과함 |
| `JdbcReportCaseRepository.tryOpen` | `ON CONFLICT (...) WHERE ... AND status IN ('OPEN','UNDER_REVIEW') DO NOTHING` — target별 partial unique index로 INV-RPT-001 강제 |
| `SafetyCaseResolutionService.resolveCase` | `findByIdForUpdate` 행 잠금 + 상태 전이 검사, `ACTIONED`+`answerId != null`일 때만 `suppressAnswer` 호출 |
| `filtering/domain/ManualReviewCaseStatus.java`, `FilterVerdict` | `OPEN`/`RESOLVED`, `resolvedVerdict`는 `RESOLVED`일 때만 존재 — "이미 숨김·수동검토"는 `OPEN` 또는 `RESOLVED`+`BLOCK` |
| `AppealCaseIntegrationTest#concurrentDecisionResolvesOnce` | `CountDownLatch(2)`+`CountDownLatch(1)`+2-way `ExecutorService` 경합 패턴 — 동시 판정 테스트의 재사용 템플릿 |
| `ManualReviewPriorityIntegrationTest.java:434-482` | `/admin/csrf` → `/admin/login` → `SESSION` 쿠키 재사용 흐름 — 신규 세션 기반 엔드포인트의 end-to-end 인증 테스트 템플릿 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 신규 사건이 CRITICAL 신고로 열렸는데도 `ReportCase.open`이 여전히 NORMAL/STANDARD로 고정 | CRITICAL 신고가 URGENT 큐에 아예 들어오지 않음 — 안전 문제 | Medium | P0 | INT-001 |
| 이미 열린 NORMAL 사건에 CRITICAL 신고가 붙어도 승격 로직이 호출되지 않거나 행 잠금 없이 경합 | 승격 유실, ESCALATED 이벤트 누락/중복 | Medium | P0 | INT-002, INT-003(동시성) |
| 승격 시 `sla_due_at`를 재계산하지 않아 URGENT인데 STANDARD SLA로 남음 | SLA 초과 판정이 늦게 뜸 | Low | P1 | INT-002 |
| 두 운영자가 같은 사건을 동시에 판정 API로 호출해도 새 웹 경로가 `findByIdForUpdate` 잠금을 우회 | 판정 중복 기록, 알림 중복 | Medium | P0 | INT-010(동시성) |
| 새 `/api/v1/operator/**` 필터체인이 기존 `appApiSecurityFilterChain`(`/api/**`, JWT)보다 뒤에 평가돼 앱 사용자 JWT로도 운영자 API에 접근됨 | 권한 우회, 심각한 보안 결함 | Low | P0 | INT-011, INT-012 |
| 자동 숨김의 distinct reporter 수 계산이 신고 상태(예: 철회된 신고)를 걸러내지 않고 전부 셈 | 임계값 미만인데도 자동 숨김 | Low | P1 | INT-005 |
| `ManualReviewCase` 상관관계 조회가 `RESOLVED`+`ALLOW`(무혐의)까지 자동 숨김 트리거로 오인 | 정상 콘텐츠가 자동 숨김됨 | Medium | P0 | INT-007 |
| 자동 숨김이 이미 숨김된 사건에 매 신고마다 재실행돼 `resolveCase`가 이미 RESOLVED인 사건에 대해 예외를 던지고 신고 접수 자체가 실패함 | 신고 접수 API가 500으로 깨짐 | Medium | P0 | INT-008 |
| `moderation_review`가 신고자 조회 SQL에 우연히 섞여 들어감(운영자 응답 조립 코드를 신고자 응답과 공유) | 신고자에게 운영자 전용 `internal_note` 노출 — 개인정보/기밀 유출 | Low | P0 | INT-013 |
| 대기열 API의 `overdue` 계산이 서버 시각이 아니라 DB `now()`와 애플리케이션 `Clock`을 혼용해 근소하게 어긋남 | 경계값에서 오탐/누락 | Low | P2 | INT-009 |
| `MORE_INFO_REQUIRED` 처리 후 사건이 실수로 RESOLVED로 전이됨(체크 제약과 도메인 가드가 어긋남) | 추가 정보 요청 사건이 조용히 종결 처리됨 | Low | P1 | INT-006 |

## 5. Unit scenarios

순수 도메인 로직만 — DB 없음.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| UNIT-001 | `ReportSubReason.CSAM`/`NCII`/`CREDIBLE_THREAT` | 심각도 산출 함수 호출 | `ReportCaseSeverity.CRITICAL` | P0 | Executor A |
| UNIT-002 | `ReportSubReason` null 또는 위 3값 외(향후 추가 대비, 현재는 3값이 전부) | 심각도 산출 함수 호출 | `ReportCaseSeverity.NORMAL` | P0 | Executor A |
| UNIT-003 | `ReportCaseSeverity.CRITICAL`/`NORMAL` | 큐 라우팅 함수 호출 | `URGENT`/`STANDARD` 각각 매핑 | P0 | Executor A |
| UNIT-004 | `ReportCase.open(...)`을 초기 severity=CRITICAL, queue=URGENT로 호출(신규 시그니처) | 생성 | `severity==CRITICAL`, `queue==URGENT`, `status==OPEN` | P0 | Executor A |
| UNIT-005 | OPEN 상태의 NORMAL/STANDARD `ReportCase` | `escalate(at)` 호출 | `severity==CRITICAL`, `queue==URGENT`, 나머지 필드(`id`/`decision`/`createdAt`) 보존 | P0 | Executor A |
| UNIT-006 | RESOLVED 상태의 `ReportCase` | `escalate(at)` 호출 | `SafetyException(REPORT_CASE_ALREADY_RESOLVED)` | P0 | Executor A |
| UNIT-007 | URGENT 상태의 `ReportCase` | `deescalate(at)` 호출 | `severity==NORMAL`, `queue==STANDARD` | P1 | Executor A |
| UNIT-008 | OPEN 상태의 `ReportCase` | `requestMoreInfo(at)` 호출 | `decision==MORE_INFO_REQUIRED`, `resolvedAt==null`, `status`는 OPEN 그대로(RESOLVED 아님) | P0 | Executor A |
| UNIT-009 | UNDER_REVIEW 상태의 `ReportCase` | `requestMoreInfo(at)` 호출 | `decision==MORE_INFO_REQUIRED`, `status`는 UNDER_REVIEW 그대로 | P1 | Executor A |
| UNIT-010 | RESOLVED 상태의 `ReportCase` | `requestMoreInfo(at)` 호출 | `SafetyException(REPORT_CASE_ALREADY_RESOLVED)` | P1 | Executor A |
| UNIT-011 | `SlaPolicy` 생성자에 0 또는 음수 `Duration` | 생성 | 예외(compact constructor 검증, `ReportRateLimitPolicy` 관례와 동일) | P1 | Executor A |
| UNIT-012 | `AutoSuppressionPolicy` 생성자에 0 또는 음수 임계값 | 생성 | 예외 | P1 | Executor A |
| UNIT-013(회귀) | 기존 `ReportCaseAndEvidenceTest`의 `ReportCase.open(...)` 호출부 6곳 | 신규 시그니처로 갱신(초기 severity/queue를 NORMAL/STANDARD로 명시 전달) | 기존 단언(모두 NORMAL/STANDARD로 여는 케이스) 그대로 통과 | P0 | Executor A |

## 6. Integration scenarios

DB 필요(PostgreSQL). Spring 컨텍스트 기동, 실제 트랜잭션 사용.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| INT-001 | `SafetyReportService.submitAnswerReport`, `mergeCase` | 대상 답변에 열린 사건 없음 | `subReason=CSAM`으로 신고 접수 | 새로 열린 `report_case`가 `severity=CRITICAL`, `queue=URGENT`, `sla_due_at`이 URGENT SLA 기준 | 트랜잭션 롤백 |
| INT-002 | `mergeCase` 승격 경로 | `subReason=null`(NORMAL)로 이미 열린 사건 1개 | 같은 대상에 `subReason=CSAM`으로 2번째 신고 접수 | 사건이 `CRITICAL`/`URGENT`로 승격, `report_case_event`에 `ESCALATED`와 `REPORT_ATTACHED` 둘 다 기록, `sla_due_at`이 URGENT 기준으로 재계산 | 트랜잭션 롤백 |
| INT-003 | `mergeCase` 승격 경로, 2-way 동시성(`AppealCaseIntegrationTest` 패턴 재사용) | 이미 열린 NORMAL 사건 1개 | 서로 다른 신고자 2명이 `subReason=CSAM`으로 동시에 신고 접수 | 사건은 정확히 한 번만 CRITICAL로 승격, `ESCALATED` 이벤트는 정확히 1건(2건 아님), 두 신고 모두 저장은 성공(승격 자체가 신고 저장을 막지 않음) | 트랜잭션 롤백 |
| INT-004 | `mergeCase` | 이미 CRITICAL/URGENT인 사건 | `subReason=null`(NORMAL)로 신고 추가 접수 | 사건은 여전히 CRITICAL/URGENT(강등 없음), `ESCALATED` 이벤트 없음 | 트랜잭션 롤백 |
| INT-005 | 자동 숨김(신고자 수 임계) | `AutoSuppressionPolicy` 임계값을 테스트에서 작게 주입(예: 2), 서로 다른 신고자 N명이 같은 대상에 신고 | 신고자 수가 임계값에 도달하는 순간의 접수 요청 | `SafetyCaseResolutionService.resolveCase`가 자동 호출돼 `Answer.status==HIDDEN`, `report_case.status==RESOLVED`, `decision==ACTIONED` | 트랜잭션 롤백 |
| INT-006 | 자동 숨김 임계 미도달 | 임계값 2, 신고자 1명만 신고 | 접수 | 사건은 여전히 OPEN, 콘텐츠는 그대로 PUBLISHED | 트랜잭션 롤백 |
| INT-007 | 자동 숨김(ManualReviewCase 상관관계) | 같은 대상(`target_type`+`target_id`)에 `ManualReviewCaseStatus.OPEN`인 `ManualReviewCase` 존재 | 신고 접수 | 자동으로 `resolveCase` 호출, `report_case.linked_manual_review_case_id`가 그 `ManualReviewCase.id`로 기록 | 트랜잭션 롤백 |
| INT-008 | 자동 숨김(ManualReviewCase 상관관계, 무혐의) | 같은 대상에 `ManualReviewCaseStatus.RESOLVED`+`resolvedVerdict=ALLOW`인 `ManualReviewCase` 존재 | 신고 접수 | 자동 숨김 트리거되지 않음, 사건은 일반 흐름대로 OPEN | 트랜잭션 롤백 |
| INT-009 | 자동 숨김 멱등성 | INT-005/007로 이미 자동 숨김·RESOLVED된 사건에 새 신고가 접수될 수 있는 경로가 있다면(재발은 새 사건이 되므로 실제로는 새 `report_case`가 열림, `INV-RPT-007`) | 같은 대상에 신고를 한 번 더 접수 | 새 `report_case`가 열리고(재개방 아님), 자동 숨김 평가는 그 신규 사건 기준으로 독립적으로 이루어짐 — 기존 RESOLVED 사건은 건드리지 않음 | 트랜잭션 롤백 |
| INT-010 | 대기열 조회 API `overdue` | `sla_due_at`이 과거인 사건 1개, 미래인 사건 1개 | `GET /api/v1/operator/report-cases` 세션 인증 호출 | 과거 사건은 `overdue: true`, 미래 사건은 `overdue: false` | 트랜잭션 롤백, 세션 정리 |
| INT-011 | 대기열 조회 API `queue` 필터 | URGENT 사건 1개, STANDARD 사건 1개 | `GET /api/v1/operator/report-cases?queue=URGENT` | URGENT 사건만 반환 | 트랜잭션 롤백 |
| INT-012 | `POST .../review` | OPEN 사건 1개 | 세션 인증으로 검토 시작 호출 | `report_case.status==UNDER_REVIEW` | 트랜잭션 롤백 |
| INT-013 | `POST .../decision`(ACTIONED) | UNDER_REVIEW 사건, `internal_note` 포함 요청 본문 | 판정 호출 | `SafetyCaseResolutionService.resolveCase` 경유로 사건 RESOLVED+ACTIONED, `moderation_review`에 `internal_note` 기록, 대상이 답변이면 전역 숨김까지 연쇄(#155 회귀) | 트랜잭션 롤백 |
| INT-014 | `POST .../decision`(NO_VIOLATION) | UNDER_REVIEW 사건 | 판정 호출 | 사건 RESOLVED+NO_VIOLATION, 콘텐츠는 숨겨지지 않음 | 트랜잭션 롤백 |
| INT-015 | `POST .../more-info` | UNDER_REVIEW 사건 | 추가 정보 요청 호출 | `decision==MORE_INFO_REQUIRED`, `status`는 UNDER_REVIEW 유지(재종결 가능한 상태), `report_case_event`에 `MORE_INFO_REQUESTED` 기록 | 트랜잭션 롤백 |
| INT-016 | `POST .../restore` | ACTIONED로 이미 숨김된 답변을 가진 RESOLVED 사건 | 복원 호출 | `Answer.status==PUBLISHED`(#155 `Answer.restore` 재사용 회귀) | 트랜잭션 롤백 |
| INT-017 | `POST .../decision`, 2-way 동시성(`AppealCaseIntegrationTest#concurrentDecisionResolvesOnce` 패턴, `resolveCase` 대상 교체) | UNDER_REVIEW 사건 1개 | 서로 다른 두 세션(운영자 계정 2개 또는 같은 계정의 동시 요청)이 같은 사건에 동시에 판정 API 호출 | 정확히 하나만 성공(200), 다른 하나는 `REPORT_CASE_ALREADY_RESOLVED`(400) 또는 그에 대응하는 API 오류, `moderation_review` 행은 정확히 1건 | 트랜잭션 롤백, 세션 정리 |
| INT-018 | `SecurityConfiguration` 신규 필터체인 | 세션 없음(비인증) | `GET /api/v1/operator/report-cases` 호출 | `401` | 없음 |
| INT-019 | `SecurityConfiguration` 신규 필터체인 vs 기존 `appApiSecurityFilterChain` | 앱 사용자용 JWT bearer 액세스 토큰(디바이스 등록 경로로 정상 발급, OPERATOR 세션 없음) | 그 JWT로 `GET /api/v1/operator/report-cases` 호출 | 세션이 없으므로 `401`(JWT가 이 경로에서 인증 수단으로 받아들여지지 않아야 한다 — bearer 토큰이 실수로 통과하면 이 테스트가 실패해야 함) | 없음 |
| INT-020 | 신고자 조회 경로 회귀 | `findMyReports`가 실행하는 실제 SQL 문자열(상수 또는 로깅) | 신고자 세션으로 `내 신고 목록` 조회 API 호출, 응답 필드에 `internal_note`/판정 세부가 없는지 확인 + 해당 리포지토리 SQL 상수에 `moderation_review` 문자열이 없는지 소스 검사 | 응답에 운영자 전용 필드 없음, SQL에 `moderation_review` 조인 없음 | 트랜잭션 롤백 |
| INT-021(회귀) | `INV-RPT-007` | RESOLVED 사건 1개(#154/#155 패턴 재사용) | 같은 대상에 새 신고 접수 | 기존 사건은 재개방되지 않고 새 `report_case`가 열림 — 이미 #154 통합 테스트가 커버하는지 확인하고, 커버하지 않으면 이 계획에서 추가 | 트랜잭션 롤백 |

## 7. Cross-cutting scenarios

### Database and transactions

- INT-005~009는 "신고 접수 트랜잭션 안에서 자동 숨김까지 같이 커밋되는지"를
  검증한다 — 자동 숨김 평가가 별도 트랜잭션으로 분리되면 신고는 저장됐는데
  숨김은 실패하는 반쪽 상태가 가능해진다. 실행 에이전트는 `mergeCase` 호출과
  같은 `@Transactional` 경계 안에서 자동 숨김 평가를 호출하는지 실제로
  확인한다(계획 단계에서 단정하지 않는다 — `SafetyReportService.submit`이
  이미 하나의 트랜잭션인지 실행 시점에 재확인).
- `report_case.sla_due_at`을 `NOT NULL`로 추가하는 마이그레이션은 기존 행이
  없는 개발 스키마 기준으로 설계한다(운영 배포 시 별도 backfill 필요 여부는
  이 계획 밖 — 마이그레이션 작성 시점에 기존 행 존재 여부를 다시 확인).

### Concurrency and idempotency

- INT-003: 승격 경합. `mergeCase`의 기존 승자/패자 재조회 루프(`tryOpen`
  실패 시 `findOpenByTarget` 재조회) 위에 승격 잠금(`findByIdForUpdate`)이
  얹히므로, 두 로직이 서로 데드락을 만들지 않는지 실제 스레드 경합으로
  확인한다.
- INT-009: 자동 숨김 재평가와 `resolveCase`의 상태 가드가 함께 동작해
  이미 RESOLVED인 사건에 대해 자동 숨김이 재호출되지 않는지 확인한다(재호출
  자체가 예외를 던지면 신고 접수 API가 깨지므로, 이미 RESOLVED인 사건은
  자동 숨김 평가 자체를 건너뛰는 가드가 필요하다 — `#155`의 `resolveCase`
  가드에만 기대지 않는다).
- INT-017: `#155`가 이미 검증한 `findByIdForUpdate` 동시성 계약을 새 REST
  경로를 통해 재확인한다(신규 잠금 로직 아님, 도달 경로 검증).

### External APIs

- 해당 없음 — 이슈 범위에 외부 API 연동이 없다.

### Failure recovery and reconciliation

- INT-005/007이 실패 복구의 핵심이다 — 자동 숨김 트랜잭션이 중간에 실패하면
  신고 저장까지 롤백되는지, 아니면 신고는 저장되고 숨김만 실패해 불일치
  상태가 남는지 확인한다(전자가 정상, 후자면 결함).
- `ManualReviewCaseRepository`에 신규 target 조회 메서드가 대상을 못 찾을
  때(정상 케이스, 상관관계 없음) `Optional.empty()`를 반환하고 예외를 던지지
  않는지 확인한다(INT-006 같은 "미도달" 케이스와 별개로, 아예 filtering
  파이프라인을 거치지 않은 대상에 대한 조회도 안전해야 한다).

## 8. Test data and isolation

- Fixtures: `#154`/`#155` 통합 테스트(`ReportIntakeApiIntegrationTest`,
  `AnswerGlobalHideIntegrationTest`, `NicknameDuplicateModerationIntegrationTest`
  등)가 이미 쓰는 질문글/답변/신고/계정 생성 헬퍼가 있으면 재사용한다.
  `ManualReviewCase`/`filter_job` fixture는 `filtering` 패키지의 기존 통합
  테스트(`ManualReviewPriorityIntegrationTest` 등)에서 생성 패턴을 가져온다.
- Database isolation: 기존 통합 테스트 컨벤션(트랜잭션 롤백 또는 명시적
  cleanup)을 그대로 따른다.
- Clock/randomness: 기존 `Clock`/`Instant at` 주입 패턴을 그대로 따라 고정
  시각을 쓴다. `overdue` 판정(INT-010)은 SLA 기준 시각을 테스트가 직접
  주입해 실제 벽시계에 의존하지 않는다.
- External API doubles: 불필요.
- Cleanup: 세션 기반 통합 테스트(INT-010~019)는 `ManualReviewPriorityIntegrationTest`의
  `OperatorSession` 헬퍼 패턴을 재사용해 세션/CSRF 쿠키를 테스트마다 새로
  발급한다(다른 테스트로 세션이 새지 않게).

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Executor A (도메인) | `safety/domain/ReportCase.java`, `safety/domain/ReportCaseSeverity.java`(정적 팩터리 `of(ReportSubReason)` 추가), `safety/domain/ReportCaseQueue.java`(정적 팩터리 `of(ReportCaseSeverity)` 추가), `safety/config/SlaPolicy.java`(신규), `safety/config/AutoSuppressionPolicy.java`(신규), `safety/ReportCaseAndEvidenceTest.java` 회귀 갱신, 신규 단위 테스트 | UNIT-001~013 | `./gradlew test --tests "com.dnd.qello.safety.*"` |
| 2 | Executor A (스키마·리포지토리) | 신규 Flyway 마이그레이션(`sla_due_at`, `linked_manual_review_case_id`), `safety/repository/jdbc/JdbcReportCaseRepository.java`(save/tryOpen/update/mapReportCase에 신규 컬럼 반영), `filtering/repository/ManualReviewCaseRepository.java` + JDBC 구현체(target 단독 조회 신규 메서드) | (스키마 확인은 INT-001~009의 전제조건) | `./gradlew integrationTest --tests "*Flyway*"` |
| 3 | Executor A (서비스 배선) | `safety/service/SafetyReportService.java`(mergeCase 확장 + 자동 숨김 평가 호출), 신규 자동 숨김 평가 헬퍼(같은 서비스 또는 `safety/service/AutoSuppressionEvaluator` 등 — 실행 시점에 적절한 위치 결정) | INT-001~009, INT-021 | `./gradlew integrationTest --tests "com.dnd.qello.*ReportCase*"` |
| 4 | Executor A (운영자 API) | 신규 `safety/web/OperatorReportCaseController.java`+`*ApiSpec.java`+요청/응답 record, `auth/config/SecurityConfiguration.java`(신규 필터체인, 기존 `@Order` 조정) | INT-010~019 | `./gradlew integrationTest --tests "com.dnd.qello.*OperatorReportCase*"` |
| 5 | Executor A (교차 검증) | 위 전체에 대한 신고자 경로 회귀(`safety/repository/jdbc/sql/*.java`의 `moderation_review` 부재 확인) | INT-020 | `./gradlew test --tests "com.dnd.qello.safety.*"` (소스 검사 성격이라 별도 assertion으로 처리 — 실행 에이전트가 구체 방식 결정) |

단일 실행 에이전트(Executor A)로 순서만 나눈 이유: 도메인 전이(escalate 등)
→ 스키마 → 서비스 배선 → 웹 계층이 순차적으로 서로에게 의존하고, 자동
숨김·승격·SLA가 같은 트랜잭션 경계와 같은 `ReportCase` 상태 머신을 공유해
`#155`와 같은 이유로 여러 에이전트로 쪼개면 계약이 어긋나기 쉽다. 병렬화가
필요하면 1(도메인)과 2(스키마·리포지토리)는 서로 독립적이라 동시 진행
가능하다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario ID 기록
      (`Source scenario: TEST-PLAN-GH-156-REPORT-SEVERITY-OPERATOR-REVIEW`)
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석(애플리케이션·DB·동시성·트랜잭션·장애 복구 관점)
- [ ] 테스트 보고서 생성(`templates/test-report.md`)
- [ ] INT-019(JWT bearer가 운영자 API를 우회하지 못함)가 실제로 실행됐고
      통과했음을 보고서에 명시 — 이 계획에서 가장 심각도가 높은 위험이다
- [ ] `sla_due_at` 재계산(승격 시) 여부가 실제로 어떻게 구현됐는지, TASK.md의
      ASSUMED 결정과 일치하는지 보고서에 명시

## 11. Human approval

- Reviewer: 사용자(저장소 소유자)
- Decision: Approved ("승인")
- Approved at: 2026-08-20
