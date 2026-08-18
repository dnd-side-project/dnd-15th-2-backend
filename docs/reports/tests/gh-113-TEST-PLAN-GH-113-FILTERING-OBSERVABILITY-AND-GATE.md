# Test Report: TEST-PLAN-GH-113-FILTERING-OBSERVABILITY-AND-GATE

> Created at: `2026-08-18T22:10:00+09:00`
> GitHub Issue: `#113`
> Branch: `feat/gh-113-filtering-observability-gate`
> Commit: 이 보고서를 포함한 커밋 직전 기준 `origin/main` 4b8bc4e 위의 작업 브랜치

## 1. Executive summary

- Result: `PASS`
- Tested scope: `operator_action_audit` 스키마와 append-only 계약, 필터링 운영자
  경로 전체의 감사 배선과 `reason` 필수화, metric tag 허용목록과 계측 실패
  격리, production 활성화 게이트의 fail-closed 동작.
- Unverified scope: **판정 경로(latency·판정 분포·queue 체류·deadline 경과)의
  실제 계측은 배선하지 않았다.** 아래 5절에 이유를 적었다. metric exporter,
  경보 규칙, 대시보드도 도구 미정이라 다루지 않았다.
- Release recommendation: 병합 가능. 다만 이 변경만으로 관측 데이터가 프로세스
  밖으로 나가지는 않는다 — exporter가 없다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 17.0.8 LTS |
