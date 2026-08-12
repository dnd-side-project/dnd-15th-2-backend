# GitHub Issue #106 Task Contract

> Generated at: `2026-08-12T09:47:58+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `[C] 필터링 시스템 — 닉네임 동기 필터 (F03)`
- GitHub Issue: `#106`
- Branch: `feat/gh-106-nickname-sync-filter`
- Base branch: `main`

## Objective

- 사용자가 닉네임을 설정·변경할 때 동기적으로 moderation을 통과해야 적용되는
  fail-closed 판정 훅을 구현한다.
- 닉네임은 별도 도메인이 아니라 `Account`(`user_account` 테이블)의 필드다 —
  이 이슈는 `Account` 엔티티를 수정하지 않고, 기존 호출 지점이 사용할 동기
  판정 훅만 제공한다.
- 답변(비동기) 처리 부하와 완전히 분리된 실행 자원을 사용한다
  (`INV-RES-002`~`004`).

## Scope

1. 답변 worker와 분리된 실행 풀, concurrency, timeout, quota와 지표를 갖는
   닉네임 전용 동기 실행 경계를 만든다.
2. `#105`가 만든 `ModerationPipelineService`/`ModerationProviderClient`
   (`filtering.moderation` 패키지)를 재사용해 주 판정기 호출 경로를 구성한다
   — pipeline 오케스트레이션 로직 자체는 변경하지 않는다.
3. 주 판정기의 `ALLOW`/`BLOCK`/timeout/error 결과를 각각 명확히 구분해
   처리한다.
4. 주 판정기가 timeout/error일 때만 독립 보조 판정기를 순차 호출한다
   (`INV-NICK-003`).
5. 주 판정기의 명시적 `BLOCK`은 확정 결과로 취급하고 보조 판정기가 재판정하지
   못하도록 권한 경계를 둔다(`INV-NICK-002`).
6. 주·보조 판정기가 모두 timeout/error이면 fail-closed로 닉네임 적용과
   서비스 진입을 거부한다(`INV-NICK-005`).
7. 최초 설정 실패와 변경 실패 모두 서비스 진입을 차단하고, 변경 실패 시
   기존 닉네임 유지나 임시 닉네임 발급으로 우회하지 않는다
   (`INV-NICK-006`, `INV-NICK-007`).
8. 신규 REST endpoint는 만들지 않는다 — 호출자(#73 `DeviceRegistrationService`
   등 Account/Auth 담당)가 사용할 동기 판정 훅(메서드/서비스)만 제공한다.

## Explicit exclusions

- `Account.createUser`/`Account.updateProfile` 호출부 수정 — Account/Auth
  담당 영역이며 별도로 조율한다. 이 이슈는 훅 자체만 만들고 실제 연결은
  포함하지 않는다.
- 독립 보조 판정기의 실제 공급자와 주 판정기와의 공통 장애 영역 확정 —
  미결정, production 차단 게이트.
- 동기 timeout, 예약 용량, quota, 사용자 오류 안내 수치 — 미결정
  (`INVARIANTS.md` §11). configuration 자리는 두되 운영 기본값으로 하드코딩
  하지 않는다.
- `user_account` 테이블 스키마 변경, 신규 마이그레이션 — 이 이슈는 DB를
  수정하지 않는다.
- 신규 REST endpoint 추가.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 닉네임 동기 판정 훅(전용 실행 자원, 주·보조 판정기 순차 호출, fail-closed 경계) | Feature executor | 답변 경로와의 자원 격리, `BLOCK` 재판정 금지, timeout/error의 `ALLOW` 전환 금지, fail-closed 통합 검증 |

## Existing user-owned changes

- `main`(#105 `feat/gh-105-moderation-pipeline` 병합 직후, commit `2d6aba2`)
  에서 새로 분기했다(`./harness start --issue 106 --type feat --slug
  nickname-sync-filter`). 분기 시점 `git status --short`는 `task-init`이
  갱신한 `TASK.md` 외에는 비어 있었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [ ] 답변 backlog와 장애 부하가 닉네임 예약 용량을 소진하지 않는다
      (`INV-RES-003`, `INV-RES-004`).
- [ ] 판정 불가를 `ALLOW`로 바꾸는 경로가 없다(`INV-GEN-002`).
- [ ] 보조 판정기 장애까지 포함한 fail-closed 통합 검증을 통과한다
      (`INV-NICK-001`~`007`).
- [ ] 주 판정기의 명시적 `BLOCK`을 보조 판정기가 뒤집지 못한다
      (`INV-NICK-002`).
- [ ] 최초 설정과 변경 실패 모두 서비스 진입을 차단하며, 임시/기존 닉네임
      우회 경로가 없다(`INV-NICK-006`, `INV-NICK-007`).
- [ ] 단위 테스트와 통합 테스트가 추가된다(정상 경로, timeout, 중복, 순서
      역전, 부분 장애 포함).
