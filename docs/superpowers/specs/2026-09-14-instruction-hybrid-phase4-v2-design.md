# After-v1 공통 지침 조합 설계

- Design ID: HARNESS-DESIGN-GH-227-002
- Issue: #227 / Task: GH-227-INSTRUCTION-ROLLOUT-PHASE4
- 상태: DRAFT_FOR_REVIEW. 감사·설계 요청 범위에서 작성했으며 구현 승인을 나타내지 않는다.
- 목적: 사용자별 워크플로우를 허용하는 공통 지침을 만들고 고정 조건에서 기존 대비 전체 토큰 변화를 확인한다.

## 선택안

root의 안전·승인·권한·역할·검증 계약은 유지한다. AWS 설계 상세와 Terraform 주석의 원문을 조건부 reference로 옮기고 root에서 해당 작업 전에 명시적으로 읽도록 연결한다. Java 관례에는 production 경로 진입점을 추가한다. 경로 없는 분석은 작업 목적별 문서 선택표를 사용한다. 범용 Skill 신설과 모델 라우팅은 포함하지 않는다.

패키지별 AGENTS를 일괄 생성하면 같은 계층 규칙이 반복되고 읽기 비용이 늘어난다. 반대로 모든 규칙을 root에 두면 무관한 상세가 매번 필요하다. 기존 문서를 소유 원본으로 유지하고 짧은 라우터를 필요한 곳에 둔다.

## 후보 파일 allowlist

| 파일 | 작업 | 정확한 범위 |
| --- | --- | --- |
| AGENTS.md | 수정 | §4.3~4.5, §5 본문을 아래 reference로 원문 이동하고 필수 진입 링크로 교체. §1.8 뒤 조건부 진입표 추가. 나머지 정책 원문 유지 |
| .agents/skills/harness-infra-design/references/design-policy.md | 생성 | 기존 root §4.3~4.5 전체, 제목·표·상태 정의 포함 |
| .agents/skills/harness-infra-build/references/comment-policy.md | 생성 | 기존 root §5 전체, 예외·금지·예시·주석 언어 포함 |
| .agents/skills/harness-infra-design/SKILL.md | 수정 | 최초 읽기에 design-policy 링크 추가; 나머지 절차·독립 검토 유지 |
| .agents/skills/harness-infra-build/SKILL.md | 수정 | 최초 읽기에 comment-policy 링크 추가; 나머지 절차·권한 유지 |
| infra/AGENTS.md | 생성 | root 계약과 comment-policy 필수 읽기 연결. root보다 권한을 넓히지 않음 |
| src/main/java/AGENTS.md | 생성 | production Java 작업 전 JAVA_CONVENTIONS 원문 참조 및 관련 ADR 선택표 연결 |
| docs/harness/TASK_DOCUMENT_ROUTING.md | 생성 | 아래 조건부 문서 선택표만 작성. 절차/검사/승인을 새로 강제하지 않음 |
| docs/harness/WORKFLOW_SKILLS.md | 수정 | 전체 흐름에서 일반 구현을 테스트 전용 Skill과 구분하고 선택표 링크 추가 |
| docs/harness/DAILY_WORKFLOW.md | 수정 | 역할 선택표에 일반 구현과 요구사항/설계 추가. 모델 지정 없이 root 역할 계약 참조 |
| docs/harness/ARCHITECTURE.md | 수정 | 일반 작업 경로와 선택표 연결. 인프라 그림은 적합 후보 비교 및 Terraform only로 정합성 수정 |

기존 TEST_SCOPED7파일은 snapshot 내용으로 유지하되 root에는 위 후속 변경을 적용한다. 두 test Skill·두 test reference·두 test AGENTS는 이번 추가 수정 대상이 아니다. CLAUDE.md, agents 역할 파일, Java 원본/테스트, 빌드/CI/guard, 전역 Skill과 설정은 후보 수정 대상이 아니다. 문서 산출물/TASK/계획은 실행 기록 파일이며 runtime 효과를 평가할 때 양쪽 동일한 평가용 TASK를 제공한다.

## 제안 라우팅 문구

root 추가 문구:

> 작업 대상이 production Java이면 src/main/java/AGENTS.md를 작업 전에 명시적으로 읽는다. AWS 요구사항·설계는 harness-infra-design의 design-policy, Terraform 작성·검토 및 주석 규칙 질의는 harness-infra-build의 comment-policy를 먼저 읽는다. 경로가 정해지지 않은 분석과 복수 영역 작업은 docs/harness/TASK_DOCUMENT_ROUTING.md에서 필요한 문서만 선택한다. 파일 존재나 cd에 따른 자동 로딩을 가정하지 않는다. 테스트 작업에는 §3을 함께 적용한다.

Java 하위 문구:

> production Java를 수정하거나 코드 적합성을 검토할 때 docs/harness/JAVA_CONVENTIONS.md를 읽고 적용한다. 상세 규칙은 이곳에 복제하지 않는다. 관련 도메인 결정은 docs/harness/TASK_DOCUMENT_ROUTING.md의 조건에 따라 확인한다. 테스트 변경에는 해당 테스트 하위 지침도 적용한다. root의 역할·승인·소유 파일·필수 검증 조건을 유지한다.

문서 선택표:

| 작업 근거/질문 | 읽을 원본 | 적용 주의 |
| --- | --- | --- |
| Java 구현/버그/리팩터링/코드 적합성 | docs/harness/JAVA_CONVENTIONS.md | formatter 실행은 허용 파일 범위 내; 전역 정리 지시 아님 |
| DB 소유·마이그레이션 | docs/adr/0001-database-schema-ownership.md | 운영 변경 승인 대체 불가 |
| persistence·transaction·repository 경계 | docs/adr/0002-jpa-jdbc-boundary.md | 코드/현재 Issue와 함께 판단 |
| 오류 응답 | docs/adr/0003-global-exception-handling.md | API 동작 변경은 승인 범위 확인 |
| 성공 응답 | docs/adr/0005-api-success-response-contract.md | 문서 작업과 동작 변경 구분 |
| 인증 요구/설계 | docs/adr/0006-split-operator-and-device-authentication.md, docs/product/AUTH_DESIGN.md | ADR 상태가 proposed이므로 최신 승인 근거 확인 |
| 온보딩 국가 | docs/product/ONBOARDING_COUNTRY_DESIGN.md | 현재 Issue의 범위 우선 확인 |
| 알림함·푸시 | docs/product/NOTIFICATION_INBOX_DESIGN.md, docs/adr/0008-adopt-fcm-push-delivery-pipeline.md | 관련 기능일 때만 선택 |
| 아직 요구가 모호함 | 현재 요청·Issue 또는 Project draft, 관련 제품 문서의 필요한 절 | 문서 전체 열람이나 Java 규칙 로딩을 일괄 요구하지 않음 |

## 변경 분류와 정책 보존

1. 원문 이동: root447개 단위 중 §4.3~4.5와§5. `gh-227-v2-policy-map.csv`의 source hash와 구현 후 실제 target slice를 비교한다. 조건부 예외의 모든 문맥을 유지한다.
2. 진입 경로 추가: Java·분석·AWS 조건부 라우터. 기존 요구를 발견 가능하게 하지만 실제 읽기는 늘 수 있다. 순수 압축 효과라고 부르지 않는다.
3. 문서 정합성: ARCHITECTURE의 CDK/비교 후보 문구를 root에 일치시킨다. 새 권한/금지 규칙을 만들지 않는다. 최신 실행 계약에 따라 C0원본과 정합성 수정까지 포함한 C1을 직접 비교한다. N0 제안은 제외하며 결과를 전체 개선 묶음의 효과로 명명한다.
4. 보존: root의 구현/Apply/State/인증/비밀/고위험/검증/실패 규칙과 금지 명령은 그대로 남는다. 자신의 권한 변경이나 게이트 완화는 후보에 포함하지 않는다.

## 구현 후 검증과 롤백

- root 원문447개 단위의 누락0, 원문 이동 hash 일치, 링크 존재/도달 조건 확인. 문장 의미를 축약해 동등하다고 추정하지 않는다.
- 일반 Java, 경로 없는 요구사항, 복수 경로, AWS 설계, Terraform 주석 질의, 테스트 혼합의 진입표를 독립 검토한다.
- 실제 diff에서 allowlist 밖 변경0, root 보호 구역 변경0을 확인한다.
- 필수 harness/check/프로젝트 검사와 hooks/diff 검사를 실행한다. 과거 PASS를 새 후보에 승계하지 않는다.
- 승인 후 격리 후보 사본에서만 변경한다. 실패하면 후보 채택을 보류하고 후보 전 snapshot을 기준으로 정확한 해당 변경만 복구한다. 사용자 미커밋 변경을 reset/clean하지 않는다.

## 다음 실행 조건

현재 산출물은 구체적 구현 검토안이다. 기존/개선 fixture 확정, 이전 하네스 버전 확정, 평가 승인 증거와 환경 drift 판정 없이는 유료 평가를 시작하지 않는다. #227 Issue 본문의 로딩 비용 중심 목적과 최신 세션 전체 목표 차이도 실제 구현 계약 확정 시 동기화 대상으로 명시한다. 현재 Issue는 읽기만 했다.

## 기준 확정 후 연결

Before 선택은 사용자 확인으로 완료됐으며 `gh-227-v2-before-manifest.json`을 따른다. 구체적 과제 계약은 `gh-227-v2-task-contracts.md`, 공통 실행 조건은 `gh-227-v2-execution-contract.md`에 기록했다. 위 다음 실행 조건 중 Before 미확정 사항은 해소됐으나 구현·평가 승인은 의미하지 않는다.
