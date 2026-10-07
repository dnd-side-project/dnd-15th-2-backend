# Test Plan: TEST-PLAN-GH-325-EXIF-STRIP

> Created at: `2026-10-07T23:07:21+09:00`
> GitHub Issue: `#325`
> Status: Approved (2026-10-07), Revision 1 Approved (2026-10-08)

## 1. Objective

confirm을 통과해 READY가 된 이미지에서 위치정보가 사라지고, 다른 사용자가 받는 파일이 처리본인지 검증한다.

실패하면 다음 일이 생긴다.

- 질문글·답변·프로필 이미지로 올린 사람의 정확한 위치가 다른 사용자에게 나간다.
- READY가 된 뒤 원본을 다시 올리면 GPS가 되살아난다.
- 메타데이터를 지우다 화질이 떨어지거나 사진이 눕는다.
- 일시적인 S3 장애로 멀쩡한 이미지가 REJECTED가 되어 다시 올려야 한다.

## 2. Scope

### Included

- EXIF 제거 모듈(이하 `ExifStripper`, ASSUMED). JPEG·PNG 무손실 제거, 허용 목록 방식, Orientation·ICC 유지
- `MediaAsset`의 `exifStripped` 필드와 READY 전이 규칙
- `JdbcMediaAssetRepository`의 `exif_stripped`·`storage_key` 저장과 조회
- `ObjectStoragePort`·`S3ObjectStoragePort`에 추가하는 전체 읽기와 쓰기
- `MediaUploadService.issueUploadUrl`의 upload key, `confirm`의 제거·저장·전이 흐름
- 프로필·피드 조회 URL이 serving key를 가리키는지
- 위 변경으로 깨지는 기존 테스트의 이전(MIGRATE)

### Excluded

- Lambda 전환(B안), 이미지 moderation, 썸네일·리사이즈
- 원본 이전 버전 삭제와 버킷 lifecycle 변경
- 배포 전에 READY가 된 기존 이미지(TASK.md 결정: 별도 처리 없음)
- 부하 테스트. t3.small 메모리 위험은 4절 R9에 기록만 한다.
- SSE-KMS 암호화 동작. LocalStack이 재현하지 않는다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #325 | READY 이미지에 위치정보가 없고 `exif_stripped = true`다. |
| GitHub Issue #325 | READY 뒤 같은 presigned URL로 다시 올려도 조회 이미지는 바뀌지 않는다. |
| GitHub Issue #325 | 처리 실패 이미지는 REJECTED다. Orientation이 유지된다. |
| GitHub Issue #325 | 로그에 storage key와 좌표가 나오지 않는다. |
| TASK.md | 무손실·허용 목록 방식. Orientation과 ICC 프로파일만 남긴다. 모듈은 S3·Spring에 의존하지 않는다. |
| TASK.md | `byte_size`·`checksum`은 원본 값 유지. 기존 READY는 별도 처리 없음. |
| `MediaUploadService.confirm` | UPLOADING이 아니면 저장소를 다시 부르지 않고 현재 상태를 돌려준다(멱등). S3 I/O는 트랜잭션 밖에서 한다. |
| `application.yml` `qello.media` | 최대 10MiB, presigned PUT 수명 10분, JPEG·PNG만 허용 |
| V1 스키마 `media_asset` | `exif_stripped BOOLEAN NOT NULL DEFAULT FALSE`, `storage_key` UNIQUE. 스키마는 바꾸지 않는다. |
| infra `dev/storage` | 버킷 versioning이 켜져 있다. 덮어쓰거나 지워도 이전 버전이 남는다. |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| R1. GPS가 다른 위치에 남는다(XMP, APP13, MPF 보조 이미지, EOI 뒤 데이터, PNG 텍스트 청크) | 높음 | 중간 | P0 | UNIT-001~004, UNIT-008~009 |
| R2. READY 뒤 원본을 다시 PUT하면 서빙 이미지가 바뀐다 | 높음 | 중간 | P0 | INT-003 |
| R3. 제거 과정에서 픽셀이 바뀐다 | 중간 | 낮음 | P0 | UNIT-005 |
| R4. Orientation이 사라져 사진이 눕는다 | 중간 | 높음(휴대폰 사진) | P0 | UNIT-001, UNIT-007, INT-001 |
| R5. 깨진 파일이 예외 없이 통과하거나 서버 오류(500)가 된다 | 중간 | 중간 | P0 | UNIT-010~011, UNIT-017, INT-005 |
| R6. S3 일시 장애로 멀쩡한 이미지가 REJECTED가 된다 | 중간 | 낮음 | P0 | UNIT-018 (H1) |
| R7. 동시 confirm이 서로 다른 결과를 만든다 | 중간 | 낮음 | P0 | INT-006 |
| R8. storage key가 로그나 예외 메시지에 나온다 | 중간 | 낮음 | P1 | UNIT-010, INT-007 |
| R9. 10MiB 이미지 여러 장을 동시에 처리하면 t3.small 메모리가 부족하다 | 중간 | 낮음 | 범위 밖 | 측정 안 함. 응답 복사·출력 버퍼·SDK 요청 복사가 겹쳐 confirm 한 건이 이미지 크기의 약 4~5배를 잡는다(Revision 1에서 정정). UNIT-012는 출력이 입력보다 커지지 않는 것만 확인한다 |
| R10. 기존 테스트가 서명 바이트만 있는 가짜 이미지를 써서 confirm이 REJECTED로 바뀐다 | 낮음 | 확정 | P0 | 9절 MIGRATE |
| R11. 모듈이 Spring·AWS SDK에 의존해 B안으로 옮길 수 없다 | 낮음 | 낮음 | P1 | UNIT-013 |

