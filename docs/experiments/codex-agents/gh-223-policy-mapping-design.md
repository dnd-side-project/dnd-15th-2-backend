# GH-223 정책 mapping 및 B0 복원 설계

- Issue: #223
- Task: GH-223-INSTRUCTION-ARCHITECTURE-PHASE2
- Design: HARNESS-DESIGN-GH-223-001
- 상태: SECTIONS_APPROVED; 전체 spec 검토·후보 구현 및 정책 보존 통과 판정 전
- 승인: 정책별 원문→후보 위치→읽는 시점→강제 수단→검증 연결, root 안전 불변조건 유지, 모호한 정책의 임의 완화 금지 기준을 사용자가 승인했다.
- 이 문서는 승인된 첫 설계 섹션의 작업 자료다. 전체 design spec과 구현 계획을 대체하지 않는다.

## 기준 원문과 증거 경계

현재 기준 commit은 `4aa58b8b5cad727372e7dac1dd254099b62243b8`이다.
AGENTS.md SHA-256은 `cb2a80093dfd08c755e66b89a106400f95a3246a377903afa4f432f9a84b4847`이다.
Phase 1 A0 `fdc088b40fabb70b04ec80cf33ee859cdec70eea`의 AGENTS.md와 내용이 동일하다.
역사적 A3는 `5b2af27706c7a3f9d6d1593ea35788c074566362`이며 수정하지 않는다.

`gh-223-policy-source-map.csv`는 모든 비어 있지 않은 비제목 원문을 447개 단위로 보존한다.
규칙뿐 아니라 문맥 문장과 코드 예시도 포함하므로 447을 독립 정책 수로 해석하지 않는다.
각 행의 원문 줄 범위·원문 내용은 자동 검증할 수 있다. 예시의 실제 규범성, 조건,
예외와 행위 주체는 문맥을 포함해 독립 검토해야 한다. CSV의 후보 위치는 설계상 목적지이며
실재 파일/anchor나 이미 구현된 검사라고 주장하지 않는다. 후보 구현 후 실제 경로·anchor·
검사 ID·증거로 치환하고 모든 행을 독립 검증하기 전 적격 판정은 금지한다.

## 정책군별 복원 및 이동 계약

아래 모든 항목은 B0 root에 동등한 강도로 복원한다. B0에서 기존 Skill이 보존해 줄 것이라고
추측하지 않는다. B1부터 상세 절차를 이동할 수 있으나 root의 불변조건·routing과 읽기 시점은
유지한다. B2의 기계 검사는 증명된 판정 항목에만 적용하고 승인 판단을 대신하지 않는다.
B3는 B2의 규칙을 유지하면서 디렉터리별 로딩 위치만 변경한다.

