# Test Report: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API

> Created at: `2026-08-14T12:40:21+09:00`
> GitHub Issue: `#122`
> Branch: `feat/gh-122-direction-preview-submit-api`
> Commit: `working tree (uncommitted)`

## 1. Executive summary

- Result: `PASS`
- Tested scope: 승인된 GLOBAL/PT12H/300 code point/단일 JPEG·PNG 정책, 서버 권위
  actor·presence·scheme 해석, preview와 matching worker의 GLOBAL 범위, media
  upload/confirm 경계, 현재 v1 fingerprint와 제출 검증, ApiSpec/Controller 및 생성
  OpenAPI 계약, 기존 단위·통합 회귀.
- Unverified scope: 별도 dedicated end-to-end POST transaction rollback 주입,
  동일 idempotency key의 2-thread API 경합, production-like `EXPLAIN` 성능 기준,
  S3 장애를 실제 Controller까지 통과시키는 전용 통합 시나리오는 실행하지 않았다.
  관련 service/기존 matching·media 테스트와 저장소 필수 게이트는 통과했다.
- Release recommendation: 코드 리뷰와 CI에 제출 가능한 상태. 배포 전에는 아래
  미검증 범위를 #127 성능 작업 또는 후속 #122 검증 커밋으로 보강한다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Gradle toolchain 21 (호스트 JVM: Temurin 25.0.3) |
