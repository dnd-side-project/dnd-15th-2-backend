# GH-223 B1~B3 구조 및 검사 경계 설계

Issue #223 / Task GH-223-INSTRUCTION-ARCHITECTURE-PHASE2 / Design HARNESS-DESIGN-GH-223-001

상태: APPROVED_SECTION_3. 공통 fixture 정정 및 B1~B3 파일 배치와 B2 검사 범위를 사용자가 각각 승인했다. 평가 설계 및 전체 spec/구현 계획 승인은 별도다.
전체 spec 이전의 설계 작업 자료이며 후보 구현 파일이 아니다.

## 공통 통제

- IC-01~08 정정은 모든 후보에 동일하게 적용한다. 추가 모호함은 baseline 문맥으로 해결 가능 여부를
  검토하고 의미 변경이 필요한 경우 독립된 사람 결정으로 남긴다.
- model gpt-5.6-sol/high, 외부 Skill/plugin/tool 구성, 평가 prompt, 작업 fixture는 고정한다.
- 후보마다 root와 상세 문서 차이가 있으므로 저장소 instruction 내용 자체는 통제 변수가 아닌 독립 변수다.
- Skill 이름과 frontmatter description은 공통 정정 후 후보 간 고정한다. 새 Skill은 만들지 않는다.
- 구조 변경을 이유로 기존 gate, CODEOWNERS, Ruleset, Apply workflow 권한/승인 절차를 수정하지 않는다.

## B0

역사적 A3에서 누락/과도한 압축을 baseline 원문 단위로 복원한다. root에 모든 규범 의미를 유지하며
공통 정정된 하위 문서로 상세 의무를 암묵적으로 떠넘기지 않는다. 목표 길이를 미리 정하지 않는다.
고정된 AGENTS 로딩 상한을 넘으면 잘라서 실험하지 않고 fixture 부적격으로 보고한다.

## B1: 상세 자연어 규칙의 필요 시 로딩

root는 안전 불변조건, 작업 시작/승인/보고 조건과 짧은 routing을 유지한다.
작업의 조건·예외·필수 목록은 함께 읽는 단위로 묶는다. 단어 수를 줄이기 위해 예외 조건을 다른
문서로 숨기지 않는다. 상세 reference는 기존 구조를 우선 활용한다.

| 정책 | 상세 소유 위치 | 진입점 |
| --- | --- | --- |
| Issue/Project/branch/TASK | .agents/skills/harness-issue/references/ | harness-issue/SKILL.md |
| 테스트 계획/실행/실패 보고 | .agents/skills/harness-test-plan/references/, harness-test-run/references/ | 각 SKILL.md |
| 인프라 입력/대안/보안/비용 | .agents/skills/harness-infra-design/references/ | harness-infra-design/SKILL.md |
| Terraform 규약/주석/검증/증거 | .agents/skills/harness-infra-build/references/ | harness-infra-build/SKILL.md |
| commit/PR/라벨/검증 기록 | harness-commit/references/, harness-pr/references/, docs/harness/LABELS.md | 각 SKILL.md |
| 역할별 책임 | agents/ 기존 역할 문서 | root 역할 routing 및 작업 Skill |
| 공통 민감정보/실패 복구 | docs/harness/SECRET_HANDLING.md, FAILURE_RECOVERY.md | root의 사전 불변조건 + 관련 Skill |

표에서 약칭 경로는 `.agents/skills/` 아래다. 기존 reference에 해당 규칙이 있으면 보강하고,
없을 때만 목적별 reference를 추가한다. 승인된 구현 계획에서 실제 파일명을 확정한다.
Skill에는 해당 작업의 필수 reference와 조건부 reference를 실제 상대 경로로 연결한다.
파일 존재를 의미 보존이나 자동 로딩 증거로 간주하지 않는다.

## B2: 기계 판정 규칙의 책임 분리

B1의 의미를 보존하며 기계 판정 가능한 세부 형식 설명을 reference/검사로 옮긴다.
root/Skill은 무엇을 지켜야 하는지와 언제 어떤 검사를 실행하는지를 유지한다.
사후 hook/CI가 작업 전 인지와 사람 승인을 대체하지 않는다.

| 대상 | 확인한 현재 수단 | B2 경계 |
| --- | --- | --- |
| branch/commit/PR type·Issue 형식 | scripts/validate-conventions.py, format-commit-msg.py | 기존 검사 재사용. 정상/오류 입력으로 차단 증거 확인 |
| TASK/branch 식별자 | harness.py는 branch 번호 추출 및 TASK 생성, 포괄적 TASK 일치 검사는 확인되지 않음 | 로컬 TASK 필수 필드/번호·branch 일치 검사 추가. Issue 실재·승인 내용은 별도 근거 확인 유지 |
| JUnit 메타데이터 | validate-java-tests.py가 첫 30줄 및 annotation 인접 문자열 검사 | 실제 날짜/생성 시각/원본 계획 진위를 보장한다고 주장하지 않음. 검사 범위 밖 의미 규칙은 reference 유지 |
| 공백/민감정보 패턴 | git diff --check, preflight.py | 기존 검사를 유지. 알려지지 않은 비밀값을 모두 탐지한다고 주장하지 않음 |
| 라벨/Husky/workflow | validate-labels.py, validate-husky.py, validate-workflows.py | 기존 검사와 권한 경계를 유지. 실제 원격 보호 설정과 다른 검증임을 명시 |
| instruction 경로 | 현재 전용 검사는 확인되지 않음 | 명시적 reference/AGENTS 경로 및 anchor 존재 검사 추가. 자연어 동등성은 독립 검토 |

