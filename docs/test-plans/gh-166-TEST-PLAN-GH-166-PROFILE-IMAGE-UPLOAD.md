# Test Plan: TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD

> Created at: `2026-08-18T23:33:15+09:00`
> GitHub Issue: `#166`
> Status: Approved for implementation

## 1. Objective

일반 사용자가 프로필 이미지를 올리고 바꾸고 지울 수 있으며, 올리지 않은 사용자도
항상 볼 수 있는 기본 이미지를 받는 흐름을 검증한다.

가장 큰 위험은 네 가지다. 첫째, **남의 이미지를 자기 프로필로 붙이는 것** —
`media_asset`은 `owner_id`를 갖지만 프로필 참조가 그 소유권을 다시 확인하지 않으면
타인의 사진이 내 프로필로 노출된다. 둘째, **실체 없는 객체를 프로필로 확정하는 것** —
`UPLOADING`은 presigned PUT만 발급됐을 뿐 S3에 객체가 있다는 보장이 없고,
`REJECTED`는 검증에 실패한 자산이다. 이런 자산을 프로필로 받으면 조회 URL이 404를
가리킨다. 셋째, **기본 이미지 설정 누락이 조용히 통과하는 것** — 키가 비었는데 기동에
성공하면 프로필을 올리지 않은 모든 사용자의 조회가 깨진다. 넷째, **버킷 이름과 객체
키가 API 경계 밖으로 새는 것** — private 버킷의 내부 주소는 응답·오류·로그에 남으면
안 된다.

여기에 가입 경로의 트랜잭션 위험이 더해진다. `DeviceRegistrationService`는 계정
생성과 자격증명 발급을 한 트랜잭션으로 묶어 "로그인할 수 없는 계정"을 막고 있는데,
프로필 이미지 지정이 그 경계 밖으로 나가면 같은 종류의 부분 반영이 생긴다.

## 2. Scope

### Included

- `user_account.profile_image_media_id` 추가 마이그레이션(V22)과 `media_asset (id, owner_id)`
  복합 FK
- `NULL`을 "기본 이미지 사용"으로 해석하는 도메인 규칙
- 프로필 이미지 설정 시 소유자 일치 검증(애플리케이션과 DB 양쪽)
- 프로필 이미지 설정 시 `READY` 상태 검증
- `/api/v1/me/profile` 프로필 조회, 프로필 이미지 변경, 프로필 이미지 삭제
- `POST /api/v1/auth/devices`의 선택 항목 프로필 이미지 media id와 그 트랜잭션 경계
- `ObjectStoragePort`의 presigned GET 발급과 `qello.media.view-url-ttl`
- 프로필에 붙은 자산이 나중에 `DELETED`가 됐을 때의 기본 이미지 폴백
- 기본 이미지 객체 키 설정의 fail-fast 검증
- 응답·오류의 버킷 이름·객체 키 비노출
- 프로필 이미지 동시 변경 시 `AccountJpaEntity`의 낙관적 락 동작
- 인증 없는 접근 차단

### Excluded

- 이미지 내용 moderation. `FilterTargetType`은 `ANSWER`와 `NICKNAME`뿐이고 이미지
  판정기가 없다. `media_asset.moderation_status`는 기존 기본값을 그대로 둔다.
- 이미지 리사이즈, 썸네일, EXIF 제거
- CDN 도입과 캐시 정책
- 기본 이미지 객체를 운영 버킷에 적재하는 작업과 Terraform 변경. 통합 테스트는
  LocalStack 버킷에 테스트용 객체를 직접 넣어 검증한다.
- 로그인(`POST /api/v1/auth/token`) 경로 변경
- 프로필의 나머지 필드(닉네임·지역·로케일·타임존) 변경 API
- presigned PUT 발급과 `confirm` 자체의 재검증. 이미
  `TEST-PLAN-GH-70-MEDIA-ASSET-SERVICE`가 다뤘고 이 계획은 그 결과물을 **소비하는**
  경계만 본다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue `#166` | 프로필 이미지 없이 가입 가능, 기본 이미지 제공, 업로드→confirm→지정→삭제 흐름, 타인 자산·비 `READY` 자산 거부, presigned URL 만료와 키 비노출, 설정 누락 시 기동 실패 |
