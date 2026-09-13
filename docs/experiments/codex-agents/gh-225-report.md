# GH-225 Phase 3 결과

status: PASS (승인된 평가·집계·저장소 검증 범위; 커밋 초안 승인 완료)
issue_number: 225
task_id: GH-225-INSTRUCTION-MODEL-PHASE3
design_id: HARNESS-DESIGN-GH-225-001

## 결론

B3를 고정한 읽기 평가에서 Astra/medium은 Sol/high보다 L1·L2의 전체 입력, 출력, 도구 호출과 시간이 적었다. 그러나 API 표준단가 환산액의 셀별 평균은 더 높았다. 이는 모델+effort 구성 비교이며 순수 모델 효과나 지침 구조 자체의 비용 절감을 입증하지 않는다.

시작 지침의 정확한 전체 source 일치 하한은 두 구성 모두 18,743 bytes였다. Astra의 추가 읽기 일치 하한 0은 지침을 읽지 않았다는 뜻이 아니다. 부분 발췌·줄번호·출력 wrapper 등은 이 계측에서 완전 일치로 잡히지 않으므로 모델 간 추가 지침량 절감률을 계산하지 않는다. 정확한 instruction token은 UNAVAILABLE이다.

본 비교 품질은 Astra 6/6, Sol 4/6 통과했다. Sol의 L3 두 답변은 Apply 경계 설명을 누락했다. 두 L3 쌍을 효율 비교에서 제외했으며 세 영역 전체의 우승 구성은 선정하지 않는다. 안전 행동은 12/12 통과했지만 실제 구현·인프라 안전성을 증명하는 실험은 아니다.

## 품질을 통과한 L1·L2 비교

각 구성·셀 n=2의 산술평균이다. 전체 입력에는 캐시 입력이 포함된다. 시간은 모델 subprocess와 수집기 경과 시간이다.

| 셀 | 구성 | 입력 | 캐시 제외 입력 | 출력 | 도구 호출 | 초 | API 환산 USD |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| L1 | sol/high | 271,130.0 | 42,970.0 | 4,628 | 8.0 | 136.0 | 0.355704 |
| L1 | astra/medium | 128,523.0 | 29,771.0 | 1,798 | 3.5 | 69.9 | 0.486362 |
| L2 | sol/high | 490,002.0 | 43,026.0 | 5,697 | 12.5 | 185.8 | 0.464834 |
| L2 | astra/medium | 131,968.5 | 31,104.5 | 1,781 | 3.5 | 72.1 | 0.500959 |

대응 반복별 Astra 변화율 = (Astra / Sol − 1) × 100. 반복별 편차를 숨기지 않기 위해 쌍별 값을 함께 제시한다.

| 셀·반복 | 입력 변화 | 시간 변화 | 환산액 변화 |
| --- | ---: | ---: | ---: |
| L1·1 | -70.0% | -60.4% | +18.0% |
| L2·1 | -77.0% | -62.2% | -11.6% |
| L2·2 | -65.4% | -59.9% | +36.2% |
| L1·2 | -19.4% | -30.9% | +62.5% |

L1은 시작 규칙 요약, L2는 테스트 계획·실행 규칙 확인, L3는 인프라 설계·승인 경계 확인이다. 고정 프롬프트와 순서는 [계획](../../superpowers/plans/2026-09-13-instruction-model-phase3.md) 및 [실행 순서](gh-225-execution-order.csv)에 있다. 각 셀 두 반복뿐이므로 유의성·일반 성능·향후 비용을 추정하지 않는다.

## 전체 실행과 제외

| 구분 | 횟수 | 입력 | 출력 | 초 | API 환산 USD |
| --- | ---: | ---: | ---: | ---: | ---: |
| 새 pilot | 2 | 406,429 | 9,001 | 249.3 | 0.8714760 |
| 본 비교 | 12 | 3,143,199 | 44,572 | 1424.4 | 5.6412332 |
| 과거 무효 pilot | 1 | 314,416 | 5,464 | 121.4 | 0.3430464 |
| 누적 | 15 | 3,864,044 | 59,037 | 1,795.1 | 6.8557556 |

실패 표본까지 포함해 승인된 총15회, $40·입력8M·출력160k·90분 및 개별10분 한도를 지켰다. 추가 replacement·reset credit·하위 평가 세션은 없다. 내부 transport retry는 UNAVAILABLE이며 관측 retry 신호는 없었다. 준비·검토 에이전트 사용량은 이 평가 누적에 포함하지 않으며 별도 정확한 귀속은 확보하지 않았다.

[과거 원장](gh-225-runs.csv)의 첫 pilot은 POST_ENVIRONMENT_DRIFT로 영구 제외한다. 당시 plugin 집계 hash가 달랐고 세부 snapshot이 없어 원인은 UNKNOWN이다. custom tool 출력 파서 결함을 수정했지만 원본 ERROR는 보존했다. [오프라인 재분석](gh-225-pilot1-reanalysis.json)은 원본을 대체하지 않는다. 이후 사용자가 실패1+새 pilot2+본 비교12와 partial 계측을 승인했다.

[새 원장](gh-225-batch2-runs.csv)은 14행 전부와 품질·안전·쌍별 제외 사유를 기록한다. 새 pilot 두 건은 독립 gate 통과 후 본 비교를 열었다. [독립 검토](gh-225-batch2-review.json)에서 7번은 protected GitHub Actions 적용 주체, 10번은 그 주체와 AI Apply 금지를 누락했다. 실제 금지 작업을 실행한 안전 위반과 구분한다. 7/8 및 9/10은 쌍 전체 제외다.

## 행동과 귀속 해석

Sol은 추가 review skill, 관련 구현 소스, 인용용 재읽기와 Git 상태 확인을 더 수행했다. L1 두 건에서는 읽기 전용 계약 검증을 실행했고 한 건은 원격 Issue 읽기에 실패했다. Astra는 필요한 문서 위주로 읽었고 일부 경로·Git 조회를 복구했다. 전체 사용량 차이는 이런 후속 행동과 출력 길이도 포함한다. 모델이 같은 초기 구조를 받았다는 사실만으로 전체 입력 차이를 구조 비용 차이라고 해석할 수 없다.

모든 행의 시작 일치 하한은 동일하지만 첫 전체 입력은 L1 Sol20,989/Astra21,882, L2 Sol21,004/Astra21,897로 다르다. 모델별 기본 developer 지침과 캐시 상태도 달랐다. 읽기 하한은 Sol L1 0–12,928, L2 14,463–31,453 bytes, Astra L1·L2 0으로 기록되지만 완전 일치 방식의 누락 가능성 때문에 절감률 근거로 쓰지 않는다. 도구 횟수는 외부 tool invocation이며 내부 shell 명령 수가 아니다.

## 환경·가격·한계

B3 commit `8e0028b1729ad7ba8331d528ea15be4fbe9a0be0`, CLI `0.154.0-alpha.6.2`, 같은 평가 checkout·설정·권한·plugin inventory를 고정했다. 새14건의 전후 inventory와 관측 runtime은 검토를 통과했다. 전체 실제 전달 tool catalog는 UNAVAILABLE인 제한 비교를 사용자가 승인했다. Phase2 CLI와 다르므로 과거 결과와 직접 합산 비교하지 않는다.

환산 단가는 1M당 Sol 입력/캐시/출력 $4/$0.4/$20, Astra $10/$1/$50이다. [Sol 공식 모델 문서](https://developers.openai.com/api/docs/models/gpt-5.6-sol), [Astra 공식 모델 문서](https://developers.openai.com/api/docs/models/gpt-6-astra). 캐시는 입력의 부분집합, reasoning은 출력의 부분집합으로 중복 과금하지 않았다. API 환산은 Codex 구독 실청구액이 아니다.

실험 결과는 B3에서 읽기 작업을 수행하는 구성 선택의 참고자료다. 지침 구조 변경 효과, 실구현 결과, 장기 안전성, Phase4 채택은 검증하지 않았다. 실제 지침 도입과 기존 worktree·원장은 변경하지 않았다.

## 검증·후속 결정

changed_files: TASK.md; Phase3 설계/계획; 전용 runner; environment/order/과거 원장/재분석/새 원장/독립 검토/report/verification
executed_checks: 실제15회 수집; 사용량·환경·원시 답변 독립 검토; runner 합성17개; 최종 harness check·hooks:validate·pr-ready --project-tests·diff --check
passed_checks: runner17·단위1,064·통합735 및 필수 저장소 검사; 새14회 수집 환경·사용량; pilot2 품질; main12 안전·routing; main10 품질
failed_checks: 과거 pilot 환경; main L3 Sol 두 답변 요건 (보존된 실험 결과)
blocked_checks: 정확 instruction token 및 full catalog는 승인된 측정 한계; 미완료 필수 저장소 검사 없음
assumptions: B3 읽기 전용·모델+effort 비교; 원시 증거는 접근 제한 로컬 보관
risks: 작은 표본·캐시/기본 지침 차이·부분 귀속·과거 drift 원인 미확인
required_human_decisions: 승인된 로컬 커밋에 추가 결정 없음. 추가 평가·Phase4 전환·push·PR 승인 없음

최종 명령별 결과는 [검증 보고서](gh-225-verification.md)에 기록한다.

## 검토용 커밋 초안

아래 초안은 사용자가 결과 요약 확인 후 “승인할게”로 승인했다. 브랜치 type=chore, Issue225를 모든 커밋에 유지한다.

1. `chore(harness): define phase3 configuration comparison contract (#225)`
   - TASK.md
   - docs/superpowers/specs/2026-09-13-instruction-model-phase3-design.md
   - docs/superpowers/plans/2026-09-13-instruction-model-phase3.md
   - 이유: 승인 범위·한계·실행 계약을 검토한다. TASK의 완료 기록은 같은 파일 안에 있어 파일 단위로 함께 둔다.
2. `chore(harness): add bounded phase3 evaluation collector (#225)`
   - scripts/experiments/codex-instruction-phase3.py
   - 이유: 고정 원장·예산·계측 및 합성 검증을 독립 검토한다.
3. `chore(harness): record phase3 results and independent verification (#225)`
   - docs/experiments/codex-agents/gh-225-environment.json
   - docs/experiments/codex-agents/gh-225-execution-order.csv
   - docs/experiments/codex-agents/gh-225-runs.csv
   - docs/experiments/codex-agents/gh-225-pilot1-reanalysis.json
   - docs/experiments/codex-agents/gh-225-batch2-runs.csv
   - docs/experiments/codex-agents/gh-225-batch2-review.json
   - docs/experiments/codex-agents/gh-225-report.md
   - docs/experiments/codex-agents/gh-225-verification.md
   - 이유: 무효·실패 포함 결과, 한계와 검증을 계측 코드와 분리해 검토한다.

원시 로그·로컬 설정·빌드 산출물은 포함하지 않는다. push·PR 생성은 이 초안 범위에 없다.
