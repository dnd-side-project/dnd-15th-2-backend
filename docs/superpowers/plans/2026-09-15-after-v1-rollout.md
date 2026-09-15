# #227 After v1 적용 및 PR 게시 계획

- 상태: APPROVED_FOR_IMPLEMENTATION
- Task: GH-227-INSTRUCTION-ROLLOUT-PHASE4
- 설계: HARNESS-DESIGN-GH-227-003
- 승인: 사용자가 After 기준안과 #227 적용·검증·커밋·PR 게시 범위 설명에 “그럼 그렇게 진행해줘”로 승인했다.

## 적용 계약

002 설계의 11개 runtime 파일 allowlist와 기존 테스트 지침 7개를 기준으로 frozen C1을 적용한다. 원본 정책 이동과 조건부 진입 경로를 보존한다. TASK_DOCUMENT_ROUTING.md에 요구사항의 CONFIRMED/ASSUMED/UNKNOWN 구분, 기존 코드 동작과 신규 승인 구분, 실패·부분 상태·민감정보 확인을 보완한다. 독립 읽기 묶음, 필요한 출력 범위, 근거 없는 재탐색·모델 폴링 감소를 간결하게 안내한다. 필수 검사를 줄이거나 권한·승인을 완화하지 않는다.

모델 선택과 사용자별 워크플로우는 고정하지 않는다. measured C1/Run1/반복 원장은 변경하지 않는다. 최종 After v1은 추가 보완을 포함한 새 버전이며 토큰 감소율과 Q 개선 효과를 검증했다고 주장하지 않는다. 새 벤치마크 모델 실행은 하지 않는다.

## 실행 순서와 소유권

1. 오케스트레이터: Issue/branch/TASK 확인, 기존 변경의 비공개 snapshot, 승인 계약 및 공개 PR 설명 준비.
2. 실행 에이전트: 002 allowlist의 지침 적용과 TASK_DOCUMENT_ROUTING 보완. 다른 작업자의 변경 보존. 공개 연구 원장·애플리케이션·Terraform·CI·전역 설정 수정 금지.
3. 독립 검증 에이전트: 정책447개 이동/보존, 보호 구역, 링크/라우팅 조건, measured C1 대비 추가 변경 검토. 제품 테스트는 root의 실제 결과를 독립 확인한다.
4. 오케스트레이터: harness check, pr-ready --project-tests, hooks:validate, diff --check. 실패는 원인 해결 후 필요한 검사 재실행. 결과/한계/롤백 보고.
5. 커밋을 지침 적용과 실험·설계·검증 기록으로 나눈다. harness sync로 부모 원격 변경 반영 후 검사, push와 #226 기반 Draft PR 게시. 병합하지 않는다.

## 완료 기준과 롤백

- 승인 범위 외 변경0, 원문 정책 누락0, 지침 링크 해소 및 독립 검토 완료.
- 필수 검사 실제 실행, 결과와 미검증 효율 주장을 구분.
- TASK, 최종 적용 보고서, 실제 지침 manifest와 PR 연결.
- 롤백은 해당 지침 적용 커밋의 revert PR로 수행하며 실험 원장은 보존한다. 기존 사용자 변경을 reset/clean하지 않는다.
