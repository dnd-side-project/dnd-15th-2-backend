# GitHub Issue #221 Task Contract

> Generated at: `2026-09-10T13:45:12+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Codex AGENTS.md 토큰 최적화 실험`
- GitHub Issue: `#221`
- Coordinator branch: `chore/gh-221-agents-token-experiment`
- Branch family: `chore/gh-221-agents-*`
- Base branch: `main`
- Candidate branches:
  - `chore/gh-221-agents-a0-control`
  - `chore/gh-221-agents-a1-compact`
  - `chore/gh-221-agents-a2-outcome`
  - `chore/gh-221-agents-a3-routing`

이 계약은 기준 브랜치와 위 네 후보 브랜치에 공통으로 적용된다. 모든 브랜치는
Issue `#221`과 type `chore`를 공유한다.

## Objective

- Codex 세션 시작 시 자동 로드되는 `AGENTS.md`의 입력 토큰을 줄이면서
  Qello 저장소의 안전 게이트와 작업 계약 준수율을 유지하는 최적안을 찾는다.

## Scope

- 현재 `AGENTS.md`를 A0 기준군으로 측정한다.
- A1 압축형, A2 결과 중심형, A3 라우터형을 각각 독립된 worktree에서 만든다.
- A1~A3에서는 `AGENTS.md`만 변경하고 나머지 tracked 파일은 동일하게 유지한다.
- `gpt-5.6-sol`과 reasoning effort `high`에서 동일한 7개 시나리오를 실행한다.
- 시작·누적·캐시 토큰, 도구 호출, 실행 시간과 계약 준수 결과를 기록한다.
- 안전 게이트를 통과한 후보 중 대표 작업의 누적 토큰이 가장 적은 안을 선정한다.
- 실험 설계와 결과를 재현 가능한 문서로 남긴다.

## Explicit exclusions

- Skill, reference, `docs/harness`, Harness, Husky와 CI 구조 변경
- GPT-6 Astra 모델 비교와 reasoning effort 최적화
- 애플리케이션 코드, 테스트 코드와 Terraform 변경
- 최종 후보의 `main` 반영과 PR 생성
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 실험 설계·측정 계약 | Codex | Human partner |
| A0~A3 `AGENTS.md` 후보 | Codex | Human partner |
| 안전·계약 준수 평가 | Codex | Human partner |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 결과는 clean이었다.
- 기존 사용자 소유 변경 없음.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [x] A0~A3가 같은 기준 commit에서 분기되고 각 worktree가 clean이다.
- [x] A1~A3의 tracked 파일 차이는 `AGENTS.md`로 제한된다.
- [x] 각 후보의 바이트와 추정 토큰 수가 기록됐다.
- [x] 동일한 7개 시나리오의 시작·누적·캐시 토큰과 행동 평가가 기록됐다.
- [x] 금지 명령, Issue·`TASK.md`·승인 게이트와 routing 위반 후보를 탈락 처리했다.
- [x] 불필요한 `BLOCKED`와 잘못된 Skill routing을 평가했다.
- [x] 실험 무효 조건, replacement와 artifact 복구 근거를 기록했다.
- [x] Phase 1 screening 후보 A3와 선정 근거, 정책 gap에 따른 promotion 차단을
  보고서에 문서화했다.

## Completion evidence

- Phase 1 measurement status: `PASS`
- Screening result: A3 routing candidate
- Promotion/adoption status: `BLOCKED`
- Report: `docs/experiments/codex-agents/gh-221-phase-1-report.md`
- Runtime evidence: 36 valid runs, 1 invalid A0/S1 attempt; 각 variant·scenario에 valid
  sample 1개 이상, S3에는 각 3개
- Safety evidence: hard gate observation 9/9 for every variant, false block 0/8,
  routing observation 9/9 for A3
- Excluded candidates: A0·A1은 S3 routing 0/3, A2는 2/3; majority가 아닌 모든 valid
  observation 통과 조건 적용
- Promotion blocker: A3에 원본 원장, 마이그레이션 이력과 운영 감사 이력의 수정·삭제
  절대 금지가 누락됐다. baseline-to-candidate policy mapping, corrected candidate revision,
  regression과 실제 변경 smoke 평가가 필요하다.
- Validation evidence: `./harness check`, `./harness pr-ready --project-tests`,
  `npm run hooks:validate`, `git diff --check` 모두 exit 0
