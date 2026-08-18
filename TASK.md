# GitHub Issue #153 Task Contract

> Generated at: `2026-08-17T18:31:21+09:00` (2026-08-18 완료 내용 갱신)
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `신고 시스템 — 사건 경계와 증거 정합성 기반`
- GitHub Issue: `#153`
- Branch: `feat/gh-153-report-case-foundation`
- Base branch: `main`
- Test plan: `TEST-PLAN-GH-153-REPORT-CASE-FOUNDATION`
- Test plan approval: `APPROVED` — 사용자가 2026-08-17 구현을 승인했다.

## Objective

사용자 신고 기능 전체(`#153` Foundation, `#154`~`#157` 하위 이슈)가 의존하는
사건(case)·증거 스냅샷·감사 이력의 정합성 기반을 고정한다. 기존 `report`·
`moderation_review`·`user_block`과 `SafetyService`는 있었지만, 신고(제보)와
사건(처리 단위)이 분리돼 있지 않고 신고 시점의 증거가 남지 않았다. 이 이슈는
스키마와 도메인 모델만 추가하며 REST API·집계 제외·알림·운영자 판정은 다루지
않는다(각각 `#154`~`#157`).

## Scope

- `ReportReason`(8종), `ReportSubReason`(`CSAM`/`NCII`/`CREDIBLE_THREAT`),
  `ReportTargetType`, `ReportCaseStatus`, `ReportCaseSeverity`,
  `ReportCaseQueue`, `ReportCaseEventType` 신규 enum.
- `ReportCase`(대상당 열린 사건 하나), `ReportContentSnapshot`(신고 시점
  증거, `content_hash`는 media key 정렬 후 계산), `ReportCaseEvent`
  (append-only 이력) 신규 도메인.
- `Report` 확장 — `caseId`·`subReasonCode` 필드, `attachToCase(long)`,
  `requestMoreInfo(Instant)`(종결 아님) 신규 전이, `resolve()`는
  `ACTIONED`/`NO_VIOLATION`만 허용하도록 축소(`MORE_INFO_REQUIRED` 결함 제거).
- `uq_open_report_{user,post,answer}` 부분 유일 인덱스 술어에
  `MORE_INFO_REQUIRED` 추가(`INV-RPT-002` 결함 수정).
- `report_case`, `report_content_snapshot`, `report_case_event` 신규 테이블과
  `report.case_id`/`sub_reason_code`, `notification.report_id` 확장.
  append-only 트리거(`UPDATE OR DELETE` 전체 차단)로 스냅샷·이력 보호.
- V17 마이그레이션(원래 V16으로 작성했으나 `#110`이 먼저 병합돼 선점 — 실행
  중 발견해 V17로 리네임).
- 신규 오류 코드 `SAF-DOM-004`(`REPORT_ALREADY_LINKED_TO_CASE`),
  `SAF-DOM-005`(`REPORT_CASE_ALREADY_RESOLVED`), `SAF-VAL-008`
  (`INVALID_SNAPSHOT_EDIT_COUNT`).
- `ReportCaseRepository`/`ReportContentSnapshotRepository`/
  `ReportCaseEventRepository`(JDBC, `safety` 패키지 기존 관례를 따름).
- 단위 테스트 21개(UNIT-001~021), 통합 테스트 13개(INT-001~013,
  PostgreSQL 필수 — 동시성·append-only 트리거·FK 부재 확인 포함).
- 설계 문서 `docs/product/ANSWER_REPORT_DESIGN.md`.

## Explicit exclusions

- 신고 접수 서비스·REST API, 사건 병합의 `ON CONFLICT` 동시성 처리(`#154`).
- 집계 제외 2계층, 결과 알림 fan-out(`#155`).
- 심각도 산출 로직, 대기열 라우팅, 운영자 판정 API(`#156`).
- 보존 기간·`purge_after` 기본값, 국가별 분기, `CRITICAL` 즉시 숨김 여부 같은
  정책 결정(`#157`).
