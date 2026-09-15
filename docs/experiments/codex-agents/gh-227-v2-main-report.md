# GH-227 본 비교 진행 및 중단 기록

최신 상태: **전체 상한 조정 답변 대기**. 개별 세션 상한을 30분으로 확대해 B After를 재개했고 B Before도 완료했다. F/B 두 과제의 두 군, 총4개 산출물 수집과 독립 oracle 검증을 마쳤다. 최종 독립 모델 검토와 R/A/Q/X는 미실행이다.

| 과제·군 | 최종 수집 | 입력 | 출력 | 모델 실행 시간 | 독립 oracle |
| --- | --- | ---: | ---: | ---: | --- |
| F Before | COLLECTED, 완료 판정 TASK_FAIL | 2,266,171 | 21,891 | 771.468초 | 7개 통과 |
| F After | COLLECTED, 예비 PASS | 2,723,377 | 20,214 | 749.559초 | 7개 통과 |
| B After | COLLECTED, 예비 PASS | 4,063,841 | 20,768 | 1,061.529초 | 단위9·DB3 통과 |
| B Before | COLLECTED, 예비 PASS | 16,237,771 | 27,626 | 1,428.824초 | 단위9·DB3 통과 |

파일럿과 기존 실패·검토를 포함한 누계는 입력 **27,282,114**, 출력 **115,942**, 실행 **4,822.456초**다. input 상한은 없고, output120,000 중 **4,058**, 전체9,000초 중 **4,177.544초**가 남았다. 같은 세션의 재개 누적을 중단 값과 이중 합산하지 않았다. 준비자·오케스트레이터·검증 준비 에이전트 비용은 이 측정 CLI 세션 원장에 포함되지 않는다.

이전 개별 시간 차단은 승인된 30분 상한 안에서 재개해 해소했다. 현재는 새 BUDGET_LIMIT 오류가 발생한 것이 아니다. 남은8개 과제 실행과2개 최종 검토를 완료하기에 출력잔여4,058이 부족해 다음 index5를 시작하기 전 상한 조정 답변을 기다린다. 제안은 출력240,000·전체18,000초이며 아직 승인으로 처리하지 않았다. 개별1,800초는 유지한다.

B Before는 수정 전 동시성 결함2개를 재현한 뒤 수정했다. 이후 서비스 규칙 검사와 포맷 검사가 각각 실패해 허용 파일 안에서 보완하고 재검증했다. 최종 전체 단위1,033·통합720, 정책·Spotless·Checkstyle·훅·diff 검사가 통과했다. 최종 제품만 별도 사본으로 옮긴 독립 oracle9+3개도 통과했다. `.git/FETCH_HEAD` 쓰기 경고와 모든 실패·재실행 비용을 보존한다.

F Before/After와 B After에는 중단·재개 이력이 있다. 최종 모델 품질 검토가 아직 없으며, 작업별 반복 실험도 없으므로 위 숫자만으로 지침의 일반적 절감률이나 모든 영역의 효율을 단정하지 않는다. B 두 군의 예비 품질 gate는 통과했지만 지침 효과와 모델 실행 편차를 분리한 인과 결론은 아니다.

```text
status: BLOCKED_PENDING_BUDGET_DECISION
issue_number: 227
task_id: GH-227-INSTRUCTION-ROLLOUT-PHASE4
design_id: HARNESS-DESIGN-GH-227-002
changed_files: TASK.md; 실행 계약; 본 보고서; private continuation-v4/review preparation/verification artifacts
executed_checks: continuation-v4 17 tests; B both 9 unit+3 DB oracle; canonical source/usage/hash audit; recorded mandatory checks
passed_checks: v4 independent preflight; B both oracle; B final mandatory model checks
failed_checks: original interruptions and corrected policy/format failures retained
blocked_checks: next index5+; R/A/Q/X; final independent model review
assumptions: input cap waived; per-session1800 approved; total output/time unchanged
risks: interrupted samples; single runs; remaining output cannot cover full plan; no final review
required_human_decisions: pending proposal output240k/total18000 seconds (not yet approved)
```

이하 이전 단계의 이력을 보존한다.

이전 상태: **BLOCKED — 버그 과제 After의 세션 900초 제한 도달**. 사용자가 입력 상한을 해제해 F After를 같은 세션에서 완료했고, B After를 실행했다. 이번 중단 원인은 입력 예산이 아니라 유지한 개별 시간 제한이다.

| 실행 | 수집 | 입력 | 출력 | 실행 시간 |
| --- | --- | ---: | ---: | ---: |
| F Before | 완료, 모델 BLOCKED/TASK_FAIL 보존 | 2,266,171 | 21,891 | 771.468초 |
| F After | 완료, 모델 PASS | 2,723,377 | 20,214 | 749.559초 |
| B After | TIME_LIMIT, 최종 보고 미완료 | 3,019,191 | 14,328 | 900.390초 |
| 파일럿 포함 누계 | 이전 실패와 재개 비용 포함 | **9,999,693** | **81,876** | **3,232.493초** |

