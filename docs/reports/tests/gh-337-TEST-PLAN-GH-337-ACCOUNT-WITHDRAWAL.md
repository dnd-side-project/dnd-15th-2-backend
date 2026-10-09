# Test Report: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL

> Created at: `2026-10-09T19:06:56+09:00`
> GitHub Issue: `#337`
> Branch: `feat/gh-337-account-withdrawal`
> Commit: `05ff34d6` (main의 `63855a2b` 위로 rebase한 뒤 최종 검증)

## 1. Executive summary

- Result: `PASS`
- Tested scope: 계획의 단위 시나리오 UNIT-001~026, 통합 시나리오 INT-001~019 전부. 기존 단위·통합 테스트 전체 회귀 포함.
- Unverified scope: 배포 설정의 sweep 키(R9), 유예 중 닉네임·프로필 이미지 변경(R10), 탈퇴 작성자 콘텐츠 신고(R11),
  재발급과 만료 처리가 겹치는 경합(6절)
- Release recommendation: 병합 가능. 단, 배포 전 운영 설정에 `qello.worker.scheduling.account-withdrawal-sweep.*`를
  넣어야 유예가 끝난 계정이 실제로 삭제된다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | OpenJDK 21.0.11 (Temurin) |
| Spring Boot | 3.5.16 |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine` |
| Test runner | JUnit 5, Gradle |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./harness pr-ready --project-tests` (최종, rebase 뒤) | PASS | 단위 1,396, 통합 844 (실패 0, 오류 0, 건너뜀 0) | 13분 8초 | 단위·통합 task를 실제로 다시 실행했다. `build/test-results`의 201개·110개 클래스 |
| `./harness check`, `npm run hooks:validate`, `git diff --check` (최종) | PASS | — | — | |
| 개발 중 `./gradlew test --rerun`, `./gradlew integrationTest` | PASS | 1,396 / 844 | 약 1분 / 12분 55초 | 커밋 전 작업 트리 |
| `./harness test-run --id TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL` | PASS | 위와 같음 | 2초 | 입력이 같아 Gradle이 직전 결과를 재사용했다 |
| `./gradlew javaConventionCheck`, `scripts/validate-java-tests.py` | PASS | 336개 파일 | — | |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001~007 | PASS | `AccountWithdrawalTest` | 철회 경계는 예정 시각 −1초·같음·+1초 |
| UNIT-008 | PASS | `DeviceCredentialTest#revoke*` | |
| UNIT-009 | PASS | `DeviceTokenServiceTest#reissuesForActiveAndWithdrawalPendingAccounts` | BLOCKED·DELETED는 기존 테스트 그대로 |
| UNIT-010~013 | PASS | `AccountWithdrawalServiceTest` | 실제 정리 구현 3개를 연결한 테스트 추가 |
| UNIT-014 | PASS | `AccountWithdrawalCompletionServiceTest` | 계획은 UNIT-010~014를 한 클래스에 두었으나 분리했다 |
| UNIT-015 | PASS | `AccountStatusServiceTest` | |
| UNIT-016 | PASS | `AnswerPublicationBlockCheckerTest` | 사유 코드 30자 이하 확인 |
| UNIT-017 | PASS | `AccountWithdrawalSweepWorkerTest` | |
| UNIT-018~019 | PASS | `DirectionMatchingWorkerTest` | 기존 시나리오 helper에 발신자 ACTIVE mock 추가(setup만) |
| UNIT-020 | PASS | `PostAnswerApiMockMvcTest#answersMarkWithdrawnAuthorsWithoutNickname` | |
| UNIT-021 | PASS | `AccountWithdrawalControllerMockMvcTest` | |
| UNIT-022 | PASS | `ProductionConventionAuditTest` | 7절 참고 |
| UNIT-023 | PASS | `AccountWithdrawalPropertiesTest`, `RateLimitDefaultsTest` | |
| UNIT-024 | PASS | `RecipientNotificationFanOutWorkerTest`, `NotificationFanOutWorkerTest` | |
| UNIT-025 | PASS | `WorkerSchedulingPropertiesTest`, `WorkerSchedulingConfigurationTest`, `CoreWorkerScheduledAdapterTest` | |
| UNIT-026 | PASS | `FlywayMigrationContractTest`, `AccountJpaMapperTest` | |
| INT-001 | PASS | `FlywayMigrationIntegrationTest`, `NotificationPreferenceMigrationIntegrationTest`, `AccountPersistenceIntegrationTest` | 실제 DB에서 최신 버전 33, CHECK 134, 인덱스 71, 적용 수 9 |
| INT-002, 003, 005, 007, 008, 009, 016 | PASS | `AccountWithdrawalIntegrationTest` | INT-003 코드 `DIR-APP-007`·`ANS-APP-003`·`DIR-APP-007`, INT-009 `ACC-APP-002` |
| INT-004 | PASS | `DirectionRecipientSelectionIntegrationTest#excludesWithdrawalPendingCandidates` | |
| INT-006 | PASS | `DirectionMatchingWorkerIntegrationTest#completesMatchingWithoutRecipientsWhenSenderIsNotActive` | WITHDRAWAL_PENDING, BLOCKED |
| INT-010~012, 015 | PASS | `AccountWithdrawalSweepIntegrationTest` | INT-010은 예정 시각과 정확히 같은 계정도 완료됨을 확인 |
| INT-013, 014, 018 | PASS | `AccountWithdrawalConcurrencyIntegrationTest` | 6절 참고 |
| INT-017 | PASS | `PostAnswerQueryIntegrationTest#hidesNicknameOfWithdrawnAuthorsButKeepsAnswers` | 유예 중 작성자 답변은 API가 막아 SQL로 넣었다 |
| INT-019 | PASS | 전체 통합 실행, `CoreWorkerSchedulingIntegrationTest` | 새 adapter를 두 프로파일 목록에 추가 |