| 원문 | 반드시 보존할 의미 | B1 이후 상세 목적지 | 검증 및 읽는 시점 |
| --- | --- | --- | --- |
| 서문 | 저장소 계약/TASK 우선, 하위 역할·Skill·자동화의 완화/우회 금지 | core reference | S1; 시작 시, 실제 플랫폼 상위 지침을 뒤집는 우선순위 선언 금지 |
| §1 | Project draft→Issue, 범위/완료 조건, branch/TASK 일치, status 확인, 최신 origin/main, 안전한 FF, stacked base 일관성 | harness-issue + delivery reference | S3 및 승인된 정상 intake; 구현 전 |
| §1 | Issue 없을 때 계획/분해만, 구현·인프라 변경·배포·PR 금지, 일정은 Project 필드 | root + harness-issue | S2/S3; 계획 허용과 구현 금지를 구분 |
| §2.1 | 오케스트레이터 분석·분해·위험·인수·실행/검증 배정, 직접 앱/Terraform 구현 및 자기 승인 금지 | roles reference | ROLE-POLICY; 역할 배정 전 |
| §2.2 | 승인된 파일/범위만, 기존 변경 보존, 결과와 실제 실행 검증 구분 | root + executor role | SMOKE-DOC/TEST; 변경 전후 diff |
| §2.3 | 독립 검증, 실제 파일/결과 확인, 통과 목적 수정·suppress·자동 승인 금지, 미검증 BLOCKED | root + verifier role | ROLE-POLICY; 검증 전 |
| §2.4 | Issue/TASK/계획/증거/승인/비용/운영/복구 리뷰 | roles reference | REVIEW-POLICY; 사람 검토 전 |
| §3 | JUnit 5, unit/integration 분리, DisplayName, 정확한 시각·원본 계획 ID, 보고서 템플릿 | harness-test-plan/run | S4, SMOKE-TEST; 테스트 작성 전 |
| §3 | 앱·infra·DB·동시성·트랜잭션·외부 API·복구 위험, 구현/환경 실패 구분, 실패 명령·요약·재현·미검증·위험 보고 | test reference | S4, REPORT-POLICY; 테스트 계획 및 보고 전 |
| §4.1 | Terraform만, CDK/CF/Pulumi/SDK/CLI/앱 시작 생성 금지, 기존 IaC 제거/변환은 별도 Issue/계획/승인 | root + infra reference | S5/S6; 인프라 작업 전 |
| §4.1 | Terraform/Provider/Module 버전, lock 커밋, 비밀 기본값 금지, 값 주입, provisioner 기본 금지, §4.1 예외 기록 7항목(적용 범위 해석 보류; 절대 금지 해제 아님) | infra reference | INFRA-POLICY 정적 문서 검토; Terraform 작성 전 |
| §4.2 | design→승인→build, Issue/DESIGN-ID/보고서/APPROVED_FOR_BUILD/파일·Module/위험검토/승인증거 모두 충족 | root route + infra reference | S5; build 전, 누락 시 BLOCKED |
| §4.3 | 15개 설계 입력과 CONFIRMED/ASSUMED/UNKNOWN/BLOCKED 의미, 미확인 값 임의 확정 금지 | infra intake reference | INFRA-POLICY; 설계 전 |
| §4.4 | 실용 최저 비용 우선, 서비스 사전 고정 금지, 적합한 후보만 비교, 선택/탈락/가용성/운영/확장/복구/비용/종속/전환비용 | infra architecture reference | INFRA-POLICY; 설계 검토 전 |
| §4.5 | 네트워크부터 태그까지 20개 AWS 검토 영역 | infra review reference | INFRA-POLICY; 설계 검토 전 |
| §4.6 | State 민감 취급, S3 backend 10개 보호조건, 가능하면 lockfile, AI State 조작 금지, State 복구 등의 별도 Issue·명시 승인 조건(승인된 주체 구분: 사람 수행 필요조건) | root + infra state reference | S6, INFRA-POLICY; 접근/설계 전 |
| §4.7 | plan 민감 취급, plan 원문 PR 복사 금지·전체 plan JSON 공개 로그 출력 금지 및 §4.10 민감값 매체별 금지, 비민감 요약, SHA-256, commit 일치, 가능한 증거 11항목, 암호화·접근제한·짧은 만료 | root + infra evidence reference | S6, REPORT-POLICY; plan 저장/보고 전 |
| §4.8 | AI apply 금지, 보호된 Actions만, 10개 apply 조건, PR/Environment 승인 분리, CODEOWNERS 한계 | root + infra approval reference | S6; 승인 흐름 설명, 실제 apply 금지 |
| §4.9 | OIDC 단기 인증, 장기키 금지, plan/apply 역할 분리, 보호된 assume, 로컬 운영 apply 권한 및 AI 운영 변경 권한 금지 | root + infra auth reference | S5/S6; 인증 설계 전 |
| §4.10 | 모든 열거된 민감정보와 기록 매체, 논리 식별자/placeholder 사용 | root + secret reference | SAFETY-POLICY; 모든 출력/파일 작성 전 |
| §5.1 | 비기본 설정·서비스 제약·보안·비용/가용성·의존성·lifecycle·조건·환경차·외부관리·일관성·예외·임시 호환성 주석 | infra comments reference | INFRA-POLICY; 주석 검토 전 |
| §5.2–5.4 | 코드 반복/추론·대화 주석 금지, 블록 위 의도, 원인→결정→영향, ADR ID, 불필요 depends_on 금지 및 명시 이유 | infra comments reference | INFRA-POLICY; 문서 사례 읽기 전용 평가 |
| §5.5 | 외부 관리 속성만 ignore_changes, 주체/이유/속성/제거 조건, all 기본 금지 및 ADR/Issue/승인 | infra comments reference | INFRA-POLICY; 읽기 전용 평가 |
| §5.6–5.7 | 임시 주석의 Issue/날짜/완료 조건, 보안 예외 이유/범위/통제/팀/만료/추적 | infra comments reference | INFRA-POLICY; 읽기 전용 평가 |
| §5.8–5.9 | description, 민감 output 표시, 한국어·원문 서비스명·단정문·추측 금지·장문 ADR·구현과 함께 갱신 | infra comments reference | INFRA-POLICY; 읽기 전용 평가 |
| §6 | 변경 보존, 범위 밖 자동 정리 금지, 넓은 변경·삭제·운영 변경 명시 승인 | root | SAFETY-POLICY, smoke; 변경 전 |
| §6 | 원본 원장·마이그레이션 이력·운영 감사 이력 수정/삭제 절대 금지 | root | 세 보호 대상 × 수정/삭제 거부, 허용된 일반 문서 수정과 대비 |
| §6 | 재실행 안전, 부분 반영 회피, 리소스 삭제/교체 위험 사전 보고 | root + failure reference | FAILURE-POLICY; 실행 전 및 실패 시 |
| §6 | State 직접 수정, 승인 장치 편의 변경, 자기 권한/금지 명령 변경 금지 | root | S6, AUTHORITY-POLICY; 어떤 작업에서도 유지 |
| §6 | 삭제/교체/DB/CIDR/공개범위/IAM/암호화/백업/로그/backend/Provider major/운영 이름 고위험과 설계·영향·복구·승인 | root + risk reference | RISK-POLICY; 위험 변경 전 |
| §7 | commit/PR 형식, type·Issue 일치, PR 증거 9항목, 한 commit 한 목적 | delivery reference | S7, DELIVERY-POLICY; commit/PR 전 |
| §7 | sync/base/rebase/충돌 수동 해결, rebase 직후 force-with-lease 예외, main·공유 force push 금지 | root + delivery reference | S7; push 전, 실제 push는 이 Issue에서 제외 |
| §8 | prepare 설치, pre-commit/prepare-msg/commit-msg/pre-push 책임, 메시지 보존·오류 차단, 우회 기록 5항목, Actions 최종 강제 | delivery reference + proven checks | DELIVERY-POLICY; hook 작업 전 |
| §9 | type 정확히 하나, area 선택, status 제한, 일정/우선순위 Project, PR type 산출 및 LABELS 참조 | harness-issue/pr reference + checks | DELIVERY-POLICY; Issue/PR 작성 전 |
| §10 | 4개 기본 검증, infra 3개 기본 검사, 승인 환경 plan, 구성된 추가 도구 생략 금지, 미실행 5항목 기록 | root route + validation reference | S4/S5/S7, REPORT-POLICY; 검증 범위 결정 전 |
| §11 | PASS/FAIL/BLOCKED 조건, 일반 12개 보고 필드와 가능한 infra 9개 필드 | root + report reference | REPORT-POLICY; 최종 보고 전 |
| §12 | 8개 Terraform 명령군과 동일 효과 우회 금지, 18개 금지 행위, 확인 불가 시 추측 금지/BLOCKED | root | S3/S6, SAFETY/AUTHORITY-POLICY; 시작 시 |

표의 추가 POLICY 시나리오 이름은 설계용 식별자다. exact prompt, 표본 수와 rubric은
후속 평가 설계에서 승인받는다. S1~S7만으로 모든 원문 정책이 동적으로 입증된다고 주장하지 않는다.
Terraform 정책은 문서 의미 검토와 읽기 전용 시나리오로만 평가한다. 실제 Terraform 변경 안전성은 미검증으로 남는다.

## B0 복원 설계

A3의 압축된 구조와 명시적 Skill routing을 출발점으로 삼되 길이 목표를 먼저 고정하지 않는다.
원장 보호 한 문장만 추가하는 방식은 불충분하다. 다음 차이를 모두 검토한다.

1. 조건부 금지와 절대 금지를 구분한다. A3의 포괄적인 hook 우회 금지는 baseline의 우회 기록
   절차와 동등하지 않다. 임의 완화도 과도한 금지로 인한 false-block도 정책 보존 실패다.
2. A3에서 누락된 운영 리소스 이름 변경과 공개 범위 확대 등 고위험 범주를 명시한다.
3. 원본 원장/마이그레이션/운영 감사 이력 보호, 자기 권한 변경 금지, 재실행 안전과 부분 반영
   회피를 root에 복원한다. 인프라 상세 승인·인증·보관·주석 규칙도 원문 단위와 대조한다.
4. B0는 상세 누락분을 root에서 복원하여 외부 reference 신설 없이 비교 출발점을 만든다.
   B1의 비용 절감은 이 복원된 B0 대비 측정한다. 역사적 A3 비용과 혼동하지 않는다.
5. B0의 실재 문구/anchor/정적 검사 및 회귀 증거가 완성될 때까지 각 CSV 행은
   SEMANTIC_REVIEW_PENDING/NOT_IMPLEMENTED 상태다. 색인 coverage를 의미 보존 PASS로 올리지 않는다.

## 정책 이동 방식의 대안

- 권고: root 불변조건 + 작업별 상세 reference + 증명된 기계 검사. 작업 전 제약 인지와
  필요 시 로딩을 함께 확보하며 이슈의 누적 비교 방향과 일치한다.
- 상세 규칙을 모두 root에 유지: 전달 누락 위험은 낮지만 B1 이후 추가 로딩 절감 가설을 검증할 수 없다.
- 상세 규칙을 모두 reference/CI에 위임: 시작 비용은 줄일 수 있으나 사전 인지 누락과
  로컬 hook 미설치·우회·원격 CI 미실행 시 안전성 공백이 생겨 채택하지 않는다.

## 승인 및 후속 검증 경계

- 정책 보존 기준, 공통 충돌 정정, B1/B2/B3 구조와 평가 조건의 섹션별 승인은 완료됐다.
- 전체 spec은 docs/superpowers/specs/2026-09-11-codex-instruction-architecture-phase2-design.md다.
- 실제 파일 allowlist/anchor, 계측 pilot과 구현 순서는 spec 검토 승인 후 구현 계획에서 확정한다.
- 전체 spec 문서 검토, 테스트 계획, 구현 계획, 후보 작성 및 모든 커밋은 각 승인 절차를 따른다.
- 외부 instruction chain의 실제 고정 환경과 동작, 후보 의미 보존은 실행 전·후 독립 검증 대상이다.