입력 상한은 없다. 출력 잔여 38,124·전체 시간 잔여 5,767.507초지만 B After가 개별900초를 소진했다. 타이머 확인·프로세스 종료 지연0.390초도 실제 시간으로 포함했다. B Before 및 R/A/Q/X와 최종 독립 모델 검토는 미실행이다. 최종 품질 검토를 마친 비교 쌍이 없어 토큰 절감률 결론을 내리지 않는다.

B After는 지정 검사 이후 전체 단위1,034개·통합720개가 모두 통과했다. 전체 통합 실행이 약6분40초 걸렸으며 최종 보고 작성 전에 제한에 도달했다. 제출 제품만 별도 사본에 옮긴 독립 oracle도 단위9개·DB통합3개가 통과했다. 최종 모델 보고와 남은 필수 검사 완료는 확인되지 않았으므로 테스트 통과를 전체 과제 완료로 대체하지 않는다. 처음 생성한 테스트 보고서 초안과 원본 실패·usage·rollout은 유지했다.

이번 continuation v3는 독립 검사15개를 통과했고 입력 제한만 제거했다. 기존 시간·출력·안전·환경·범위·누적usage 검사를 유지했다. F After의 추가단위2개를 포함한 최종 전체 단위672개·통합503개가 통과했고, 제품은 oracle7개가 통과한 기존 사본과 같았다. 준비자 검증은 별도 비용이며 위 누계는 고정 CLI 평가 세션만 나타낸다.

현재 완료 조건을 막는 항목은 개별 시간 제한과 그에 따른 미완료 평가다. 재개하려면 시간 제한 또는 중단 표본 처리 계약을 조정해야 하며, 추가 실행이나 replacement를 자동으로 수행하지 않았다.

```text
status: BLOCKED
issue_number: 227
task_id: GH-227-INSTRUCTION-ROLLOUT-PHASE4
design_id: HARNESS-DESIGN-GH-227-002
changed_files: TASK.md; 실행 계약; 본 보고서; private continuation-v3/review preparation/verification artifacts
executed_checks: continuation-v3 15 tests; F final source/oracle hash; B 9 unit+3 DB oracle; recorded checks; cumulative accounting
passed_checks: continuation-v3; F final reported checks; B independent oracle and observed full suites
failed_checks: B After TIME_LIMIT; original interruption histories retained
blocked_checks: B final report and remaining required checks; remaining tasks; final model reviews
assumptions: input cap waived only; output/time caps retained
risks: interrupted/resumed samples; incomplete coverage; recorded-suite success is not final task PASS
required_human_decisions: per-session time limit or interrupted-sample handling before continuation
```

이하 이전 단계의 보고 및 중단 이력을 보존한다.

이전 중단 상태: **BLOCKED — 전체 입력 예산 도달**. 수집기를 수정·독립 검증하고 Before를 같은 UUID로 완료한 뒤 After까지 진행했다. After는 최종 보고 전에 예산으로 중단됐다. 완료된 과제 쌍은 0개이며 토큰 절감률을 산출하지 않는다. 아래 초기 중단과 재개 기록은 이력으로 보존한다.

| 항목 | Before | After |
| --- | --- | --- |
| 수집 상태 | COLLECTED, 중단 후 동일 세션 재개 | ERROR / BUDGET_LIMIT, 최종 turn 미완료 |
| 모델 최종 상태 | BLOCKED → 독립 gate에서 완료 판정 TASK_FAIL로 기록 | 최종 상태 미제출 |
| 입력 토큰 | 2,266,171 | 1,786,117 (중단 시점) |
| cached input | 2,187,776 | 1,723,264 |
| 출력 토큰 | 21,891 | 10,472 (중단 시점) |
| 세션 실행 시간 | 771.468초, 중단 전후 합 | 482.790초, 예산 중단까지 |
| 전체 단위 XML | 668개 통과 | 670개 통과 |
| 전체 통합 XML | 503개 통과 | 503개 통과 |
| 같은 독립 oracle | 7개 통과 | 7개 통과 |
| 최종 과제 보고서 | 있음 | 미작성 |

파일럿·실패·검토 및 이번 두 실행을 포함한 측정 모델 누계는 **입력 6,043,242·출력 57,806·2,065.334초**다. 동일 Before 재개 누적을 중단 소비량과 이중 합산하지 않았다. 입력 상한 6,000,000보다 43,242가 많다. 마지막 응답의 입력 증가량 56,222가 한 번에 보고되어 초과를 처음 관측한 시점에 프로세스를 종료했다. 이 수집기는 응답 도중 서버 사용량을 미리 제한하는 hard cap이 아니며, 초과분도 실제 사용량으로 남긴다.

