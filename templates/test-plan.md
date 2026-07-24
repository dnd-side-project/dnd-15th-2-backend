# Test Plan: <TEST-PLAN-ID>

> Created at: `<CREATED-AT>`
> Jira: `<JIRA-KEY>`
> GitHub Issue: `#<GITHUB-ISSUE>`
> Status: Draft

## 1. Objective

검증할 사용자 가치와 실패 시 위험을 작성한다.

## 2. Scope

### Included

-

### Excluded

-

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| Jira | |
| GitHub Issue | |
| ADR / API / schema | |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| | | | | |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| <TEST-PLAN-ID>-UNIT-001 | | | | P0 | |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| <TEST-PLAN-ID>-INT-001 | | | | | |

## 7. Cross-cutting scenarios

### Database and transactions

-

### Concurrency and idempotency

-

### External APIs

-

### Failure recovery and reconciliation

-

## 8. Test data and isolation

- Fixtures:
- Database isolation:
- Clock/randomness:
- External API doubles:
- Cleanup:

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | | | | |

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer:
- Decision:
- Approved at:
