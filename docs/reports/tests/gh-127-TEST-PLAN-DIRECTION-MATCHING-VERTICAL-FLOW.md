# Test Report: TEST-PLAN-DIRECTION-MATCHING-VERTICAL-FLOW

> Created at: `2026-08-18T16:00:00+09:00`
> GitHub Issue: `#127`
> Branch: `test/gh-127-direction-matching-vertical-flow`
> Commit: `357db91` (base) — 신규 파일 4개, 수정 파일 2개 미커밋. 커밋은 `/harness-commit`에서 목적별로 분리한다.

## 1. Executive summary

- Result: `PASS`
- Tested scope: Gap A(E2E 수직 흐름, INT-001~005), Gap C(체인 종단 복구, INT-006~008),
  Gap B(10,000명 성능·규모, PERF-001~003). `./gradlew test`(601건)·`integrationTest`
  (463건, 신규 8건 포함)·`performanceTest`(3건) 전부 통과.
- Unverified scope: 실제 FCM/APNs 푸시 전달(설계상 미구현, `notification_delivery`
  행 생성까지만 관측), 실제 운영 부하, H3·Redis·Kafka, 발신자에게 매칭 실패를
  알리는 자동 복구 경로(관측만 하고 판정하지 않음).
- Release recommendation: 이 브랜치의 변경(테스트 3개 파일 + `build.gradle` 태스크
  분리)은 프로덕션 코드를 건드리지 않으므로 병합 위험이 낮다. 다만 §6에서 발견한
  **10,000명 규모에서 부분 GIST 인덱스가 후보 조회 계획에 선택되지 않는다는 사실**은
  별도 GitHub Issue로 보고해야 한다(완료 조건 부분 미충족, 아래 §7 참조).
  이 발견은 "인덱스가 쓸모없다"는 뜻이 **아니다** — 합성 데이터의 계정:presence
  비율(1:1)과 좌표 분포가 인덱스가 선택될 조건 자체를 만들지 못했다. 정확한 해석과
  선행 재측정 조건은 §6을 따른다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java (Gradle 실행) | OpenJDK 21.0.12 (Homebrew), toolchain 고정 |
| Java (기본 셸) | OpenJDK 25.0.3 (Temurin) — Gradle 빌드 스크립트와 비호환이라 `JAVA_HOME`을 21로 override해 실행 |
| Spring Boot | 3.5.16 |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine` (PostgreSQL 16.14 + PostGIS) |
| Test runner | JUnit 5 (Gradle `test`/`integrationTest`/`performanceTest` 태스크) |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` | PASS | 601 | ~1분 | `build/test-results/test/*.xml` |
| `./gradlew integrationTest` | PASS | 463 (신규 8건 포함) | 3m 54s | `build/test-results/integrationTest/*.xml` |
| `./gradlew performanceTest` | PASS | 3 | ~16s | `build/test-results/performanceTest/*.xml`, 로그 §6 |
| `./harness check` | PASS | — | 즉시 | 정적 검증(secret·JUnit 정책·commit 포맷·workflow·라벨·husky) |
| `./harness pr-ready --project-tests` | PASS | — | ~4분 | `./gradlew check` = `test` + `integrationTest`만 의존, `performanceTest` 미포함 확인 |
| `npm run hooks:validate` | PASS | — | 즉시 | Husky 검증 통과 |
| `git diff --check` | PASS | — | 즉시 | 공백 오류 없음 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| INT-001 | PASS | `DirectionMatchingVerticalFlowIntegrationTest.confirmsAndFansOutThroughTheRealChain` | presence→preview→submit→matching→fan-out 관통, 픽스처 주입 없음 |
| INT-002 | PASS | `...answeringRecipientReleasesSlotExactlyOnce` | 실제 수신함 열람 후 답변 제출·검열 통과(verdict worker)로 슬롯 1→0 |
| INT-003 | PASS | `...skipConfirmationReleasesSlotExactlyOnce` | 넘김 유예 후 sweep 1회 해제, 재실행 시 추가 감소 없음 |
| INT-004 | PASS | `...expirationSweepReleasesSlotExactlyOnce` | 만료 sweep이 체인의 세 수신자 전원을 해제(각자 정확히 1회) |
| INT-005 | PASS | `...neitherOutboxNorNotificationPayloadsExposePreciseCoordinates` | outbox payload·notification 스키마에 좌표 없음 |
| INT-006 | PASS | `DirectionMatchingVerticalFlowRecoveryIntegrationTest.deadMatchingLeavesPostStuckWithoutLeakingSlots` | maxAttempts=1로 DEAD 즉시 재현. 관측: `direction_post`가 `MATCHING`에 영구히 머무름(§6) |
| INT-007 | PASS | `...blockedRecipientKeepsEligibilityWhileNotificationIsSuppressed` | 차단된 수신자도 outcome PROCESSED, 알림만 억제, 수신 자격·슬롯 유지 |
| INT-008 | PASS | `...reclaimingExpiredMatchingLeaseCompletesChainWithoutDuplicateRecipients` | 크래시 시뮬레이션 후 재claim, 중복 수신자 없이 체인 완주 |
| PERF-001 | PASS(수정된 기준) | `DirectionMatchingPerformanceIntegrationTest.previewCandidateCountQueryAvoidsSeqScanAtScale` | Seq Scan 부재만 단언. 실제 접근 경로는 증거로 기록(§6) |
| PERF-002 | PASS(수정된 기준) | `...matchingCandidateSelectionQueryAvoidsSeqScanAtScale` | 위와 동일 |
| PERF-003 | PASS(단언 없음) | `...recordsPreviewAndMatchingLatencyAsEvidence` | preview_ms=356~405, matching_ms=77~83 (머신 편차 범위, 3회 실행) |
| PERF-004(계획서 원안: Gradle 구성) | PASS | `build.gradle` 검토 + `./harness pr-ready --project-tests` 로그 | `check`가 `performanceTest`에 의존하지 않음을 태스크 그래프로 직접 확인 |

