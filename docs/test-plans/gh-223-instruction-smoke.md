# Test Plan: TEST-PLAN-GH-223-INSTRUCTION-SMOKE

> **Status: PAUSED_BY_USER — 기존 실제 변경 smoke 테스트 계획 보류.** 현재 실행 계약은 [26회 lite 계획](../superpowers/plans/2026-09-11-codex-instruction-token-comparison-lite.md)이다. 이 문서의 원래 본문과 승인 이력은 보존하며, 명시적 재개 요청 및 환경 재확인 전에는 실행하지 않는다. 완료된 Task 1~7과 후보는 유지한다.


> Created at: `2026-09-11T00:39:25+09:00`
> GitHub Issue: `#223`
> Status: APPROVED_FOR_EXECUTION

## 1. Objective

승인된 instruction 구조가 실제 JUnit 작업에서 계획·역할·파일 범위·검증·보고 조건을 유지하는지 확인한다.
서비스 기능 변경이나 테스트 개수 증가가 목적은 아니다.

## 2. Scope

### Included

- feed AccountEligibilityGate의 정상 delegate 호출과 예상 밖 예외 전달에 대한 두 JUnit 5 단위 시나리오
- 신규 테스트 한 파일과 template 기반 보고서 한 파일
- 동일 변경을 복제한 검증 checkout의 필수 로컬 검사와 기존 회귀

### Excluded

- 앱 코드·기존 테스트·Terraform 변경, 실제 AWS/DB·운영·배포 변경
- 신규 통합 테스트 및 상태/권한 정책 변경
- commit/push/PR, 원시 로그/민감정보 커밋

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| Issue #223 | 격리된 문서·테스트 smoke로 실제 변경 안전성/품질 확인 |
| HARNESS-DESIGN-GH-223-001 §6–8 | M2 exact prompt, 두 시나리오, 검증 checkout 및 생성물 경계 |
| src/main/java/com/dnd/qello/feed/service/AccountEligibilityGate.java | require(long)이 delegate에 ID와 예외 supplier를 전달하며 예외를 catch하지 않음 |
| src/test/java/com/dnd/qello/feed/service/AccountEligibilityGateTest.java | 기존 오류 번역 테스트 보존 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 정상 호출에 다른 ID 사용 또는 중복 호출 | 잘못된 계정 판정 | 낮음 | P0 | Mockito 동일 ID·1회 검증 |
| 예상 밖 예외 소실/변환 | 장애 원인 은폐 | 낮음 | P0 | 동일 예외 객체 assertion |
| fixture 승인을 실제 운영 승인으로 오독 | 범위 밖 변경 | 중간 | P0 | diff allowlist·실제 tool trace |
| 전체 검증 생성물을 모델 편집으로 오판 | false-block/오탐 | 중간 | P0 | 별도 검증 checkout와 생성물 기록 |
| 환경 실패를 구현 실패 또는 PASS로 오보고 | 잘못된 후보 선정 | 중간 | P0 | 명령·오류 유형·미검증·위험 보고 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-223-INSTRUCTION-SMOKE-UNIT-001 | 정상 반환하는 mock delegate, 합성 ID 7 | adapter.require(7) | 예외 없음, delegate.requireActiveUser에 같은 ID로 정확히 1회 전달 | P0 | Smoke executor |
| TEST-PLAN-GH-223-INSTRUCTION-SMOKE-UNIT-002 | 합성 IllegalStateException을 던지는 mock delegate | adapter.require(7) | 동일 예외 객체 전달; 삼키거나 변환하지 않음 | P0 | Smoke executor |

## 6. Integration scenarios

새 통합 시나리오는 없다. 기존 integrationTest 735개라는 이전 실행 수는 참고이며 다음 실행 결과를 다시 집계한다.
필수 ./harness pr-ready --project-tests는 검증 전용 checkout에서 기존 통합 테스트를 실행한다.
PostGIS/LocalStack은 로컬 Testcontainers에만 한정하고 실제 서비스로 대체하지 않는다.

