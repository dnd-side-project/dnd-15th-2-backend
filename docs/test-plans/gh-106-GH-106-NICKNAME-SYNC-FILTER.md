# Test Plan: TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER

> Created at: `2026-08-12T09:50:57+09:00`
> GitHub Issue: `#106`
> Status: Draft

## 1. Objective

닉네임 설정·변경 시 동기적으로 호출되는 fail-closed moderation 훅이 다음을
보장하는지 검증한다.

- 명시적 `ALLOW`가 있을 때만 닉네임을 적용한다(`INV-NICK-001`). 깨지면
  검사되지 않은 닉네임이 그대로 적용된다.
- 주 판정기의 명시적 `BLOCK`은 확정 결과이며 보조 판정기가 재판정하거나
  뒤집지 못한다(`INV-NICK-002`). 깨지면 이미 차단된 부적절한 닉네임이 보조
  판정기의 관대한 판단으로 통과할 수 있다.
- 보조 판정기는 주 판정기의 timeout/error에만 호출된다(`INV-NICK-003`).
  깨지면 정상 응답이 있는데도 불필요한 이중 외부 호출·지연·비용이 발생한다.
- 주·보조 모두 timeout/error이면 fail-closed로 거부한다(`INV-NICK-005`,
  `INV-GEN-002`). 깨지면 판정 불가가 `ALLOW`로 둔갑해 미검사 닉네임이
  통과하거나, 반대로 서비스가 무기한 대기해 가용성이 무너진다.
- 최초 설정·변경 실패 모두 서비스 진입을 차단하며, 변경 실패 시 기존/임시
  닉네임으로 우회하지 않는다(`INV-NICK-006`, `INV-NICK-007`). 깨지면 검사
  안 된 닉네임으로 서비스를 이용할 수 있는 우회 경로가 생긴다.
- 닉네임 동기 경로는 답변 비동기 경로와 실행 자원을 공유하지 않는다
  (`INV-RES-002`~`004`). 깨지면 답변 backlog·장애가 닉네임 응답 지연·장애로
  전파된다.

## 2. Scope

### Included

- `#105`가 만든 `ModerationPipelineService`/`ModerationProviderClient`
  (`filtering.moderation` 패키지, Spring 빈 아님)를 재사용하는 닉네임 전용
  동기 게이트(gate) 서비스의 순수 오케스트레이션 로직.
- 주 판정기(`ModerationPipelineService.execute`)의 `ALLOW`/명시적
  `BLOCK`/timeout·error(`FilteringException`) 세 갈래 분기 처리.
- 주 판정기 timeout/error일 때만 순차 호출되는 독립 보조 판정기 포트
  (`SecondaryModerationClient`, 신규 인터페이스 — 실제 공급자 구현은 이
  이슈 제외 범위, §2 "설계 가정" 참고)와의 통합.
- 게이트가 자체 소유하는 `ExecutorService`(답변 경로와 별도)로 primary/
  secondary 호출을 실행하고, 설정 가능한(하드코딩 아닌) timeout으로 각
  단계를 경계 짓는 구조.
- 게이트의 최종 출력 계약(호출자가 서비스 진입 차단 여부를 명확히 판단할 수
  있는 결과 타입) — §2 "설계 가정" 참고.
- 답변 경로의 실행 자원 포화가 닉네임 게이트에 전파되지 않음을 보이는
  동시성 테스트.

### Excluded

