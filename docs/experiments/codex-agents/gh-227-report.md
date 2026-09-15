# GH-227 Phase 4 적용 및 검증 보고

status: BLOCKED (지침 구현·필수 검사 통과; 제한 회귀 환경 검사 실패로 중단)
issue_number: 227
task_id: GH-227-INSTRUCTION-ROLLOUT-PHASE4
design_id: HARNESS-DESIGN-GH-227-001

## 적용 범위

사용자가 설계와 수정 예시 확인 후 “오케이 진행해줘”로 테스트 영역7개 지침 파일 적용 및 제한 회귀4회를 승인했다.
TEST_SCOPED는 B3와 다른 새 조합이다. 인프라·역할·전달·안전 root 정책은 그대로 유지했다.

| 파일 | 이전 bytes | 이후 bytes | 변화 |
| --- | ---: | ---: | ---: |
| AGENTS.md | 25,233 | 25,006 | -227 |
| src/test/AGENTS.md | 0 | 1,130 | +1,130 |
| src/integrationTest/AGENTS.md | 0 | 1,269 | +1,269 |
| harness-test-plan/SKILL.md | 839 | 888 | +49 |
| harness-test-plan/references/test-policy.md | 0 | 762 | +762 |
| harness-test-run/SKILL.md | 1,110 | 1,231 | +121 |
| harness-test-run/references/reporting.md | 0 | 270 | +270 |

Skill 경로는 .agents/skills 아래다. root 자체는 약0.9% 작아졌다. 새 하위 지침 때문에 root+단위 시작 경로의 문서 bytes는 기존보다903, root+통합은1,042 증가한다. 이 수치는 실제 자동 로딩량이나 토큰이 아닌 정적 파일 합이다. 모든 파일 총합이나 전체 모델 입력을 지침 토큰 절감으로 해석하지 않는다. 로컬 tokenizer가 없어 추정 토큰과 정확 instruction token은 UNAVAILABLE이다.

## 정책 보존과 독립 검토

원문 §3을 두 reference로 이동하고 root·하위 AGENTS·기존 Skill에서 읽는 시점을 연결했다. §3 외 root는 byte-identical이다. 두 Skill의 metadata와 기존2번 이후 단계·승인 조건은 동일하다.
[새 정책 mapping](gh-227-policy-map.csv)은 과거 source map의447개 원문 단위와 hash·대상 줄 범위를 연결한다.447은 독립 정책 수가 아니다. 접두사만 같은 대상 줄을 가리키던5개 좌표를 수정했고 독립 검증자가447개 모두 완전한 원문 블록과 일치함을 확인했다. 링크·anchor 및 의미 보존은 검사했지만 원격 정책 강제나 모든 행동을 증명하지 않는다.
원본 원장 보호 P316은 문자열 보존만으로 기계 강제된다고 하지 않는다. 실제 금지 행동은 실행하지 않고 읽기 회귀에서 판단한다.

## 회귀 계약

추가 평가4회,Sol/high,입력2M·출력40k·30분,개별8분,replacement0·하위평가0. 과거204회/smoke는 재활성화하지 않았다.
회귀는 안내된 정책 준수와 한정 작업 수행을 확인한다. 프롬프트가 기대 정책 및 실행 범위를 설명하므로 무안내 지침 발견 능력이나 통계적 비용 효과를 평가하지 않는다.
실제 소스 설명 수정은 별도 비공개 사본에서만 허용되며 공개 branch의 Java 파일은 변경하지 않는다. 실행 스냅샷·prompt·runner·helper·환경 hash를 고정하고 결과 검토 gate 뒤에 다음 호출을 실행한다.
초기 manifest는 모델 호출 전 network 설정 보완으로 v2에 supersede되었으며 원본을 보존한다. Gradle/Testcontainers 사용을 위한 workspace-write network 허용은 실행 프로세스 한정이며 계정 설정이나 운영 권한을 바꾸지 않았다. 실제 전달 tool catalog는 UNAVAILABLE이며 전역 도구 동등성을 주장하지 않는다.

## 실행 결과

| 회귀 | 실제 실행 | 결과 |
| --- | --- | --- |
| 1 root 계획 작성 | Sol/high 1회,185.64초,9 tool calls,child0,exit0 | 계획 생성·기존 snapshot 보존. POST_ENVIRONMENT_DRIFT로 유효 회귀 채택 불가 |
| 2 단위 실제 설명 수정·검사 | 미실행 | 승인된 환경 실패 중단 조건 적용 |
| 3 통합 실제 설명 수정·검사 | 미실행 | 같은 중단 조건 적용 |
| 4 혼합·안전 경계 읽기 | 미실행 | 같은 중단 조건 적용 |

누적 입력421,601(캐시 포함),출력8,214,시간185.64초. 재시도·replacement·reset credit0.
환경 차이는 설정/플러그인 inventory aggregate hash에 한정되며 CLI 및 helper hash는 동일했다. 첫 manifest에 파일별 이전 hash가 없으므로 정확한 변경 파일·주체·원인은 확정할 수 없다.
추가 조사에서 실행 중 수정 시각인 파일은 config.toml 하나였고, 실행 직후 현재 설정에는 해당 평가 사본의 trust_level=trusted 항목이 있다. 이는 CLI 시작 시 신뢰 등록과 일치하는 정황이지만 이전 파일별 증거가 없어 원인으로 확정하지 않는다. 에이전트가 설정 파일을 수정하는 tool call은 없었다. 해당 설정을 임의로 수정·되돌리지 않았다.

