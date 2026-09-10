# GitHub Issue #223 Task Contract

> Generated at: `2026-09-10T23:54:06+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Codex instruction architecture Phase 2 구조 비용·정책 보존 평가`
- GitHub Issue: `#223`
- Branch: `chore/gh-223-instruction-architecture-phase2`
- Base branch: `main`
- Task ID: `GH-223-INSTRUCTION-ARCHITECTURE-PHASE2`
- Design ID: `HARNESS-DESIGN-GH-223-001`
- Status: `IMPLEMENTATION_APPROVED`
- Intake approval: 사용자가 전체 Issue 초안과 Project/branch/TASK 연결 제안에 `좋아`로 승인했다. 설계·구현·커밋 승인은 별도다.

## Objective

- 모든 baseline 정책을 보존하며 instruction 시작 고정비와 필요 시 추가 로딩 비용을 줄인다.
- 실제 변경 smoke는 안전성과 작업 품질을 판정하는 gate이며 코드 생성 토큰 최소화가 목적이 아니다.

## Scope

- 전체 baseline 정책을 식별하고 후보 위치·적용 시점·강제 수단·검증 시나리오에 mapping한다.
- 역사적 A3 `5b2af27706c7a3f9d6d1593ea35788c074566362`를 입력으로 모든 누락 정책을 복구한 B0를 설계한다.
- 누적 B1: 상세 자연어 규칙을 Skill/reference로 이동한다.
- 누적 B2: 기계 판정 규칙을 Harness·Husky·CI로 이동한다.
- 누적 B3: infra/test 하위 AGENTS.md로 적용 범위를 분리한다.
- 전체 instruction chain의 우선순위, 자율 실행 경계, subagent 조건, 검증 범위를 감사한다.
- gpt-5.6-sol/high와 동일 Codex·플러그인·도구·프롬프트 조건으로 S1~S7 반복 평가한다.
- 실제 변경 smoke는 격리된 문서·테스트 작업으로 제한한다. infra는 읽기 전용 gate와 cwd별 로딩을 평가한다.
- 시작·캐시 입력, 추가 instruction bytes/추정 tokens, 누적 input/output, tool calls, elapsed, gate/routing/false-block 및 품질을 기록한다.
- 원시 로그 대신 재현 가능한 집계와 독립 검증 근거를 기록한다.

## Approval sequence

1. Issue intake 승인 완료. Project draft를 Issue #223으로 전환했다.
2. 상세 설계 섹션별 사람 승인 후 design spec을 작성한다.
3. harness-commit 초안과 사람 승인 후 spec을 커밋하고 문서 검토를 요청한다.
4. spec 검토 승인 후 writing-plans로 구현 계획을 작성하고 승인받는다.
5. 승인된 계획에 따라 B0~B3를 격리해 구현·평가한다.
6. 모든 후속 커밋도 초안과 사람 승인이 필요하다. push/PR/merge는 별도 요청이 필요하다.

## Explicit exclusions

- Terraform 코드 변경, 인프라 적용, 배포와 프로덕션 변경
- GPT-6 Astra 비교 및 reasoning effort 최적화
- 서비스 애플리케이션 동작과 DB 변경
- 원시 Codex 세션 로그 및 민감정보 커밋
- 기존 기본 checkout 및 Phase 1 A0~A3 branch/worktree 변경·정리
- 별도 요청 없는 push·PR·merge
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 정책 mapping·설계·측정 계약 | Orchestrator | Human partner |
| 승인된 후보·평가기·smoke 구현 | Executor | Independent verifier 및 Human partner |
| 정책 보존·결과·품질 검증 | Independent verifier | Human partner |

## Existing user-owned changes

- 원래 checkout과 새 조정 worktree 모두 시작 시 clean이었다. 기존 Phase 1 worktree는 보존한다.
- 조정 worktree만 Issue #223 branch로 전환했다. PR #222는 병합되어 stacked base가 필요하지 않다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [ ] 모든 baseline 정책의 보존 mapping이 완성되고 누락된 안전 불변조건이 복구된다.
- [ ] 설계 섹션·spec·구현 계획 및 커밋의 사람 승인 증거가 기록된다.
- [ ] 각 후보의 안전·Issue·TASK·승인·routing gate가 모든 유효 관측에서 통과한다.
- [ ] false-block과 문서·테스트 smoke 품질을 별도로 판정한다.
- [ ] 구조 비용 산식과 반복 표본 수를 실행 전에 확정한다.
- [ ] 시작 비용과 추가 로딩 비용을 누적 작업 비용과 구분한다.
- [ ] 적격 후보 중 구조 비용 최소안을 선정하거나 적격 후보가 없음을 보고한다.
- [ ] 필수 검증과 독립 검증 결과, 미검증 범위 및 위험을 기록한다.

## Current evidence

- Issue intake 및 Project 연결 완료: P2 / In Progress / Chore / Sprint 미지정.
- 정책 보존 첫 섹션 기준은 사용자 `좋아`로 승인됨. 공통 fixture 충돌 정정 방식은 후속 `승인`으로 승인됨. B1~B3 파일 배치와 B2 검사 범위도 후속 `승인`으로 승인됨. 204개 세션의 평가 설계도 후속 `승인`으로 승인됨. 설계 문서 두 커밋(85cec19, c42ce20) 및 전체 spec 문서 검토도 승인됨. 구현 계획·테스트 계획·실행 계약 문서 커밋도 승인됨. 후속 구현 커밋은 별도 승인 대상이다.
- 정책 원문 색인 및 B0 복원 설계 작업 자료를 작성했다. 후보 파일과 평가 코드는 변경하지 않았다.