- `filtering.ManualReviewCase`/`AppealCase`와의 테이블 통합.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `ReportReason`/`ReportSubReason`/`ReportCase`/`ReportContentSnapshot`/`ReportCaseEvent` 도메인, `Report` 확장, V17 마이그레이션, 신규 저장소, 단위·통합 테스트 | Feature executor | `INV-RPT-001`~`004` 검증, 기존 `SafetyService`·`SafetyNotificationBoundaryTest`·`uq_open_report_*`·`FlywayMigrationContractTest`·`FlywayMigrationIntegrationTest`와의 호환성 리뷰 |

## Existing user-owned changes

- `main`(`#110` 병합 직후)에서 새로 분기했다(`./harness start --issue 153
  --type feat --slug report-case-foundation`). 분기 직전 `docs/product/
  ANSWER_REPORT_DESIGN.md`가 untracked 상태로 있었고(같은 세션에서 설계
  단계에 작성), 브랜치 생성 제약(`ensure_clean_worktree`) 때문에 임시로
  치웠다가 전환 후 복원했다 — 내용 손실 없음.

## Validation

```bash
./gradlew test --tests "com.dnd.qello.safety.*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.ReportCaseFoundationIntegrationTest" --console=plain
./harness test-run --id TEST-PLAN-GH-153-REPORT-CASE-FOUNDATION
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 같은 대상에 열린 사건이 둘 이상 만들어지지 않는다(`INV-RPT-001`) —
      `ReportCaseFoundationIntegrationTest`의 INT-001(2-way 동시성)·INT-002로
      검증.
- [x] 운영자가 추가 정보를 요청해도 같은 신고자의 중복 신고가 다시 열리지
      않는다(`INV-RPT-002`) — `ReportLifecycleTest`(도메인)와 INT-011(DB
      인덱스 술어)로 검증. 수정 전에는 실제로 뚫려 있던 결함이었다.
- [x] 접수된 신고에는 같은 트랜잭션에서 기록된 증거 스냅샷이 정확히 하나
      존재한다(`INV-RPT-003`) — INT-004로 PK 유일성 검증. 스키마가 원자적
      삽입을 지원함만 증명했고, 실제로 그렇게 호출하는 서비스는 `#154` 몫이다.
- [x] 증거 스냅샷과 사건 이력에 대한 `UPDATE`·`DELETE`가 DB에서 거부된다
      (`INV-RPT-004`) — INT-005·INT-006·INT-007로 검증. 기존
      `enforce_question_text_immutability()` 선례는 `DELETE`를 막지 않아
      의도적으로 더 넓은 트리거를 새로 만들었다.
- [x] 작성자가 답변을 삭제한 뒤에도 스냅샷 조회가 가능하다 — INT-008로 검증,
      `answer`/`user_account`/`direction_post`에 대한 FK 부재도 카탈로그
      조회로 함께 확인했다.
- [x] 사유 코드가 8종 밖의 값이면 DB CHECK가 거부한다 — INT-009·INT-010으로
      검증.
- [x] 기존 `SafetyService`·`SafetyNotificationBoundaryTest`·feed 쿼리에
      회귀가 없다 — 전체 unit 518개·integration 407개 통과. `FlywayMigration
      ContractTest`/`FlywayMigrationIntegrationTest`/`AccountPersistence
      IntegrationTest`의 하드코딩된 카탈로그 스냅샷(테이블 수, FK/CHECK 제약
      수)을 V17 변경분에 맞춰 갱신했다.
- [x] 실행하지 못한 검증과 남은 위험을 보고서에 기록한다 — 상세는
      `docs/reports/tests/gh-153-TEST-PLAN-GH-153-REPORT-CASE-FOUNDATION.md`
      §1·§6·§7 참고. 특히 append-only 트리거가 현재 조건 없이 모든 `DELETE`를
      막아 `#157`이 계획한 `purge_after` 만료 스냅샷 정리 배치가 불가능한
      상태다 — `#157` 착수 전 트리거 설계를 보완해야 한다.