## 5. Failures and diagnostics

최종 실행에는 실패가 없다. 구현 과정에서 재현·수정한 테스트 저작 결함 세 가지를
투명성을 위해 기록한다(전부 이 브랜치의 신규 테스트 코드 문제였고, 프로덕션 코드는
건드리지 않았다):

1. **시계 정렬 오류.** `InboxApplicationService`/`AnswerSubmissionApplicationService`는
   주입된 `Clock` 빈을 직접 읽는데, 매칭·fan-out 단계는 `BatchCommand`의 명시적
   `at`(NOW+91초까지)을 썼다. mutable clock을 그 이후로 옮기지 않고 수신함을 열람하려
   해 `방향 시각 순서가 올바르지 않습니다`(matched_at 이전 시각) 예외가 났다. 체인
   단계 사이에 mutable clock을 명시적으로 전진시켜 해결했다.
2. **잘못된 sweep 단언.** INT-004 초안은 만료 sweep이 "정확히 1건만" 해제한다고
   단언했는데, 같은 체인의 나머지 두 후보도 아직 미응답이라 sweep이 셋 다 해제하는 게
   맞는 동작이었다. "batch 결과 건수"가 아니라 "지정한 한 수신자의 카운터가 정확히
   한 번만 감소한다"로 단언을 정정했다.
3. **정리 순서로 인한 FK 위반.** 성능 테스트 `@AfterAll`이 `user_account`를
   `post_recipient`보다 먼저 삭제해 FK 위반이 났다. `post_recipient`→`outbox_event`→
   `post_audience`→`direction_post`→`recipient_receive_state`→`user_account` 순으로
   정리하도록 고쳤다.

## 6. Potential issues

### Application code

- 없음. 관측된 동작은 전부 계약대로였다.

### Infrastructure and resource limits

- 없음.

### Database and migrations

