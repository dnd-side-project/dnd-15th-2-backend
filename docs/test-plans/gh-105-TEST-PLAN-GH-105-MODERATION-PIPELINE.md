# Test Plan: TEST-PLAN-GH-105-MODERATION-PIPELINE

> Created at: `2026-08-11T20:43:38+09:00`
> GitHub Issue: `#105`
> Status: Approved

## 1. Objective

닉네임(동기)과 답변(비동기)이 공유하는 공통 moderation 판정 pipeline이 다음을
보장하는지 검증한다.

- 공급자(OpenAI)의 단일 `flagged` 값이 최종 `ALLOW`/`BLOCK`을 직접 결정하지
  않는다(`INV-PIPE-003`). 이것이 깨지면 내부 정책과 무관하게 공급자 카테고리
  기준이 서비스 차단 기준이 되어 오탐·미탐이 통제 밖에서 발생한다.
- 고신뢰 로컬 규칙이 명확한 `BLOCK`을 반환하면 OpenAI를 호출하지 않는다.
  깨지면 이미 확정된 차단에 불필요한 외부 호출·지연·비용이 발생한다.
- OpenAI timeout/error가 임의의 `ALLOW`/`BLOCK`으로 둔갑하지 않는다. 이것이
  깨지면 (a) fail-open 방향 회귀 시 미검사 유해 콘텐츠가 통과하고, (b)
  fail-closed 방향 회귀 시 정상 콘텐츠가 시스템 장애로 차단된다. 닉네임 경로는
  이 판정 불가 신호를 그대로 받아 결정 4(Fail-closed)를 적용해야 하므로,
  pipeline이 스스로 판정을 만들어내면 그 경계가 무너진다.
- 규칙 적중 여부, 공급자 원시 응답, 최종 정책 결정을 각각 관측할 수 있다
  (`INV-PIPE-005`). 깨지면 오탐·미탐이 규칙 문제인지 모델 문제인지 정책 결합
  문제인지 사후에 구분할 수 없다.
- 닉네임 동기 경로와 답변 비동기 경로가 pipeline 판정 로직은 공유하되 실행
  자원(스레드풀·동시성 설정)은 구조적으로 공유할 수 없다(`INV-RES-001`,
  `INV-RES-002`). 깨지면 답변 backlog가 닉네임 응답 지연·장애로 전파되어
  결정 3(실행 자원 격리)이 무력화된다.

## 2. Scope

### Included

- 입력(원문, 언어, 콘텐츠 종류 `ANSWER`/`NICKNAME`, 적용할 `FilterRelease`)을
  받아 정규화 → 고신뢰 로컬 규칙 → OpenAI 호출 → 내부 정책 결합 순서로 실행하는
  pipeline 서비스의 순수 오케스트레이션 로직.
- 로컬 규칙 `BLOCK` 적중 시 OpenAI 호출을 생략하는 단락(short-circuit) 동작.
- OpenAI 벤더 중립 어댑터: 요청 구성과, `flagged`/`categories`/
  `category_scores`/실제 `model` 응답을 내부 결과로 매핑하는 파싱 로직.
- OpenAI timeout/error를 판정으로 변환하지 않고 호출자에게 그대로 전달하는
  경로.
- 규칙 적중/모델 원시 응답/최종 정책 결정을 분리 관측할 수 있는 결과 구조.
- `filterJobId`가 있는 호출(비동기 답변 경로를 흉내낸 통합 시나리오)에서
  `filter_decision` 저장까지 포함한 흐름.
- pipeline이 내부에 정적/공유 스레드풀을 하드코딩하지 않고 실행 자원을
  주입받는 구조인지 검증하는 구조적 테스트.

### Excluded

- 정규화 규칙, 고신뢰 로컬 사전, category mapping과 한국어·영어 threshold의
  실제 값 — 미결정. 모든 시나리오는 테스트 전용 fake 규칙·정책으로 검증한다.
- 독립 보조 판정기(fallback moderator) — #106 소관.
- 닉네임 동기 API·답변 비동기 워커에서 이 pipeline을 실제로 호출하는 배선,
  `deadline_at` 산정, `PUBLISHED_UNREVIEWED` 전환 — #106/#107 소관.
- 재시도 정책(`Retry-After`, exponential backoff), snapshot health 상태 머신,
  emergency migration — #108/#109 소관.
