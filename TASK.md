# GitHub Issue #381 Task Contract

> Generated at: `2026-11-23T22:10:18+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `위치정보 이용 동의·철회 이력 저장과 미동의 사용자 위치 수집 거부`
- GitHub Issue: `#381`
- Branch: `feat/gh-381-location-consent-history`
- Base branch: `main` (2026-11-24 rebase 반영).

## Objective

- 위치기반서비스 운영 배포 전 점검표의 작업 C9. 앱에서 받은 위치정보 이용 동의·철회를 서버에 이력으로 남기고, 현재 약관 버전에 동의한 사용자만 정확 좌표 API를 쓸 수 있게 한다.
- 확인된 사실(2026-11-23):
  - 저장소에 위치정보 동의·약관 관련 코드와 테이블이 없다.
  - 정확 좌표 API는 `PUT /api/v1/direction/presence`, `GET /api/v1/direction/preview`, `POST /api/v1/direction/posts` 세 개이며, #379에서 각 진입 메서드 첫 줄에 `LocationCollectionGate.ensureEnabled()`를 넣었다.
  - 최신 마이그레이션은 `V35__add_location_consent_history.sql`이다(2026-11-24 rebase 후). main의 V34 operator_access_log는 보존한다.
- 결정(2026-11-23, @Byuntil):
  - 유형 기능 작업, Priority P0.
  - 위치정보 이용 동의는 앱 안에서 받고, 약관은 별도 Qello 홈페이지에 게시한다(점검표 Q5 답변).
  - 철회 시 그 사용자의 `active_user_presence` 행은 같은 트랜잭션에서 삭제한다. 시간 경과 파기는 C6에 남긴다.
- Test plan: `docs/test-plans/gh-381-TEST-PLAN-GH-381-LOCATION-CONSENT.md` — `APPROVED` — 단위 10개 + 통합 15개, D1·D2·D3·D4·D7·D8 추천안대로, D5 수정안(YAML 기록·리뷰로만 변경, B1 켜짐 시 필수), D6 추천안 A(모바일 동의 화면 출시 후 병합). 승인자 @Byuntil, 2026-11-23. 승인 증거: https://github.com/dnd-side-project/dnd-15th-2-backend/issues/381#issuecomment-7102530621

## Scope

- 동의·철회 이력 테이블 Flyway 마이그레이션. 사용자, 약관 버전, 동의 또는 철회 구분, 서버 기록 시각. 요청마다 행을 추가하고 기존 행은 수정하지 않는다.
- 인증 사용자 본인의 동의, 철회, 현재 동의 상태 조회 API.
- 현재 약관 버전 서버 설정. 현재 버전이 아닌 약관 버전의 동의 요청은 거부한다.
- 위치 API 세 개에서 현재 약관 버전 미동의 사용자 거부. B1 게이트 검사 다음에 확인한다.
- 철회 시 `active_user_presence` 행 즉시 삭제.
- 새 오류 코드, `*ApiSpec` 명세와 `docs/api/openapi.json`, `docs/error-codes.md`.
- 승인된 테스트 계획에 따른 단위·통합 테스트.

## Explicit exclusions

- 약관 문서 작성과 홈페이지 게시, 약관 버전·시행일 표시.
- 시간 경과에 따른 좌표 파기(C6).
- 위치정보 제3자 제공 동의.
- 만 14세 미만 법정대리인 동의 처리.
- 기존 dev 사용자에 대한 동의 기록 일괄 생성.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `TASK.md`, 범위 | @Byuntil | 사람의 범위 승인 |
| 테스트 계획 | 계획 에이전트(omo, `/harness-test-plan`) | 사람의 계획 승인 |
| 동의 도메인, API, 마이그레이션, 테스트 | 실행 에이전트(omo) | PR에서 작성자가 아닌 백엔드 한 명 승인 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 출력은 `TASK.md`(task-init) 하나였다.
- `gh`와 `harness`는 `env -u SSL_CERT_FILE -u SSL_CERT_DIR`로 시스템 CA를 사용한다.
- C1(tkv00)도 위치 처리 경로를 고치므로 착수 순서를 tkv00과 맞춘다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- 동의 기록이 없는 사용자, 철회한 사용자, 이전 약관 버전에만 동의한 사용자의 위치 갱신이 거부되고 `active_user_presence` 행이 바뀌지 않는다(통합 테스트).
- 현재 약관 버전에 동의한 사용자의 위치 갱신이 저장된다(통합 테스트).
- 미리보기와 발송도 같은 조건에서 거부된다(통합 테스트).
- 동의·철회 요청마다 이력 행이 하나씩 추가되고 기존 행은 바뀌지 않는다(통합 테스트).
- 철회 후 그 사용자의 `active_user_presence` 행이 없다(통합 테스트).
- `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check`가 통과한다.
