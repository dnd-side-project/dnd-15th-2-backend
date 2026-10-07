# GitHub Issue #323 Task Contract

> Generated at: `2026-10-07T18:23:36+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를 따른다.

## Work gate

- Title: `내게 온 질문 목록의 카테고리 필터 제거`
- GitHub Issue: `#323`
- Task ID: `GH-323-INBOX-CATEGORY-REMOVAL`
- Branch: `feat/gh-323-inbox-category-removal`
- Base branch: `main`
- Source: GitHub Project draft `PVTI_lADOBD3v1M4BfKwozg_GM5Q`를 Repository Issue로 전환
- Test plan: `docs/test-plans/gh-323-TEST-PLAN-GH-323-INBOX.md`
- Design ID: 해당 없음
- Implementation gate: 테스트 계획 사람 승인 완료; 승인된 범위의 구현·테스트 작성·실행 허용
- Approval evidence: 2026-10-07 현재 부모 세션의 사용자 메시지 “테스트 계획 승인할게” (기록 시각: `2026-10-07T19:33:51+09:00`)
- Execution status: `PASS` — 승인 범위 구현·필수 검사·독립 검증 완료. 검증 시점의 변경은 미커밋 상태였으며 사람의 GitHub PR 승인을 대체하지 않는다.
- Test report: `docs/reports/tests/gh-323-TEST-REPORT-GH-323-INBOX.md`
- ClickUp task: `z8rzdfwkfd` (사용자 제공; 링크는 PR 본문에 기록)
- Commit/PR execution approval: 커밋 3개·PR 전체 초안·Java 구현 커밋 한 개의 Hook 밖 동일 검사 및 `--no-verify` 예외를 제시한 뒤, 2026-10-07 현재 부모 세션 사용자가 “PR 올려줘”로 진행을 지시했다. Ready for review, 리뷰어 `@tkv00`, Project In Progress 유지.
- Hook exception scope: Java 구현 커밋 한 개의 `pre-commit`·`commit-msg`만 해당한다. linked worktree의 Git 환경이 임시 Git 저장소 테스트에 전파될 위험 때문에 같은 검사들을 Git Hook 밖에서 실행하고 결과를 보고서와 PR에 기록한다. 문서 커밋과 pre-push는 정상 Hook을 실행한다.

## Objective

- 내게 온 질문을 한 번 조회해 프론트가 카드 status로 미답변·답변 완료 화면을 구분하게 한다.

## Scope

- `GET /api/v1/direction/inbox`의 category 파라미터 제거.
- 미답변(AVAILABLE, DISCOVERED, OPENED, SKIP_PENDING)과 ANSWERED 합집합 조회.
- 본인 소유권, 차단·신고·삭제·공개 상태 등 기존 가시성과 만료 전 조건 유지.
- 페이지네이션 없이 매칭 시각·항목 ID 내림차순으로 조회 가능한 항목 전체 반환.
- chips는 방향 필터와 무관하게 전체 미답변 항목 기준으로 집계.
- 기존 선택 directionSegmentKey 파라미터 유지. 프론트의 전체 상태 분류용 호출에서는 생략.
- 카드 status와 응답 구조 유지, API 설명·생성 OpenAPI 및 관련 테스트 갱신.

## Design and allowed files

내부의 UNANSWERED/ANSWERED 조회 계약은 유지하고 InboxCategory.ALL을 추가한다.
HTTP 목록은 ALL을 고정 전달한다. ALL은 두 카테고리의 합집합을 조회하고 칩만 UNANSWERED로 집계한다.
기존 REPEATABLE_READ 경계와 조회 시각을 유지한다. 선택 방향 필터는 기존처럼 반환 카드에 적용한다.

Production:
- `src/main/java/com/dnd/qello/feed/web/InboxApiSpec.java`
- `src/main/java/com/dnd/qello/feed/web/InboxController.java`
- `src/main/java/com/dnd/qello/feed/view/InboxCategory.java`
- `src/main/java/com/dnd/qello/feed/service/InboxQueryService.java`
- `src/main/java/com/dnd/qello/feed/repository/jdbc/JdbcInboxQueryRepository.java`
- `src/main/java/com/dnd/qello/feed/web/response/InboxListingResponse.java`

Tests:
- `src/test/java/com/dnd/qello/feed/web/InboxApiMockMvcTest.java`
- `src/test/java/com/dnd/qello/feed/web/InboxWebContractTest.java`
- `src/integrationTest/java/com/dnd/qello/InboxQueryIntegrationTest.java`
- `src/integrationTest/java/com/dnd/qello/InboxDirectionChipIntegrationTest.java`
- `src/integrationTest/java/com/dnd/qello/InboxListIsolationIntegrationTest.java`