| `TASK.md` (`#166`) | `NULL` = 기본 이미지, `media_asset` 재사용, presigned GET 채택, 기본 이미지 객체 사전 배치 전제 |
| `src/main/resources/db/migration/V1__create_direction_communication_schema.sql` | `media_asset`의 `uq_media_asset_id_owner (id, owner_id)` — 복합 FK의 전제 |
| `MediaUploadService` | `READY` 전이는 `confirm`의 HeadObject·시그니처 검증을 통과한 자산만 도달한다 |
| `MediaAttachmentService` | 첨부는 `READY`만 허용한다는 기존 선례 |
| `AnswerErrorCode` | `MEDIA_NOT_FOUND`(404), `MEDIA_OWNER_MISMATCH`(403), `INVALID_MEDIA_STATUS`(409) |
| `AccountJpaEntity` | `@Version` 낙관적 락이 이미 있다 |
| `DeviceRegistrationService` | 계정 생성과 자격증명 발급이 한 트랜잭션 |
| `MediaStorageProperties` | 설정 누락을 compact constructor에서 예외로 막는 기존 방식 |
| `AGENTS.md` 3절 | `@DisplayName`, 클래스 헤더의 생성 시각과 source scenario |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 타인 소유 `media_asset`을 프로필로 지정 | 타인 사진이 내 프로필로 노출 | 중 | P0 | UNIT-005, INT-002, INT-010 |
| `UPLOADING`/`REJECTED`/`DELETED` 자산을 프로필로 확정 | 조회 URL이 없는 객체를 가리킴 | 중 | P0 | UNIT-006~008, INT-003 |
| 기본 이미지 키 설정 누락이 기동을 통과 | 미설정 사용자 전체의 프로필 조회 실패 | 중 | P0 | UNIT-014 |
| 버킷 이름·객체 키가 응답이나 오류에 노출 | private 버킷 내부 구조 유출 | 중 | P0 | UNIT-012, INT-003 |
| 가입 트랜잭션 부분 반영 | 계정만 있고 프로필이 없거나 그 반대 | 낮 | P0 | INT-005, INT-006 |
| V22 복합 FK가 기존 데이터와 충돌 | 마이그레이션 실패로 배포 중단 | 낮 | P0 | INT-001 |
| 프로필 이미지 동시 변경 | 나중 요청이 앞선 변경을 조용히 덮어씀 | 낮 | P1 | INT-008 |
| presigned GET TTL 미적용 또는 과다 | 만료 없는 URL이 외부에 남음 | 중 | P1 | UNIT-013, INT-011 |
| 인증 없이 프로필 접근 | 타인 프로필 열람 | 낮 | P1 | INT-009 |
| S3 장애가 도메인 예외로 변환되지 않음 | 500과 스택트레이스 노출 | 낮 | P1 | UNIT-015 |
| 프로필 이미지 삭제가 `media_asset`까지 삭제 | 다른 곳에 첨부된 자산 파괴 | 낮 | P1 | UNIT-017, INT-007 |
| 프로필에 붙은 자산이 나중에 `DELETED`가 됨 | 조회가 없는 객체를 가리키거나 500 | 낮 | P1 | UNIT-018, INT-013 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-001 | 프로필 이미지가 없는 `Account` | 유효한 media id로 프로필 이미지를 설정한다 | 새 인스턴스가 그 id를 갖고 나머지 필드는 보존된다 | P0 | A |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-002 | 프로필 이미지가 설정된 `Account` | 프로필 이미지를 해제한다 | 참조가 `null`이 되고 나머지 필드는 보존된다 | P0 | A |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-003 | 가입 입력 | `Account.createUser`를 호출한다 | 프로필 이미지 참조가 `null`이다 — 기본 이미지 상태 | P0 | A |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-004 | `Account` | `0` 또는 음수 media id로 프로필 이미지를 설정한다 | `AccountException(INVALID_ID)`로 거부한다 | P1 | A |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-005 | 다른 사용자가 소유한 `READY` 자산 | 프로필 이미지로 지정한다 | `MEDIA_OWNER_MISMATCH`(403)로 거부한다 | P0 | B |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-006 | 본인 소유 `UPLOADING` 자산 | 프로필 이미지로 지정한다 | `INVALID_MEDIA_STATUS`(409)로 거부한다 | P0 | B |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-007 | 본인 소유 `REJECTED` 자산 | 프로필 이미지로 지정한다 | `INVALID_MEDIA_STATUS`(409)로 거부한다 | P0 | B |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-008 | 본인 소유 `DELETED` 자산 | 프로필 이미지로 지정한다 | `INVALID_MEDIA_STATUS`(409)로 거부한다 | P0 | B |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-009 | 존재하지 않는 media id | 프로필 이미지로 지정한다 | `MEDIA_NOT_FOUND`(404)로 거부한다 | P0 | B |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-010 | 프로필 이미지 참조가 `null`인 계정 | 프로필을 조회한다 | 설정된 기본 이미지 키로 presigned GET을 발급한다 | P0 | B |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-011 | 프로필 이미지가 설정된 계정 | 프로필을 조회한다 | 해당 자산의 `storageKey`로 presigned GET을 발급한다 | P0 | B |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-012 | 프로필 응답 객체 | 선언된 필드를 전수 확인한다 | 버킷 이름과 객체 키에 해당하는 필드가 하나도 없다 | P0 | B |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-013 | `qello.media.view-url-ttl` 설정값 | 프로필을 조회한다 | 포트에 업로드 TTL이 아니라 조회 TTL이 전달되고 만료 시각이 응답에 담긴다 | P1 | B |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-014 | 기본 이미지 키가 `null`·빈 문자열·공백인 설정 | properties를 생성한다 | 예외로 기동을 실패시킨다 | P0 | B |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-015 | presigned GET 발급 중 `SdkException` | 프로필을 조회한다 | `STORAGE_UNAVAILABLE`로 변환하고 원인 예외를 감싼다 | P1 | B |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-016 | 프로필 이미지 media id가 없는 가입 요청 | 요청을 검증한다 | 검증을 통과한다 — 선택 항목이다 | P0 | C |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-017 | 프로필 이미지가 설정된 계정 | 프로필 이미지를 삭제한다 | 참조만 해제하고 `media_asset` 삭제나 상태 전이를 호출하지 않는다 | P1 | B |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-018 | 프로필에 붙은 자산이 `DELETED`인 계정 | 프로필을 조회한다 | 오류가 아니라 기본 이미지 키로 presigned GET을 발급한다. 프로필 참조 자체는 해제하지 않는다 | P1 | B |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-INT-001 | Flyway, PostgreSQL | 기존 마이그레이션이 적용된 스키마 | V22까지 마이그레이션한다 | `profile_image_media_id` 컬럼이 nullable로 생기고, `media_asset (id, owner_id)` 복합 FK가 존재하며, 기존 행은 모두 `NULL` | 컨테이너 종료 |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-INT-002 | PostgreSQL | 사용자 A·B와 B 소유 자산 | A의 행에 B 자산 id를 직접 `UPDATE` 한다 | FK 위반으로 실패한다 — 애플리케이션을 우회해도 막힌다 | 트랜잭션 롤백 |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-INT-003 | HTTP, PostgreSQL, LocalStack S3 | 인증된 사용자 | 업로드 URL 발급 → 객체 PUT → `confirm` → 프로필 이미지 지정 → 프로필 조회 | 조회 응답이 만료 있는 presigned GET URL을 반환하고, 응답 본문에 버킷 이름과 객체 키가 없다 | 버킷 객체와 DB 정리 |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-INT-004 | HTTP, PostgreSQL, LocalStack S3 | 버킷에 기본 이미지 객체를 미리 넣는다 | 프로필 이미지 없이 가입한 뒤 프로필을 조회한다 | 기본 이미지의 presigned GET URL을 반환하고 그 URL로 객체를 받을 수 있다 | 버킷 객체와 DB 정리 |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-INT-005 | HTTP, PostgreSQL, LocalStack S3 | 본인 소유 `READY` 자산 | 프로필 이미지 media id를 포함해 가입한다 | 계정·자격증명·프로필 참조가 모두 커밋된다 | DB 정리 |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-INT-006 | HTTP, PostgreSQL | 타인 소유 또는 비 `READY` 자산 id | 그 id를 포함해 가입한다 | 요청이 거부되고 `user_account`·`device_credential` 어느 쪽에도 행이 남지 않는다 | DB 정리 |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-INT-007 | HTTP, PostgreSQL, LocalStack S3 | 프로필 이미지가 설정된 사용자 | 프로필 이미지를 삭제하고 다시 조회한다 | 기본 이미지 URL로 돌아가고, `media_asset` 행은 `READY`로 남아 있다 | DB 정리 |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-INT-008 | HTTP, PostgreSQL | 본인 소유 `READY` 자산 두 건 | 같은 계정의 프로필 이미지를 동시에 변경한다 | 한 요청만 성공하고 나머지는 낙관적 락 충돌로 거절되며, 최종 상태가 성공한 쪽과 일치한다 | DB 정리 |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-INT-009 | HTTP, Spring Security | 인증 없는 요청 | 프로필 조회·변경·삭제를 호출한다 | 모두 401로 차단된다 | 없음 |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-INT-010 | HTTP, PostgreSQL | 사용자 A로 인증, B 소유 `READY` 자산 | A가 B의 자산을 프로필로 지정한다 | 403 `MEDIA_OWNER_MISMATCH`이고 A의 프로필은 바뀌지 않는다 | DB 정리 |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-INT-011 | LocalStack S3 | 발급된 presigned GET URL | URL로 객체를 받고, TTL 경과 후 다시 받는다 | 최초에는 성공하고 만료 후에는 실패한다 | 버킷 객체 정리 |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-INT-012 | OpenAPI 생성 | 애플리케이션 기동 | 스펙을 생성한다 | 신규 endpoint 3종이 인증 요구와 오류 응답을 포함해 문서화된다 | 없음 |
| TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-INT-013 | HTTP, PostgreSQL, LocalStack S3 | 프로필 이미지가 설정된 사용자 | 그 자산을 `DELETED`로 전이시킨 뒤 프로필을 조회한다 | 500이 아니라 기본 이미지 URL을 반환하고, `profile_image_media_id`는 그대로 남는다 | DB와 버킷 정리 |

