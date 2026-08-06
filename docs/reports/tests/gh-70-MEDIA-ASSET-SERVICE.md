# Test Report: MEDIA-ASSET-SERVICE

> Created at: `2026-08-07T02:24:21+09:00`
> GitHub Issue: `#70`
> Branch: `feat/gh-70-media-asset-service`
> Commit: `167260a` (테스트 계획 승인 시점) — 구현은 이후 커밋에서 추가됨

## 1. Executive summary

- Result: `PASS`
- Tested scope: `MediaAsset` 도메인 상태 전이, presigned URL 발급/화이트리스트
  검증, LocalStack 기반 실제 업로드→confirm 흐름(READY/REJECTED 판정,
  크기·타입 불일치, 객체 없음), 동시 confirm 멱등성, storage_key unique
  경합, `MediaAttachmentService`의 소유권·콘텐츠 불변식 사전 검증, DB
  복합 FK 및 deferred trigger 방어선, `answer`(media 포함) 패키지의 feature
  경계 스캔. `./gradlew check`(전체 회귀: 단위 80개 + 통합 93개)까지 통과.
- Unverified scope: 실제 AWS S3(운영 dev 버킷) 대상 수동/스모크 테스트 —
  런타임 IAM Role이 아직 없어 이번 이슈 범위 밖(TASK.md 제외 목록). LocalStack과
  실제 AWS S3의 presigned URL 서명 동작 완전 동등성도 미검증(계획 R-09).
- Release recommendation: 승인 가능. 다만 이 기능은 controller/DTO가 없는
  service 계층까지이므로 이 자체로 배포 가능한 사용자 기능은 아니며, 이후
  이슈에서 HTTP API와 런타임 IAM Role 연결이 필요하다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 21 (toolchain) |
