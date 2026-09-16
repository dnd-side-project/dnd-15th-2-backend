# GH-227 v2 감사·설계 준비 결과

```text
status: PASS (감사·설계 및 명시된 부분 준비 범위)
issue_number: 227
task_id: GH-227-INSTRUCTION-ROLLOUT-PHASE4
design_id: HARNESS-DESIGN-GH-227-002 (DRAFT_FOR_REVIEW)
implementation_status: NOT_STARTED
runtime_evaluation_status: BLOCKED
new_evaluation_sessions: 0
```

## 변경 파일

신규: gh-227-v2-instruction-audit.md, gh-227-v2-instruction-inventory.json,
gh-227-v2-policy-map.csv, gh-227-v2-fixture-catalog.md, 이 보고서(모두 이 디렉터리),
docs/superpowers/specs/2026-09-14-instruction-hybrid-phase4-v2-design.md.
수정: TASK.md, docs/superpowers/plans/2026-09-14-instruction-hybrid-workflow-phase4-v2.md.

런타임 지침과 이전 회귀 원장은 이 새 세션에서 수정하지 않았다. 감사 전14파일 snapshot과 비교해 TASK/현재v2계획만 변경된 것을 확인했다.

## executed_checks / passed_checks

- git status 및 branch/TASK 확인, GitHub Issue #227 OPEN 읽기 확인.
- ./harness check: PASS.
- npm run hooks:validate: PASS.
- git diff --check: PASS.
- snapshot 보존/hash와 기존 참조 경로 확인: PASS.
- CSV447행·447개 고유 ID 확인: PASS.
- 독립 정적 검토(audit_routing_v2): PASS, material finding0. 기존 원장과447개 ID·절·좌표·hash·현재 경로의 불일치0. 제안 소유 위치296root+16test+69design+66comment 일치.

## failed_checks / blocked_checks

새 준비 단계의 실행 검사 실패0. 제품 검사는 이번 문서 준비의 검증 수단이 아니므로 재실행하지 않았다. 실제 후보 변경 후 필수 프로젝트 검사를 새로 실행해야 한다. 과거001의 프로젝트 검사 PASS를002에 승계하지 않는다.
유료 평가: 실행 계약·예산 미승인, C0와 fixture 입력/검사 미확정, 기존 환경 drift 미해결로 미실행.

## assumptions / risks

Sol/high는 고정 측정 구성 제안이며 실제 사용자에게 강제하지 않는다. 하위 지침의 존재는 자동 로딩 보장이 아니다. Java 진입점은 초기 읽기를 늘릴 수 있다. CSV는 기존 root447단위의 제안 mapping이며 모든 문서의 정책이나 실제 target hash 보존 검증을 완료한 자료가 아니다.

## required_human_decisions

- 구체적11파일 조합 설계의 적용 범위 검토.
- 실제 사용하던 Before의 하네스 버전과 정규화 비교 기준 확정.
- 과제 입력·허용 파일·검사·승인 증거가 완성된 실행 계약과 평가 예산 검토.

설계/평가 계약은 아직 자기 승인하지 않았다. 커밋/push/PR/merge·전역 설정·측정 플랫폼 작업은 수행하지 않았다.