B/R/A/Q/X 10개 실행과 예정된 최종 독립 모델 검토 2회는 미실행이다. 수집기·fixture·검증 도구 준비와 이 오케스트레이터 및 준비 검증 에이전트 사용량은 위 측정 세션 표에 포함되지 않는다. 따라서 위 수치를 프로젝트 작업 전체 토큰 또는 실제 금액으로 표현하지 않는다.

Before는 중단·재개됐고 After는 예산에 의해 잘렸으므로 두 숫자의 단순 차이는 지침 최적화 효과를 의미하지 않는다. 품질 검토까지 마친 완전한 세션 쌍이 없고, 일반 기능 외 다른 영역의 모델 실험 근거도 아직 없다. C1 운영 적용이나 개선 확정 결론을 내리지 않는다.

최신 실행기는 exact Sites hash 복원과 실제 high override에 영향 없는 정확한 두 기본 설정 hash만 예외 처리한다. 독립 테스트 12개와 실행 전 환경 검증이 통과했고 실제 재개에서 runtime·누적 usage·범위 검사가 동작했다. 원본과 v1 후보는 보존했다. 추가 모델 실행, replacement, 예산 확대, 커밋·push·운영 지침 반영은 하지 않았다.

```text
status: BLOCKED
issue_number: 227
task_id: GH-227-INSTRUCTION-ROLLOUT-PHASE4
design_id: HARNESS-DESIGN-GH-227-002
changed_files: TASK.md; 본 보고서; 실행 계약; 실행 계획; private continuation and verifier artifacts
executed_checks: continuation 12 tests; independent preflight; F oracle both arms; recorded model checks; output/usage/hash audit
passed_checks: continuation checks; both 7-case oracles; recorded full unit/integration checks; original evidence preservation
failed_checks: After BUDGET_LIMIT; original environment interruption; archived oracle invocation compilation failures
blocked_checks: After final report/turn; B/R/A/Q/X; final independent model reviews; token reduction conclusion
assumptions: measured-session budget excludes preparation/orchestration
risks: interrupted/censored samples; response-granularity budget overshoot; before git fetch warning; incomplete broad coverage
required_human_decisions: additional evaluation budget and sample handling before further model execution
```

이하 항목은 당시 시점의 중단 및 복구 이력이다.

## 원인과 확인 근거

`main-001`의 고정 수집기는 이미 사용자 예외를 받은 Sites 0.1.62 캐시 4개 파일의 삭제만 허용했다. 실행 중 같은 파일이 다시 생겼으며 4개 SHA-256은 삭제 전 값과 모두 일치했다. 프로젝트 trust 등록을 제외한 정규화 설정 hash도 같았다. 따라서 새로운 내용 변경이 아니라 알려진 캐시 복원을 예외로 처리하지 못한 수집기 결함이다. 복원을 수행한 주체는 확인하지 못했다.

원래 manifest, runner, ERROR 결과, stdout 및 rollout을 보존했다. `1-interruption-audit.json`에 파일별 비교와 XML 집계를 기록했다. 실행 중단을 사후 성공으로 바꾸지 않는다. 예외 보완은 정확한 4개 경로의 기존 hash와 부재 사이 전환에 한정해야 하며, 다른 파일 추가나 내용 변경까지 무시하면 안 된다.

## 중단 시점의 실제 결과

- 모델 표적 단위 검사와 `harness test-run`은 exit 0이었다.
- 최종 XML은 단위 668개, 통합 503개이며 실패·오류·skip은 0이었다.
- `harness check`, `harness pr-ready --project-tests`, `npm run hooks:validate` 명령은 exit 0이었다. pr-ready의 Gradle 검사는 UP-TO-DATE 재사용이었다.
- pr-ready 내부 fetch는 sandbox의 `.git/FETCH_HEAD` 쓰기 제한으로 실패했다. 도구 자체는 기존 로컬 기준의 조상 검사와 검증을 수행하고 exit 0을 반환했다. 실제 원격 최신화 성공으로 해석하지 않는다.
- 변경은 허용된 신규 제품 파일, 신규 단위 테스트, 기본 테스트 보고서 3개에 한정됐다.
- XML 집계의 첫 shell 명령은 실패했으며 후속 집계로 수정됐다. 실패 이력은 원본 로그에 보존했다.
- 최종 과제 보고서, 모델 turn 완료, 독립 oracle 실행, 독립 품질 검토는 미완료다. 테스트 성공만으로 과제 PASS를 선언하지 않는다.

## 사용량

| 구분 | 입력 | 출력 | 모델 세션 시간 |
| --- | ---: | ---: | ---: |
| 이번 중단 실행 | 1,736,073 | 12,474 | 580.427초 |
| 이전 파일럿 포함 누계 | 3,727,027 | 37,917 | 1,391.503초 |
| 승인 상한의 잔여 | 2,272,973 | 82,083 | 7,608.497초 |

