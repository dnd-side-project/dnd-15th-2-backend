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
- Test plan: 미작성. ADR 승인 뒤 `/harness-test-plan`으로 작성한다.

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
