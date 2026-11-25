# GitHub Issue #388 Task Contract

> Generated at: `2026-11-25T01:23:02+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `정확 좌표 컬럼 저장 암호화와 매칭 조회 대체 설계`
- GitHub Issue: `#388`
- Branch: `feat/gh-388-coordinate-encryption`
- Base branch: `main`

## Objective

- 위치기반서비스 운영 배포 전 점검표의 작업 C8. `active_user_presence.position`과 `post_audience.origin_position`을 컬럼 단위로 암호화해 저장하고, 평문 PostGIS 연산에 의존하는 매칭 조회를 승인된 대체 방식으로 바꾼다.
- 확인된 사실(2026-11-24):
  - 두 컬럼은 평문 `geography(Point, 4326)`이다(V1 119행·433행). `active_user_presence`에는 GIST 부분 인덱스 `active_user_presence_position_gix`가 있다.
  - `ActiveUserPresenceSql.FIND_CANDIDATES_SQL`, `FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL`이 `ST_DWithin`·`ST_Distance`·`ST_Azimuth`로 거리·방위·역방위를 DB에서 계산한다. `JdbcActiveUserPresenceRepository`, `JdbcPostAudienceRepository`는 `ST_X`·`ST_Y`로 좌표를 복원한다.
  - `qello.direction.post.max-distance-meters` 기본값은 20,100,000이다.
  - `notification/push/security`의 `AesGcmPushTokenProtector`, `PushTokenKeyRing`이 AES-GCM과 key id 회전을 구현하고, test-server는 SSM으로 key를 주입한다(#364).
  - `post_recipient`의 거리·방위는 발송 원점과 함께 읽으면 수신자 위치를 복원할 수 있다.
  - 최신 마이그레이션은 `main` 기준 `V34__add_operator_access_log.sql`이다. #381(PR #384, 병합 대기)이 V35를 쓴다.
- 결정(2026-11-24, @Byuntil):
  - 유형 기능 작업, Priority P0. Issue 하나에서 ADR → 테스트 계획 → 구현 순서로 진행한다.
  - 컬럼 단위 저장 암호화가 필요하다(점검표 Q1 답변, 2026-11-23). RDS 디스크 암호화만으로는 인정받지 못한다고 판단했다.
- 결정(2026-11-25, @Byuntil): ADR D1~D6·D8~D10은 추천안대로 승인했다. D7은 기존 평문 좌표 폐기와 단일 Flyway migration으로 수정 승인했다(운영 DB 없음, dev는 점검표 대상 제외, B1 수집 게이트 전제).
- ADR: `docs/adr/0009-encrypt-coordinate-storage-and-replace-spatial-matching.md`, `accepted`. 승인자 @Byuntil, 2026-11-25, `https://github.com/dnd-side-project/dnd-15th-2-backend/issues/388#issuecomment-7138508452`. 테스트 계획 승인은 별도다.
- Test plan: `docs/test-plans/gh-388-TEST-PLAN-GH-388-COORDINATE-ENCRYPTION.md` — `APPROVED`, 단위 14·통합 24·성능 3. D1~D5 추천안·D6 수정안(개인 OCI arm64 2코어·11GB에서 동일 arm64 PostGIS 이미지/digest·고정 CPU/메모리로 main과 #388 교대 측정, 상대 비교로 판정·절대 지연은 참고값, Docker 설치 별도 승인). 승인자 @Byuntil, 2026-11-25, `https://github.com/dnd-side-project/dnd-15th-2-backend/issues/388#issuecomment-7139174419`.

## Scope

- `docs/adr/`에 좌표 저장 암호화와 매칭 조회 방식 ADR. 대안 A(앱 암호화 + 앱 계산), B(A + 평문 저정밀 셀), C(DB 내부 복호화) 비교, 후보 수 가정과 성능 측정 기준, key 주입·회전, 기존 평문 행 처리, `post_recipient` 파생 값 판단.
- 승인된 안에 따른 Flyway migration(두 좌표 컬럼 암호문 전환, GIST 인덱스 제거 또는 교체).
- `ActiveUserPresenceSql`, `PostAudienceSql`, `JdbcActiveUserPresenceRepository`, `JdbcPostAudienceRepository`와 매칭·미리보기 서비스 변경.
- 좌표 암호화 key 설정. local·test 고정 key와 dev 주입 방식은 ADR 또는 착수 시 정한다.
- `DIRECTION_COMMUNICATION_ERD.md`, `direction_communication.dbml`, `schema-manifest.md` 반영.
- 승인된 테스트 계획에 따른 단위·통합 테스트와 매칭 소요 시간 측정.

## Explicit exclusions

- 좌표 보존 기간과 파기(C6).
- RDS 저장 매체 암호화(I2).
- 운영 SSM 파라미터 등 Terraform 변경.
- `post_recipient` 파생 값의 암호화 구현. 필요하면 별도 Issue로 만든다.
- ADR 승인 전 production 코드와 migration 변경.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `TASK.md`, 범위 | @Byuntil | 사람의 범위 승인 |
| ADR, 테스트 계획 | 계획 에이전트(omo) | 사람의 ADR·계획 승인 |
| 마이그레이션, 조회·암호화 코드, 테스트 | 실행 에이전트(omo) | PR에서 작성자가 아닌 백엔드 한 명 승인 |

## Implementation file ownership

승인자 @Byuntil, 2026-11-25 (Issue #388 댓글에 같은 목록 기록). Java 경로 접두사는 `src/main/java/com/dnd/qello/`다. 테스트 파일은 승인된 테스트 계획의 소유 범위를 따른다.

| 구분 | 파일 | 용도 |
| --- | --- | --- |
| 수정 | `build.gradle` | GeographicLib-Java 2.1(MIT) 의존성, 성능 전용 PostGIS 이미지를 `performanceTest`에만 전달하는 설정 |
| 수정 | `direction/domain/PostAudience.java` | D7: 원점·셀 모두 NULL인 과거 행 복원 허용, 신규 생성 검증 유지 |
| 수정 | `direction/repository/jdbc/JdbcActiveUserPresenceRepository.java`, `direction/repository/jdbc/JdbcPostAudienceRepository.java` | 암호화 저장·복호화 읽기, 앱 측 거리·방위 계산 연결 |
| 수정 | `direction/repository/jdbc/sql/ActiveUserPresenceSql.java`, `direction/repository/jdbc/sql/PostAudienceSql.java` | 평문 PostGIS 연산 제거, 암호문 컬럼 사용 |
| 신규 | `direction/security/**` | 좌표 전용 key ring, AES-GCM envelope(행 결합 AAD), 보호 오류 |
| 신규 | `direction/geo/**` | GeographicLib 기반 거리·방위·역방위 계산, 섹터 판정 |
| 신규 | `direction/config/CoordinateProtectionProperties.java` | 좌표 key 설정 |
| 신규 | `src/main/resources/db/migration/V35__encrypt_exact_coordinates.sql` | D7 단일 migration |
| 수정 | `src/main/resources/application.yml`, `application-local.yml`, `application-dev.yml` | key 설정 자리. local은 fixture 값, dev는 환경변수 이름만 둔다 |
| 수정 | `docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md`, `direction_communication.dbml`, `schema-manifest.md`, `docs/error-codes.md`(새 오류 코드가 있을 때만) | 스키마·오류 문서 |
| 신규·수정 | `docs/reports/tests/gh-388-TEST-PLAN-GH-388-COORDINATE-ENCRYPTION.md` | 테스트 실행 보고 |

추가 승인(@Byuntil, 2026-11-25): 아래 기존 테스트 파일은 기재한 범위로만 수정한다. 테스트 삭제·skip·suppression·옛 SQL 문자열 주입은 금지한다.

| 파일 | 허용 범위 |
| --- | --- |
| `src/test/java/com/dnd/qello/FlywayMigrationContractTest.java` | V35를 목록에 추가하고 최신 버전 기대를 35로 바꾼다. V1·V2·V8 checksum 검증은 유지한다 |
| `src/test/java/com/dnd/qello/direction/DirectionPreviewPersistenceBoundaryTest.java` | 제거된 PostGIS SQL 문자열 고정 대신 한 snapshot 읽기와 후보 0개 segment 보존 같은 행동 계약으로 바꾼다 |
| `src/test/java/com/dnd/qello/direction/DirectionRecipientSelectionBoundaryTest.java` | SQL `ORDER BY` 문자열 고정 대신 필터 조건과 앱 측 공정성 정렬 계약을 검증한다 |
| `src/integrationTest/java/com/dnd/qello/PostgisContainerIntegrationTestSupport.java` | `performanceTest`에서만 이미지·platform을 바꿀 수 있게 한다. 일반 CI·로컬 기본값은 유지한다 |
| `src/integrationTest/java/com/dnd/qello/DevProfileBootIntegrationTest.java` | 기존 `DynamicPropertySource`에 합성 좌표 key fixture만 주입한다. production dev fallback은 넣지 않는다(2차 추가 승인) |
| `src/integrationTest/java/com/dnd/qello/NotificationPreferenceMigrationIntegrationTest.java` | V25~V35 적용 수 기대를 10에서 11로 바꾸고 설명을 갱신한다. 알림 설정 이행 검증은 유지한다(2차 추가 승인) |

성능 기준선 overlay 승인(@Byuntil, 2026-11-25, https://github.com/dnd-side-project/dnd-15th-2-backend/issues/388#issuecomment-7152803870): 성능 하네스를 공통 runner와 adapter로 나누는 변경은 `src/integrationTest/java/com/dnd/qello/` 아래 테스트 전용 파일(기존 성능 파일과 새 runner·adapter 파일)에 한정한다. main 기준선용 평문 adapter는 OCI 기준선 사본에만 적용하고 patch SHA-256과 파일 목록을 테스트 보고서에 남긴다.

허용하지 않는다. 필요하면 멈추고 보고한다.

- `direction/repository/ActiveUserPresenceRepository.java`, `direction/repository/PostAudienceRepository.java`의 메서드 시그니처 변경.
- `direction/matching/DirectionMatchingWorker.java`, `direction/service/**`, `direction/web/**`.
- `notification/push/security/**`(구조 참고만 하고 공통화하지 않는다).
- `config/java-conventions/baseline.json`, `.github/workflows/**`, 다른 도메인 코드.

## Existing user-owned changes

- 작업 시작 시 `git status --short` 출력은 `TASK.md`(task-init) 하나였다.
- `gh`와 `harness`는 `env -u SSL_CERT_FILE -u SSL_CERT_DIR`로 시스템 CA를 사용한다.
- C6(좌표 파기)과 C1(tkv00)이 같은 위치 테이블·경로를 고치므로 착수 순서를 맞춘다.

## Validation

```bash
./harness check
./gradlew test
./gradlew integrationTest
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- ADR이 Accepted 상태이고 사람 승인 기록이 있다.
- 위치 갱신과 발송 후 두 좌표 컬럼에 평문 위도·경도가 없고 key 없이 복원되지 않는다(통합 테스트).
- 같은 입력에서 매칭 후보 집합·순서와 거리·방위·역방위 값이 기존 PostGIS 결과와 ADR에서 정한 허용 오차 안에서 같다(통합 테스트).
- 방향별 후보 수 집계(미리보기)가 기존 결과와 같다(통합 테스트).
- key 회전 후에도 이전 key id로 암호화한 행을 읽는다(단위 테스트).
- 로그와 예외 메시지에 좌표와 key가 나오지 않는다(테스트).
- ADR에서 정한 후보 수 기준으로 매칭 1회 소요 시간을 측정해 기록한다.
- `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check`가 통과한다.