이번 입력 중 cached input은 1,668,864다. 입력 사용량은 누적 token_count 기준이며 고유 문서 크기나 금전 비용이 아니다. 수집기·fixture 준비 및 오케스트레이터 사용량은 이 표에 포함되지 않는다. 12개 과제 실행과 검토를 잔여 상한 내 완료할 수 있다고 보장하지 않는다.

## 다음 실행 조건

동일 hash 캐시 복원을 다루는 수집기 수정과 독립 검증이 필요하다. 중단된 세션을 성공 표본으로 재분류하거나 자동 replacement하지 않는다. 재개한다면 원래 오류와 사용량을 유지하고 동일 세션 이어가기 또는 별도 재실행의 비용·시간·표본 처리 계약을 먼저 확정한다. 그 전에는 후속 모델 실행을 하지 않는다.

```text
status: BLOCKED
issue_number: 227
task_id: GH-227-INSTRUCTION-ROLLOUT-PHASE4
design_id: HARNESS-DESIGN-GH-227-002
changed_files: TASK.md; 본 보고서; 실행 계약; 실행 계획; private evaluation artifacts
executed_checks: XML 집계; 캐시 SHA 비교; 정규화 설정 비교; 허용 변경 경로 비교
passed_checks: 캐시 4개 기존 hash 일치; 정규화 설정 동일; 허용 범위 밖 변경 0
failed_checks: frozen runner LIVE_ENVIRONMENT_DRIFT / POST_ENVIRONMENT_DRIFT
blocked_checks: 최종 세션 완료; 독립 oracle; After와 나머지 과제; 독립 결과 검토
assumptions: 준비 비용은 측정 모델 세션 예산과 별도
risks: 미완료 표본; 잔여 입력 예산; git fetch 경고; 원격 도구 부작용 관측 한계
required_human_decisions: 수정 검증 후 중단 세션 재개 방식과 예산 내 표본 처리
```

## 중단 세션 재개 승인

사용자의 “수정하고 이어서 진행해”에 따라 동일 hash 캐시 복원의 예외를 수정하고 독립 검증 후 기존 UUID를 이어간다. 원래 오류와 frozen runner/manifest는 보존하고 새 실행 증거를 추가한다. 중단 전후 누적 토큰은 한 번만 집계하며 실행 구간 시간은 합산해 기존 세션 900초와 전체 9,000초 상한을 유지한다. 수리 대기시간은 모델 실행시간에 넣지 않는다. 첫 Before는 중단·재개된 표본임을 명시하고 중단 없는 After와 동등한 시간 비교로 해석하지 않는다. 입력 6M·출력 120k 상한과 추가 replacement 0을 유지한다.

## 재개 준비에서 확인한 계측·검증 보완

재개 전 설정 차이를 조사해 현재 정규화 설정의 `model_reasoning_effort`만 medium에서 low로 되돌린 메모리 사본의 hash가 원래 기록과 정확히 같음을 확인했다. 실험 CLI는 high를 명시하고 모든 turn의 실제 high를 검사한다. 전역 설정은 수정하지 않으며, 정확히 확인된 두 정규화 hash의 차이만 별도 continuation 수집기에 반영한다.

첫 독립 F oracle는 계약에 없는 생성자 인자 순서를 가정해 컴파일 실패했다. 생성자 주입 보정 후에는 역시 계약에 없는 boolean record 접근자 이름 가정으로 컴파일 실패했다. 두 실패와 원본 oracle를 보존하고, 동일 타입에 따른 생성자 주입과 URL·Instant·boolean record 접근만 보정한 별도 v2 adapter를 만들었다. 사례와 기대값은 유지하며 Before/After 모두 같은 adapter를 사용한다. 이 검증 도구 수정은 모델에 피드백하지 않는다. 중단 시점 제품에 대한 보정 oracle는 7개가 통과했고, 최종 재개 제품과 hash가 같은지 다시 확인해야 한다.

## F Before 재개 완료 및 After 진행

수집기 v2의 독립 검증 12개가 통과했고 동일 UUID 재개가 `COLLECTED`로 종료됐다. 누적 입력 2,266,171·출력 21,891·실행 시간 771.468초는 중단 전 실행을 포함한 값이다. 중단 시 소비량을 이 값에 다시 더하지 않는다. 파일럿 포함 총 입력 4,257,125·출력 47,334·1,582.544초이며 입력 잔여는 1,742,875다.

모델은 최종 상태를 `BLOCKED`로 보고했다. 독립 판정에서 실제 `pr-ready`는 exit 0이고, HEAD·origin/main·고정 로컬 bare 원본 main이 같은 seed임을 확인했다. fetch 쓰기 제한은 실제 경고로 유지하되, 고정 입력 평가의 필수 ancestry 검사 실패는 아니므로 모델의 완료 판정 실패를 `TASK_FAIL`로 기록하고 다음 After를 진행한다. 지침 구조가 이 판단을 유발했다고 인과를 단정하지 않는다. 제품은 독립 oracle 7개가 통과한 사본과 해시가 같고 반환 record는 URL·Instant·boolean만 포함한다. 최종 배치 품질 검토는 별도다.

