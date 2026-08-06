# Test Plan: TEST-PLAN-GH-70-MEDIA-ASSET-SERVICE

> Created at: `2026-08-07T02:15:28+09:00`
> GitHub Issue: `#70`
> Status: Approved

## 1. Objective

방향글(`direction_post`)과 답변(`answer`)에 이미지를 첨부하는 흐름(presigned URL
발급 → 클라이언트 업로드 → 업로드 확인(confirm) → attach)을 V1 schema의
`media_asset`/`media_attachment` 제약과 애플리케이션 서비스 계층 양쪽에서
검증한다. 실패하면 (a) 소유자가 아닌 사용자의 미디어가 첨부되거나, (b) 실제
S3에 존재하지 않거나 검증되지 않은 객체가 READY로 승인되거나, (c) 본문도
READY 미디어도 없는 콘텐츠가 공개 상태가 되거나, (d) 동시 업로드/attach
경쟁으로 `media_asset` 상태와 첨부 관계가 어긋날 수 있으므로, PostgreSQL의
FK/unique/deferred trigger와 서비스 레벨 사전 검증을 실제 통합 테스트로
증명한다. `media_asset`/`media_attachment` 테이블과 관련 trigger는 V1
migration에 이미 존재하며 이번 이슈에서 새 migration은 만들지 않는다.

## 2. Scope

### Included

- `MediaAsset` 도메인 상태 전이(UPLOADING→READY/REJECTED/DELETED) 단위 검증
- presigned URL 발급 서비스의 소유자 검증과 mime/size 화이트리스트 검증
- 업로드 완료 확인(confirm) 시 `HeadObject` 기반 검증과 상태 전이(READY/REJECTED)
- `MediaAttachmentService`의 attach 소유권·`display_order`·콘텐츠 불변식
  사전 검증 (기존 `answer.domain.MediaAttachment` record 자체의 회귀 커버리지
  포함 — 현재 단위 테스트가 없음)
- `media_asset`/`media_attachment`의 기존 V1 제약(owner 일치 복합 FK, XOR
  target, `uq_media_asset_storage_key`, `ct_media_attachment_preserves_content`,
  `ct_media_status_preserves_content` deferred trigger)에 대한 실제
  PostgreSQL/PostGIS 통합 검증
- Testcontainers LocalStack 기반 S3 상호작용(presigned PUT, `HeadObject`) 통합 검증
- 동시 confirm 재호출, 동시 storage_key 충돌, 동시 attach 경쟁 시나리오
- `answer`가 `direction`의 리포지토리 포트(도메인 객체 반환)만 사용하고 다른
  feature의 JPA Entity/JDBC 구현을 직접 참조하지 않는지 확인(ADR-0002)

### Excluded

- HTTP controller, API 문서, DTO
- 실제 dev S3 버킷 대상 수동/스모크 테스트 — 런타임 IAM Role이 아직 없음
  (`docs/reports/infrastructure/gh-63-D-1.md` §5, 별도 이슈)
