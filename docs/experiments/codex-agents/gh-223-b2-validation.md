# GH-223 B2 검증 기록

- issue_number: 223
- task_id: GH-223-INSTRUCTION-ARCHITECTURE-PHASE2
- design_id: HARNESS-DESIGN-GH-223-001
- status: PASS
- candidate branch: chore/gh-223-instruction-b2
- base commit: d13fd2f9057bfa80f6591dcb0f80a99e00ffdd79
- reviewer model: gpt-5.6-sol/high

## 구현과 독립 검토

작업 계약과 instruction 링크를 검사하는 Python validator 두 개를 추가하고 Harness, staged pre-commit, 읽기 전용 CI policy 단계에 연결했다. 첫 Work gate의 Issue와 구체 branch, 필수 세 섹션을 확인하며 원격 Issue와 승인 진위를 판정하지 않는다. 링크 검사는 선언된 source/target/anchor를 확인한다. staged 검사는 Git index를 읽고 삭제만 staged된 경우에도 실행한다.

독립 검토에서 안전한 parent-relative 링크를 잘못 차단하는 문제와 CLI 상대경로 진단 누락을 발견했다. 첫 수정 후 Unicode 비인쇄 문자가 진단 출력에 포함되는 문제를 추가 발견했다. 두 차례 수정과 scoped 재검토 후 코드 판정은 PASS다. 정상 한글 상대경로와 저장소 내부 parent-relative 링크는 허용하고 저장소 밖 경로, symlink 탈출 및 비인쇄 진단 경로는 차단한다.

B1 root의 IaC 정책 전체와 Hook 우회 보고 조건은 보존했다. 기존 정책 추적표 447행의 22개 열은 값과 순서를 그대로 유지하고 B2 열 세 개를 추가했다. manifest는 21개 선언 경로를 검사한다. 원문 정책 의미와 승인 진위를 자동으로 검증한다고 주장하지 않는다.

## 실행한 검사

- 두 validator self-test와 실제 B2 TASK/link: PASS
- staged add/delete/rename/missing 및 working/index 불일치 fixture: PASS
- 삭제-only pre-commit, detached branch 명시, main 완료 TASK fixture: PASS
- 안전한 parent-relative, root escape, percent-decoded escape와 symlink fixture: PASS
- CLI 상대경로, 문서 본문·절대경로 비출력 및 Unicode 비인쇄문자 fixture: PASS
- `./harness check`: PASS
- `npm run hooks:validate`: PASS
- `git diff --check`: PASS
- 독립 source/정책/코드 검토 및 수정 범위 재검토: PASS
- `./harness pr-ready --project-tests`: PASS, exit 0

## 통합 테스트 실패 이력

B1과 B2의 애플리케이션, 단위·통합 테스트 및 Gradle 파일은 동일하다. 다음 실패를 숨기거나 통과 결과로 대체하지 않는다.

1. 최초 `./gradlew check`의 `:integrationTest`에서 PushDeviceRegistrationIntegrationTest 7건이 실패했다. 최초 1건은 Flyway의 로컬 PostgreSQL Testcontainer 연결 초기화 중 SSL 설정 오류였고, 6건은 Spring context failure-threshold에 따른 연쇄 실패였다.
2. 해당 클래스 단독 재실행은 7/7 통과했다. 전체 integrationTest 재실행에서도 해당 7건은 통과했으나 전체 735건 중 ObservabilityMeterContractIntegrationTest 2건이 실패했다.
3. 새 실패는 management listener의 Prometheus scrape 무응답으로 assertion 전에 발생했다. 해당 클래스 단독 재실행은 2/2 통과했다.

두 실패 모두 단독 재현되지 않았으며 근본 원인은 미확정이다. 컨테이너 준비나 연결 상태는 가설이며 확인된 원인으로 기록하지 않는다. retry/timeout 변경이나 테스트 suppress는 추가하지 않았다. 재현은 로컬 Testcontainers에서 수행했고 외부 AWS·공유 DB·운영 자원에 접근하지 않았다. 원문 로그는 저장소 밖 권한 0600 임시 파일로 보관하며 이 문서에 민감값을 포함하지 않는다.

## 한계와 남은 위험

- assumptions: 검사 대상은 선언된 manifest 경로이며 자연어 의미·사람 승인 진위는 독립 검토 대상이다.
- risks: 간헐적인 로컬 통합 테스트 실패가 재발할 수 있다. manifest와 필수 항목 수를 함께 줄이는 변경은 diff 검토가 필요하다.
- blocked_checks: 없음. 원격 CI와 모델 평가는 후속 단계 미검증으로 구분한다.
- unverified_checks: 원격 CI, Ruleset, 실제 instruction 전달, 파일럿, 204세션 비교 평가.
- required_human_decisions: 검증 완료 후 B2 구현과 증거 커밋 초안 승인. push·PR·merge는 별도 요청 전 수행하지 않는다.

독립 검토 보고서와 검토 대상 파일의 SHA-256은 [fixture 기록](gh-223-fixtures.json)에 기록한다. 후보별 정책 목적지와 자동 검사 한계는 [정책 추적표](gh-223-policy-source-map.csv)에 기록한다.

## 최종 프로젝트 검증

`./harness pr-ready --project-tests`가 exit 0으로 완료됐다. 최종 통합 테스트 결과는 735건, failures 0, errors 0이다. 단위 테스트 결과 1064건도 failures 0, errors 0이며 이번 명령에서는 UP-TO-DATE였다. 앞선 간헐 실패 두 종류의 원인은 확정되지 않았으므로 위 실패 이력과 재발 위험을 유지한다.

Harness가 다른 checkout에서 사용 중인 로컬 main의 fast-forward를 시도하며 경고했으나 해당 checkout을 변경하지 않고 검증을 계속해 통과했다. 커밋·push·PR·merge는 실행하지 않았다.
