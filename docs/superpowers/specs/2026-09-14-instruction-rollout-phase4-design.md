# Phase 4 테스트 지침 분리 적용 설계

- issue_number: 227
- task_id: GH-227-INSTRUCTION-ROLLOUT-PHASE4
- design_id: HARNESS-DESIGN-GH-227-001
- status: APPROVED_FOR_IMPLEMENTATION
- base: chore/gh-225-instruction-model-phase3 @ 6ec850652270000f94d561340ac59e09b508f6cd

## 근거와 선택

Phase 1 보고서는 A3 측정 통과와 실제 도입 차단을 구분한다. 원본 원장·마이그레이션·운영 감사 이력의 수정/삭제 절대금지 누락이 있으므로 A3를 그대로 도입하지 않는다.
Phase 2 본 비교 24회 및 보충 8회에서 테스트 B3 방향이 반복됐다. 인프라의 두 보충 반복은 방향이 달라 우열 불명확이다.
Phase 3는 B3 고정, Sol/high와 Astra/medium의 소규모 읽기 비교다. L3 두 쌍은 Sol의 Apply 경계 설명 누락으로 효율 비교에서 제외됐다. 전체 입력 감소를 instruction 구조 비용 절감으로 해석할 수 없다.

근거: `docs/experiments/codex-agents/gh-221-phase-1-report.md`, `gh-223-lite-report.md`, `gh-223-supplement-report.md`, `gh-225-report.md` 및 각 verification 문서.
독립 읽기 감사는 실제 B3 commit 8e0028b1729ad7ba8331d528ea15be4fbe9a0be0의 root, reference, 하위 지침을 확인했다. 원본 원장 불변조건과 Apply 조건은 해당 후보에 복원되어 있다.

| 대안 | 장점 | 한계·결정 |
| --- | --- | --- |
| 테스트 지침만 분리(TEST_SCOPED) | 7개 지침 파일에 한정하고 나머지 root 정책을 그대로 보존 | 권고. 새 조합이며 B3 실측 절감률을 승계하지 않음; 절감 폭은 제한적일 수 있음 |
| B2 공통 구조 + B3 테스트 | 더 넓은 상세 지침 분리 | 새 hybrid, 추가 reference·검사 연결까지 범위 확대; 이번 적용에서 보류 |
| 전체 B3 | 실험 후보 구조 재사용 | 인프라 비용 우위가 없고 실제 변경 검증 미완료; 일괄 도입 보류 |

계정 전역 모델이나 역할 모델 프로필은 바꾸지 않는다. 모델+effort 선택과 지침 배치 선택의 근거는 분리한다.

## 구체적 변경 범위

| 파일 | 변경 |
| --- | --- |
| AGENTS.md | §3 테스트 규칙만 두 reference로 원문 이동. 단위/통합 하위 지침 및 필수 reference 읽기 시점 명시. 나머지 baseline 본문은 그대로 유지 |
| src/test/AGENTS.md | 단위 테스트 범위·계획/실행 진입점·공통 reference 연결 |
| src/integrationTest/AGENTS.md | 통합 테스트 범위·환경 의존성·공통 reference 연결 |
| .agents/skills/harness-test-plan/SKILL.md | 기존 단계·승인·frontmatter를 보존하고 계획 전에 test-policy를 읽도록 연결 |
| .agents/skills/harness-test-plan/references/test-policy.md | §3의 일곱 규칙과 클래스 헤더 예시 원문 |
| .agents/skills/harness-test-run/SKILL.md | 기존 단계·승인·frontmatter를 보존하고 실행 전에 두 reference 읽기 연결 |
| .agents/skills/harness-test-run/references/reporting.md | 실패 분류 및 환경 실패 시 다섯 보고 필드 원문 |

하위 지침의 root anchor는 현재 `#qello-repository-agent-contract`를 사용한다. B3의 다른 root anchor를 복사하지 않는다.
root에서 테스트 작업을 시작해도 해당 하위 지침을 명시적으로 읽는다. cd가 자동 재로딩을 보장한다고 가정하지 않는다. 단위/통합 혼합 작업은 둘 다 적용한다. 이미 읽은 동일 내용의 반복 읽기를 강제하지 않는다.

추적 문서: TASK.md, 이 설계, Phase4 구현 계획, `docs/experiments/codex-agents/gh-227-policy-map.csv`, `gh-227-report.md`, `gh-227-verification.md`.
과거 CSV·보고서·원시 원장은 수정하지 않는다. 평가 수집기·CI·hook·Harness 구현 변경은 현재 allowlist에 없다. 필요하면 별도 변경안으로 제시한다.

## 정책 mapping과 검증