| Spring Boot | 3.5.16 |
| Database | Testcontainers PostgreSQL/PostGIS(로컬 컨테이너, `postgis/postgis:16-3.5-alpine` 기반) |
| Object storage | Testcontainers LocalStack `localstack/localstack:3.8`(S3 서비스만) |
| AWS SDK | `software.amazon.awssdk:bom:2.51.1`(s3, url-connection-client) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` (전체 단위) | PASS | 80 | ~4s | 로컬 실행, 실패 0 |
| `./gradlew integrationTest` (전체 통합) | PASS | 93 | ~57s(컨테이너 기동 포함 시 최초 실행은 더 오래 걸림) | 로컬 실행, 실패 0, Testcontainers 14개 클래스 |
| `./gradlew check` (전체 회귀) | PASS | 173(단위+통합) | ~57s(증분 캐시 반영) | 로컬 실행, 실패 0 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `MediaAssetTest.validatesStructuralMetadata` | mime/size/storageKey 구조 검증 |
| UNIT-002 | PASS | `MediaAssetTest.transitionsOnlyFromUploading` | UPLOADING에서만 READY/REJECTED 전이 |
| UNIT-003 | PASS | `MediaAssetTest.deletedIsTerminal` | DELETED는 terminal, deletedAt 불변식 |
| UNIT-004 | PASS | `MediaUploadServiceTest.rejectsIssueWhenRequesterIsNotOwner`, `rejectsDisallowedMimeAndOversizedRequests`, `issuesUrlForOwnerWithinWhitelist` | 소유자 불일치·화이트리스트·정상 발급 |
| UNIT-005 | PASS | `MediaUploadServiceTest.confirmsReadyWhenObjectMatches`, `rejectsWhenObjectMissingOrMismatched`, `confirmIsIdempotentAfterResolution` | confirm 판정 로직(fake storage) |
| UNIT-006 | PASS | `MediaAttachmentServiceTest.validatesExactlyOneTarget` | `MediaAttachment` record 회귀 커버(기존 로직, 신규 테스트) |
| UNIT-007 | PASS | `MediaAttachmentServiceTest`의 attach/detach 6개 메서드 | 소유권·READY 상태·콘텐츠 불변식 사전 검증(post 대상) |
| UNIT-008 | PASS | `AnswerJdbcBoundaryTest`(3개 메서드) | domain/port framework 독립성, 양방향 feature 경계(direction/feed ↔ answer) |
| INT-001 | PASS | `MediaAssetStorageIntegrationTest.issuesUploadUrlAndCreatesUploadingAsset` | UPLOADING 자산 생성, 유일 storage_key |
| INT-002~003 | PASS | `MediaAssetStorageIntegrationTest.confirmTransitionsToReadyWhenUploadedObjectMatches` | LocalStack 실제 PUT→confirm→READY |
| INT-004 | PASS | `MediaAssetStorageIntegrationTest.confirmRejectsWhenObjectMissing` | 객체 없음 → REJECTED |
| INT-005 | PASS | `confirmRejectsWhenSizeMismatches`, `confirmRejectsWhenContentTypeMismatches` | 크기 불일치(presigned PUT 실제 업로드) / 타입 불일치(서명 특성상 presigned URL 우회, 직접 PUT으로 재현) |
| INT-006 | PASS | `MediaAttachmentIntegrationTest.attachRejectsStrangerMediaBeforeReachingDatabase` | 남의 미디어 attach는 DB 도달 전 애플리케이션에서 거부 |
| INT-007 | PASS | `MediaAttachmentIntegrationTest.bypassingServiceViolatesOwnerCompositeForeignKey` | 서비스 우회 시 복합 FK 위반(즉시, non-deferred) |
| INT-008 | PASS | `MediaAttachmentIntegrationTest.bypassingServiceDetachIsBlockedByDeferredTriggerAtCommit` | 서비스 우회 시 deferred trigger가 commit 시점에 차단 |
| INT-009 | PASS | `MediaAttachmentIntegrationTest.serviceDetachFailsFastWithoutMutatingState` | 서비스 경유 시 commit 전 명확한 오류 코드로 빠르게 실패, 상태 보존 |
| INT-010 | PASS | `MediaAssetStorageIntegrationTest.concurrentConfirmIsIdempotent` | 동시 confirm 2회, 조건부 UPDATE로 단일 확정 |
| INT-011 | PASS | `MediaAssetStorageIntegrationTest.storageKeyCollisionIsRejectedByUniqueConstraint` | `uq_media_asset_storage_key` 충돌 거부 |
| INT-012 | PASS | 전체 `./gradlew check` | 기존 answer/direction/feed 등 전체 스위트 회귀 없음 |

## 5. Failures and diagnostics

구현 과정에서 발견하고 수정한 문제(최종 실행에는 반영되어 실패 없음):

- `MediaAssetStorageIntegrationTest`에서 처음에 `mimeType="text/plain"`을 썼다가
  `qello.media.allowed-mime-types` 화이트리스트(`image/jpeg,image/png,image/webp`)에
  없어 4개 테스트가 `INVALID_MEDIA_METADATA`로 실패 — 테스트 fixture를
  `image/jpeg`로 수정.
- `AnswerRepository`에 `findByIdAndAuthorId`를 인터페이스에 먼저 추가하고
  `JpaAnswerRepository`/`SpringDataAnswerRepository` 구현을 누락해
  컴파일 실패 — 구현 추가로 해결.
- `MediaAttachmentIntegrationTest`의 `@BeforeEach` 정리 순서 문제: 본문 없는
  ACTIVE post의 유일한 READY 미디어를 남겨둔 채 `DELETE FROM media_attachment`를
  먼저 실행하면 `ct_media_attachment_preserves_content` deferred trigger가
  정리 자체를 막음 — `DELETE FROM direction_post`를 먼저 실행해 `ON DELETE
  CASCADE`로 media_attachment가 같은 statement 안에서 함께 사라지게 순서를
  변경해 해결(5절 참고, 실제 서비스 코드가 아니라 테스트 fixture 정리 순서
  문제였음).
- `application-local.properties`에 `qello.media.bucket`을 기본값 없이 두었더니
  `local` 프로필로 컨텍스트를 올리는 `QelloLocalProfileIntegrationTest`가
  placeholder 해석 실패로 깨질 뻔함 — `${QELLO_MEDIA_BUCKET:qello-local-dev}`로
  안전한 로컬 기본값을 추가해 해결(실제 dev 버킷 이름이 아님을 이름 자체로
  명시).

## 6. Potential issues

### Application code

- presigned URL 서명에는 발급 시 지정한 content-type이 포함되므로, 클라이언트가
  다른 content-type으로 실제 업로드하면 그 URL 자체가 거부된다(서명 불일치,
  S3/LocalStack 레벨). `INT-005`의 content-type 불일치 케이스는 이 특성 때문에
  presigned URL을 우회해 재현했다 — 실제 서비스에서는 "이미 다른 타입으로
  저장된 객체"가 이 경로로 자연 발생하기 어렵고, 오히려 서명 검증이 1차
  방어선 역할을 한다는 뜻이다. 크기 불일치는 presigned PUT 서명에
  Content-Length가 포함되지 않아 실제로 재현 가능했다.
- 고아 상태로 남는 UPLOADING `media_asset`(클라이언트가 끝내 confirm을 호출하지
  않는 경우)을 정리하는 배치가 없다 — TASK.md에 이미 명시된 제외 범위이며
  후속 이슈로 분리 필요.

### Infrastructure and resource limits

- 애플리케이션 런타임이 실제 AWS에 접속하려면 `QELLO_MEDIA_BUCKET` 환경변수와
  IAM Role(Task Role 등)이 필요한데 둘 다 이 저장소에 아직 없다
  (`docs/reports/infrastructure/gh-63-D-1.md` §5) — 별도 인프라 이슈 대상.
- LocalStack 컨테이너가 통합 테스트마다 새로 기동되어(Postgres 컨테이너와
  별도) 최초 실행 시 이미지 pull 시간이 추가된다. CI에서 이미지 캐시 전략을
  검토할 필요가 있다(이번 이슈 범위 밖).

### Database and migrations

- 신규 migration 없음 — `media_asset`/`media_attachment`는 V1에 이미 존재.
- `ct_media_attachment_preserves_content`/`ct_media_status_preserves_content`의
  `RAISE EXCEPTION` 메시지가 `ConstraintExceptionMapper.knownConstraints()`
  목록에 없어(계획 R-07), 서비스 사전 검증을 우회한 경로는
  `CommonErrorCode.CONFLICT`(범용 409)로만 응답된다 — `INT-008`로 실제 동작을
  확인했다. 서비스 경유 경로(`MediaAttachmentService.detach`)는 `ANS-DOM-009
  MEDIA_CONTENT_REQUIRED`로 명확하게 응답하므로 정상 사용 경로는 문제가
  없지만, 어떤 이유로든 서비스를 우회하는 코드가 추가되면 진단 정보가
  약해진다. 트리거 메시지에 trigger 이름을 포함시켜 매핑을 추가하는 방안은
  기존 트리거 4개(direction/answer 포함)를 함께 건드리는 더 큰 변경이라 이번
  이슈 범위에서는 보류를 권고한다.
- `fk_media_attachment_asset_owner`/`fk_media_attachment_post_owner`/
  `fk_media_attachment_answer_owner`는 DEFERRABLE이 아니어서 즉시(문장 단위)
  검증된다 — `INT-007`에서 확인.

### Concurrency and idempotency

- `MediaAssetRepository.transitionFromUploading`의 조건부 UPDATE(`WHERE id = :id
  AND status = 'UPLOADING'`)로 동시 confirm 경쟁을 방어했고 `INT-010`으로
  검증했다.
- `uq_media_asset_storage_key`가 최종 방어선이며(`INT-011`), 애플리케이션은
  `UUID.randomUUID()`로 채번해 실제 충돌 확률은 낮다.
- attach/detach 자체의 동시 경쟁(같은 media를 두 요청이 동시에 attach하는
  경우)은 이번 계획에 별도 시나리오로 포함하지 않았다 — `media_attachment`의
  PK가 `media_id`이므로 동시 attach 시도는 PK 충돌로 자연히 하나만 성공한다고
  예상되지만 별도 테스트로 확인하지 않았다(잔여 위험).

### Transactions and event ordering

- `MediaUploadService.issueUploadUrl`/`confirm`, `MediaAttachmentService.attach`/
  `detach` 모두 `@Transactional` 단일 메서드로 DB 쓰기가 원자적이다. presigned
  URL 발급 자체(S3 API 호출)는 DB transaction 커밋 여부와 무관하게 부수효과가
  없는 순수 조회성 호출이라 rollback돼도 안전하다.

### External APIs

- 실제 검증한 유일한 외부 API는 LocalStack S3(PutObject via presigned URL,
  HeadObject)다. 실제 AWS 자격 증명은 사용하지 않았다.
- S3 통신 실패(네트워크/5xx)는 `ANS-EXT-001 STORAGE_UNAVAILABLE`로 매핑했지만,
  이 경로 자체를 실패시키는 통합 테스트(예: LocalStack 강제 중단)는 이번
  계획에 포함하지 않았다 — 코드 리뷰로만 확인됨(잔여 위험, 우선순위 낮음).

### Failure recovery and reconciliation

- 존재하지 않는 객체에 대한 confirm은 예외 없이 REJECTED로 안전하게
  귀결된다(`INT-004`) — 재시도 가능한 사용자 흐름(다시 업로드 후 새
  presigned URL 요청)으로 자연스럽게 복구된다.
- UPLOADING 상태로 무기한 방치되는 자산의 정리는 이번 범위 밖(위 Application
  code 절 참고).

## 7. Regression and residual risk

- 기존 answer/direction/feed/question/account/safety/notification 전체
  통합·단위 테스트가 이번 변경 이후에도 전부 통과했다(`./gradlew check`,
  173개 테스트, 실패 0).
- 잔여 위험: R-07(트리거 메시지 매핑 공백), R-09(LocalStack-AWS 완전 동등성
  미보증), 고아 UPLOADING 정리 배치 부재, attach 동시 경쟁 전용 테스트 부재.
  모두 이번 이슈의 명시적 제외 범위이거나 우선순위 낮은 잔여 위험으로
  후속 이슈 후보다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-70-MEDIA-ASSET-SERVICE.md`
- CI run: 로컬 실행만 수행(이 세션에서 원격 CI 미실행)
- Related ADR: `docs/adr/0001-database-schema-ownership.md`,
  `docs/adr/0002-jpa-jdbc-boundary.md`
- PR: 아직 생성 전(이 보고서 작성 시점 기준)

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨(실제 AWS 스모크 테스트, attach 동시 경쟁, S3
      통신 실패 경로)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 — 아직 이슈 미생성, PR 리뷰
      시점에 사용자와 협의 필요
- [x] 실행 결과와 PR 설명이 일치함(PR 생성 시 이 보고서를 링크)