## 5. Unit scenarios

`ExifStripper`는 `byte[]`와 형식을 받아 `byte[]`를 돌려준다(ASSUMED). 지원하지 않거나 깨진 입력은 전용 예외(이하 `UnsupportedImageException`, ASSUMED)를 던진다.

출력 검증에는 **테스트 쪽 파서**(`ExifTestImages`)를 쓴다. production 모듈의 파서로 자기 결과를 검증하지 않는다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-001 | APP1 Exif에 GPS IFD, Make·Model·DateTime, IFD1 썸네일, Orientation=6이 있는 JPEG | 제거한다 | 결과의 APP1 Exif는 하나뿐이고, IFD0에 0x0112(값 6)만 있으며 0x8825(GPS 포인터)·IFD1이 없다 | P0 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-002 | GPS를 담은 APP1 XMP, APP13, COM, APP3~APP12·APP15 세그먼트가 있는 JPEG | 제거한다 | 결과에 이 세그먼트가 하나도 없다 | P0 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-003 | APP0 JFIF, APP2 ICC_PROFILE, APP2 MPF, APP14 Adobe가 있는 JPEG | 제거한다 | APP0·ICC_PROFILE·APP14는 원래 바이트 그대로 남고 MPF는 없다 | P0 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-004 | EOI 뒤에 GPS EXIF가 든 두 번째 JPEG이 붙은 파일 | 제거한다 | 결과가 첫 EOI(`FF D9`)에서 끝난다 | P0 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-005 | UNIT-001~004의 JPEG과 UNIT-008의 PNG | 제거 전후를 ImageIO로 디코드한다 | 가로·세로와 모든 픽셀 값이 같다 | P0 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-006 | 메타데이터가 없는 JPEG(APP0만) | 제거한다. 결과를 한 번 더 제거한다 | 두 번 모두 입력과 바이트가 같다 | P1 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-007 | Orientation이 big-endian(MM)·little-endian(II)으로 각각 3·6·8, 1, 없음, 잘못된 값(0, 9)인 JPEG | 제거한다 | 3·6·8은 같은 값으로 남는다. 1·없음·잘못된 값이면 APP1 Exif 자체가 없다 | P1 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-008 | eXIf(GPS·Orientation 6), tEXt, iTXt(XMP), zTXt, tIME, iCCP, pHYs 청크가 있는 PNG | 제거한다 | IHDR이 처음, IEND가 마지막이다. eXIf는 Orientation만 담고 CRC가 맞다. iCCP·pHYs는 남고 텍스트 청크와 tIME은 없다 | P0 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-009 | IEND 뒤에 데이터가 붙은 PNG. 메타데이터가 없는 PNG | 제거한다 | 앞 파일은 IEND에서 끝난다. 뒤 파일은 입력과 바이트가 같다 | P1 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-010 | 세그먼트 길이가 파일 끝을 넘는 JPEG, 길이 필드가 2 미만인 JPEG, SOS가 없는 JPEG, EOI가 없는 JPEG | 제거한다 | 모두 `UnsupportedImageException`이고, 예외 메시지에 입력 바이트 내용이 들어 있지 않다 | P0 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-011 | 청크 길이가 파일 끝을 넘는 PNG, IHDR이 처음이 아닌 PNG, IEND가 없는 PNG | 제거한다 | 모두 `UnsupportedImageException` | P0 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-012 | 이 표의 모든 정상 입력 | 제거한다 | 결과 길이가 입력 길이 이하다 | P1 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-013 | production 클래스 | ArchUnit으로 검사한다 | `ExifStripper`와 그 예외가 `org.springframework..`, `software.amazon..`에 의존하지 않는다 | P1 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-014 | UPLOADING 자산 | serving key로 READY 전이한다. 다른 자산은 REJECTED 전이한다 | READY 자산은 `exifStripped = true`, `storageKey`가 serving key다. REJECTED 자산은 `exifStripped = false`다. UPLOADING이 아닌 자산의 READY 전이는 기존처럼 거부된다 | P0 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-015 | READY이면서 `exifStripped = false`인 저장값(배포 전 데이터) | `MediaAsset.restore` | 예외 없이 복원된다 | P1 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-016 | fake 저장소의 upload key에 GPS JPEG이 있다 | confirm | READY, `exifStripped = true`, `storageKey` = serving key다. serving key에는 UNIT-001 기준을 만족하는 바이트가 canonical Content-Type으로 저장된다. upload key 객체는 그대로다 | P0 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-017 | 서명은 맞지만 구조가 깨진 JPEG | confirm | REJECTED이고 쓰기 호출이 0회다 | P0 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-018 | fake 저장소가 전체 읽기 또는 쓰기에서 `STORAGE_UNAVAILABLE`을 던진다 | confirm 후, 장애를 걷고 다시 confirm | 첫 호출은 `STORAGE_UNAVAILABLE`이 전파되고 자산은 UPLOADING이다. 두 번째 호출은 READY다(H1) | P0 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-019 | HeadObject 크기는 맞지만 전체 읽기 결과 길이가 `byte_size`와 다르다(그 사이 객체가 바뀜) | confirm | REJECTED이고 쓰기 호출이 0회다 | P1 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-020 | 이미 READY인 자산 | confirm | HeadObject·전체 읽기·쓰기 호출이 모두 0회이고 같은 결과를 돌려준다 | P1 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-021 | `uploads/`로 시작하는 upload key, 배포 전 형식(`media/`)의 upload key | serving key를 만든다 | 둘 다 upload key와 다르고, 같은 입력에는 항상 같은 값이 나온다(H2) | P1 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-022 | 정상 발급 요청 | issueUploadUrl | storage key가 `uploads/{ownerId}/`로 시작한다(H2) | P1 | Executor A |

