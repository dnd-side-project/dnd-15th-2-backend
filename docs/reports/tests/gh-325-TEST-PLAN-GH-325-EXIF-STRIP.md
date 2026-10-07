# Test Report: TEST-PLAN-GH-325-EXIF-STRIP

> Created at: `2026-10-07T23:55:51+09:00`
> GitHub Issue: `#325`
> Branch: `feat/gh-325-exif-strip-on-confirm`
> Commit: `1a3b1254` (기준 커밋. 변경은 아직 커밋하지 않았고 작업 트리 기준으로 실행했다)

## 1. Executive summary

- Result: `PASS`
- Tested scope: EXIF 제거 모듈(JPEG·PNG), `MediaAsset`의 READY·`exifStripped` 규칙, JDBC 매핑, confirm 흐름(처리본 저장, REJECTED, 저장소 장애 시 UPLOADING 유지), upload/serving key 분리, 프로필·피드 조회 URL이 처리본을 가리키는지
- Unverified scope: `GlobalExceptionHandler`가 저장소 장애를 HTTP 응답으로 바꿀 때 남기는 ERROR 로그(6절), 부하 상황의 메모리 사용량, SSE-KMS 암호화(LocalStack 미지원), 실제 휴대폰 사진 표본
- Release recommendation: 테스트 기준으로는 병합 가능. 다만 계획 Revision 1의 사람 확인, 커밋 승인 뒤 `./harness sync`와 `pr-ready` 재실행, 6절 후속 Issue(로그·원본 보존) 판단이 남았다

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | Temurin 21 |
| Spring Boot | 저장소 `build.gradle` 기준 |
| Database | Testcontainers PostGIS 16(로컬 Docker, linux/amd64 에뮬레이션) |
| Object storage | Testcontainers LocalStack 3.8 S3 |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./harness check` | PASS | 정책 검사 | 수십 초 | 로컬 실행 로그 |
| `./gradlew check` 중 Unit(`test`) | PASS | 1,264건(189개 클래스), 실패·건너뜀 0 | `check` 전체 11분 27초 | `build/test-results/test` |
| `./gradlew check` 중 Integration(`integrationTest`) | PASS | 796건(104개 클래스), 실패·건너뜀 0 | 위와 같음 | `build/test-results/integrationTest` |
| `./harness test-run --id TEST-PLAN-GH-325-EXIF-STRIP` | PASS | 위 결과 재사용(Gradle UP-TO-DATE) | 수 초 | 이 보고서 생성 |
| `npm run hooks:validate` | PASS | - | - | 로컬 실행 로그 |
| `git diff --check` | PASS | - | - | 로컬 실행 로그 |
| `./harness pr-ready --project-tests` | BLOCKED | - | - | 실행 중 `origin/main`이 앞서가 "branch is behind origin/main" 게이트에서 멈췄다. `./harness sync`는 깨끗한 작업 트리가 필요해 커밋 승인 전에는 실행할 수 없다. 같은 검사(`check`, `./gradlew check`, `git diff --check`)를 위처럼 따로 실행했다 |

새로 들어온 main 커밋(#323, #324)은 수신함(inbox) 파일만 바꿔 이번 변경 파일과 겹치지 않는다. sync 뒤 `pr-ready`를 다시 실행해야 한다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `ExifStripperTest.keepsOnlyOrientationFromPhoneExif` | 원본에 GPS IFD가 있는지 먼저 확인 |
| UNIT-002 | PASS | `ExifStripperTest.removesXmpPhotoshopCommentAndOtherAppSegments` | |
| UNIT-003 | PASS | `ExifStripperTest.keepsJfifIccAndAdobeButRemovesMpf` | |
| UNIT-004 | PASS | `ExifStripperTest.dropsDataAfterEndOfImage` | |
| UNIT-005 | PASS | `ExifStripperTest.keepsPixelsAndCompressedDataUnchanged` | 픽셀과 JPEG 압축 데이터 바이트 비교 |
| UNIT-006 | PASS | `ExifStripperTest.leavesCleanJpegUntouchedAndIsIdempotent` | |
| UNIT-007 | PASS | `ExifStripperTest.keepsRotatingOrientationForBothByteOrders`, `dropsExifWhenOrientationNeedsNoRotation` | 두 바이트 순서 × 값 15개 |
| UNIT-008 | PASS | `ExifStripperTest.rewritesPngExifAndRemovesTextChunks` | CRC 검증 포함 |
| UNIT-009 | PASS | `ExifStripperTest.dropsDataAfterPngEndAndLeavesCleanPngUntouched` | |
| UNIT-010 | PASS | `ExifStripperTest.rejectsMalformedJpegWithoutLeakingPayload` | |
| UNIT-011 | PASS | `ExifStripperTest.rejectsMalformedPng` | IDAT 없는 PNG 추가(Revision 1) |
| UNIT-012 | PASS | `ExifStripperTest.neverGrowsOutput` | 다음 IFD offset 없는 TIFF 추가(Revision 1) |
| UNIT-013 | PASS | `ExifStripperTest.stripperDoesNotDependOnSpringOrAws` | ArchUnit |
| UNIT-014 | PASS | `MediaAssetTest.readyPointsToServingKeyAndMarksExifStripped` | |
| UNIT-015 | PASS | `MediaAssetTest.restoresLegacyReadyRowWithoutStrippedFlag` | |
| UNIT-016 | PASS | `MediaUploadServiceTest.confirmStoresStrippedCopyUnderServingKey` | |
| UNIT-017 | PASS | `MediaUploadServiceTest.rejectsMalformedImageWithoutWriting` | |
| UNIT-018 | PASS | `MediaUploadServiceTest.storageFailureKeepsUploadingSoRetryCanSucceed` | H1 |
| UNIT-019 | PASS | `MediaUploadServiceTest.rejectsWhenObjectChangedAfterHead` | |
| UNIT-020 | PASS | `MediaUploadServiceTest.confirmIsIdempotentAfterResolution` | 저장소 호출 수 비교 |
| UNIT-021 | PASS | `MediaUploadServiceTest.servingKeyDiffersFromUploadKeyAndIsDeterministic` | H2 |
| UNIT-022 | PASS | `MediaUploadServiceTest.issuesUploadUrlUnderUploadsPrefix` | H2 |
| UNIT-023 | PASS | `ExifStripperTest.removesMetadataBetweenProgressiveScans` | Revision 1 |
| UNIT-024 | PASS | `ExifStripperTest.keepsRestartMarkersUntouched` | Revision 1. fixture에 DRI·RST가 있는지 먼저 확인 |
| UNIT-025 | PASS | `ExifStripperTest.skipsFillBytesBeforeMarkers` | Revision 1 |
| UNIT-026 | PASS | `ExifStripperTest.keepsOnlyFirstExifOrientation` | Revision 1 |
| UNIT-027 | PASS | `ExifStripperTest.removesJfxxAndReservedMarkers` | Revision 1 |
| INT-001 | PASS | `ExifStripIntegrationTest.confirmStoresStrippedJpegUnderServingKey` | |
| INT-002 | PASS | `ExifStripIntegrationTest.confirmStoresStrippedPngUnderServingKey` | |
| INT-003 | PASS | `ExifStripIntegrationTest.reuploadingAfterReadyDoesNotChangeServedObject` | 다른 사진으로 재업로드(Revision 1) |
| INT-004 | PASS | `ExifStripIntegrationTest.profileImageUrlServesStrippedObject` | |
| INT-005 | PASS | `ExifStripIntegrationTest.malformedJpegIsRejectedWithoutServedObject` | |
| INT-006 | PASS | `MediaAssetStorageIntegrationTest.concurrentConfirmIsIdempotent` | |
| INT-007 | PASS | `ExifStripIntegrationTest.confirmDoesNotLogStorageIdentifiers` | 서비스 경로만 본다(6절) |
| INT-008 | PASS | `FeedMediaViewUrlIntegrationTest.confirmedPhotoIsServedWithoutGps` | |
| INT-009 | PASS | `ExifStripIntegrationTest.legacyReadyRowWithoutStrippedFlagIsReadable` | |

## 5. Failures and diagnostics

실행 중 만난 실패와 처리:

| 실패한 명령 | 오류 요약 | 구분 | 처리 |
| --- | --- | --- | --- |
| 대상 통합 테스트 1차 실행 | `ProfileImageIntegrationTest` 4건 READY 대신 REJECTED | 테스트 fixture 문제 | fixture의 "1x1 PNG"가 IHDR만 있는 잘린 파일이었다. 실제 PNG로 바꿨다(Revision 1) |
| 대상 통합 테스트 1차 실행 | `MediaAssetStorageIntegrationTest` 컨텍스트 로드 실패(PostgreSQL 연결 시도 실패, EOFException) | 테스트 환경 문제 | 같은 명령을 다시 실행하자 통과했다 |
| `./harness pr-ready` 1차 | `MediaAssetTest` 헤더에 Source scenario 없음 | 정책 위반 | Spotless가 클래스 Javadoc을 한 줄로 합쳤다. 헤더를 `package` 위로 옮겼다 |
| `./harness pr-ready` 2차(중단) | 여러 통합 테스트 클래스 컨텍스트 로드 실패 | 테스트 환경 문제로 판단 | 독립 검토 반영을 위해 중단했다. 이후 `./gradlew check` 전체 실행에서 796건이 모두 통과했다 |

환경 문제의 재현 조건: Apple Silicon Mac의 Docker에서 PostGIS 이미지를 linux/amd64로 에뮬레이션하고, 테스트 클래스마다 컨테이너를 새로 띄울 때 간헐적으로 생겼다. 원인을 확정하지 못했다. 이 변경과 관계없는 클래스(알림, 답변 조회)에서도 같은 오류가 났고, 다시 실행하면 통과했다. 남은 위험은 CI에서 같은 간헐 실패가 날 수 있다는 것이다.

## 6. Potential issues

### Application code

- 저장소 장애가 HTTP 응답으로 바뀔 때 `GlobalExceptionHandler`가 SDK 예외 원인을 ERROR로 남긴다. 연결 장애 메시지에는 버킷 이름이 든 호스트가 나올 수 있다. 기존 HeadObject·읽기 경로에도 있던 문제이고 `putObject`가 경로를 하나 더한다. object key가 메시지에 들어가는 경우는 확인하지 못했다. 후속 Issue #327.
- Orientation이 SHORT·count 1이 아닌 형식(LONG 등)으로 저장된 사진은 회전 정보를 잃는다. EXIF 규격 밖이라 드물다.
- `ExifStripper`는 `ImageMimeType`(answer.domain)에 의존한다. Lambda로 옮길 때 이 enum도 함께 옮기거나 형식 인자를 바꿔야 한다.

### Infrastructure and resource limits

- confirm 한 건이 이미지 크기의 약 4~5배 메모리를 잡는다(SDK 응답 복사, 출력 버퍼, SDK 요청 복사). 최대 10MiB면 약 50MiB다. t3.small(2GiB)에서 동시 업로드가 몰리면 위험하다. 측정하지 않았다.
- 버킷 versioning 때문에 GPS가 든 원본(`uploads/`)이 이전 버전과 함께 180일 남는다. 사용자에게 조회 URL은 나가지 않는다. `uploads/` prefix에 짧은 lifecycle을 거는 작업은 후속 Issue #328.

### Database and migrations

- 스키마 변경은 없다. 이 규칙 전에 READY가 된 행은 `exif_stripped = false`로 남고 GPS가 든 원본을 계속 서빙한다(TASK.md 결정: 출시 전 테스트 데이터로 보고 처리하지 않음).

### Concurrency and idempotency

- 동시 confirm 두 건 중 늦은 쪽도 serving key에 쓴다. 그 사이 소유자가 다른 사진을 다시 올리면 READY 뒤에 서빙 바이트가 바뀔 수 있다. 결과는 여전히 메타데이터가 지워진 사진이고, 소유자 본인만 만들 수 있으며, 창은 밀리초 단위다.

### Transactions and event ordering

- confirm은 `NOT_SUPPORTED`로 트랜잭션 밖에서 S3를 읽고 쓰고, 상태 전이만 짧은 트랜잭션에서 한다. 처리본 저장 뒤 전이 전에 실패하면 serving 객체만 남고, 재시도가 같은 key에 덮어쓴다.
- `MediaUploadService` 클래스 단위 `@Transactional(readOnly = true)`는 두 public 메서드가 모두 덮어써 실제 효과가 없다. 바뀐 `@Service`에 적용되는 Java 관례 규칙(TX-001) 때문에 붙였다.

### External APIs

- LocalStack은 실제 S3의 SSE-KMS, IAM 권한, 오류 메시지 형식을 재현하지 않는다. 앱 Role은 버킷 전체(`bucket/*`)에 Get/PutObject가 있어 `uploads/` prefix도 허용된다(Terraform 기준 확인, 실제 계정에서는 확인하지 않음).

### Failure recovery and reconciliation

- 저장소 장애는 503과 UPLOADING 유지로 재시도할 수 있다. REJECTED·UPLOADING 자산의 원본(`uploads/`)과 실패로 남은 serving 객체는 정리하지 않는다(기존 고아 업로드 문제와 같다).

## 7. Regression and residual risk

- 계획 밖 파일 세 개를 고쳤다(Revision 1). 컴파일용 2개와, 잘린 PNG fixture를 실제 PNG로 바꾼 `ProfileImageIntegrationTest`다. 사람이 승인했다(2026-10-08).
- 커밋 2~5는 사람 승인을 받아 pre-commit·commit-msg 훅을 건너뛰었다(`--no-verify`, AGENTS.md 8절).
  - 이유: worktree에서 훅이 실행하는 `ChangedJavaTypesTest`가 훅의 `GIT_INDEX_FILE`(절대 경로)을 물려받아 실제 인덱스에 `DeviceTokenService` fixture를 스테이징하고 훅을 실패시킨다. 하네스 결함이고 이번 범위에서 고치지 않았다.
  - 수동 검증: 커밋 전에 `./harness check`, `./gradlew javaConventionCheck`(spotless·checkstyle·아키텍처 테스트 포함)를 훅 밖에서 실행해 통과했다. 커밋마다 `git diff --cached --check`와 `scripts/validate-conventions.py --commit-file`로 메시지를 검사해 통과했다.
  - 남은 위험: 다른 worktree에서 Java 파일을 커밋할 때도 같은 실패가 난다.
- Spotless ratchet 때문에 고친 기존 테스트 파일 전체가 다시 포맷되어 diff가 크다(예: `MediaAttachmentIntegrationTest`는 실제 변경 두 곳). 리뷰 때 공백·줄바꿈 변경을 무시하고 보면 된다.
- 실제 휴대폰 사진(삼성 모션 포토, iPhone HEIC를 JPEG로 바꾼 파일 등)으로는 시험하지 않았다. 생성한 fixture로만 검증했다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-325-TEST-PLAN-GH-325-EXIF-STRIP.md`
- CI run: 없음(아직 push하지 않음)
- Related ADR: 없음. 위치 결정(D8, A안)은 Issue #325 본문에 요약
- PR: 없음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨(#327, #328)
- [ ] 실행 결과와 PR 설명이 일치함