## 7. Cross-cutting scenarios

### Database and transactions

- V22는 컬럼 추가와 FK 추가만 한다. 기존 행 변환이 없어야 하고, 되돌릴 때 데이터
  손실이 없어야 한다(INT-001).
- 복합 FK `(profile_image_media_id, id) → media_asset (id, owner_id)`가 소유권
  불변식의 최종 방어선이다. 애플리케이션 검증이 뚫려도 DB가 막는다(INT-002).
- 가입은 계정·자격증명·프로필 참조가 한 트랜잭션이다. 어느 하나가 실패하면 전부
  롤백된다(INT-005, INT-006).
- 프로필 이미지 삭제는 참조 해제만 한다. `media_asset` 행의 상태를 바꾸지 않는다
  (UNIT-017, INT-007).

### Concurrency and idempotency

- `AccountJpaEntity`에 이미 `@Version`이 있다. 같은 계정의 프로필 이미지 동시 변경은
  뒤늦은 쪽이 거절되어야 하고, 조용한 덮어쓰기가 있으면 안 된다(INT-008).
- 같은 media id로 프로필 이미지를 두 번 설정하는 것은 멱등해야 한다. 두 번째 호출이
  오류가 되지 않고 상태도 그대로여야 한다(INT-008에 포함).
- 프로필 조회는 부수효과가 없다. 호출할 때마다 새 presigned URL이 나오는 것은
  정상이며, 그것이 상태 변경을 뜻하지 않는다.

