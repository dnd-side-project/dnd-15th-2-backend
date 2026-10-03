# Test Plan: TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL

> Created at: `2026-10-02T16:23:08+09:00`
> GitHub Issue: `#300`
> Status: Approved (Revision 2, 2026-10-02)

## 1. Objective

받은 질문, 보낸 질문, 답변 목록 응답이 `mediaIds` 대신 `media: [{ mediaId, url, expiresAt }]`를
내려주고, 클라이언트가 그 URL로 private 버킷의 이미지를 바로 받을 수 있는지 검증한다.

실패 시 위험은 다음과 같다.

- 클라이언트가 첨부 이미지를 계속 표시하지 못한다.
- storage key나 버킷 이름이 응답으로 새어 나간다.
- READY가 아닌(삭제·거부된) 자산의 URL이 발급된다.
- 첨부가 없는 카드에서도 presign을 호출해, 자격 증명이 없는 기존 feed 통합 테스트
  컨텍스트 전체가 `STORAGE_UNAVAILABLE`로 깨진다(8절 참고).

## 2. Scope

### Included

- feed 서비스 계층의 미디어 조회 URL 변환(이하 resolver). 입력은 `display_order` 순서의
  (mediaId, storageKey) 목록이고 출력은 (mediaId, url, expiresAt) 목록이다.
- `InboxQuerySql`, `SentPostQuerySql`, `PostAnswerQuerySql`의 READY 필터, `display_order` 정렬,
  `storage_key` 조회
- `InboxListingResponse`, `SentPostListingResponse`, `AnswerListingResponse`의 JSON 구조
- 목록 카드 타입을 재사용하는 `InboxDetailResponse.card`, `SentPostDetailResponse.card`의 JSON 구조(Revision 2)
- `docs/api/openapi.json`의 세 목록 item schema와 두 상세 `card` schema
- `mediaIds`를 참조하는 기존 feed 테스트의 단언 이전

### Excluded

- 질문글·답변 작성 요청의 `mediaIds` 입력 필드(`SubmitDirectionPostRequest`, `SubmitAnswerRequest`)와
  그 테스트
- `view-url-ttl` 값 변경, 만료 URL 재발급 엔드포인트, CloudFront
- `media_asset.moderation_status` 기반 필터(5절 위험 R6, 11절 결정 H2). 이미지 검수 기능이 아직 없어
  후속 작업으로 구현해 추가한다.
- 목록 조회 자격 규칙 자체. 기존 `InboxListIsolationIntegrationTest`,
  `PostAnswerQueryIntegrationTest`, `SentPostQueryIntegrationTest`가 소유한다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #300 | 세 목록 API 응답에 `mediaIds`가 없고 `media[].mediaId`, `media[].url`, `media[].expiresAt`가 있다. |
