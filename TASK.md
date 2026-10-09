# GitHub Issue #337 Task Contract

> Generated at: `2026-10-09T17:42:21+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `회원 탈퇴(30일 유예)와 계정 차단 진입점 — 기기 자격증명 폐기 연동`
- GitHub Issue: `#337`
- Branch: `feat/gh-337-account-withdrawal`
- Base branch: `main`

## Objective

`Account.block()`·`unblock()`·`delete()`를 호출하는 코드가 없고 `DeviceCredential`을 REVOKED로 바꾸는 코드도 없어서
`DeviceTokenService.reissue`의 계정 상태 확인이 실제로 쓰이지 않는다. 앱에서 30일 유예 탈퇴를 요청·철회할 수 있게 하고,
F08이 호출할 차단 진입점을 제공해 계정 상태가 토큰 재발급·매칭·답변 목록에 반영되게 한다.

## Scope

- 계정 상태:
  - `AccountStatus.WITHDRAWAL_PENDING`을 추가한다. V33에서 `ck_user_account_status`에 값을 추가하고 `withdrawal_requested_at`
    컬럼, 상태와 컬럼이 함께 존재하는 CHECK, 만료 조회용 부분 인덱스를 만든다.
  - 상태 전이: ACTIVE → WITHDRAWAL_PENDING(요청), WITHDRAWAL_PENDING → ACTIVE(철회), WITHDRAWAL_PENDING → DELETED(유예 만료),
    ACTIVE ↔ BLOCKED(F08). 유예 중 계정의 차단 요청은 거절한다.
  - 유예 기간은 `qello.account.withdrawal.grace-period`(기본 `P30D`)로 주입한다.
- 탈퇴 요청 `POST /api/v1/users/me/withdrawal`: 한 트랜잭션에서 계정을 WITHDRAWAL_PENDING으로 바꾸고, `push_device`를 모두
  REVOKED로 바꿔 미발송 `notification_delivery`를 취소하고, `active_user_presence` 행을 삭제한다. 기기 자격증명은 철회를
  위해 유지한다. 응답에 삭제 예정 시각을 담는다.
- 철회 `DELETE /api/v1/users/me/withdrawal`: WITHDRAWAL_PENDING 계정만 ACTIVE로 되돌린다. 푸시 등록과 위치는 앱이 다시 보낸다.
- 유예 만료 워커: 기존 sweep 워커 방식으로 유예 기간이 지난 계정을 DELETED로 바꾸고 닉네임을 NULL로 비우고 기기 자격증명을
  전부 REVOKED로 바꾼다. 유예 중 다시 등록된 푸시 기기도 전부 해지한다. 상태 조건부 갱신으로 중복 실행에 안전하게 만든다.
- 기기 자격증명: `DeviceCredential.revoke(at)`와 사용자 단위 일괄 폐기를 추가한다. `DeviceTokenService.reissue`는
  WITHDRAWAL_PENDING 계정에도 토큰을 발급하고 `DeviceTokenResponse`에 계정 상태를 추가한다. BLOCKED·DELETED는 403을 유지한다.
- 차단 진입점: account 모듈에 `block(userId)`·`unblock(userId)` 서비스를 둔다. 상태만 바꾸고 기기 자격증명은 폐기하지 않는다.
- 읽기·매칭 반영:
  - `DirectionMatchingWorker`는 발신자가 ACTIVE가 아니면 수신자를 만들지 않고 이벤트를 완료한다.
  - `AnswerListingResponse`에 작성자 탈퇴 여부를 추가한다. WITHDRAWAL_PENDING·DELETED 작성자는 닉네임을 null로 내리고 앱이
    "탈퇴한 사용자"로 표시한다. 답변 본문은 남긴다.
  - `AnswerPublicationBlockChecker` 등 계정 상태로 분기하는 기존 코드에 WITHDRAWAL_PENDING을 반영한다.
- 문서: 해당 `*ApiSpec`, `docs/api/openapi.json`, `docs/product/AUTH_DESIGN.md`를 갱신한다.

## Explicit exclusions

- 앱 로그아웃, 분실 기기 폐기. 복구 코드(AUTH_DESIGN §8.1) 결정 후 별도 이슈로 다룬다.
- 즉시 차단 캐시(AUTH_DESIGN §4.6 2단계). 차단 반영은 액세스 토큰 TTL(30분)만큼 늦을 수 있다.
- 차단 운영자 API, 차단 사유와 감사 이력(F08).
- Google Play 데이터 삭제 안내 웹 페이지(스토어 등록 요건). 백엔드 범위가 아니다.
- 탈퇴 계정 개인정보 물리 파기 배치, 탈퇴자 답변 삭제.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 계정 상태·V33·탈퇴/철회 API·만료 워커·자격증명 폐기·차단 진입점·매칭 gate·답변 목록 표시 | 실행 에이전트 | 사용자 PR 리뷰 |
| 테스트 수정·추가 | 실행 에이전트 | `/harness-test-plan` 승인 후 작성 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(`4c7bcef8`)에서 worktree
  `.claude/worktrees/gh-337-account-withdrawal`로 분기했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 탈퇴 요청 후 질문 발송·답변 제출·위치 갱신이 403을 반환한다.
- 탈퇴 요청 후 그 사용자는 매칭 후보와 알림 fan-out에서 빠지고, 그 사용자의 진행 중 질문글은 수신자를 만들지 않는다.
- 유예 중 같은 기기로 재발급 후 철회하면 ACTIVE로 돌아오고 닉네임이 유지된다.
- 유예 만료 후 계정은 DELETED, 닉네임은 NULL, 기기 자격증명은 전부 REVOKED이고 재발급이 거절된다.
- 만료 후 다른 계정이 같은 닉네임을 쓸 수 있고, 옛 답변은 탈퇴 작성자로 표시된다.
- 차단 후 재발급이 403이고, 해제 후 같은 기기로 재발급이 성공한다.
- 탈퇴 요청·철회·만료 워커를 중복 실행해도 상태가 같다.
- `./harness check`와 `./harness pr-ready --project-tests`가 통과한다.

## Decisions

- 2026-10-09 사용자 결정: 계정 차단은 F08 담당, 탈퇴와 기기 폐기는 로그인 담당으로 범위를 나눈다.
- 2026-10-09 사용자 결정: 탈퇴한 작성자는 "탈퇴한 사용자"로 표시한다. 공개된 답변은 남긴다.
- 2026-10-09 사용자 결정: 탈퇴자의 진행 중 질문글은 매칭 gate로 멈추고 만료 처리로 닫는다.
- 2026-10-09 사용자 결정: 앱 로그아웃과 분실 기기 폐기는 미룬다.
- 2026-10-09 사용자 결정: 탈퇴 유예 기간은 30일이다(Apple 5.1.1(v), Google Play 요건과 Instagram·X 사례 검토 후).
- 2026-10-09 사용자 결정: 테스트 계획 `TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL` 승인. 계획 9절의 가정 A1~A10을 채택한다.
  A4에 따라 유예 만료 처리에서 푸시 기기를 한 번 더 전부 해지한다. A7에 따라 `DeviceTokenService`를 method 단위
  `@Transactional`로 바꾸고 `ProductionConventionAuditTest`의 단언을 갱신한다.