### External APIs

- S3는 LocalStack으로 대체한다. 실제 AWS 자격 증명을 쓰지 않는다.
- presigned GET 발급 실패(`SdkException`)는 `STORAGE_UNAVAILABLE`로 변환한다
  (UNIT-015). 원인 예외 메시지를 클라이언트 응답에 그대로 싣지 않는다.
- presigned URL의 만료는 서버 `Clock`이 아니라 SDK가 서명 시점 기준으로 계산한다.
  테스트는 TTL을 짧게 설정해 실제 만료를 관찰한다(INT-011).

### Failure recovery and reconciliation

- 프로필에 붙은 자산이 나중에 `DELETED`로 바뀌면 조회는 **기본 이미지로 폴백한다**
  (UNIT-018, INT-013). 프로필 참조를 그때 지우지는 않는다. 읽기 경로가 쓰기를 하면
  조회가 낙관적 락 충돌을 일으킬 수 있고, 자산이 되살아나는 경로가 생겼을 때 원래
  참조를 잃는다. 폴백은 읽는 쪽의 해석으로만 처리한다.
- 이 폴백은 설정 시점의 `READY` 검증(UNIT-006~008)을 완화하지 않는다. **`DELETED`
  자산을 프로필로 새로 지정하는 것은 여전히 거부된다.** 이미 붙어 있던 자산이 나중에
  삭제된 경우만 폴백 대상이다.