- 실제 OpenAI 계정으로의 네트워크 호출. 모든 외부 API 시나리오는 로컬
  stub/fake 서버로 대체한다.
- 인프라 apply, 배포, 실제 API 키 사용.

### 설계 가정 (executor 확인 필요)

이슈 #105의 "백엔드 영향"은 `DB: moderation_decision 기록`이라고 명시하지만,
"범위"는 job 생성을 다루지 않고 `filter_decision`은 `filter_job_id`에 대한
FK를 강제한다(`V10__create_filtering_schema.sql`). 두 요구를 모두 만족하는
가정은 다음과 같다.

- pipeline 호출에 `filterJobId`/`attemptGeneration`이 주어지면, 같은
  트랜잭션에서 `FilterDecision`을 저장한다(답변 비동기 경로가 이미 만든
  `FilterJob`을 넘겨주는 경우).
- `filterJobId`가 없는 호출(예: 닉네임 동기 사전 검증)에서는 영속화 없이
  판정 결과만 반환한다.

이 가정이 틀리면 시나리오 INT-001, INT-002, INT-004의 저장 단언이 바뀌어야
한다. 구현 착수 전 이 가정을 확정하거나 사람 승인으로 대체 설계를 정한다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #105 | 공급자의 단일 `flagged` 값이 최종 판정을 직접 결정하지 않는다 (`INV-PIPE-003`) |
| GitHub Issue #105 | 규칙 적중, 모델 응답과 최종 정책 결정을 각각 관측할 수 있다 (`INV-PIPE-005`) |
| GitHub Issue #105 | 닉네임과 답변이 정책·결과 계약을 공유하되 실행 용량은 공유하지 않는다 (`INV-RES-001`, `INV-RES-002`) |
| GitHub Issue #105 | `INV-PIPE-001`, `002`, `004`를 위반하지 않는다 — 이 세 항목은 이슈 본문에 정의가 없어 `확인 필요`로 남긴다(§4 위험 목록 참고). |
| GitHub Issue #105 | timeout/error는 판정으로 변환하지 않고 호출 경로에 돌려준다 |
| GitHub Issue #103 (Foundation) | pipeline은 `filter_job`/`filter_decision`의 기존 authority·append-only 원장 모델을 위반하지 않는다 (`INV-GEN-004`, `INV-GEN-005`) |
| `docs/product` DESIGN.md 결정 6 | 정규화 → 고신뢰 규칙 → 규칙 미확정 입력만 모델 판정 → 정책 결합 순서 |
| `docs/product` DESIGN.md 결정 7 | 외부 관리형 Moderation API 사용, 벤더 중립 어댑터로 내부 스키마 변환 |
| `docs/product` DESIGN.md 결정 8 | 1차 공급자 OpenAI `omni-moderation-latest`, 공급자 `flagged`를 최종 판정으로 직접 사용하지 않음 |
| `src/main/resources/db/migration/V10__create_filtering_schema.sql` | `filter_decision`은 `filter_job_id` FK와 `(filter_job_id, attempt_generation)` 유일 인덱스를 강제 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 공급자 `flagged`/`category_scores`가 정책 엔진을 우회해 직접 최종 판정에 쓰임 | 서비스 차단 기준이 벤더 정책으로 암묵 이전됨(`INV-PIPE-003` 위반) | 중 | P0 | UNIT-003, UNIT-004 |
| OpenAI timeout/error가 `ALLOW`로 대체됨(fail-open 회귀) | 미검사 유해 콘텐츠 노출, 닉네임 fail-closed 경계 붕괴 | 중 | P0 | UNIT-005, UNIT-006 |
| 로컬 규칙 `BLOCK` 적중 후에도 OpenAI를 호출함 | 불필요한 외부 호출·지연·비용, 규칙 확정 결과를 모델이 다시 흔들 위험 | 중 | P0 | UNIT-001 |
| 규칙 적중/모델 응답/최종 결정이 하나로 뭉쳐 기록됨 | 오탐·미탐 원인을 규칙/모델/정책 중 무엇인지 사후 구분 불가(`INV-PIPE-005` 위반) | 중 | P0 | UNIT-008, INT-001 |
| pipeline이 내부에 정적/공유 스레드풀·HTTP 클라이언트를 하드코딩함 | 답변 backlog 급증이 닉네임 동기 응답 지연·장애로 전파(`INV-RES-001/002`, 결정 3 위반) | 중 | P1 | UNIT-013, INT-006 |
| OpenAI 응답 파싱이 문서화되지 않은 필드나 필드 누락에 예외를 던짐 | 정상 응답도 판정 불가로 처리되어 가용성 저하 | 낮음 | P1 | UNIT-011, UNIT-012 |
| `filterJobId`+`attemptGeneration` 중복 호출이 `filter_decision`을 두 번 기록 | append-only 원장 무결성 훼손, `INV-GEN` 계열 위반 가능성 | 낮음 | P1 | INT-004 |
| `requestedReleaseId`와 실제 `actualModel`이 섞여 기록되지 않음 | 릴리스 재현성 상실(`INV-REL-005`, 결정 10 위반 가능성) | 낮음 | P1 | UNIT-007, INT-001 |
| `INV-PIPE-001`, `002`, `004`의 정의를 이슈 본문에서 찾을 수 없음 | 이 세 불변식에 대한 커버리지를 주장할 수 없음 | 확인 필요 | P0(확인) | 사람 확인 — 정의 출처(별도 문서/Notion 등) 필요 |

