# Test Report: TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL

> Created at: `2026-10-02T17:15:57+09:00`
> GitHub Issue: `#300`
> Branch: `feat/gh-300-feed-media-view-url`
> Commit: `b8ec5a3` (작업 내용은 아직 커밋하지 않은 working tree 기준)

## 1. Executive summary

- Result: `PASS`
- Tested scope: 승인된 계획(Revision 2)의 UNIT-001~009, INT-001~010 전부와 MIGRATE 항목. 전체 단위·통합
  스위트를 다시 실행했다.
- Unverified scope: 실제 AWS 환경(ECS task role 자격 증명, 운영 버킷 권한)에서의 발급과 조회. 통합 테스트는
  LocalStack만 사용했다.
- Release recommendation: 병합 가능. 7절 R-2(URL 안의 객체 경로 해석)는 PR 리뷰에서 확인이 필요하다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | Gradle toolchain Java 21 |
| Spring Boot | 3.5.16 |
| Database | Testcontainers PostGIS(`postgis/postgis:16-3.5-alpine`) |
| Object storage | Testcontainers LocalStack 3.8 (S3) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| Unit (Revision 2 후 `./gradlew test integrationTest`) | PASS | 1,090 (실패·오류·skip 0) | 두 task 합계 7m 26s | `build/test-results/test` |
| Integration (같은 실행) | PASS | 757 (실패·오류·skip 0) | 위와 같음 | `build/test-results/integrationTest` |
| Revision 1 기준 `./harness test-run` | PASS | 단위 1,088 / 통합 755 | 35s / 6m 58s | 보고서 scaffold 생성 시 실행 |
| `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check` | PASS | - | - | Revision 2 후 재실행 |
| READY 필터 변이 확인 | 기대대로 실패 | 7 중 1 실패(INT-004) | 15s | `FeedMediaSql`에서 `AND m.status = 'READY'`를 임시로 빼고 실행한 뒤 원복했다 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `FeedMediaViewResolverTest.returnsEmptyWithoutIssuingWhenNoMedia` | 첨부가 없으면 port 호출 0회 |
| UNIT-002 | PASS | `FeedMediaViewResolverTest.issuesViewUrlPerMediaInInputOrder` | 순서·mediaId·URL·`viewUrlTtl` 확인 |
| UNIT-003 | PASS | `FeedMediaViewResolverTest.propagatesStorageUnavailable` | H1(D2) 결정대로 예외 전파 |
| UNIT-004 | PASS | `InboxApiMockMvcTest.listExposesMediaViewUrlsWithoutStorageKey` | |
| UNIT-005 | PASS | `SentPostApiMockMvcTest.listExposesMediaViewUrlsWithoutStorageKey` | envelope 경로는 `$.data.cards` |
| UNIT-006 | PASS | `PostAnswerApiMockMvcTest.answersExposeMediaViewUrlsWithoutStorageKey` | envelope 경로는 `$.data.answers` |
| UNIT-008 | PASS | `InboxApiMockMvcTest.detailExposesMediaViewUrlsWithoutStorageKey` | Revision 2 |
| UNIT-009 | PASS | `SentPostApiMockMvcTest.detailExposesMediaViewUrlsWithoutStorageKey` | Revision 2 |
| UNIT-007 | PASS | `FeedPersistenceBoundaryTest.cardViewsAndResponsesDoNotCarryStorageLocation` | 소스 문자열 대신 record component 반사로 검사했다 |
| INT-001 | PASS | `FeedMediaViewUrlIntegrationTest.inboxListingExposesMediaViewUrl` | |
| INT-002 | PASS | `FeedMediaViewUrlIntegrationTest.sentPostListingExposesMediaViewUrl` | |
| INT-003 | PASS | `FeedMediaViewUrlIntegrationTest.answerListingExposesMediaViewUrl` | |
| INT-004 | PASS | `FeedMediaViewUrlIntegrationTest.nonReadyMediaIsExcludedFromAllListings` | 변이 확인에서 필터 제거 시 실패함을 확인했다 |
| INT-005 | PASS | `FeedMediaViewUrlIntegrationTest.mediaFollowsDisplayOrder` | |
| INT-006 | PASS | `FeedMediaViewUrlIntegrationTest.issuedUrlFetchesUploadedObject` | LocalStack에서 200과 동일 바이트 |
| INT-007 | PASS | `FeedMediaViewUrlIntegrationTest.listingsWithoutMediaReturnEmptyArray` | |
| INT-008 | PASS | `OpenApiSpecificationIntegrationTest.documentsFeedListingMediaContract` | `docs/api/openapi.json` 재생성. Revision 2에서 상세 `card` schema 2개까지 확장 |
| INT-009 | PASS | `FeedMediaViewUrlIntegrationTest.inboxDetailExposesMediaViewUrl` | Revision 2. DELETED 전환 후 빈 배열 포함 |
| INT-010 | PASS | `FeedMediaViewUrlIntegrationTest.sentPostDetailExposesMediaViewUrl` | Revision 2. DELETED 전환 후 빈 배열 포함 |
| MIGRATE | PASS | `InboxQueryIntegrationTest`, 세 MockMvc 테스트의 카드 생성자 | `FeedInteractionApplicationServiceTest`는 `List.of()`가 새 타입으로 추론돼 수정이 필요 없었다 |

## 5. Failures and diagnostics

- 최종 실행에서 실패는 없다.
- 작성 중 `InboxQueryIntegrationTest`가 `mediaIds()` 참조로 컴파일되지 않았고, 계획의 MIGRATE 항목대로
  `media()`로 바꿨다.
