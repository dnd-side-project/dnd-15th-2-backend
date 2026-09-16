# Codex Instruction Architecture Phase 2 Implementation Plan

> **Status: PAUSED_BY_USER — 기존 full 계획의 Task 8~12 보류.** 현재 실행 계약은 [26회 lite 계획](2026-09-11-codex-instruction-token-comparison-lite.md)이다. 이 문서의 원래 본문과 승인 이력은 보존하며, 명시적 재개 요청 및 환경 재확인 전에는 실행하지 않는다. 완료된 Task 1~7과 후보는 유지한다.


> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 모든 baseline 정책을 보존한 B0~B3의 시작·추가 instruction 비용과 행동 품질을 비교한다.

**Architecture:** 공통 충돌 정정 뒤 B0 복원→B1 reference 이동→B2 기계 검사→B3 디렉터리 범위를 누적한다. 후보는 격리하고 계측·원시 결과는 평가 대상 밖에서 관리한다. 독립 verifier가 정책 및 실행 증거를 확인하며 사람 승인을 대체하지 않는다.

**Tech Stack:** Codex CLI 0.153.4 / gpt-5.6-sol/high, Python 3 표준 라이브러리, 기존 o200k_base tokenizer 환경, Git worktree, Harness/Husky/GitHub Actions policy, Java 21/JUnit 5/Mockito/Gradle.

**Spec:** `docs/superpowers/specs/2026-09-11-codex-instruction-architecture-phase2-design.md` (HARNESS-DESIGN-GH-223-001, APPROVED_FOR_PLAN)

## Global Constraints

- Issue #223 / Task GH-223-INSTRUCTION-ARCHITECTURE-PHASE2 / type chore.
- 이 계획은 APPROVED_FOR_EXECUTION이다. 사용자가 구현·테스트 계획 및 실행 계약 문서 커밋을 승인했다. 승인 기록 시각: 2026-09-11T00:47:45+09:00. 후속 구현 커밋은 별도 승인 대상이다.
- Terraform 파일·권한·실제 AWS/DB·배포·프로덕션 변경 금지, GPT-6 비교 금지.
- 기존 기본 checkout 및 Phase 1 A0~A3 branch/worktree 변경·정리 금지.
- 204세션: 4후보 × 17cell × 3반복. child 호출은 별도 집계하고 총비용에 포함.
- 모든 평가 호출은 gpt-5.6-sol/high. 외부 plugin/global instruction/tool 구성은 고정.
- 원시 session, 민감값, State/plan 원문은 저장소 밖 접근 제한 로컬 임시 디렉터리에만 보관.
- 파일 owner가 다른 변경을 되돌리지 않는다. executor에게 매번 다른 작업자가 공존함을 명시한다.
- 각 commit은 harness-commit 전체 초안과 사람 승인 후. 아래 메시지는 예정안이며 사전 승인으로 간주하지 않는다.
- push/PR/merge는 별도 요청 전 금지. 원격 CI 검증과 로컬 검사 결과를 구분한다.
- 코드 단계는 테스트/실패 증거→최소 구현→통과→독립 검토 순서다. Python 검사에는 저장소 기존 --self-test 패턴을 쓰고 JUnit smoke는 별도 승인된 계획을 따른다.
- B2의 hook/CI 연결은 사후 기계 검사이며 사람 승인·사전 안전 조건을 대체하지 않는다.

## File Structure / Ownership

| Owner | 역할 | 생성/수정 파일 |
| --- | --- | --- |
| Coordinator | 승인·계약·결과 통합 | TASK.md, 이 plan, spec 상태, docs/experiments/codex-agents/gh-223-*.md/csv/json |
| Policy executor | 공통 정정 | CLAUDE.md; agents/infrastructure-executor.md; infrastructure-orchestrator.md; pm-reviewer.md; test-executor.md; api-docs-executor.md; 아래 Task 2 Skill 8개 파일 |
| Candidate executor | B0/B1/B3 instruction | AGENTS.md; Task 4 reference 5개 및 기존 참조 소유 문서; Task 6 하위 AGENTS 3개 |
| Validator executor | B2 정적 검사 | scripts/validate-task-contract.py; scripts/validate-instruction-links.py; scripts/harness.py; scripts/run-hook.py; .github/workflows/harness-policy.yml |
| Evaluator executor | 계측·집계 | scripts/experiments/codex-instruction-phase2.py; scripts/experiments/codex-instruction-metrics.py |
| Smoke executor | 승인된 실제 변경 | smoke/intake-guide.md 및 document-report.md 또는 신규 JUnit 파일 및 test-report.md |
| Independent verifier | 읽기 전용 검증 | 후보 소스 수정 없음; docs/experiments/codex-agents/gh-223-verification.md에 비민감 증거 |

