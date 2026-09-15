# GitHub Issue #227 Task Contract

## Work gate

- Title: `Phase 4 지침 구조 최종 적용 및 정책 보존 검증`
- GitHub Issue: `#227`
- Branch: `chore/gh-227-instruction-rollout-phase4`
- Base branch: `chore/gh-225-instruction-model-phase3`
- Task ID: `GH-227-INSTRUCTION-ROLLOUT-PHASE4`
- Design ID: `HARNESS-DESIGN-GH-227-003` (APPROVED_FOR_IMPLEMENTATION; 기존001/002는 아래 이력 보존)
- Status: `AFTER_V1_VERIFIED` (적용·필수 검사·독립 검토 완료; 커밋·PR 게시 진행)
- Base commit: `6ec850652270000f94d561340ac59e09b508f6cd`
- Intake approval: 사용자의 새 Issue 생성 및 Phase 4 시작 요청. 구체적 적용 설계 승인은 별도다.
- Project: P2 / In Progress / Chore / Sprint 미지정(활성 iteration 없음).

## Objective

Phase 1~3의 실제 증거로 지침 구조 적용 범위를 정하고 승인된 범위를 격리 branch에서 구현·검증한다.
최적화 대상은 대표 작업의 세션 전체 토큰이다. 하나의 실행 구성을 고정해 기존/개선 지침을 비교하며, instruction 로딩 비용은 별도로 구분한다. 모델·워크플로우 간 효율 비교는 제외한다.

## Scope

- 선행 Issue·PR·설계·결과·정책 mapping 감사.
- 파일 범위·정책 보존·제한된 실제 변경 회귀·예산·롤백 설계.
- 사람의 설계 승인 후 해당 지침 파일 구현과 독립 검증.
- 구체적 commit 및 stacked Draft PR 초안 준비.

## Explicit exclusions

- 이전 204회 평가 및 actual-change smoke 자동 재활성화.
- 애플리케이션·Terraform·DB·인프라·운영 변경과 배포.
- 계정 전역 모델·플러그인·권한 변경 및 reset credit 사용.
- 기존 checkout/실험 worktree 변경·정리 및 과거 원장 수정·삭제.
- 측정 플랫폼·OTel·Grafana·EC2 구축.
- 승인 없는 평가 호출·커밋·push·PR·merge 및 main push.
- 원시 prompt/code/log와 민감정보의 공개 산출물 포함.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 근거 종합·설계·계약 | 오케스트레이터 | 사람의 적용 설계 승인 |
| 독립 근거 감사 | 읽기 전용 감사 에이전트 | 실제 파일 및 원문 정책 확인 |
| 승인된 지침 구현 | 실행 에이전트 | 독립 검증 에이전트 |
| 정책·회귀·보고 판정 | 독립 검증 에이전트 | 사람 최종 도입 판단 |

## Existing user-owned changes

작업 시작 git status --short는 빈 출력이었다. 이 앱 worktree에서만 작업한다.
Harness가 다른 worktree에서 사용 중인 로컬 부모 branch 갱신을 거부해 그대로 두었고,
원격 부모 commit에서 Phase 4 branch 생성에 성공했다.

## Validation

준비 단계: Issue·Project·branch·base 확인, 문서 자체 검토, git diff --check, harness check.
구현 후: 전체 baseline 정책 mapping 및 링크 검증, 승인된 제한 회귀, 독립 검증,
./harness check, ./harness pr-ready --project-tests, npm run hooks:validate, git diff --check.
실제 검사와 재사용 결과, 환경 실패 및 미검증 범위를 구분한다.

## 기존001 설계 Completion criteria (역사적 승인 범위)

- [x] Project draft → Issue #227 → 필드 → branch/TASK 연결.
- [x] 구체적 적용 설계·실행 횟수·예산 승인.
- [x] 전체 baseline 정책 보존 및 승인된 구현.
- [ ] 제한 회귀·필수 검사·독립 검증 보고.
- [ ] 검토 가능한 commit/stacked Draft PR 초안.
- [x] Before 측정 및 최종 도입 gate 기록.

