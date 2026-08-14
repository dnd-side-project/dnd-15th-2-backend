# GitHub Issue #122 Task Contract

> Generated at: `2026-08-14T12:08:03+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `방향 미리보기·질문글 제출 API`
- GitHub Issue: `#122`
- Branch: `feat/gh-122-direction-preview-submit-api`
- Base branch: `main`
- Test plan: `TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API`
- Test plan approval: `APPROVED` — 사용자가 2026-08-14 구현 진행을 승인했다.
- Policy approval: `CONFIRMED` — 사용자가 GLOBAL 발송, `PT12H`, 본문 300자,
  JPEG/JPG/PNG 이미지 최대 1장과 텍스트·이미지 조합을 승인했다.

## Objective

- 인증된 앱 사용자가 현재 위치를 기준으로 모든 활성 방향 구간의 예상 후보 수를
  한 번에 미리보고, 승인 질문과 방향을 선택해 질문글을 비동기로 제출할 수 있는
  REST API 경계를 제공한다.
- 미리보기는 사용자 식별자·정확 좌표를 노출하지 않는 참고값으로만 반환하고,
  실제 수신자는 #120 매칭 worker가 발송 snapshot과 실행 시점 자격을 다시 확인해
  확정한다.
- 질문글은 텍스트만, 이미지 1장만, 이미지 1장과 텍스트 조합을 지원한다.
- 제출은 질문글·미디어 첨부·audience snapshot·MatchRequested Outbox를 하나의
  트랜잭션으로 기록하고 수신자 확정을 기다리지 않은 채 `202 SUBMITTED`를 반환한다.
- 같은 멱등 키의 같은 요청은 최초 결과를 반환하고, 질문·방향·본문·미디어가 다른
  요청은 공통 `409 IDEMPOTENCY_KEY_REUSED` 오류로 거절한다.

## Recommended API contract

1. `GET /api/v1/direction/preview`
   - `appAccessToken` Bearer JWT의 `sub`만 발신자 ID로 사용한다.
   - 사용자 ID, 좌표, 시각, 거리와 지역은 요청에서 받지 않는다.
   - 서버 `Clock`, 현재 presence, configured active scheme과 GLOBAL 거리 정책을 사용한다.
   - scheme ID/code/version와 정렬된 segment별 candidate count만 반환한다.
2. `POST /api/v1/direction/posts`
   - `Idempotency-Key` 헤더와 `approvedQuestionId`, `schemeId`, `segmentKey`,
     nullable `bodyText`, 0개 또는 1개의 `mediaIds`를 받는다.
   - `bodyText`와 `mediaIds`가 모두 비어 있으면 거절한다.
   - `202 Accepted`와 `postId`, `submissionStatus=SUBMITTED`, 최초 `submittedAt`,
     최초 `expiresAt`만 반환한다.
   - 수신자 ID·목록·예상/확정 수와 정확 위치는 반환하지 않는다.
3. 기존 `MediaUploadService`를 모바일에서 사용할 HTTP 경계를 제공한다.
   - `POST /api/v1/media-assets/upload-requests`는 인증 사용자의 JPEG/JPG
     (`image/jpeg`) 또는 PNG(`image/png`) 이미지 한 건에 대해 `201 Created`와
     `mediaId`, presigned PUT URL, 요청에 사용할 content type, URL 만료 시각을 반환한다.
   - `POST /api/v1/media-assets/{mediaId}/confirm`은 같은 소유자의 업로드를 확인해
     `200 OK`와 `mediaId`, `READY` 또는 기존 terminal 상태를 멱등하게 반환한다.
   - 저장소 URL이나 storage key는 질문글 제출 응답·오류·로그에 복사하지 않는다.
4. `ActiveUserPresenceApiSpec`과 같은 분리 규칙을 적용한다.
   - 메서드 매핑, `@Tag`, `@Operation`, `@ApiResponses`, `@SecurityRequirement`는
     `*ApiSpec`에 둔다.
   - Controller는 클래스 수준 `@RequestMapping`, `implements`, `@Override`와
     application service 호출만 담당한다.
   - request/response DTO는 `direction.web.request`와 `direction.web.response`에 둔다.

## Configuration contract

| Property | Type | State | Meaning |
| --- | --- | --- | --- |
| `qello.direction.post.delivery-scope` | enum | `CONFIRMED: GLOBAL` | 국가·대략 지역과 무관하게 방향·거리로 후보를 찾는다. |
| `qello.direction.post.min-distance-meters` | long | `CONFIRMED: 0` | 후보 최소 거리. |
| `qello.direction.post.max-distance-meters` | long | `CONFIRMED: 20100000` | 지구상의 다른 국가까지 포함할 수 있는 GLOBAL 상한. |
| `qello.direction.post.ttl` | Duration | `CONFIRMED: PT12H` | 최초 서버 제출 시각부터 질문글 만료까지의 기간. |
| `qello.direction.post.max-body-code-points` | int | `CONFIRMED: 300` | 정규화된 질문글 본문의 Unicode code point 상한. |
| `qello.direction.post.max-media-count` | int | `CONFIRMED: 1` | 질문글에 첨부할 수 있는 이미지 수. |
| `qello.media.allowed-mime-types` | set | `CONFIRMED: image/jpeg,image/png` | JPEG/JPG와 PNG만 허용한다. |
| `qello.media.max-byte-size` | long | `REUSED: 10485760` | 기존 파일당 10 MiB 상한을 유지한다. |

