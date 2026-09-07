# Test Plan: TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING

> Created at: `2026-09-07T09:37:28+09:00`
> GitHub Issue: `#204`
> Status: Approved

## Execution status (2026-09-07)

- Unit 8개(UNIT-001~008)는 계획대로 구현·실행해 전부 PASS했다. 실제 클래스명은
  계획대로 `AnswerModerationExecutionConfig`이며, `WorkerSchedulingProperties`는
  execution/deadline/verdict 세 worker를 개별 `enabled` 없이 `answerModeration.enabled`
  하나로만 묶는 구조로 확정했다(부분 활성화를 설정 자체로 표현 불가능하게 함).
- `WorkerSchedulingConfigurationTest`에 UNIT-008 확장과 INT-005(세 adapter
  동시 등록)를 `#182`가 이미 쓰는 `ApplicationContextRunner` + 컴포넌트 스캔
  패턴으로 추가했다 — 원래 계획한 무거운 `@SpringBootTest`+Testcontainers
  방식 대신, `#182`의 기존 관례(mock worker + 실제 `@Scheduled` 실행)를
  그대로 따랐다.
- INT-001~004(Testcontainers 기반 실제 DB/외부 provider stub 시나리오)는
  이 세션에서 Docker 데몬이 실행되지 않아 미실행 상태(`BLOCKED`)다. Docker
  기동 후 `./gradlew integrationTest --tests '*AnswerModeration*'`로
  재확인이 필요하다.

## 1. Objective

`AnswerModerationExecutionWorker`(현재 의도적으로 Spring 빈이 아님),
`AnswerModerationDeadlineWorker`, `AnswerModerationVerdictWorker`(둘 다 빈이지만
`@Scheduled` 트리거 없음)를 production gate 뒤에서 원자적으로 배선한다. 실패
시 위험은 다음과 같다.

- 세 worker 중 일부만 켜지면(예: execution만 켜지고 verdict가 꺼짐) 판정은
  나갔는데 답변 상태에 영구 반영되지 않는 응답 없는 답변이 쌓인다.
- production gate가 켜졌는데 answer 전용 credential이 비어 있으면 매 배치마다
  예외가 나거나, 더 나쁘게는 조용히 아무 일도 하지 않는 fail-open이 된다.
- 답변 판정용 실행 자원(RestClient, ExecutorService)이 닉네임 경로와
  공유되면 한쪽 부하가 다른 쪽 판정 지연으로 번진다(INV-RES-001,
  INV-RES-002 위반).

이 계획은 새로 추가되는 **배선·설정 계층**만 검증한다. `AnswerModerationExecutionWorker`
/ `DeadlineWorker` / `VerdictWorker`의 내부 판정·재시도·lease 로직 자체는 이미
`AnswerModerationExecutionWorkerTest` 등 기존 단위 테스트가 덮고 있으므로
재검증하지 않는다.

## 2. Scope

### Included

- production gate(`qello.filtering.production.enabled`) 켜짐/꺼짐에 따른
  answer 전용 `ModerationPipelineService`·provider client·executor·
  `AnswerModerationExecutionWorker` bean 등록/부재
- answer 전용 credential(예: `openai-api-key`)·필수 timeout 값 누락 시
  fail-closed 기동 실패
- `AnswerModerationExecutionWorker`, `AnswerModerationDeadlineWorker`,
  `AnswerModerationVerdictWorker` 세 개의 scheduler adapter 등록과, 셋이
  분리 활성화되지 않는다는 계약(부분 활성화 금지)
- `WorkerSchedulingProperties`에 추가되는 answer moderation 세 블록의
  검증 로직(batch·lease·backoff)
- `WorkerMetrics.WorkerName`에 추가되는 `ANSWER_MODERATION_EXECUTION` /
  `_DEADLINE` / `_VERDICT` 태그가 outcome 열거값만 신는지
- 답변 전용 실행 자원이 nickname 경로와 별도 인스턴스인지
- outbox 행 하나가 실제 `@Scheduled` 주기를 거쳐 execution → verdict → 답변
  publish/reject까지 도달하는 최소 end-to-end 흐름(provider는 stub)

