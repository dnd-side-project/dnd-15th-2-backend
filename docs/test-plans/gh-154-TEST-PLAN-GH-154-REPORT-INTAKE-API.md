# Test Plan: TEST-PLAN-GH-154-REPORT-INTAKE-API

> Created at: `2026-08-18T21:40:00+09:00`
> GitHub Issue: `#154`
> Status: Approved

## 1. Objective

사용자가 더보기 → 신고 → 사유 선택 → (설명) → 접수까지 완료하고 접수증을
받는 REST 경로를 검증한다. Foundation(#153, `main` 병합 완료)이 만든
`ReportCase`/`ReportContentSnapshot`/`ReportCaseEvent`/`Report.attachToCase`와
부분 유일 인덱스 위에, 이 이슈는 처음으로 실제 신고 흐름을 조립한다.

검증해야 하는 것:

- 신고 시점 증거가 **신고 저장과 같은 트랜잭션**에서 남는다(`INV-RPT-003` —
  Foundation은 스키마가 원자성을 허용함만 증명했고, 이 이슈가 실제로 그렇게
  호출하는 첫 서비스다).
- 같은 대상에 서로 다른 신고자가 동시에 접수해도 사건은 하나로 수렴한다
  (`INV-RPT-001`의 서비스 레벨 소비 — Foundation의 `ON CONFLICT` 없는 raw
  INSERT 위에 이 이슈가 처음으로 재조회·병합 로직을 얹는다).
- 반복 신고가 사건을 무한 생성하지 않는다: 같은 신고자의 열린 신고는
  멱등하게 반환하고, 종결된 사건이라도 내용이 그대로면 새 사건을 만들지
  않는다(완료 조건 2·4번).
- 신고자에게 반환되는 어떤 응답도 상대 식별자나 운영자 내부 판단을 담지
  않는다(`INV-RPT-005`).
- 열람 자격이 없는 대상은 신고할 수 없고, 존재 여부를 노출하지 않는다
  (완료 조건 5번, 존재하지 않는 대상과 볼 수 없는 대상이 같은 응답).

## 2. Scope

### Included

- `ReportSubmission`(신규 값 객체, `safety.domain`) — 사유·하위사유 조합과
  `OTHER` 사유의 설명 필수 여부를 검증한다. Foundation은 이 검증을 의도적으로
  DB CHECK에만 맡기고 도메인 레벨에서는 하지 않았다(§3.2) — 이 이슈가 처음
  도메인 레벨 검증을 추가해 빠른 400 실패를 만든다. 예약된
  `SAF-VAL-006`(`INVALID_REPORT_DETAIL`)·`SAF-VAL-007`
  (`INVALID_REPORT_SUB_REASON`)을 소비한다.
- `SafetyReportService`(신규, `safety.service`) — 자기 신고 거절
  (`SAF-DOM-003`), rate limit(`SAF-APP-004`), 대상 열람 자격 확인 + 404
  (`SAF-APP-002`), 같은 신고자의 열린 신고 멱등 반환, 종결된 사건과 내용
  동일 시 억제(`DUPLICATE_SUPPRESSED` 이벤트만 기록), 사건 병합
  (`ReportCaseRepository.tryOpen` 신규 메서드로 `ON CONFLICT DO NOTHING` +
  재조회), 증거 스냅샷 캡처, `blockAuthor` 옵션의 같은 트랜잭션 차단 통합.
- `ReportTargetRepository`(신규 포트+JDBC) — 답변/질문글/사용자 대상의
  존재·열람 자격·현재 콘텐츠(작성자, 본문, 미디어, 편집 횟수, 공개 시각)를
  한 번에 읽는다. 답변은 `PostAnswerQuerySql.CAN_VIEW_ANSWERS_SQL`과 같은
  열람 자격 조건을, 질문글은 `FeedScopeSql.ACTIVE_POST_VISIBILITY`와 같은
  조건을 재사용한다.
- `SafetyRepository` 확장 — `findMostRecentClosedReport`(종결된 재신고 억제용),
  `countReportsByReporterSince`(rate limit용), `findReportsByReporter`
  (커서 페이지네이션, `/reports/me`용).
- `ReportCaseRepository` 확장 — `tryOpen`(경쟁 시 빈 값 반환), `findOpenByTarget`.
- `safety/web` 신규: `SafetyController`, `SafetyApiSpec`, request/response
  record. 엔드포인트 8개(이슈 본문과 동일).
- `docs/api/openapi.json` 재생성 — 기존
  `OpenApiSpecificationIntegrationTest`를 재실행해 커밋한다(신규 테스트
  아님).
- `docs/error-codes.md` SAF 절 갱신.

### Excluded

- 집계 제외 2계층, 결과 알림 fan-out(`#155`).
- 심각도 산출, 대기열 라우팅, 운영자 판정 API(`#156`).
- 보존 기간, 국가별 분기, `CRITICAL` 즉시 숨김 정책(`#157`).
- rate limit·설명 길이 상한의 실제 운영 수치 — 주입 값으로만 존재한다
  (`AGENTS.md` §4.3 표기로 `UNKNOWN`, 테스트는 주입한 임의값으로 경계만
  검증한다).
- 사용자 신고의 "관계 확인" 기준(§9.4 참고) — 사용자 간 방향글/수신 이력이
  하나라도 있으면 열람 가능으로 **가정**한다(`ASSUMED`). 실제 제품 정책이
  다르면 후속 이슈에서 좁힌다.
- 접수 시점 푸시 알림 — 동기 응답의 접수증으로 대신한다.
- `PERMANENT_CONFIRMED` 성격의 즉시 조치 — 이 이슈는 접수만 하고 아무것도
  자동으로 숨기지 않는다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #154 | 접수 응답은 `reportId`·`status`·`receivedAt`·안내 문구만 담고 상대 식별자·내부 판단을 담지 않는다(`INV-RPT-005`) |
| GitHub Issue #154 | 같은 신고자의 재신고가 새 행을 만들지 않고 기존 접수증을 반환한다 |
| GitHub Issue #154 | 서로 다른 신고자 2명이 동시에 같은 대상을 신고해도 사건이 1개다 |
| GitHub Issue #154 | 종결된 사건과 내용이 같은 재신고가 사건을 만들지 않고 이벤트만 남긴다 |
| GitHub Issue #154 | 열람 자격 없는 사용자의 신고가 404다 |
| GitHub Issue #154 | `OTHER` 사유에 설명이 없으면 400이다 |
| GitHub Issue #154 | `docs/api/openapi.json`이 재생성돼 있다 |
| 설계 문서 §6.1 | 신고 중복 3종(멱등/병합/억제)의 정확한 분기 조건 |
| 설계 문서 §6.2 | 사건 병합 동시성은 `ON CONFLICT ... DO NOTHING` + 재조회로 처리한다 |
| 설계 문서 §9.4 | 신고자가 볼 수 없는 콘텐츠는 신고할 수 없고, 없는 대상과 볼 수 없는 대상 모두 404다 |
| 설계 문서 §10 | 신고자 응답에 `moderation_review`를 어떤 쿼리에서도 조인하지 않는다 |
| Foundation(#153) `ReportCase` 주석 | "여러 신고자의 제보를 하나로 묶는 병합 로직은 소유하지 않는다(#154)" — 이 이슈가 그 소유자다 |
| Foundation(#153) `SafetyErrorCode` 주석 | `SAF-VAL-006`·`007`·`SAF-DOM-003`·`SAF-APP-002`~`004`는 이 이슈가 예약한 번호 |
| 기존 코드: `AppealController`/`AppealApiSpec`(#112) | `AuthenticatedUserId.require`, `ApiResponseFactory`, Controller/ApiSpec 분리 관례 |
| 기존 코드: `AnswerSubmissionApiMockMvcTest`(#125) | standalone MockMvc + `GlobalExceptionHandler` + `AuthenticationResolver` 계약 테스트 패턴 |
| 기존 코드: `OpenApiSpecificationIntegrationTest` | 스펙 재생성·검증을 겸하는 기존 테스트. 새로 만들지 않고 재실행한다 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 신고 저장과 스냅샷 저장이 실제로는 별도 트랜잭션이라 하나만 반영됨 | Critical — `INV-RPT-003` 위반, 증거 없는 신고 | Medium | P0 | INT-001, INT-017 |
| 사건 병합 재조회가 경쟁 상황에서 두 사건을 만들거나 예외를 흘림 | Critical — `INV-RPT-001` 위반 | Medium | P0 | INT-003 |
| `findOpenReport`처럼 status IN 목록이 `MORE_INFO_REQUIRED`를 빠뜨려 멱등 조회가 실패 | High — Foundation 결함이 API 레벨에서 재발 | Low(이미 Foundation이 수정, 회귀만 확인) | P1 | INT-002b |
| 종결 재신고 억제 판정이 신고자를 구분하지 않아 다른 사람의 신고로 억제됨 | High — 새 위반이 조용히 묻힘 | Medium | P0 | INT-004, INT-004b |
| 열람 자격 확인이 존재 여부와 자격 여부를 다른 상태 코드로 구분해 존재를 노출 | Medium — 대상 존재 스캔 가능 | Medium | P0 | INT-006 |
| rate limit 카운트가 신고자 아닌 전역으로 걸리거나 창을 벗어난 오래된 행까지 셈 | Medium — 무고한 사용자 차단 또는 무제한 신고 | Medium | P1 | INT-008 |
| `blockAuthor` 옵션이 신고 저장과 별도 트랜잭션이라 신고만 남고 차단 실패가 무시됨 | Medium | Low | P1 | INT-009 |
| 응답 DTO에 실수로 `authorId`/`internalNote` 등이 섞여 나감 | High — 개인정보·내부 판단 노출 | Medium | P0 | UNIT-011, UNIT-012 |
| `/reports/{id}` 접근이 소유자 확인 없이 다른 사용자의 신고를 반환 | High — 정보 노출 | Low | P0 | INT-014 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| UNIT-001 | `SEXUAL_CONTENT` + `CSAM` | `ReportSubmission` 생성 | 정상 생성 | P1 | Feature executor |
| UNIT-002 | `VIOLENCE_OR_THREAT` + `CREDIBLE_THREAT` | 생성 | 정상 생성 | P1 | Feature executor |
| UNIT-003 | `SPAM_OR_ADVERTISING` + `CSAM`(잘못된 조합) | 생성 | 거절(`SAF-VAL-007`) | P0 | Feature executor |
| UNIT-004 | `HATE_OR_HARASSMENT` + subReason 없음 | 생성 | 정상 생성(하위 사유 선택) | P2 | Feature executor |
| UNIT-005 | `OTHER` + `detail=null` | 생성 | 거절(`SAF-VAL-006`) | P0 | Feature executor |
| UNIT-006 | `OTHER` + `detail="구체적 설명"` | 생성 | 정상 생성 | P0 | Feature executor |
| UNIT-007 | `SPAM_OR_ADVERTISING` + `detail=null` | 생성 | 정상 생성(`OTHER`가 아니면 설명 선택) | P1 | Feature executor |
| UNIT-008 | `detail`이 주입 상한(예: 500자)보다 김 | 생성 | 거절(`SAF-VAL-006`) | P1 | Feature executor |
| UNIT-009 | 없음 | `ReportReasonResponse` 레코드 컴포넌트 조회 | `code`·`label`·`subReasons`·`detailRequired` 정확히 이 4개 | P1 | Feature executor |
| UNIT-010 | `ReportReason.OTHER`로 카탈로그 항목 생성 | `detailRequired` 조회 | `true`, 나머지 7종은 `false` | P1 | Feature executor |
| UNIT-011 | 없음 | `ReportReceiptResponse` 레코드 컴포넌트 조회 | `reportId`·`status`·`receivedAt`·`alreadyReceived`·`guidance` 정확히 이 5개(`INV-RPT-005`) | P0 | Feature executor |
| UNIT-012 | 없음 | `ReportSummaryResponse`·`ReportDetailResponse` 레코드 컴포넌트 조회 | 상대 식별자·닉네임·`internalNote`·사건 id 등 어떤 필드명에도 포함되지 않음(`INV-RPT-005`) | P0 | Feature executor |
| UNIT-013 | 인증 없는 MockMvc 클라이언트 | `POST /api/v1/answers/9/reports` 호출 | `401` | P0 | Feature executor |
| UNIT-014 | 인증됨, `reasonCode` 공백 요청 본문 | 호출 | `400` | P0 | Feature executor |
| UNIT-015 | 인증됨, mock 서비스가 신규 접수 결과 반환 | `POST /api/v1/answers/9/reports` 호출 | `201`, 서비스에 인증 subject·경로 변수·요청 필드가 정확히 전달됨 | P0 | Feature executor |
| UNIT-016 | 인증됨, mock 서비스가 `alreadyReceived=true` 반환 | 호출 | `200`(`201` 아님) | P0 | Feature executor |
| UNIT-017 | 인증됨 | `GET /api/v1/report-reasons` 호출 | `200`, 8종 배열 | P1 | Feature executor |
| UNIT-018 | 인증됨, mock 서비스가 `REPORT_NOT_FOUND` 예외 | `GET /api/v1/reports/{id}` 호출 | `404` | P0 | Feature executor |
| UNIT-019 | 인증됨 | `POST /api/v1/users/9/blocks` 호출 | `SafetyService.block`이 인증 subject를 blocker로 호출됨, 성공 상태 코드 | P1 | Feature executor |
| UNIT-020 | 인증됨, blocker == blocked | `POST /api/v1/users/{자기id}/blocks` 호출 | `400`(`SAF-DOM-001`, 기존 도메인 규칙 재사용 확인) | P1 | Feature executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| INT-001 | `SafetyReportService`, PostgreSQL | 열람 가능한 답변 1건 | `POST /answers/{id}/reports` 신규 접수 | `201`, `report`·`report_content_snapshot`·`report_case`(OPEN)·`report_case_event`(`CASE_OPENED`) 행이 모두 같은 커밋에 존재(`INV-RPT-003`) | 정리 |
| INT-002 | `SafetyReportService`, PostgreSQL | 같은 신고자가 이미 같은 답변에 열린 신고 보유 | 같은 답변 재신고 | `200`, `alreadyReceived=true`, `report` 행 수 불변 | 정리 |
| INT-002b | `SafetyReportService`, PostgreSQL | 같은 신고자의 기존 신고가 `MORE_INFO_REQUIRED` 상태 | 같은 답변 재신고 | `200`, `alreadyReceived=true`(Foundation의 `INV-RPT-002` 수정이 API 레벨에서도 유지됨을 재확인하는 회귀 테스트) | 정리 |
| INT-003 | `SafetyReportService`, PostgreSQL | 서로 다른 신고자 2명, 동일 답변, 둘 다 열람 가능 | 두 스레드에서 동시 `POST` | 둘 다 `201`(각자 새 신고), `report_case` 행은 1개, 두 `report.case_id`가 같은 값을 가리킴, 이벤트는 `CASE_OPENED` 1건 + `REPORT_ATTACHED` 1건 | 정리 |
| INT-004 | `SafetyReportService`, PostgreSQL | 같은 신고자가 이 답변을 이미 신고했고 그 사건이 `RESOLVED`, 답변 내용 미변경 | 같은 답변 재신고 | `200`, `alreadyReceived=true`, 새 `report`·`report_case` 행 생성 없음, 기존 사건에 `DUPLICATE_SUPPRESSED` 이벤트만 추가 | 정리 |
| INT-004b | `SafetyReportService`, PostgreSQL | INT-004와 동일 사건, 단 답변이 그 사이 수정돼 본문이 달라짐 | 같은 신고자가 재신고 | `201`, 새 `report_case`(OPEN) 생성(내용이 달라졌으므로 억제하지 않음) | 정리 |
| INT-005 | `SafetyReportService`, PostgreSQL | 신고자가 열람 자격이 없는 답변(차단 관계 또는 수신 자격 만료) | 신고 시도 | `404`, 존재하지 않는 답변 ID로 시도한 경우와 응답 본문이 동일한 오류 코드 | 정리 |
| INT-006 | `SafetyReportService`, PostgreSQL | 자기 자신이 작성한 답변 | 자기 답변 신고 시도 | `400`(`SAF-DOM-003`) | 정리 |
| INT-007 | `SafetyReportService`, PostgreSQL | 주입된 rate limit 한도(예: 창당 3회)에 도달한 신고자 | 한도 초과 신고 시도 | `429`(`SAF-APP-004`) | 정리 |
| INT-008 | `SafetyReportService`, `SafetyRepository`, PostgreSQL | 열람 가능한 답변, `blockAuthor=true` | 신고 접수 | `201`, `report` 저장과 `user_block` 저장이 같은 트랜잭션(강제 실패 주입 시 둘 다 rollback) | 정리 |
| INT-009 | `SafetyReportService`, PostgreSQL | 신고자와 매칭 이력(방향글 송수신)이 없는 사용자 | 그 사용자 신고 시도 | `404` | 정리 |
| INT-010 | `SafetyReportService`, PostgreSQL | 신고자와 방향글로 매칭된 이력이 있는 사용자 | 그 사용자 신고 | `201`, 스냅샷 `targetType=USER`, `bodyText`에 닉네임 캡처 | 정리 |
| INT-011 | `SafetyReportService`, PostgreSQL | 열람 가능한 질문글(direction_post) | 질문글 신고 | `201`, 스냅샷에 발신자 id·본문 캡처, `report.direction_post_id` 설정 | 정리 |
| INT-012 | `SafetyRepository`, PostgreSQL | 한 신고자의 신고 5건(서로 다른 대상, 생성 시각 분산) | `GET /reports/me` 커서 페이지네이션 | 최신순, 다른 신고자의 신고는 포함되지 않음 | 정리 |
| INT-013 | `SafetyRepository`, MockMvc, PostgreSQL | 신고자 A의 신고 1건 | 신고자 B가 `GET /reports/{A의 reportId}` 호출 | `404`(소유자 아님, `403` 아님 — 존재 비노출 원칙) | 정리 |
| INT-014 | `SafetyController`, PostgreSQL | 활성 차단 없음 | `POST /users/{id}/blocks` 후 `DELETE /users/{id}/blocks` | 각각 성공 상태 코드, `user_block.released_at` 설정 확인(기존 `SafetyService` 재사용 회귀 없음) | 정리 |
| INT-015 | `OpenApiSpecificationIntegrationTest`(기존, 재실행) | 새 8개 엔드포인트 추가 후 | 기존 테스트 재실행 | `docs/api/openapi.json`이 재생성돼 커밋된 파일과 일치, 금지 문자열 없음 | 해당 없음(기존 테스트) |

## 7. Cross-cutting scenarios

### Database and transactions

- 신고 접수는 단일 `@Transactional` 경계 안에서 `report` INSERT → 사건 병합
  (`tryOpen`/`findOpenByTarget`) → `report.attachToCase` → 스냅샷 INSERT →
  이벤트 INSERT → (옵션) 차단 INSERT까지 전부 처리한다. 어느 하나가
  실패해도 전체가 롤백됨을 INT-001·INT-008이 확인한다.
- `tryOpen`은 Foundation의 부분 유일 인덱스에 기대는
  `INSERT ... ON CONFLICT (target_column) WHERE status IN ('OPEN','UNDER_REVIEW') DO NOTHING RETURNING id`
  형태다. PostgreSQL이 대상별로 다른 partial index 3개를 갖고 있으므로
  `ON CONFLICT` 절도 호출하는 대상 컬럼에 맞춰 3가지로 분기한다.

### Concurrency and idempotency

- INT-003이 사건 병합의 핵심 동시성이다. 승자는 `RETURNING id`로 즉시
  `caseId`를 얻고, 패자는 빈 결과를 받아 `findOpenByTarget`으로 승자의
  `caseId`를 재조회한다 — 이 재조회 시점에 승자의 트랜잭션이 아직
  커밋되지 않았다면 패자는 승자가 커밋할 때까지 블로킹된다(같은 인덱스
  엔트리에 대한 행 잠금).
- 같은 신고자가 같은 대상에 동시에 두 번 요청을 보내는 경쟁은 별도로
  다루지 않는다 — `uq_open_report_*`가 두 번째 INSERT를 막고, 그 경합은
  멱등 응답(`alreadyReceived=true`) 대신 `DataIntegrityViolationException`
  이 한쪽에서 발생할 수 있다. 서비스는 이 예외를 잡아 재조회 후 멱등
  응답으로 흡수해야 한다 — INT-002의 확장으로 다루되, 정확한 재현은
  실행 에이전트가 필요 시 추가 시나리오로 보고한다.

### External APIs

- 해당 없음.

### Failure recovery and reconciliation

- rate limit 카운트는 `report.created_at` 기준 슬라이딩 윈도우 조회이므로
  별도 상태를 유지하지 않는다 — 재시작·재배포에도 카운트가 유실되지
  않는다.
- 트랜잭션 실패 시 부분 반영이 없어야 한다는 요구는 INT-008에서 강제
  실패를 주입해 검증한다.

## 8. Test data and isolation

- Fixtures: `account()`/`question()`/`post()`/`answer()` 헬퍼(#153의
  `ReportCaseFoundationIntegrationTest`와 동일한 형태로 재사용), 방향글
  송수신 매칭 이력을 만드는 헬퍼(사용자 신고 자격 시나리오용).
- Database isolation: `PostgisContainerIntegrationTestSupport`,
  `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`.
  MockMvc는 실제 HTTP 서버 없이 Spring context 내에서 요청을 디스패치한다.
- Unit(MockMvc 계약) 테스트는 DB 없이 `standaloneSetup(controller)` +
  `AuthenticationResolver`(#125 관례 재사용) + `GlobalExceptionHandler`로
  구성한다.
- Clock/randomness: 고정 `Instant`/`Clock` 주입, wall-clock 의존 없음.
- Cleanup: 각 통합 테스트가 `report_case_event`(TRUNCATE, append-only
  트리거 회피)·`report_content_snapshot`(TRUNCATE)·`user_block`·`report`·
  `report_case`·`answer`·`post_recipient`·`direction_post`·
  `approved_question`·`user_account` 순으로 정리한다(Foundation의
  `ReportCaseFoundationIntegrationTest.resetSchemaFixtures` 패턴 재사용).

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Feature executor | `safety/domain/ReportSubmission.java`; `safety/service/SafetyReportService.java`; `safety/repository/{ReportTargetRepository,ReportCaseRepository,SafetyRepository}.java`와 JDBC 구현; `safety/repository/jdbc/sql/ReportTargetSql.java`; `safety/web/**`(신규); `safety/error/SafetyErrorCode.java`(SAF-VAL-006/007, SAF-DOM-003, SAF-APP-002~004 실제 사용); `docs/error-codes.md`; `docs/api/openapi.json`(재생성); `src/test/java/com/dnd/qello/safety/**`, `src/test/java/com/dnd/qello/safety/web/**`(신규 단위·MockMvc 테스트); `src/integrationTest/java/com/dnd/qello/ReportIntakeApiIntegrationTest.java`(신규) | UNIT-001~020, INT-001~015 | `INV-RPT-001`·`003`·`005` 검증, Foundation `ReportCase`/`ReportContentSnapshot`/`Report.attachToCase` 계약과의 호환성 리뷰, `#112`(AppealController) REST 관례 일관성 리뷰 |

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성 —
      `docs/reports/tests/gh-154-TEST-PLAN-GH-154-REPORT-INTAKE-API.md`

## 11. Human approval

- Reviewer: tkv00
- Decision: Approved
- Approved at: `2026-08-18T21:45:00+09:00`
