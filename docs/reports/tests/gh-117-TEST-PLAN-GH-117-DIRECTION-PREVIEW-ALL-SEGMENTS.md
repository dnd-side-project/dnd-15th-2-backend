# Test Report: TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS

> Created at: `2026-08-12T01:10:06+09:00`
> GitHub Issue: `#117`
> Branch: `feat/gh-117-direction-preview-all-segments`
> Commit: `94c0145`

## 1. Executive summary

- Result: `PASS`
- Tested scope: #117 unit/SQL boundary/PostgreSQL-PostGIS integration 및 기존 방향 회귀
- Unverified scope: Controller/JSON serialization, production deployment, query plan/explain 비용 증거
- Release recommendation: 필수 harness 검증과 독립 리뷰 후 진행

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | 21.0.12 |
| Spring Boot | 3.5.16 |
| Database | PostgreSQL 16 / PostGIS 3.5 Testcontainers |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./harness test-run --id TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS` unit phase | PASS | 226 | 4초 | Gradle `test` BUILD SUCCESSFUL |
| `./harness test-run --id TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS` integration phase | PASS | 231 | 2분 7초 | Gradle `integrationTest` BUILD SUCCESSFUL |
| `./gradlew test --tests 'com.dnd.qello.direction.*'` | PASS | 방향 단위 suite | 1초 | 신규 service/boundary 및 기존 direction unit 통과 |
| `./gradlew integrationTest --tests com.dnd.qello.DirectionPreviewIntegrationTest` | PASS | 4 | 8초 | 실제 PostgreSQL/PostGIS Testcontainer 통과 |
| `./gradlew integrationTest --tests com.dnd.qello.DirectionPostgisPersistenceIntegrationTest --tests com.dnd.qello.DirectionRecipientSelectionIntegrationTest` | PASS | 기존 방향 회귀 | 12초 | 기존 단일-sector/eligibility 회귀 통과 |
| `./harness check` | PASS | 정책/secret/JUnit/workflow 검사 | 1초 | Harness checks passed |
| `npm run hooks:validate` | PASS | Husky 정책 검사 | 0.1초 | Husky validation passed |
| `./harness pr-ready --project-tests` | PASS | 전체 project test gate | 2분 18초 | Local PR readiness checks passed |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-001 | PASS | `DirectionPreviewServiceTest.fillsMissingSegmentsWithZeroInPolicyOrder` | 전체 segment 순서와 zero-fill 확인 |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-004~006 | PASS | `DirectionPreviewServiceTest` | privacy-safe model, inactive scheme, 거리 범위 검증 |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-002~003, UNIT-007 | PASS | `DirectionPreviewPersistenceBoundaryTest` 및 기존 `DirectionDomainTest` | half-open/wrap, PostGIS SQL boundary, 단일 query source 확인 |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-INT-001 | PASS | `DirectionPreviewIntegrationTest.aggregatesAllActiveSegmentsAndZeroFillsEmptySegments` | 한 query 결과의 전체 segment/0 count |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-INT-002~003 | PASS | `DirectionPreviewIntegrationTest.appliesHalfOpenBoundariesWithoutDuplicateCounts`, `handlesInternationalDateLine` | 실제 PostGIS 경계 직전·직후와 날짜 변경선 |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-INT-005~007 | PASS | `DirectionPreviewIntegrationTest.appliesInclusiveDistanceBoundaries` | min/max inclusive, 만료·수신불가·비활성 제외 |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-INT-004, INT-008 | PASS | `DirectionPreviewIntegrationTest` 및 repository source boundary | geography/단일 JDBC query 경계 확인 |

## 5. Failures and diagnostics

오류의 유형, 재현 조건, 안전하게 정리한 메시지만 기록한다. 로그 원문에
민감정보가 있을 가능성이 있으면 첨부하지 않는다.

첫 통합 실행에서 정확한 22.5° fixture가 `ST_Project`→`ST_Azimuth`의 부동소수점
오차로 이전 segment에 배정되었다. fixture를 실제 scheme 시작각인 0°, 45°, 315°의
직전·직후 값으로 조정했고 재실행에서 4개 모두 통과했다. 정확한 시작각 포함·종료각
제외 규칙은 `DirectionDomainTest`와 SQL boundary test에서 직접 고정한다.

`harness test-run`은 전체 테스트를 통과했지만, 기존 보고서 파일 덮어쓰기 보호로 마지막
보고서 scaffold 단계에서 종료 코드 2를 반환했다. 이 보고서는 실행 결과를 수동 반영했다.

## 6. Potential issues

### Application code

- 단일 `LEFT JOIN` 집계 SQL과 service zero-fill 결과 모델을 추가했다.
- Controller와 JSON serialization은 미구현이며 이번 Issue 범위 밖이다.

### Infrastructure and resource limits

- Apple Silicon에서 amd64 PostGIS image emulation 경고가 있었으나 테스트는 통과했다.

### Database and migrations

- 신규 migration/index는 추가하지 않았다. 기존 PostGIS GiST index를 사용한다.

### Concurrency and idempotency

- preview는 읽기 전용이며 수신 슬롯 예약·recipient/outbox side effect를 만들지 않는다.

### Transactions and event ordering

- Controller, recipient 확정, outbox는 이번 범위에서 실행하지 않았다.

### External APIs

- 외부 API는 범위 밖이며 실행하지 않았다.

### Failure recovery and reconciliation

- DB query 실패를 zero-fill로 위장하는 fallback은 구현하지 않았다.

## 7. Regression and residual risk

- Query plan의 실제 index 선택과 부하 성능은 미검증이다. 운영 RPS/데이터량이 확정되면
  별도 성능 검증이 필요하다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-117-TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS.md`
- CI run: 미실행
- Related ADR:
- PR:

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [x] 실행 결과와 PR 설명이 일치함