재개 당시 patch 형식 오류는 모델이 직접 수정했다. 실패 명령과 비용은 기록에 남는다. 최종 모델 보고서·기본 테스트 보고서와 제품·단위 테스트의 4개 허용 파일만 변경됐다. 실제 원격 최신화 성공이나 원래 오류의 소멸을 주장하지 않는다.

## 이번 문서 정리 검증

원래 checkout에서 `./harness check`, `npm run hooks:validate`, `git diff --check`가 exit 0이었다. 새 보고서 등 변경 문서는 별도 공백 검사로 확인했고, 기존 지침·이전 증거 12개와 continuation의 원본 bindings 해시가 유지됐다. 원래 checkout의 `./harness pr-ready --project-tests`는 이번 문서 정리에서 재실행하지 않았다. 실제 기능 구현·Gradle 검증은 평가 사본에서만 수행했고 운영 코드에 반영하지 않았으므로 원래 checkout의 프로젝트 전체 검증이나 PR 준비 완료로 표현하지 않는다.

## 입력 상한 해제 및 재개 승인

사용자의 “입력 예산 일단 무시하고 이어서 진행해줘”에 따라 입력 토큰 상한만 해제한다. 입력 사용량은 계속 집계하며 원래 예산 중단 이력은 보존한다. 출력120,000·전체 모델실행9,000초·과제별900초 상한은 유지한다. After는 같은 UUID를 재개하고 중단전482.790초와 누적사용량을 포함한다. 이후 원래 과제 순서와 검증 절차를 따른다.

## 입력 상한 해제 후 F 두 군 수집 완료

After를 같은 UUID로 재개해 COLLECTED로 종료했다. 입력2,723,377·출력20,214·749.559초는 중단 전후 합계다. 모델은 PASS를 제출했고 최종 단위672·통합503개가 통과했다. 추가단위2개를작성했으나제품hash는기존oracle7PASS검증사본과같다. F 두 군의전체수집은완료됐지만Before TASK_FAIL판정과양쪽중단이력을유지하며독립최종품질검토는미완료다. 단순사용량차이를최적화효과로해석하지않는다. 버그과제After(index3)를다음으로시작했다.

## 개별 세션 30분 상한 승인

사용자가 개별세션상한30분으로확대후계속진행하는제안에 “어 늘려서 이어가줘”로동의했다. input상한없음은유지하고개별실행1,800초로변경한다. 출력120,000·전체모델실행9,000초는유지한다. B After는같은UUID를재개하며기존900.390초와누적usage를포함하고원래TIME_LIMIT증거는보존한다. 이후기존과제순서를따른다.

## 30분 상한 적용 후 B After 재개 완료

Continuation-v4의독립검사17개를통과한뒤 B After를같은UUID로이어갔다. 최종COLLECTED/모델PASS이며입력4,063,841·출력20,768·1,061.529초는기존중단전실행을포함한다. 제품hash는독립oracle9unit+3DB통과사본과같고최종보고및필수검사종료를확인했다. 원래TIME_LIMIT는보존하며최종배치검토전예비gatePASS로다음B Before(index4)를시작했다.

## 전체 출력·시간 상한 확대 승인

사용자가 출력24만토큰·전체실행300분확대제안에 “좋아”로동의했다. output240,000·total18,000초로확대하고input무상한·개별1,800초는유지한다. 기존사용량과모든중단이력을그대로포함하며다음index5리팩터링Before부터기존순서로진행한다.

## R 두 군 수집과 독립 회귀 확인

R Before는입력2,049,525·출력15,891·685.413초, R After는입력4,027,423·출력15,212·666.544초로COLLECTED/모델PASS다. 두군모두허용된5개제품파일에서기존retryfactory호출로정리했고테스트scope파일은hash동일하다. 별도사본의기존회귀35개가각각통과했다. 최종품질검토전예비gate이며실험1회차의입력증감을일반효과로해석하지않는다. 다음A After(index7)를시작했다.

## A After 수집 완료 및 Before 실행

A After(index 7)는 COLLECTED/모델 PASS로 종료했다. 입력 513,523, 캐시 입력 466,560, 출력 16,639, 실행 시간 505.221초다. 허용된 설계·보고 문서 두 개만 변경했으며, 대안·책임 경계·트랜잭션·동시성·복구·미확정값을 다뤘다. 예비 gate는 PASS이며 최종 독립 품질 검토는 아직 남아 있다. A Before(index 8)를 시작했다.

## A·Q 두 군 수집 완료, X 진행

A Before는 입력 455,936·캐시 입력 401,536·출력 14,623·364.759초로 수집됐다. A 양쪽 예비 gate는 PASS다. 동일한 기술 선택을 제시했더라도 최종 독립 리뷰 전 품질 동등성을 확정하지 않는다.

