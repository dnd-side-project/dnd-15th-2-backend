# #227 After v1 적용 보고서

status: PASS (적용·검증 완료; 게시 확인 대기)

## 적용과 근거

사용자가 After 기준안과 #227 적용·검증·커밋·PR 게시 범위를 승인했다. [적용 계획](../../superpowers/plans/2026-09-15-after-v1-rollout.md)을 따라 지침을 수정했다. 모델 선택이나 개인별 작업 순서는 고정하지 않는다.

- 공통 권한·승인·역할·필수 검증은 root에 유지하고 테스트·AWS 설계·Terraform 주석 상세 정책은 원문 reference와 조건부 진입 경로로 연결했다.
- production Java 경로 지침과 작업별 문서 선택표를 추가했다. 각 패키지에 같은 지침을 일괄 복제하지 않는다.
- 문서 선택표에 요구사항의 확정·가정·미확정 구분, 기존 동작과 신규 승인 구분을 추가했다. 독립 읽기 묶음과 필요한 출력 범위, 불필요한 재탐색·폴링 감소도 안내한다.
- 권한·승인·필수 검사 및 실패 보고 조건을 완화하지 않았다. 애플리케이션·Terraform·CI 코드는 수정하지 않았다.

[적용 manifest](gh-227-after-v1-rollout-manifest.json)는 변경한 runtime17파일의 SHA-256을 기록한다. 002설계11파일 중8파일은 frozen C1과 동일하다. TASK_DOCUMENT_ROUTING.md에 후속 보완을 추가했고, 인프라 reference2파일은 staged 공백 검사를 위해 EOF 빈줄만 제거했다. 본문 정책은 보존했다. 기존 테스트 지침6파일은 보존했다.

## 측정 결과와 최종 적용안의 차이

[Run1](gh-227-v2-results.md)과 [B/X 반복](gh-227-v2-repeat-report.md)은 frozen C1에 대한 결과다. X 입력 감소는58.84%·65.41%·49.77%로 반복됐으나 B의 최초74.97% 감소는 재현되지 않았다. B 반복1건의 환경 영향 TASK_FAIL과 Q 양쪽 FAIL을 보존한다. F/R/A는 추가 반복 없이 [실행 경로](gh-227-v2-fra-trajectory.md)를 분석했다.

이후 추가한 Q 근거 관리와 탐색 문구는 새 After v1에 포함된다. 이 버전의 세션 전체 토큰 감소나 Q 품질 개선은 측정하지 않았다. 정책·링크·필수 검사 통과를 비용 절감 검증으로 표현하지 않는다. 원본 평가 입력·결과·실패 원장은 변경하지 않았다.

## 검증

- ./harness check: PASS.
- ./harness pr-ready --project-tests: exit0 PASS. Gradle 단위/통합은 UP-TO-DATE로 결과를 재사용했다. XML 단위1,064·통합735, 실패/오류/skip0이며 이번 적용 후 새로 실행한 테스트 수로 표현하지 않는다.
- npm run hooks:validate 및 git diff --check: PASS.
- 부모 branch는 다른 worktree에서 사용 중이라 로컬 fast-forward가 거부되어 그대로 두었다. 원격 기준 최신성 검사는 통과했다.
- 원문447개 정책 보존·보호 구역·라우팅·공개 산출물 독립 검토: PASS. 링크23개 해소, runtime17파일 hash 일치, 기존 연구 기록30파일 byte 보존과 공개 CSV3파일의 모든 셀·결과 hash 보존을 확인했다. CSV3파일은 게시용 CRLF→LF 정규화만 수행했으며 원본은 비공개 snapshot에 보존했다. [변환 해시](gh-227-publication-normalization.json)를 따른다.
- 신규 벤치마크·Q 재평가·Terraform 검사/plan/apply: 미실행. Terraform 파일 변경이 없으며 비용·품질 개선 재평가는 별도 범위다.

## 게시와 롤백

지침 적용 커밋과 실험·설계·검증 기록 커밋을 분리한다. #226 branch를 base로 Draft PR을 게시하고 사람 리뷰를 기다린다. 병합·배포는 수행하지 않는다. 지침 도입을 되돌릴 때는 해당 지침 커밋의 revert PR로 처리하며 연구 원장은 유지한다.

## 결과 계약

- issue_number: 227
- task_id: GH-227-INSTRUCTION-ROLLOUT-PHASE4
- design_id: HARNESS-DESIGN-GH-227-003
- changed_files: runtime17파일(manifest), TASK, 적용 계획·보고서·실험 증거
- executed_checks/passed_checks: 위 검증 참조
- failed_checks: 이번 적용의 필수 검사 실패 없음. 과거 실험 실패는 원장 유지
- blocked_checks: 필수 검사 차단 없음; 게시 최종 확인 대기
- assumptions/risks: 작은 표본과 환경 차이; 추가 문구의 효과 미측정; 필수 지침 선택 누락 가능성
- required_human_decisions: PR 리뷰·병합 여부; 후속 실사용 평가 여부

## 로컬 Hook 설치와 보완 검사

첫 지침 커밋 `568360c` 당시 core.hooksPath는 설정되어 있었으나 이 worktree의 Husky 실행 파일과 의존성이 없어 자동 pre-commit/prepare-commit-msg/commit-msg가 실행되지 않았다. 우회 옵션은 사용하지 않았다. npm ci의 prepare로 Husky를 설치했다. 이후 branch/commit 규칙, 전체 파일 secret preflight, 첫 커밋 diff --check, 메시지 formatter self-test, 첫 커밋 메시지의 run-hook.py commit-msg를 직접 실행해 모두 PASS였다. Java/CI 소스 변경은 없어 해당 조건부 훅 검사는 적용 대상이 아니다. 후속 커밋과 push는 설치된 훅을 사용한다. 초기 자동 훅 미실행 이력은 남기며 GitHub CI를 별도 최종 검증으로 확인한다.
