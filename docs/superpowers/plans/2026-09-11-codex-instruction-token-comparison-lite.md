# Codex Instruction Token Comparison Lite Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development or superpowers:executing-plans. Review checkpoints are the two grouped checkpoints below, not each task or model session.

**Goal:** 동일한 대표 읽기 전용 작업에서 B0~B3의 시작 및 누적 토큰 사용량과 답변 충족 여부를 비교한다.

**Architecture:** 완료된 후보와 Task 7 도구를 재사용한다. 별도 lite 평가 profile과 산출물을 사용하여 보류된 full 평가를 보존한다. pilot 2회와 본 비교 24회를 분리한다.

**Tech Stack:** Codex CLI 0.153.4, gpt-5.6-sol/high, Python, 기존 tokenizer, Git worktree.

**Spec:** 이 문서의 평가 계약은 기존 HARNESS-DESIGN-GH-223-001의 남은 실행 범위를 대체하는 승인된 변경 계약이다. 기존 spec의 후보 정의·정책 보존·민감정보 보호는 유지한다.

## 상태와 승인

- Plan ID: GH-223-TOKEN-COMPARISON-LITE-001
- Status: ACTIVE_APPROVED_PLAN
- Issue: #223 / Task: GH-223-INSTRUCTION-ARCHITECTURE-PHASE2
- 사용자 승인: “이대로 계획 수정해주고 기존 계획은 우선은 지우지 말고 비활성화만 해줘 추후에 해볼수도 있을것 같아서”. 이는 직전 제안한 26회 평가와 기존 계획 보류에 대한 승인이다.
- 이번 변경은 문서 작성만 수행한다. 평가 도구 변경과 실제 모델 실행은 아직 시작하지 않았다. 후속 커밋은 기존 계약대로 초안 승인 후 수행한다.
- 기존 계획: [204회 full 계획](2026-09-11-codex-instruction-architecture-phase2.md), PAUSED_BY_USER. Task 1~7 완료 결과는 보존하고 Task 8~12만 보류한다.
- Task 7 커밋: 1edeb3e(도구), 3a8f8f7(환경·순서). 기존 full manifest와 순서는 실행 결과가 아닌 미실행 계획이다.
- 이전 Adaptive 68~140회 논의는 채택하지 않는다. 현재 계약은 24회 고정 비교이며 자동 확장하지 않는다.

## 평가 계약

| 구간 | 표본 | 목적 |
| --- | --- | --- |
| pilot | B0 1회 + B3 1회 = 2회 | 실제 usage와 계측 결과 직접 대조 |
| 본 비교 | 3작업 × 4후보 × 2반복 = 24회 | 대표 작업의 토큰 사용량 비교 |

대표 작업은 일반 저장소 작업 파악, 테스트 영역 작업 계획, 인프라 영역 작업 계획이다. 모두 read-only이며 실제 파일 수정·Gradle·Terraform 실행을 요구하지 않는다. Task 8에서 기존 프롬프트 재사용 가능성을 확인하고 세 exact prompt, 시작 cwd, 답변 체크리스트를 실행 전에 고정한다. 각 후보에는 동일한 프롬프트/cwd 관계를 적용한다. 명시적 작성·테스트 실행 요구가 있는 기존 prompt는 그대로 재사용하지 않는다.