## 6. Integration scenarios

새 클래스 `ExifStripIntegrationTest`는 `LocalStackContainerIntegrationTestSupport`를 상속한다. 업로드는 실제 presigned PUT으로 한다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-325-EXIF-STRIP-INT-001 | MediaUploadService → S3(LocalStack) → JDBC | GPS·Orientation 6이 든 JPEG을 presigned PUT | confirm | DB 행이 READY, `exif_stripped = true`, `storage_key`가 serving key이고 upload key와 다르다. serving 객체가 UNIT-001 기준을 만족한다. `byte_size`·`checksum`은 원본 값이다 | `@BeforeEach` 행 삭제 |
| TEST-PLAN-GH-325-EXIF-STRIP-INT-002 | 같음 | eXIf·tEXt·iTXt가 든 PNG를 presigned PUT | confirm | serving 객체가 UNIT-008 기준을 만족한다 | 같음 |
| TEST-PLAN-GH-325-EXIF-STRIP-INT-003 | presigned PUT → confirm → 재PUT | INT-001 흐름으로 READY | 같은 presigned URL(유효 시간 안)로 GPS 원본을 다시 PUT하고 confirm을 다시 호출한다 | confirm은 READY를 그대로 돌려준다. serving 객체 바이트가 처음 처리본과 같다 | 같음 |
| TEST-PLAN-GH-325-EXIF-STRIP-INT-004 | confirm → ProfileService → presigned GET | INT-001 흐름으로 READY | 프로필로 지정하고 프로필을 조회한 뒤 받은 URL로 HTTP GET | 200이고 본문에 GPS 포인터·XMP·GPS 청크가 없다 | 같음 |
| TEST-PLAN-GH-325-EXIF-STRIP-INT-005 | 같음 | 서명만 맞고 구조가 깨진 JPEG을 presigned PUT | confirm | REJECTED, `exif_stripped = false`이고 serving key에 객체가 없다 | 같음 |
| TEST-PLAN-GH-325-EXIF-STRIP-INT-006 | 동시 confirm(기존 `MediaAssetStorageIntegrationTest.concurrentConfirmIsIdempotent` 확장) | GPS JPEG을 presigned PUT | 두 스레드가 동시에 confirm | 두 결과가 같은 READY와 같은 serving key다. DB 전이는 한 번이고 serving 객체는 UNIT-001 기준을 만족한다 | 같음 |
| TEST-PLAN-GH-325-EXIF-STRIP-INT-007 | 로그 | `OutputCaptureExtension` | INT-001과 INT-005 흐름 실행 | 출력에 upload key, serving key, 버킷 이름이 없다 | 없음 |
| TEST-PLAN-GH-325-EXIF-STRIP-INT-008 | confirm → 첨부 → feed 목록 URL | GPS JPEG을 confirm으로 READY로 만들어 본문 있는 질문글에 첨부 | 발신자로 `GET /posts` 후 `media[0].url`로 HTTP GET | 200이고 본문이 serving 객체와 같으며 GPS가 없다 | 기존 클래스의 정리 방식 |
| TEST-PLAN-GH-325-EXIF-STRIP-INT-009 | JDBC 매핑 | READY·`exif_stripped = false` 행을 JDBC로 삽입(배포 전 데이터) | `findById` | 예외 없이 `exifStripped = false`로 읽힌다 | `@BeforeEach` 행 삭제 |

