# Test Report: TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD

> Created at: `2026-08-19T00:15:51+09:00`
> GitHub Issue: `#166`
> Branch: `feat/gh-166-profile-image-upload`
> Commit: 작업 트리 기준(미커밋). 분기점은 `e7086dc`

## 1. Executive summary

- Result: `PASS`
- Tested scope: `user_account.profile_image_media_id` 마이그레이션과 복합 FK, `Account`의
  프로필 이미지 설정·해제, 소유권과 `READY` 검증, presigned GET 발급과 조회 전용 TTL,
  기본 이미지 폴백(미설정과 `DELETED` 양쪽), 설정 누락 fail-fast, 응답의 storage 위치
  비노출, 인증 없는 접근 차단, OpenAPI 스펙 반영.
- Unverified scope: INT-008(같은 계정의 프로필 이미지 동시 변경 시 낙관적 락). 계획의
  나머지 항목 중 UNIT-016·INT-005·INT-006은 전제가 성립하지 않아 폐기했다(계획 13절).
- Release recommendation: 병합 가능. 다만 **기본 이미지 객체를 대상 버킷에 미리 넣는 것이
  배포 전제 조건**이다. presigned 발급은 객체 존재를 확인하지 않으므로, 객체가 없으면 URL은
  정상 발급되고 그 URL이 404를 가리킨다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 17.0.8 LTS |