Q Before는 입력 693,669·출력 8,912·361.280초, Q After는 입력 582,059·출력 11,795·403.896초다. 두 군 모두 같은 UUID에서 두 차례 고정 답변을 제공했다. 질문 의미·응답 key·정확한 답변·turn을 개별 증거로 보존했다. 두 군 모두 모델은 PASS를 보고했지만 실패/중간 상태 원자성을 질문하거나 명시적 미확정으로 남기지 않아 예비 TASK_FAIL로 기록했다. Q5 응답은 제공하지 않았다. After는 기존 사용자 이관 조건을 질문하여 Q6 답변을 받았고, Before는 운영 이관 정책을 미확정으로 남겼다. 최종 리뷰가 대화·요구사항을 독립적으로 대조해야 한다.

Index 10까지 파일럿 이월분을 포함한 예산 집계는 입력 35,604,249·출력 199,014·7,809.570초다. 입력은 계속 측정하되 상한이 없고 출력 240,000·전체 18,000초·개별 1,800초를 유지한다. X After(index 11)를 시작했다. 준비·오케스트레이션 도구 비용은 이 CLI 집계에 포함되지 않는다. 최종 독립 모델 리뷰 2회는 아직 실행하지 않았다.

## X After 시간 상한 중단 — 현재 최신 상태

상태는 `BLOCKED`이며 전체 실험은 완료되지 않았다. Index 11은 1,800.175초에 `TIME_LIMIT`로 중단됐다. 같은 UUID는 `01a0a003-0ab7-72c1-9edd-f31b2c924205`다. 입력 4,192,243·캐시 입력 4,088,704·출력 23,040을 사용했다. 최종 요구 보고서 `evaluation-output/X/report.md`는 아직 없고 기존 하네스 보고서는 골격이다. 허용된 Java 3개와 하네스 보고서 외 변경은 승인된 build/cache 산출물뿐이며 범위 이탈은 발견되지 않았다. 원본 ERROR 결과를 성공으로 대체하지 않았다.

지정 단위·통합 테스트는 통과했다. 최초 전체 통합에서는 기존 InboxCommandConcurrencyIntegrationTest 4건이 PostgreSQL 연결 시간 초과로 실패했다. 같은 4건을 단독 재실행해 통과한 뒤 전체 pr-ready를 재실행하여 exit 0을 확인했다. 최초 실패와 재실행 비용은 그대로 포함한다. 단독 재실행 성공만으로 원인이나 비회귀를 확정하지 않는다. `.git/FETCH_HEAD` 경고는 계속 기록한다.

전체 사용량은 파일럿 이월분 포함 입력 39,796,492·출력 222,054·9,609.744초다. 승인된 출력 240,000 중 17,946, 전체 18,000초 중 8,390.256초가 남았다. Input 상한은 없다. Index 12와 독립 리뷰 13·14는 시작하지 않았다.

현재 제출제품에 별도 고정 oracle를 실행했지만 단위19·통합4가 실패했다. 다수는 완료 로그 수집 조건, 하나는 suppressed exception 기대와 관련되어 요구사항에 없는 구현 형식 강제 여부를 독립 검토 중이다. 검증 원본과 제품을 수정하지 않았으며 제품 결함 수로 확정하지 않는다.

재개 제안(미승인·미적용): 개별 논리 세션 상한을 2,700초(45분), 전체 출력 상한을 320,000으로 변경한다. 전체 시간 18,000초, 입력 상한 없음, 모델/순서/과제 수는 유지한다. Index 11은 기존 UUID에서 남은 보고를 완료하고 중단 전 사용량을 포함한다. 이후 index 12와 리뷰 13·14를 수행한다. 원본 runner/manifest를 변경하지 않고 승인 후 별도 continuation과 독립 검증을 사용한다. 새로운 과제나 실험 군을 추가하지 않는다.

### X oracle 독립 진단

독립 진단에서 계약 밖 과잉 제약 세 가지를 확인했다. 원본 oracle는 완료 메시지 `http_request_completed`, SLF4J key-value 저장 방식, logging 예외의 suppressed 추가를 강제했다. 실행 계약은 이 구체 형식을 지정하지 않는다. 실제 완료 로그는 XML에 존재하며, 단위18·통합4의 수집 실패와 단위1의 suppressed 실패만으로 제품 결함을 확정할 수 없다. 실패 이후 assertions는 실행되지 않았으므로 제품 PASS도 아니다. 원본을 보존하고 같은 의미 조건을 양쪽 X에 적용하는 별도 검증 adapter를 준비 중이다. 이 작업은 추가 모델 실험 없이 수행한다.

### X oracle 보완 v2 실제 검증 완료