- `minDistanceMeters >= 0`, `maxDistanceMeters > minDistanceMeters`,
  `maxDistanceMeters <= 20100000`, `ttl > 0`, `maxBodyCodePoints = 300`,
  `maxMediaCount = 1`을 시작 시 검증한다.
- JPG와 JPEG는 모두 `image/jpeg`로 취급한다. 파일명 확장자만 신뢰하지 않고 기존
  confirm의 실제 object `Content-Type`과 크기 검증을 유지한다.
- GLOBAL 범위에서는 `direction_post.coarse_region_code`를 작성자의 대략 지역 표시
  snapshot으로만 사용하고 후보 SQL의 region 제한으로 사용하지 않는다.
- preview와 worker는 동일한 GLOBAL 거리 정책을 사용한다. 둘 중 하나만 지역 제한을
  제거하는 구현은 허용하지 않는다.
- 정책 변경은 기존 질문글의 audience/만료에 소급 적용하지 않으며 동일 멱등 키 재시도는
  최초 snapshot과 최초 응답을 반환한다.

## Scope

- JWT `sub` 기반 preview·submit·media upload/confirm 소유권
- ACTIVE USER와 현재 발신 위치 재검증
- configured active direction scheme 조회와 stale scheme 제출 거절
- GLOBAL 후보 집계와 GLOBAL worker 재계산의 동일 범위 보장
- 0~20,100km·12시간·300자·이미지 1장 설정과 fail-fast 검증
- 텍스트만·이미지만·텍스트와 이미지 제출 조합 검증
- READY·본인 소유·미첨부 media asset 검증
- media ID를 포함한 현재 `v1` request fingerprint와 nullable fingerprint 행 복원
- 질문글·media attachment·audience·Outbox 원자적 저장과 rollback
- 동일 멱등 요청·키 재사용·동시 제출 계약
- `202 SUBMITTED` 응답과 수신자 비동기 확정 경계
- 공통 성공/오류 응답, ApiSpec/Controller 분리와 OpenAPI 산출물 재생성
- 단위·MockMvc·PostgreSQL/PostGIS·LocalStack 통합 테스트와 테스트 보고서

## Explicit exclusions

- preview 결과의 cache 또는 실제 수신자 목록 반환
- client가 user ID, 좌표, region, 거리, submittedAt, expiresAt을 선택하는 계약
- 이미지 2장 이상, WebP, GIF, HEIC, 영상과 이미지 편집
- 질문글 또는 이미지 모더레이션 정책·worker 변경
- 단계적 거리 확장, 국가별 운영 범위 전환 UI와 SAME_COUNTRY 구현
- #123 인앱 알림 fan-out, 외부 Push, #124 이후 수신함·답변 API
- H3·Redis·Kafka 도입과 GLOBAL 쿼리 성능 최적화; 성능 기준 검증은 #127과 연결한다.
- 새 AWS 인프라, 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Implementation plan

1. **정책과 application 경계**
   - direction post 정책 객체를 추가하고 GLOBAL 거리·TTL·본문·미디어 상한을 한 곳에서
     검증한다.
   - 인증 사용자, ACTIVE USER, 현재 presence, configured active scheme과 서버 시각을
     application 계층에서 해석하고 Controller가 repository를 직접 호출하지 않게 한다.
   - 발신자 대략 지역과 GLOBAL 후보 제한의 의미를 분리한다.
2. **preview와 matching 범위 동기화**
   - preview는 모든 활성 segment를 한 쿼리로 집계하되 region 필터를 사용하지 않는다.
   - worker도 post의 표시 지역을 candidate region 필터로 전달하지 않고 저장된 audience
     거리·방향 snapshot으로 전 세계 후보를 재계산한다.
3. **콘텐츠·멱등 제출 transaction**
   - 본문을 NFC/바깥 공백 기준으로 정규화하고 300 code point 상한을 검증한다.
   - media ID 0/1개, 소유권, READY, 중복 첨부 여부를 검증한다.
   - 현재 `v1` fingerprint에 nullable 본문과 순서가 보존된 media ID를 포함하고
     fingerprint가 없는 nullable 행은 저장된 audience 의도로 복원한다.
   - 질문글, media attachment, audience, Outbox를 한 transaction에 기록한다. 재시도는
     최초 저장 snapshot을 사용하고 현재 정책이나 지역 변경으로 충돌시키지 않는다.
4. **Web·OpenAPI 경계**
   - preview, submit, media upload request/confirm ApiSpec·Controller·DTO를 추가한다.
   - `appAccessToken`, 200/201/202/400/401/403/404/409/503 중 실제 근거가 있는 응답만
     문서화하고 공통 customizer와 중복하지 않는다.
   - `docs/api/openapi.json`은 생성 테스트로만 갱신한다.