| Spring Boot | 3.5.16 |
| Database | Testcontainers PostGIS (통합), 컨테이너 없음 (단위) |
| Object storage | Testcontainers LocalStack S3 |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` | PASS | 664 | 9s | `build/test-results/test` |
| `./gradlew integrationTest` | PASS | 492 | 6m 42s | `build/test-results/integrationTest` |
| `./gradlew integrationTest --tests '*ProfileImageIntegrationTest*'` | PASS | 8 | 20s | 신규 통합 8건 |
| `./harness check` | PASS | — | — | secret preflight 973 파일, JUnit 정책 172 파일 |
| `npm run hooks:validate` | PASS | — | — | Husky validation |
| `git diff --check` | PASS | — | — | 공백 오류 없음 |

신규 단위 16건, 신규 통합 8건이다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `AccountProfileImageTest.keepsOtherFieldsWhenProfileImageIsSet` | |
| UNIT-002 | PASS | `AccountProfileImageTest.clearsReferenceWhenProfileImageIsRemoved` | |
| UNIT-003 | PASS | `AccountProfileImageTest.startsWithoutProfileImageOnCreation` | |
| UNIT-004 | PASS | `AccountProfileImageTest.rejectsNonPositiveMediaId` | |
| — | PASS | `AccountProfileImageTest.preservesProfileImageAcrossStatusTransitions` | 계획 외 추가. 상태 전이가 참조를 잃지 않는지 확인 |
| UNIT-005 | PASS | `ProfileServiceTest.rejectsAssetOwnedByAnotherUser` | 403이 아니라 404로 수정(계획 13절 2번) |
| UNIT-006 | PASS | `ProfileServiceTest.rejectsUploadingAsset` | |
| UNIT-007 | PASS | `ProfileServiceTest.rejectsRejectedAsset` | |
| UNIT-008 | PASS | `ProfileServiceTest.rejectsDeletedAsset` | 설정 시점 거부. 폴백과 구분됨 |
| UNIT-009 | PASS | `ProfileServiceTest.rejectsMissingAsset` | |
| UNIT-010 | PASS | `ProfileServiceTest.issuesDefaultImageUrlWhenUnset` | |
| UNIT-011 | PASS | `ProfileServiceTest.issuesOwnImageUrlWhenSet` | |
| UNIT-012 | PASS | `ProfileResponseTest.doesNotExposeStorageLocation` | record component 전수 확인 |
| UNIT-013 | PASS | `ProfileServiceTest.usesViewTtlNotUploadTtl` | 업로드 TTL과 다른 값임을 함께 단언 |
| UNIT-014 | PASS | `MediaStorageViewSettingsTest.rejectsMissingDefaultProfileImageKey` 외 2건 | null·빈 문자열·공백 |
| UNIT-015 | PASS | `ProfileServiceTest.translatesStorageFailure` | |
| UNIT-016 | 폐기 | — | 가입 요청 필드 자체가 성립하지 않음(계획 13절 1번) |
| UNIT-017 | PASS | `ProfileServiceTest.removesReferenceWithoutTouchingAsset` | |
| UNIT-018 | PASS | `ProfileServiceTest.fallsBackToDefaultWhenAttachedAssetBecomesDeleted` | 참조 보존까지 확인 |
| INT-001 | PASS | `ProfileImageIntegrationTest.addsNullableProfileImageColumn` | |
| INT-002 | PASS | `ProfileImageIntegrationTest.compositeForeignKeyRejectsAssetOwnedByAnotherUser` | 애플리케이션 우회 직접 UPDATE 차단 |
| INT-003 | PASS | `ProfileImageIntegrationTest.servesOwnImageAfterUploadAndConfirm` | 실제 LocalStack PUT·confirm·조회 |
| INT-004 | PASS | `ProfileImageIntegrationTest.servesDownloadableDefaultImage` | 발급 URL로 실제 200 수신 |
| INT-005 | 폐기 | — | 계획 13절 1번 |
| INT-006 | 폐기 | — | 계획 13절 1번 |
| INT-007 | PASS | `ProfileImageIntegrationTest.removingProfileImageKeepsAsset` | 자산이 READY로 남는지 확인 |
| INT-008 | NOT_RUN | — | 7절 참조 |
| INT-009 | PASS | `ProfileImageIntegrationTest.requiresAuthentication` | 401 |
| INT-010 | PASS | `ProfileImageIntegrationTest.rejectsAssetOwnedByAnotherUserThroughService` | 404로 수정 |
| INT-011 | PARTIAL | `ProfileImageIntegrationTest.servesOwnImageAfterUploadAndConfirm` | 발급 URL로 200 수신과 만료 시각 존재는 확인. **TTL 경과 후 실패는 확인하지 않았다** — 5분 대기가 필요해 스위트에 넣지 않았다 |
| INT-012 | PASS | `OpenApiSpecificationIntegrationTest` (기존) | `docs/api/openapi.json`에 `/api/v1/me/profile`, `/api/v1/me/profile/image` 반영 |
| INT-013 | PASS | `ProfileImageIntegrationTest.fallsBackToDefaultWhenAttachedAssetIsDeleted` | 참조 보존 확인 |

## 5. Failures and diagnostics

최종 실행에서 실패한 테스트는 없다. 구현 도중 두 건의 기존 테스트가 실패했고 둘 다
**의도된 매니페스트 갱신**이었다.

1. `FlywayMigrationContractTest` — 마이그레이션 파일 목록이 고정 목록이라 V21 추가로
   실패했다. 목록에 등록해 해소했다.
2. `FlywayMigrationIntegrationTest` — 적용 개수(20 → 21)와 FK 총계(54 → 55)가 고정값이라
   실패했다. `user_account`가 매니페스트 대상 테이블이라 V21의 FK가 총계에 반영된다.
   기대값을 갱신하고 근거를 주석으로 남겼다.

두 경우 모두 검증을 우회하거나 suppress 하지 않고 기대값을 갱신했다.

## 6. Potential issues

### Application code

- `ProfileImageResolver`는 프로필 조회마다 `media_asset` 한 건을 추가로 읽는다. 프로필이
  목록 응답(피드·인박스)에 실리기 시작하면 N+1이 된다. 지금은 `/api/v1/me/profile`
  단건 조회뿐이라 문제가 없지만, 프로필 이미지를 목록에 넣는 후속 작업에서 일괄 조회가
  필요하다.
- 프로필 조회는 호출할 때마다 새 presigned URL을 만든다. 캐시가 없으므로 조회가 잦아지면
  서명 비용이 선형으로 는다.

### Infrastructure and resource limits

- **기본 이미지 객체가 버킷에 없으면 조용히 깨진다.** presigned 발급은 객체 존재를
  확인하지 않으므로 URL은 정상 발급되고 클라이언트가 404를 받는다. 테스트로는 잡을 수 없는
  종류이며, 배포 전 객체 적재를 전제 조건으로 둔다.
- 애플리케이션 런타임 IAM Role이 아직 없다(`MediaStorageS3Config` 주석). 실제 환경에서
  presigned GET을 발급하려면 그 작업이 선행돼야 한다.

### Database and migrations

- V21은 컬럼 추가와 FK 추가만 하고 데이터를 변환하지 않는다. 기존 행은 모두 `NULL`이라
  기본 이미지 상태가 된다.
- FK는 `ON DELETE RESTRICT`다. `media_asset`은 물리 삭제 대신 상태 전이를 쓰므로 현재
  경로에서는 걸리지 않지만, 훗날 자산을 물리 삭제하려면 프로필 참조를 먼저 끊어야 한다.

### Concurrency and idempotency

- `AccountJpaEntity`의 `@Version`이 프로필 이미지 동시 변경도 함께 보호한다. 다만 이번에
  실제 동시 실행으로 확인하지는 않았다(INT-008).
- 프로필 조회는 쓰기가 없어 폴백이 상태를 바꾸지 않는다. 같은 media id로 두 번 설정하는
  것도 결과가 같다.

### Transactions and event ordering

- 프로필 이미지 변경은 검증과 갱신이 한 트랜잭션이다. 검증 이후 자산이 `DELETED`로
  바뀌는 경합이 이론상 남지만, 결과는 조회 시 기본 이미지 폴백이라 사용자에게 보이는
  실패가 되지 않는다.

### External APIs

- S3 장애는 `STORAGE_UNAVAILABLE`(503)로 변환된다. 원인 예외를 감싸되 메시지를 응답에
  그대로 싣지 않는다.

### Failure recovery and reconciliation

- 프로필에 붙은 자산이 `DELETED`가 되어도 참조는 남는다. 자산이 되살아나는 경로가 생기면
  원래 이미지가 그대로 돌아온다.

## 7. Regression and residual risk

- **INT-008 미실행.** 같은 계정의 프로필 이미지를 동시에 바꾸는 상황을 실제 스레드로
  확인하지 않았다. 근거는 `@Version`이 이미 다른 프로필 변경 경로에서 검증된 기존
  메커니즘이고 이번 변경이 그 경로를 바꾸지 않았다는 점이지만, **확인하지 않은 것은
  확인된 것이 아니다.** 남은 위험은 동시 변경 시 나중 요청이 앞선 변경을 조용히 덮어쓰는
  것이며, 영향은 사용자가 고른 이미지가 아닌 다른 이미지가 남는 정도다.
- **INT-011 부분 실행.** 만료 후 실패는 확인하지 않았다. TTL 값이 설정에서 포트로
  전달되는 것은 UNIT-013이 확인한다.
- 기존 테스트 4개 파일의 `AccountRepository` 대역에 `updateProfileImage`를 추가했다.
  전부 `UnsupportedOperationException`이라 기존 시나리오의 동작은 바뀌지 않는다.
- `MediaStorageProperties`에 component 2개가 늘어 생성자 시그니처가 바뀌었다. 호출자는
  Spring 바인딩과 테스트 3곳뿐이며 모두 갱신했다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-166-TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD.md`
- CI run: 미실행(로컬 실행 결과만 기록)
- Related ADR: 없음
- PR: 미생성

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (INT-008, INT-011 부분)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 — 기본 이미지 적재와 런타임 IAM Role은 아직
      이슈가 없다
- [ ] 실행 결과와 PR 설명이 일치함 — PR 미생성
