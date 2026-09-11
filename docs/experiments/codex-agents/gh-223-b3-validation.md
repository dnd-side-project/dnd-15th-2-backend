# GH-223 B3 검증 기록

- status: PASS
- issue_number: 223
- task_id: GH-223-INSTRUCTION-ARCHITECTURE-PHASE2
- design_id: HARNESS-DESIGN-GH-223-001
- candidate branch: chore/gh-223-instruction-b3
- base commit: 9b6ddcd4d15226a318a716e7286149fc7dd12029

## 변경

B2를 누적 상속하고 `infra/AGENTS.md`, `src/test/AGENTS.md`, `src/integrationTest/AGENTS.md`에 짧은 진입 지침을 추가했다. 상세 정책은 기존 Skill과 reference를 공유한다. 루트에서 해당 영역을 다루는 경우에도 먼저 하위 지침을 읽고, 혼합 작업은 관련 지침을 모두 적용한다. `cd`만으로 instruction이 자동 재로딩된다고 가정하지 않는다.

읽기·분석·계획·검토는 기존 허용 범위에서 수행하며, Terraform 구현 및 테스트 작성·실행 시점에 승인 조건을 확인한다. 안전·승인 정책 본문, Skill frontmatter와 기존 21개 manifest 항목을 유지했다. 하위 지침 연결 25개를 추가하여 manifest는 총 46개다. 앱·Java·Terraform·검사기·workflow 코드는 변경하지 않았다.

## 크기와 정책 추적

| 시작 경로 | 저장소 AGENTS chain bytes |
| --- | ---: |
| root | 16,990 |
| infra | 18,945 |
| src/test | 18,113 |
| src/integrationTest | 18,252 |

모든 저장소 chain이 32,768 bytes 이하다. 이 값은 파일 크기 합계이며 실제 모델 전달량이나 추가 reference 로딩량을 의미하지 않는다. 글로벌 지침·도구·wrapper 비용 및 실제 읽기 비용은 후속 계측에서 따로 다룬다.

정책 추적표의 기존 25열·447행 parsed 값과 순서를 보존하고 B3 목적지·앵커·의미 검토 상태·증거 열을 추가했다. 공통 Skill frontmatter 10개도 고정된 상태다.

## 실행한 검증

- TASK 계약, 46개 source/target/anchor 검사: PASS
- `./harness check`: PASS
- `npm run hooks:validate`: PASS
- `git diff --check`, 신규 파일 EOF, manifest 기존 항목·metadata·추적표 보존: PASS
- `./gradlew check`: PASS. 단위 테스트 1,064건, 통합 테스트 735건 모두 failures 0, errors 0.
- `./harness pr-ready --project-tests`: PASS, exit 0. 직전 전체 실행 결과를 UP-TO-DATE로 재사용했다.
- 독립 정책·routing 검토: PASS, 요청 변경 또는 차단 finding 없음.

Harness는 다른 checkout에서 사용 중인 로컬 main의 fast-forward를 시도하며 경고했으나 해당 checkout을 변경하지 않고 검증을 계속해 통과했다. Gradle 원문 로그는 저장소 밖 권한 0600 임시 파일에만 보관한다. B2에서 관찰한 간헐적 로컬 통합 실패는 B2 보고서에 남아 있으며 이번 B3 전체 실행에서는 발생하지 않았다.

## 한계

- failed_checks: 현재 실행한 검사에서 없음.
- blocked_checks: 없음.
- assumptions: 기존 reference가 상세 정책의 출처이며 하위 지침은 적용 경로를 연결한다.
- risks: 링크·파일 크기 검사만으로 실제 instruction 전달, 행동 준수 및 비용 개선을 증명할 수 없다.
- unverified_checks: 실제 모델 로딩, pilot, 204세션 평가, 원격 CI 및 Ruleset 효과.
- required_human_decisions: 독립 검토 완료 후 B3 구현·증거 커밋 초안 승인.

후보 파일 hash와 검증 근거는 [fixture 기록](gh-223-fixtures.json), 정책별 경로는 [정책 추적표](gh-223-policy-source-map.csv)에 기록한다. 커밋·push·PR·merge는 아직 실행하지 않았다.
