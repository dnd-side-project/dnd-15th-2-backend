# GitHub Issue #105 Task Contract

> Generated at: `2026-08-11T20:37:13+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `필터링 시스템 공통 moderation pipeline 구현 (F02)`
- GitHub Issue: `#105`
- Branch: `feat/gh-105-moderation-pipeline`
- Base branch: `main`

## Objective

닉네임(동기)과 답변(비동기)이 공유하는 공통 moderation 판정 pipeline을 구현한다.
정규화 → 고신뢰 로컬 규칙 → 고정 OpenAI snapshot → 내부 정책 결합 순서로
처리하며, 공급자의 단일 `flagged` 값을 그대로 최종 판정으로 사용하지 않는다.

## Scope

1. 입력 텍스트와 언어·콘텐츠 종류(닉네임/답변)를 받는 pipeline 진입점을 만든다.
2. `FilterRelease`(#103/#104)에 귀속된 방식으로 입력을 정규화한다.
3. 고신뢰 로컬 규칙의 명확한 `BLOCK`을 먼저 적용하고, 적중 시 OpenAI 호출 없이
   결과를 확정한다.
4. 규칙에서 차단하지 않은 입력만 release에 고정된 OpenAI snapshot으로 전송한다.
5. 공급자 응답(`flagged`, `categories`, `category_scores`, 실제 `model`)을
   벤더 중립 내부 결과로 변환하는 어댑터를 만든다.
6. 내부 정책이 category, score, 콘텐츠 종류와 언어를 해석해 최종
   `ALLOW`/`BLOCK`을 결정한다.
7. OpenAI 호출의 timeout/error는 임의의 `ALLOW`/`BLOCK`으로 변환하지 않고
   판정 불가로 호출 경로에 그대로 반환한다.
8. 규칙 적중, 모델 응답(raw)과 최종 정책 결정을 각각 별도로 관측·기록할 수
   있게 한다(`FilterDecision` 확장 또는 연관 기록).
9. pipeline 실행 자원(스레드풀·동시성·설정)을 닉네임 동기 경로와 답변 비동기
   경로가 재사용하지 못하도록 호출자가 경로별로 격리해 구성할 수 있는 구조로
   만든다(실제 격리 배선은 #106/#107 소관, 이 이슈는 공유 불가능한 구조만
   보장한다).

## Explicit exclusions

- 정규화 규칙, 고신뢰 로컬 사전, category mapping과 한국어·영어 threshold의
  구체 값 — 평가 후 확정 예정이며 이 이슈에서 최종 확정하지 않는다.
- 보조 판정기(fallback) 구체 공급자·독립성 기준 — 닉네임 동기 필터 이슈(#106)
  에서 다룬다.
- 닉네임 동기 API와 답변 비동기 워커의 실제 연동(#106, #107).
- `FilterRelease` registry 자체의 생성·승격·rollback(#104에서 이미 구현).
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| moderation pipeline 서비스(정규화·로컬 규칙·OpenAI 어댑터·정책 결합) | Feature executor | `flagged` 직접 사용 금지, timeout/error 판정 변환 금지, 규칙/모델/최종결정 관측 분리 경계 검토 |

## Existing user-owned changes

- 브랜치는 F01(#104)의 release registry 승격·rollback API가 병합된 직후
  `origin/main`에서 새로 분기했다(`./harness start --issue 105 --type feat
  --slug moderation-pipeline`). 분기 시점 `git status --short`는 `task-init`이
  갱신한 `TASK.md` 외에는 비어 있었다.
- `./harness sync`로 `origin/main`(issue #115 병합 포함)을 재반영하는 과정에서
  `TASK.md`가 #115 브랜치의 내용과 충돌했다 — 각 기능 브랜치가 자신의
  `TASK.md`를 소유하므로 이 브랜치(#105) 내용으로 해결했다. 코드 충돌은 없었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 공급자의 단일 `flagged` 값이 최종 판정을 직접 결정하지 않는다
      (`INV-PIPE-003`) — UNIT-003, UNIT-004로 검증.
- [x] 규칙 적중, 모델 응답과 최종 정책 결정을 각각 관측할 수 있다
      (`INV-PIPE-005`) — UNIT-008로 검증.
- [x] 닉네임과 답변이 정책·결과 계약을 공유하되 실행 용량은 공유하지 않는
      구조다(`INV-RES-001`, `INV-RES-002`) — `ModerationPipelineService`를
      Spring 빈으로 만들지 않아 호출자마다 독립 인스턴스를 구성한다. UNIT-013은
      독립적으로 생성된 두 pipeline 인스턴스가 서로의 실행을 블로킹하지
      않음을 순수 자바 동시성 테스트로 검증하지만, 실제 프로덕션 배선(각
      경로가 실제로 별도 executor/RestClient를 갖는지)은 #106/#107이 구성한
      뒤에 검증 가능하다 — 그 전까지는 부분 검증으로 취급한다.
- [ ] `INV-PIPE-001`, `002`, `004`를 위반하지 않는다 — 이슈 본문과 저장소
      어디에도 정의가 없어 검증하지 못했다. `확인 필요`로 남긴다.
- [x] 고신뢰 로컬 규칙이 명확한 `BLOCK`을 반환하면 OpenAI를 호출하지 않는다 —
      UNIT-001, INT-002로 검증.
- [x] OpenAI timeout/error 시 임의의 `ALLOW`/`BLOCK`으로 대체하지 않고 호출자에
      판정 불가로 반환한다 — UNIT-005, UNIT-006, INT-003, INT-007(HTTP 5xx)로 검증.
- [x] 단위 테스트와 통합 테스트가 추가된다 — unit 13개, integration 6개
      (INT-006은 Spring 빈 구성이 없어 대상이 없음, UNIT-013이 인스턴스 간
      비공유 속성만 대체 검증; INT-007은 PR 리뷰로 추가된 5xx 오류 변환 커버리지).
      보고서: `docs/reports/tests/gh-105-TEST-PLAN-GH-105-MODERATION-PIPELINE.md`.
