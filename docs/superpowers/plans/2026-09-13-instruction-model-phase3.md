# Phase 3 실험 준비 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 구조를 고정한 두 모델 비교를 과금 실행 전에 검토 가능하게 준비한다.

**Architecture:** Phase 2 원장은 보존하고 Phase 3 산출물을 분리한다. 설계·예산 승인 후 계측 도구 구현 계획을 구체화하고 독립 검증을 거쳐 실행한다.

**Tech Stack:** GitHub Projects, Git, Markdown, 기존 Python 계측 도구, Codex CLI.

**Spec:** docs/superpowers/specs/2026-09-13-instruction-model-phase3-design.md

## Global Constraints

- Issue #225, GH-225-INSTRUCTION-MODEL-PHASE3, HARNESS-DESIGN-GH-225-001.
- 본 비교와 pilot은 후보·횟수·예산 승인 전에 실행하지 않는다.
- Sol high / Astra medium; 구조·prompt·도구·권한·cwd 동일; 기존 후보/원장 수정 금지.
- 사용자의 “계획대로 진행해줘” 승인으로 사전 점검과 Phase 3 계측·제한 실행을 진행한다. gate 완화와 커밋은 이 승인에 포함하지 않는다.

### Task 1: 계약과 선행 증거 감사

**Files:** TASK.md; 위 Spec.

- [x] Phase 2 최신 PR 상태/댓글/TASK/설계/보고서/CSV 확인.
- [x] Project draft를 Issue로 전환하고 P2/In Progress/Chore로 연결.
- [x] 원격 Phase 2 기준 branch와 TASK 구성.
- [x] 귀속 instruction unavailable 및 전체 카탈로그 한계를 설계에 기록.
- [x] 공식 모델 페이지 확인 및 가격 시나리오 산출.

### Task 2: 문서 검증과 사람 결정

**Files:** 위 두 문서와 이 계획만.

- [x] 문서 링크, git diff --check, ./harness check, npm run hooks:validate 확인.
- [x] 독립 읽기 검토로 모델/구조 교란, 예산, 과도한 결론, 안전 게이트 누락 확인.
- [x] B3 고정 및 총 14회/운영 상한을 사람에게 승인받는다.
- [x] 승인된 범위만 TASK와 설계에 기록한다.

### Task 3: 승인 후 계측 구현 계약 구체화

**Planned files:** scripts/experiments/codex-instruction-phase3.py;
docs/experiments/codex-agents/gh-225-environment.json, gh-225-execution-order.csv,
gh-225-runs.csv, gh-225-report.md, gh-225-verification.md.
이 목록은 실행 승인 범위다. 코드 구현 전 함수·입출력·합성 회귀 사례를 구체화하며 승인된 실험 변수와 gate를 유지한다.

- [x] CLI 호환·catalog 증거·source span 복원 가능성을 무과금으로 점검한다. 결과: catalog gate BLOCKED, 실제 모델 호환성과 source-span은 미검증.
- [ ] 새 runner는 Phase 2 코드/24회 게이트를 변경하지 않고 두 model ID를 검증한다.
- [ ] 합성 usage/중복/cache/child/잘림/ambiguous source 실패 케이스로 계측을 검증한다.
- [ ] 동일 후보 평가 checkout 및 manifest/hash/순서를 고정한다.
- [ ] 승인된 pilot 2회 후 exact/partial/unavailable 판정을 독립 검증한다.
- [ ] 계측 gate 통과 시 12회 본 비교, 통과 불가 시 BLOCKED와 남은 선택을 보고한다.
- [ ] 결과·실패 원장과 독립 검증을 묶어서 작성하고 완료 검증을 한 번 실행한다.
- [ ] harness-commit으로 검토 목적별 초안 제시 후 승인된 커밋만 수행한다.

## 준비 단계 보고

status: BLOCKED (실험 후보·예산 승인 대기; Issue 및 문서 준비와 구분)
issue_number: 225
task_id: GH-225-INSTRUCTION-MODEL-PHASE3
design_id: HARNESS-DESIGN-GH-225-001
changed_files: TASK.md, Spec, 이 계획
executed_checks: ./harness check; npm run hooks:validate; git diff --check; 문서 링크·가격 산술; Issue/Project/branch/base 조회
passed_checks: 위 준비 검사 모두 통과
failed_checks: 없음
blocked_checks: pilot, 본 비교, 최종 pr-ready --project-tests
assumptions: B3 단일 구조와 14회는 제안이며 미승인
risks: 귀속·catalog 미확보 가능성, 작은 표본, Astra 사용량 불확실성
required_human_decisions: 후보 및 실행/예산 승인; 후속 커밋 별도 승인