| Spring Boot | 3.5.16 |
| Database | PostgreSQL 16 + PostGIS 3.5 Testcontainers |
| Object storage | LocalStack 3.8 Testcontainer (media integration) |
| Build | Gradle 8.14.3, `--max-workers=1` for deterministic container runs |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --max-workers=1 --no-daemon` | PASS | 325 | — | Gradle test task |
| Exception boundary follow-up (`MediaUploadServiceTest`, `MediaAssetWebContractTest`, `DirectionPostApiMockMvcTest`, `DirectionPostPropertiesTest`) | PASS | — | — | existing `AnswerException`/`DirectionException` mapping |
| `./gradlew integrationTest --tests '*OpenApiSpecificationIntegrationTest' --max-workers=1 --no-daemon --no-parallel --rerun-tasks` | PASS | 9 | — | generated OpenAPI assertions |
| `./gradlew integrationTest --tests 'com.dnd.qello.DirectionPreviewIntegrationTest' --max-workers=1 --no-daemon --no-parallel --rerun-tasks` | PASS | 6 | — | GLOBAL preview/PostGIS |
| `./gradlew integrationTest --tests 'com.dnd.qello.DirectionMatchingWorkerIntegrationTest' --max-workers=1 --no-daemon --no-parallel --rerun-tasks` | PASS | 17 | — | GLOBAL worker/PostGIS |
| `./gradlew integrationTest --tests 'com.dnd.qello.DirectionMatchingContractIntegrationTest' --max-workers=1 --no-daemon --no-parallel --rerun-tasks` | PASS | 9 | — | current v1 fingerprint/idempotency regression |
| `./gradlew integrationTest --tests 'com.dnd.qello.MediaAssetStorageIntegrationTest' --max-workers=1 --no-daemon --no-parallel --rerun-tasks` | PASS | — | — | LocalStack storage |
| `./gradlew integrationTest --tests 'com.dnd.qello.MediaAttachmentIntegrationTest' --max-workers=1 --no-daemon --no-parallel --rerun-tasks` | PASS | — | — | media attachment persistence |
| `./harness test-run --id TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API` | PASS | unit + integration tasks | 2m 40s | generated report task |
| `./harness check` | PASS | — | — | repository policy gates |
| `./harness pr-ready --project-tests` | PASS | unit + integration tasks | 2m 32s | local PR readiness |
| `npm run hooks:validate` | PASS | — | — | Husky validation |
| `git diff --check` | PASS | — | — | whitespace check |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `DirectionPostPropertiesTest` | GLOBAL, distance, TTL, 300 code point, media count binding/fail-fast |
| UNIT-002, 007, 008, 013 | PASS | `DirectionPostApplicationServiceTest` | JWT subject, ACTIVE account/presence, active scheme, server Clock/policy, key validation |
| UNIT-003 | PASS | `DirectionPostPolicyTest` | NFC/trim, text/media combinations, 300 code point and one-media rule |
| UNIT-004 | PASS | `MediaUploadServiceTest` | JPEG/JPG canonicalization, PNG, unsupported MIME, size/ID validation |
| UNIT-005, 006 | PASS | `DirectionRequestFingerprintTest`, `DirectionPostSubmissionServiceTest` | current v1 media-aware fingerprint, nullable-row restore, retry/conflict behavior |
| UNIT-009 | PASS | `MediaAttachmentServiceTest` | owner/READY/unattached and malformed command guards |
| UNIT-010, 012 | PASS | `DirectionPostWebContractTest`, `MediaAssetWebContractTest` | privacy DTO reflection and ApiSpec/Controller separation |
| UNIT-011 | PASS | preview/persistence boundary, `DirectionMatchingWorkerTest`, `DirectionPreviewServiceTest` | GLOBAL candidate region argument is null |
| INT-001, INT-003 | PASS | `DirectionPreviewIntegrationTest` | 6 tests; cross-region/country PostGIS preview and boundary behavior |
| INT-014 | PASS | `DirectionMatchingWorkerIntegrationTest` | 17 tests; worker recomputes GLOBAL candidate scope |
| INT-008 (service-level) | PASS | `MediaAssetStorageIntegrationTest`, `MediaAttachmentIntegrationTest` | LocalStack/object metadata and attachment persistence regression; HTTP controller and INT-017 failure mapping remain dedicated follow-up |
| INT-016 | PASS | `OpenApiSpecificationIntegrationTest` | 9 assertions; four paths, auth/status/error/privacy/schema constraints |
| Repository regression | PASS | full `test` and `harness pr-ready --project-tests` | Existing direction, answer media, Flyway and matching coverage included |
| INT-004~007, INT-010~013, INT-015, INT-018, INT-019 | NOT_RUN (dedicated) | — | Existing integration coverage exists in parts; dedicated API rollback, two-thread replay, expiry-boundary and planner evidence remain follow-up work |

## 5. Failures and diagnostics

최종 실행에서 구현 실패는 없었다. 작업 중 병렬 Gradle 실행으로 stale output과
테스트 XML 동시 쓰기 오류가 일시적으로 발생했고, `--max-workers=1`·`--no-parallel`
및 `--rerun-tasks`로 재실행해 모두 통과했다. GLOBAL preview에서 nullable
`regionCode`를 PostgreSQL이 추론하지 못한 문제는 candidate SQL에 명시적 `CAST`를
추가해 해결했으며, 수정 후 preview/worker 통합 테스트가 통과했다. 민감한 로그나
자격 증명은 보고서에 기록하지 않았다.

## 6. Potential issues

### Application code

- GLOBAL은 표시용 `coarseRegionCode` snapshot을 저장하지만 candidate SQL의 지역
  필터에는 사용하지 않는다. preview와 worker의 범위 동치가 테스트로 확인됐다.
- application service가 account/presence/scheme/Clock/정책을 서버에서 해석한다.
  정확 좌표, recipient, storage key는 public response에 매핑하지 않는다.
- 기존 core command 호출 형태는 유지되며 신규 API 제출은 media-aware 현재 v1
  fingerprint를 사용한다. 버전 간 분기나 운영 legacy 버전 호환은 두지 않는다.
- 미디어 설정 오류와 응답 매핑의 잘못된 `mediaId`는 기존 `AnswerException`/`AnswerErrorCode`로,
  멱등키 입력 오류는 기존 `DirectionException`/`DirectionErrorCode`로 변환한다.
- SHA-256 알고리즘 부재와 DI 구성 누락은 사용자 입력이 아닌 서버 불변식이므로 기존
  `IllegalStateException`을 유지한다. 인증 HTTP 경계는 기존 Controller와 동일한
  `ResponseStatusException` 계약을 유지한다.

### Infrastructure and resource limits

- 20,100km GLOBAL `ST_DWithin`의 production-like planner/latency 기준은 실행하지
  않았다. 범위를 임의로 축소하지 않고 #127에서 데이터·인덱스·분할 전략을 검토한다.

### Database and migrations

- 새 migration은 추가하지 않았고 기존 V1 media 제약과 V12 fingerprint 저장 계약을
  재사용했다. PostgreSQL nullable region parameter type 오류는 SQL `CAST`로 보완했다.

### Concurrency and idempotency

- DB unique/fingerprint와 service 재조회 경로를 유지하고 media ID를 현재 v1 fingerprint에
  포함했다. 전용 두 transaction/latch API 경합 시나리오는 NOT_RUN이다.

### Transactions and event ordering

- 제출 service는 post/audience/media attachment/MatchRequested Outbox를 transaction
  안에서 처리하고 recipient 확정은 worker 경계로 남긴다. 강제 attachment/Outbox 실패
  rollback 주입 테스트는 NOT_RUN이다.

### External APIs

- LocalStack S3 storage integration은 통과했다. 실제 AWS 자격 증명은 사용하지 않았다.
  upload request만 presigned URL을 반환하고 confirm/submit/error 경계는 URL/key를
  반환하지 않는다.

### Failure recovery and reconciliation

- storage unavailable은 `ANS-EXT-001`로 매핑하고 storage key field를 제거했다. 실제
  Controller까지의 503/retry reconciliation 전용 테스트와 Outbox planner 검증은 후속
  범위다.

## 7. Regression and residual risk

- `./gradlew test` 325건과 `./harness pr-ready --project-tests`가 통과해 기존 회귀
  범위는 PASS다. 다만 전용 API-to-DB rollback/동시성/expiry boundary/EXPLAIN 증거가
  없어 해당 영역은 병합 후 CI 또는 후속 검증에서 보강해야 한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-122-TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API.md`
- OpenAPI: `docs/api/openapi.json` (generated by `OpenApiSpecificationIntegrationTest`)
- CI run: 없음 (로컬 필수 게이트만 실행)
- Related ADR: 없음
- PR: 아직 생성하지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] GLOBAL 성능 위험을 후속 #127과 연결함
- [x] 실행 결과와 현재 작업 범위가 일치함