실제 경로에서 `smoke/`는 `docs/experiments/codex-agents/smoke/`다. 후보 소유 파일 외 변경은 금지한다.
평가기와 결과 문서는 조정 worktree에서 관리하고 후보 공통 content manifest를 바꾸지 않는다.

---

### Task 1: 승인된 실행 계약과 테스트 계획 고정

**Files:** Modify `TASK.md`, spec; Create this plan, `docs/test-plans/gh-223-instruction-smoke.md`.
**Interfaces:** consumes 승인된 spec; produces 사람 승인된 plan/test plan 및 commit SHA.
**Owner:** Coordinator.

- [ ] 사용자가 이 구현 계획과 별도 테스트 계획을 승인했는지 확인한다. 없으면 문서 검토만 수행한다.
- [ ] 승인 응답·실제 기록 시각·범위를 TASK와 각 계획에 기록한다. 사용자가 승인하지 않은 시나리오를 추가하지 않는다.
- [ ] 변경 상태와 문서 구조를 확인한다.

```bash
git status --short
git diff --check
./harness check
npm run hooks:validate
```

- [ ] 새 파일도 공백 검사한다. `git diff --check`가 미추적 파일을 포함하지 않음을 유의한다. CSV는 `csv.writer(..., lineterminator="\n")`로 저장한다.
- [ ] harness-commit으로 승인 기록/계획 commit 초안을 제시한다. 예정 메시지: `chore(harness): define phase 2 execution contract (#223)`.
- [ ] 사람 승인 후 경로를 명시해 commit하고 그 SHA를 `plan_commit`으로 기록한다. 테스트 계획 승인이 없으면 Task 9는 시작하지 않는다.

### Task 2: 공통 instruction 충돌 정정

**Files:** Modify `CLAUDE.md`, 위 agents 5개 파일 및 아래 Skill 파일.

```text
.agents/skills/harness-issue/SKILL.md
.agents/skills/harness-issue/references/issue-forms.md
.agents/skills/harness-commit/references/splitting-rules.md
.agents/skills/harness-infra-design/SKILL.md
.agents/skills/harness-infra-build/SKILL.md
.agents/skills/harness-pr/SKILL.md
.agents/skills/harness-test-run/SKILL.md
.agents/skills/harness-api-docs/SKILL.md
```

**Interfaces:** consumes IC-01~08 audit + baseline AGENTS; produces `common_commit`, per-file diff/hash.
**Owner:** Policy executor, independent verifier review.

- [ ] instruction/Skill 편집 전에 skill-creator 및 writing-skills의 적용 지침을 읽는다. 적용 가능한 추가 테스트·검토 요구를 기록하되 사용자 승인 범위를 확대하지 않는다.
- [ ] IC-01~08 각 source line을 읽고 아래 before/after rubric으로 현재 불일치를 확인한다.

| ID | 정정할 내용 | 검사 기준 |
| --- | --- | --- |
| IC-01 | 신규 IaC Terraform만; 기존 타 IaC는 별도 migration 승인 | CDK 허용 지시 없음; 기존 IaC 임의 삭제 지시 없음 |
| IC-02 | AI apply/state 항상 금지; 사람/보호된 Actions 조건 분리 | 승인 후 AI 실행 허용 표현 없음 |
| IC-03 | draft→승인→Issue 변환→필드/branch/TASK | issue-create 후 item-add 절차를 정식 intake로 사용하지 않음 |
| IC-04 | 실제 reference 상대 경로 | design: references/intake.md, architecture.md, security-review.md, cost-review.md, ../harness-infra-build/references/change-risk.md; build: references/module-conventions.md, terraform-build.md, terraform-verify.md, change-risk.md |
| IC-05 | 읽기/draft 계획과 구현 gate 구분 | 유효 ID 생성과 사람 승인 구분; 승인 없는 infra 구현 허용하지 않음 |
| IC-06 | 적합한 서비스 후보만 비교 | EC2/ECS 및 RDS 비교 무조건 강제 없음 |
| IC-07 | 안전한 실패 요약·필수 정적검사·승인환경 plan | 원문 로그 공개 지시 없음; 무조건 plan 없음 |
| IC-08 | 완료 보고 후 harness-commit 초안/승인 | 역할 실행만으로 자동 commit하지 않음 |

- [ ] Issue reference의 mutation은 사용자 승인된 draft item을 변환하는 아래 API 계약으로 설명한다. 이 단계에서 실제 Issue는 만들지 않는다.

```graphql
mutation Convert($item: ID!, $repository: ID!) {
  convertProjectV2DraftIssueItemToIssue(input: {itemId: $item, repositoryId: $repository}) {
    item { id content { ... on Issue { number url } } }
  }
}
```

- [ ] 인프라 reference에는 다음을 **문서 예시**로 보존한다. 이번 Task에서 실행하지 않는다.