Before 준비/완료는 현재 확인되지 않았다. 이는 최종 main 도입 gate이며 격리 설계 준비를 막지 않는다.

## 검토 가능한 설계

[Phase 4 적용 설계](docs/superpowers/specs/2026-09-14-instruction-rollout-phase4-design.md).
독립 근거 감사는 테스트 영역 7개 지침 파일 한정안을 권고했다.
사용자가 수정 예시 확인 후 “오케이 진행해줘”로 설계 적용과 제안된 제한 회귀를 승인했다.
새 회귀 4회·입력2M·출력40k·30분, replacement0·하위평가0, 개별8분 상한을 유지한다.
커밋·push·PR 승인은 별도 구체적 초안에 적용한다.

## 현재 실행 결과

7개 지침 파일 구현과 원문447개 보존 독립 검토 완료. 필수검사 PASS:단위1,064·통합735 실제 실행.
신규 회귀1회는 exit0으로 계획을 생성했으나 POST_ENVIRONMENT_DRIFT로 제외했다. 입력421,601·출력8,214·185.64초를 보존한다.
승인된 중단 조건으로 나머지3회는 미실행. 재실행/replacement0. 상세는 GH-227 보고서와 검증 보고서를 따른다.
실패1회 보존+보완 신규4회(총5회,원래 누적 토큰/시간 한도 유지)의 재개안은 제안 상태이며 미승인이다.
커밋·push·PR·main 도입은 실행하지 않았다.

## 수정된 목표와 계획 작성 요청

사용자는 최적화 대상을 테스트/인프라 지침 읽기에 한정하지 않고 기능 구현·버그 수정·리팩터링·아키텍처 설계·요구사항 분석·복합 작업의 세션 전체 비용으로 명확히 했다. 지침 로딩은 별도 구성 요소로 측정한다.
B2/B3를 배타적 승자 후보로 고정하지 않고 필요한 위치/작업별 지침을 조합한다. 최신 요청에 따라 모델 역할 조합·워크플로우 간 효율 비교는 제외한다. 하나의 실행 구성을 고정해 기존 대비 개선 지침의 토큰 변화를 확인하고, 이후 사용자별 실사용 기간 측정의 After-v1 후보로 삼는다. 대표 과제6종은 유지하며, 고정 실행 구성은 사용자의 실제 워크플로우를 강제하지 않는다.

수정 계획: [Phase4 v2](docs/superpowers/plans/2026-09-14-instruction-hybrid-workflow-phase4-v2.md).
제안 설계 ID HARNESS-DESIGN-GH-227-002는 DRAFT_FOR_REVIEW다. 기존001 설계의 실행 승인·결과는 역사적 범위로 보존한다.
현재는 재설계 계획 작성 단계이며 기존 실패1+신규4회 재개 제안 및 미실행3회는 보류한다. 새 후보 구현·평가 예산·Issue 범위 변경·commit/push/PR은 아직 승인/실행하지 않았다.

## v2 감사·설계 진행 결과

사용자의 새 세션 진행 요청에 따라 기존14개 미커밋 파일을 비공개 snapshot/hash로 보존하고 지침 감사를 수행했다. 원본 실험 원장과001 보고서는 수정하지 않았다.

- 감사: docs/experiments/codex-agents/gh-227-v2-instruction-audit.md
- inventory: docs/experiments/codex-agents/gh-227-v2-instruction-inventory.json
- 제안447단위 mapping: docs/experiments/codex-agents/gh-227-v2-policy-map.csv
- 구체적11파일 후보 설계: docs/superpowers/specs/2026-09-14-instruction-hybrid-phase4-v2-design.md
- 평가 준비: docs/experiments/codex-agents/gh-227-v2-fixture-catalog.md (부분 준비; 실행 manifest 아님)

실사용 Before 버전, 완성된 과제 입력/승인·검사 계약, 환경 drift 해결과 새 평가 예산은 미확정이다. #227은 OPEN을 확인했으며 최신 목표와 Issue 본문 목적의 동기화는 실행 계약 확정 시 처리 대상으로 남겼다. 현재 runtime 지침 추가 수정·신규 평가·커밋/push/PR은 하지 않았다.

