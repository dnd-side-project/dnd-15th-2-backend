# GH-223 instruction 충돌 감사

Issue #223 / Task GH-223-INSTRUCTION-ARCHITECTURE-PHASE2 / Design HARNESS-DESIGN-GH-223-001

상태: FAIL (기존 문서의 정합성). 독립 읽기 전용 감사에서 충돌을 확인했으며 금지 행위를 실행한 결과가 아니다.
공통 기반에서 정정하고 B0~B3에 동일하게 적용하는 방식은 사용자가 `승인`했다. 실제 파일 수정은 전체 설계·구현 계획 승인 후 수행한다.
기준 commit: 4aa58b8b5cad727372e7dac1dd254099b62243b8.

| ID | 근거 | 문제 | 보존할 정책과 수정 제안 |
| --- | --- | --- | --- |
| IC-01 | agents/infrastructure-executor.md:5,11; infrastructure-orchestrator.md:29; issue/references/issue-forms.md:75; commit/references/splitting-rules.md:78 | Terraform 또는 CDK 허용 | Terraform 전용, 기존 타 IaC는 임의 제거하지 않고 별도 Issue·마이그레이션 승인 |
| IC-02 | agents/infrastructure-executor.md:6,20–28; issue/references/issue-forms.md:83; AGENTS.md:315 | apply의 기본 금지/승인 후 수동 단계가 AI 실행 허용으로 오독됨 | AI 실행 절대 금지, 보호된 Actions 10개 조건, 사람 복구 승인과 AI 권한 구분 |
| IC-03 | harness-issue/SKILL.md:135–146 | Issue 직접 생성 후 Project 추가 | Project draft 계획→검토/승인→Repository Issue 전환→필드/branch/TASK |
| IC-04 | harness-infra-design/SKILL.md:25–31; harness-infra-build/SKILL.md:27–34 | reference를 없는 Skill로 호출, 교차 경로 불명확 | 실제 상대 경로와 agent 위임을 구분, 독립 검증 handoff 명시 |
| IC-05 | CLAUDE.md:290; harness-infra-design/SKILL.md:15–18 | TASK 없이 작업 전체 차단, infra branch/사용자 제공 ID 고정 | 읽기·draft 계획과 구현 게이트 구분, ID 생성은 승인 증거와 분리 |
| IC-06 | agents/infrastructure-orchestrator.md:8–12; agents/pm-reviewer.md:20 | EC2/ECS·RDS/자체 운영 비교 무조건 요구 | 워크로드에 적합한 대안만 비교 |
| IC-07 | harness-pr/SKILL.md:66–73 | 실패 로그 그대로 보고, 무조건 plan 및 정적 검사 누락 | 민감값 제거한 요약, 기본 검사 보존, 승인 환경에서만 plan, 미실행 상태 정확히 보고 |
| IC-08 | harness-test-run/SKILL.md:19; agents/test-executor.md:36; agents/api-docs-executor.md:51; harness-api-docs/SKILL.md:129 | 역할의 커밋 지시와 Skill 범위/승인 route 불일치 | 완료 보고→별도 harness-commit 초안/사람 승인, 자동 commit 금지 |

표에서 issue/commit/harness-* 약칭은 `.agents/skills/` 아래 실제 해당 스킬 디렉터리다.
감사자는 AGENTS/CLAUDE/TASK, agents 8개 파일, 저장소 SKILL 10개와 reference 12개를 읽었다.
글로벌 using-superpowers는 배정된 subagent 예외를 확인했다. 전체 글로벌/플러그인 chain 감사는 별도다.
검토 권고, 독립 검증 판정과 사람의 승인 상태는 다른 필드로 유지한다.

## 승인된 공통 fixture 방식

위 충돌을 B1의 문서 이동과 동시에 수정하면 행동 개선이 구조 변경 때문인지 정책 수정 때문인지
분리할 수 없다. 따라서 baseline 의미에 맞춘 충돌 정정을 공통 fixture에 먼저 수행하고
B0~B3 모두 같은 정정을 상속하게 한다. 이는 다섯 번째 실험 후보가 아니라 통제 환경이다.
정정 자체의 파일 diff와 instruction bytes를 별도 기록하고 B단계 절감량에 섞지 않는다.

- 기존 A0~A3 및 Phase 1 증거는 그대로 보존한다.
- baseline의 의미를 바꾸는 정정은 허용하지 않는다. 모호한 해석은 사람 결정 전 보류한다.
- 공통 fixture를 확정한 다음 B0 root 복원, B1 상세 이동, B2 기계 강제, B3 범위 분리를 누적한다.
- B2에서 승인 게이트·CODEOWNERS·Ruleset·Apply workflow의 승인 절차를 변경하거나 약화하지 않는다.
- 외부 global Skill과 plugin 파일은 실험 중 변경하지 않고 버전/해시와 적용 지침을 기록한다.
- 공통 fixture와 B0의 정책 보존이 확인되지 않으면 B1~B3 평가를 시작하지 않는다.

## 판정 한계

문서 텍스트와 파일 존재 관계만 감사했다. Harness/Husky/CI enforcement, runtime 로딩,
모델 행동 및 smoke 품질은 미실행이다. 이 감사로 정책 보존 100%나 후보 적격을 주장하지 않는다.