v1 실제 실행은 단위18/19·통합4/4 실패였다. 추가 진단에서 원본 및 v1이 `HTTP_REQUEST` logger만 관측하지만 제출제품은 클래스 logger를 사용하는 제4 과잉 제약을 발견했다. 최초 독립 검토에서도 이 관측 경로를 놓쳤으므로 검토 누락으로 기록한다. 제품 결함으로 확정하지 않는다. v2는 두 logger에 같은 appender를 부착·해제하고 이벤트를 제거하거나 중복 제거하지 않는다. v2 초안의 실패 주입 도달 assertion 누락도 독립 검토에서 발견하여 실제 실행 전에 보완했다. v1 및 실제 실패 증거는 그대로 남았다.

최종 v2 정적 독립 검토 후 별도 사본에서 실제 실행하여 단위19/19, 보안 통합4/4, 실패·오류·skip 0을 확인했다. 제출제품 hash `b0159dd376b212feca7cfb71ec87eec438b392224e06670947aac7b08b176ede`는 변하지 않았다. v2는 structured 표현과 현재 key=value 표현을 판독하지만 임의의 모든 로그 인코딩을 지원한다고 보장하지 않는다. 양쪽 X에 동일한 v2를 사용하며 원본과 v1 결과도 공개 평가 해석에 함께 기록해야 한다. 제품 전체 품질 PASS, 최종 보고 완료 또는 TIME_LIMIT 해소를 의미하지 않는다.

- v2 manifest SHA-256: `050fdce6c5882e530f8777a937eddb0e3b4e2f53e6caed0b9bd02164c5ab9140`
- v2 driver SHA-256: `e5dce42934ba7fafb046f5077335b6002fc6fa55516af7b1fc21e38ed59b8b53`
- private evidence: `main-001/verification/neutral-11-x-adapter-v2/summary.json`

### 현재 보고 계약

- status: BLOCKED
- issue_number: 227
- task_id: GH-227-INSTRUCTION-ROLLOUT-PHASE4
- design_id: N/A (인프라 설계/구현 아님)
- changed_files: TASK.md, 이 보고서, 비공개 continuation gate/질문응답 증거 및 review/oracle 준비 파일. 기존 운영 지침과 과거 증거12개 hash는 유지됐다.
- executed_checks / passed_checks: 원래 checkout의 `./harness check`, `npm run hooks:validate`, `git diff --check`, 이번 변경 문서 공백 검사; X v2 단위19/통합4 실제 통과.
- failed_checks: X 최초 전체 통합4건 실패 및 원본/v1 oracle 실패 이력 보존. 전체 통합 재시도와 v2는 통과. Q 양쪽 예비 TASK_FAIL, 최종 독립 판정 대기.
- blocked_checks: 개별 시간 상한으로 index11 최종 보고 미완료. index12 및 독립 리뷰13/14 미실행. 원래 checkout `./harness pr-ready --project-tests`는 문서 정리에서 재실행하지 않았으며 PR 준비 완료를 주장하지 않는다.
- assumptions: 고정 Sol/high 및 동일 실행 계약 유지. 준비/오케스트레이션·독립 결정적 검증은 측정 CLI 모델 사용량에서 제외.
- risks: 과제당 군별1회, 중단/재시도 및 도중 예산 변경, 검증기 보완, 불완전 최종 품질 판정 때문에 보편적인 최적화 효과를 확정할 수 없음.
- required_human_decisions: 개별2,700초·출력320,000으로 재개 승인 여부. 총18,000초와 입력 무상한 유지, 기존 UUID 재개 및 이력 보존. 이 제안은 아직 미적용.

## 개별 45분·전체 출력 32만 승인 및 재개

사용자의 “어 늘려서 진행”에 따라 개별 논리 세션 상한을 2,700초, 전체 출력 상한을 320,000으로 변경한다. 입력 상한 없음·전체 모델 실행 18,000초·고정 Sol/high·실행 순서·과제 수는 유지한다. Index 11은 기존 UUID를 재개하고 기존 1,800.175초와 누적 토큰을 포함한다. 원본 TIME_LIMIT, 최초 회귀 실패 및 oracle 보완 이력을 보존하며 동일 누적 사용량을 이중 합산하지 않는다. Index 12와 독립 리뷰 13·14까지 진행한다. 사전 작성한 검증기 결과나 품질 피드백을 측정 실행자에게 전달하지 않는다.

## X After 재개 완료, 마지막 Before 시작

Continuation-v6 자체/독립 검증20개를 통과한 뒤 index11을 같은 UUID로 재개했다. 최종 COLLECTED/모델PASS이며 입력4,884,762·출력30,389·2,014.605초는 중단 전후 전체다. 기존 출력23,040 등을 다시 더하지 않는다. 최종 제품 hash는 v2 oracle 단위19·통합4 통과 사본과 같다. 요구 보고서와 하네스 보고서, 허용 Java3개만 변경했다. TIME_LIMIT·최초 전체회귀 연결 실패·검증기 보완 기록은 그대로 보존한다. 최종 독립 모델 품질 검토 전 예비 gate PASS로 index12 X Before를 시작했다.