- 비노출 단언 최초 초안은 "응답 본문 어디에도 storage key가 없다"였는데, presigned URL은 서명 대상인 객체
  경로를 값 안에 담으므로 성립하지 않는다. 단언을 "`url` 값을 제외한 필드와 값에 storage key와 버킷 이름이
  없고, `storageKey`·`bucket` 필드가 없다"로 바꿨다(7절 R-2).
- 첫 `./harness pr-ready --project-tests`가 JUnit 정책 검사에서 실패했다. `FeedPersistenceBoundaryTest`의 헤더가
  import 아래 class Javadoc에 있었는데, import 추가와 `spotlessApply` 줄바꿈으로 `Source scenario` 식별자가
  검사 범위(첫 30줄) 밖으로 밀렸다. 헤더를 다른 테스트처럼 파일 맨 위로 옮긴 뒤 `./harness check`,
  `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check`가 모두 통과했다.

## 6. Potential issues

### Application code

- 상세 응답도 바뀐다. `InboxDetailResponse.card`와 `SentPostDetailResponse.card`가 목록 카드 타입을 재사용하므로
  `GET /inbox/{postRecipientId}`, `GET /posts/{postId}`도 `mediaIds` 대신 `media`를 내보낸다. 최초 보고에서 "상세는
  media를 내보내지 않아 발급이 낭비된다"고 적은 것은 틀린 분석이었고, 사용자 결정(TASK.md D4)으로 상세 2개를
  범위에 넣고 UNIT-008·009, INT-009·010으로 검증했다.
- 상세 조회도 발급 실패 시 503이 된다. 수신함 상세는 OPENED 전이와 같은 트랜잭션이라 503이면 전이도 롤백된다
  (부분 반영 없음).
- 저장 위치(storage key)는 `feed.repository.jdbc` 안에서만 다루고, 카드 view에는 `MediaView`(mediaId, URL,
  만료 시각)만 들어간다. jdbc repository는 feed가 소유한 `FeedMediaViewIssuer` 인터페이스에만 의존하며
  구현체는 `feed.service.FeedMediaViewResolver`다.

### Infrastructure and resource limits

- 운영에서 발급한 URL이 동작하려면 앱 runtime role에 미디어 객체 `s3:GetObject` 권한이 필요하다. 프로필 이미지가
  같은 권한에 이미 의존하지만, 이 작업에서 실제 AWS로 검증하지는 않았다.
- presigned URL을 임시 자격 증명으로 서명하면 URL 수명은 `view-url-ttl`(5분)과 그 자격 증명의 남은 수명 중
  짧은 쪽이 된다. SDK가 만료 전에 갱신하므로 위험은 낮다.

### Database and migrations

- 스키마 변경은 없다. 카드마다 `media_attachment`·`media_asset` 상관 서브쿼리가 1개에서 2개(id 배열, key 배열)로
  늘었다. 두 배열은 `FeedMediaSql` 한 곳에서 같은 필터·정렬로 만들고, 길이가 다르면 매핑에서 예외를 던진다.

### Concurrency and idempotency

- 조회 전용 변경이라 새 경쟁 조건은 없다. 같은 목록을 다시 조회하면 URL 문자열은 바뀌고 mediaId와 순서는 같다.

### Transactions and event ordering

- presign은 서명을 로컬에서 계산하고 자격 증명은 캐시된다. 기존 읽기 트랜잭션(수신함 REPEATABLE_READ 포함)
  안에서 호출되지만 트랜잭션 길이에 의미 있는 영향은 없다.

### External APIs

- 발급 실패는 `STORAGE_UNAVAILABLE`(503)로 목록 전체를 실패시킨다(TASK.md D2). 실제 SDK 실패는 통합
  테스트로 재현하지 않았고 UNIT-003으로만 확인했다.
- 객체가 없거나(404), 권한이 없거나 URL이 만료된 경우(403)는 클라이언트가 객체를 받을 때 드러난다.

### Failure recovery and reconciliation

- 서버 상태를 바꾸지 않는 변경이라 복구할 데이터가 없다. 클라이언트는 URL 만료 시 목록을 다시 조회한다
  (OpenAPI `FeedMedia.url` 설명에 기록).

## 7. Regression and residual risk

- R-1. 해소됨. 상세 응답 변경은 범위에 포함됐다(TASK.md D4, test plan Revision 2).
- R-2. Issue 완료 조건 "응답 JSON에 storage key와 버킷 이름이 나오지 않는다"는 presigned URL의 특성상 `url`
  값 안의 객체 경로까지 포함하면 성립할 수 없다. 테스트는 "별도 필드와 `url` 밖의 값에 없다"로 검증했다.
  프로필 이미지 API와 같은 해석이다.
- R-3. `media_asset.moderation_status` 필터는 범위 밖이다. 이미지 검수 기능이 없어 모든 자산이 `PENDING`이며,
  검수 기능을 후속 작업으로 구현할 때 추가한다(TASK.md D3).
- breaking change: 세 목록 응답과 두 상세 응답에서 `mediaIds`가 사라졌다. 클라이언트가 함께 배포돼야 한다.
- 포맷: `spotlessApply` ratchet 규칙으로 수정한 Java 파일 전체가 다시 포맷돼, 일부 diff는 주석 줄바꿈과
  text block 들여쓰기 변경이다. 공백 무시 diff(`git diff -w`)에서는 기능 변경만 남는다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-300-TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL.md`
- CI run: 미실행(PR 생성 전)
- Related ADR: 없음
- PR: 미생성

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 (R-3 이미지 검수 기능은 후속 작업으로 등록 필요)
- [ ] 실행 결과와 PR 설명이 일치함 (PR 생성 시 확인)