5. **검증과 보고**
   - 승인된 테스트 계획의 P0 단위·통합 시나리오를 구현한다.
   - GLOBAL PostGIS, LocalStack upload, 동일 키 경합, transaction rollback, privacy와
     기존 #117/#118/#120/#121 회귀를 실행하고 테스트 보고서를 생성한다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 정책·인증 application 경계와 단위 테스트 | Direction application executor | 서버 권위 값, GLOBAL/표시 지역 분리, Clock, 설정 fail-fast 리뷰 |
| fingerprint·제출 transaction·미디어 연결과 테스트 | Direction submission executor | 현재 v1 fingerprint, media 소유권, 원자성, 동일 키 snapshot 리뷰 |
| GLOBAL preview·worker persistence와 PostGIS 통합 테스트 | Direction persistence executor | preview/worker 범위 동치, region 미필터, 쿼리 성능 리뷰 |
| Controller·request/response DTO·MockMvc | Direction web executor | JWT 소유권, 202 계약, 콘텐츠 조합, 민감정보 비노출 리뷰 |
| ApiSpec·OpenAPI 산출물 | API docs executor | 인증·상태 코드·media schema·privacy 문서 리뷰 |
| 테스트 계획·보고서 | Test orchestrator | 시나리오 증거, LocalStack 환경, 미검증 위험 리뷰 |
| 최종 변경 | Independent reviewer | Issue/TASK/계획과 실제 diff·실행 증거 독립 검증 |

각 실행자는 승인된 계획에서 지정한 파일만 수정하고 다른 실행자나 사용자의 변경을
되돌리지 않는다. 구체적인 비중복 파일 소유권은 테스트 계획 §9를 따른다.

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 clean이었다.
- `./harness start`가 최신 `origin/main` commit
  `a7bab227cde90e22377141bd16b51d14d65ef69f`에서 브랜치를 만들었다.
- 현재 작업 트리에는 이 `TASK.md`와 #122 테스트 계획 문서만 있다.

## Validation

```bash
./gradlew test --max-workers=1 --no-daemon
./gradlew test --tests "com.dnd.qello.direction.web.DirectionPostApiMockMvcTest" --tests "com.dnd.qello.direction.web.DirectionPostWebContractTest" --tests "com.dnd.qello.answer.web.MediaAssetWebContractTest" --max-workers=1 --no-daemon
./gradlew integrationTest --tests "com.dnd.qello.DirectionPreviewIntegrationTest" --max-workers=1 --no-daemon --no-parallel --rerun-tasks
./gradlew integrationTest --tests "com.dnd.qello.DirectionMatchingWorkerIntegrationTest" --max-workers=1 --no-daemon --no-parallel --rerun-tasks
./gradlew integrationTest --tests "com.dnd.qello.MediaAssetStorageIntegrationTest" --max-workers=1 --no-daemon --no-parallel --rerun-tasks
./gradlew integrationTest --tests "com.dnd.qello.MediaAttachmentIntegrationTest" --max-workers=1 --no-daemon --no-parallel --rerun-tasks
./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest" --max-workers=1 --no-daemon --no-parallel --rerun-tasks
./harness test-run --id TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- preview는 인증 사용자와 서버 시각의 현재 위치로 모든 활성 방향 count를 반환한다.
- preview·worker 모두 country/coarse region으로 후보를 제한하지 않고 GLOBAL 거리·방향
  조건을 사용한다.
- 서울-제주와 국가가 다른 fixture가 방향·거리 조건에 맞으면 preview와 worker 후보에
  포함된다.
- 제출 API가 텍스트만, JPEG/PNG 이미지 1장만, 둘의 조합을 허용하고 빈 콘텐츠·2장
  이상·WebP·타인/미확정 media를 거절한다.
- 신규 제출은 질문글·첨부·audience·Outbox를 모두 commit하거나 모두 rollback한다.
- 같은 멱등 요청은 최초 202 결과를 반환하고 본문·media ID가 달라지면 409를 반환한다.
- 제출 API는 수신자를 동기 확정하거나 `PostRecipient`/receive slot을 직접 변경하지 않는다.
- 정확 좌표, 수신자 ID·목록과 storage key가 응답·오류·로그·OpenAPI example에 없다.
  presigned URL은 upload request 성공 응답에만 placeholder schema로 존재하고
  preview·submit·confirm 응답과 오류·로그에는 없다.
- `PT12H`, 300 code points, 이미지 1장, JPEG/PNG와 기존 10 MiB 상한이 설정과 테스트에
  일관되며 정책 숫자를 SQL과 fixture에 중복 하드코딩하지 않는다.
- ApiSpec/Controller 분리, 공통 응답과 생성된 OpenAPI 산출물이 저장소 계약을 지킨다.
- 승인된 P0 테스트와 저장소 필수 검증이 통과하고 테스트 보고서가 남는다.
- 실행하지 못한 검증과 GLOBAL 쿼리의 남은 성능 위험을 보고서에 기록한다.