| GitHub Issue #300 (2026-10-02 정정) | 받은 질문·보낸 질문 상세 응답의 `card`에도 `mediaIds`가 없고 `media`가 있다. |
| GitHub Issue #300 | READY가 아닌 자산은 `media`에 포함되지 않는다. |
| GitHub Issue #300 | 응답 JSON에 storage key와 버킷 이름이 나오지 않는다. |
| GitHub Issue #300 | `media` 순서가 `media_attachment.display_order`와 같다. |
| `application.yml` `qello.media.view-url-ttl` | 조회 URL 수명은 기존 값(PT5M)을 쓴다. |
| `ObjectStoragePort.issueGetUrl` | 발급은 객체 존재를 확인하지 않는다. 실패는 `STORAGE_UNAVAILABLE`(503)로 변환된다. |
| V1 스키마 `ct_media_status_preserves_content` | 본문이 있는 질문글·답변에 첨부된 자산은 DELETED로 전이할 수 있다. 본문이 없으면 전이가 거부된다. |
| `ProfileImageResolver` | 같은 port와 TTL로 presigned GET을 발급하는 선례. 실패 시 예외를 그대로 전파한다. |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| R1. 첨부가 없는 카드에서도 port를 호출한다 | 높음. `application-test.yml`의 placeholder 설정에는 자격 증명이 없어 presign이 실패하고, Postgis 전용 feed 통합 테스트가 전부 503이 된다 | 중간 | P0 | UNIT-001 |
| R2. storage key·버킷 이름이 응답에 노출된다 | 높음. 객체 경로 구조 노출 | 중간 | P0 | UNIT-004~007, INT-001~003, INT-008 |
| R3. READY가 아닌 자산의 URL이 발급된다 | 중간. 삭제한 이미지가 계속 보인다 | 중간 | P0 | INT-004 |
| R4. `media` 순서가 `display_order`와 다르다 | 낮음. 현재 질문글은 1장 고정 | 낮음 | P1 | UNIT-002, INT-005 |
| R5. 발급 URL이 다른 객체를 가리키거나 서명이 무효다 | 높음. 이미지가 안 보인다 | 낮음 | P1 | INT-006 |
| R6. moderation 미통과 이미지(`moderation_status` ≠ PASSED)의 URL도 발급된다 | 중간. 이미지 판정기가 없어 모든 자산이 `PENDING`이고 Java 코드는 이 컬럼을 읽거나 쓰지 않는다. 지금 PASSED 필터를 걸면 모든 첨부 이미지가 사라진다 | 확정(검수 기능 없음) | 범위 밖 | H2 결정. 검수 기능 구현 시 추가 |
| R7. presign이 실패한다(자격 증명 조회 실패). 실패는 개별 이미지가 아니라 그 시점의 발급 전체에 걸친다 | 중간. 미디어만 빼면 모든 카드에서 이미지가 이유 없이 사라진다 | 낮음 | P1 | UNIT-003, H1 결정(목록 전체 503) |
| R9. 상세 응답도 목록 카드 타입을 재사용해 함께 바뀐다. Revision 1은 이를 범위 밖으로 잘못 분류했다 | 중간. 상세 화면의 이미지가 검증 없이 바뀐다 | 확정 | P0 | UNIT-008~009, INT-009~010, INT-008 확장 |
| R8. `mediaIds` 제거로 기존 테스트가 컴파일되지 않는다 | 낮음. 이전 대상이 정해져 있다 | 높음 | P0 | 9절 MIGRATE 항목 |

## 5. Unit scenarios

resolver 클래스 이름은 구현 단계에서 정한다. 이 계획에서는 `FeedMediaViewResolver`(ASSUMED)로
부른다. 테스트는 `ObjectStoragePort`의 테스트 대역만 쓰고 Spring 컨텍스트를 띄우지 않는다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-UNIT-001 | 첨부 참조가 빈 목록이다 | resolver로 변환한다 | 빈 목록을 반환하고 `ObjectStoragePort.issueGetUrl` 호출이 0회다 | P0 | Executor A |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-UNIT-002 | (mediaId 11, key-a), (mediaId 12, key-b) 순서의 첨부 참조와 key별로 다른 URL을 돌려주는 port 대역이 있다 | resolver로 변환한다 | 결과가 [11, 12] 순서이고, 각 항목의 url·expiresAt이 해당 key로 발급된 값이며, 모든 호출의 ttl이 `MediaStorageProperties.viewUrlTtl()`과 같다 | P0 | Executor A |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-UNIT-003 | port 대역이 `STORAGE_UNAVAILABLE` `AnswerException`을 던진다 | resolver로 변환한다 | 같은 오류 코드의 예외가 전파되고 일부 항목만 담긴 결과를 반환하지 않는다(H1 승인안 기준) | P1 | Executor A |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-UNIT-004 | `InboxApplicationService.list`가 media 1건이 있는 카드를 반환하도록 mock했다 | `GET /api/v1/direction/inbox`(MockMvc) | `data.cards[0].media[0]`에 `mediaId`, `url`, `expiresAt`이 있고 `data.cards[0].mediaIds`가 없으며 응답 본문에 `storageKey` 필드가 없다 | P0 | Executor A |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-UNIT-005 | `FeedInteractionApplicationService.listSentPosts`가 media 1건이 있는 카드를 반환하도록 mock했다 | `GET /api/v1/direction/posts`(MockMvc) | UNIT-004와 같은 단언 | P0 | Executor A |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-UNIT-006 | `FeedInteractionApplicationService.answers`가 media 1건이 있는 카드를 반환하도록 mock했다 | `GET /api/v1/direction/posts/{postId}/answers`(MockMvc) | UNIT-004와 같은 단언 | P0 | Executor A |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-UNIT-008 | `InboxApplicationService.detail`이 media 1건이 있는 카드의 상세를 반환하도록 mock했다 | `GET /api/v1/direction/inbox/{postRecipientId}`(MockMvc) | `data.card.media[0]`에 `mediaId`, `url`, `expiresAt`이 있고 `data.card.mediaIds`와 `storageKey` 필드가 없다 | P0 | Executor A |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-UNIT-009 | `FeedInteractionApplicationService.sentPostDetail`이 media 1건이 있는 카드의 상세를 반환하도록 mock했다 | `GET /api/v1/direction/posts/{postId}`(MockMvc) | UNIT-008과 같은 단언 | P0 | Executor A |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-UNIT-007 | `feed/view`와 `feed/web/response` 소스 | 소스를 읽는다 | 응답 record와 media record의 component에 `storageKey`, `bucket`, `mediaIds`가 없다 | P1 | Executor A |