## 5. Unit scenarios

모든 unit 시나리오는 Spring 컨텍스트 없이 순수 객체와 테스트 전용
fake(`TextNormalizer`, `LocalRuleEngine`, `ModerationProviderClient`,
`PolicyEngine` 각각의 fake 구현)로 pipeline 오케스트레이션 로직만 검증한다.
OpenAI 응답 파싱(UNIT-011, UNIT-012)만 예외적으로 고정 JSON 문자열을 입력으로
쓴다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-001 | fake 로컬 규칙이 정규화된 입력에 `BLOCK`을 반환하도록 구성됨 | pipeline을 실행한다 | 최종 verdict는 `BLOCK`이고, fake `ModerationProviderClient`는 한 번도 호출되지 않는다 | P0 | Feature executor |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-002 | fake 로컬 규칙이 미적중(`NO_MATCH`)을 반환하도록 구성됨 | pipeline을 실행한다 | fake `ModerationProviderClient`가 정확히 한 번, "정규화된" 텍스트(원문이 아님)로 호출된다 | P0 | Feature executor |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-003 | 로컬 규칙 미적중, fake 공급자 응답 `flagged=true`(고점수), fake 정책 엔진이 그 카테고리·점수를 해석해 `ALLOW`를 반환하도록 구성됨 | pipeline을 실행한다 | 최종 verdict는 `ALLOW`다 — 공급자 `flagged`가 아니라 정책 엔진의 해석 결과를 따른다(`INV-PIPE-003`) | P0 | Feature executor |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-004 | 로컬 규칙 미적중, fake 공급자 응답 `flagged=false`, fake 정책 엔진이 다른 근거(예: 언어별 threshold)로 `BLOCK`을 반환하도록 구성됨 | pipeline을 실행한다 | 최종 verdict는 `BLOCK`이다 — `flagged=false`가 자동으로 `ALLOW`를 뜻하지 않는다 | P0 | Feature executor |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-005 | fake `ModerationProviderClient`가 timeout(예외 또는 timeout 신호)을 발생시키도록 구성됨 | pipeline을 실행한다 | pipeline은 `ALLOW`/`BLOCK` 중 어느 것도 반환하지 않고 판정 불가 신호(예외 또는 명시적 미결정 결과)를 호출자에게 그대로 전달한다. fake `PolicyEngine`은 호출되지 않는다 | P0 | Feature executor |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-006 | fake `ModerationProviderClient`가 서버 오류(5xx 상당)를 발생시키도록 구성됨 | pipeline을 실행한다 | UNIT-005와 동일하게 판정 불가로 처리되며 `ALLOW`로 대체되지 않는다 | P0 | Feature executor |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-007 | 요청 `requestedReleaseId`와 공급자 응답이 보고하는 실제 `model` 문자열이 서로 다르게 구성됨(예: release의 `modelSnapshot`과 응답 `model`이 다른 값) | pipeline을 실행한다 | 결과에 `requestedReleaseId`와 응답이 보고한 실제 `model`이 각각 별도 필드로 남는다 — 하나가 다른 하나를 덮어쓰지 않는다 | P1 | Feature executor |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-008 | 로컬 규칙 미적중, 공급자 응답과 정책 결과가 서로 다르게 구성됨(UNIT-003 상황 재사용) | pipeline을 실행한다 | 결과 구조에서 (a) 규칙 적중 여부, (b) 공급자 원시 응답(카테고리·점수), (c) 최종 정책 결정을 각각 독립적으로 읽어낼 수 있다(`INV-PIPE-005`) | P0 | Feature executor |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-009 | 콘텐츠 종류가 `NICKNAME`인 요청과 `ANSWER`인 요청을 각각 구성 | pipeline을 실행한다 | fake `PolicyEngine`에 전달되는 입력에 콘텐츠 종류와 언어가 그대로 보존된다(정책 엔진이 종류별로 분기할 수 있는 계약 확인) | P1 | Feature executor |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-010 | 서로 다른 `normalizationRef`를 가진 두 `FilterRelease`로 같은 원문을 처리 | pipeline을 각각 실행한다 | fake `TextNormalizer`가 매번 해당 release의 `normalizationRef`와 함께 호출된다 — 정규화가 release에 귀속됨을 확인 | P1 | Feature executor |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-011 | OpenAI Moderation API 문서에 기재된 형태의 고정 JSON 응답 문자열(`flagged`, `categories`, `category_scores`, `model` 포함) | 벤더 중립 어댑터의 응답 매퍼로 파싱한다 | `flagged`, 카테고리별 boolean, 카테고리별 점수, `model` 값이 내부 결과 객체에 정확히 매핑된다 | P0 | Feature executor |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-012 | 위 JSON에 문서화되지 않은 추가 카테고리 키가 포함됨 | 같은 매퍼로 파싱한다 | 예외 없이 알려진 필드는 매핑되고 알 수 없는 키는 무시된다(공급자 스키마 확장에 대한 관용성) | P1 | Feature executor |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-013 | 서로 다른 두 `Executor`(또는 동시성 한도)를 주입받은 pipeline 인스턴스 두 개를 생성 | 각 인스턴스로 OpenAI 어댑터 호출을 트리거한다(fake HTTP 계층) | 각 인스턴스는 자신에게 주입된 executor/동시성 한도만 사용하고, 정적 필드나 클래스 공유 상태를 통해 서로 영향을 주지 않는다(`INV-RES-001/002`의 구조적 전제) | P1 | Feature executor |