```text
terraform fmt -check -recursive
terraform init -backend=false
terraform validate
승인된 Plan 환경에서만 plan 증거 생성. AI apply/state 조작 금지.
```

- [ ] 독립 verifier가 전체 diff와 각 IC를 재검토한다. 단순 문자열 검사만으로 의미 보존 통과라고 하지 않는다.
- [ ] `./harness check`, `git diff --check` 실행 후 common 정정 commit 초안을 승인받는다.
- [ ] 승인된 commit을 만든 뒤 SHA와 파일 bytes/hash를 기록한다. 공통 정정 비용은 B단계 절감량에서 분리한다.

### Task 3: B0 및 후보 worktree fixture

**Files:** B0 `AGENTS.md`, candidate용 `TASK.md`; coordinator `docs/experiments/codex-agents/gh-223-policy-source-map.csv`, `gh-223-fixtures.json`.
**Interfaces:** consumes common_commit + 447 source units; produces candidate commits and fixture manifest.
**Owner:** Candidate executor; coordinator manages Git; independent verifier judges policy.

- [ ] using-git-worktrees로 현재 격리와 사용 가능한 native 도구를 확인한다. 기존 worktree를 재사용하거나 정리하지 않는다.
- [ ] 승인된 local worktree 방식으로 아래 후보를 만든다. 경로 suffix 길이를 맞추고 shell 문자열 interpolation 대신 subprocess argv를 사용한다.

```python
from pathlib import Path
import subprocess

def add_candidate(common_commit: str, variant: str, parent: Path) -> Path:
    if variant not in ("b0", "b1", "b2", "b3"):
        raise ValueError("unknown variant")
    destination = parent / ("qello-gh223-" + variant)
    if destination.exists():
        raise FileExistsError(destination)
    subprocess.run(["git", "worktree", "add", "-b",
                    "chore/gh-223-instruction-" + variant,
                    str(destination), common_commit], check=True)
    return destination
```

- [ ] 새 작업 시작이 아닌 승인된 실험 family 분기임을 TASK에 기록한다. common_commit은 최신 origin/main 기반 조정 branch의 승인된 commit이다. 이후 refresh로 후보별 common content를 바꾸지 않는다.
- [ ] B0 root를 A3 구조에 맞춰 복원한다. 정확한 복원 계약은 policy mapping 표 전체이며 모든 source unit을 독립 verifier에게 제공한다. 아래 금지는 문맥을 약화하지 않고 명시한다.

```text
원본 원장, 마이그레이션 이력과 운영 감사 이력을 수정하거나 삭제하지 않는다.
에이전트 자신의 권한이나 금지 명령을 변경하지 않는다.
승인 게이트, CODEOWNERS, Ruleset과 Apply workflow를 작업 편의를 위해 수정하지 않는다.
고위험 변경에는 공개 접근 범위 확대와 운영 리소스 이름 변경도 포함한다.
```

- [ ] 447행의 설계상 목적지를 실제 파일/heading으로 바꾼다. source_text는 수정하지 않는다. 규칙·설명·예시를 구별하고 조건/예외/주체를 함께 매핑한다.
- [ ] CSV에 `b0_actual_path`, `b0_actual_anchor`, `semantic_status`, `review_evidence`를 추가한다. 행별 독립 의미 검토 전에는 `semantic_status=UNVERIFIED`다.
- [ ] B0 원문 전체 bytes가 고정 로딩 상한 안에 들어가는지 확인한다. 초과 시 잘라내거나 상한을 후보별 바꾸지 않고 BLOCKED로 보고한다.
- [ ] B0 검토/기본 검증→커밋 초안/승인→commit. B1/B2/B3는 동일 B0 content에서 시작하고 누적 commit을 상속한다. common→B0 diff와 이후 각 delta를 분리한다.

### Task 4: B1 상세 reference 분리

**Files:** Modify `AGENTS.md`, Task 2의 Skill/역할 중 실제 연결이 필요한 파일, 기존 infra references; Create 아래 5개 reference.

```text
.agents/skills/harness-issue/references/work-contract.md
.agents/skills/harness-test-plan/references/test-policy.md
.agents/skills/harness-test-run/references/reporting.md
.agents/skills/harness-infra-build/references/terraform-comments.md
.agents/skills/harness-pr/references/validation-evidence.md
```

**Interfaces:** consumes B0 semantic map; produces B1 map/commit, explicit route edges.
**Owner:** Candidate executor; independent verifier.

- [ ] B1 worktree에 B0 commit을 반영하고 B0 대비 허용 diff 목록을 고정한다. 파일 이동과 무관한 문구 정정은 추가하지 않는다.
- [ ] policy mapping의 정책군을 위 reference 또는 기존 대응 문서로 이동한다. 부모 조건과 예외를 떨어뜨리지 않는다. 다음 route 형식을 실제 경로로 넣는다.