UNIT-004~006의 JSON 경로는 `InboxApiMockMvcTest`가 쓰는 `$.data.cards[...]`를 기준으로 했다.
보낸 질문·답변 목록의 실제 envelope 경로가 다르면 그 경로로 맞추고 보고서에 기록한다.

## 6. Integration scenarios

새 클래스 `FeedMediaViewUrlIntegrationTest`는 `LocalStackContainerIntegrationTestSupport`를
상속한다. presign에 LocalStack 자격 증명과 endpoint가 필요하고, INT-006은 실제 객체를 받아야
하기 때문이다. 각 시나리오는 HTTP 계층(MockMvc)으로 호출한다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-INT-001 | InboxController → InboxQuerySql → resolver → S3Presigner(LocalStack) | 발신자 질문글(본문 있음)에 READY 자산 1건 첨부, 수신자에게 매칭 | 수신자로 `GET /inbox` | `media[0].mediaId`가 첨부 id, `url`이 비어 있지 않음, `expiresAt`이 요청 시각 + `view-url-ttl` 범위 안, `mediaIds` 필드 없음, 응답 본문 문자열에 storage key와 `TEST_BUCKET` 이름 없음 | `@BeforeEach`에서 fixture 행 삭제 |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-INT-002 | SentPostController → SentPostQuerySql → resolver | INT-001과 같은 질문글 | 발신자로 `GET /posts` | INT-001과 같은 단언 | 같음 |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-INT-003 | PostAnswerController → PostAnswerQuerySql → resolver | 수신자가 본문 있는 답변에 READY 자산 1건을 첨부해 PUBLISHED | 발신자로 `GET /posts/{postId}/answers` | INT-001과 같은 단언(답변 미디어 기준) | 같음 |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-INT-004 | 세 SQL의 READY 필터 | INT-001~003 fixture에서 첨부 자산의 `status`를 `DELETED`, `deleted_at`을 설정(본문이 있어 `ct_media_status_preserves_content` 통과) | 세 목록을 각각 조회 | 해당 카드의 `media`가 빈 배열이다. 카드 자체는 그대로 나온다 | 같음 |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-INT-005 | SentPostQuerySql 정렬 | 한 질문글에 READY 자산 2건을 `display_order` 1, 0 순서로 JDBC 삽입(API 상한 1장은 서비스 정책이고 스키마는 여러 행을 허용한다) | 발신자로 `GET /posts` | `media`의 mediaId 순서가 `display_order` 0, 1 순서다 | 같음 |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-INT-006 | 발급 URL → LocalStack S3 | READY 자산의 storage key로 PNG 바이트를 LocalStack에 put | `GET /posts`로 받은 `media[0].url`에 HTTP GET | 200이고 본문 바이트가 put한 바이트와 같다 | 같음. 버킷 객체는 storage key가 매번 고유해 정리하지 않는다 |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-INT-007 | 세 목록의 빈 첨부 | 첨부 없는 본문 질문글과 답변 | 세 목록을 각각 조회 | `media`가 `null`이 아니라 빈 배열 `[]`이다 | 같음 |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-INT-009 | InboxController 상세 → InboxQuerySql → resolver | INT-001과 같은 질문글, READY 첨부 1건 | 수신자로 `GET /inbox/{postRecipientId}` | `card`에 INT-001과 같은 단언. 이어서 첨부를 DELETED로 바꾸고 다시 조회하면 `card.media`가 빈 배열이다 | 같음 |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-INT-010 | SentPostController 상세 → SentPostQuerySql → resolver | 같은 질문글 | 발신자로 `GET /posts/{postId}` | INT-009와 같은 단언 | 같음 |
| TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-INT-008 | springdoc → `OpenApiSpecificationIntegrationTest` | 없음 | `/v3/api-docs` 조회 | 세 목록 item schema와 두 상세 `card` schema(Revision 2)에 `media`가 있고 그 item 필드가 정확히 `mediaId`, `url`, `expiresAt`이며, `mediaIds`와 `storageKey`가 없다 | 없음 |