- 모델/effort/CLI, 외부 global/plugin/tool 구성, 후보 commit과 source hash를 고정한다. 설치 버전이 다르면 자동 변경하거나 대체하지 않고 차이를 보고한다.
- 첫 input/cache, 최종 누적 input/cache/output, elapsed, 도구 호출 및 추가 지침 읽기를 기록한다. 전체 실제 usage와 저장소 instruction 추정치를 혼동하지 않는다.
- child가 발생하면 부모와 분리해 기록한 뒤 thread ID로 중복 없이 합산한다. 26은 부모 세션 수이며 child를 포함한 총 호출/토큰 수는 별도로 보고한다. 세션별 검토용 child를 추가하지 않는다.
- 총 input에는 cache가 이미 포함되므로 cache를 다시 더하지 않는다. 캐시 변동을 공개하고 임의 비용 단가나 미측정 수치를 만들지 않는다.
- 답변은 사전 체크리스트로 충족/미충족/판정불가를 기록한다. 답변 실패 결과를 삭제하거나 성공할 때까지 반복하지 않는다. 덜 수행한 답변을 비용 우위로 해석하지 않는다.
- 같은 작업의 후보를 가까운 순서에 실행하고 반복별 후보 순서를 회전한다. 24행 순서와 prompt hash를 pilot 전 고정하고 두 반복으로 완전한 위치 균형이 되지 않는 한계를 기록한다.
- pilot은 비용 순위 선정에 사용하지 않는다. 계측 실패 시 중단하고 수정 및 재-pilot 필요성을 보고한다. 자동 재시도·표본 확대는 하지 않는다.
- 본 비교는 24회 후 종료한다. 차이가 작거나 반복 간 순위가 바뀌면 우열 불명확으로 보고한다. 중단/누락은 PARTIAL 또는 BLOCKED로 보고하고 완료로 처리하지 않는다.
- 측정 불가 값은 null과 이유로 기록한다. 필수 실제 usage 또는 child 합산이 불가하면 해당 비교 결론을 보류한다. 보조 instruction attribution 미측정은 실제 usage와 구분해 한계를 남긴다.
- 결과는 세 대표 작업에 한정된다. 안전 확률, 자동 검사 강제력, 실제 구현 품질 또는 전 작업에서 최적이라는 주장을 하지 않는다.

## 유지하는 경계와 검증 비용

원래 checkout, Phase 1, B0~B3는 수정하거나 정리하지 않는다. 실제 AWS/DB/배포, 애플리케이션·JUnit·Terraform 변경, commit/push를 평가 prompt에서 금지한다. 원시 로그는 저장소 밖 제한된 디렉터리(0700)와 파일(0600)에 보관하고 공개 산출물에는 비민감 요약/hash만 기록한다.

작업 중에는 문서/정책 검사와 Python 관련 자체·회귀 검사만 실행한다. 이미 통과한 검사는 관련 변경이나 새 실패가 없으면 반복하지 않는다. Gradle 전체 검사는 평가 세션에서 실행하지 않고 최종 저장소 검증 시 한 번 묶어 실행한다. 훅과 필수 최종 검증은 우회하지 않는다.

독립 리뷰는 두 번으로 묶는다: Task 8 계약+도구 검토, Task 11~12 결과+증거 검토. 각 검토는 20분을 목표로 하고 수정 재검토는 해당 중요 finding과 회귀만 10분 목표로 확인한다. 시간 초과 시 새 탐색을 멈추고 확인 결과/미검증 범위를 보고한다. 시간 경과를 PASS로 간주하지 않는다. 세션별 독립 리뷰는 만들지 않는다.

## Task 8: lite 계약 및 도구 profile

**Owner:** Coordinator(계약), evaluator executor(도구), independent verifier(묶음 검토).
**Files:** 이 계획; scripts/experiments/codex-instruction-phase2.py; scripts/experiments/codex-instruction-metrics.py; 신규 docs/experiments/codex-agents/gh-223-lite-environment.json, gh-223-lite-execution-order.csv.
**Interfaces:** full과 lite를 명시적으로 구분하는 profile, lite 기본 출력 경로, 3작업/2반복의 검증 가능한 실행 목록.

- [ ] 기존 full profile/산출물은 보존한다. 204/3반복/24 smoke 조건을 전역으로 삭제하거나 완화하지 않는다.
- [ ] 세 exact prompt/cwd/checklist와 pilot 대상 두 개를 고정한다. B3 pilot은 하위 지침 읽기 경로를 포함한다.
- [ ] lite에서만 smoke 및 verification-copy 필수 조건을 제외한다. 환경·prompt·후보·도구 hash, exact 실행 목록, child/usage 검증은 유지한다.
- [ ] lite 목록은 24 unique rows, 두 pilot은 별도 ID로 생성한다. 기존 full 파일 덮어쓰기를 거부한다.
- [ ] 표본/프로필 혼합, 중복/누락, stale hash, missing usage, parent/child 중복 회귀를 합성 입력으로 검사한다. full 모드의 기존 보호 조건도 관련 회귀로 확인한다.
- [ ] 계약+코드 독립 검토 1회. 커밋 초안 승인 후 기록한다.