Documentation:
- `TASK.md`
- `docs/test-plans/gh-323-TEST-PLAN-GH-323-INBOX.md`
- `docs/reports/tests/gh-323-TEST-REPORT-GH-323-INBOX.md`
- `docs/api/openapi.json` (생성 테스트만 사용; 직접 편집 금지)

## Explicit exclusions

- 내가 보낸 질문 API 및 페이지네이션.
- 수신 슬롯 제한, 답변 공개, 만료·넘김 상태 전이, 상세 열람 정책.
- directionSegmentKey 제거, 새 응답 필드, DB 스키마·마이그레이션.
- 전역 formatter 또는 범위 밖 코드 정리.
- 인프라 apply, 배포, 프로덕션 변경.
- 비밀정보 및 실제 운영 식별자 기록.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 요구사항·Issue·TASK·테스트 계획 | 현재 부모 오케스트레이터 | 사람의 테스트 계획 승인 |
| 위 production/test/생성 OpenAPI·테스트 보고서 | inbox-executor (계획 승인 후 호출) | 독립 검증 |
| 실제 diff·필수 검사·범위 확인 | inbox-verifier (구현 후 호출) | 사람의 최종 리뷰 |

계획 준비 및 승인 전에는 자식 에이전트를 생성하지 않았다. 승인 후 위 실행·독립 검증 순서로 호출한다.
구현자는 다른 변경을 되돌리지 않으며 허용 파일만 수정한다.

## Existing user-owned changes

원래 작업 디렉터리에서 다음 미추적 항목을 확인하고 보존했다.
- `.agents/skills/harness-connect/`
- `.harness-delta-managed/`

이 파일들을 이동·삭제·stash하지 않고, ignored `.worktrees/gh-323-inbox-category`에 독립 worktree를 만들었다.
작업 브랜치 시작 시 해당 worktree에는 변경이 없었다.
origin/main, 로컬 main, 시작 HEAD는 동일한 commit이었다.

## Validation

테스트 계획 승인 이후 Java 21 및 Testcontainers용 Docker 환경에서 실행한다.

```bash
./gradlew test
./gradlew integrationTest
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

OpenAPI는 `OpenApiSpecificationIntegrationTest`로 생성한다.
실행하지 못한 검증은 BLOCKED로 보고하며 성공으로 간주하지 않는다.

### Verified outcome

- 기준 수신함 테스트 통과 후 HTTP 계약 및 혼합 상태 조회의 예상 실패를 확인하고 구현했다.
- 집중 수신함 테스트: 44개, 실패·오류·스킵 0.
- 전체 단위: 1,225개, 전체 통합: 794개, 실패·오류·스킵 0.
- `./harness test-run --id TEST-REPORT-GH-323-INBOX`, `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check` 통과.
- OpenAPI는 기존 생성 테스트로 갱신했다. 수신함 목록 외 API 경로와 보낸 질문의 참조 스키마는 불변이다.
- 구현자 `inbox_executor`와 독립 검증자 `inbox_verifier`의 증거를 분리했다. 독립 검증의 명세·코드 품질·필수 증거 판정은 모두 PASS다.
- 최종 실패·차단 검사 및 미실행 필수 검사는 없다. 초기 예상 RED는 최종 검사 실패와 구분해 보고서에 기록했다.

## Completion criteria

- Issue #323의 완료 조건을 충족한다.
- category 없이 혼합 상태 목록이 반환되고 status가 그대로 노출된다.
- 만료 경계와 기존 보안·가시성 범위가 유지된다.
- 칩은 미답변만 집계하며 조회 가능한 합계가 5개를 넘어도 잘리지 않는다.
- 기존 방향 필터와 목록 snapshot 보장을 유지한다.
- 내가 보낸 질문 관련 production·응답·OpenAPI 계약이 변경되지 않는다.
- 승인된 테스트와 필수 검사에 실패·차단 항목이 없다.

## Decisions, risks and rollback

- CONFIRMED: 2026-10-07 사용자 요청으로 내게 온 질문만 변경한다.
- CONFIRMED: 프론트가 status로 분류하며 답변 완료 화면에는 방향 필터가 없다.
- CONFIRMED: 사용자가 Project draft를 통한 Issue 생성 및 작업 시작을 승인했다.
- ASSUMED: Project Sprint 미지정, Priority P1, Status In Progress, Work type Feature.
- CONFIRMED: 2026-10-07 현재 부모 세션에서 사용자가 테스트 계획을 승인했다. 승인 근거와 기록 시각은 Work gate 및 테스트 계획의 Human approval에 기록한다.
- 위험: 기본 응답에 ANSWERED가 추가되므로 프론트의 status 분류 적용이 필요하다.
- 위험: 답변 완료 후 슬롯이 반환되므로 전체 항목은 5개를 넘을 수 있다.
- 복구: 변경 commit revert로 기존 category 요청·집계 복구. DB 복구 작업 없음.