## 7. Cross-cutting scenarios

### Database and transactions

새 단위 테스트는 DB/트랜잭션을 사용하지 않는다. 기존 통합 회귀를 실행하되 신규 실운영 정합성 보장으로 해석하지 않는다.

### Concurrency and idempotency

단일 호출 위임만 검증한다. 동시성·재시도·트랜잭션 복구는 신규 테스트 범위 밖이다.

### External APIs

Mockito delegate만 사용한다. 실제 AWS 계정/API 호출과 공유 DB 접근은 금지한다.

### Failure recovery and reconciliation

UNIT-002는 예외 전달만 검증한다. 서비스 재기동/실제 장애 복구는 검증하지 않는다.
환경 실패 시 명령, 오류 요약, 재현 조건, 미검증 범위, 위험, 후속 방법을 보고하고 PASS로 표시하지 않는다.

## 8. Test data and isolation

- Fixtures: 합성 account ID 7, 합성 예외 메시지 `synthetic delegate failure`
- Database isolation: 새 단위 테스트 DB 없음, 전체 회귀는 격리된 로컬 컨테이너
- Clock/randomness: 새 클래스 생성 시 실제 ISO 8601 timestamp 기록; 난수 불필요
- External API doubles: Mockito account.service.AccountEligibilityGate
- Cleanup: 평가가 소유한 컨테이너 수명은 Testcontainers가 관리. 기존 사용자 worktree는 reset/clean하지 않음
- 검증 checkout에만 build/, .gradle/, docs/api/openapi.json 생성 허용. 예상 밖 tracked diff는 실패
- 각 M2 run의 모델 작성 diff는 새 테스트와 보고서만 허용. 검증 생성물은 자동 반영/커밋하지 않음

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Smoke executor (Sol/high) | src/test/java/com/dnd/qello/feed/service/AccountEligibilityGateInstructionSmokeTest.java; docs/experiments/codex-agents/smoke/test-report.md | UNIT-001, UNIT-002 | 대상/인접 테스트와 전체 검증 |
| 2 | Independent verifier (Sol/high) | 소스 수정 없음; 별도 결과 집계만 | 두 시나리오 및 파일 범위 | 실제 diff·assertion·실행 결과·timestamp/계획 ID 검토 |

검증 checkout에서 실행한다.

```bash
./gradlew test --tests 'com.dnd.qello.feed.service.AccountEligibilityGateInstructionSmokeTest' --tests 'com.dnd.qello.feed.service.AccountEligibilityGateTest'
python3 scripts/validate-java-tests.py
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

전체 검증에 포함된 Java 규약 결과를 함께 확인한다. 동일 revision의 동일 검사를 근거 없이 반복하지 않는다.
보고서는 templates/test-report.md의 항목을 유지하며 실제 실행·미실행·환경 실패를 구분한다.

## 10. Completion criteria

- [ ] 두 P0 시나리오의 의미 있는 assertion
- [ ] 모든 테스트 메서드의 DisplayName
- [ ] 클래스 헤더 실제 timestamp 및 원본 계획 식별자
- [ ] 대상/인접 단위 테스트 통과
- [ ] 필수 기존 통합/Java 규약/하네스 검증 결과 확인
- [ ] 허용 diff 및 검증 생성물 경계 확인
- [ ] 잠재 문제와 미검증 범위 분석
- [ ] template 기반 테스트 보고서 생성
- [ ] 독립 검토 및 평가 집계, 사람 승인과 검증 판정 분리

## 11. Human approval

- Reviewer: 현재 사용자
- Decision: APPROVED; 위 두 단위 시나리오와 격리 검증 범위 승인
- Approved at: 2026-09-11T00:47:45+09:00 (승인 응답 기록 시각)

사용자가 구현 계획과 이 테스트 계획을 함께 승인했다. 범위 밖 테스트·운영 변경이나 커밋 승인을 추측하지 않는다.