## 7. Cross-cutting scenarios

### Database and transactions

- confirm은 `NOT_SUPPORTED`로 트랜잭션 밖에서 S3를 읽고 쓴다. 상태 전이만 짧은 트랜잭션에서 한다. 이 구조를 바꾸지 않는다.
- `transitionFromUploading`은 `status`와 함께 `storage_key`, `exif_stripped`를 조건부로 바꾼다. INT-001·INT-006이 결과 행을 확인한다.
- 스키마 마이그레이션이 없다. `storage_key` UNIQUE는 serving key가 upload key마다 다르므로 충돌하지 않는다.

### Concurrency and idempotency

- 같은 upload key에서 serving key와 처리 결과가 항상 같다. 동시 confirm 두 건이 같은 key에 같은 바이트를 쓰고 전이는 하나만 이긴다(INT-006).
- 버킷 versioning 때문에 동시·재시도 쓰기는 serving key에 이전 버전을 더 남긴다. 내용이 같아 위험은 없고 검증하지 않는다.

### External APIs

- 단위 테스트는 fake `ObjectStoragePort`만 쓴다. 통합 테스트는 LocalStack S3만 쓰고 실제 AWS를 부르지 않는다.
- 이미지 생성과 비교에는 JDK `ImageIO`만 쓴다. 새 테스트 의존성을 추가하지 않는다.

