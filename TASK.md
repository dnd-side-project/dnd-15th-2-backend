# GitHub Issue #157 Task Contract

> Generated at: `2026-08-21T18:46:10+09:00` (harness task-init) / decisions
> recorded `2026-08-21`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `[D] 신고 시스템 — 법률·안전 검토와 production gate (R04)`
- GitHub Issue: `#157`
- Branch: `feat/gh-157-report-legal-production-gate`
- Base branch: `main`
- 선행 이슈 `#153`~`#156` 전부 병합 확인
  (`origin/main` 최신 커밋 `f26118c`, PR #186으로 `#156` 병합됨).
- 참조 설계 문서: `docs/product/ANSWER_REPORT_DESIGN.md` §4.1, §10, §11.2, §12.
- Test plan: `TEST-PLAN-GH-157-REPORT-LEGAL-PRODUCTION-GATE`
  (`docs/test-plans/gh-157-TEST-PLAN-GH-157-REPORT-LEGAL-PRODUCTION-GATE.md`)
- Test plan approval: `APPROVED` — 사용자가 2026-08-21 "진행"으로 계획을
  승인했다.

## 결정 게이트에 대한 중요한 주의

이 이슈의 완료 조건 중 "법률·안전 담당자의 검토 증거가 이 이슈에 연결됐다"는
AI 에이전트가 대신 만들어낼 수 없다. 아래 결정들은 **저장소 소유자
`tkv00`이 2026-08-21 대화에서 제품/정책 결정권자로서 직접 내린 값**이며,
별도의 공식 법무 검토 절차가 조직에 존재한다면 그 서명은 이 이슈 밖에서
별도로 받아야 한다. 그 전까지 아래에서 신규로 만드는 "즉시 전역 숨김"
동작은 반드시 기능 플래그로 감싸 기본값 OFF로 두고, 운영 활성화는 별도
승인 후 값을 켜는 것으로 한다(§ Scope 6 참고).

## Decisions (사용자 승인, 2026-08-21)

| # | 항목 | 결정 | 근거 |
| --- | --- | --- | --- |
| 1 | CRITICAL 1건 즉시 전역 숨김 (설계 §4.1) | **A안 채택** — 즉시 전역 숨김 + 남용 통제 | 피해 확산 방지 우선. 통제 장치: 계정당 CRITICAL 일일 쿼터(#4) |
| 2 | 판정 노출 수준 (설계 §10) | **3단계 유지** | 이미 `ReportDetailResponse.status`가 `Report.status`(RECEIVED/AUTO_HIDDEN/UNDER_REVIEW/ACTIONED/NO_VIOLATION/MORE_INFO_REQUIRED) 전체를 그대로 노출 중 — **CONFIRMED, 추가 구현 불필요** |
| 3 | 자해·자살 위험 사유 추가 | **추가한다** — `ReportSubReason.SELF_HARM_RISK`, 상위 `ReportReason.ILLEGAL_OR_DANGEROUS`, severity `CRITICAL` | 생명 위험은 CSAM·협박과 동급 긴급도로 판단 |
| 4 | 국가별 신고 의무 분기 | **범위 밖** — 이번 이슈에서 구현 안 함 | 법적 요건 미확정. 별도 이슈로 미룸 |
| 5 | 증거 보존 기간(`purge_after`) | **180일** (스냅샷 `capturedAt + 180일`) | 재신고·이의제기 대응에 충분한 중간값 |
| 6 | 이의제기 경로 (설계 §11.2) | **범위 밖** — 별도 이슈로 미룸 | `AppealCase.filterDecisionId` 확장은 스키마·도메인 변경이 필요한 별도 설계 |
| 7 | 운영자 role 정책 | **현재 단일 `OPERATOR` role로 충분** — CONFIRMED | `#156`이 만든 세션 기반 OPERATOR role이 CRITICAL/URGENT 큐도 처리 |
| 8a | 자동 숨김 임계값(서로 다른 신고자 수) | **3명** (기존 개발 임시값 5명 → 변경) | 운영 확정값 |
| 8b | SLA (URGENT/STANDARD) | **유지** — URGENT 4시간 / STANDARD 72시간 | 기존 개발값이 합리적 |
| 8c | 신고 rate limit | **유지** — 60분당 10건 | 기존 개발값이 합리적 |
| 8d | CRITICAL 일일 신고 쿼터 | **5건/일** (신규 구현) | 설계 §4.1 남용 통제 장치 (a) |
| 9 | `report_content_snapshot` append-only 트리거와 purge 배치의 충돌 | **트리거에 `media_object_keys`만 비우는 예외 경로 추가** | 증거 메타데이터(본문 등)는 영구 불변 유지, 미디어만 정리 가능하게 좁은 예외를 DB 레벨에서 강제 |

트리거 충돌(#9)은 코드 조사 중 발견한 사실이며 이슈 본문에 명시되지 않았던
추가 제약이다 — 사용자에게 별도로 확인받았다.

## Scope

### 1. `ReportSubReason.SELF_HARM_RISK` 추가 (Decision #3)

- `ReportSubReason`에 `SELF_HARM_RISK` 추가. `ReportCaseSeverity.of(...)`의
  `switch`가 exhaustive라 컴파일러가 누락을 잡아준다 — `CSAM, NCII,
  CREDIBLE_THREAT, SELF_HARM_RISK -> CRITICAL`로 확장.
- Flyway `V27`: `ck_report_sub_reason` CHECK에
  `(reason_code = 'ILLEGAL_OR_DANGEROUS' AND sub_reason_code = 'SELF_HARM_RISK')`
  절 추가. `ck_report_reason`은 이미 `ILLEGAL_OR_DANGEROUS`를 포함하므로
  변경 없음.
- `ReportReasonResponse`/`GET /report-reasons` 카탈로그에 신규 하위 사유
  노출 확인(기존 구조를 그대로 따르면 자동 반영되는지 확인 필요).

### 2. 즉시 전역 숨김 — CRITICAL 사건 자동 숨김 (Decision #1)

- `ReportCaseAutoSuppressionEvaluator.evaluate(...)`에 3번째 트리거 조건
  추가: 사건의 최종 severity가 `CRITICAL`이면(신규 오픈이든 승격이든)
  `resolveIfStillOpen`을 호출한다. 기존 두 조건(서로 다른 신고자 수,
  이미 flagged된 manual review case)과 동일하게
  `SafetyCaseResolutionService.resolveCase(caseId, ACTIONED, now)`를
  재사용한다(#155 자원 재사용, 신규 숨김 로직 없음).
- `SafetyReportService.mergeCase(...)`가 반환하는 caseId만으로는 최종
  severity를 알 수 없으므로, `mergeCase`가
  `record CaseMergeResult(long caseId, ReportCaseSeverity resolvedSeverity)`를
  반환하도록 바꾼다(신규 오픈 시 산출된 severity, 병합 시
  `escalateIfMoreSevere` 이후의 실제 severity).
- **기능 플래그**: `qello.safety.report-case.auto-suppress.critical-enabled`
  (`@Value`, 기본값 `false`). 꺼져 있으면 CRITICAL 사건도 URGENT 큐로는
  라우팅되지만(`#156`이 이미 구현) 자동 전역 숨김은 트리거하지 않는다 —
  운영 활성화는 별도 승인 후 값을 `true`로 바꾸는 것으로 한다(§ 결정
  게이트 참고).

### 3. CRITICAL 일일 신고 쿼터 (Decision #8d, 설계 §4.1 남용 통제 (a))

- 신규 `CriticalReportQuotaPolicy`(record, `maxPerDay` — 다른 Policy
  클래스와 동일 패턴)와 `SafetyReportConfiguration`에 `@Bean` 추가
  (`@Value("${qello.safety.report.critical-daily-quota.max-requests:5}")`).
- `SafetyRepository`에 `countCriticalReportsByReporterSince(reporterId,
  since)` 추가 — `countReportsByReporterSince`와 동일 패턴, `sub_reason_code
  IN ('CSAM','NCII','CREDIBLE_THREAT','SELF_HARM_RISK')` 조건 추가.
- `SafetyReportService.submit(...)`에서 `severity == CRITICAL`일 때만
  `enforceRateLimit`과 별도로 `enforceCriticalDailyQuota`를 호출, 초과 시
  신규 오류 코드 `SAF-APP-005 CRITICAL_REPORT_DAILY_QUOTA_EXCEEDED`
  (`429`) 발생. 하루 경계는 UTC 자정이 아니라 `now.minus(Duration.ofDays(1))`
  롤링 윈도우로 계산한다(기존 rate limit과 동일 방식, 자정 경계보다 우회가
  어렵다).

### 4. 증거 보존 기간과 purge 배치 (Decision #5, #9)

- `SafetyReportConfiguration`에 `EvidenceRetentionPolicy`(record,
  `retentionPeriod: Duration`) 추가,
  `@Value("${qello.safety.report.evidence.retention-days:180}")`.
- `ReportContentSnapshot.capture(...)` 호출부(`SafetyReportService.submit`)에서
  `purgeAfter = capturedAt.plus(retentionPolicy.retentionPeriod())`로 계산해
  전달 — 현재 `capture()`는 항상 `purgeAfter=null`이므로 팩토리 메서드
  시그니처에 `purgeAfter` 파라미터 추가 필요.
- Flyway `V27`(§1과 같은 마이그레이션 파일)에 트리거 함수 교체:
  - `report_content_snapshot` 전용 신규 함수
    `enforce_report_snapshot_immutability_except_purge()` 작성.
    DELETE는 항상 거부. UPDATE는 오직 `media_object_keys`만 바뀌고 나머지
    전체 컬럼이 `OLD`와 동일하며, `NEW.media_object_keys = '{}'`이고
    `OLD.legal_hold = FALSE`일 때만 허용한다.
  - `tr_report_content_snapshot_immutable` 트리거를 이 신규 함수로
    교체(`DROP TRIGGER` + `CREATE TRIGGER`). `report_case_event`의
    트리거는 기존 `enforce_report_evidence_immutability()`를 그대로
    쓴다(변경 없음, append-only 완전 유지).
- 신규 `ReportEvidencePurgeSweepWorker`(`safety/sweep` 패키지, `#158`
  `RecipientExpirationSweepWorker` 패턴 그대로 — batch 조회 + 행별 처리,
  **`@Scheduled` 없음, 운영 주기 실행 활성화는 이 이슈 범위 밖**):
  - `ReportContentSnapshotRepository.findPurgeable(now, limit)` —
    `legal_hold = FALSE AND purge_after < :now AND media_object_keys <> '{}'`.
  - `ReportContentSnapshotRepository.purgeMedia(reportId)` — 신규 UPDATE,
    트리거가 허용하는 정확히 그 형태(`media_object_keys = '{}'`, 나머지
    컬럼 미변경)로 실행.
  - S3 등 실제 오브젝트 스토리지에서 미디어 파일 자체를 지우는 것은 이
    이슈 범위 밖 — DB 레코드가 더 이상 그 미디어를 "보존 중"이라고
    표시하지 않게 되는 것까지만 다룬다(§ Explicit exclusions).

### 5. 운영 기본값 반영 (Decision #8a~8c)

- `SafetyReportConfiguration`의 `@Value` 기본값 변경:
  - `qello.safety.report-case.auto-suppress.reporter-threshold` 기본값
    `5` → `3`.
  - SLA·rate limit 기존값 유지, 단 주석의 "실제 운영 수치는 미정" 문구를
    "운영 기본값 확정(#157)"으로 갱신.

### 6. 기능 플래그와 관측 (설계 "운영 반영" 항목)

- § Scope 2의 `critical-enabled` 플래그가 이 항목의 핵심 산출물.
- 신규 관측 지표는 이 이슈에서 새 대시보드·알람 인프라를 만들지 않는다 —
  기존 로깅 패턴(`RecipientExpirationSweepWorker`의 batch 요약 로그
  스타일)을 `ReportEvidencePurgeSweepWorker`에도 적용하는 선까지만 한다.
  Prometheus·CloudWatch 알람 등 실제 인프라 변경은 범위 밖(§ AGENTS.md
  §4의 인프라 게이트 대상이며 이 이슈는 애플리케이션 코드 이슈다).

## Explicit exclusions

- 국가별 신고 의무 분기 구현(Decision #4).
- 신고 기반 숨김의 작성자 이의제기 경로, `AppealCase` 확장(Decision #6).
- 운영자 role 세분화(Decision #7).
- 계정 삭제 요청 시 증거 보존 우선순위 관련 신규 코드 — 계정 삭제 기능
  자체가 저장소에 아직 없고, `report_content_snapshot`이 이미 `author_id`에
  FK 없이 비정규화 사본을 갖고 있어(증거가 이긴다) 설계상 이미 해결됨.
  신규 구현 없음, 결정만 문서화.
- 실제 미디어 오브젝트(S3 등) 삭제 — DB 보존 표시 해제까지만.
- `ReportEvidencePurgeSweepWorker`의 실제 주기 실행 트리거(`@Scheduled`,
  운영 스케줄러 배선) — `#158` 선례와 동일하게 범위 밖.
- 허위 CRITICAL 신고에 대한 신규 `report_case_event` 타입 — 기존
  `REPORT_ATTACHED`/`CASE_OPENED` 이벤트가 이미 사건과 신고를 연결해
  기록하므로 신규 이벤트 타입 불필요(CONFIRMED, 추가 구현 없음).
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- `qello.safety.report-case.auto-suppress.critical-enabled`를 실제
  프로덕션에서 `true`로 켜는 것은 이 이슈의 코드 변경에 포함되지 않는다
  (기본값 `false`로 머지) — 활성화는 별도 승인 절차.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `ReportSubReason` 확장, CRITICAL 자동 숨김 트리거 조건, `CriticalReportQuotaPolicy`, `EvidenceRetentionPolicy`, evidence purge 트리거 예외·`ReportEvidencePurgeSweepWorker`, `SafetyReportConfiguration` 운영값 변경, 신규 Flyway `V27`, 단위·통합 테스트 | Feature executor | 트리거 예외 로직이 `legal_hold` 행을 확실히 보호하는지, `media_object_keys`만 바뀌는 UPDATE 외에는 전부 거부하는지 통합 테스트로 검증. `critical-enabled` 플래그 OFF/ON 양쪽 동작 검증. 기존 `INV-RPT-004`(증거 불변성)가 여전히 성립하는지(본문·해시 등은 절대 안 바뀜) 확인 |

## Existing user-owned changes

- `git status --short` 결과 없음(clean). `origin/main`(`f26118c`, `#186`
  병합 이후)에서 `./harness start`로 새로 분기했다.

## Validation

```bash
./gradlew test --tests "com.dnd.qello.safety.*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.*ReportCase*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.*ReportContentSnapshot*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.*Purge*" --console=plain
./gradlew integrationTest --tests "*Flyway*" --console=plain
./harness test-run --id <TEST-PLAN-ID>
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] `ReportSubReason.SELF_HARM_RISK`가 `ILLEGAL_OR_DANGEROUS`와 조합되어
      CHECK를 통과하고 severity `CRITICAL`로 산출된다 —
      `ReportContentSnapshotImmutabilityIntegrationTest#selfHarmRiskCombinationIsAccepted`(INT-014),
      `#invalidSelfHarmRiskCombinationIsRejected`(INT-015),
      `ReportCaseAndEvidenceTest#selfHarmRiskSubReasonProducesCriticalSeverity`(UNIT-001).
- [x] `critical-enabled` 플래그가 `true`일 때 CRITICAL 사건(신규 오픈 또는
      승격)이 자동으로 전역 숨김·RESOLVED 처리된다. 플래그가 `false`이면
      URGENT 큐 라우팅만 되고 자동 숨김은 트리거되지 않는다 —
      `ReportCaseCriticalAutoSuppressionIntegrationTest`(INT-001~003, `@TestPropertySource`로
      플래그 켬), `ReportCaseSeverityIntegrationTest#newCaseOpensAsCriticalWhenSubReasonIsCsam`(기본값
      꺼짐에서 OPEN·PUBLISHED 유지 확인).
- [x] 계정당 CRITICAL 신고가 일일 쿼터(5건)를 넘으면
      `CRITICAL_REPORT_DAILY_QUOTA_EXCEEDED`로 거부된다 —
      `ReportCaseSeverityIntegrationTest#rejectsCriticalReportBeyondDailyQuota`(GH157-INT-004),
      롤링 24시간 윈도우는 `#rollingWindowExcludesReportsOlderThan24Hours`(GH157-INT-005)가 확인.
- [x] 신규 스냅샷의 `purge_after`가 `capturedAt + 180일`로 저장된다 —
      `ReportCaseAndEvidenceTest#captureStoresGivenPurgeAfter`(UNIT-005, 도메인),
      `SafetyReportService.submit`이 `EvidenceRetentionPolicy` 기반으로 계산해 전달.
- [x] `legal_hold=true`인 스냅샷은 purge 대상에서 제외된다 —
      `ReportEvidencePurgeSweepWorkerIntegrationTest#purgesOnlyEligibleSnapshots`(INT-012),
      `ReportContentSnapshotImmutabilityIntegrationTest#purgeMediaRejectsLegalHoldSnapshot`(INT-007).
- [x] purge 배치가 만료된 스냅샷의 `media_object_keys`만 비우고 나머지
      컬럼(본문, 해시 등)은 절대 바뀌지 않는다 — 트리거가 그 외 모든
      UPDATE·모든 DELETE를 거부함을 통합 테스트로 확인한다 —
      `ReportContentSnapshotImmutabilityIntegrationTest`(INT-006 media만 변경 허용,
      INT-008 본문 변경 거부, INT-009 동시 변경 거부, INT-010 DELETE 거부,
      INT-011 `report_case_event` 트리거 회귀 없음).
- [x] 자동 숨김 임계값이 3명, SLA·rate limit이 기존 값 유지로 설정값에
      반영된다 — `ReportCaseSeverityIntegrationTest#autoSuppressionThresholdDefaultsToThree`(GH157-INT-016),
      `SafetyReportConfiguration` `@Value` 기본값 변경.
- [x] 실행하지 못한 검증과 남은 위험을 보고서에 기록한다 — 아래 "실행 결과"
      절 참고.

## 실행 결과 (2026-08-21)

- `./gradlew test` — 전체 단위 테스트 통과.
- `./gradlew integrationTest` — 전체 통합 테스트 스위트 통과(9분, 기존 회귀
  없음 확인). 개별로도 `ReportCaseSeverityIntegrationTest`,
  `ReportCaseCriticalAutoSuppressionIntegrationTest`,
  `ReportContentSnapshotImmutabilityIntegrationTest`,
  `ReportEvidencePurgeSweepWorkerIntegrationTest`, `*Flyway*`,
  `com.dnd.qello.*Report*` 각각 통과 확인.
- `./harness check` — Secret preflight, JUnit 정책, convention, commit
  형식, workflow, label, Husky 검증 전부 통과.
- `./harness pr-ready --project-tests` — 통과.
- `npm run hooks:validate` — 통과.
- `git diff --check` — whitespace 오류 없음.
- 구현 중 발견해 즉시 고친 결함 2건(둘 다 실제 테스트 실행으로 발견,
  코드 리뷰만으로는 놓쳤을 것):
  1. `ReportSubmission`의 도메인 레벨 reason/subReason 조합 검증 맵에
     `ILLEGAL_OR_DANGEROUS`→`SELF_HARM_RISK`를 추가하지 않아 DB CHECK는
     통과해도 서비스 계층에서 `INVALID_REPORT_SUB_REASON`으로 거부되고
     있었다 — `ReportSubmission.ALLOWED_SUB_REASONS`에 추가로 해결.
  2. 자동 숨김 임계값을 5→3으로 낮추자 기존 `#156` 테스트(`정확히 5명`
     루프)가 3번째 신고에서 이미 답변이 HIDDEN돼 4·5번째 신고가
     `REPORT_TARGET_NOT_FOUND`로 실패했다 — 루프를
     `autoSuppressionPolicy.distinctReporterThreshold()` 기준으로 바꿔
     운영값이 바뀌어도 깨지지 않게 했다.
- 실행하지 못한 검증: 없음(계획한 모든 시나리오를 구현하고 실행했다).
- 남은 위험: `critical-enabled` 실제 프로덕션 활성화는 이 PR에 포함되지
  않는다(기본값 `false`로 머지) — § 결정 게이트 참고. `report_case_event`와
  달리 `report_content_snapshot` 트리거를 이번에 처음으로 부분적으로
  약화시켰다(media_object_keys 예외) — 통합 테스트로 그 예외의 경계를
  직접 검증했지만, 새 트리거 함수 자체의 장기 유지보수 부담(향후 컬럼
  추가 시 트리거 조건도 함께 갱신해야 함)은 남는다.

## `main` rebase 결과 (2026-08-23)

PR #188을 최신 `origin/main` 위로 rebase했다. 충돌과 조정은 다음 세 가지다.

1. `TASK.md` — `main`에는 다른 이슈(#190)의 계약이 올라와 있었다. 이 파일은
   브랜치별 작업 계약이므로 #157 계약을 유지했다.
2. Flyway 버전 충돌 — `main`이 먼저 `V26__split_notification_user_setting.sql`을
   병합했다. 같은 버전이 둘이면 Flyway 기동이 실패하므로 이 이슈의
   마이그레이션을 `V27__add_self_harm_sub_reason_and_evidence_purge_exception.sql`로
   다시 번호를 매겼다(내용 변경 없음). `FlywayMigrationContractTest` 카탈로그와
   `FlywayMigrationIntegrationTest`의 적용 수(26→27)도 함께 맞췄다.
3. `NotificationPreferenceMigrationIntegrationTest` — V24에서 최신까지 실행되는
   마이그레이션 수 단언이 2였는데 `V27`이 늘어 3이 되었다. 이 단언만 갱신했고
   해당 테스트가 지키는 계약 자체는 건드리지 않았다.

`docs/api/openapi.json`은 충돌 없이 병합됐고, 이 브랜치가 더한 변경은
`ReportSubReason` enum의 `SELF_HARM_RISK` 한 건뿐임을 diff로 확인했다.

재검증 결과는 테스트 보고서 §3.1에 기록했다. 전체 단위 테스트와 전체 통합
테스트(656건) 모두 통과했다.

## 남은 위험 / 후속 결정 필요

- `critical-enabled` 플래그를 실제 프로덕션에서 켜는 시점은 이 이슈
  머지와 별개로, 공식 법무·안전 검토(있다면)를 거친 뒤 사람이 결정해야
  한다.
- `ReportEvidencePurgeSweepWorker`를 실제로 주기 실행하려면 별도 스케줄러
  배선(운영 이슈)이 필요하다 — 지금은 코드만 존재하고 자동으로 돌지
  않는다.
- 국가별 신고 의무·이의제기 경로·계정 삭제 시 증거 우선순위·운영자 role
  세분화는 전부 별도 이슈로 미뤄졌다(Decision #4, #6, #7, 계정삭제 항목).