## 독립 준비 문서 검토

독립 검토자가 귀속 exact의 대상과 catalog 미확보 처리 두 가지 명확화를 요청했다.
설계에 exact source-span bytes와 proxy tokenizer 토큰의 차이,
full catalog 전달 증거 미확보 시 BLOCKED 및 별도 승인 절차를 명시해 반영했다.
가격 산술 직접 재계산은 $10.08241444 / $30.1677418이며 두 자리 반올림을 적용했다.
검토자는 파일을 수정하거나 실험을 실행하지 않았다. 이 기록은 사람의 설계/예산 승인이 아니다.

최종 준비 검증: harness check, hooks:validate, diff 공백, 문서 링크, 가격 산술,
GitHub 필드와 branch/base 확인 통과. 실제 pilot·모델 비교·Gradle·pr-ready는 실행하지 않았다.
실험 완료 상태는 BLOCKED이며 준비 문서만 검토 가능하다.

## 실행 승인 후 상태

사용자가 Astra medium 변경 후 “계획대로 진행해줘”로 실행을 승인했다.
위 준비 단계의 승인 대기 상태는 과거 기록이며 현재는 PREFLIGHT_IN_PROGRESS다.
CLI 0.154.0-alpha.6.2가 설치되어 기존 0.153.4 adapter 호환성과 full catalog 증거를 먼저 점검한다.

현재 실행 상태: BLOCKED_PREFLIGHT. docs/experiments/codex-agents/gh-225-report.md에 확인 근거와 재개 선택을 기록했다. 모델/pilot 호출은 0회이며 기존 승인 예산은 소비하지 않았다(현재 작업 자체 사용량 제외).

## 제한 비교 실행 재개

사용자가 limited environment equivalence를 승인했다. full catalog UNAVAILABLE을 허용하고 Phase 3 runner 구현 및 합성 검증→독립 리뷰→pilot을 진행한다. attribution gate는 유지한다. 신규 runner는 prepare/self-test/run-pilot 명령으로 나누고 시작 상태/순서/예산 및 사용량 누락을 fail-closed 처리한다. 기존 scripts는 변경하지 않는다.


## Pilot1과 수정 결과

Sol/high 첫 pilot은 process exit0이나 POST_ENVIRONMENT_DRIFT로 채택 불가다.
사용량/답변은 독립 확인했고 custom-call parser의 호출0/read누락 오류를 수정했다.
합성14개 및 원본 보존 offline reanalysis 완료. 새 source-byte 값은 partial로만 보고한다.
추가 실행 없이 BLOCKED_AFTER_PILOT_1을 유지한다. 실제 원장/실패를 삭제하거나 재승격하지 않는다.
추가 산출물 gh-225-pilot1-reanalysis.json은 기존 raw의 비민감 hash/파생 지표이며 새 모델 평가가 아니다.

## 현재 활성 실행 계약: 승인된 batch 2

사용자가 실패1회 보존 + 새 pilot2회 + 본 비교12회, 지침 읽기량 partial 표기,
총15회 상한 및 기존 누적 예산 유지 제안에 “승인”으로 동의했다.
이 계약이 과거 14회/재실행 불허/partial 본 비교 차단 조건을 이번 batch에 한해 대체한다.

- 구성: B3 고정; gpt-5.6-sol/high, gpt-6-astra/medium.
- 신규 batch: L2 sol→astra pilot2회; 본 비교12회는 기존 순서 그대로.
- 누적 한도: 과거 실패314416 input/5464 output/$0.3430464/121.3678877초를 포함해
  $40/input8M/output160k/90분. 총 평가15회, 개별10분. 이번 batch 추가 replacement는0회.
- partial source bytes는 관측 하한이며 전체 구조 비용/정확 instruction token으로 해석하지 않는다.
- 기존 raw 원장은 수정하지 않는다. 고정 batch2 하위 원장에 별도 기록하고 과거 사용량/hash를 연결한다.
- 새 pilot2회의 사용량·환경·답변을 독립 검토한 뒤에만 본 비교12회를 실행한다.
- 환경/usage/safety 실패 또는 예산 한도 도달 시 다음 호출을 중단한다.
- 전체 도구 전달 동일성은 이미 승인된 미검증 한계로 유지한다.
- 모델 호출과 커밋 승인은 구분하며 commit/push/PR은 아직 실행하지 않는다.

## 실행 완료 기록

승인된 총15회 완료. 새 pilot2 gate PASS 후 main12 수집 및 독립 검토 완료. L3 품질 실패2건과 두 쌍 제외를 보존한다. 추가 평가 없이 보고서와 최종 저장소 검증을 완료한다. 커밋 초안 승인 전 commit/push/PR은 없다.