## Before 및 과제 실행 계약

사용자가 Before를4aa58b8b5cad727372e7dac1dd254099b62243b8의 저장소 하네스로 확정했다.85파일 manifest, GH-227-EVAL-V2-001 공통 실행 계약,6종 상세 과제 계약을 작성했다. 기존의 Before 미확정 기록은 이 확인으로 해소됐다.
C0원본/C1개선 두 군만 비교하며 N0 제안은 제외한다. F는 이미지 Resolver 한정, R은 기존 retry factory 호출부 치환, A/Q의 source snapshot과 질문 응답표를 구체화했다. 테스트 명령은 소스에 근거해 지정했으며 제품 검사를 실행한 것으로 보고하지 않는다. 실제 fixture·oracle 생성/오프라인 검증, 환경 drift 판정, C1 구현 및 평가 예산 승인은 남아 있다.

## v2 오프라인 준비 완료

사용자의 “좋아 다음으로 진행해줘”에 따라 새 비공개 사본에서 6과제 fixture·oracle/참고 구현과 C1 후보를 준비했다. 원본 운영 지침 적용·벤치마크 CLI 호출·커밋·push는 수행하지 않았다. C1 정책447개 보존 및 F/B/R/X 전체 회귀 검증이 통과했다. 실제 평가용 입력 분리·수집기·환경 gate와 구체적 예산 확정은 남았다. 이전 절의 미준비 상태는 해당 작성 시점의 이력이다.

상세: [오프라인 준비 결과](docs/experiments/codex-agents/gh-227-v2-offline-preparation-report.md).

## v2 파일럿 진행 요청

사용자가 파일럿 이후 단계 설명을 확인하고 “오케이 진행해줘”로 후속 진행을 승인했다. GH-227-EVAL-V2-001의 기존17세션·입력6M·출력120k·150분·개별15분·replacement0 상한과 실패 중단 조건을 따른다. 현재는 정답 없는 소형 입력·수집기·환경 사전검증 단계다. 실제 실행 결과는 별도 보고하며, 운영 도입/커밋/push/PR은 수행하지 않는다.

## v2 파일럿 실제 결과

Before 첫turn이30.30초에 LIVE_ENVIRONMENT_DRIFT로 중단됐다. 입력68,018(캐시41,600)·출력843을 보존했다. config의 해당경로 trust항목 추가와 Sites캐시4파일 소실을 확인했으며 캐시변화 원인은UNKNOWN이다. 후속turn·After·독립모델검토·본비교는미실행, replacement0유지. 전역설정을 직접수정하거나실패원장을덮어쓰지않았다. [파일럿 보고서](docs/experiments/codex-agents/gh-227-v2-pilot-report.md)를 따른다.

## v2 파일럿 재개 승인

사용자가 알려진 trust설정추가와 Sites캐시소실을 예외로 허용하고 재실행에 동의했다. 실행계약12절에 따라 pilot-002를 준비한다. 과거실패는보존하고 사용량을전체한도에포함하며 다른환경검사·안전·품질gate는유지한다.

## v2 재개 파일럿 완료

승인된환경예외하에 Before/After 각2turn과별도검토를완료했다. 원시증분·누적검증및독립검토PASS다. Before수집기해석오류는원장보존후별도정정했으며모델재호출은없다. [재개파일럿보고서](docs/experiments/codex-agents/gh-227-v2-pilot-resume-report.md)에전체사용량과잔여예산을기록했다. 본6과제·운영적용·커밋·push·PR은미실행이다.

## v2 본 비교 준비

사용자의 “좋아 다음”으로본비교를진행한다. clean12사본·동일TASK/input·정답이력분리·고정Java/Gradle환경을준비했고, Gradle/Docker샌드박스사전접근검사가통과했다. 실행계약15절의부수산출물/접근범위와기존잔여예산을따른다.

## v2 측정 최종 마감