## 7. Cross-cutting scenarios

### Database and transactions

- `InboxQueryService.list`는 REPEATABLE_READ 읽기 전용 트랜잭션이다. presign은 SDK 안에서 서명만
  계산해 트랜잭션 길이에 영향이 없다. 별도 시나리오를 만들지 않는다.
- READY 필터는 INT-004가 세 SQL 모두에서 검증한다. DELETED 전이는 본문이 있을 때만 허용되므로
  fixture에 반드시 본문을 넣는다.

### Concurrency and idempotency

- 조회 전용 변경이다. 같은 목록을 두 번 조회하면 URL 문자열은 달라도 mediaId 집합과 순서는 같다.
  INT-002를 두 번 호출하는 것으로 충분해 별도 시나리오를 두지 않는다.
- 조회 중 자산이 DELETED로 바뀌는 경쟁은 REPEATABLE_READ 스냅샷 기준으로 일관된다. 새 위험이
  아니므로 범위 밖이다.

### External APIs

- 단위 테스트는 `ObjectStoragePort` 대역만 쓴다(UNIT-001~003).
- 통합 테스트는 LocalStack S3를 쓴다. 실제 AWS를 호출하지 않는다.
- 발급은 객체 존재를 확인하지 않는다. 객체가 없으면 URL은 404를 가리키며, 이것은 기존
  `ObjectStoragePort` 계약이다. INT-006만 실제 객체를 둔다.

### Failure recovery and reconciliation

- presign 실패는 H1 결정대로 목록 전체를 `STORAGE_UNAVAILABLE`(503)로 실패시키고 UNIT-003으로
  검증한다. 실제 SDK 실패를 통합 테스트로 재현하지 않는다.
- presign은 서명을 로컬에서 계산한다. 실패할 수 있는 지점은 SDK가 task role 임시 자격 증명을
  얻거나 갱신하는 단계이고, 결과는 캐시된다. 객체 없음(404), 권한 없음·URL 만료(403)는 발급이
  아니라 클라이언트의 객체 조회 시점에 드러나며 이 계획의 범위가 아니다.
- URL 만료 후 클라이언트가 목록을 다시 조회하는 흐름은 서버 상태를 바꾸지 않으므로 시나리오가 없다.

## 8. Test data and isolation

- Fixtures: 기존 feed 통합 테스트의 계정·질문글·수신자 생성 방식을 재사용한다. 미디어는
  `MediaAsset.upload(...).ready()`로 저장하고 storage key에 `System.nanoTime()`을 붙여 고유하게
  만든다(`MediaAttachmentIntegrationTest` 선례). 질문글과 답변에는 본문을 넣는다.
- Database isolation: `@BeforeEach`에서 `media_attachment`, `media_asset`, 질문글·수신자·답변
  fixture를 FK 순서대로 지운다. `MediaAttachmentIntegrationTest`의 삭제 순서 주석(deferred
  trigger)을 따른다.
- Clock/randomness: `expiresAt`은 실제 시각 기반 SDK 값이라 고정하지 않는다. 요청 전후 시각으로
  [before + ttl - 여유, after + ttl + 여유] 범위를 검사한다.
- External API doubles: 단위는 수동 fake port, 통합은 LocalStack(`localstack/localstack:3.8`).
- Postgis 전용 컨텍스트: `application-test.yml`의 media 설정은 placeholder이고 자격 증명이 없다.
  첨부가 있는 카드를 조회하는 시나리오는 반드시 LocalStack 지원 클래스에서 실행한다. 기존
  Postgis 전용 feed 테스트가 계속 통과하는 근거는 UNIT-001이다.
- Cleanup: DB는 `@BeforeEach` 삭제. LocalStack 객체는 고유 key라 정리하지 않는다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

