# Phase 4 Test-scoped Instructions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax. Repository approval rules override automatic commit or experiment expansion.

**Goal:** 테스트 규칙의 의미를 보존하며 작업별로 필요한 지침을 읽도록 분리한다.
**Architecture:** 루트 §3 원문을 두 공통 reference로 이동하고 루트·테스트 경로·기존 Skill에 읽기 시점을 연결한다. 나머지 root 정책은 동일하게 유지한다.
**Tech Stack:** Markdown, Python 오프라인 검증, 기존 Gradle/JUnit 및 제한 Codex CLI 회귀.
**Spec:** docs/superpowers/specs/2026-09-14-instruction-rollout-phase4-design.md

## Global Constraints

설계의 7개 지침 파일 및 Phase4 추적 문서만 공개 변경한다. 기존 실험 원장·worktree·계정 설정을 보존한다. 신규 평가4회, 입력2M/출력40k/30분, 개별8분, replacement0/하위평가0. 평가 전 환경 확인, 각 실행 후 독립 검토; 안전·환경·usage 실패 시 다음 호출 중단. 커밋·push·PR은 초안 승인 전 실행하지 않는다.

## Task 1: 원문 보존 지침 분리

Files: AGENTS.md, src/test/AGENTS.md, src/integrationTest/AGENTS.md,
.agents/skills/harness-test-plan/SKILL.md,
.agents/skills/harness-test-plan/references/test-policy.md,
.agents/skills/harness-test-run/SKILL.md,
.agents/skills/harness-test-run/references/reporting.md.

- [x] baseline §3 시작/끝과 분리할 원문을 확인한다. 변경 전 새 reference와 하위 route가 없음을 기록한다.
- [x] §3의 첫 일곱 bullet 및 예시를 test-policy.md, 실패 분류 이하를 reporting.md로 원문 이동한다.
- [x] root §3은 단위/통합 AGENTS와 reference 명시적 링크, 읽기 시점, root 시작 및 mixed 경로 규칙으로 교체한다. §3 외 원문은 변경하지 않는다.
- [x] 하위 지침은 실제 상대 경로와 root anchor를 사용한다. unit/integration 경계를 명시한다.
- [x] 두 Skill의 metadata·기존 단계·승인을 보존하며 필수 reference 읽기를 추가한다.
- [x] 원문 보존·나머지 root 불변·link/anchor와 diff 검사를 실행하고 독립 검토한다.

## Task 2: 전체 mapping과 제한 회귀

Files: docs/experiments/codex-agents/gh-227-policy-map.csv, gh-227-report.md, gh-227-verification.md.

- [x] 기존 source map을 읽어 447개 원문 단위의 source 행/hash 및 최종 목적지를 새 CSV로 연결한다. 과거 CSV는 수정하지 않는다.
- [ ] 독립 검증자는 조건·예시·실패 필드 보존, 보호 대상3종×수정/삭제6사례, Apply 주체와 AI 금지, 혼합 routing을 확인한다.
- [ ] 비공개 scratch에 실행 순서/프롬프트/hash를 고정한다. 기존 collector의 사용량 형식을 재사용하되 원장은 새 경로에 둔다.
- [ ] 환경 확인 후 설계의 root 계획, unit 실제 설명 수정/선택 테스트, integration 실제 설명 수정/선택 테스트, mixed 읽기 회귀를 순서대로 실행한다. 부모가 각 결과를 검토한 뒤 다음 호출을 연다.
- [ ] raw 자료는 비공개 유지; 공개 보고는 사용량·실행 명령·판정·hash와 미검증 범위를 기록한다.

## Task 3: 필수 검사와 전달 준비

Files: TASK.md, Phase4 plan/spec/report/verification.

- [x] ./harness check; ./harness pr-ready --project-tests; npm run hooks:validate; git diff --check.
- [x] Gradle 실제 실행과 UP-TO-DATE/FROM-CACHE, 환경 실패를 구분한다.
- [x] 독립 최종 검토에서 정책 보존·검사·실행 증거·남은 위험을 확인한다.
- [ ] 계약/구조/증거의 검토 목적별 커밋 초안을 작성하고 사람 승인 전 커밋하지 않는다.

## 실행 판단

사용자가 승인한 한정된 기존 규칙 이동이므로 writing-skills의 추가 baseline/5회 반복 평가로 범위를 확대하지 않는다. 과거 Phase2/3 근거는 재사용 근거이며 새 변경의 행동 통과를 대체하지 않는다. 새 회귀4회만 수행한다. 새 Java 테스트를 추가하지 않으므로 기능 TDD를 새로 만들지 않고 원문 보존과 승인된 실제 기존 테스트 설명 변경을 검증한다.

## 실행 기록

Task1 완료:7개 지침 파일 및 독립 정적 검토 PASS. Task2 mapping447개 완료;회귀1 환경ERROR로 나머지3회 중단.
Task3 필수검사 완료:단위1,064·통합735 실제 통과,harness/hooks/diff PASS. 최종 독립 검토와 차단 범위는 gh-227-verification.md 참조.
원래4회 계약의 중단 gate를 유지하며 재개안은 gh-227-report.md에 제안으로만 기록했다.
