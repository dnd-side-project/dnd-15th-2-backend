# GitHub Issue #154 Task Contract

> Generated at: `2026-08-18T21:15:46+09:00` (2026-08-18 완료 내용 갱신)
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `신고 접수·사유·차단 진입점`
- GitHub Issue: `#154`
- Branch: `feat/gh-154-report-intake-api`
- Base branch: `main`
- Test plan: `TEST-PLAN-GH-154-REPORT-INTAKE-API`
- Test plan approval: `APPROVED` — 사용자가 2026-08-18 구현을 승인했다.

## Objective

사용자가 더보기 → 신고 → 사유 선택 → (설명) → 접수까지 완료하고 접수증을
받는 REST 경로를 만든다. 내게 온 질문글과 내가 받은 답변 두 화면이 같은
진입점 집합(신고·차단)을 갖는다. Foundation(#153, `main` 병합 완료)이 만든
`ReportCase`/`ReportContentSnapshot`/`ReportCaseEvent`/`Report.attachToCase`
위에, 이 이슈가 처음으로 실제 사건 병합·증거 캡처·중복 판정·REST 계층을
조립한다.

## Scope

- `ReportSubmission`(신규 값 객체) — 사유·하위사유 조합, `OTHER` 사유의
  설명 필수 여부 검증(`SAF-VAL-006`, `SAF-VAL-007`).
- `SafetyReportService`(신규) — 자기 신고 거절(`SAF-DOM-003`), rate limit
  (`SAF-APP-004`), 대상 열람 자격 확인(`SAF-APP-002`), 같은 신고자의 열린
  신고 멱등 반환, 종결된 사건과 내용 동일 시 억제(`DUPLICATE_SUPPRESSED`
  이벤트만 기록), 사건 병합(`ON CONFLICT DO NOTHING` + 재조회, 재시도
  소진 시 `SAF-INFRA-002`), 증거 스냅샷 캡처, `blockAuthor` 옵션의 같은
  트랜잭션 차단 통합.
- `ReportTargetRepository`(신규) — 답변/질문글/사용자 대상의 존재·열람
  자격·현재 콘텐츠를 한 번에 읽는다. `PostAnswerQuerySql`/`FeedScopeSql`의
  열람 자격 조건을 재사용한다.
- `SafetyRepository` 확장 — `findMostRecentClosedReport`,
  `countReportsByReporterSince`, `findReportsByReporter`.
- `ReportCaseRepository` 확장 — `tryOpen`, `findOpenByTarget`.
- `safety/web` 신규 — `SafetyController`/`SafetyApiSpec`과 request/response
  DTO, 엔드포인트 8개(신고 사유 목록, 답변/질문글/사용자 신고, 내 신고
  목록·상세 조회, 사용자 차단·차단 해제).
- `docs/api/openapi.json` 재생성(기존 `OpenApiSpecificationIntegrationTest`
  재실행).
- `docs/error-codes.md` SAF 절 갱신 — 신규 오류 코드 6개
  (`SAF-VAL-006/007`, `SAF-DOM-003/004`, `SAF-APP-002/003/004`,
  `SAF-INFRA-002`; DOM-004/005는 Foundation이 이미 예약).
- 단위 테스트 21개(도메인 검증, 응답 DTO 필드 집합, MockMvc 계약), 통합
  테스트 16개(PostgreSQL 필수 — 동시성·트랜잭션 원자성·열람 자격·중복
  판정 포함).

## Explicit exclusions

- 집계 제외 2계층, 결과 알림 fan-out(`#155`).
- 심각도 산출, 대기열 라우팅, 운영자 판정 API(`#156`).
- rate limit·설명 길이 상한의 실제 운영 수치 — 주입 값으로만 존재(`UNKNOWN`).
- 사용자 신고의 "관계 확인" 기준의 실제 제품 정책 — 직접 송수신 또는
  같은 질문글의 co-recipient 관계로 `ASSUMED`.
- 접수 시점 푸시 알림 — 동기 응답의 접수증으로 대신한다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `ReportSubmission`, `SafetyReportService`, `ReportTargetRepository`, `SafetyRepository`/`ReportCaseRepository` 확장, `safety/web` REST 계층, 단위·통합 테스트 | Feature executor | `INV-RPT-001`·`003`·`005` 검증, Foundation `ReportCase`/`ReportContentSnapshot`/`Report.attachToCase` 계약과의 호환성 리뷰, `#112`(AppealController) REST 관례 일관성 리뷰 |

## Existing user-owned changes

- `main`(`#153` Foundation 병합 직후, `origin/main` 최신 커밋 `4b8bc4e`)에서
  새로 분기했다. 최초 `./harness start --issue 154 --type feat --slug
  report-intake-api --base feat/gh-153-report-case-foundation`로 stacked
  브랜치를 만들었으나, 도중에 `#153`의 PR이 이미 squash merge된 것을
  발견해 `git reset --hard origin/main`으로 다시 `main` 기준으로 정렬했다
  (branch 자체의 커밋 이력은 비어 있었으므로 유실된 작업은 없다).

## Validation

```bash
./gradlew test --tests "com.dnd.qello.safety.*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.ReportIntakeApiIntegrationTest" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest" --console=plain
./harness test-run --id TEST-PLAN-GH-154-REPORT-INTAKE-API
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 접수 응답이 `reportId`·`status`·`receivedAt`·`alreadyReceived`·
      `guidance`만 담고 상대 식별자·내부 판단을 담지 않는다(`INV-RPT-005`)
      — `ReportResponseContractTest`의 레코드 컴포넌트 반사(reflection)
      검사로 검증.
- [x] 같은 신고자의 재신고가 새 행을 만들지 않고 기존 접수증을 반환한다
      — `ReportIntakeApiIntegrationTest#resubmitReturnsExistingOpenReport`,
      `#resubmitIsIdempotentWhenExistingReportAwaitsMoreInfo`(Foundation
      `INV-RPT-002` 회귀 확인 포함).
- [x] 서로 다른 신고자 2명이 동시에 같은 대상을 신고해도 사건이 1개다 —
      `#concurrentReportsFromDifferentReportersMergeIntoOneCase`(2-way
      PostgreSQL 동시성).
- [x] 종결된 사건과 내용이 같은 재신고가 사건을 만들지 않고 이벤트만
      남긴다 — `#resubmitAfterResolutionIsSuppressedWhenContentUnchanged`,
      내용이 바뀌면 새 사건을 만드는 경계는
      `#resubmitAfterResolutionCreatesNewCaseWhenContentChanged`.
- [x] 열람 자격 없는 사용자의 신고가 404다 —
      `#reportingUnviewableAnswerIsNotFound`,
      `#reportingUnrelatedUserIsNotFound`.
- [x] `OTHER` 사유에 설명이 없으면 400이다 —
      `ReportSubmissionTest#rejectsOtherWithoutDetail`.
- [x] `docs/api/openapi.json`이 재생성돼 있다 — 기존
      `OpenApiSpecificationIntegrationTest` 재실행, `git diff`로 새 8개
      엔드포인트 반영 확인.
- [x] 실행하지 못한 검증과 남은 위험을 보고서에 기록한다 — 상세는
      `docs/reports/tests/gh-154-TEST-PLAN-GH-154-REPORT-INTAKE-API.md`
      §1·§6·§7 참고. 사건 병합 재시도 소진 경로(`SAF-INFRA-002`)는 여전히
      재현하지 못했다. 같은 신고자의 동시 중복 요청은 PR #167 코드 리뷰
      후속으로 아래에서 해소했다.

## PR #167 code review follow-up (Major)

CodeRabbit의 PR #167 리뷰 중 Major 등급 4건을 수정했다. Minor/Trivial 등급은
이번 후속 작업 범위에 포함하지 않았다.

- `SafetyReportService.submit`에서 `enforceRateLimit`을 멱등 반환·억제
  경로 뒤(`mergeCase` 직전)로 옮겼다 — 한도를 채운 신고자가 같은 대상을
  재제출(예: 네트워크 재시도)해도 더 이상 429를 받지 않는다.
- `SafetyRepository.acquireReporterSubmissionLock`(`pg_advisory_xact_lock`
  기반, `JdbcSafetyRepository`에 구현)을 `submit` 최상단에서 획득해 같은
  신고자의 제출을 트랜잭션 단위로 직렬화했다. `countReportsByReporterSince`
  → `saveReport`의 경합(rate limit 원자성)과 `findOpenReport` → `saveReport`
  의 경합(동시 재신고 멱등성)을 같은 메커니즘으로 없앴다.
- 새 통합 테스트 3건을 `ReportIntakeApiIntegrationTest`에 추가했다 —
  `concurrentReportsFromSameReporterAreIdempotent`(동시 재신고 1건만 신규
  접수), `concurrentReportsFromSameReporterEnforceRateLimitAtomically`(한도
  직전 동시 요청 중 정확히 1건만 성공), `blockAuthorFailureRollsBackReport
  SnapshotAndCase`(`blockAuthor=true` 경로에서 차단 삽입 실패 시 신고·
  스냅샷·사건 전체 롤백 — `SafetyRepository`를 `@MockitoSpyBean`으로 감싸
  이 테스트의 reporterId/authorId 조합에만 매칭되는 예외를 주입했다. 실제
  기존 차단 행으로 재현하면 열람 자격 양방향 `NOT EXISTS` 조건에 걸려
  대상 조회가 먼저 404가 되므로 이 방식을 썼다).
- 전체 unit(`./gradlew test`)·integration(`./gradlew integrationTest`)
  재실행, 모두 통과.