### Excluded

- `ModerationPipelineService`/`PolicyEngine`/`LocalRuleEngine`/판정
  알고리즘·category threshold 자체 (기존 판정 단위 테스트 범위, 이 이슈의
  제외 사항)
- `AnswerModerationExecutionWorker`/`DeadlineWorker`/`VerdictWorker`의 내부
  outcome 분기(재시도·소진·race 처리) — 기존 단위 테스트가 이미 덮음
- `SlackNotifier` 구현과 Slack dispatch (`#205`)
- nickname moderation 동작
- 실제 OpenAI(또는 다른 provider) 엔드포인트 호출 — 모든 시나리오는 stub/mock만 사용
- `FilteringProductionGate`(#113)의 DPA·data-residency 등 5개 확인 항목 자체
  로직 재검증(이미 존재하는 게이트, 이 이슈는 그 뒤에 얹히기만 한다)
- 실제 production 활성화, 배포, 인프라 apply

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue `#204` | 완료 조건 7개(gate off 시 미실행 / credential 누락 시 fail-closed / execution·deadline·verdict 주기 동작 / lease 동시성 보호 / retry 정책 준수 / 부분 활성화 금지 / tag·로그 비식별) |
| `docs/filtering-production-gate.md` §3 | "게이트가 보장하지 않는 것" 4개 항목 중 pipeline bean 등록과 deadline/verdict 스케줄러 배선이 이 이슈가 채워야 할 공백임을 명시 |
| `#182` Core worker scheduling 산출물 | `WorkerSchedulingProperties`/`WorkerInstanceIdentity`/`WorkerMetrics`/`WorkerSchedulingConfiguration` 계약 — 새 worker도 이 계약을 그대로 재사용해야 한다 |
| `NicknameModerationGateConfig`(`#168`) 및 그 테스트 | production gate 뒤에서 pipeline·전용 실행 자원을 구성하고 credential 누락 시 fail-closed하는 기존 참조 구현 |
| `TASK.md`(이 브랜치) | 범위·제외·완료 조건의 최종 계약 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| execution/deadline/verdict 중 일부만 활성화되는 구성이 허용됨 | 판정이 답변 상태에 반영되지 않는 응답 없는 답변 누적 (완료조건 6) | Medium | P0 | 세 worker 개별 on/off 조합 통합 테스트로 "부분 활성화 불허" 계약 고정 |
| production gate on인데 answer 전용 credential 누락 시 fail-open | 외부 호출이 매 배치 예외를 던지거나 조용히 스킵됨 | Medium | P0 | ApplicationContextRunner + 실제 컨텍스트 기동 두 레이어에서 기동 실패 검증 |
| answer 전용 RestClient/ExecutorService가 nickname과 자원 공유 | 한쪽 부하가 다른 쪽 지연으로 전이(INV-RES-001/002) | Low | P1 | bean 인스턴스 식별자 비교로 분리 확인 |
| 신규 `WorkerMetrics.WorkerName` tag가 우회 경로로 원문·식별자를 태깅 | 지표를 통한 개인정보/원문 유출 | Low | P1 | `recordOutcome`/`recordClaimed`가 기존과 동일하게 열거값만 받는지 확인, `FilteringMetricTags` 대상이 아님을 명시 |
| production=false, worker.scheduling=true인 조합에서 answer moderation이 실행됨 | 로컬/개발 환경에서 의도치 않은 외부 호출 시도 | Medium | P0 | 게이트 조합 매트릭스 통합 테스트 |
| 두 인스턴스가 같은 outbox 행을 동시 클레임 | 중복 외부 호출/중복 판정 | Low (기존 `claimDue` 원자성 재사용, 신규 로직 아님) | P2 | 기존 outbox claim 원자성 회귀만 확인, 신규 시나리오 최소화 |

## 5. Unit scenarios

실행 에이전트가 실제로 만들 설정 클래스명은 구현 계획에서 확정된다. 아래
`AnswerModerationExecutionConfig`는 `NicknameModerationGateConfig`와 대칭되는
제안 이름이며, 실제 클래스명이 다르면 시나리오 ID는 유지한 채 대상 클래스만
구현 계획에 맞게 바꾼다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING-UNIT-001 | `qello.filtering.production.enabled=false`(기본값) | `AnswerModerationExecutionConfig`를 `ApplicationContextRunner`로 로드 | `ModerationPipelineService`/`AnswerModerationExecutionWorker`/answer 전용 `RestClient`·`ExecutorService` 빈이 모두 부재 | P0 | Execution agent |
| TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING-UNIT-002 | `production.enabled=true` + answer 전용 `openai-api-key`·timeout·executor-pool-size 등 필수값 모두 제공 | 컨텍스트 로드 | `AnswerModerationExecutionWorker` 빈이 정확히 하나 등록되고, 그 안의 provider client가 `OpenAiModerationProviderClient` 인스턴스 | P0 | Execution agent |
| TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING-UNIT-003 | `production.enabled=true`, answer 전용 `openai-api-key`가 빈 문자열 | 컨텍스트 로드 | 컨텍스트 기동 실패(`hasFailed()`), 예외 메시지에 실제 키 값이 포함되지 않음 | P0 | Execution agent |
| TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING-UNIT-004 | `production.enabled=true`, answer 전용 pipeline-timeout 등 필수 Duration 값 누락(구현이 필수로 정의한 값 기준) | 컨텍스트 로드 | 컨텍스트 기동 실패 — 값이 없어도 암묵적 기본값으로 조용히 뜨지 않음 | P0 | Execution agent |
| TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING-UNIT-005 | `production.enabled=true`, 모든 필수값 제공 | answer 전용 `RestClient`/`ExecutorService` 빈과 `NicknameModerationGateConfig`가 만든 nickname 전용 빈을 함께 로드 | 두 경로의 `RestClient`/`ExecutorService` 인스턴스가 서로 다름(`!=`) | P1 | Execution agent |
| TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING-UNIT-006 | `WorkerSchedulingProperties`에 추가된 answer moderation execution/deadline/verdict 블록 | `enabled=true`인데 `fixedDelay`/`batchSize`/`leaseDuration` 등 필수 값이 0 이하이거나 없음 | 생성자에서 `IllegalArgumentException` (기존 `validateOutbox`류 패턴과 동일한 실패 방식) | P1 | Execution agent |
| TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING-UNIT-007 | 세 scheduler adapter의 `@ConditionalOnProperty` 조건(구현이 확정한 조건식) | 세 worker 중 하나만 `enabled=true`, 나머지는 `false`인 property 조합 | 구현이 채택한 "부분 활성화 거부" 방식대로 관측 가능하게 실패하거나 무시됨(정확한 실패 모드는 구현 계획에서 확정 — 이 시나리오는 "조용히 부분 실행되지 않는다"는 계약만 고정) | P0 | Execution agent |
| TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING-UNIT-008 | `WorkerMetrics`에 추가된 `ANSWER_MODERATION_EXECUTION`/`_DEADLINE`/`_VERDICT` `WorkerName` | `recordOutcome`/`recordClaimed`를 새 이름으로 호출 | 기존 `WorkerName`과 동일하게 `worker`/`outcome` 두 tag만 기록되고 값은 열거형 `name()`뿐(원문·식별자 없음) | P1 | Execution agent |

## 6. Integration scenarios

`@SpringBootTest` + Testcontainers PostGIS 기준. provider는 전부 stub/fake로
대체하고 실제 외부 엔드포인트를 호출하지 않는다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING-INT-001 | 기본 프로필(`production.enabled=false`) | `MODERATION_EXECUTION_REQUESTED` outbox 행 1개를 fixture로 저장 | 애플리케이션 컨텍스트 기동, 짧게 대기 | 세 scheduler adapter 빈이 존재하지 않고, outbox 행 상태가 그대로(PENDING) 유지됨 | 트랜잭션 롤백 |
| TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING-INT-002 | `production.enabled=true`, `worker.scheduling.enabled=true`, 세 worker 모두 `enabled=true`, 필수 credential 존재(stub 값) | 컨텍스트 기동 | 컨텍스트 정상 기동 확인 | 세 scheduler adapter 빈이 모두 존재하고 `AnswerModerationExecutionWorker`가 컴포넌트 스캔이 아닌 명시적 `@Bean`으로 정확히 하나 등록됨 | 컨텍스트 종료 |
| TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING-INT-003 | INT-002와 동일 설정 + `ModerationProviderClient`를 ALLOW를 반환하는 테스트 stub으로 오버라이드, 세 worker의 `fixedDelay`를 짧게(≤ PT1S) 설정 | `AUTOMATED` 상태 `FilterJob`과 그에 딸린 `MODERATION_EXECUTION_REQUESTED` outbox 행을 저장 | Awaitility로 일정 시간(예: 5초) 내 수렴 대기 | `FilterJob`이 `RESOLVED`로 전이하고 `MODERATION_VERDICT_READY`가 발행되며, `AnswerModerationVerdictWorker`가 소비해 대상 답변이 `publish()`됨(ALLOW 기준) | 컨텍스트 종료, 테스트 데이터 롤백 |
| TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING-INT-004 | `production.enabled=true`, answer 전용 credential 누락, 나머지는 INT-002와 동일 | 애플리케이션 컨텍스트 기동 시도 | 기동 실패(`ApplicationContextException`류) — 단위 테스트(UNIT-003)와 별개로 실제 통합 부트스트랩 레벨에서도 같은 결과 확인 | 컨텍스트 기동 실패이므로 별도 정리 불필요 |
| TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING-INT-005 | `production.enabled=true`, 세 worker 중 execution만 `enabled=true`, deadline·verdict는 `false` | 컨텍스트 기동 시도 | UNIT-007과 동일한 계약 재확인 — 통합 레벨에서 실제 property 파일 형태로도 부분 활성화가 조용히 성공하지 않음 | 컨텍스트 종료/기동 실패 |

## 7. Cross-cutting scenarios

### Database and transactions

- `FilterJob`/`OutboxEvent`/`ManualReviewCase`의 트랜잭션 경계는 기존
  `AnswerModerationExecutionWorker` 구현이 이미 소유한다. 이 계획은 그
  경계를 재검증하지 않고, 새 설정·스케줄러 계층이 트랜잭션 경계를 바꾸지
  않는지만 코드 리뷰(diff)로 확인한다 — 별도 테스트를 추가하지 않는다.

### Concurrency and idempotency

- INT-003에서 `fixedDelay`로 스케줄이 여러 번 반복돼도 같은 outbox 행이
  두 번 처리되지 않아야 한다(`complete()` 이후 `claimDue`가 다시 집지
  않음) — 기존 `claimDue`/lease 원자성의 회귀 확인 수준이며 새 동시성
  로직을 추가하지 않는다.
- 두 애플리케이션 인스턴스가 동시에 같은 outbox 행을 처리하지 못한다는
  계약은 `#182`가 이미 다른 worker로 검증했다. 이 계획에서 새로 반복하지
  않고, `AnswerModerationExecutionWorker`가 같은 `leaseOwner`/`claimDue`
  계약을 그대로 쓰는지만 코드 리뷰로 확인한다.

### External APIs

- 모든 단위·통합 테스트는 실제 OpenAI(또는 다른 provider) 엔드포인트를
  호출하지 않는다. `ModerationProviderClient`는 테스트 전용 구현으로
  대체하거나, `RestClient`의 `baseUrl`을 로컬 stub 서버로 돌린다.
- credential 값은 `"example-key-for-unit-test"`류의 형식만 갖춘 placeholder만
  쓰고 실제 키 형식·값을 흉내 내지 않는다.

### Failure recovery and reconciliation

- `FilteringProductionGate`(#113)가 이미 담당하는 DPA 등 5개 확인 항목의
  기동 실패는 이 계획의 대상이 아니다. 이 계획은 그 게이트를 통과한
  **이후**, answer 전용 credential이 없어 별도로 fail-closed되는 지점만
  검증한다 — 두 실패 지점이 섞이지 않게 시나리오 설명에 어떤 확인 항목이
  비었는지 명시한다.

## 8. Test data and isolation

- Fixtures: 기존 `AnswerModerationReleaseTestFixture`를 재사용해 `FilterRelease`/`FilterJob`을
  만든다. 스케줄러 통합 시나리오(INT-003)에만 최소 outbox 행 fixture를
  추가한다.
- Database isolation: 기존 통합 테스트와 동일하게 Testcontainers PostGIS +
  트랜잭션 롤백 또는 `@Transactional` 테스트 경계를 따른다.
- Clock/randomness: 단위 테스트는 `Clock.fixed`. INT-003만 실제 wall-clock
  기반 `@Scheduled` 주기(≤ PT1S)를 쓰고 Awaitility로 폴링해 sleep 기반
  대기를 피한다.
- External API doubles: `ModerationProviderClient`의 테스트 전용 구현
  (고정 `ALLOW`/`BLOCK` 반환) 또는 WireMock. 실제 네트워크 호출 없음.
- Cleanup: 컨텍스트 기동 실패를 검증하는 시나리오는 별도 정리가 필요 없다.
  나머지는 트랜잭션 롤백으로 정리한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

정확한 파일명·클래스명은 구현 계획(다음 단계)에서 확정한다. 아래는 파일
소유권이 겹치지 않게 나누기 위한 제안이며, 실행 에이전트는 실제 이름을
구현 계획과 PR에 기록한다.

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Execution agent (설정·배선) | `src/main/java/com/dnd/qello/filtering/config/AnswerModerationExecutionConfig.java`(신규), `src/main/java/com/dnd/qello/scheduling/config/WorkerSchedulingProperties.java`, `src/main/java/com/dnd/qello/scheduling/observability/WorkerMetrics.java`, `src/main/java/com/dnd/qello/scheduling/adapter/AnswerModerationExecutionScheduledAdapter.java`·`AnswerModerationDeadlineScheduledAdapter.java`·`AnswerModerationVerdictScheduledAdapter.java`(신규), `src/main/resources/application.yml` | UNIT-001~008 | `./gradlew test --tests '*AnswerModerationExecutionConfig*' --tests '*WorkerSchedulingProperties*' --tests '*WorkerMetrics*'` |
| 2 | Test executor (통합) | `src/integrationTest/java/com/dnd/qello/AnswerModerationSchedulingExposureIntegrationTest.java`(신규, 파일명 제안) | INT-001~005 | `./gradlew integrationTest --tests '*AnswerModerationSchedulingExposure*'` |
| 3 | Independent verifier | 소유 파일 없음(읽기 전용 검증) | 전체 | `./gradlew check`, `git diff --name-only`로 변경 파일이 `TASK.md` Scope를 벗어나지 않는지 확인 |

Execution agent와 Test executor는 서로 다른 파일만 수정한다 — 설정/스케줄러
어댑터 vs 통합 테스트로 소유권을 분리했다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현 (UNIT-001, 002, 003, 004, 007 / INT-001, 002, 004, 005)
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더에 정확한 ISO 8601 생성 시각과 이 계획의 Scenario ID 기록
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석(특히 부분 활성화 실패 모드, credential 회전 시 재기동 필요성)
- [ ] 테스트 보고서 생성(`templates/test-report.md`)
- [ ] 이슈 #204 완료 조건 7개 전부가 위 시나리오 중 하나 이상에 매핑됨

## 11. Human approval

- Reviewer: 사용자(tkv0098)
- Decision: 승인 — 초안 제시 직후 "작업 진행" 지시로 승인
- Approved at: `2026-09-07T09:50:00+09:00`(세션 시각 기준, 정확한 타임스탬프는 커밋 시각으로 대체 가능)