| Spring Boot | 3.5.16 |
| Database | Testcontainers로 기동한 PostGIS 16 계열 테스트 컨테이너 |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` | PASS | 636 (실패 0, 건너뜀 0) | 약 9초 | `build/test-results/test` |
| `./gradlew integrationTest` | PASS | 482 (실패 0, 건너뜀 0) | 약 6분 1초 | `build/test-results/integrationTest` |
| `./harness check` | PASS | 민감정보 954파일·JUnit 정책 166파일·컨벤션·workflow·label·Husky | — | 명령 출력 |
| `git diff --check` | PASS | — | — | 공백 오류 없음 |

이번 이슈가 추가한 테스트는 단위 14건(`OperatorActionAuditTest` 3,
`FilteringMetricsTest` 6, `FilteringProductionGateTest` 4, 그리고 기존 파일의
보강 1), 통합 5건(`OperatorActionAuditIntegrationTest`)이다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `FilteringMetricsTest.rejectsTagKeyOutsideAllowlist` | `user_id`·`raw_content` 거부 |
| UNIT-002 | PASS | `FilteringMetricsTest.acceptsAllowedTag` | 허용목록에 식별자 키가 없음도 확인 |
| UNIT-003 | PASS | `FilteringMetricsTest.rejectsUnusableTagValues` | 빈 값·60자 초과·홀수 쌍 |
| UNIT-004 | PASS | `OperatorActionAuditTest.requiresAllFourAuditElements` | actor·행위·대상·시간 |
| UNIT-005 | PASS | `OperatorActionAuditTest.rejectsBlankReasonAndPolicyVersion` | `OperatorReason`도 함께 |
| UNIT-006 | PASS | `OperatorActionAuditTest.rejectsOverlongText` | 근거 500자·대상 키 200자 |
| UNIT-007 | PASS | `FilteringProductionGateTest.allowsActivationWhenEveryConfirmationIsPresent` | |
| UNIT-008 | PASS | `FilteringProductionGateTest.refusesActivationWhenAnyConfirmationIsMissing` | 누락 항목명이 메시지에 |
| UNIT-009 | PASS | `FilteringProductionGateTest.doesNotBlockWhenActivationIsNotRequested` | |
| UNIT-010 | PASS | `FilteringMetricsTest.recordsVerdictByReleaseAndActualModel` | |
| UNIT-011 | PASS | `FilteringMetricsTest.countsLogicalAttemptAndProviderCallSeparately` | |
| UNIT-012 | PASS | `FilteringMetricsTest.instrumentationFailureNeverPropagates` | 계측 실패가 전파되지 않음 |
| UNIT-013 (추가) | PASS | `FilteringMetricsTest.everyRecordedMeterUsesAllowedTagsOnly` | 기록된 meter 전수 검사 |
| UNIT-014 (추가) | PASS | `FilteringProductionGateTest.reportsEveryMissingConfirmation` | 누락 항목 전부 보고 |
| INT-001 | PASS | `OperatorActionAuditIntegrationTest.appliesOperatorActionAuditSchema` | 컬럼 8·CHECK 4·인덱스 2 |
| INT-002 | PASS | `OperatorActionAuditIntegrationTest.promotionRecordsAuditInSameTransaction` | 4요소 전부 확인 |
| INT-004 | PASS | `OperatorActionAuditIntegrationTest.distinctOperatorPathsRecordDistinctActionTypes` | 5개 경로 구분 |
| INT-005 | PASS | `OperatorActionAuditIntegrationTest.auditCannotRunOutsideCallerTransaction` | MANDATORY 전파 |
| INT-006 | PASS | `OperatorActionAuditIntegrationTest.auditLedgerIsAppendOnlyAndRejectsBlankReason` | |
| INT-012 | PASS | 기존 통합 테스트 12개 파일 | `reason` 추가 후 회귀 없음 |

미실행 시나리오는 6절과 7절에 적었다.

## 5. Failures and diagnostics

최종 실행에서 실패한 테스트는 없다. 구현 도중 발생했다가 해결한 실패는 넷이다.

1. **스키마 인벤토리 가드 4종.** `operator_action_audit` 하나가 늘면서
   테이블 수(48→49), 마이그레이션 수(19→20), CHECK 제약 수(117→121), 인덱스
   수(62→64) 단언이 함께 깨졌다. 전부 "스키마가 사람 모르게 늘지 않는가"를 묻는
   장치이므로 값을 갱신하고 근거를 주석으로 남겼다.
2. **`FlywayMigrationContractTest`의 파일 목록 정렬.** `V20`을 `V19` 뒤에
   넣었더니 실패했다. 실제 디렉터리 정렬에서는 `V1__create...` 다음에 `V20`이
   온다(문자열 정렬이라 `V19` < `V1__` < `V20` < `V2__`). 실제 순서에 맞췄다.
3. **운영자 endpoint 요청 본문 6종.** `reason` 필수화로 release 전이·수동 검토
   결정·이의제기 결정·snapshot health 승인 요청이 400이 됐다. 테스트 payload에
   근거를 추가했다 — 제품 코드의 결함이 아니라 의도한 계약 변경이다.
4. **`rollback` 대상 오해.** 새로 쓴 감사 통합 테스트가 방금 승격한 release를
   바로 `rollback`하려다 `INVALID_RELEASE_STATUS`로 실패했다. `rollback`은 이미
   `ROLLED_BACK`으로 내려간 release를 다시 올리는 경로라, 두 번째 release를
   승격해 첫 번째를 내린 뒤에야 호출할 수 있다. 테스트를 실제 계약에 맞췄다.

## 6. Potential issues

### Application code

- **판정 경로 계측이 비어 있다.** `FilteringMetrics`는 만들었지만
  `ModerationPipelineService`에 배선하지 않았다. 그 클래스는 의도적으로 Spring
  bean이 아니고(`PolicyEngine` 구현체 미정), 호출자가 직접 생성해 쓴다. 지금
  계측을 넣으면 손으로 만든 생성 지점 10곳을 모두 고쳐야 하는데, 정작 그것을
  구동하는 프로덕션 경로가 없어 관측할 대상이 없다. pipeline이 bean으로 배선되는
  후속 이슈에서 함께 넣는 편이 맞다. 그때까지 `recordPipeline`·`recordVerdict`·
  `countLogicalAttempt`·`countProviderCall`·`recordQueueDwell`·
  `countDeadlineElapsed`·`countManualDecision`·`countAppealDecision`·
  `countSlackDelivery`는 테스트에서만 호출된다.
- `FilteringMetrics`가 모든 예외를 삼킨다. 계측이 판정을 실패시키지 않게 하는
  의도지만, 허용목록 위반이 운영에서 조용히 지표를 누락시킬 수 있다.
  `everyRecordedMeterUsesAllowedTagsOnly`가 개발 단계에서 잡도록 둔 이유다.

### Infrastructure and resource limits

- actuator를 추가했지만 `management.endpoints.web.exposure.include`를 비우고
  `enabled-by-default=false`로 뒀다. 노출 설정을 되돌리면 관리 endpoint가 즉시
  열리므로, 환경별 설정에서 이 두 값을 덮어쓰지 않는지 배포 시 확인해야 한다.

### Database and migrations

- V20은 신규 테이블 생성만 하고 기존 테이블을 건드리지 않는다. 기존 행 보정
  문제가 없다.
- `operator_action_audit`은 대상 도메인에 FK를 걸지 않는다. 대상 행이 지워져도
  감사 이력은 남아야 하기 때문이다. 대신 참조 무결성은 보장되지 않으므로,
  존재하지 않는 대상을 가리키는 감사 행이 생길 수 있다.
- 감사 원장에 보관 기간·삭제 정책이 없다. 무한히 쌓인다. 보관 정책은 이슈가
  명시적으로 제외한 항목이라 이번에 정하지 않았다.

### Concurrency and idempotency

- 감사는 append-only라 경합해도 서로를 덮어쓰지 않는다. 유일성 제약을 두지
  않은 것도 의도다 — 같은 운영자가 같은 행위를 두 번 하면 두 행이 남는 것이 맞다.

### Transactions and event ordering

- `OperatorActionAuditRecorder`는 `Propagation.MANDATORY`다. 호출자 트랜잭션에
  반드시 합류하며, 트랜잭션 없이 호출되면 실패한다(INT-005). 감사가 자체
  트랜잭션을 열면 "결정은 커밋됐는데 근거는 롤백된" 조합이 생긴다.
- `SnapshotEmergencyMigrationService`는 감사를 두 행 남긴다 — registry 서비스가
  남기는 승격 감사와, 그 승격이 긴급 이관이었다는 상위 행위 감사다. 승격 감사만
  보면 통상 승격과 구분할 수 없기 때문이다.

### External APIs

- 외부 호출을 새로 만들지 않았다. exporter가 없어 지표는 프로세스 안에만 머문다.

### Failure recovery and reconciliation

- 게이트는 fail-closed다. 확인 항목이 하나라도 비면 기동이 실패한다(UNIT-008).
  경고 로그만 남기고 뜨는 방식을 택하지 않은 이유는 5절이 아니라
  `docs/filtering-production-gate.md` 1절에 적었다.

## 7. Regression and residual risk

- **운영자 API 계약이 바뀌었다.** release 전이 5종, 수동 검토 결정, 이의제기
  결정·연장, snapshot health 승인이 이제 `reason`을 요구한다. 백오피스 클라이언트가
  있다면 함께 고쳐야 한다. `docs/api/openapi.json`을 재생성해 반영했다.
- 기존 통합 테스트 12개 파일의 호출부를 갱신했다. 전부 통과한다.
- 남은 위험: 감사 배선이 필터링 도메인에 한정된다. 질문 제안 검토
  (`/admin/questions/proposals/**`)와 신고 처리도 운영자 행위지만 `#113`의 부모
  이슈 범위 밖이라 배선하지 않았다. 같은 테이블을 쓰도록 설계했으므로 후속
  이슈에서 배선만 하면 된다.
- 남은 위험: `policy_version`이 행위마다 다른 출처를 쓴다. release는
  `release:<id>`, 수동 검토는 case의 `priorityPolicyVersion`, 이의제기는
  `appeal-window-v1`, snapshot health와 긴급 이관은 `unversioned`다. 전역 단일
  버전이 없으므로 여러 정책을 가로질러 집계하려면 해석 규칙이 필요하다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-113-TEST-PLAN-GH-113-FILTERING-OBSERVABILITY-AND-GATE.md`
- 운영 문서: `docs/filtering-production-gate.md`
- CI run: 로컬 실행 결과만 존재한다. GitHub Actions 실행은 PR 생성 후 확인한다.
- Related ADR: 없음. 설계 판단은 `TASK.md`의 Design decisions 절에 기록했다.
- PR: 본 브랜치의 Pull Request

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (판정 경로 계측, exporter·경보, INT-003·INT-007~011)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 — 아직 생성하지 않았다. pipeline
      계측·스케줄러 배선·Slack 구현체·감사 보관 정책은 후속 이슈가 필요하다.
- [x] 실행 결과와 PR 설명이 일치함