- `Account.createUser`/`Account.updateProfile` 호출부에서 이 훅을 실제로
  호출하는 배선(#73 `DeviceRegistrationService` 등, Account/Auth 담당 영역).
- 독립 보조 판정기의 실제 공급자 구현(OpenAI 재사용 여부 포함)과 주
  판정기와의 실제 공통 장애 영역 확정 — 미결정, production 차단 게이트.
  이 계획은 `SecondaryModerationClient` 포트를 fake로만 검증한다.
- 동기 timeout, 예약 용량(dedicated executor pool size), quota, 사용자 오류
  안내 문구의 구체 수치 — 미결정(`INVARIANTS.md` §11). 시나리오는 이 값들을
  테스트 전용으로 주입한 임의의 짧은 값(예: 수백 ms)으로 검증하되, 운영
  기본값을 주장하지 않는다.
- `ModerationPipelineService`/`ModerationProviderClient` 자체의 오케스트레이션
  로직 변경 — `#105`에서 이미 구현·검증됨, 이 계획은 그 계약을 소비만 한다.
- `user_account` 테이블, 신규 REST endpoint, 인프라 변경.

### 설계 가정 (구현 착수 전 승인 필요)

이슈 #106 본문은 "동기 판정 훅(메서드/서비스)"만 요구하고 정확한 클래스 이름과
반환 타입을 지정하지 않는다. 아래 가정으로 시나리오를 작성했으며, 사람 승인
전에는 `결정`으로 취급하지 않는다.

1. **게이트 클래스**: `NicknameSyncModerationGate`(가칭, 최종 이름은 구현
   시 확정)가 다음을 생성자로 주입받는다 — 답변 경로와 분리 구성된
   `ModerationPipelineService`(주 판정기), `SecondaryModerationClient`(보조
   판정기 포트, 신규), 닉네임 전용 `ExecutorService`, primary/secondary 각각의
   `Duration` timeout, 사용할 `FilterRelease`.
2. **보조 판정기 포트**: `SecondaryModerationClient`는 `moderate(String
   normalizedContent, ModerationLanguage language)` 형태로 이미 해석된
   `FilterVerdict`(ALLOW/BLOCK)를 반환하거나, 실패 시 항상
   `FilteringException`(신규 코드 `FLT-EXT-002` 등)을 던진다 — 원시 provider
   응답이 아니라 이미 정책까지 결합된 최종 판정만 노출한다(주 판정기와 달리
   보조 판정기는 이 이슈에서 policy 재해석 단계를 두지 않는다). 실제 구현체는
   이 이슈 범위 밖이며 테스트는 fake만 사용한다.
3. **출력 계약**: 게이트는 예외를 던지지 않고 `NicknameModerationOutcome`
   (가칭) sealed 결과를 반환한다 — `ALLOWED` 또는 `REJECTED(reason)`이며
   `reason`은 `BLOCKED_BY_PRIMARY`/`BLOCKED_BY_SECONDARY`/`UNAVAILABLE`
   중 하나로 관측 가능해야 한다(`INV-GEN-006`: 원인 구분 가능해야 함). 호출자
   관점에서는 `ALLOWED`만 서비스 진입을 허용하고 나머지는 모두 차단 대상이다
   — 이슈 문구 "실패 시 호출자가 서비스 진입을 차단할 수 있도록 명확한 실패
   결과를 반환"과 일치시키기 위한 가정이다.
4. **필요 시 대안**: 예외 기반 계약(예: `NicknameModerationRejectedException`
   하나로 통일)도 가능하나, 이 계획은 결과-객체 방식을 1차 후보로 승인
   요청한다 — 승인권자가 예외 기반을 선호하면 시나리오의 Then 절만 바뀌고
   구조는 동일하다.

이 가정이 승인 전 변경되면 이 계획의 §5/§6 시나리오 Then 절을 갱신한 뒤
구현을 진행한다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #106 | 명시적 `ALLOW`가 있어야만 닉네임을 적용하고, 실패 시 호출자가 서비스 진입을 차단할 수 있는 명확한 결과를 반환한다 |
| GitHub Issue #106 | 답변 worker와 분리된 실행 풀·concurrency·timeout·quota·지표를 갖는다 |
| GitHub Issue #106 | 주 판정기 timeout/error에만 독립 보조 판정기를 순차 호출한다 |
| GitHub Issue #106 | 주 판정기의 명시적 `BLOCK`을 보조 판정기가 뒤집지 못한다 |
| GitHub Issue #106 | 주·보조 모두 실패하면 fail-closed로 거부한다 |
| GitHub Issue #106 | 변경 실패 시 기존/임시 닉네임으로 우회하지 않는다 |
| GitHub Issue #106 | 신규 REST endpoint를 만들지 않는다(훅만 제공) |
| `INVARIANTS.md` §3 | `INV-RES-002`~`004`: 닉네임·답변 실행 자원 분리, 답변 backlog가 닉네임 예약 용량을 소진하지 않음 |
| `INVARIANTS.md` §4 | `INV-NICK-001`~`007` 전체 |
| `INVARIANTS.md` §1 | `INV-GEN-002`(판정 불가를 임의 상태로 변환 금지), `INV-GEN-006`(공개 상태·workflow·case 등 상태 분리 — 이 이슈에서는 ALLOWED/REJECTED 사유 구분에 대응) |
| `docs/product` `IMPLEMENTATION_PLAN.md` F03 | 관련 결정 3~6, 8 — 답변 worker와 분리된 실행 풀, 주 판정기 ALLOW/BLOCK/timeout/error 처리, 보조 판정기 순차 호출과 권한 경계, fail-closed |
| `src/main/java/.../filtering/moderation/ModerationPipelineService.java` | 이미 구현된 주 판정기 진입점 — 오케스트레이션 로직 변경 금지, 결과·예외 계약만 소비 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 주 판정기 timeout/error가 `ALLOWED`로 변환됨(fail-open 회귀) | 미검사 닉네임이 그대로 적용되고 서비스 진입 허용 — `INV-NICK-005`/`INV-GEN-002` 정면 위반 | 중 | P0 | UNIT-003, UNIT-006, UNIT-007 |
| 주 판정기 명시적 `BLOCK` 후에도 보조 판정기가 호출되거나 그 결과가 최종 판정을 뒤집음 | `INV-NICK-002`/`INV-NICK-003` 위반, 확정 차단이 우회됨 | 중 | P0 | UNIT-002 |
| 주·보조 모두 실패했는데 게이트가 예외로 죽거나 무한 대기해 호출자가 명확한 실패를 받지 못함 | 호출자가 서비스 진입 차단 여부를 판단할 수 없음 — `INV-NICK-006` 위반 가능 | 중 | P0 | UNIT-006, INT-003 |
| 답변 경로 executor 포화·장애가 닉네임 게이트의 스레드/커넥션 자원을 고갈시킴 | 닉네임 설정이 답변 backlog에 의해 지연·거부됨 — `INV-RES-002`~`004` 위반 | 중 | P0 | INT-002 |
| 게이트가 `ModerationPipelineRequest`를 `filterJobId`와 함께(비동기 답변 경로처럼) 구성해 `FilterDecision`이 잘못 영속화됨 | 닉네임 판정이 답변 원장에 오염되어 기록됨(`#105` §2 설계 가정 위반) | 낮음 | P1 | UNIT-010 |
| 동기 timeout이 primary/secondary 각각이 아니라 전체 예산으로만 걸려, 한쪽이 timeout budget을 모두 소모해 나머지 단계가 실행 기회를 못 받음 | 보조 판정기가 사실상 항상 건너뛰어지거나, 반대로 fail-closed 결정이 지연됨 | 낮음 | P1 | INT-003 |
| 보조 판정기 포트가 정상 완료 시에도 애매한 값(null 등)을 반환할 수 있는 계약으로 설계됨 | 방어 누락 시 NPE 또는 판정 불가가 조용히 다른 값으로 취급될 위험 | 낮음 | P1 | UNIT-008 |
| 보조 판정기의 실제 공급자·주 판정기와의 공통 장애 영역(`INV-NICK-004`) | 실제 장애 시 두 판정기가 동시에 죽어 fail-closed만 계속 발생하거나, 반대로 "독립"이라는 전제가 실제로는 거짓일 위험 | 확인 필요 | P0(확인) | 이 이슈에서는 코드 레벨 독립성(별도 인스턴스·별도 executor)만 구조로 검증 — 실제 공급자·인프라 공통점 분석은 production 차단 게이트로 인계, 사람 확인 필요 |

## 5. Unit scenarios

모든 unit 시나리오는 Spring 컨텍스트 없이 순수 객체와 테스트 전용 fake(주
판정기를 흉내내는 fake `ModerationProviderClient`/`TextNormalizer`/
`LocalRuleEngine`/`PolicyEngine` 조합, 그리고 fake `SecondaryModerationClient`)
로 게이트 오케스트레이션만 검증한다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-UNIT-001 | 주 판정기가 `ALLOW`를 반환하도록 구성 | 게이트를 실행한다 | 결과는 `ALLOWED`이고, fake 보조 판정기는 한 번도 호출되지 않는다(`INV-NICK-001`, `INV-NICK-003`) | P0 | Feature executor |
| TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-UNIT-002 | 주 판정기가 명시적 `BLOCK`을 반환하도록 구성, 보조 판정기는 `ALLOW`를 반환하도록 구성(호출되면 결과가 뒤집힐 수 있는 상황을 의도적으로 세팅) | 게이트를 실행한다 | 결과는 `REJECTED(BLOCKED_BY_PRIMARY)`이고, 보조 판정기는 호출되지 않는다 — 보조의 `ALLOW` 구성이 결과에 영향을 주지 않음을 확인(`INV-NICK-002`) | P0 | Feature executor |
| TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-UNIT-003 | 주 판정기가 timeout(`FilteringException(MODERATION_PROVIDER_UNAVAILABLE)`)을 던지도록 구성 | 게이트를 실행한다 | 보조 판정기가 정확히 1회 호출된다(`INV-NICK-003`) | P0 | Feature executor |
| TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-UNIT-004 | 주 판정기 timeout, 보조 판정기가 `ALLOW`를 반환하도록 구성 | 게이트를 실행한다 | 결과는 `ALLOWED` | P0 | Feature executor |
| TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-UNIT-005 | 주 판정기 timeout, 보조 판정기가 `BLOCK`을 반환하도록 구성 | 게이트를 실행한다 | 결과는 `REJECTED(BLOCKED_BY_SECONDARY)` | P0 | Feature executor |
| TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-UNIT-006 | 주 판정기 timeout, 보조 판정기도 timeout/error(예외)를 던지도록 구성 | 게이트를 실행한다 | 게이트는 예외를 전파하지 않고 결과는 `REJECTED(UNAVAILABLE)`을 반환한다(`INV-NICK-005`, `INV-GEN-002`) | P0 | Feature executor |
| TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-UNIT-007 | 주 판정기가 timeout이 아닌 다른 예외(`FilteringException`, 임의 provider 오류)를 던지도록 구성 | 게이트를 실행한다 | UNIT-003과 동일하게 보조 판정기 경로로 전환되고, 임의 예외가 `ALLOWED`로 대체되지 않는다 | P0 | Feature executor |
| TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-UNIT-008 | 보조 판정기 fake가 방어적 계약 문서를 그대로 구현(정상 완료 시 null을 반환하지 않음)했는지 컴파일·타입 수준으로 확인 | 정상 완료 케이스들(UNIT-004/005)을 실행한다 | 반환된 `FilterVerdict`가 항상 `ALLOW`/`BLOCK` 중 하나이며 null 처리 분기가 필요 없음을 확인 | P1 | Feature executor |
| TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-UNIT-009 | 같은 게이트 인스턴스를 순차로 두 번 실행(1차 timeout→보조 BLOCK, 2차 정상 ALLOW) | 게이트를 두 번 실행한다 | 1차 결과가 2차 실행에 영향을 주지 않는다(내부 상태 누수 없음, 인스턴스 재사용 가능) | P1 | Feature executor |
| TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-UNIT-010 | 주 판정기 fake가 전달받은 `ModerationPipelineRequest`를 캡처하도록 구성 | 게이트를 실행한다 | 캡처된 요청은 `filterJobId`가 `null`인 `ephemeral` 요청이고 `contentType`은 `FilterTargetType.NICKNAME`이다(`#105` §2 설계 가정과 일치, `FilterDecision` 영속화 없음) | P0 | Feature executor |
| TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-UNIT-011 | 초기 설정(최초 닉네임)과 변경(기존 닉네임 교체) 두 호출 경로를 각각 구성 — 게이트 API가 두 경우를 구분하는 파라미터를 받는지 확인 | 두 경로 모두 주 판정기 timeout+보조 실패로 구성해 실행한다 | 두 경로 모두 동일하게 `REJECTED(UNAVAILABLE)`을 반환한다 — 변경 실패에 대해 기존 닉네임을 유지시키는 별도의 관대한 분기가 게이트 내부에 존재하지 않는다(`INV-NICK-006`, `INV-NICK-007`) | P0 | Feature executor |

## 6. Integration scenarios

Spring 컨텍스트 없이(이 게이트는 Spring 빈이 아님, `#105`의 `ModerationPipelineService`
와 동일한 이유 — 실제 구현체 미확정) 실제 `ExecutorService`와 시간 지연을 쓰는
동시성 통합 시나리오. DB는 사용하지 않는다(닉네임 게이트는 `filterJobId` 없이
호출되어 영속화 경로를 타지 않음, UNIT-010이 이를 보장).

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-INT-001 | 게이트, 전용 `ExecutorService`, 지연 응답하는 fake 주 판정기 | 주 판정기가 게이트에 설정된 timeout보다 오래 걸리도록 구성 | 게이트를 실행한다 | 게이트는 timeout 시점에 대기를 끊고 보조 판정기로 전환한다 — 전체 소요 시간이 "주 timeout + 보조 처리 시간" 근방이지 원래 주 판정기의 긴 지연 전체를 기다리지 않는다 | executor 종료 |
| TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-INT-002 | 게이트(닉네임 전용 executor), 별도로 흉내낸 "답변 경로" `ExecutorService` | 답변 경로 executor를 CountDownLatch로 인위적으로 포화(saturate)시킴 | 동시에 닉네임 게이트를 호출한다 | 닉네임 게이트 호출이 답변 경로 포화의 영향 없이 설정된 timeout 이내로 완료된다(`INV-RES-002`~`004` 직접 검증 — `#105` UNIT-013보다 강한 증거: 실제 게이트 인스턴스 사용) | 양쪽 executor 종료 |
| TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-INT-003 | 게이트, 주·보조 모두 지연 응답하는 fake | 주·보조 판정기가 각각 설정된 timeout보다 오래 걸리도록 구성 | 게이트를 실행한다 | 결과는 `REJECTED(UNAVAILABLE)`이고, 전체 소요 시간이 유한한 예산(주 timeout + 보조 timeout 근방) 안에서 반환된다 — 무기한 대기하지 않는다 | executor 종료 |
| TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-INT-004 | 게이트, 고정 크기 전용 `ExecutorService` | 게이트의 executor 용량(예: 고정 스레드 수)을 초과하는 수의 동시 요청을 제출 | 초과 요청들을 포함해 모두 실행한다 | 초과분이 무기한 대기하거나 답변 경로 executor로 유출되지 않고, 정의된 방식(대기열 또는 즉시 거부 — 구현 시 결정, 이 시나리오는 "답변 경로로 새지 않는다"는 구조적 속성만 검증)으로 처리된다. 정확한 큐잉/거부 수치는 미결정이므로 이 항목은 구조 검증에 한정한다 | executor 종료 |

## 7. Cross-cutting scenarios

### Database and transactions

- 이 이슈는 DB를 직접 다루지 않는다(§2 Excluded, `user_account` 미수정).
  UNIT-010이 게이트가 pipeline을 항상 `filterJobId` 없이(ephemeral) 호출해
  `FilterDecision` 영속화 경로를 타지 않음을 보장한다.

### Concurrency and idempotency

- INT-002/INT-004가 실행 자원 격리와 게이트 자체의 동시 호출 안전성을 다룬다.
- 게이트는 상태를 갖지 않는(stateless) 설계를 전제한다 — UNIT-009가 순차
  재사용 시 상태 누수가 없음을 확인한다. 동시 호출 시 내부 mutable 상태 공유
  여부는 코드 검토로 추가 확인한다(게이트가 `synchronized`/공유 mutable
  필드를 두지 않는지).
- 이 게이트는 재시도를 하지 않는다(주→보조 순차 호출은 재시도가 아니라
  서로 다른 판정기로의 전환이다) — 답변 경로의 retry 정책(`#108` 소관)과
  혼동하지 않는다.

### External APIs

- 주 판정기의 실제 OpenAI 호출은 `#105`에서 이미 검증됨 — 이 계획은 fake
  `ModerationProviderClient`만 사용한다.
- 보조 판정기는 실제 구현체가 없으므로 모든 시나리오가 fake
  `SecondaryModerationClient`를 사용한다. 실제 공급자 연동 검증은 이
  이슈의 production 차단 게이트로 별도 이슈에서 다룬다.

### Failure recovery and reconciliation

- 이 게이트는 자동 재시도를 하지 않는다 — `REJECTED(UNAVAILABLE)`은 최종
  결과이며 호출자(또는 사용자의 재요청)가 다시 게이트를 호출하는 것으로만
  복구된다. 자동 재시도 부재를 명시적으로 확인하는 시나리오는 두지 않고
  코드 검토로 "게이트 내부에 재시도 루프가 없는지"를 확인한다.
- INT-003이 fail-closed 결과가 유한 시간 안에 반환됨을 확인해, 호출자가
  무기한 블로킹되지 않고 명확한 실패로 서비스 진입을 차단할 수 있는 기반을
  보장한다.

## 8. Test data and isolation

- Fixtures: `#105`의 `ModerationPipelineServiceTest`/
  `ModerationPipelineIntegrationTest` 패턴을 재사용 — fake `TextNormalizer`
  (원문 그대로 반환), fake `LocalRuleEngine`(`LocalRuleVerdict.noMatch()`
  고정), fake `ModerationProviderClient`(테스트별로 `ALLOW`/`BLOCK`/예외 구성),
  fake `PolicyEngine`. 보조 판정기는 신규 fake `SecondaryModerationClient`
  (테스트별로 `FilterVerdict` 또는 예외 구성)로 추가한다.
- Database isolation: 사용하지 않음(§7 Database and transactions 참고).
- Clock/randomness: 시간 의존 검증(INT-001/003/004)은 실제 `Duration`
  기반 timeout과 `CountDownLatch`/`Future#get(timeout)`으로 측정한다 —
  `Clock` 주입이 아니라 실제 경과 시간을 짧은 상한(예: 초 단위)으로 두어
  테스트 실행 시간을 통제한다.
- External API doubles: 신규 외부 라이브러리 없이 순수 fake 구현
  (인터페이스 익명 클래스 또는 테스트 전용 클래스)만 사용 — 실제 HTTP는
  이 계획에서 다루지 않는다(§7 External APIs).
- Cleanup: 테스트가 생성한 `ExecutorService`는 각 테스트 종료 시
  `shutdownNow()`로 명시적으로 정리한다(`#105` PR 리뷰에서 지적된 executor
  누수를 이 계획에서는 처음부터 방지한다).

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Feature executor | `src/main/java/com/dnd/qello/filtering/moderation/**`(신규: 닉네임 게이트 서비스, `SecondaryModerationClient` port, `NicknameModerationOutcome` 결과 타입), `src/main/java/com/dnd/qello/filtering/error/FilteringErrorCode.java`(신규 오류 코드 추가만, 기존 코드 값 변경 금지), `src/test/java/com/dnd/qello/filtering/moderation/**`(신규 게이트 테스트) | UNIT-001 ~ UNIT-011 | `./gradlew test --tests "com.dnd.qello.filtering.moderation.*"` |
| 2 | Feature executor | 위와 동일 디렉터리의 동시성 통합 테스트(순수 Java, Spring 컨텍스트 불필요 — `src/test` 또는 `src/integrationTest` 배치는 구현 시 결정) | INT-001 ~ INT-004 | `./gradlew test`(또는 `integrationTest`) `--tests "<게이트 통합 테스트 클래스>"` |

두 순서 모두 같은 실행 에이전트(Feature executor)가 순차로 담당한다. Order 2는
Order 1이 정의하는 게이트 클래스와 포트에 의존하므로 병렬 실행하지 않는다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현(UNIT-001~007, UNIT-010, UNIT-011, INT-001~003)
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합(동시성) 테스트 통과
- [ ] 잠재 문제 분석(애플리케이션 경계, 동시성, 외부 API, 장애 복구 —
      `agents/test-executor.md` 기준. 이 이슈는 DB/트랜잭션이 없으므로 해당
      항목은 "해당 없음"으로 명시)
- [ ] 테스트 보고서 생성(`templates/test-report.md`)
- [ ] §2 "설계 가정"이 실제 구현과 다르게 확정되면 이 계획을 갱신한 뒤
      진행한다.
- [ ] `INV-NICK-004`(보조 판정기의 실제 독립성)는 이 계획에서 구조적으로만
      확인하고, 실제 공급자·공통 장애 영역 확정은 production 차단 게이트로
      남겨둔다.

## 11. Human approval

- Reviewer: tkv00
- Decision: Approved — §2 "설계 가정" 1~3(게이트 구조, `SecondaryModerationClient`가
  이미 정책 결합된 `FilterVerdict`만 반환, 예외 대신 `NicknameModerationOutcome`
  결과 객체 계약)을 그대로 승인함.
- Approved at: `2026-08-12T10:09:19+09:00`