### Failure recovery and reconciliation

- 이미지 형식 문제는 REJECTED, S3 장애는 503과 UPLOADING 유지로 나눈다(H1, UNIT-017·018).
- serving key 쓰기 뒤 DB 전이 전에 실패하면 serving 객체만 남는다. 재시도가 같은 key에 덮어쓰므로 정리 작업은 두지 않는다.
- REJECTED·UPLOADING 자산의 upload key 원본 정리는 범위 밖이다(기존 고아 업로드와 같다).

## 8. Test data and isolation

- Fixtures: 테스트 이미지는 코드로 만든다. ImageIO로 작은 JPEG·PNG를 만들고 세그먼트·청크를 바이트로 끼워 넣는다. 바이너리 파일은 커밋하지 않는다.
- GPS 값은 실제 장소가 아닌 더미 값(위도 1도, 경도 2도)을 쓴다. 문자열 좌표를 남기지 않는다.
- 소스 세트가 서로의 클래스를 보지 못하므로 `ExifTestImages`를 `src/test`와 `src/integrationTest`에 각각 둔다. 통합 쪽은 INT에 필요한 JPEG·PNG와 검사 함수만 둔다.
- Database isolation: 기존 media 통합 테스트처럼 `@BeforeEach`에서 `media_attachment`, `media_asset` 등 fixture 행을 FK 순서대로 지운다.
- Clock/randomness: 발급 시각은 기존 테스트처럼 고정값을 쓴다. key의 UUID는 고정하지 않고 규칙(prefix, upload key와 다름)만 검사한다.
- External API doubles: 단위는 수동 fake port(전체 읽기·쓰기 실패 주입 가능), 통합은 LocalStack.
- Cleanup: DB는 `@BeforeEach`에서 지운다. LocalStack 객체는 key가 매번 달라 지우지 않는다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

production 구현은 TASK.md의 Feature executor가 먼저 한다(`ExifStripper`, `MediaAsset`, `JdbcMediaAssetRepository`, `ObjectStoragePort`, `S3ObjectStoragePort`, `MediaUploadService`). Executor A·B는 아래 테스트 파일만 소유한다.

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Executor A (unit) | 신규 `src/test/java/com/dnd/qello/answer/ExifStripperTest.java`, 신규 `src/test/java/com/dnd/qello/answer/ExifTestImages.java` | UNIT-001~013 | `./gradlew test --tests '*ExifStripperTest'` |
| 1 | Executor A (unit) | `src/test/java/com/dnd/qello/answer/MediaAssetTest.java` | UNIT-014~015, MIGRATE(READY 전이 호출) | `./gradlew test --tests '*MediaAssetTest'` |
| 1 | Executor A (unit) | `src/test/java/com/dnd/qello/answer/MediaUploadServiceTest.java` | UNIT-016~022, MIGRATE(fake port에 전체 읽기·쓰기 추가, READY 기대 케이스를 실제 이미지 바이트로 교체) | `./gradlew test --tests '*MediaUploadServiceTest'` |
| 1 | Executor A (unit) | `src/test/java/com/dnd/qello/answer/MediaAttachmentServiceTest.java` | MIGRATE(READY 전이 호출만) | `./gradlew test --tests '*MediaAttachmentServiceTest'` |
| 2 | Executor B (integration) | 신규 `src/integrationTest/java/com/dnd/qello/ExifStripIntegrationTest.java`, 신규 `src/integrationTest/java/com/dnd/qello/ExifTestImages.java` | INT-001~005, INT-007, INT-009 | `./gradlew integrationTest --tests '*ExifStripIntegrationTest'` |
| 2 | Executor B (integration) | `src/integrationTest/java/com/dnd/qello/MediaAssetStorageIntegrationTest.java` | INT-006, MIGRATE(READY 기대 케이스의 서명+0 바이트를 실제 JPEG으로 교체) | `./gradlew integrationTest --tests '*MediaAssetStorageIntegrationTest'` |
| 2 | Executor B (integration) | `src/integrationTest/java/com/dnd/qello/FeedMediaViewUrlIntegrationTest.java` | INT-008 | `./gradlew integrationTest --tests '*FeedMediaViewUrlIntegrationTest'` |
| 2 | Executor B (integration) | `src/integrationTest/java/com/dnd/qello/MediaAttachmentIntegrationTest.java` | MIGRATE(READY 전이 호출만) | `./gradlew integrationTest --tests '*MediaAttachmentIntegrationTest'` |