TASK 일치/지침 경로 검사만 신규 정적 검사 후보로 제한한다. 기존 검사 자체의 전면 재작성,
Java 파서 도입, 승인 자동 판정과 새로운 보안 체계는 이 Issue 범위에 추가하지 않는다.
신규 검사에는 정상/오류 fixture와 재현 명령이 필요하며 저장소 전체 fixture에서 false-block을 확인한다.
검사 구현은 scripts/ 하위, 연결은 scripts/harness.py·scripts/run-hook.py와
.github/workflows/harness-policy.yml의 읽기 전용 policy job으로 제한한다.
Husky shell hook에 같은 검사 로직을 복제하지 않는다. stage 대상 검사와 working tree 검사를
구분하여 unstaged 변경만으로 staged 유효성을 오판하지 않도록 한다.
이 연결은 승인 게이트 완화나 Apply workflow 변경을 포함하지 않는다.

## B3: 적용 디렉터리 분리

B2 위에 아래 세 파일을 추가한다.

- infra/AGENTS.md: 인프라 설계/build reference 진입점과 infra 범위 의무
- src/test/AGENTS.md: 단위 테스트 의무 및 공통 테스트 reference 진입점
- src/integrationTest/AGENTS.md: 통합 테스트 의무 및 같은 공통 reference 진입점

테스트 규칙을 src/AGENTS.md에 두면 앱 코드 작업에도 로드될 수 있으므로 선택하지 않는다.
unit/integration에 긴 본문을 각각 복사하지 않고 공통 reference를 공유한다.
root는 infra/test/혼합 작업을 인식하면 해당 하위 AGENTS와 필수 reference를 작업 전에 읽도록 한다.
root 세션에서 cd만 했다고 자동 재로딩된다고 가정하지 않는다. 이미 읽은 파일은 내용 hash가
같다면 중복 읽기가 불필요하나, 실제 중복 로딩은 비용에서 제외하지 않는다.

root, infra, src/test, src/integrationTest 시작 cwd를 모든 후보에서 비교한다.
B3만 다른 cwd에서 측정하지 않는다. root에서 infra/test를 다루는 교차 범위 시나리오도 포함한다.
혼합 작업은 양쪽 규칙을 함께 적용하고 충돌 시 상위 안전/승인 조건을 완화하지 않는다.

## 자율 실행과 역할

읽기 전용 근거 수집과 승인된 계획 범위의 가역적 작업은 추가 확인 없이 진행한다.
Issue 없는 상태의 허용 범위는 baseline의 Project draft 계획·작업 분해 계약을 유지한다.
변경 실행은 승인된 계획·파일 소유권에 따른 executor가 담당한다. 독립 verifier는 실제 diff와
증거를 확인하며 통과 목적 수정이나 자기 승인을 하지 않는다.
독립적인 읽기/검증 작업이나 승인된 파일 소유권이 분리된 구현에 subagent를 사용한다.
필수 인계가 아닌 사소한 한 단계 작업을 매번 위임하는 의무는 만들지 않는다.
실행·검증·사람 승인 필드는 분리하고, 평가에 포함되는 모든 모델 호출은 Sol/high로 고정한다.

## 실패 처리와 비적격 조건

필수 문서 누락, 정책 의미 불명확, 로딩 상한 초과, 검사 미실행은 실패/차단 증거로 남긴다.
환경 오류에 의한 무효 run과 유효 run의 행동 실패를 구분한다. 유효 실패는 retry로 지우지 않는다.
정책을 알기 전에 금지 작업을 수행하고 CI가 잡는 형태는 안전 gate 통과가 아니다.
구조가 더 작아도 단 한 번의 안전/승인/routing 위반이나 허용 작업 false-block을 숨기지 않는다.

## 공식 근거와 한계

- https://learn.chatgpt.com/docs/agent-configuration/agents-md
  - 실행 시작 시 root부터 cwd까지 chain을 구성한다. 하위 경로 파일을 root 시작에서 자동 발견한다고 가정하지 않는다.
- https://learn.chatgpt.com/docs/build-skills
  - metadata를 먼저 읽고 선택된 Skill 본문을 읽는다. catalog 노출/생략도 실제 평가 환경에서 확인해야 한다.

공식 문서는 2026-09-11에 확인했다. 현재 문서 설명을 과거 CLI 0.153.4의 실측으로 간주하지 않는다.
Phase 2 실행 버전을 고정하고 실제 로딩 관측을 별도로 수집한다.
평가 섹션도 승인됐으며 표본 수·비용 산식·exact prompt·품질 gate는 전체 spec과 평가 문서에 기록한다.