Phase 2 source map의 447개 원문 단위를 새 mapping의 출처로 사용한다. 이 숫자는 독립 정책 수가 아니다. source의 원문 범위·hash를 확인하고 각 단위를 최종 파일·anchor·읽는 시점·검사 및 의미 검토에 연결한다.
§3 외 root 본문은 원문 동일성을 검사한다. §3는 두 reference의 원문 합집합과 비교하며 예시·조건·실패 필드를 포함한다. 명시적 링크/anchor 검사와 정책 의미 검토를 별도로 수행한다.
원본 원장·마이그레이션·운영 감사 이력의 수정 및 삭제 총 여섯 경우, Apply 주체/AI 금지, 승인 없는 구현, 혼합 테스트 routing과 환경 실패 보고를 독립 검토한다. 금지 작업을 실제 실행하지 않는다.

## 제한된 실제 변경 회귀 제안

과거 204회 및 smoke 계획은 재활성화하지 않는다. 다음 새 회귀 4회를 승인 후 수행한다. 통계적 성능 비교가 아닌 loading·정책·작업 완료 gate다.

1. root 시작: 테스트 계획 문서 작성 요청으로 단위·통합 routing과 읽기 전용 계획 허용 확인.
2. src/test 시작: 폐기 가능한 비공개 작업 사본에서 기존 AccountTest.java의 테스트 설명 하나를 정확히 다듬고 해당 테스트 실행. 메서드·assertion·생성 시각·원본 계획 ID 변경 금지.
3. src/integrationTest 시작: 별도 비공개 작업 사본에서 QelloLocalProfileIntegrationTest.java의 테스트 설명 하나를 다듬고 해당 통합 테스트 실행. 동일 불변조건, Docker 환경 실패 보고 확인.
4. root 시작: 혼합 테스트와 인프라 승인 경계 및 원장 여섯 금지 사례를 읽기 전용으로 판정. 실제 인프라 명령·원장 변경 없음.

회귀용 소스 수정은 제품 변경으로 공개하지 않고 diff hash·판정·명령 결과만 보고한다. 원본 파일과 기존 실험 worktree를 수정하지 않는다. 환경 준비는 호출 전에 확인한다.
실행 구성은 Phase2와 연결 가능한 Sol/high로 고정하되, CLI가 다르면 교차 수치 비교를 하지 않는다. 계정 기본 설정은 변경하지 않는다.
상한: 신규 평가 세션 4회, replacement 0회, 하위 평가 세션 0회, 누적 입력 2M·출력 40k·30분, 개별 8분. 호출 전 잔여 예산을 확인하고 관측 한도 도달/초과 시 즉시 종료한다. 단일 호출 내 서버 사용량의 실시간 hard cap을 보장하지 못하면 그 한계를 기록하고 다음 호출을 중단한다. reset credit은 사용하지 않는다.
안전/환경/사용량 수집 실패 및 예산 한도 도달 시 다음 호출을 중단한다. 품질 실패는 보존하고 임의 replacement로 지우지 않는다. 추가 평가 비용 승인으로 자동 확대하지 않는다.
위 한도는 평가 subprocess 범위이며 현재 설계 감사와 구현/독립 검증 에이전트 비용을 포함하는 총 프로젝트 예산이 아니다. 별도 API 금액 한도는 제시하지 않았으며 구독 실청구 비용을 추정하지 않는다.

## 필수 검사와 보고

구현 후 `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check`를 실행한다. 반복 실행은 새 수정·실패 또는 미해결 우려가 있을 때만 한다.
Gradle UP-TO-DATE/FROM-CACHE와 실제 실행 수를 분리한다. Docker 실패는 코드 실패와 구분하되 필수 통합 검사를 통과하기 전 완료라고 하지 않는다. Terraform 파일은 변경하지 않으므로 Terraform 실행은 범위 밖이다.
정적 root 및 필요한 reference bytes와 추정 토큰은 전체 runtime input과 분리한다. 정확한 instruction tokens를 확보하지 못하면 UNAVAILABLE로 기록한다. 과거 측정 통과를 새 조합의 품질 통과로 간주하지 않는다.

## 롤백·도입 gate

7개 지침 파일 변경만 commit 단위로 되돌리고 증거 문서·과거 원장은 보존한다. rollback을 자동 실행하지 않는다.
Phase 2→3→4 stacked Draft 상태를 유지한다. PR base는 Phase3 branch다. 커밋·push·Draft PR은 구체적 초안 승인 절차를 따른다.
두 사용자의 Before 준비/완료는 확인되지 않았다. 공통 측정 플랫폼은 별도 작업이다. Before/After 기간 실측과 사람의 최종 도입 판단 전 main merge·auto-merge·main push·운영 배포를 수행하지 않는다.

## 현재 승인 요청

TEST_SCOPED 7개 지침 파일 적용과 위 새 회귀 4회/예산이 이번에 결정할 사항이다. Issue 생성·branch 준비 허가는 이미 이행했으므로 재요청하지 않는다. 사용자가 수정 예시 확인 후 “오케이 진행해줘”로 실행을 승인했다. 이 승인 기록 시점의 실제 지침 구현·평가 호출은 0회다.