- `ProfileImageIntegrationTest`는 수정하지 않는다. 1x1 PNG에 메타데이터가 없어 결과가 같아야 하므로 회귀 확인용으로 실행만 한다.
- 기존 파일을 고칠 때는 클래스 헤더의 `Source scenario`에 이 계획의 시나리오 ID와 `(added <ISO 8601 시각>)`을 덧붙인다. MIGRATE는 기존 단언의 의미를 바꾸지 않는다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과(`./gradlew test`)
- [ ] 통합 테스트 통과(`./gradlew integrationTest`)
- [ ] `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check` 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성(`templates/test-report.md`)

실패 판단 기준은 다음과 같다.

- **FAIL**: P0 시나리오가 하나라도 실패하거나, 기존 media·profile·feed 테스트가 새로 실패한다.
- **BLOCKED**: Docker나 LocalStack을 띄울 수 없어 INT를 실행하지 못한다.

## 11. Human approval

결정이 필요한 항목:

- **H1. 실패 구분**: 이미지 형식이 깨졌으면 REJECTED로 둔다. S3 읽기·쓰기 장애면 `STORAGE_UNAVAILABLE`(503)을 돌려주고 UPLOADING을 유지해 다시 confirm할 수 있게 한다. Issue의 "처리 실패 시 REJECTED"를 이 둘로 나누는 안이다.
- **H2. key 규칙**: upload key는 `uploads/{ownerId}/{uuid}`, serving key는 `media/{ownerId}/{uuid}`로 한다. 배포 전 형식(`media/...`)의 UPLOADING 자산은 upload key와 다른 serving key로 보낸다. 나중에 `uploads/`에만 짧은 lifecycle을 걸면 원본 보존 문제를 별도 인프라 Issue로 풀 수 있다.
- **H3. PNG 청크 허용 목록**: IHDR, PLTE, IDAT, IEND, tRNS, gAMA, cHRM, sRGB, iCCP, sBIT, bKGD, pHYs와 애니메이션 청크(acTL, fcTL, fdAT)는 남긴다. eXIf는 Orientation만 담아 다시 쓴다. 나머지는 지운다.

- Reviewer: @tkv00
- Decision: 승인. H1·H2·H3 모두 권장안대로 결정
- Approved at: `2026-10-07T23:13:07+09:00`

## 12. Revision 1 (2026-10-07, 구현 중 발견)

- Reviewer: @tkv00
- Decision: 승인
- Approved at: `2026-10-08T00:23:58+09:00`

승인된 9절과 달라진 점이다. 시나리오와 기대 결과는 바뀌지 않았다.