- **[FINDING — 별도 Issue 후보] 10,000명 규모에서 부분 GIST 인덱스
  (`active_user_presence_position_gix`)가 실제 후보 조회 쿼리에서 전혀 선택되지
  않는다.** `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`으로 실측한 결과, preview
  집계 쿼리와 매칭 후보 조회 쿼리 둘 다 계획기가 `user_account`(driving, Seq Scan,
  10,001행)를 먼저 훑고 `active_user_presence`를 **PK 인덱스**
  (`active_user_presence_pkey`, `user_id` 기준)로 nested-loop join했다. 부분
  GIST 인덱스는 후보로도 고려되지 않았다.
  - **관측된 직접 원인.** 두 쿼리 모두
    `JOIN user_account ua ON ua.id = p.user_id`를 갖는데, 이 테스트 데이터에서
    두 테이블이 정확히 1:1이라 `user_account`를 driving으로 몰고 presence를
    PK로 찍는 편이 공간 인덱스 하강보다 싸다. 공간 조건은 PK 조회 뒤 필터로만
    적용된다.
  - **⚠️ 이 결론의 결정적 한계 — 원인 자체가 테스트 데이터의 인공물일 수 있다.**
    위 두 조건은 모두 이 스위트가 합성한 데이터의 성질이지 운영의 성질이 아니다.
    - `seedSyntheticPresence`는 계정 10,000개를 만들고 **전원에게** presence를
      넣는다. 운영에서 `active_user_presence`는 `user_account`의 작은
      부분집합(위치 공유 중 + 미만료 + `receive_allowed`)이므로 이 1:1 비율은
      재현되지 않는다. 비율이 벌어지면 `user_account` Seq Scan 비용이 급격히
      올라 계획기 선택이 뒤집힐 수 있다.
    - 좌표를 원점 기준 50~5,000m 안에 10,000명 **전부** 배치했다. 그래서 반경을
      20,100km에서 5km로 좁혀도 후보가 줄지 않았고, 공간 조건의 선택도를 이
      스위트는 **한 번도 실험하지 못했다**. 반경 실험이 접근 경로를 바꾸지
      못한 것은 인덱스의 성질이 아니라 데이터 분포의 성질이다.
    - 따라서 이 발견은 "부분 GIST 인덱스가 쓸모없다"가 **아니다**. 정확한 진술은
      **"이 테스트는 인덱스가 선택될 조건 자체를 만들지 못했다"**이다. 두 조건을
      현실적으로 바꾼 재측정 전에는 인덱스의 조회 가치에 대해 어느 방향으로도
      결론을 내릴 수 없다.
  - **영향.** `V1__create_direction_communication_schema.sql:1100-1101`의 주석
    ("매칭 쿼리는 항상 receive_allowed = TRUE 후보만 본다. 부분 인덱스로 두면
    수신을 끈 사용자의 좌표가 인덱스에 들어가지 않아 크기와 갱신 비용이 줄어든다")은
    인덱스 **크기·갱신 비용** 근거를 주장하며, 조회 가속을 주장한 적이 없다 —
    그 주석은 이 발견으로 반박되지 않는다. 반박된 것은 계획서가 세운
    "후보 조회가 이 인덱스를 사용한다"는 **미실측 전제**다. Seq Scan은 없었으므로
    (PK join이 이미 인덱스 기반이라) 성능 문제도 아니다(preview_ms≈356~405,
    matching_ms≈77~83, §4).
  - **후속 조치.** 이 브랜치에서 인덱스나 쿼리를 수정하지 않는다(`type: test`,
    TASK.md 결정 4). 별도 Issue로 다음을 제안한다. (a)가 나머지의 선행 조건이다.
    (a) **먼저 현실적 데이터 모양으로 재측정한다** — 계정:presence 비율을 벌리고
    (예: 계정 100k / presence 10k) 좌표를 반경보다 넓게 흩은 뒤 같은 `EXPLAIN`을
    반복한다. 이 결과 없이는 (b)·(c)를 판단할 근거가 없다.
    (b) (a)에서도 인덱스가 선택되지 않으면, 공간 조건이 driving이 되도록 쿼리
    형태를 바꿀 가치가 있는지 검토한다(예: presence를 공간 인덱스로 먼저 좁히고
    `user_account`를 PK로 확인하는 순서).
    (c) `delivery-scope`가 GLOBAL이 아닌 값으로 바뀌면 그 시점에 (a)를 반복한다.
  - 이 발견 때문에 TASK.md 완료 조건 "10,000명 규모에서 후보 조회가 부분 GIST
    인덱스를 사용하고 active_user_presence에 Seq Scan이 없다"는 **절반만
    충족**한다 — Seq Scan 부재는 참, GIST 인덱스 사용은 거짓. §7에 명시한다.

### Concurrency and idempotency

- 없음(이 계획의 범위 밖 — 단계 내부 동시성은 `DirectionMatchingWorkerConcurrencyIntegrationTest`
  등 기존 계획이 소유).

### Transactions and event ordering

- 없음. INT-006/INT-008에서 관측한 트랜잭션 경계는 계약대로였다.

### External APIs

- 외부 FCM/APNs 푸시는 이 시스템에 구현되어 있지 않다. INT-001/INT-007에서
  `notification_delivery` 행 생성까지만 관측했고 실제 전송은 미검증 범위다.

### Failure recovery and reconciliation

- **[관측, 판정 아님] 매칭 outbox가 `DEAD`로 끝나면 `direction_post`가 영구히
  `MATCHING` 상태에 머무른다(INT-006).** 자동 재시도·발신자 알림·수동 복구 경로가
  현재 없다. 이 이슈는 이 상태를 관측하는 것까지가 범위이며(`agents/test-orchestrator.md`
  가드레일 — 구현되지 않은 동작을 사실처럼 가정하지 않는다), 복구 정책 자체는 별도
  Issue로 제품/엔지니어링 결정이 필요하다.
- fan-out 부분 실패(차단)는 수신 자격을 훼손하지 않음을 확인했다(INT-007) — `#116`
  완료 조건("수신 자격과 푸시 전달 상태가 분리된다")이 관통 경로에서도 성립한다.
- 임대 만료 후 재claim은 중복 수신자를 만들지 않음을 확인했다(INT-008).

## 7. Regression and residual risk

- §6 "Database and migrations" 발견은 회귀가 아니라 이 이슈가 존재하는 이유
  그 자체다(사전에 아무도 측정하지 않았던 것을 이번에 측정했다). 병합 자체의
  위험은 낮지만(테스트 코드만 추가, 프로덕션 코드 미변경), **완료 조건 미충족
  항목이 있으므로 사람의 결정이 필요하다**: 이 발견을 이번 PR 설명에 명시하고
  별도 Issue를 생성할지, 아니면 TASK.md 완료 조건을 "Seq Scan 부재"로 재정의하고
  이 PR로 그대로 종결할지.
- **가장 큰 잔존 위험은 이 스위트가 자기 데이터 모양을 검증 대상으로 삼지 않았다는
  것이다.** 계정:presence 1:1 비율과 좁은 좌표 분포는 의도한 설계가 아니라 규모
  재현을 행 수(10,000)로만 이해한 결과다. 그래서 성능 스위트가 지금 실제로 보장하는
  것은 "규모가 커져도 전수 스캔으로 퇴화하지 않는다" 하나뿐이며, 공간 인덱스의 조회
  가치는 **어느 방향으로도 측정되지 않았다**. §6 후속 조치 (a)가 이 공백을 메운다.
- 나머지 잔존 위험은 §6에 이미 명시한 그대로다: `DEAD` 이후 복구 경로 부재,
  외부 푸시 미구현.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-127-TEST-PLAN-DIRECTION-MATCHING-VERTICAL-FLOW.md`
- CI run: 로컬 실행만 있음(이 세션에서 `./harness pr-ready --project-tests`,
  `./gradlew performanceTest` 직접 실행). GitHub Actions run은 PR 생성 후 별도 링크.
- Related ADR: 없음(신규 ADR 미작성 — §6 발견이 채택되면 후속 Issue에서 검토)
- PR: 미생성(`/harness-pr`에서 생성 예정)

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (§1 Unverified scope: 실제 푸시 전달, 실제 운영 부하, 자동 복구)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 — §6 부분 GIST 인덱스 미사용 발견과
      `DEAD` 이후 복구 부재 발견은 아직 Issue로 등록되지 않았다. PR 생성 전 사람이
      결정해야 한다.
- [x] 실행 결과와 PR 설명이 일치함(PR 작성 시 이 보고서를 그대로 링크)