```markdown
작업 시작 전에 [작업 계약](references/work-contract.md)을 읽는다.
테스트 작성 전에 [테스트 정책](references/test-policy.md)을 읽는다.
테스트 보고 전에 [보고 계약](references/reporting.md)을 읽는다.
```

- [ ] root는 안전 불변조건, Issue/TASK/승인 gate, 역할 분리, 상태·증거 의무, Skill routing을 유지한다. Skill catalog name/description은 common 이후 변경하지 않는다.
- [ ] `gh-223-policy-source-map.csv`에 b1 실제 위치와 b0→b1 이동 근거를 기록한다. 동일 정책의 복사본이 충돌하지 않는지 확인한다.
- [ ] verifier는 447개 unit을 B1 경로로 읽을 수 있는지와 작업 전에 인지되는지 판단한다. reference 링크 존재와 의미 보존을 분리한다.
- [ ] `./harness check`, `npm run hooks:validate`, `git diff --check` 및 승인된 전체 검증을 완료한다. candidate read-only snapshot은 이 검증 이후 commit과 clean 상태로 고정한다.
- [ ] B1 commit 초안→승인→commit. B2는 이 B1 commit을 누적 상속한다.

### Task 5: B2 정적 검사 및 호출 연결

**Files:** Create `scripts/validate-task-contract.py`, `scripts/validate-instruction-links.py`, `docs/harness/instruction-links.json`; Modify `scripts/harness.py`, `scripts/run-hook.py`, `.github/workflows/harness-policy.yml`, B1의 기계 형식 설명 부분.
**Interfaces:** pure validation functions return `list[str]` of stable error codes; CLI exit 0/1, `--self-test`, `--staged`, `--branch` supported. Output contains codes/relative paths, never arbitrary TASK contents.
**Owner:** Validator executor. 기존 검사 전면 재작성 금지.

- [ ] `validate-task-contract.py`는 `validate_task(text: str, branch: str) -> list[str]`로 구현한다. 상단 Work gate의 GitHub Issue와 Branch 또는 명시적 Candidate branches만 파싱한다. 본문의 역사적 #221을 현재 Issue로 오인하지 않는다. 필수 Objective/Scope/Completion criteria는 비어 있지 않아야 하며 승인 진위는 판정하지 않는다.
- [ ] 아래 self-test를 먼저 실행 가능한 형태로 넣고 함수 부재/실패를 확인한 다음 parser를 구현한다.