| 파일 | 변경 | 이유 |
| --- | --- | --- |
| `src/test/.../account/service/ProfileServiceTest.java` | `MediaAsset.restore` 인자 추가, fake port에 `putObject` 추가 | 포트와 복원 시그니처가 바뀌어 컴파일되지 않았다(MIGRATE). 단언은 그대로다 |
| `src/test/.../feed/service/FeedMediaViewResolverTest.java` | fake port에 `putObject` 추가 | 같은 이유(MIGRATE) |
| `src/integrationTest/.../ProfileImageIntegrationTest.java` | 1x1 PNG fixture를 IHDR·IDAT·IEND가 있는 실제 PNG로 교체 | 기존 fixture가 IHDR만 있는 잘린 PNG라 confirm의 구조 검사에서 REJECTED가 됐다. 9절은 이 파일을 "수정 없음"으로 잘못 가정했다 |
| `src/integrationTest/.../ExifTestImages.java` | INT에 필요한 부분만이 아니라 `src/test` 클래스를 통째로 복사 | 두 사본이 서로 다르게 바뀌지 않게 같은 내용으로 둔다 |

### 독립 검토 반영

구현을 보지 않은 검토 에이전트의 지적으로 다음을 바꿨다.

| 항목 | 변경 |
| --- | --- |
| JPEG 허용 목록 | APP0은 JFIF일 때만 남긴다(JFXX 썸네일 안의 EXIF 차단). APP 밖에서는 SOFn·DHT·DAC·DQT·DNL·DRI·DHP·EXP만 남기고 예약 마커와 JPGn은 지운다 |
| PNG 구조 검사 | IEND 전에 IDAT가 없으면 거절한다. JPEG의 SOS 필수 조건과 맞춘다 |
| Orientation 읽기 | 다음 IFD offset까지 있는 IFD에서만 읽는다. 다시 쓴 EXIF가 원본보다 길어지는 경우를 없앤다 |
| INT-003 | 같은 사진이 아니라 다른 GPS 사진을 다시 올려 "다시 처리되지 않음"을 구분한다 |

추가 시나리오:

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-023 | progressive JPEG의 두 번째 스캔 앞에 XMP APP1과 COM을 끼웠다 | 제거한다 | 파일 전체에 APP1·COM이 없고, 스캔 수와 픽셀이 같으며, 끼우기 전 파일과 바이트가 같다 | P0 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-024 | DRI와 압축 데이터 속 RST 마커가 있는 JPEG | 제거한다 | 바이트와 픽셀이 그대로다 | P0 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-025 | 마커 앞에 fill 바이트(0xFF)가 있는 JPEG | 제거한다 | fill 없는 원래 파일과 바이트가 같고 픽셀이 같다 | P1 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-026 | EXIF APP1이 두 개인 JPEG | 제거한다 | 첫 번째의 Orientation만 남고 두 번째의 기기 정보는 없다 | P1 | Executor A |
| TEST-PLAN-GH-325-EXIF-STRIP-UNIT-027 | GPS EXIF 썸네일을 담은 JFXX APP0과 예약 마커(0xF1) 세그먼트가 있는 JPEG | 제거한다 | APP0은 JFIF 하나만 남고 0xF1과 썸네일 내용이 없다 | P0 | Executor A |

기존 시나리오 보강: UNIT-007은 두 바이트 순서 모두에서 3·6·8과 잘못된 값을 검사한다. UNIT-011에 IDAT 없는 PNG, UNIT-012에 다음 IFD offset이 빠진 TIFF를 추가했다.

INT-007의 한계: 서비스 경로의 로그만 본다. 저장소 장애가 HTTP 응답으로 바뀔 때 `GlobalExceptionHandler`가 SDK 예외 원인을 ERROR로 남기는 경로는 검증하지 않는다. 연결 장애 메시지에는 버킷 이름이 든 호스트가 나올 수 있다. 이 경로는 기존 HeadObject·읽기에도 있었고 `putObject`가 하나를 더한다. 테스트 보고서의 잠재 문제에 기록한다.

구현 메모: 범위의 "저장소 포트 전체 읽기 추가"는 새 메서드 대신 기존 `readObjectPrefix`를 `byte_size + 1`로 호출해 구현했다. 포트에 새로 생긴 메서드는 `putObject` 하나다.