- 기본 이미지 객체 자체가 버킷에 없는 경우, presigned GET은 발급되지만 그 URL은
  404를 가리킨다. presigned 발급은 객체 존재를 확인하지 않기 때문이다. 이 한계를
  보고서에 명시하고, 운영에서는 기본 이미지 적재를 배포 전제 조건으로 둔다.

## 8. Test data and isolation

- Fixtures: 기존 `AccountPersistenceIntegrationTest`와 `MediaAssetStorageIntegrationTest`의
  준비 방식을 재사용한다. 사용자는 `region_code` 카탈로그에 있는 값으로 만든다.
- Database isolation: `PostgisContainerIntegrationTestSupport`의 Testcontainers
  PostgreSQL. 시나리오별 트랜잭션 롤백 또는 명시적 정리.
- Object storage: `LocalStackContainerIntegrationTestSupport`를 확장한다. 기본 이미지
  객체는 `@BeforeAll`에서 테스트 버킷에 직접 PUT 한다.
- Clock/randomness: 서버 `Clock`은 고정 `Clock`으로 주입한다. `storageKey`의 UUID는
  검증 대상이 아니므로 값 자체를 단언하지 않는다.
- External API doubles: 단위 테스트는 `ObjectStoragePort`를 테스트 대역으로 바꾼다.
  통합 테스트만 LocalStack을 쓴다.
- Cleanup: 각 통합 테스트는 자신이 넣은 버킷 객체를 지운다. 컨테이너는 클래스 단위로
  공유한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다. LocalStack이 발급하는 임시 키만
사용하며 테스트 코드에도 하드코딩하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | A — 스키마와 도메인 | `src/main/resources/db/migration/V22__*.sql`, `account/domain/Account.java`, `account/repository/jpa/*`, `src/test/java/com/dnd/qello/account/domain/AccountProfileImageTest.java`, `src/integrationTest/java/com/dnd/qello/ProfileImageSchemaIntegrationTest.java` | UNIT-001~004, INT-001~002 | `./gradlew test --tests '*AccountProfileImage*'`, `./gradlew integrationTest --tests '*ProfileImageSchema*'` |
| 2 | B — 서비스와 저장소 포트 | `account/service/**`, `answer/service/port/ObjectStoragePort.java`, `answer/service/port/S3ObjectStoragePort.java`, `answer/config/MediaStorageProperties.java`, `src/test/java/com/dnd/qello/account/service/**`, `src/integrationTest/java/com/dnd/qello/ProfileImageStorageIntegrationTest.java` | UNIT-005~015, UNIT-017~018, INT-011 | `./gradlew test --tests '*ProfileImage*'`, `./gradlew integrationTest --tests '*ProfileImageStorage*'` |
| 3 | C — web과 가입 연결 | `account/web/**`, `auth/web/DeviceRegistrationRequest.java`, `auth/service/DeviceRegistrationService.java`, `src/test/java/com/dnd/qello/account/web/**`, `src/integrationTest/java/com/dnd/qello/ProfileImageApiIntegrationTest.java` | UNIT-016, INT-003~010, INT-012~013 | `./gradlew test --tests '*ProfileImageWeb*'`, `./gradlew integrationTest --tests '*ProfileImageApi*'` |
| 4 | 전체 | 없음 | 전체 | `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check` |

소유 파일이 겹치지 않는다. `S3ObjectStoragePort`와 `MediaStorageProperties`는 B만
수정하고, A와 C는 읽기만 한다.

**확정된 결정 (2026-08-18)**

1. **`DELETED` 자산은 기본 이미지로 폴백한다.** 프로필 참조는 지우지 않고 읽는 쪽에서만
   해석한다. 새로 지정하는 것은 여전히 거부된다(7절).
2. **조회 URL TTL은 별도 설정 `qello.media.view-url-ttl`을 두고 `PT5M`으로 한다.**
   업로드 TTL이 `PT10M`인 것은 최대 10MB PUT 하나가 느린 회선에서 끝날 시간을 재기
   때문이고, 조회 URL이 살아 있어야 하는 시간은 응답 수신부터 렌더링까지다. private
   버킷의 presigned GET은 그 객체에 대한 bearer 자격증명이므로 수명이 곧 노출 창이다.
   1분대로 더 줄이지 않은 것은 화면을 잠깐 벗어났다 돌아오는 흔한 동작에서 이미지가
   깨지고 재조회 트래픽만 늘기 때문이다. 업로드 TTL을 재사용하지 않는 이유는, 나중에
   큰 파일을 위해 업로드 TTL을 늘릴 때 조회 URL 수명이 함께 늘어나 성능 튜닝의
   부수효과로 보안 속성이 바뀌는 것을 막기 위해서다.