- 실제 이미지 모더레이션 파이프라인 연동(`moderation_status`는 PENDING 유지)
- feed(Inbox/SentPost) 조회 응답에 미디어 노출
- 고아 UPLOADING 미디어 정리 배치
- V1 migration 자체의 수정, 신규 Flyway migration
- 실제 AWS S3 대비 LocalStack presigned URL 서명 동작의 완전한 동등성 보증
  (알려진 한계로 4절에 기록)

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #70 | presigned URL 업로드 흐름, 소유권·화이트리스트·콘텐츠 불변식 검증, LocalStack 기반 통합 테스트 |
| `TASK.md` | Scope/Explicit exclusions/Completion criteria 전체, ownership(`answer/**`, `direction/**` attach 통합 지점) |
| `docs/adr/0001-database-schema-ownership.md` | Flyway V1이 schema source of truth, Hibernate는 `validate`만 |
| `docs/adr/0002-jpa-jdbc-boundary.md` | 조건부 상태 전이·lock류는 JDBC, 다른 feature Entity/Spring Data Repository 직접 참조 금지, 외부 aggregate는 scalar ID로 연결 |
| `V1__create_direction_communication_schema.sql` (355~382, 558~586, 940~1091) | `media_asset`/`media_attachment` 컬럼·제약, `assert_post_has_content`/`assert_answer_has_content`, `ct_media_attachment_preserves_content`, `ct_media_status_preserves_content` deferred trigger |
| `docs/error-codes.md` §9 (ANS) | 기존 `ANS-VAL-005 INVALID_MEDIA_TARGET`; 신규 코드는 `ANS-VAL-00x`/`ANS-DOM-00x`/`ANS-EXT-001` 계열로 이어 붙임(정확한 번호는 구현 시 확정) |
| `common/error/ConstraintExceptionMapper.java`, `GlobalExceptionHandler.extractConstraintName` | `DataIntegrityViolationException` 매핑은 예외 메시지에 제약/trigger 이름 문자열이 그대로 포함될 때만 동작 — `ct_media_*` trigger의 `RAISE EXCEPTION` 메시지는 현재 trigger 이름을 포함하지 않아 매핑되지 않으면 `CommonErrorCode.CONFLICT`로 귀결됨(4절 R-07) |
| `docs/reports/infrastructure/gh-63-D-1.md` §2, §5 | S3 버킷은 presigned URL 접근을 전제로 설계됨, 런타임 IAM Role은 별도 이슈로 유예 |
| 기존 코드 패턴 | `answer/domain/Answer.java`(불변 도메인 + 상태 전이 메서드), `answer/service/AnswerNotificationService.java`(같은 transaction에서 여러 repository 조정, 조건부 UPDATE로 경쟁 제어), `PostgisContainerIntegrationTestSupport.java`(Testcontainers 기반 클래스) |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| R-01 소유자가 아닌 사용자가 presigned URL을 요청하거나 남의 `media_asset`을 attach한다. | 타인 계정으로 임의 파일 업로드/첨부 | Medium | P0 | 소유자 불일치 unit + FK 조합(`fk_media_attachment_asset_owner` 등) integration |
| R-02 presigned URL 발급이 mime/size 화이트리스트 없이 응답한다. | 허용되지 않은 파일 형식·과대 용량 업로드 | Medium | P0 | 화이트리스트 위반 unit |
| R-03 confirm이 실제 S3 객체 존재·크기·타입을 검증하지 않고 READY로 전환한다. | 존재하지 않거나 변조된 콘텐츠가 공개됨 | Medium | P0 | LocalStack 기반 confirm 성공/실패 integration |
| R-04 본문 없는 글/답변에서 유일한 READY 미디어가 detach/DELETED로 사라진다. | 빈 콘텐츠 공개, `ct_media_*` deferred trigger가 최종 방어지만 서비스 사전 검증 없이는 늦게(commit 시점) 발견됨 | Medium | P0 | 서비스 사전 검증 unit + trigger rollback integration |
| R-05 두 요청이 동시에 같은 `storage_key`를 발급받아 한쪽이 다른 쪽 객체를 덮어쓴다. | 콘텐츠 유실/뒤바뀜 | Low | P1 | `uq_media_asset_storage_key` 경합 integration |
| R-06 confirm이 중복 호출되어 상태 전이가 중복 부수효과를 일으킨다. | 중복 처리, 상태 어긋남 | Medium | P1 | 동시 confirm 멱등 integration |
| R-07 `ct_media_attachment_preserves_content`/`ct_media_status_preserves_content`의 `RAISE EXCEPTION` 메시지가 `ConstraintExceptionMapper.knownConstraints()`에 없어 `CommonErrorCode.CONFLICT`로만 응답된다. | 서비스 사전 검증을 우회하는 경로(동시성 등)에서는 원인이 불분명한 409만 반환됨 | Medium | P1 | 사전 검증을 의도적으로 건너뛴 repository 직접 호출로 trigger만 단독 발동시켜 실제 응답 코드 확인 |
| R-08 `answer` 패키지의 attach 서비스가 `direction`의 JPA Entity/JDBC 구현을 직접 참조한다. | ADR-0002 위반, feature 결합도 상승 | Low | P1 | 아키텍처 경계 소스 스캔 테스트 |
| R-09 LocalStack의 presigned URL/S3 동작이 실제 AWS S3와 100% 동일하지 않다. | 통합 테스트 통과가 실제 AWS 동작을 완전히 보증하지 못함 | Low | P2 | 알려진 한계로 문서화, 실 버킷 스모크 테스트는 범위 밖(별도 확인 필요) |
| R-10 `MediaAttachment` record(기존 코드, PR #40 유래)가 현재 단위 테스트로 커버되지 않는다. | 회귀 발생 시 감지 못함 | Low | P1 | 신규 unit으로 기존 검증 로직 커버 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| MEDIA-ASSET-SERVICE-UNIT-001 | ownerId, mimeType, byteSize 조합(허용/비허용 mime, 0 이하 또는 상한 초과 size 포함) | `MediaAsset` 생성(UPLOADING) 커맨드 검증 | 허용 mime·size 범위만 통과하고 위반 시 명확한 field/reason의 예외를 던진다. | P0 | Media domain executor |
| MEDIA-ASSET-SERVICE-UNIT-002 | UPLOADING 상태의 `MediaAsset` | READY/REJECTED 전이 메서드 호출 | 허용된 전이만 성공하고, 이미 READY/REJECTED/DELETED인 자산에 재전이를 시도하면 상태별로 멱등 처리되거나 `INVALID_MEDIA_STATE` 계열 예외가 발생한다(멱등 대상과 거부 대상을 명시적으로 구분). | P0 | Media domain executor |
| MEDIA-ASSET-SERVICE-UNIT-003 | DELETED `MediaAsset` | 임의 상태 전이 시도 | 모든 전이가 거부된다(terminal state). | P0 | Media domain executor |
| MEDIA-ASSET-SERVICE-UNIT-004 | presigned URL 발급 요청 커맨드(요청자 ID, 대상 소유자 ID 일치/불일치) | 발급 서비스 사전 검증 | 요청자와 소유자가 다르면 URL을 발급하지 않고 거부한다. | P0 | Media domain executor |
| MEDIA-ASSET-SERVICE-UNIT-005 | `HeadObject` 결과 fake(존재+크기/타입 일치, 존재+불일치, 미존재) | confirm 판정 로직(순수 함수, S3 호출은 port fake로 대체) | 일치하는 경우만 READY로 판정하고 나머지는 REJECTED로 판정하며 원인을 구분해 기록한다. | P0 | Media domain executor |
| MEDIA-ASSET-SERVICE-UNIT-006 | `MediaAttachment` record 생성 값(mediaId/ownerId/displayOrder 양수, postId/answerId 중 정확히 하나) | record 컴팩트 생성자 검증(기존 로직 회귀 커버) | id 비양수, XOR 위반, 대상 id 비양수 각각에서 `INVALID_MEDIA_TARGET`/`INVALID_ID`를 던진다. | P0 | Attachment integration executor |
| MEDIA-ASSET-SERVICE-UNIT-007 | attach 대상(post/answer)의 소유자 ID, 요청자 ID, 대상 상태(공개 전/공개 후), 기존 READY 미디어 유무 | `MediaAttachmentService` attach 사전 검증 | 소유자 불일치, 비 READY 미디어 attach 시도, 공개 상태에서 유일한 콘텐츠를 제거하는 detach를 각각 거부한다. | P0 | Attachment integration executor |
| MEDIA-ASSET-SERVICE-UNIT-008 | `answer`/`direction` 패키지의 소스 파일 집합과 import 목록 | 아키텍처 스캔 | media 관련 클래스가 다른 feature의 `*JpaEntity`, `.repository.jdbc.`, `.repository.jpa.` 를 직접 참조하지 않는다(리포지토리 포트·도메인 객체 참조는 허용). | P1 | Test orchestrator |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| MEDIA-ASSET-SERVICE-INT-001 | `MediaAsset` JDBC repository + Postgres | 빈 스키마, 소유자 계정 fixture | presigned URL 발급 커맨드 실행 → `media_asset` UPLOADING 행 생성 확인 | `storage_key`가 unique하게 채번되고 상태·소유자가 요청과 일치한다. | media_asset 행 삭제 |
| MEDIA-ASSET-SERVICE-INT-002 | 발급 서비스 + LocalStack S3 | LocalStack 컨테이너, presigned URL 발급 결과 | 발급받은 URL로 실제 PUT 업로드 실행 | 객체가 버킷에 생성되고 이후 `HeadObject`로 크기/타입을 확인할 수 있다. | LocalStack 객체 삭제 또는 컨테이너 폐기 |
| MEDIA-ASSET-SERVICE-INT-003 | confirm 서비스 + LocalStack | INT-002로 업로드된 객체, UPLOADING `media_asset` | confirm 호출 | 크기/타입이 발급 시 신고 값과 일치하면 READY로 전이한다. | media_asset/LocalStack 객체 정리 |
| MEDIA-ASSET-SERVICE-INT-004 | confirm 서비스 + LocalStack | UPLOADING `media_asset`이지만 해당 키로 객체 미업로드 | confirm 호출 | `HeadObject` 404 → REJECTED로 전이하고 명확한 오류/사유를 남긴다. | media_asset 정리 |
| MEDIA-ASSET-SERVICE-INT-005 | confirm 서비스 + LocalStack | 업로드된 객체의 크기 또는 content-type이 신고 값과 다름 | confirm 호출 | REJECTED로 전이한다(내용 검증 실패로 구분). | media_asset/LocalStack 객체 정리 |
| MEDIA-ASSET-SERVICE-INT-006 | `MediaAttachmentService` + `direction.repository.DirectionPostRepository`/`answer.repository.AnswerRepository` | READY `media_asset`(소유자 A), post/answer(소유자 A, 소유자 B 각각) | 소유자 A/B로 attach 시도 | 소유자 A는 성공하고 소유자 B는 애플리케이션 사전 검증에서 거부되며 DB까지 도달하지 않는다. | media_attachment/media_asset 정리 |
| MEDIA-ASSET-SERVICE-INT-007 | `MediaAttachmentRepository` 직접 호출(서비스 사전 검증 우회) | READY media(소유자 A), 소유자 B의 post | 사전 검증 없이 repository.save 직접 호출로 owner 불일치 attach 강행 | 복합 FK(`fk_media_attachment_asset_owner`/`fk_media_attachment_post_owner`) 위반으로 insert가 rollback된다 — 이 경로의 실제 HTTP 등가 오류 코드를 확인하고 R-07 문서화에 반영한다. | 컨테이너 rollback |
| MEDIA-ASSET-SERVICE-INT-008 | `direction_post`/`answer` + `media_attachment` + deferred trigger | 본문 없는 post/answer, READY media 1건으로 공개(ACTIVE/PUBLISHED) 상태 | 그 media를 detach하거나 DELETED로 전이 시도 | `ct_media_attachment_preserves_content`/`ct_media_status_preserves_content`가 commit 시점에 거부하고 rollback한다. R-07에 따라 실제 응답 오류 코드를 기록한다. | 컨테이너 rollback |
| MEDIA-ASSET-SERVICE-INT-009 | 위와 동일 대상, 서비스 사전 검증 경유 | `MediaAttachmentService`를 통해 동일한 detach 시도 | 애플리케이션 사전 검증이 DB보다 먼저 명확한 도메인 오류 코드로 거부한다(빠른 실패). | 없음(요청 실패, 상태 불변) |
| MEDIA-ASSET-SERVICE-INT-010 | 동시 confirm 두 worker | 동일 UPLOADING `media_asset`, 정상 업로드 완료된 객체 | 두 transaction이 동시에 confirm 실행 | 한쪽만 READY 전이를 반영하고 다른 쪽은 이미 READY임을 멱등하게 확인한다(중복 부수효과 없음). | media_asset 정리 |
| MEDIA-ASSET-SERVICE-INT-011 | 동시 발급 요청 2건(강제로 같은 key 충돌 유도) | 동일 storage_key를 쓰도록 조작한 두 삽입 시도 | 동시 insert 실행 | `uq_media_asset_storage_key`가 두 번째 삽입을 거부한다. | media_asset 정리 |
| MEDIA-ASSET-SERVICE-INT-012 | 전체 V1 schema + media 관련 repository adapter | 빈 PostgreSQL/PostGIS 컨테이너 | migration 재적용 + `ddl-auto=validate` + 기존 answer/direction 테스트 스위트 | media 관련 변경이 기존 스키마 회귀를 일으키지 않는다. | 컨테이너 폐기 |

## 7. Cross-cutting scenarios

### Database and transactions

- `media_asset`/`media_attachment`는 JDBC로 조작한다(조건부 상태 전이, unique
  경합 확인이 필요한 연산 — ADR-0002). Hibernate는 관여하지 않는다.
- `ct_media_attachment_preserves_content`/`ct_media_status_preserves_content`는
  `DEFERRABLE INITIALLY DEFERRED`이므로 통합 테스트는 commit 시점까지 진행한
  뒤 rollback 여부를 확인해야 한다 — 같은 transaction 안에서의 assertion만으로는
  검증되지 않는다.
- presigned URL 발급(‘media_asset’ insert)과 attach(‘media_attachment’
  insert)는 서로 다른 요청/transaction으로 분리된다 — 두 호출 사이의 상태
  일관성(예: attach 시점에 media_asset이 이미 REJECTED/DELETED로 바뀐 경우)을
  별도로 검증한다.

### Concurrency and idempotency

- confirm 재호출, 동시 storage_key 충돌, 동시 attach 시도를 DB unique/FK를
  최종 방어선으로 실제 경합 상황에서 확인한다(INT-010, INT-011).
- 승인된 schema에 없는 version 컬럼이나 락 컬럼을 새로 추가하지 않는다.

### External APIs

- 유일한 실제 외부 의존성은 LocalStack S3(Testcontainers)다. 실제 AWS
  자격 증명이나 dev 버킷은 사용하지 않는다.
- presigned URL 발급/`HeadObject`는 애플리케이션이 정의한 port 인터페이스
  뒤에 두고, 단위 테스트는 fake 구현으로, 통합 테스트는 LocalStack 실제
  구현으로 검증해 두 계층의 assertion이 겹치지 않게 한다.
- LocalStack과 실제 AWS S3 presigned URL 서명 동작의 완전한 동등성은
  보증하지 않는다(R-09) — 완료 조건에 이 한계를 명시한다.

### Failure recovery and reconciliation

- confirm 도중 S3 조회 실패(객체 없음/네트워크 오류)는 예외로 전체 요청을
  실패시키지 않고 REJECTED로 안전하게 귀결시키는 경로와, 실제 인프라 오류로
  판정해 재시도 가능하게 남겨야 하는 경로를 구분한다(예: 404는 REJECTED
  확정, 5xx/timeout은 재시도 가능한 오류로 구분해 응답).
- 고아 상태로 남는 UPLOADING `media_asset`(클라이언트가 끝내 confirm을 호출하지
  않는 경우)은 이번 이슈에서 배치로 정리하지 않는다 — 알려진 제약으로
  완료 조건과 테스트 보고서에 명시한다(TASK.md 제외 범위와 일치).

## 8. Test data and isolation

- Fixtures: 계정 2개 이상(소유자/제3자), `direction_post`/`answer` fixture를
  공개 전(MATCHING/SUBMITTED)과 공개 후(ACTIVE/PUBLISHED) 상태 각각으로 준비,
  `media_asset`을 UPLOADING/READY/REJECTED/DELETED 상태별로 준비.
- Database isolation: `PostgisContainerIntegrationTestSupport`를 확장하거나
  같은 패턴의 새 지원 클래스에 LocalStack 컨테이너를 추가한다. 일반 시나리오는
  transaction rollback으로, deferred trigger 검증은 명시적 commit 후 다음
  테스트에서 행 삭제 또는 컨테이너 lifecycle로 정리한다.
- Clock/randomness: UTC 고정 `Instant`/`Clock`을 fixture로 주입한다(기존
  Answer/DirectionPost 패턴과 동일). `storage_key`의 UUID 성분은 생성기를
  주입 가능하게 하거나 테스트에서 직접 지정해 충돌 시나리오(INT-011)를
  재현할 수 있게 한다.
- External API doubles: 단위 테스트는 presigned URL 발급/`HeadObject` port의
  fake 구현만 사용한다. 통합 테스트는 LocalStack 컨테이너의 실제 S3 API를
  사용한다.
- Cleanup: `media_attachment` → `media_asset` → post/answer 순으로 FK 역순
  정리하거나 컨테이너 폐기로 대체한다. LocalStack에 남는 테스트 객체는 각
  테스트에서 삭제하거나 컨테이너 재시작으로 정리한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Media domain & storage executor | `src/main/java/com/dnd/qello/answer/domain/MediaAsset*.java`, `src/main/java/com/dnd/qello/answer/repository/MediaAssetRepository.java`, `src/main/java/com/dnd/qello/answer/repository/jdbc/JdbcMediaAssetRepository.java`, `src/main/java/com/dnd/qello/answer/service/MediaUploadService.java`(또는 동등 클래스), 신규 S3 연동/포트·설정 클래스, `build.gradle`(AWS SDK v2, Testcontainers LocalStack 의존성), `src/integrationTest/java/com/dnd/qello/**`(LocalStack 지원 클래스), `src/test/java/com/dnd/qello/answer/**`(해당 범위) | UNIT-001~005, INT-001~005, INT-010~011 | `./gradlew test`, `./gradlew integrationTest` 중 해당 범위 |
| 2 | Attachment integration executor | `src/main/java/com/dnd/qello/answer/repository/MediaAttachmentRepository.java`(조회 메서드 추가), `src/main/java/com/dnd/qello/answer/repository/jdbc/JdbcMediaAttachmentRepository.java`, `src/main/java/com/dnd/qello/answer/service/MediaAttachmentService.java`, `docs/error-codes.md`(§9 ANS 신규 코드), `src/test/java/com/dnd/qello/answer/**`(attach 범위), `src/integrationTest/java/com/dnd/qello/answer/**` | UNIT-006~007, INT-006~009 | 소유권·콘텐츠 불변식 unit + PostgreSQL FK/trigger integration |
| 3 | Test orchestrator | `src/test/java/com/dnd/qello/architecture/**`(또는 기존 boundary test 패턴 위치), `docs/reports/tests/gh-70-MEDIA-ASSET-SERVICE.md`, 전체 회귀 확인 | UNIT-008, INT-012, 모든 P0 | 전체 Gradle/Testcontainers 실행 + 보고서 + Harness 게이트 |

각 executor는 소유 경로 밖의 파일을 수정하지 않는다. 새 dependency(AWS SDK,
LocalStack), 공유 fixture, 새 오류 코드 번호는 실행 전 서로 조율하고 충돌 시
상위 작업에 보고한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현 및 결과 증거 확보
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 모든 테스트 클래스 헤더에 정확한 ISO 8601 timestamp와 source scenario ID
- [ ] 단위 테스트 및 실제 PostgreSQL/PostGIS + LocalStack 통합 테스트 통과
- [ ] deferred trigger commit-time rollback, 동시 confirm 멱등, storage_key
      경합 증거 확보
- [ ] R-07(제약 매핑 공백)의 실제 응답 결과를 테스트 보고서에 기록하고, 서비스
      사전 검증이 1차 방어선임을 명시
- [ ] R-09(LocalStack-AWS 동등성 한계)를 테스트 보고서의 잔여 위험으로 기록
- [ ] `templates/test-report.md` 기반 테스트 보고서 생성
- [ ] `./harness check`, `./harness pr-ready --project-tests`,
  `npm run hooks:validate`, `git diff --check` 통과
- [ ] 구현 결과를 origin에 push하지 않고 사용자 검토 대기

## 11. Human approval

- Reviewer: User
- Decision: Approved
- Approved at: 2026-08-07T02:30:00+09:00