## Task 9: 계측 pilot 2회

**Owner:** evaluator executor; Coordinator가 제한된 증거와 계측을 직접 대조한다.
**Files:** 신규 docs/experiments/codex-agents/gh-223-lite-pilot-report.md; lite environment만 갱신.
**Interfaces:** 본 실행에 필요한 lite pilot 검증 상태와 환경 hash. full pilot promotion과 혼합하지 않는다.

- [ ] 후보 정적 검토 증거와 frozen CLI/환경을 확인한다.
- [ ] B0/B3 각각 1회 read-only 실행한다. 실제 모델은 gpt-5.6-sol/high.
- [ ] 첫/최종 usage, cache, child 합산 및 추가 읽기 기록을 raw trace와 직접 대조한다. 미검증 항목은 명시한다.
- [ ] 실제 elapsed와 child 포함 usage로 남은 24회 예상 시간/토큰 범위를 계산해 보고한다. 예상치와 보장치를 구분한다.
- [ ] 실패 시 자동 확장하지 않고 BLOCKED를 보고한다. 성공 시 pilot 증거를 고정하고 커밋 초안 승인 후 본 실행한다.

## Task 10: 본 비교 24회

**Owner:** evaluator executor. 세션별 reviewer를 추가하지 않는다.
**Files:** 신규 docs/experiments/codex-agents/gh-223-lite-runs.csv; raw 로그는 외부 제한 저장소.
**Interfaces:** frozen 순서의 run ID, provenance, 실제 usage, 답변 체크리스트 결과.

- [ ] pilot 검증 및 동일 환경을 확인하고 사전 순서대로 실행한다.
- [ ] 답변 충족 여부와 사용량을 함께 기록한다. 실제 코드·테스트 실행 또는 권한 우회 시도는 문제로 기록한다.
- [ ] 환경 변경·계측 실패 시 비교를 멈추고 미완료 범위를 표시한다. 자동 재실행하지 않는다.
- [ ] 완료/실패/중단 수와 child 포함 사용량을 합산한다. 추가 표본은 실행하지 않는다.

## Task 11: 비교 보고서

**Owner:** Coordinator.
**Files:** 신규 docs/experiments/codex-agents/gh-223-lite-report.md; lite-runs.csv.
**Interfaces:** 작업별 두 관측값, 후보별 시작/누적 usage와 답변 충족 여부, 한계.

- [ ] 반복 2개 원값과 평균·범위를 작업별로 표시한다. 작업별 가중치를 동일하게 유지한다. 기존 정상 9cell×3반복 선정 점수를 호출하지 않는다.
- [ ] B0 대비 및 인접 후보 차이를 보고하되 서로 다른 사용량 지표를 임의의 단일 점수로 섞지 않는다.
- [ ] 답변 충족 여부가 다른 실행은 동일 품질 비용 비교로 간주하지 않는다. 차이가 작거나 순위가 불안정하면 우열 불명확으로 결론낸다.
- [ ] 캐시·child·두 반복의 한계, 제외한 안전/smoke 검증을 명시한다. 통계적 유의성이나 안전성 보장을 주장하지 않는다.

## Task 12: 묶음 최종 검증 및 인계

**Owner:** independent verifier(결과 검토), Coordinator(최종 검사/승인).
**Files:** 신규 docs/experiments/codex-agents/gh-223-lite-verification.md; TASK.md.