12개 과제와 독립 리뷰 2개 수집을 완료했다. F/B/R/A/X 양쪽 PASS, Q 양쪽 FAIL이다. 최종 수치·품질·예산·한계는 `docs/experiments/codex-agents/gh-227-v2-results.md`와 동명 CSV를 따른다. 앞선 진행 상태와 실패 기록은 당시 이력으로 보존한다. 운영 적용·커밋·push·PR은 미실행이다.

## 선택 반복 요청

사용자가 B/X Before·After 각각2회 추가와 F/R/A 실행 경로 분석, Q 지침 우선 보완을 요청했다. `docs/superpowers/plans/2026-09-15-instruction-targeted-repeat.md`에 범위·고정 조건·예산 질문을 기록했다. Run 1 결과와 반복 입력 지침은 보존한다.

## 6회 후 일시 중지

사용자 요청에 따라 추가6회 수집과 독립검증을 마무리하고 멈춘다. 7/8(X Run3 양쪽)은 미실행이며 재개 요청 전 시작하지 않는다. F/R/A 분석과 Q 후보는 작성 완료, Q 실제 재평가는 미실행이다. 상세는 `docs/experiments/codex-agents/gh-227-v2-repeat-report.md`를 따른다.

## 추가 8회 수집 및 최종 oracle 완료

추가 B/X 8회 수집이 완료됐다. 1~6은 원 결과, 7/8은 continuation-v2 canonical 결과이며 7번 중단 사용량을 중복 합산하지 않는다. 과거 일시 중지 기록은 당시 상태로 보존한다. 최종 X v5를 Run1 양쪽·반복 네 실행에 동일 적용하는 여섯 검증과 독립 감사는 완료됐으며 8번 gate는 PASS다. B 감소의 재현 불안정과 X 세 쌍의 감소 방향은 관측값이며 확증이 아니다. 상세 수치·환경/검증기 보정·미관측 한계는 gh-227-v2-repeat-report.md와 8행 CSV를 따른다. Q 재평가 및 운영 적용은 미실행이다.

## 추가 반복 최종 마감

상태: PASS(실험 종합 범위). 추가8회 수집·canonical 집계·품질 검토를 완료했다. X v5 동일 적용6건은 각 단위19·통합4, 실패/오류/skip0이며 독립 final-v5-common-audit.json에서 PASS다. 8번 gate PASS. task2는 환경 영향 TASK_FAIL로 유지한다. harness check·hooks:validate·diff --check PASS이며 루트 pr-ready --project-tests는 이번 문서 마감에서 미실행이다. Q 재평가·운영 적용은 범위 밖으로 남긴다. 위 중지·대기 상태는 당시 이력이며 이 절이 현재 상태다.

## After v1 적용·PR 게시 승인 (현재 계약)

사용자가 After 기준안과 #227 적용·검증·커밋·PR 게시 범위를 확인하고 “그럼 그렇게 진행해줘”로 승인했다. [최종 적용 계획](docs/superpowers/plans/2026-09-15-after-v1-rollout.md)을 따른다. 기존 C1을 기반으로 Q 근거 관리와 탐색 지침을 보완하되 정책·권한·필수 검사 조건을 보존한다. 이전의 커밋/push/PR 미승인 문구는 당시 이력이며 현재 승인은 #227 branch의 적용과 stacked Draft PR에 한정한다. 추가 벤치마크·병합·배포는 범위 밖이다.

## After v1 검증 결과

최종 runtime17파일을 적용했다. 원문447개·링크23개·manifest17개 hash의 독립 검토PASS, 기존 연구 기록30파일 byte 보존 및 게시용 CSV3파일 셀·결과 hash 보존(CRLF→LF 변환, 원본 snapshot 보존). harness check/pr-ready --project-tests/hooks:validate/diff --check PASS. 단위1064·통합735 결과는 Gradle UP-TO-DATE 재사용이며 새 실행으로 보고하지 않는다. 세부는 `docs/experiments/codex-agents/gh-227-after-v1-rollout.md`를 따른다. 추가 문구의 효율·Q 재평가는 미실행이다.