두 실행자는 서로 다른 source set의 파일만 소유한다. Executor B는 Executor A의 production 변경이
컴파일되는 상태에서 시작한다.

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Executor A (unit) | 신규 `src/test/java/com/dnd/qello/feed/service/FeedMediaViewResolverTest.java`(이름은 resolver 이름을 따른다) | UNIT-001~003 | `./gradlew test --tests '*FeedMediaViewResolverTest'` |
| 1 | Executor A (unit) | `src/test/java/com/dnd/qello/feed/web/InboxApiMockMvcTest.java`, `SentPostApiMockMvcTest.java`, `PostAnswerApiMockMvcTest.java` | UNIT-004~006, UNIT-008~009(Revision 2), MIGRATE(카드 생성자의 `mediaIds` 인자 교체) | `./gradlew test --tests 'com.dnd.qello.feed.web.*'` |
| 1 | Executor A (unit) | `src/test/java/com/dnd/qello/feed/FeedPersistenceBoundaryTest.java` | UNIT-007 | `./gradlew test --tests '*FeedPersistenceBoundaryTest'` |
| 1 | Executor A (unit) | `src/test/java/com/dnd/qello/feed/service/FeedInteractionApplicationServiceTest.java` | MIGRATE(`SentPostCard` 생성자 인자 교체만) | `./gradlew test --tests '*FeedInteractionApplicationServiceTest'` |
| 2 | Executor B (integration) | 신규 `src/integrationTest/java/com/dnd/qello/FeedMediaViewUrlIntegrationTest.java` | INT-001~007, INT-009~010(Revision 2) | `./gradlew integrationTest --tests '*FeedMediaViewUrlIntegrationTest'` |
| 2 | Executor B (integration) | `src/integrationTest/java/com/dnd/qello/OpenApiSpecificationIntegrationTest.java`, `docs/api/openapi.json`(재생성) | INT-008 | `./gradlew integrationTest --tests '*OpenApiSpecificationIntegrationTest'` |
| 2 | Executor B (integration) | `src/integrationTest/java/com/dnd/qello/InboxQueryIntegrationTest.java` | MIGRATE(177행 `mediaIds()` 단언을 `media()`로 교체) | `./gradlew integrationTest --tests '*InboxQueryIntegrationTest'` |

기존 파일을 수정할 때는 클래스 헤더의 `Source scenario`에 이 계획의 시나리오 ID와
`(added <ISO 8601 시각>)`을 덧붙인다. MIGRATE 항목은 새 시나리오가 아니라 기존 단언의 대상
필드만 바꾸며 기대 의미를 바꾸지 않는다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과(`./gradlew test`)
- [ ] 통합 테스트 통과(`./gradlew integrationTest`)
- [ ] `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check` 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성(`templates/test-report.md`)

실패 판단 기준: P0 시나리오 하나라도 실패하거나, 기존 feed 단위·통합 테스트가 하나라도 새로
실패하면 FAIL이다. LocalStack을 띄울 수 없어 INT를 실행하지 못하면 BLOCKED로 보고한다.

## 11. Human approval

결정 사항:

- H1. presign 실패 처리: 목록 전체를 `STORAGE_UNAVAILABLE`(503)로 실패시킨다. `ProfileImageResolver`와
  같은 방식이다. 실패는 자격 증명 조회 단계에서 그 시점의 발급 전체에 걸치므로, 미디어만 빼면
  클라이언트가 원인을 알 수 없다.
- H2. `moderation_status` 필터: 이번 범위에서 제외한다. 현재 이미지 검수 기능이 없어 모든
  `media_asset`이 `PENDING`으로 남아 있고, 지금 PASSED 필터를 걸면 모든 첨부 이미지가 사라진다.
  이미지 검수 기능을 후속 작업으로 구현할 때 이 필터를 추가한다. 그때 검수 전(`PENDING`) 이미지의
  노출 정책도 함께 정한다.

- Reviewer: @Byuntil
- Decision: APPROVED (H1·H2 위 결정대로)
- Approved at: 2026-10-02

Revision 2 (2026-10-02):

- 변경 이유: `InboxDetailResponse.card`와 `SentPostDetailResponse.card`가 목록 카드 타입을 재사용해
  상세 응답도 함께 바뀐다. Revision 1은 상세 Response 파일만 보고 미디어 필드가 없다고 잘못 분류했다.
  사용자가 상세 2개를 범위에 넣기로 결정했다(TASK.md D4).
- 추가 시나리오: UNIT-008, UNIT-009, INT-009, INT-010. INT-008을 상세 `card` schema까지 확장한다.
- production 코드 변경은 없다.

- Reviewer: @Byuntil
- Decision: APPROVED (Revision 2)
- Approved at: 2026-10-02