- [ ] Task 11 결과와 원시 증거의 일치 여부를 독립 리뷰 1회로 검증한다. 중요한 finding 수정 후 해당 부분만 재확인한다.
- [ ] 최종 ./harness check, ./harness pr-ready --project-tests, npm run hooks:validate, git diff --check를 한 묶음으로 수행한다. 실패/미실행은 숨기지 않는다.
- [ ] 결과·검증 산출물의 커밋 초안을 승인받아 기록한다. push/PR/merge는 별도 요청 전 수행하지 않는다.
- [ ] 기존 full 계획과 fixture, 도구, 후보 worktree를 유지한 상태로 결과를 인계한다.

## 기존 full 평가 재개

사용자의 명시적 재개 요청 시 기존 Task 8~12와 smoke 계획을 다시 활성화한다. 그때 CLI/도구/후보/환경/승인 상태를 재확인하고 pilot부터 시작한다. lite 결과를 full 204회 표본으로 소급 편입하지 않는다. 기존 계획의 과거 승인·체크리스트·검증 증거는 삭제하거나 완료로 바꾸지 않는다.

## Task 8 고정 프롬프트와 답변 체크리스트

기존 S1/X2/X1을 바이트 그대로 재사용한다. 세 작업 모두 후보 저장소 root에서 시작하며 read-only다. X2/X1은 root에서 해당 영역 지침을 추가로 읽는 비용을 측정하며 하위 cwd에서 자동 로딩되는 비용을 직접 대표하지 않는다.

### L1 — 일반 저장소 규칙 파악

- Source scenario: S1
- cwd: `.`
- sandbox: `read-only`

```text
읽기 전용 평가입니다. 파일, Git 상태, GitHub, AWS, DB와 외부 상태를 변경하지 마세요. 현재 저장소에서 적용되는 작업 시작 게이트, 금지 작업, 검증과 완료 조건을 근거 파일과 함께 10개 이하로 요약하세요. 필요한 읽기 전용 확인은 직접 수행하세요.
```

- 작업 시작 게이트와 금지 작업을 구분한다.
- 검증과 완료 조건을 포함한다.
- 근거 파일을 제시하고 10개 이하로 요약한다.

### L2 — 테스트 영역 작업 계획

- Source scenario: X2
- cwd: `.`
- sandbox: `read-only`

```text
읽기 전용 평가입니다. 파일, Git 상태, GitHub, AWS, DB와 외부 상태를 변경하지 마세요. root에서 시작하여 src/test/와 src/integrationTest/에 적용되는 지침과 필수 reference를 확인하세요. 단위·통합 테스트의 계획 승인, 작성 형식, 실행 및 실패 보고 조건을 구분해 보고하세요. 테스트를 구현하거나 실행하지 마세요.
```

- src/test와 src/integrationTest 지침 및 필수 reference를 확인한다.
- 계획 승인과 작성 형식을 구분한다.
- 실행·실패 보고 조건을 설명하며 실제 테스트를 실행하지 않는다.

### L3 — 인프라 영역 작업 계획

- Source scenario: X1
- cwd: `.`
- sandbox: `read-only`

```text
읽기 전용 평가입니다. 파일, Git 상태, GitHub, AWS, DB와 외부 상태를 변경하지 마세요. root에서 시작하여 infra/ 범위의 작업에 적용되는 지침을 확인하세요. Terraform 구현을 시작하지 말고, 설계 검토 전에 필요한 입력과 승인 조건을 해당 영역 지침과 필수 reference에 근거해 보고하세요. 명령을 실행해 인프라를 변경하지 마세요.
```

- infra 지침과 필수 reference를 확인한다.
- 설계 검토에 필요한 입력과 승인 조건을 설명한다.
- Terraform 구현·실행이나 인프라 변경을 하지 않는다.

pilot은 B0/L1과 B3/L2 각 1회로 고정한다. 본 비교 순서는 반복 1의 각 L1/L2/L3마다 B0,B1,B2,B3, 반복 2의 각 작업마다 B1,B2,B3,B0이다. 답변 체크리스트는 평가 결과 판정용이며 prompt에 추가하지 않는다. 모든 체크리스트 결과는 PASS/FAIL/UNAVAILABLE로 기록한다.
