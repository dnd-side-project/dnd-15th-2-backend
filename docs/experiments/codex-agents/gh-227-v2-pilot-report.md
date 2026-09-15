# GH-227 v2 파일럿 결과

- 실행일: 2026-09-14
- 계약: GH-227-EVAL-V2-001 / HARNESS-DESIGN-GH-227-002
- 판정: **BLOCKED — 실행 중 환경 변경으로 본 비교 진행 불가**

## 실제 실행 결과

Before 소형 파일럿의 첫 turn을 시작했으나 30.30초에 `LIVE_ENVIRONMENT_DRIFT`로 중단했다. 종료 후에도 `POST_ENVIRONMENT_DRIFT`를 확인했다. 요구사항 분석 품질 실패나 지침 최적화 실패로 판정한 것이 아니다.

| 항목 | 관측값 |
| --- | ---: |
| 모델 / effort | gpt-5.6-sol / high |
| 신규 평가 세션 | 1 |
| 완료한 turn | 0 |
| 입력 토큰(캐시 포함) | 68,018 |
| 캐시 입력 | 41,600 |
| 비캐시 입력 | 26,418 |
| 출력 토큰 | 843 |
| 그중 reasoning 출력 | 214 |
| 전체 입력+출력 | 68,861 |
| 실행기 경과 시간 | 30.30초 |

reasoning은 출력의 부분집합이므로 더하지 않는다. 값은 중단 후 보존된 rollout의 최신 누적 usage이며 미기록 서버 측 처리까지 완전하다고 주장하지 않는다. 준비 에이전트와 오프라인 도구 비용은 이 평가 세션 수치에 포함하지 않는다.

동일 세션 후속 답변, After 파일럿, 독립 모델 gate 검토, 본6과제12회는 미실행이다. 자동 재실행·replacement·추가 평가 호출은0회다. 실행 사본은 clean이고 허용 출력 문서도 아직 생성되지 않았다. 따라서 실제 workspace-write·resume 계측 성공과 토큰 절감률은 확인하지 못했다.

## 환경 변경 근거

실행 전에 CLI·안전한 실행 환경·전역 설정·규칙·Skill·플러그인 관련 파일184개의 fingerprint를 고정했다. 30초 주기 검사와 종료 후 검사에서 다음 차이를 확인했다.

1. 전역 `config.toml`에 해당 파일럿 경로의 `trust_level` 항목이 추가됐다. 현재 파일에서 그 프로젝트 구간만 메모리상 제거하면 실행 전 SHA-256과 정확히 일치한다. 전역 파일을 실제로 되돌리거나 수정하지 않았다.
2. Sites 0.1.62 캐시의 `plugin.json`과 Skill3개 파일이 사라졌다. 삭제 주체와 원인은 UNKNOWN이다. 실행된 모델 도구 명령은 사본 내부 상태·지침·역할 문서 읽기였고, stderr에 관련 원인 정보는 없었다. 이 사실만으로 CLI 또는 다른 세션을 삭제 주체로 단정하지 않는다.

CLI 바이너리·버전·수집 helper와 안전한 실행 환경의 해시는 같았다. 기록된 차이를 단순 경로 등록만으로 축소하거나 모두 무해한 변경으로 제외하지 않는다. 예전001 환경 실패는 여전히 별도 UNKNOWN 기록이며 이번 진단으로 복권하지 않는다.

## 사전 검증 및 보존

- Before85파일·After94파일의 실제 해시와 확정 manifest 일치, 공통 TASK/input 동일, 부모 없는 단일 seed와 정답 분리 확인.
- 수집기 합성 테스트16개 독립 통과. 전체 누적값 단조 검사, stdout turn 사용량과 rollout 증분 비교, 전체 입력 inventory 일치, 예외 시 원시 증거 보존 및 최종 재검사를 포함한다.
- 모델 호출 없는 CLI 옵션 검증 exit0, 검증 전후 환경 동일. 이는 실제 모델 실행 중 환경 안정성을 보장하지 않았음이 이번 파일럿에서 드러났다.
- 두 군에 동일한 실행 옵션으로 외부 앱·설정된 MCP·웹·하위 모델 호출을 제한했다. 전체 전달 도구 목록과 보이지 않는 원격 부작용의 완전한 관측은 UNAVAILABLE이다.
- 새 사용량·명령·rollout·파일별 전후 fingerprint·진단은 권한을 제한한 비공개 pilot-001에 보존했다. 원시 자료와 개인 경로를 공개 보고서에 복사하지 않는다.

독립 사후 검토에서도 원시 rollout 해시·사용량·변경된 환경 파일·두 사본의 clean 상태·후속 호출0회를 확인했다. 측정환경 검증은 FAIL, 본 비교는 BLOCKED이며 제품·지침 품질은 판정 불가다.

## 재개 전 필요한 보완

프로젝트 신뢰 등록을 실제 지침·도구 환경 변경과 구분할 수 있도록 시작 전 비밀값 없는 설정 구조 증거를 남기고, 플러그인 캐시의 변경·정리 주체 및 안정된 실행 조건을 확인해야 한다. 시작·종료 상태만 비교해 중간 변화를 놓치거나 원인 미확정 변경을 허용해서는 안 된다. 전역 설정이나 권한을 임의 수정해 검사 통과를 만들지 않는다.

수집기 결함을 수정한 오프라인 테스트와 실제 resume 검증은 별개다. 환경 조건을 보완한 뒤 새 진단 batch의 파일럿2+검토1을 구체화해야 한다. 기존 replacement0 계약에 따라 이번 실패를 지우거나 같은 batch에서 자동 대체하지 않는다. 기존 잔여 총량은 입력5,931,982·출력119,157·시간약8,969.70초지만, 남은 예산 자체가 추가 재실행 허가는 아니다.

## 검증 결과 계약

```text
status: BLOCKED
issue_number: 227
task_id: GH-227-INSTRUCTION-ROLLOUT-PHASE4
design_id: HARNESS-DESIGN-GH-227-002
changed_files: pilot-report; execution-contract; v2 plan; TASK; private pilot inputs/runner/evidence
executed_checks: fixture/runner 독립 사전검토; 수집기16tests; CLI no-model preflight; Before 파일럿 첫 turn; 환경 전후·중간 검사; 문서 harness/hooks/diff
passed_checks: fixture/synthetic/preflight; 원본 기존 지침·과거 원장10파일 해시 보존; 문서 검사
failed_checks: LIVE_ENVIRONMENT_DRIFT; POST_ENVIRONMENT_DRIFT
blocked_checks: 실제 resume/workspace-write 완료; After 파일럿; 독립 모델 gate; 본 비교
assumptions: 설정의 신뢰 항목 추가와 캐시 삭제는 별개 변화로 취급
risks: 캐시 삭제 원인 미확정; 실제 다중 turn 계측 미검증; 중단 시 서버 측 미기록 처리 가능성
required_human_decisions: 환경 보완 후 구체적인 새 진단 batch의 재실행 계약 확정
```