3. **경로는 `/api/v1/me/profile`이다.** 프로필 조회, 프로필 이미지 변경, 프로필 이미지
   삭제 세 endpoint가 이 경로 아래 놓인다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer: `@tkv00`
- Decision: Approved for implementation
- Approved at: `2026-08-18T23:42:07+09:00`
- 승인 범위: 9절의 확정된 결정 3건(기본 이미지 폴백, `qello.media.view-url-ttl`
  `PT5M`, `/api/v1/me/profile`)을 포함한 계획 전체.

## 12. Handoff

**선행 조건.** 이 계획의 시나리오는 아직 존재하지 않는 동작을 검증한다.
`user_account.profile_image_media_id`, 프로필 API, presigned GET이 구현되기 전에
`/harness-test-run`을 실행하면 모든 시나리오가 실패한다. 구현이 각 executor의
소유 범위만큼 끝난 뒤에 해당 순서의 테스트를 실행한다.

| Order | Executor | Scenario IDs | Verification |
| --- | --- | --- | --- |
| 1 | A — 스키마와 도메인 | UNIT-001~004, INT-001~002 | `./gradlew test --tests '*AccountProfileImage*'`, `./gradlew integrationTest --tests '*ProfileImageSchema*'` |
| 2 | B — 서비스와 저장소 포트 | UNIT-005~015, UNIT-017~018, INT-011 | `./gradlew test --tests '*ProfileImage*'`, `./gradlew integrationTest --tests '*ProfileImageStorage*'` |
| 3 | C — web과 가입 연결 | UNIT-016, INT-003~010, INT-012~013 | `./gradlew test --tests '*ProfileImageWeb*'`, `./gradlew integrationTest --tests '*ProfileImageApi*'` |
| 4 | 전체 | 전체 | `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check` |

소유 파일은 9절의 표를 따른다. 실패는 구현 문제와 테스트 환경 문제로 구분해
기록하고, 환경 문제로 판단한 경우에도 실패한 명령·오류 요약·재현 조건·미검증
범위·남은 위험을 보고서에 남긴다(`AGENTS.md` 3절).

보고서는 `templates/test-report.md`에서 생성한다.

## 13. 구현 중 확인된 계획 대비 차이

구현하면서 계획이 전제한 것과 코드가 다른 지점이 드러났다. 승인된 계획을 조용히 바꾸지
않기 위해 여기에 남긴다.

1. **가입 요청의 프로필 이미지 필드는 구현할 수 없어 뺐다(UNIT-016, INT-005, INT-006
   폐기).** `media_asset.owner_id`는 `user_account.id`를 FK로 참조하므로 자산은 소유자
   계정 행이 있어야 존재할 수 있고, 업로드 endpoint는 `/api/**` 체인의
   `anyRequest().authenticated()` 아래에 있어 토큰이 필요하다. 가입 전에는 토큰이 없으니
   가입 시점에 넘길 수 있는 유효한 media id가 원리적으로 존재하지 않는다. 실제 순서는
   기기 등록 → 업로드 → confirm → 프로필 지정이며, 사용자 관점의 "가입할 때 사진 올리기"는
   온보딩 화면에서 그대로 성립한다.
2. **남의 자산은 403이 아니라 404로 거절한다(UNIT-005, INT-010 수정).** 403으로 구분하려면
   소유권 없는 `findById`로 존재 여부를 먼저 확인해야 하는데, 그 메서드에는 "사용자 입력으로
   직접 호출하지 않는다"는 계약이 명시돼 있다. 구분하면 순차 증가하는 media id를 훑어
   "존재하지만 내 것이 아니다"를 알아낼 수 있는 열거 오라클이 된다.
   `MediaUploadService.confirm`도 같은 이유로 두 경우를 구분하지 않는다.
3. **INT-012는 새로 쓰지 않았다.** 기존 `OpenApiSpecificationIntegrationTest`가
   `docs/api/openapi.json`을 재생성하고 커밋본과 다르면 실패시킨다. 신규 endpoint 2개가
   그 경로로 스펙에 반영됐다.
4. **INT-008(동시 변경 낙관적 락)은 실행하지 않았다.** P1이며 미실행 사실을 보고서에
   남긴다. 근거와 남은 위험은 보고서 7절에 있다.