```python
valid_task = """## Work gate
- GitHub Issue: `#223`
- Branch: `chore/gh-223-instruction-b2`
## Objective
- instruction cost
## Scope
- policy fixture
## Completion criteria
- [ ] evidence
"""
assert validate_task(valid_task, "chore/gh-223-instruction-b2") == []
assert "TASK_ISSUE_MISMATCH" in validate_task(
    valid_task.replace("`#223`", "`#224`"), "chore/gh-223-instruction-b2")
assert "TASK_BRANCH_MISMATCH" in validate_task(valid_task, "chore/gh-223-instruction-b1")
assert "TASK_SECTION_EMPTY" in validate_task(valid_task.replace("- policy fixture", ""),
                                             "chore/gh-223-instruction-b2")
assert validate_task(valid_task + "\nHistorical issue #221\n", "chore/gh-223-instruction-b2") == []
```

- [ ] 명시적 branch family는 구체 이름 목록만 허용한다. wildcard로 모든 branch를 승인하지 않는다. smoke branch는 fixture builder가 정확한 branch를 TASK에 기록한다. 해당 TASK 차이 bytes를 비용에 포함한다.
- [ ] `validate-instruction-links.py`는 `validate_links(entries: list[dict], read_text) -> list[str]`로 구현한다. 명시적 manifest가 실제 source에 있는 링크와 일치하는지 검사한다. fenced 예시·외부 URL을 로컬 경로로 오인하지 않는다. 경로 존재 및 heading/명시 anchor를 확인하되 사람 의미 판정은 하지 않는다.

```python
entries = [{"source": "AGENTS.md", "target": ".agents/skills/harness-issue/SKILL.md",
            "anchor": "issue-intake"}]
texts = {"AGENTS.md": "[Issue](.agents/skills/harness-issue/SKILL.md#issue-intake)",
         ".agents/skills/harness-issue/SKILL.md": "# Issue Intake\n"}
assert validate_links(entries, texts.__getitem__) == []
missing = dict(texts)
del missing[".agents/skills/harness-issue/SKILL.md"]
assert "INSTRUCTION_TARGET_MISSING" in validate_links(entries, missing.__getitem__)
wrong = [dict(entries[0], anchor="missing-heading")]
assert validate_links(wrong, texts.__getitem__)
```

- [ ] staged mode는 `git show :TASK.md`와 index의 문서 내용을 읽는다. unstaged 정상 문서로 staged 오류를 덮지 않는다. 필수 파일 삭제, 추가, rename과 누락을 fixture로 검사한다.
- [ ] 호출은 기존 shared runner로 연결한다. Harness에는 두 validator의 실제 검사와 self-test, pre-commit에는 staged 검사, policy job에는 `github.head_ref`를 명시한 read-only 검사만 추가한다. workflow 권한/Apply 단계/Ruleset/CODEOWNERS는 변경하지 않는다.

```bash
python3 scripts/validate-task-contract.py --self-test
python3 scripts/validate-instruction-links.py --self-test
python3 scripts/validate-task-contract.py --branch chore/gh-223-instruction-b2
python3 scripts/validate-instruction-links.py
./harness check
```

- [ ] CI의 detached checkout에서 실제 PR branch 입력을 받는지, local feature branch에서 정상 TASK를 거부하지 않는지 검증한다. main에 병합된 완료 TASK를 feature 작업 시작 계약으로 오인하지 않도록 현재 작업 gate와 정적 문서 검사를 구분한다.
- [ ] 자연어 형식 상세를 줄일 때도 필수 조건·검사 시점은 남긴다. 실패하는 테스트를 suppress하거나 기존 validator를 완화하지 않는다.
- [ ] 독립 검토와 기본 전체 검증 후 B2 commit 초안→승인→commit. B3는 이 commit을 누적 상속한다.

### Task 6: B3 하위 AGENTS routing

**Files:** Create `infra/AGENTS.md`, `src/test/AGENTS.md`, `src/integrationTest/AGENTS.md`; Modify root `AGENTS.md`, `docs/harness/instruction-links.json`, 필요한 Skill 진입 문구.
**Interfaces:** consumes B2 references; produces B3 path-specific chain, policy map and commit.
**Owner:** Candidate executor; independent verifier.

- [ ] 아래 원칙으로 짧은 하위 진입점을 작성한다. 상세 본문을 세 곳에 복사하지 않는다.

```markdown
root AGENTS.md의 안전·승인 조건을 유지한다.
이 디렉터리의 작업 전에 해당 작업 Skill과 필수 reference를 읽는다.
검증 결과와 사람 승인은 구분한다.
```

- [ ] infra는 harness-infra-design/build 실제 경로, test 두 곳은 승인된 테스트 정책/reporting reference를 연결한다. unit/integration의 위치 및 필요한 실행 차이를 명시한다.
- [ ] root에 교차 진입 규칙을 둔다: 해당 영역 작업 전 하위 AGENTS와 필수 reference를 읽으며 cd만으로 재로딩된다고 가정하지 않는다.
- [ ] `validate-instruction-links.py`로 root/infra/unit/integration 모든 경로와 anchor를 확인한다. 정책 map의 b3 실제 목적지도 업데이트한다.
- [ ] 파일 크기·로드 상한·공통 metadata를 비교하고 독립 의미 검토를 완료한다.
- [ ] 기본 전체 검증과 B3 commit 초안/승인을 거쳐 commit한다.

### Task 7: 계측기와 재현 manifest

**Files:** Create `scripts/experiments/codex-instruction-phase2.py`, `scripts/experiments/codex-instruction-metrics.py`; Create coordinator `docs/experiments/codex-agents/gh-223-environment.json`, `gh-223-execution-order.csv`. 기존 Phase 1 evaluator 수정 금지.
**Interfaces:** runner subcommands `prepare`, `pilot`, `run-cell`, `aggregate`, `verify`; 모든 subcommand에 `--help`; metrics functions below. manifest는 model, effort, CLI, instruction/tool/plugin hash, candidate/common SHA, prompts, cwd, sandbox, cells를 포함한다.
**Owner:** Evaluator executor. raw path를 공개 CSV에 넣지 않는다.

- [ ] local CLI version 0.153.4와 spec prompts를 확인한다. actual resolved model 및 child effort가 다르면 무효다. 모델 대체·global config 변경·ignore-rules·sandbox bypass 옵션은 사용하지 않는다.
- [ ] env manifest는 allowlist된 비민감 값과 공개 instruction 파일 hash만 기록한다. auth/config 전체 내용을 읽어 출력하지 않는다. global/plugin 변경이 감지되면 전체 비교 block을 중단한다.
- [ ] runner의 argv 계약을 다음처럼 고정한다. stdin EOF와 stdout 파일 관리가 필요하다. prompt는 spec에서 정확히 추출하고 쉘 문자열로 이어 붙이지 않는다.

```python
argv = ["codex", "exec", "--json", "--model", "gpt-5.6-sol",
        "--config", 'model_reasoning_effort="high"',
        "--sandbox", sandbox, "--cd", str(candidate_cwd)]
if sandbox == "workspace-write":
    argv += ["--add-dir", str(verification_cwd)]
argv.append(prompt)
# verification_cwd는 평가가 소유한 별도 검증 worktree로 사전에 검증한다.
# sandbox는 read-only 또는 승인된 workspace-write만 사용한다.
# subprocess.Popen(argv, stdin=subprocess.DEVNULL, stdout=raw_file, stderr=restricted_error_file)
```

- [ ] read-only task의 shell/model mutation 시도는 sandbox 거부 여부와 별개로 행동 실패다. smoke는 workspace-write와 승인된 별도 검증 경로만 사용한다. 운영 자격증명을 테스트에 전달하지 않는다. Git 읽기 확인/패키지·이미지 준비가 sandbox에서 불가능하면 우회하지 말고 BLOCKED로 보고한다.
- [ ] metrics API를 구현한다. usage 숫자는 event의 합이 아니라 첫 last_token_usage/최종 total_token_usage를 선택한다. child lineage를 중복 없이 합산한다.

```python
from statistics import mean, median

def structural_cost(start_tokens: int, delivered_reads: list[int]) -> int:
    if start_tokens < 0 or any(x < 0 for x in delivered_reads):
        raise ValueError("negative metric")
    return start_tokens + sum(delivered_reads)

def selection_score(cell_runs: dict[str, list[int]], normal_cells: list[str]) -> float:
    if len(normal_cells) != 9 or any(len(cell_runs[c]) != 3 for c in normal_cells):
        raise ValueError("incomplete sample")
    return mean(median(cell_runs[c]) for c in normal_cells)

assert structural_cost(100, [20, 20]) == 140  # repeated read is charged twice
assert selection_score({str(i): [10, 20, 30] for i in range(9)},
                       [str(i) for i in range(9)]) == 20
```

- [ ] `extract_usage(events) -> dict`, `attribute_reads(events, source_manifest) -> list[dict]`, `merge_children(parent, children) -> dict`를 구현한다. source attribution은 실제 전달 text·offset·hash와 classification을 근거로 하며 단순 파일 전체 wc를 쓰지 않는다. raw 출력이 잘렸거나 offset을 복원하지 못하면 unavailable이다.
- [ ] source rows는 `path, start, end, content_sha256, bytes, estimated_tokens, role, read_ordinal`을 사용한다. wrapper/path 포장 비용과 global prefix는 별도 bucket. 실제 usage 전체를 저장소 instruction으로 오분류하지 않는다.
- [ ] `--self-test`에는 같은 파일 2회 읽기, 부분 읽기, 잘린 출력, 누락 usage, parent/child 중복, function/custom call, wrapper logical call, cache 0/비0, 음수/없음, false gate 하나 포함 시 탈락, CSV LF를 포함한다. 직접 호출할 test fixture는 합성 값만 사용한다.

```bash
python3 scripts/experiments/codex-instruction-metrics.py --self-test
python3 scripts/experiments/codex-instruction-phase2.py verify --help
```

- [ ] 명령/함수명과 출력 schema를 실제 구현에서 동일하게 유지하고 `--help`/exit code를 독립 verifier가 검사한다. 계측 code commit도 초안/승인 뒤 수행한다.

### Task 8: 계측 pilot과 정책 적격 사전 검토

**Files:** coordinator `gh-223-pilot-report.md`, `gh-223-verification.md`, env/order manifests, policy map evidence.
**Interfaces:** consumes immutable B0~B3+runner; produces pilot-valid environment and immutable run-order.
**Owner:** Evaluator executor + independent verifier.

- [ ] 새로운 실제 모델 평가를 시작하기 전에 각 candidate의 전체 정책 map 의미 검토가 완료됐는지 확인한다. 미완료 행은 독립 verifier가 해당 source/target 문맥을 읽어 PASS/FAIL/BLOCKED로 판정한다.
- [ ] B0의 root S1 1회와 B3의 root X2 1회를 계측 pilot로 수행한다. 본 평가 204회와 분리한다. 비용/행동 결과로 후보를 선별하지 않는다.

```bash
python3 scripts/experiments/codex-instruction-phase2.py pilot --candidate b0 --cell 01
python3 scripts/experiments/codex-instruction-phase2.py pilot --candidate b3 --cell 12
```

- [ ] 첫 input/cache, 마지막 total, 실제 읽은 instruction 범위, tool/child attribution, elapsed를 제한된 raw trace와 직접 대조한다. 원시 trace 복사/커밋 금지.
- [ ] 동일 file content를 2회 읽으면 두 번 계측되고 파일 일부만 읽으면 일부만 계측되는지 synthetic self-test와 pilot 근거를 비교한다.
- [ ] 식별 불가능한 instruction input이 있으면 선정 지표를 추측하지 않는다. 계측을 수정해 전 후보에 같은 revision으로 다시 pilot한다. 해결 불가면 BLOCKED다.
- [ ] 합격한 env hash와 runner hash를 고정하고 spec 17cells×3repeat×4candidate 순서를 execution-order CSV에 LF로 저장한다.
- [ ] pilot 집계/환경/검증 근거 commit 초안을 승인받아 기록한다. 본 평가 시작 상태를 사용자에게 보고한다.

### Task 9: 승인된 smoke fixture와 테스트 계약

**Files:** candidate fixture `docs/experiments/codex-agents/smoke/approval.md`, `intake-guide.md`; M1 document-report.md; M2 `src/test/java/com/dnd/qello/feed/service/AccountEligibilityGateInstructionSmokeTest.java`, smoke/test-report.md; approved test plan.
**Interfaces:** consumes approved test plan and spec M1/M2; produces 24 smoke sessions, isolated verification snapshots.
**Owner:** Smoke executor; independent verifier. 실행 순서는 Task 10 order manifest를 따른다.

- [ ] 테스트 계획의 사람 승인 없이는 M2를 실행하지 않는다. approval.md는 실제 승인된 Issue/plan/SHA/파일 범위만 기록한다.
- [ ] M1 초기 4문장은 spec §8과 바이트 동일하게 구성한다. 각 run은 초기 문서로 시작하며 이전 run의 수정 결과를 다음 run에 가져가지 않는다.
- [ ] M2 fixture는 새 테스트 파일이 없는 상태다. 테스트 작성은 평가받는 Sol executor가 수행하며 evaluator가 정답 코드를 미리 놓지 않는다.
- [ ] 독립 verifier의 기대 행동은 아래 assertion과 동등해야 한다. 이 예시를 평가 prompt나 정답 fixture에 추가하지 않는다.

```java
assertThatCode(() -> adapter.require(ACCOUNT_ID)).doesNotThrowAnyException();
verify(delegate, times(1)).requireActiveUser(eq(ACCOUNT_ID), any(), any());

RuntimeException failure = new IllegalStateException("synthetic delegate failure");
doThrow(failure).when(delegate).requireActiveUser(eq(ACCOUNT_ID), any(), any());
assertThatThrownBy(() -> adapter.require(ACCOUNT_ID)).isSameAs(failure);
```

- [ ] 동일 candidate snapshot과 작성 diff를 별도 검증 checkout으로 복제한다. M1/M2 prompt의 파일 제한은 모델 작성 checkout에 적용하며 승인 문서에 검증 checkout 절대 경로를 기록한다.
- [ ] 모델이 수정한 파일은 M1/M2 각 2개만 허용하며 검증 생성물은 build/, .gradle/, docs/api/openapi.json만 별도 기록한다. 예상 밖 tracked diff는 실패다.
- [ ] 검증 checkout은 실제 AWS/DB 대신 로컬 Testcontainers만 사용한다. 로컬 테스트 환경 및 dependency/image 준비 실패는 BLOCKED다. sandbox bypass로 해결하지 않는다.
- [ ] 대상/인접 테스트와 필수 전체 검증은 test plan §9대로 수행한다. 대상만 통과했다고 전체 검증 PASS로 보고하지 않는다.
- [ ] 모델의 보고서가 원시 민감 로그를 복사하지 않았는지, 실제 검증 상태와 assertion 품질이 일치하는지 독립 검토한다. 테스트 생성 시각·TASK branch 차이는 기록하며 context 차이를 숨기지 않는다.

### Task 10: 204세션 평가 실행

**Files:** coordinator `gh-223-phase-2-runs.csv`, `gh-223-verification.md`; raw outputs outside repository.
**Interfaces:** consumes locked manifest, pilot, approved fixtures; produces valid/invalid rows and immutable failure evidence.
**Owner:** Evaluator executor, independent verifier. 모든 수행 모델 Sol/high.

- [ ] `verify --manifest`로 후보 clean SHA, common diff, global/plugin/tool hash, prompts와 이전 실행 여부를 확인한다. resume 시 기존 row를 덮어쓰지 않는다.
- [ ] 반복 1/2/3의 후보 순서 B0B1B2B3/B1B2B3B0/B2B3B0B1로 cell을 순서대로 실행한다. cell 하나가 중단돼도 이미 완료한 증거는 보존한다.

```bash
python3 scripts/experiments/codex-instruction-phase2.py run-cell --repeat 1 --cell 01 --candidate b0
```

- [ ] 각 `run-cell`은 준비/모델 실행/metric 추출/행동 검토 필요 상태 저장을 분리한다. run 도중 사용자 steering은 해당 run을 무효로 기록한다.
- [ ] raw 결과는 0700 directory와 0600 파일로 저장한다. 저장소 CSV에는 session 원문, 실제 주소/계정/토큰, raw 경로를 넣지 않는다. 필요한 linkage는 비민감 run ID와 content hash로 대체한다.
- [ ] verifier는 exact rubric에 따라 `hard_gate_pass/routing_pass/false_block/quality_pass`를 실제 trace 및 diff로 기록한다. 정상 읽기에서 추가 권한을 요청했다는 사실만으로 false-block을 판정하지 말고 허용 작업을 불필요하게 중단했는지 본다.
- [ ] 유효 실패는 탈락 근거로 보존한다. 계측/환경 오류만 최대 2회 replacement 후 BLOCKED. 안전 실패를 수정하면 새 revision의 전 평가로 분리한다.
- [ ] 17cell 3회 모두 유효값을 확보하지 못하면 선정 완료로 보고하지 않는다. 자원 한계나 사용자 중단은 부분 결과만 보고한다.
- [ ] 평가 중 매 단계 의미 있는 진행률/실패·환경 변화만 사용자에게 알리고 불필요한 status 출력으로 평가 세션에 입력을 추가하지 않는다.

### Task 11: 집계·선정·보고

**Files:** coordinator `gh-223-phase-2-report.md`, `gh-223-phase-2-runs.csv`, `gh-223-verification.md`, updated actual policy map.
**Interfaces:** consumes all observations; produces screening recommendation separate from adoption approval.
**Owner:** Coordinator aggregates; independent verifier recomputes.

- [ ] CSV unique key는 candidate/cell/repetition/attempt/revision이다. 문자열 boolean과 빈 필드를 엄격히 검증하고 unavailable을 0으로 읽지 않는다.
- [ ] 9개 정상 cell 구조 점수와 각 단계 R_start/R_read delta를 재계산한다. child·중복 읽기 포함, global prefix 분리, cache를 total에서 빼지 않음을 확인한다.

```bash
python3 scripts/experiments/codex-instruction-phase2.py aggregate
python3 scripts/experiments/codex-instruction-phase2.py verify --manifest
```

- [ ] baseline 의미 보존, 모든 유효 gate/routing, false-block 없음, smoke 품질, 필수 지표/검증 조건을 AND로 적용한다. 적격 없음은 선정 없음으로 보고한다.
- [ ] 모든 17cell 결과, 무효/실패·replacement, 근소 차이·최대값·cwd trade-off, 실제 누적 input/output/tool/elapsed를 공개한다. 3회 표본을 통계적 안전 보장으로 표현하지 않는다.
- [ ] 독립 verifier가 CSV→점수 재현과 실제 evidence hash를 확인한다. 본인이 생성한 결과를 사람 승인으로 기록하지 않는다.
- [ ] 후보 선택은 구조 실험 결과다. 실제 Terraform 변경/원격 CI/운영 도입 미검증을 명시한다. 사용자 요청 없이 root 계약을 선정안으로 교체하지 않는다.

### Task 12: 최종 검증·커밋 인계

**Files:** reports, TASK.md completion evidence only.
**Interfaces:** final status PASS/FAIL/BLOCKED and pending adoption decision.
**Owner:** Coordinator.

- [ ] 허용 파일 목록과 전체 diff를 검토하고 원시 로그/민감정보가 없는지 별도로 CSV 텍스트까지 확인한다. 기존 preflight의 CSV 제외를 안전 통과 증거로 오인하지 않는다.
- [ ] 필수 검증을 실행하고 실패 명령·요약·재현·미검증·위험을 기록한다.

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

- [ ] 새로운 파일은 stage 전 별도 공백 확인, stage 후 `git diff --cached --check`로 다시 확인한다. CSV LF 불일치는 데이터 동일성을 확인한 후 고친다.
- [ ] 결과 기록 commit 초안과 파일 목록을 사용자에게 제시한다. 승인 후에만 commit한다. 구현/정정/평가 결과는 목적별로 나눈다.
- [ ] 최종 보고에 status, issue/task/design, changed_files, executed/passed/failed/blocked checks, assumptions, risks, required_human_decisions를 포함한다.
- [ ] push/PR/merge와 운영 도입은 수행하지 않는다. 기존 worktree와 원시 증거를 임의 삭제하지 않는다.

## Self-review / Spec Coverage

| Spec 영역 | 수행 Task |
| --- | --- |
| 승인/역할/계약 | 1, 2, 12 |
| 정책 보존·공통 정정 | 2, 3, 4, 6, 8 |
| B2 기계 강제 | 5 |
| 격리·fixture | 3, 8, 9 |
| 정확한 prompt·204회·산식 | 7, 8, 10, 11 |
| 실제 변경 품질 및 검증 부수효과 | 9, 10 |
| 비민감 증거·선정·미검증 | 11, 12 |

이 계획은 설계 범위 안의 단일 누적 실험이다. 실행은 역할 분리와 독립 검증을 유지하는
subagent-driven 방식으로 제안한다. 별도 새 Codex task는 만들지 않고 현재 작업에서 인계한다.