## 5. Failures and diagnostics

- 최종 실행에 실패는 없다.
- 커밋할 때 pre-commit 훅이 실패했다. 별도 작업 폴더(git linked worktree)에서 커밋하면 훅이 받은 `GIT_DIR`·
  `GIT_INDEX_FILE`이 절대 경로라, 훅 안에서 도는 `ChangedJavaTypesTest`와 `ChangedJavaTypes`가 임시 저장소가 아니라
  실제 저장소를 대상으로 git을 실행했다. 실제 index에 fixture가 들어갔고, 테스트의 임시 커밋은 husky 훅에 걸려 실패했다.
  ref는 바뀌지 않았다. 두 파일의 git 실행에서 `GIT_*` 환경 변수를 지우도록 고쳤다(사용자 승인, 범위 밖 수정).
  테스트 환경 문제이고 이번 기능 구현과는 무관하다.
- 커밋할 때만 도는 checkstyle 메서드 길이 검사(50줄)가 두 테스트 메서드를 막았다. 단언은 그대로 두고 헬퍼로 나눴다.
  `WorkerSchedulingPropertiesTest`의 메서드는 원래 91줄이었다.
- 개발 중 `ProductionConventionAuditTest#splitsMissingReadOnlyAndClassWrite`가 `IllegalArgumentException`으로 실패했다.
  A7로 `DeviceTokenService`가 바뀌어 class write Service가 하나도 남지 않았고, AssertJ `doesNotContainAnyElementsOf`가
  빈 목록을 거절했다. 구현 문제가 아니라 단언 방식의 한계라서 "두 목록이 겹치지 않는다"는 의미를 유지한 채 교집합
  비교로 바꿨다.

## 6. Potential issues

### Application code

- 동시에 들어온 같은 탈퇴 요청 중 하나는 409 `CMN-DOM-003`(공통 낙관적 잠금 충돌)을 받는다. A8로 허용한 동작이지만
  `ACC-DOM-004`가 아니므로 앱 재시도 처리에 공유해야 한다.
- 푸시 등록 API는 계정 상태를 보지 않아 유예 중 다시 등록된 기기가 ACTIVE로 남는다. 푸시 발송 단계가 수신자 상태로
  취소하고 만료 처리가 다시 해지한다(INT-010, INT-018).
- 유예 중 닉네임·프로필 이미지 변경은 막지 않는다(R10, 범위 밖).

### Infrastructure and resource limits

- sweep adapter는 기본값이 꺼져 있다. 배포 설정에 `account-withdrawal-sweep`의 `enabled`, `fixed-delay`,
  `batch-size`가 없으면 유예가 끝나도 계정이 삭제되지 않는다(R9). worker pool 크기도 함께 검토해야 한다.

### Database and migrations

- V33은 `ck_user_account_status`를 같은 이름으로 다시 만들고 CHECK 1개와 부분 인덱스 1개를 더한다. 기존 행은 모두
  ACTIVE·BLOCKED·DELETED이고 `withdrawal_requested_at`이 NULL이라 새 CHECK를 만족한다.
- CHECK를 더하는 `ADD CONSTRAINT`는 `user_account` 전체를 검사한다. 지금 데이터 규모에서는 문제가 없지만 행이 많아지면
  `NOT VALID` 후 `VALIDATE`로 나누는 방법이 있다.

### Concurrency and idempotency

