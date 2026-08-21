# GitHub Issue #156 Task Contract

> Generated at: `2026-08-20T21:00:00+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `신고 시스템 — 심각도·긴급 대기열과 운영자 판정 API (R03)`
- GitHub Issue: `#156`
- Branch: `feat/gh-156-report-severity-operator-review`
- Base branch: `main`
- 선행 이슈 `#153`, `#154`, `#155`(PR #173) 전부 병합 확인
  (`origin/main` 최신 커밋 `bbecf3e`, `SafetyCaseResolutionService`·
  `ReportCaseRepository.findByIdForUpdate` 존재 확인 완료).
- Test plan: `TEST-PLAN-GH-156-REPORT-SEVERITY-OPERATOR-REVIEW`
  (`docs/test-plans/gh-156-TEST-PLAN-GH-156-REPORT-SEVERITY-OPERATOR-REVIEW.md`)
- Test plan approval: `APPROVED` — 사용자가 2026-08-20 "승인"으로 계획과
  TASK.md의 Scope decision 1~4를 승인했다.

## Objective

즉시 대응이 필요한 신고(CSAM·NCII·CREDIBLE_THREAT)를 일반 대기열과 분리해
`URGENT` 큐로 라우팅하고, 운영자가 사건을 검토·판정하는 내부 API 경로를
만든다. `#155`가 만든 `SafetyCaseResolutionService.resolveCase(...)`가 이
경로의 실행부를 담당하지만 REST 진입점이 없다 — `#156`이 그 진입점과, 그
앞단의 심각도 산출·대기열 라우팅·사건 승격·SLA·자동 숨김 조건을 채운다.

## Scope decision (사용자 승인 완료)

1. **브랜치 기반**: `#155`(PR #173)가 이미 `main`에 병합된 것을 확인했다 —
   `feat/gh-156-...`을 `main`에서 새로 분기한다(stacked 브랜치 불필요).
2. **운영자 API 경로**: 이슈 본문 그대로 `/api/v1/operator/report-cases…`를
   쓴다. 기존 운영자 API(`OperatorQuestionProposalController` 등)는
   `/admin/**` + `backofficeSecurityFilterChain`(세션 기반, OPERATOR role)을
   쓰는데, 이 경로는 그 패턴과 다르므로 **`SecurityConfiguration`에 새
   `SecurityFilterChain`을 추가한다** — `securityMatcher("/api/v1/operator/**")`,
   세션 기반 인증(백오피스와 동일하게 `OperatorLoginController`가 발급한
   세션 사용), OPERATOR role 요구, CSRF는 백오피스와 동일하게 적용한다.
   기존 `appApiSecurityFilterChain`(`/api/**`, JWT bearer, `@Order(2)`)보다
   우선 평가되도록 `@Order`를 그 앞에 둔다(예: 기존 체인들의 순서를 밀고 새
   체인을 `@Order(2)`로, 기존 app API 체인을 `@Order(3)`으로).
3. **판정 감사 기록**: 기존 `moderation_review` 테이블(`report_id`·
   `reviewer_id`·`decision`·`internal_note`·`reviewed_at` 이미 보유)만
   쓴다. `operator_action_audit`(V20)는 `target_type` CHECK에
   `REPORT_CASE`가 없고 이 이슈에서 확장하지 않는다 — 별도 이슈로 미룬다.
4. **SLA 에스컬레이션 "알림"**: 별도 발송 채널을 만들지 않는다. 대기열 조회
   API 응답에서 `sla_due_at`이 지난 사건을 `overdue` 플래그로만 표시하고,
   `ReportCaseEventType.ESCALATED`는 심각도 승격(§ Scope 1항)에만 쓴다.
   Push/Slack 실제 발송은 이 이슈 범위 밖이다(`notification_event`가
   `manual_review_case`에만 연결된 `UNIQUE` FK라 그대로 재사용하기 위험하고,
   운영자는 `push_device`를 가진 사용자 계정이 아니라 기존 in-app
   notification/outbox 경로도 맞지 않는다는 점을 확인했다).

## Scope

### 1. 심각도·큐 산출과 사건 승격

- `ReportCaseSeverity`/`ReportCaseQueue`는 이미 있다(둘 다 항상
  NORMAL/STANDARD로 여는 자리표시자 — 주석에 "#156이 실제 산출한다"라고
  명시돼 있음, **확인 완료**).
- 순수 함수로 `subReason(ReportSubReason) → ReportCaseSeverity`
  (CSAM/NCII/CREDIBLE_THREAT → CRITICAL, 그 외/null → NORMAL)와
  `severity → ReportCaseQueue`(CRITICAL → URGENT, NORMAL → STANDARD)를
  만든다. `ReportSubReason` enum이 정확히 그 3값만 가짐을 확인했다.
- `ReportCase.open(...)`(현재 `safety/domain/ReportCase.java`, 항상
  NORMAL/STANDARD로 생성)을 초기 severity/queue를 인자로 받도록 바꾼다 —
  새 사건이 CRITICAL 신고로 열리면 처음부터 URGENT로 열리고, 굳이
  ESCALATED 이벤트를 만들지 않는다(승격은 "이미 열린 NORMAL 사건에 나중에
  CRITICAL 신고가 붙는" 경우로 한정, 이슈 본문과 일치).
- `ReportCase`에 `escalate(Instant at)` 전이 메서드를 추가한다 — OPEN·
  UNDER_REVIEW에서만 허용, severity를 CRITICAL·queue를 URGENT로 바꾼다.
  RESOLVED에서 호출하면 `REPORT_CASE_ALREADY_RESOLVED`.
- `ReportCase`에 `deescalate(Instant at)` 전이 메서드도 추가한다 — 운영자
  전용 강등(URGENT→STANDARD). 신고 접수 경로에서는 호출하지 않는다.
- `SafetyReportService.mergeCase(...)`(현재 `safety/service/
  SafetyReportService.java:159`)를 확장한다:
  - `tryOpen` 성공 경로(신규 사건): 산출된 severity/queue로 연다.
  - `existing.isPresent()` 경로(기존 열린 사건에 병합): 새로 붙는 신고의
    severity가 기존 사건의 severity보다 높으면(NORMAL→CRITICAL만 해당,
    강등은 없음) `findByIdForUpdate`로 행 잠금 후 `escalate(now)`,
    `reportCaseEventRepository.save(... ESCALATED ...)`를 `REPORT_ATTACHED`
    이벤트와 함께 남긴다. 잠금 없이 read-then-write하면 두 CRITICAL 신고가
    동시에 붙을 때 ESCALATED 이벤트가 중복되거나 유실될 수 있다 — 이
    잠금이 `INV-RPT` 계열 신규 불변식이다.

### 2. SLA

- Flyway 신규 마이그레이션(다음 여유 번호 확인 필요 — `main`이 현재
  `V23`까지이므로 이 브랜치가 최종 push되는 시점에 `V24`가 비어 있는지
  다시 확인하고 번호를 정한다) — `report_case.sla_due_at TIMESTAMPTZ`
  추가. NULL 허용하지 않는다(모든 사건은 여는 순간 SLA를 갖는다).
- `SlaPolicy`(신규, `ReportRateLimitPolicy`/`SafetyReportConfiguration`
  패턴을 따르는 `@ConfigurationProperties` 또는 `@Value` 기반 설정
  클래스) — `queue → Duration` 매핑. 실제 운영 SLA 값은 미정이므로 기본값은
  개발 편의용 임시값이라고 주석에 명시한다(기존 rate-limit 설정 클래스와
  동일한 관례).
- 사건을 열 때 `sla_due_at = now + slaPolicy.of(queue)`로 계산해 저장한다.
  승격(escalate) 시 큐가 바뀌므로 `sla_due_at`도 새 큐 기준으로
  재계산한다(**ASSUMED** — 이슈 본문이 명시하지 않음, 승격되면 더 급해지는
  게 자연스러우므로 재계산 쪽으로 가정. 승인 필요하면 사용자에게 확인).
- 대기열 조회 API가 `sla_due_at < now`인 사건에 `overdue: true`를 계산해
  응답에 포함한다(§ Scope decision 4).

### 3. 자동 전역 숨김

- 세 조건 중 하나라도 만족하면 자동으로 `SafetyCaseResolutionService`와
  같은 방식으로 전역 숨김한다(운영자 조치에 의한 숨김은 이미 `#155`가
  구현한 `resolveCase(ACTIONED)` 경로 그대로 재사용 — 세 번째 조건은 이미
  구현됨, 나머지 둘이 신규):
  1. **서로 다른 신고자 수 임계 도달** — 같은 사건에 달린 `report` 중
     서로 다른 `reporter_id` 개수가 주입된 임계값 이상이면 트리거.
  2. **같은 대상의 `filter_job` 판정이 이미 숨김·수동검토** — `report_case`의
     대상(`answer_id` 등)과 같은 `target_type`+`target_id`로
     `filter_job`/`manual_review_case`(둘 다 `filtering` 스키마, `V10`)를
     조회해 이미 HIDDEN 판정이거나 수동검토 중이면 트리거. 이때
     `report_case.linked_manual_review_case_id`에 찾은
     `manual_review_case.id`를 기록한다(상관관계만, 테이블 통합 없음).
- `AutoSuppressionPolicy`(신규, 같은 설정 패턴) — 신고자 수 임계값을
  주입값으로 받는다.
- 자동 숨김은 신고 접수(`mergeCase`/`REPORT_ATTACHED` 직후) 시점에
  평가한다 — 매 신고마다 재계산하되, 이미 숨김된 사건은 재평가하지
  않는다(멱등).
- `linked_manual_review_case_id` 컬럼도 같은 §2 마이그레이션에 추가한다.
  `filtering.manual_review_case`를 참조하는 FK를 걸지, opaque id로만
  둘지는 스키마 소유 경계 문제라 테스트 계획 단계에서 확정한다
  (**ASSUMED**: FK 없이 opaque id — `report_case`와 `filtering` 스키마
  간 직접 FK가 기존 저장소에 선례가 없다).

### 4. 운영자 판정 API (`/api/v1/operator/report-cases`)

- 신규 패키지 `safety/web`(`SafetyReportService`/`SafetyCaseResolutionService`
  패턴을 따른다).
- 엔드포인트:
  1. `GET /api/v1/operator/report-cases` — `queue` 필터, 페이지네이션,
     `overdue` 플래그 포함 목록.
  2. `POST /api/v1/operator/report-cases/{id}/review` — `startReview()`
     호출(OPEN→UNDER_REVIEW).
  3. `POST /api/v1/operator/report-cases/{id}/decision` — 판정
     (`ACTIONED`/`NO_VIOLATION`), 내부적으로 `SafetyCaseResolutionService.
     resolveCase(...)`를 호출하고 `moderation_review`에 `internal_note`
     포함해 기록한다.
  4. `POST /api/v1/operator/report-cases/{id}/more-info` — `MORE_INFO_REQUIRED`
     비종결 판정(신규 `ReportCase.requestMoreInfo(Instant)` 도메인
     메서드 필요 — `decision`만 세팅, `status`는 그대로, `resolved_at`은
     null 유지. `ck_report_case_resolution` CHECK가 이미 이 조합을
     허용함을 확인했다).
  5. `POST /api/v1/operator/report-cases/{id}/restore` — 이미 숨김된
     콘텐츠의 복원, `#155`가 만든 `Answer.restore(at)` 재사용.
- 두 운영자 동시 판정 방지: `SafetyCaseResolutionService.resolveCase`가
  이미 `findByIdForUpdate` 행 잠금 + 상태 전이 검사를 하므로(**확인
  완료**, `#155`), 신규 코드가 이 보장을 다시 만들 필요는 없다 — 동시성
  테스트만 이 계약을 검증하면 된다. `startReview`/`more-info`/`restore`
  경로도 같은 잠금 패턴을 새로 적용해야 한다(현재는 `resolveCase`에만
  있음).
- 신고자에게 노출되는 어떤 조회에도 `moderation_review`를 조인하지
  않는다(완료 조건 그대로 유지 — 기존 `findMyReports` 등 신고자 경로는
  이 이슈에서 건드리지 않는다).

## Explicit exclusions

- `CRITICAL` 1건으로 즉시 전역 숨김할지 여부(R04, 법무 결정) — 이슈 본문
  명시 제외.
- 운영자 백오피스 화면 — 이슈 본문 명시 제외.
- 허위 신고자 제재 정책 — 이슈 본문 명시 제외.
- SLA 초과 알림의 실제 발송(push/Slack/이메일) — § Scope decision 4,
  `overdue` 플래그 표시까지만.
- `operator_action_audit`(V20) 확장 — § Scope decision 3.
- `SafetyService`의 미사용 `report`/`startReview`/`resolve`/`review`/
  `reviewAndResolve` 메서드 — 어떤 컨트롤러도 호출하지 않는 죽은 코드임을
  확인했다. 제거도 재활용도 하지 않고 그대로 둔다(범위 밖 정리는 별도
  이슈).
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `ReportCase` 도메인 확장(escalate/deescalate/requestMoreInfo), 심각도·큐·SLA·자동숨김 정책 클래스, `SafetyReportService.mergeCase` 확장, 신규 `safety/web` 운영자 API, `SecurityConfiguration` 신규 필터체인, Flyway 마이그레이션, 단위·통합 테스트 | Feature executor | `INV-RPT-007`(재개방 금지) 검증, `#155` `SafetyCaseResolutionService`/`findByIdForUpdate` 계약과의 호환성 리뷰, 동시 판정 방지 동시성 테스트, 신규 보안 필터체인이 기존 `/admin/**`·`/api/**` 체인과 충돌하지 않는지 리뷰 |

## Existing user-owned changes

- `git status --short` 결과 없음(clean). `origin/main`(`bbecf3e`, `#173`
  병합 이후)에서 새로 분기했다.

## Validation

```bash
./gradlew test --tests "com.dnd.qello.safety.*" --console=plain
./gradlew test --tests "com.dnd.qello.notification.*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.*ReportCase*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.*OperatorReview*" --console=plain
./gradlew integrationTest --tests "*Flyway*" --console=plain
./harness test-run --id <TEST-PLAN-ID>
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] `CRITICAL` 하위 사유가 `URGENT` 대기열로 라우팅된다 —
      `ReportCaseSeverityIntegrationTest#newCaseOpensAsCriticalWhenSubReasonIsCsam`(INT-001).
- [x] `NORMAL` 사건에 `CRITICAL` 신고가 붙으면 승격되고 `ESCALATED` 이벤트가
      남는다 — `#escalatesExistingNormalCaseWhenCriticalReportAttaches`(INT-002).
- [x] 두 운영자가 같은 사건을 동시에 판정해도 판정은 한 번만 기록된다
      (PostgreSQL 동시성 테스트) —
      `OperatorReportCaseIntegrationTest#concurrentDecisionResolvesOnce`(INT-017).
- [x] 사건은 재개방되지 않고 재발은 새 사건이 된다(`INV-RPT-007`) — 기존
      `ReportCaseFoundationIntegrationTest#allowsNewCaseForTargetWithResolvedCase`(#154)가
      검증, #156은 이 계약을 변경하지 않았다.
- [x] 임계값·SLA가 전부 주입값이고 코드에 상수로 박히지 않았다 —
      `SafetyReportConfiguration`의 `slaPolicy`/`autoSuppressionPolicy`
      `@Bean`, 둘 다 `@Value` 기반.
- [x] 운영자 응답에만 `internal_note`가 포함되고 신고자 경로에는 어떤
      쿼리에서도 `moderation_review`를 조인하지 않는다 —
      `#decideActionedHidesAnswerAndRecordsInternalNote`(INT-013)가
      운영자 쪽 기록을 확인, 신고자 응답 DTO는 구조적으로 그 필드가 없음을
      코드로 확인(보고서 §4 INT-020 참고).
- [x] 서로 다른 신고자 수 임계 도달 시 자동 전역 숨김된다 —
      `ReportCaseSeverityIntegrationTest#autoSuppressesWhenDistinctReporterThresholdIsReached`(INT-005).
- [x] 같은 대상의 `filter_job`/`manual_review_case`가 이미 숨김·수동검토면
      자동 전역 숨김되고 `linked_manual_review_case_id`가 기록된다 —
      `#autoSuppressesWhenManualReviewCaseIsOpen`(INT-007).
- [x] 대기열 조회 API가 `sla_due_at`을 넘긴 사건을 `overdue: true`로
      표시한다 — `OperatorReportCaseIntegrationTest#queueMarksOverdueCases`(INT-010).
- [x] 실행하지 못한 검증과 남은 위험을 보고서에 기록한다 — 상세는
      `docs/reports/tests/gh-156-TEST-PLAN-GH-156-REPORT-SEVERITY-OPERATOR-REVIEW.md`
      §6·§7 참고. N-way(3+) 동시성 미실측, `linked_manual_review_case_id`
      FK 부재(ASSUMED 결정), SLA 알림 실제 발송 범위 밖.
