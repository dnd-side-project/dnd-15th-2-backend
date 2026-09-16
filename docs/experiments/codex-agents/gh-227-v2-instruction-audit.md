# GH-227 v2 지침 감사

- 상태: 감사 완료, 후보 미구현, 토큰 절감 미검증
- 작업: GH-227-INSTRUCTION-ROLLOUT-PHASE4 / HARNESS-DESIGN-GH-227-002
- 기준 HEAD: 6ec850652270000f94d561340ac59e09b508f6cd
- 관측일: 2026-09-14
- 독립 읽기 전용 감사: audit_routing_v2. 실제 소스 근거를 확인했으며 실행 모델 평가는 하지 않았다.

## 보존과 측정 경계

감사 전 미커밋14파일을 비공개 pre-audit snapshot과 SHA-256 manifest로 보존했다. 과거 Phase4 원장/보고서/검증 문서는 수정하지 않았다. `gh-227-v2-instruction-inventory.json`은39개 지침 파일의 공개 경로·크기·hash 목록이다. 이는 전역 plugin inventory나 실제 모델 주입 목록이 아니다.

현재 root 25,006 bytes, CLAUDE 9,857 bytes, Java 관례5,960 bytes다. bytes를 실제 토큰으로 변환하지 않는다. 과거 TEST_SCOPED의 정적 원문 보존 통과를 새로운 조합의 검증 통과로 승계하지 않는다.

## 근거와 결정

| 근거 | 관찰 | 이번 후보 결정 |
| --- | --- | --- |
| AGENTS.md:179,208,245 | AWS 입력·대안·검토 상세가 모든 작업의 root에 위치 | §4.3~4.5를 원문 이동하고 AWS 설계 전 명시적 라우팅. 구현/Apply/State/비밀 경계는 root 유지 |
| AGENTS.md:387 | Terraform 주석 상세와 예시가 전역 위치 | §5 원문을 전용 reference로 이동. Terraform 작성·검토·규칙 질의에 명시적 진입 |
| CLAUDE.md:97,126,154 | root의 인프라 규칙 중복 | 이번 수정에서 보류. Claude 고유 Console 자동화/파일 변경 제한(:243,:267) 보존 필요; 실제 Codex 읽기 없으면 절감도 없음 |
| JAVA_CONVENTIONS.md:24,39,70,83 | 주입·트랜잭션·ratchet·복잡도 규칙 존재, runtime 진입 링크 없음 | production Java 라우터 하나 추가, 각 도메인/service에 복제하지 않음 |
| agents/README.md:7, ARCHITECTURE.md:9 | 일반 기능/버그/리팩터링/분석 진입 설명 부족 | 짧은 작업별 문서 선택표. 신규 범용 Skill·모델 역할·승인 절차는 추가하지 않음 |
| test-run Skill:14, agents/test-executor.md:12 | 테스트 metadata 반복 | 이번에는 기존7파일을 보존. 추가 축약은 토큰 효과 근거 없이 확대하지 않음 |
| test-run Skill:21, harness-pr Skill:57 | 전체 검사 반복 가능 경로 | 실행 여부는 미확인. 필수 검사를 삭제하지 않고 동일 상태 검사 기록을 계측 |
| infra-design Skill:27 | 설계 reference5개 필수 읽기 | 완료 설계의 필수 읽기/독립 검토 유지. 이번에는 로딩 순서 재설계하지 않음 |
| API Skill:13, agents/api-docs-executor.md:18 | 절차·권한 설명 일부 반복 | API 문서 전용 역할의 안전 경계를 별도 감사해야 하므로 이번에는 보류 |
| 각 Skill description | 일부 길고 test-plan의 before implementation 해석이 넓을 가능성 | 과도 호출은 관측되지 않음. metadata 전체 재작성은 보류 |
| ARCHITECTURE.md:55,57 | 특정 후보 비교 고정, Terraform or AWS CDK | 현행 root에 맞추는 정합성 수정. 배치 변경과 별도 분류 |

## 범용성과 트레이드오프

하위 지침은 실제 경로에 묶이는 관례를 담당하고, 경로 없는 요구사항·설계는 root에서 조건부 문서 선택표로 연결한다. 사용자의 Astra/Sol 선택을 강제하지 않는다. Java 규칙의 발견성을 높이면 초기 읽기는 증가할 수 있으며, 재작업 감소 여부는 실측해야 한다. AWS 작업은 이동된 본문을 결국 읽으므로 절감이 작거나 링크 탐색 비용이 늘 수 있다.

## 정책 mapping 범위

`gh-227-v2-policy-map.csv`는 과거 root447개 원문 단위 각각의 현재 위치와 제안 소유 위치다. 원문 hash는 기존 검증된 원장을 재사용하며, 신규 후보의 target line/hash나 보존 PASS를 주장하지 않는다. root 외39문서 전체의 모든 문장을447개 mapping이 덮는다고 하지 않는다. 이번 allowlist 중 root 외 문서의 새 라우팅·정합성 수정은 설계의 변경 분류표와 실제 diff 검토를 추가 적용한다.

## 미확정 사항

실사용 Before의 정확한 하네스 버전은 아직 확정되지 않았다. Phase1 A0나 Phase2 B0를 자동으로 Before로 선택하지 않는다. 기존 회귀 환경 drift는 미해결이다. 역사적 구현 과제의 승인/정답 분리 fixture는 준비 계약 단계이며 생성·실행하지 않았다.
