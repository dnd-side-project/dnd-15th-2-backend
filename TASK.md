# GitHub Issue #379 Task Contract

> Generated at: `2026-11-23T16:31:34+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `위치기반서비스 신고 수리 전 실제 좌표 수집 차단 게이트`
- GitHub Issue: `#379`
- Branch: `feat/gh-379-location-collection-gate`
- Base branch: `main`

## Objective

- 위치기반서비스 운영 배포 전 점검표의 작업 B1. 위치기반서비스사업 신고 수리 참조가 설정되기 전에는 정확 좌표를 받거나 읽는 API를 서버에서 거부한다.
- 확인된 사실(2026-11-23):
  - 정확 좌표는 `PUT /api/v1/direction/presence`(`ActiveUserPresenceController`)로만 들어와 `active_user_presence`에 저장된다.
  - `GET /api/v1/direction/preview`와 `POST /api/v1/direction/posts`는 `DirectionPostService.activeSender`로 발신자 presence 좌표를 읽고, 발송은 `post_audience.origin_position`에 원점을 복사한다.
  - 참고 구현은 `filtering/gate/FilteringProductionGate`(fail-closed, placeholder 거부, 8자 이상 참조)다.
- 결정(2026-11-23, @Byuntil):
  - 유형 기능 작업, Priority P0.
  - dev·local은 게이트를 켜 둔다. 신고 참조 검사 생략은 활성 프로필이 dev 또는 local일 때만 허용한다. `application.yml` 기본값은 꺼짐이다.
  - 거부 응답은 `DirectionErrorCode.LOCATION_COLLECTION_DISABLED`(`DIR-APP-008`), HTTP 503.
- Test plan: `docs/test-plans/gh-379-TEST-PLAN-GH-379-LOCATION-GATE.md` — `APPROVED` — 단위 8개 + 통합 5개, 설계 결정 D1~D6 제안대로 확정, 승인자 @Byuntil, 2026-11-23. 승인 증거: https://github.com/dnd-side-project/dnd-15th-2-backend/issues/379#issuecomment-7094564968

## Scope

- 게이트 설정: `qello.location.collection.enabled`(기본 `false`), `qello.location.collection.lbs-registration-reference`. `enabled: true`인데 참조가 비었거나 placeholder면 dev·local이 아닌 프로필에서 기동 실패.
- `src/main/resources/application.yml`, `application-dev.yml`, `application-local.yml`.
- 거부 대상 API 세 개와 `DirectionErrorCode` 새 코드. 거부 시 `active_user_presence`, `direction_post`, `post_audience`에 행을 쓰지 않는다.
- `ActiveUserPresenceApiSpec`, `DirectionPostApiSpec`의 오류 응답 문서.
- `docs/location-collection-gate.md`.
- 승인된 테스트 계획에 따른 단위·통합 테스트. 기존 위치·발송·미리보기 통합 테스트는 게이트를 켠 설정으로 통과시킨다.

## Explicit exclusions

- 위치정보 이용 동의 확인과 동의 이력 저장(C9).
- 저장된 좌표의 파기(C6).
- `DirectionMatchingWorker` 차단.
- 운영 프로필 추가(C7).
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `TASK.md`, 범위 | @Byuntil | 사람의 범위 승인 |
| 테스트 계획 | 실행 에이전트(`/harness-test-plan`) | 사람의 계획 승인 |
| 게이트 코드, 설정, 문서, 테스트 | 실행 에이전트 | PR에서 작성자가 아닌 백엔드 한 명 승인 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 출력은 `TASK.md`(task-init) 하나였다.
- `gh`와 `harness`는 `env -u SSL_CERT_FILE -u SSL_CERT_DIR`로 시스템 CA를 사용한다.
- C1(tkv00)·C6·C9가 같은 위치 처리 경로를 고치므로 착수 순서를 tkv00과 맞춘다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- 게이트가 꺼진 상태에서 세 API가 `LOCATION_COLLECTION_DISABLED`를 반환하고 관련 테이블 행 수가 변하지 않는다(통합 테스트).
- `enabled: true`이고 신고 참조가 비었거나 placeholder면 dev·local이 아닌 프로필에서 context 기동이 실패한다(통합 테스트).
- dev 프로필에서 게이트가 켜져 있고 위치 갱신이 성공한다(`DevProfileBootIntegrationTest`).
- 기존 위치·발송·미리보기 통합 테스트가 게이트를 켠 설정으로 통과한다.
- `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check`가 통과한다.