## 6. Integration scenarios

Spring context + Testcontainers Postgres(`PostgisContainerIntegrationTestSupport`
패턴 재사용) + OpenAI 호출은 JDK 내장 `com.sun.net.httpserver.HttpServer`로
띄운 로컬 fake 서버로 대체한다(신규 외부 라이브러리 의존성을 추가하지 않기
위한 선택 — WireMock 등을 도입하려면 별도 승인 필요, §8 참고).

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-INT-001 | pipeline 서비스, OpenAI 어댑터, `FilterDecisionRepository`, Postgres | 로컬 fake 서버가 `flagged=true`(정책상 `BLOCK`) 응답을 반환하도록 구성. `PROMOTED` `FilterRelease`와 `FilterJob`(attempt 1)을 미리 저장 | 해당 job/release/attempt로 pipeline을 실행한다 | `filter_decision`에 행이 1개 생성되고 `verdict='BLOCK'`, `requested_release_id`, `actual_model`이 정확히 기록된다 | `filter_decision`/`filter_job`/`filter_release` 테이블 정리 |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-INT-002 | 위와 동일 | 로컬 규칙이 `BLOCK`을 반환하도록 구성(모델 호출 불필요 케이스) | pipeline을 실행한다 | `filter_decision`에 `verdict='BLOCK'`이 저장되고, fake OpenAI 서버가 받은 요청 수는 0이다 | 동일 |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-INT-003 | 위와 동일 | 로컬 fake 서버가 응답 전 pipeline에 설정된 timeout보다 오래 지연하도록 구성 | pipeline을 실행한다 | `filter_decision`에는 어떤 행도 생성되지 않고, 호출자에게 판정 불가 신호(예외)가 전달된다 | 동일 |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-INT-004 | 위와 동일 | 동일 `filterJobId`+`attemptGeneration`으로 pipeline을 두 번 순차 호출(같은 fake 응답) | 두 번째 호출을 실행한다 | 두 번째 호출은 `uq_filter_decision_job_attempt` 유일 인덱스 위반으로 거부되거나(멱등 처리로 기존 행을 그대로 반환), 어느 쪽이든 `filter_decision` 행이 정확히 1개만 남는다 | 동일 |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-INT-005 | 위와 동일 | `filterJobId` 없이(닉네임 동기 사전 검증을 흉내낸) pipeline 호출을 구성 | pipeline을 실행한다 | 판정 결과는 정상 반환되지만 `filter_decision`에는 어떤 행도 생성되지 않는다(§2 설계 가정 검증) | 동일 |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-INT-006 | pipeline 서비스, 두 개의 서로 다른 Spring 빈 구성(닉네임용/답변용 executor 프로필을 흉내냄) | 답변용 executor를 인위적으로 포화(saturate)시킴 | 닉네임용 executor로 구성된 pipeline 인스턴스를 동시에 호출한다 | 닉네임 경로 호출이 답변 경로 포화의 영향 없이 완료된다(대기열 공유가 없음을 통합 수준에서 재확인) | executor 종료, 스레드 정리 |