- INT-013은 철회와 만료 처리가 커밋 전에 반드시 만나도록 테스트 전용 clock과 정리 hook으로 동기화했고 10회 반복했다.
  철회가 이긴 회차에서는 만료 처리가 낙관적 잠금 충돌로 실패했고, 이미 폐기했던 자격증명과 푸시 기기가 함께
  rollback되어 ACTIVE로 남았다. 섞인 상태는 한 번도 없었다(R1).
- INT-014는 매번 한쪽만 성공했고 계정 version은 1만 올랐다.
- INT-018은 두 순서 모두 10초 안에 끝났고 교착이 없었다.
- 재발급이 자격증명 ACTIVE·계정 유예 중을 읽은 직후 만료 처리가 커밋하면 재발급은 토큰을 발급한다(최대 30분 유효).
  `@DynamicUpdate`로 폐기 상태가 ACTIVE로 되돌아가지는 않고 쓰기 API는 ACTIVE 검사로 막힌다. R13과 같은 수준으로
  수용한다. 이 경합은 테스트하지 않았다.

### Transactions and event ordering

- 탈퇴 요청의 JDBC 정리(푸시 해지, 위치 삭제)는 JPA 계정 변경보다 먼저 실행되지만 같은 트랜잭션이라 함께
  rollback된다(INT-015에서 정리 직후 실패를 주입해 확인).
- 위치 갱신이 탈퇴 요청과 겹치면 ACTIVE 검사를 통과한 위치가 탈퇴 커밋 뒤 남을 수 있다. 매칭 후보 조회가 ACTIVE
  계정만 보고 위치는 24시간 뒤 만료되므로 매칭에는 영향이 없다. 테스트하지 않았다.

### External APIs

- FCM은 호출하지 않는다. 푸시 해지는 DB 행만 바꾼다.

### Failure recovery and reconciliation

- 만료 처리에 실패한 계정은 유예 중으로 남아 다음 sweep에서 다시 후보가 된다. 한 계정의 실패가 같은 batch의 다른
  계정을 막지 않는다(UNIT-017).
- 예정 시각이 지났지만 sweep이 아직 돌지 않은 계정은 철회할 수 없고 다음 sweep이 완료한다(INT-012).

## 7. Regression and residual risk

- 기존 테스트의 단언 의미는 바꾸지 않았다. 바꾼 단언은 다음뿐이다.
  - `ProductionConventionAuditTest`: `DeviceTokenService`가 class write 목록에 있다는 단언을 "없다"로 바꿨다(A7 승인).
    같은 클래스의 겹침 검사는 5절 이유로 교집합 비교로 바꿨다. 승인된 한 줄 범위를 조금 넘는 변경이다.
  - 마이그레이션 수치(INT-001 승인): `FlywayMigrationIntegrationTest`, `NotificationPreferenceMigrationIntegrationTest`.
- 커밋 과정에서 바꾼 테스트: `ChangedJavaTypes`, `ChangedJavaTypesTest`(git 환경 변수), `WorkerSchedulingPropertiesTest`,
  `AccountWithdrawalConcurrencyIntegrationTest`(메서드 길이). 마지막 둘은 클래스 단위로 다시 실행했고 최종 전체 실행에도
  포함된다.
- 컴파일만 고친 파일: 새 port 메서드를 fake에 추가하거나 생성자 인자를 더했다. `NicknameRegistrationServiceTest`,
  `ProfileServiceTest`, `DeviceRegistrationServiceTest`, `OperatorLoginServiceTest`, `DirectionPresenceServiceTest`,
  `PushDeliveryDispatchScheduledAdapterTest`, `RecipientNotificationFanOutWorkerIntegrationTest`(계획 소유 목록 밖).
- spotless가 바뀐 파일 전체를 다시 포맷해 기존 파일 diff에 들여쓰기 변경이 섞여 있다. 공백을 무시하면 통합 테스트의
  실제 변경은 259줄 추가, 41줄 삭제다.
- `docs/api/openapi.json`은 `OpenApiSpecificationIntegrationTest`가 다시 만들었다. 경로·스키마는 추가만 있고 삭제는
  없다. 추가: `/api/v1/users/me/withdrawal`(POST·DELETE), `AccountWithdrawalResponse`, `DeviceTokenResponse.accountStatus`,
  `AnswerCard.authorWithdrawn`.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-337-TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL.md`
- CI run: PR 생성 후 확인한다
- Related ADR: `docs/adr/0002-jpa-jdbc-boundary.md`, `docs/product/AUTH_DESIGN.md` 4.6·4.7절
- PR: 미생성

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [ ] 실행 결과와 PR 설명이 일치함