계획 생성 결과는 root·양쪽 테스트 지침·계획 Skill·test-policy·원본 테스트를 읽고 지정 문서를 생성했다. 원래 snapshot1,445개 파일은 보존됐다. 결과 문서의 동일 세션 실행 표현은 평가 fixture의 “this single evaluation session”과 연결되어 있어 지침 변경의 오류로 곧바로 귀속하지 않는다. fixture의 역할·회귀 세션 경계와 승인된 계획/새 산출물 draft의 차이를 명확히 보완해야 한다. 환경 실패와 산출물 품질 판정은 별개다.

## 필수 검사 결과

- ./harness pr-ready --project-tests: PASS,6분50초.
- 단위 테스트1,064개(168클래스),통합735개(93클래스): 모두 실제 실행,실패/오류/skip0. UP-TO-DATE/FROM-CACHE 재사용으로 표기되지 않았다.
- ./harness check: PASS.
- npm run hooks:validate: PASS.
- git diff --check: PASS.
- 기존 Gradle 설정의 checkstyleTest/checkstyleIntegrationTest는 SKIPPED이며 이를 실행했다고 주장하지 않는다. Java convention 및 다른 check 작업은 통과했다.
- 별도 Terraform 검사는 Terraform 변경이 없어 수행하지 않았다.

준비 중 Codex sandbox CLI 도움말 확인에서 이전 macos 하위 명령 사용과 permission-profile 미지정 명령이 실패했다. sandbox 내부 Docker 검사가 실행됐다고 주장하지 않는다. 일반 Docker info는 성공했고 회귀1의 실제 workspace-write/network 설정은 rollout에서 확인했다. 회귀2/3은 미실행이므로 평가 sandbox에서 Gradle/Docker 동작은 미검증이다.

changed_files: 설계의7개 지침 파일; TASK.md; Phase4 spec/plan; gh-227-policy-map.csv; gh-227-report.md; gh-227-verification.md
executed_checks: 원문447개/링크/역할/승인 독립 검토; runner 오프라인 예산·순서 검사; 회귀1; 필수 저장소 검사
passed_checks: 정적 정책 보존·link 및 필수 저장소 검사
failed_checks: 회귀1 POST_ENVIRONMENT_DRIFT (환경 검증)
blocked_checks: 회귀2~4·sandbox 실제 변경 검증; 정확 instruction token; 기간 Before 준비/완료
assumptions: 정적 파일 크기는 runtime instruction 비용이 아님; 회귀는 안내된 시나리오
risks: 실제 변경 회귀 미완료; 환경 drift 인과 미확정; 원래 evaluator fixture의 실행 주체 모호함; 비용효과 미확인
required_human_decisions: 아래 재개안 승인 여부. 현 상태에서는 Phase4 완료·최종 도입 승인 요청하지 않음

## 검토 가능한 재개안 (미승인·미실행)

기존 실패1회를 원본 그대로 보존하고, fixture와 파일별 환경 기록을 보완해 신규4회를 별도 batch로 수행한다. 총평가5회이며 원래 누적 입력2M·출력40k·30분 상한은 늘리지 않는다. 남은 예산은 입력1,578,399·출력31,786·약1,614초,개별8분이다. 새 batch에서도 replacement0 및 환경/usage/safety 실패 시 다음 호출 중단을 유지한다.

새 저장소 신뢰 등록 변수를 줄이기 위해 이미 사용한 비공개 사본1개에서 root→unit→integration→mixed를 순차 실행하는 방안을 제안한다. 원래 실패 산출물·raw는 보존하고 새 계획/보고서는 새 파일명에 저장한다. 단위와 통합은 별도 모델 세션으로 실행하고 각 실행 전후 snapshot 및 허용된 diff를 개별 보관한다. 공유 사본의 순차 회귀이므로 독립 비용 비교로 해석하지 않는다.

준비 단계에서 읽기 전용으로 config/plugin 파일별 hash와 비민감 구조 비교용 hash를 기록한다. 어떠한 drift도 자동 제외·통과시키지 않는다. CLI가 자동으로 기록한 신뢰 항목을 에이전트가 임의 변경하지 않는다.
fixture에는 단위/통합 별도 세션,이미 승인된 정확한 수정 범위와 새 계획 문서의 draft 표기를 구분한다. 실행 전에 새 prompt·snapshot·collector hash 및 gate를 독립 검토한다. 이 제안은 실행 승인으로 처리하지 않는다.

## 보존 및 롤백

기존 checkout/실험 worktree/원시 원장은 변경하지 않았다. 이 branch의7개 지침 파일 변경만 커밋 단위로 되돌리고 증거 문서는 보존한다. 계정 모델·플러그인·권한 설정, AWS 리소스와 제품 동작은 변경하지 않았다.
커밋 및 stacked Draft PR 초안은 회귀·필수 검사·독립 검증 후 확정한다. base는 chore/gh-225-instruction-model-phase3이다.