## X Before 전송 중단 및 동일 세션 재개 준비

Index12가 지정 단위 테스트를 실제 통과한 뒤 통합 검사 단계에서 `TRANSPORT_NOTICE`로 중단됐다. CLI가 계정 사용 한도 오류를 반환했다. 입력672,001·출력12,904·351.378초 및 UUID `01a0a02f-a653-7b83-b64a-f4f6929dfcd2`를 보존한다. 수집기의 추가 환경/범위 오류는 없었다. 이어서 호출한 앱의 읽기 전용 한도 조회는 ordinaryUsageAllowed=true, primary usedPercent=0을 반환해 CLI 오류와 상충했다. 두 응답만으로 계정·인증 변경이나 오류 해소를 확정하지 않는다. 계정 식별자·인증정보를 보관하거나 변경하지 않았다.

사용자의 “이어서 진행해줘”와 현재 가용성 응답을 근거로 같은 UUID의 1회 재개를 준비한다. 모델/개별2,700초/출력320,000/전체18,000초/입력 무상한은 변경하지 않는다. 기존351.378초와 누적사용량을 포함하고 원본 오류·전송 guard를 보존한다. 동일 오류가 재발하면 후속 실행을 멈춘다. 충전·reset credit 사용·계정 전환은 하지 않는다.

## 12개 과제 수집 완료 및 독립 리뷰 실행

Index12가 같은 UUID 재개 후 COLLECTED/모델PASS로 종료했다. 입력11,867,053·출력36,291·1,694.032초는 한도 오류 전후 합계다. 최종 코드로 단위1,046·통합723개가 실제 통과했고 필수 저장소 검사를 완료했다. 원래 usage-limit 전송 오류는 보존한다.

두 X에 최종 adapter v3를 동일하게 실행하여 각각 단위19·보안 통합4개를 실패/오류/skip 없이 통과했다. v3는 v2에 정확한 선택적 `request_completed ` 메시지 접두사 해석만 추가했고 독립 정적 검토를 통과했다. 이전 oracle 실패와 보완 이력은 유지한다.

12개 과제와 파일럿 이월 사용량은 입력52,356,064·출력265,694·11,518.206초다. 최종 독립 리뷰 직전 잔여 예산은 출력54,306·전체6,481.794초다. Review13은 F/B/R6개, Review14는 A/Q/X6개를 읽기 전용으로 평가한다. 두 리뷰용 자료는 후보명/토큰을 제외하고 actualdiff·요청·품질계약·검증 증거를 담았다. Q 대화의 환경 wrapper 각1개는 제외했지만 실제 사용자 메시지 각3개와 assistant메시지는 보존했다. 제외 source line/hash/type은 준비자용으로만 남겼다. 원본 대화는 수정하지 않았다.

Reviewer13/14 사본은 각각114/81파일이며 snapshot 및 현재 환경 동일성을 확인한 뒤 index13을 시작했다. 이 단계의 품질 판정 전 예비 gate는 F Before와 Q 양쪽 TASK_FAIL, 나머지PASS다. 최종 판정이 예비 결과와 다르면 근거를 구분해 기록한다.

## 독립 리뷰13 수집기 오탐 중단 및 수정 준비

Review13이 읽기 전용 자료의 도구 사용 기록을 jq/rg로 검색하던 중 `NESTED_EXTERNAL_TOOL_OBSERVED`로 중단됐다. 입력2,796,955·출력8,373·276.197초, UUID `01a0a051-9732-72c3-aab9-bea6a16554a0`, 변경 파일0을 보존한다. 같은 오탐이 capture_error에도 기록됐다.

독립 검증자가 전체 response_item의 실제호출32개를 확인하여 모두 exec→로컬 exec_command 읽기 명령임을 확인했다. 탐지 정규식의 `tools\..*spawn_agent`가 exec_command 인자의 검색 문자열까지 탐욕적으로 연결했다. 외부 도구나 추가 에이전트 실제 호출 증거는 없었다. 도구 식별자 부분을 `[A-Za-z0-9_]*`로 한정하는 최소 수정을 준비하며 실제 mcp/web/agent tool 차단은 유지한다. 원본 ERROR를 덮어쓰지 않고 같은 reviewer UUID를 재개한다. 예산·모델·검토 건수와 읽기 전용 범위는 유지한다.

## v2 측정 최종 마감

12개 과제와 독립 리뷰 2개 수집을 완료했다. F/B/R/A/X 양쪽 PASS, Q 양쪽 FAIL이다. 최종 수치·품질·예산·한계는 `docs/experiments/codex-agents/gh-227-v2-results.md`와 동명 CSV를 따른다. 앞선 진행 상태와 실패 기록은 당시 이력으로 보존한다. 운영 적용·커밋·push·PR은 미실행이다.