## 7. Cross-cutting scenarios

### Database and transactions

- `filter_decision` insert는 `filter_job_id` FK와 `(filter_job_id,
  attempt_generation)` 유일 인덱스를 이미 DB 제약으로 강제한다(V10
  마이그레이션). pipeline 계층이 이 제약을 우회하는 별도 삽입 경로를 만들지
  않는지 INT-004에서 확인한다.
- OpenAI 호출은 절대 DB 트랜잭션 내부에서 수행하지 않는다(외부 I/O를 커넥션을
  잡은 채로 기다리지 않음) — 어댑터 호출과 `filter_decision` 저장 트랜잭션의
  경계를 코드 검토와 INT-001/INT-003으로 확인한다.
- INT-003(timeout)에서 부분 기록이 남지 않는지(트랜잭션 rollback 또는 애초에
  쓰기 시도 없음) 확인한다.

### Concurrency and idempotency

- INT-004는 같은 `filterJobId`+`attemptGeneration`의 중복 실행을 다룬다.
- UNIT-013/INT-006은 실행 자원(스레드풀/동시성 한도)이 인스턴스별로 격리되어
  경로 간 경합이 없음을 다룬다.
- pipeline 자체는 재시도를 하지 않는다(#108 소관) — 재시도 부재를 명시적으로
  단언하는 시나리오는 이 계획에서 다루지 않고, "pipeline이 스스로 재시도
  루프를 도는지" 코드 검토로 별도 확인한다.

### External APIs

- OpenAI 요청/응답 스키마 매핑은 UNIT-011/012에서 순수 단위로, 실제 HTTP
  경계(timeout·지연)는 INT-003/INT-006에서 로컬 fake 서버로 검증한다.
- 실제 네트워크 호출, 실제 API 키, 실제 OpenAI 엔드포인트는 어떤 시나리오에서도
  사용하지 않는다.
- 어댑터가 API 키 등 secret을 로그나 예외 메시지에 남기지 않는지 코드 검토로
  확인한다(민감정보 노출 방지, 별도 자동 테스트는 필요 시 추가).

### Failure recovery and reconciliation

- timeout/error가 `filter_decision`에 어떤 흔적도 남기지 않아야, 이후 재시도
  (호출자 책임)가 같은 `attemptGeneration`으로 안전하게 재시도할 수 있다 —
  INT-003이 이 전제를 검증한다.
- 판정 불가 신호가 예외 타입/결과 타입으로 명확히 구분되어 호출자가 "정상
  ALLOW"와 혼동할 수 없는 형태인지 UNIT-005/006에서 타입 수준으로 확인한다.

## 8. Test data and isolation

- Fixtures: `FilterRelease.candidate(...).promote(...)` 형태로 `PROMOTED`
  release를 만드는 테스트 헬퍼(`FilterReleaseTest`의 `candidate()` 패턴 재사용).
  OpenAI 응답 고정 JSON은 DESIGN.md 결정 8에 인용된 공식 필드 형태(`flagged`,
  `categories`, `category_scores`, `model`)를 그대로 본뜨되 실제 API 키·계정
  정보는 포함하지 않는다.
- Database isolation: `PostgisContainerIntegrationTestSupport` +
  `@ActiveProfiles({"test", ...})` 패턴을 `FilterReleaseRegistryIntegrationTest`
  와 동일하게 재사용한다. `@BeforeEach`에서 `filter_decision`, `filter_job`,
  `filter_release` 순서로 정리한다(FK 순서 준수).
- Clock/randomness: `FilterReleaseRegistryService`와 동일하게 `Clock`을
  생성자 주입하고 단위 테스트에서 고정 `Instant`를 사용한다.
- External API doubles: 신규 외부 라이브러리(WireMock 등) 없이 JDK 내장
  `com.sun.net.httpserver.HttpServer`로 로컬 fake 서버를 띄운다. 이 선택이
  부적절하다고 판단되면(예: 정교한 매칭이 필요) 사람 승인을 받아 WireMock
  도입으로 전환한다 — 이번 계획에서는 임의로 새 의존성을 추가하지 않는다.
- Cleanup: fake HTTP 서버와 테스트 전용 `ExecutorService`는 각 테스트
  종료 시 명시적으로 `stop()`/`shutdown()`한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다. OpenAI API 키는 어떤 테스트
설정에도 등장하지 않는다(모든 외부 호출은 fake로 대체).

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Feature executor | `src/main/java/com/dnd/qello/filtering/moderation/**`(신규 패키지: pipeline 서비스, `TextNormalizer`/`LocalRuleEngine`/`ModerationProviderClient`/`PolicyEngine` port, 결과 타입), `src/main/java/com/dnd/qello/filtering/error/FilteringErrorCode.java`(신규 오류 코드 추가만, 기존 코드 값 변경 금지), `src/test/java/com/dnd/qello/filtering/moderation/**` | UNIT-001 ~ UNIT-013 | `./gradlew test --tests "com.dnd.qello.filtering.moderation.*"` |
| 2 | Feature executor | `src/main/java/com/dnd/qello/filtering/moderation/openai/**`(OpenAI 어댑터, Spring 설정/executor 배선), `src/integrationTest/java/com/dnd/qello/ModerationPipelineIntegrationTest.java` | INT-001 ~ INT-006 | `./gradlew integrationTest --tests "com.dnd.qello.ModerationPipelineIntegrationTest"` |

두 순서 모두 같은 실행 에이전트(Feature executor)가 순차로 담당한다. Order 2는
Order 1이 정의하는 port 인터페이스에 의존하므로 병렬 실행하지 않는다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현 (UNIT-001~006, UNIT-008, UNIT-011, INT-001~004)
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석 (애플리케이션 경계, DB 제약, 동시성, 트랜잭션, 외부 API,
      장애 복구 — `agents/test-executor.md` 기준)
- [ ] 테스트 보고서 생성 (`templates/test-report.md`)
- [ ] `INV-PIPE-001`, `002`, `004`의 정의를 확인하지 못한 상태로 남아 있다면
      완료 보고에 `확인 필요`로 명시하고 커버리지를 주장하지 않는다.
- [ ] §2 "설계 가정"이 실제 구현과 다르게 확정되면 이 계획을 갱신한 뒤 진행한다.

## 11. Human approval

- Reviewer: tkv00
- Decision: Approved — §2 설계 가정(`filterJobId` 유무에 따른 조건부 영속화)과
  `INV-PIPE-001`/`002`/`004` 미정의 상태를 인지한 채로 승인함. 정의가 확인되지
  않으면 완료 보고에서 해당 항목을 `확인 필요`로 남긴다.
- Approved at: `2026-08-11T21:05:00+09:00`

## 12. Review addendum

PR #130 CodeRabbit 리뷰(Major)에서 원래 계획의 INT-003(timeout)만으로는
`OpenAiModerationProviderClient`의 HTTP 5xx 오류 변환 경로가 검증되지 않는다는
지적을 받아 다음 시나리오를 추가한다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-105-MODERATION-PIPELINE-INT-007 | 위와 동일 | 로컬 fake 서버가 HTTP 503과 오류 본문을 반환하도록 구성 | pipeline을 실행한다 | `filter_decision`에는 어떤 행도 생성되지 않고, 호출자에게 `FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE`를 담은 예외가 전달된다 | 동일 |

INT-007은 기존 §2 설계 가정이나 P0 범위를 변경하지 않는 순수 커버리지 보강이라
별도 재승인 없이 추가한다.
