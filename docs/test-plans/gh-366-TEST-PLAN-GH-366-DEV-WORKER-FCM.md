# Test Plan: TEST-PLAN-GH-366-DEV-WORKER-FCM

> Created at: `2026-11-21T11:39:34.360Z`
> GitHub Issue: `#366`
> Task: `TASK.md` / branch `chore/gh-366-dev-worker-fcm`
> Status: `APPROVED`
> 단위 6개 + 통합 3개 = 자동 검증 시나리오 ID 9개, 병합 후 수동 3개. 매개변수별 실행 횟수와 다르다.
> 이번 단계는 계획 작성뿐이며 아래 테스트와 구현 단계 검증 명령은 실행하지 않았다.

## 1. Objective

dev 설정만으로 알림 생성·전달용 다섯 worker를 활성화하고 FCM provider를 선택해도
설정 바인딩과 실제 애플리케이션 기동이 성공하는지 검증한다. 제외 worker의 우발적
활성화, 필수 credential 누락을 가리는 fixture, 자동 scheduler의 공유 DB 오염과
테스트 중 실제 Google OAuth/FCM 호출을 막는다.

서버 자동 검증과 배포 후 실기기 검증은 분리한다. context 기동이나 가짜 provider의
Accepted만으로 실제 FCM 발송·Android 수신을 성공했다고 판정하지 않는다.

## 2. Scope

### Included

- 실제 `application.yml` + `application-dev.yml`의 dev 바인딩과 provider 선택.
- 네 outbox worker와 push dispatch의 서비스/adapter bean, scheduler pool 및 제외 조건.
- dev의 FCM 설정 누락·잘못된 JSON 실패와 가짜 service account credential 파싱.
- 기존 dev FID 등록·해지, AES 보호, health 공개·나머지 actuator 비공개 회귀.
- 두 설정 롤백의 기동 계약 및 문서 검토, 병합 후 수동 검증 절차.
- 이번 단계의 유일한 수정 파일은 이 계획이다. 기존 미커밋 `TASK.md`는 보존한다.

### Excluded

- 이번 단계의 production 코드·설정·테스트·기타 문서 수정 및 테스트 실행.
- production Java 변경, migration, infra/workflow/secret 주입 경로 변경.
- sweep 세 종류와 answer-moderation 활성화, 운영 수치 결정, prod 활성화.
- 실제 credential·FID·`.env` 읽기, 클라우드 호출·배포·원격 DB 접근.
- commit/push/PR 생성·Issue/PR 코멘트. 아래 수동 절차도 이번 단계 실행하지 않는다.
- 답변 제출과 `ANSWER_*` 생성·실발송. 제공된 TASK/지시의 확인 사실에 따르면
  `AnswerModerationIntake` bean이 없어 제출이 `MODERATION_INTAKE_NOT_CONFIGURED`로
  실패하고, moderation verdict 이전에는 해당 알림이 생성되지 않는다.
  Issue 본문의 `ANSWER_*` 생성 서술을 이번 인수 조건으로 해석하지 않는다.

## 3. Source requirements

축약 ID에는 모두 `TEST-PLAN-GH-366-DEV-WORKER-FCM-` 접두사를 붙인다.

| Source | Requirement / acceptance criterion | Evidence |
| --- | --- | --- |
| Issue #366 범위 / TASK | global ON, 다섯 worker 설정 바인딩, pool-size가 활성 worker 수 이상 | UNIT-001, INT-001 |
| TASK 완료 조건 / 지시 1 | 지정 다섯 서비스와 adapter 등록, sweep 세 adapter와 moderation 세 adapter 제외 | INT-001 |
| Issue #366 / 지시 2 | dev 기본 provider=fcm, FCM 관련 bean 존재, noop 부재, credential 누락 실패 | UNIT-002~003, INT-001 |
| 지시 3 | 자동 실행이 공유 DB와 테스트 순서를 오염시키거나 외부 발송하지 않음 | UNIT-006, INT-001~003, §7~8 |
| #365 INT-006 / 지시 4 | 기존 dev=noop 기대 교체, 실제 AES 기반 FID 등록·해지 유지 | UNIT-002, INT-001~002 |
| Issue #366 롤백 / 문서 10절 | global false 또는 provider noop으로 변경 후 재배포 | UNIT-004~005, §7 문서 검토 |
| Issue #366 실발송 완료 조건 | 배포 성공, SENT·provider_message_id, 중복 조사, Android 세 상태 수신 | §12 MANUAL-001~003 |
| TASK / 지시의 범위 정정 | AnswerModerationIntake 미등록과 ANSWER_* 제외를 명시 | §2, §11~12 |
| AGENTS §10 / TASK Validation | harness check, pr-ready/project-tests, hooks, diff 검증 | §9~10, 승인 후 실행 |

### 현재 소스에서 관찰한 계약

- `WorkerSchedulingConfiguration`은 global enabled가 true일 때만 properties, pool,
  identity, metrics와 `@EnableScheduling`을 제공한다. pool 검증은 양수만 강제하므로
  **pool-size >= 5는 dev 설정 인수 assertion**으로 별도 검증해야 한다.
- 지정 다섯 adapter는 global 및 개별 enabled가 모두 true여야 한다. fixed-delay에는
  initial-delay가 없으므로 큰 delay를 주어도 첫 실행을 막을 수 없다.
- answer-moderation adapter 세 개는 global이 아니라 자기 enabled만 조건으로 사용한다.
  따라서 global false만으로 모든 worker가 꺼진다고 일반화하지 않는다.
- 앞의 네 outbox 서비스는 `@Service`로 등록된다. OFF 검증에서 이 서비스까지 없다고
  기대하지 않는다. push dispatch 서비스/planner/retry는 global·push enabled에 조건부다.
- `PushConfiguration`은 dev에서 provider 미지정 시 noop을 기본으로 허용하지만 이번
  dev YAML은 명시적으로 fcm을 선택해야 한다. noop은 private nested 구현이므로
  `noOpPushProvider` bean 이름 부재 및 단일 provider 타입으로 확인한다.
- FCM 구성은 `PushProperties`, `fcmRestClient`, 실제
  `GoogleCredentialsFcmAccessTokenProvider`, `FcmHttpV1PushProvider`를 만든다.
  project-id/credential-json과 timeout 검증은 properties 생성 시 수행한다.
- 로컬 소스상 credential 생성자는 `GoogleCredentials.fromStream().createScoped()`만
  호출하며, `refreshIfExpired()`는 `accessToken()`에서 호출한다. 실제 HTTP 발송은
  provider의 `send()`에서 시작한다. Google 라이브러리 내부의 무통신 여부는 이번에
  실행 검증하지 않았으므로 fixture 토큰 endpoint 관찰과 호출 차단을 계획에 포함한다.
- `application-dev.yml` 값이 기본 YAML의 `QELLO_WORKER_SCHEDULING_ENABLED`
  placeholder보다 우선한다는 것은 TASK의 제공 사실이다. 환경변수 하나를 false로
  바꾸는 것을 롤백 절차로 안내하지 않는다.
- schema는 `notification_delivery(notification_id, push_device_id)` 유일성을 보장한다.
  DB 한 행이라는 사실은 provider 네트워크 요청이 한 번뿐이었다는 증거가 아니다.

근거는 현재 소스, Issue 본문, TASK, #365 승인 계획 및
`docs/push-production-setup.md` 10절과 ADR-0008이다. 과거 알림함 설계의
“현재 상태” 절은 당시 기록이며 현재 worker 존재 여부의 근거로 사용하지 않는다.
#364 apply와 #365 배포·secret 주입 완료는 TASK가 제공한 선행 사실이며 live 재검증하지 않았다.

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 필수 worker 수치/재시도 이름 누락 | dev 기동 실패 | 높음 | P0 | UNIT-001 실제 YAML 바인딩; base-delay/max-delay와 base-backoff/backoff-cap 구별 |
| global ON만 확인하고 adapter 일부 누락 | 생성 또는 발송 파이프라인 중단 | 중간 | P0 | INT-001 다섯 서비스·다섯 adapter·다섯 등록 작업 |
| sweep/moderation 우발 활성화 | 제외 작업 실행·데이터 변경 | 중간 | P0 | UNIT-001 flags, INT-001 세 sweep·세 moderation adapter 부재 |
| pool은 양수지만 활성 worker보다 작음 | 주기 실행 지연 | 중간 | P1 | UNIT-001 pool-size >= 활성 5개, INT-001 실제 core pool 일치 |
| 기존 noop send assertion 유지 | 실제 OAuth/FCM 호출 | 높음 | P0 | send 제거, UNIT-002와 INT-001의 실제 FCM bean 검증·네트워크 차단 |
| 기존 provider 단위 context가 dev worker까지 생성 | repository 누락으로 엉뚱한 실패 | 높음 | P0 | provider 전용 context는 global OFF만 덮어쓰기; 실제 worker wiring은 INT-001 소유 |
| credential 실패가 key/policy 누락에 가려짐 | 잘못된 실패 테스트가 통과 | 중간 | P0 | UNIT-003은 한 항목만 제거하고 구체적 cause 유형·설정 필드 확인 |
| cached dev context의 scheduler가 다음 클래스 DB 처리 | 간헐 실패·원치 않는 발송 | 높음 | P0 | refresh 이전 등록 포획, INT-001~003 context 종료, UNIT-006은 mock worker만 |
| 가짜 authorized_user만 검증 | 실제 service account key 파싱 문제 누락 | 중간 | P0 | 생성 RSA key의 PKCS#8 service_account fixture로 실제 생성자 검증 |
| noop 롤백의 SENT를 실발송으로 오인 | 검증 기록 오판 | 중간 | P0 | UNIT-005/§12: noop Accepted는 비발송, 미발송 작업을 성공 처리할 수 있음 명시 |
| DB 행 수만으로 중복 발송 없음 선언 | 재전송·응답 유실을 놓침 | 중간 | P0 | §12 DB 조사 + 승인된 provider/device 관측, 증거 없으면 UNKNOWN |
| 설정만으로 해결 못 하는 문제 | production Java 범위 침범 | 미확인 | P0 | §11의 BLOCKED 후보로 반환, production 변경 금지 |

## 5. Unit scenarios

새 중복 테스트를 늘리기보다 기존 테스트의 fixture와 assertion을 교체·확장한다.
모든 시나리오는 P0이며 pool capacity 부가 assertion은 P1이다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| UNIT-001 | 실제 기본/dev YAML, 환경·시스템 설정에서 격리한 dev context | properties만 등록해 config data 바인딩 | global 및 지정 다섯 enabled=true; pool-size=5로 활성 수 5 이상; outbox 네 개 각각 fixed-delay=PT5S, batch-size=20, lease-duration=PT1M, retry.max-attempts=5, retry.base-delay=PT5S, retry.max-delay=PT5M; push-delivery-dispatch는 fixed-delay=PT5S, batch-size=10, lease-duration=PT2M, retry.max-attempts=5, retry.base-backoff=PT10S, retry.backoff-cap=PT10M; constructor 검증 성공; 제외 flags는 null 블록 또는 false. 모두 결정된 dev 전용 fixture이며 운영 권장값 아님 | P0/P1 | E1 |
| UNIT-002 | 실제 dev YAML, 가짜 service_account JSON·project-id, 유효 보호 key/policy; provider 값은 덮어쓰지 않음 | provider 전용 context 기동, global만 OFF로 격리 | provider property=fcm, 단일 FcmHttpV1PushProvider 및 GoogleCredentialsFcmAccessTokenProvider·PushProperties·fcmRestClient 존재, noOpPushProvider 없음, 실제 AES 존재; 토큰 endpoint 요청 0; accessToken/send 호출 없음 | P0 | E2 |
| UNIT-003 | UNIT-002에서 project-id 또는 credential-json 한 항목만 누락/빈 값/공백, 별도 malformed JSON·파싱 불가 key 케이스 | 각 context 기동 | 의도한 PushProperties 바인딩 또는 credential 파싱 cause로 실패; key/policy/worker 미설정 때문인 실패는 불인정; fixture 원문 로그/실패 출력 비노출; 외부 요청 0 | P0 | E2 |
| UNIT-004 | 실제 dev 설정, valid FCM/key, global=false의 명시적 고우선 설정 | scheduling context 기동 | scheduler/identity/metrics와 core 다섯 adapter 없음; push dispatch 조건부 bean 없음; 제외 moderation도 계속 OFF. 기존 네 무조건부 서비스 부재는 요구하지 않음. FCM 설정은 여전히 필요 | P0 | E1 |
| UNIT-005 | 실제 dev YAML에 provider=noop만 명시적 덮어쓰기, FCM 값 제거, 보호 key는 유지 | provider context 기동; global만 테스트에서 OFF | 단일 noop, FCM properties/client/token-provider 부재, AES 존재; noop send의 Accepted는 외부 발송 증거가 아님. 기존 explicit noop 회귀 재사용 | P0 | E2 |
| UNIT-006 | dev YAML의 다섯 adapter, 실제 scheduler와 사전에 결과를 stub한 다섯 mock worker, 고정 Clock·local MeterRegistry | context refresh 전에 worker별 최초 호출 latch 등록 후 기동 | 각 adapter가 대응 worker를 호출하고 command의 batch/lease/retry가 바인딩값과 일치; 이름이 바뀐 worker로 오배선되지 않음; 각 latch를 bounded await, context 종료 후 scheduler shutdown. 실제 DB/provider는 연결하지 않음 | P0 | E1 |

UNIT-004의 전체 scheduling rollback과 UNIT-005의 provider rollback을 모두 자동 검증한다.
전자는 작업 실행을 멈추고 후자는 작업 실행 자체가 아니라 외부 전송 provider만 바꾸므로
동등하지 않다. UNIT-005는 provider 경계만 격리하며 ON 상태의 worker wiring은 INT-001과
UNIT-006이 담당한다. noop과 scheduler ON의 실발송 실험은 하지 않는다.

## 6. Integration scenarios

모든 통합 시나리오는 P0이며 기존 `DevProfileBootIntegrationTest`에서 수행한다.
Setup/Action/Expected는 Given/When/Then에 해당한다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| INT-001 | 실제 dev Spring context·PostGIS·worker 및 FCM 구성 | 실제 YAML의 enabled/provider/pool은 덮어쓰지 않음; 가짜 credential/key/media/auth; §8의 refresh 전 scheduler 포획과 네트워크 차단 | 전체 context 기동, bean과 등록 작업 관찰 | WorkerSchedulingProperties 검증 성공; 네 outbox 서비스 + PushDeliveryDispatchWorker, 지정 다섯 adapter 각 1개; scheduler·identity·metrics 존재; 실제 pool-size가 바인딩과 일치하고 >=5; 등록 작업 정확히 5개; 세 sweep 및 moderation execution/deadline/verdict adapter 없음; FCM 단일 provider와 관련 bean, noop 부재; OAuth/FCM 요청 0 | context 폐기, scheduler/가짜 endpoint 종료 |
| INT-002 | dev HTTP→실제 AES protector→DB | INT-001과 같은 실행 격리; test-only FID·인증 계정 | 기존 fid JSON 등록 후 동일 FID 해지 | 두 요청 204/빈 body; ACTIVE→REVOKED, ciphertext는 FID 원문과 다르고 실제 protector 복호화/Fingerprint 일치; 외부 발송 0. 등록·해지 검증 삭제 금지 | 기존 finally의 device/account 정리, context 폐기 |
| INT-003 | dev 보안·actuator·DB health | 같은 dev context/격리 | 기존 health와 나머지 actuator 요청 | health 인증 없이 200/UP; 기존 actuator 비공개 기대 유지; FCM 선택/worker 등록으로 보안 계약 변경 없음 | context 폐기 |

### #365 INT-006 변경 계약

- `devProfileContextLoads()`의 “dev 기본 noop/FCM bean 없음” 기대는 INT-001의
  실제 FCM 구성 기대로 교체한다. 기존 `provider.send(...)` 검증은 제거한다.
- `registersAndRevokesFidWithAutoConfiguredProtector()`는 INT-002로 계속 실행한다.
  test protector로 바꾸거나 등록·해지를 mock 처리하지 않는다.
- 기존 #230 health·노출 제한 테스트는 INT-003으로 회귀 실행한다. 원래 헤더와 생성
  시각은 보존하고 이번 확장 시각 및 원본 시나리오 ID만 추가한다.
- `PushConfigurationTest.devDefaultsToNoOpAndWiresRealTokenProtector()`도 실제 dev
  config data를 사용하므로 변경 대상이다. UNIT-002의 fcm fixture/AES 기대에 합치며
  noop은 explicitNoOp 회귀인 UNIT-005에서 유지한다.
- provider 없는 순수 `pushContext("dev")`의 코드 기본 fallback 정책 자체는 바꾸지
  않는다. 실제 dev YAML의 명시적 fcm 선택과 구별한다.
- provider 전용 `applicationConfigContext("dev")`는 global OFF로 격리한다. 그렇지
  않으면 PushDispatchWorkerConfiguration의 repository 요구 때문에 provider 검증
  이전에 실패한다. 이 격리를 dev worker 활성화 증거로 제출하지 않는다.
- key 오류를 확인하는 기존 dev 시나리오는 명시적 noop과 한 항목 실패 분리를 유지한다.
  #365 계획 원문 및 다른 회귀 파일을 이번 작업에서 일괄 수정하지 않는다.

## 7. Cross-cutting scenarios

### Database and transactions

- PostGIS/Flyway는 기존 통합 support를 사용한다. 공유 DB의 baseline 복원은 context를
  먼저 얻은 뒤 수행되므로 “BeforeEach에서 scheduler 종료”는 너무 늦다.
- INT-001~003은 실제 worker bean을 유지하되 자동 batch 실행은 refresh 이전에 차단한다.
  실제 DB 등록·해지 transaction 검증은 INT-002에 남긴다.
- 새 migration/lease/fencing 로직 변경은 없다. 기존 dispatch·등록 테스트를 전체 회귀로
  실행하며 새 DB 동시성 fixture를 추가하지 않는다.

### Concurrency and idempotency

- full boot에서는 등록되는 scheduling 작업을 포획하고 실행하지 않아 테스트 데이터
  삽입과 batch claim 사이의 경합을 제거한다. global/개별 enabled를 끄는 것으로
  INT-001을 통과시키면 요구사항 미검증이다.
- 실제 scheduler 실행은 UNIT-006으로 분리한다. 다섯 worker 결과를 context 생성 전에
  빈 batch로 stub하고 최초 호출 신호를 먼저 등록한다. DB·FCM 없이 배선만 검증한다.
- fixed sleep, 반복 polling, 아주 긴 delay로 첫 실행 회피, 실행 횟수의 시간창 추정은
  금지한다. bounded latch 결과를 assert하고 실패해도 finally에서 종료한다.
- callback 횟수의 정확한 상한은 요구하지 않는다. 주기 실행 특성상 추가 호출이 가능하다.
  동일 adapter 비중첩은 기존 scheduling 회귀를 재사용하며 새 시간 의존 테스트를 만들지 않는다.
- 공유 DB에서 dev context를 캐시한 채 다음 클래스가 실행되지 않도록 클래스 종료 시
  context를 폐기한다. 테스트 병렬 실행을 새로 켜지 않는다.

### External APIs

- 실제 credential 생성자·FCM provider를 mock으로 대체하지 않는다. 가짜 service account
  JSON의 파싱과 production bean 선택 경계를 유지한다.
- 생성자 바깥의 `accessToken()`과 FCM RestClient 요청 진입은 refresh 전 test-only
  BeanPostProcessor로 감시/차단한다. 실제 생성된 bean을 spy로 감싸 호출 시 즉시 테스트
  실패를 기록하고 외부 I/O 전에 중단한다. bean 타입과 production provider 연결은 보존한다.
- 생성자 중 OAuth 요청 가능성도 관찰하려고 가짜 credential의 token_uri는 context보다
  먼저 시작한 loopback 전용 endpoint로 설정한다. 요청이 오면 고정 안전 오류만 반환하고
  요청 수를 기록한다. credential/bearer/body를 출력하거나 정상 OAuth를 흉내 내지 않는다.
- 현재 로컬 코드상 토큰 갱신과 send는 명시적 메서드 호출 시점에만 시작하지만 library
  내부 무통신은 실행으로 확인할 항목이다. 생성 중 네트워크를 요구하거나 endpoint
  차단을 우회하면 PASS하지 않는다. 외부 접속을 허용하지 말고 §11 BLOCKED 후보로 반환한다.
- 새 HTTP wire/오류 분류 테스트는 만들지 않는다. 기존 FCM provider/dispatch 회귀를
  실행한다. FCM 수락과 Android 도착의 차이는 §12에서 확인한다.

### Failure recovery and reconciliation

- UNIT-003으로 잘못된 설정의 fail-fast를 검증한다. production Java 변경이 필요하면 중단한다.
- rollback은 두 경로 모두 자동 context 검증 + 문서 검토로 둔다. 배포 실험은 제외한다.
- 문서 10절은 실제 dev fixture 표, dev 전용/운영 권장 아님, 활성화 조건, 설정 변경 후
  재배포를 설명해야 한다. `enabled: false`는 scheduling 정지이고 FCM credential 요구는
  유지된다. `provider: noop`은 FCM credential 요구를 없애지만 보호 key는 계속 필수다.
- 결정됨(2026-11-21, @Byuntil): 발송 중지는
  `qello.worker.scheduling.enabled: false`를 우선 권고한다. `provider: noop`은 FCM
  자격 증명 문제일 때만 사용하며, worker가 계속 돌아 대기 중 delivery를 실제 발송 없이
  SENT로 처리한다는 경고를 문서 10절에 적는다. 기존 원장 상태를 수동으로 고치거나 재발송하지 않는다.
- 환경변수 `QELLO_WORKER_SCHEDULING_ENABLED=false`만으로 dev YAML을 이길 수 있다고
  쓰지 않는다. 문서 prose를 고정하는 테스트는 추가하지 않는다.

## 8. Test data and isolation

- 결정된 dev fixture의 5초 주기는 실기기 확인 지연을 줄이기 위한 값이다. push lease 산정 근거는 FCM 1건 connect 3s + read 5s = 8s, batch 10건의 최악 80s보다 긴 PT2M이며 운영 권장값은 아니다.
- FCM fixture: 테스트 안에서 JDK RSA KeyPairGenerator로 2048-bit key pair를 생성한다.
  private key는 PKCS#8 PEM으로 메모리에서 JSON 직렬화하며 파일·로그에 저장하지 않는다.
  `type=service_account`, 가짜 project_id/client_email/private_key_id/client_id,
  private_key 및 loopback token_uri를 사용한다. 실제 계정이나 서비스 계정은 사용하지 않는다.
  private key 파싱을 검사하므로 무작위 key 생성은 스케줄/기대값 결정에 사용하지 않는다.
- 이 계획에는 키/JSON 원문을 넣지 않는다. 잘못된 JSON·key는 별도 fixture다.
  기존 authorized_user fixture를 전부 갈아엎지 않고 dev service_account 경로만 보강한다.
- AES/HMAC fixture는 기존 32-byte 고정 배열과 Base64 helper, test-only key ID를 재사용한다.
  FID는 실제 발급값이 아닌 sentinel이며 DB/응답/log에서 평문 비노출을 유지한다.
- 단위 config context는 기존 helper처럼 시스템 환경과 시스템 property source를 제거하고
  classpath 기본/dev 설정만 로드한다. provider default 검증에서는 provider를 직접
  fcm으로 덮어쓰지 않는다. local YAML의 `.env` import는 로드하지 않는다.
- 통합 context도 환경의 실제 FCM/key/DB 값에 의존하지 않도록 가짜 값을 명시하고
  PostGIS ServiceConnection을 사용한다. test profile을 추가해 dev 보호 bean을 제외하지 않는다.
- scheduler 포획은 `DevProfileBootIntegrationTest` 안의 test-only static configuration/
  BeanPostProcessor에서 **refresh 전에** 설치한다. production workerTaskScheduler bean의
  설정·초기화는 유지하고 scheduling 등록 경계만 spy로 감싸 Runnable과 delay를 수집한다.
  예약 성공을 나타내는 취소 가능한 Future를 돌려주되 executor에는 작업을 제출하지 않는다.
  다섯 실제 ScheduledMethodRunnable의 대상/메서드·delay와 작업 수를 확인한다.
- 포획 장치는 그 자체가 worker 실행 증거가 아니다. UNIT-006에서 실제 scheduler로
  worker callback을 별도 관찰한다. 통합 테스트는 global OFF, worker mock, provider mock으로
  대체하지 않는다. 새 공용 helper 파일은 만들지 않고 해당 테스트 파일 안에 한정한다.
- 실패에서도 endpoint/executor/context와 spy 상태를 정리한다. DB fixture는 기존 finally를
  유지한다. 실행 전 장치 설치를 보장하지 못하면 실제 credential이나 인터넷으로 우회하지 않는다.

## 9. Execution contracts

사람 승인 후의 소유권이다. 이번 단계에는 실행 에이전트 기동·구현·테스트 실행이 없다.

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | E1 scheduling 테스트 | `src/test/java/com/dnd/qello/scheduling/config/WorkerSchedulingPropertiesTest.java`, `src/test/java/com/dnd/qello/scheduling/WorkerSchedulingConfigurationTest.java` | UNIT-001, UNIT-004, UNIT-006 | 해당 두 클래스 test |
| 1 | E2 provider 테스트 | `src/test/java/com/dnd/qello/notification/config/PushConfigurationTest.java` | UNIT-002~003, UNIT-005; 기존 #365 dev 기대/fixture 수정 포함 | 해당 클래스 test |
| 2 | E3 dev 통합 | `src/integrationTest/java/com/dnd/qello/DevProfileBootIntegrationTest.java` | INT-001~003; test-only 포획/차단·가짜 credential 포함 | 해당 클래스 integrationTest |
| 별도 계약 | 설정·문서 실행자 | `src/main/resources/application-dev.yml`, `docs/push-production-setup.md` 10절 | UNIT-001~006, INT-001의 입력; 롤백 수동 문서 검토 | 실제 YAML 바인딩과 문서 검토 |
| 3 | 오케스트레이터/독립 검증자 | 읽기·검증만: 실제 diff와 기존 회귀, 승인 후 보고서 | 전체 | 전체 게이트와 실행 증거 독립 확인 |
| 병합 후 | 오케스트레이터·모바일 담당자 | 원격 읽기/실기기 검증은 별도 승인; 이번 파일 소유권 아님 | MANUAL-001~003 | §12, 결과 기록도 별도 승인 |

같은 파일을 여러 실행자가 수정하지 않는다. E1/E2는 코드 소유가 분리되지만 설정 입력
변경이 반영된 후 검증한다. E3의 공유 DB 테스트는 병렬 실행하지 않는다.
새 테스트 파일은 계획하지 않는다. TASK와 production Java는 어느 실행자 소유에도 없다.

승인 후 Java 21·Docker/Testcontainers 환경에서 다음을 실행한다. 실행·보고 전
harness-test-run SKILL과 reporting 계약을 읽는다.

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.*' --tests 'com.dnd.qello.notification.config.PushConfigurationTest' --console=plain
./gradlew integrationTest --tests 'com.dnd.qello.DevProfileBootIntegrationTest' --console=plain
env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness test-run --id TEST-PLAN-GH-366-DEV-WORKER-FCM
env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness check
env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness pr-ready --project-tests
npm run hooks:validate
GIT_MASTER=1 git diff --check
```

장시간 검증은 monitor로 실행하고 종료 코드·실패 원인을 확인한다. 전체 회귀에 기존
FCM adapter/dispatch, FID 등록·해지, 기본 test profile scheduling OFF가 포함되어야 한다.
실제 `.env`를 로드하는 기존 local-profile 회귀 경로가 있으면 승인 없는 `.env` 읽기를
하지 않도록 실행 환경을 분리하고, 불가능하면 해당 검증을 BLOCKED로 보고한다.
실패를 skip/suppress하거나 다른 오류가 나도록 fixture를 약화하지 않는다.

## 10. Completion criteria

### 이번 계획 단계

- [x] Issue·branch·TASK #366 일치와 기존 TASK 변경 확인.
- [x] template 1~11절, 단위 6개·통합 3개, 소유 파일 및 완료 조건 추적.
- [x] 실제 dev 설정의 pool/worker 조건, #365 dev noop 기대 교체·FID 회귀 보존.
- [x] 가짜 service account/test key, scheduler·외부 I/O 격리, 두 롤백의 검증 범위 명시.
- [x] 실기기 확인을 병합 후 수동 단계로 분리하고 ANSWER_* 제외 기록.
- [x] DRAFT 유지. 테스트 작성·실행·설정 변경·배포·commit/push 없음.

세션의 apply_patch 전용 파일 변경 지시에 따라 `harness test-plan` scaffold는 실행하지
않고 `templates/test-plan.md` 전 절을 이 파일에 구체화했다. 계획 단계의 확인은
산출물 범위·정책 검토와 diff 공백 검사이며 제품 검증 성공을 의미하지 않는다.

### 사람 승인 후 구현·서버 검증

- [ ] 오케스트레이터의 §11 최종 승인 표시 후 결정된 dev fixture 수치로 지정 파일만 수정.
- [ ] 모든 P0/P1 assertion 통과, 실제 config data를 상수 복제 테스트로 대체하지 않음.
- [ ] JUnit 5·한국어 DisplayName·실제 확장 ISO 시각·원본 시나리오 ID 헤더 준수.
- [ ] 단위/통합 및 저장소 게이트 통과. 문서 prose 고정 테스트 없음.
- [ ] DB·transaction·동시성·외부 API·장애 복구·비밀 비노출 결과 분석.
- [ ] templates/test-report.md 기반 보고서 및 독립 검증. 미실행 항목은 BLOCKED.
- [ ] §12 증거 확보 전 실발송·중복 없음·Android 수신 완료를 선언하지 않음.

## 11. Human approval

- Reviewer: @Byuntil
- Decision: `APPROVED` — 아래 결정을 반영한 계획을 승인했다. 승인 증거: https://github.com/dnd-side-project/dnd-15th-2-backend/issues/366#issuecomment-7062302661
- Approved at: 2026-11-21

### 결정됨(2026-11-21, @Byuntil)

- dev 전용 fixture(운영 권장값 아님): pool-size=5. outbox 네 개
  (`direction-matching`, `recipient-notification-fan-out`, `notification-fan-out`,
  `report-resolution-fan-out`) 각각 fixed-delay=PT5S, batch-size=20, lease-duration=PT1M,
  retry.max-attempts=5, retry.base-delay=PT5S, retry.max-delay=PT5M.
  push-delivery-dispatch는 fixed-delay=PT5S, batch-size=10, lease-duration=PT2M,
  retry.max-attempts=5, retry.base-backoff=PT10S, retry.backoff-cap=PT10M.
  추적: UNIT-001, §8의 주기·lease 근거.
- 계획 범위는 단위 6개 + 통합 3개 + 병합 후 수동 3개로 승인되었다.
  시나리오 수와 §9 소유 파일은 유지하며 최종 승인 표시는 오케스트레이터가 기록한다.
- 발송 중지는 `qello.worker.scheduling.enabled: false` 우선 권고로 확정한다.
  `provider: noop`은 FCM 자격 증명 문제일 때만 사용하며, worker가 계속 돌아
  대기 중 delivery가 실제 발송 없이 SENT로 처리된다는 경고를 문서에 적는다.
  추적: UNIT-004~005, §7 장애 복구.
- 병합 후 담당자는 MANUAL-001 오케스트레이터, MANUAL-002 오케스트레이터와 모바일 담당,
  MANUAL-003 모바일 담당으로 확정한다. 추적: §12 담당 표.

남은 확인 항목은 수동 검증에 필요한 접근 권한과 모바일 증거 수집 방식이다.
이미 결정된 수치·범위·롤백 권고·담당자를 다시 승인 요청하지 않는다.

### 상태 구분과 BLOCKED 후보

- CONFIRMED(지시/TASK 제공): 다섯 worker ON, fcm 선택, 제외 worker/production Java
  변경 금지, ANSWER_* 제외, dev YAML 우선순위. #364/#365 선행 완료는 제공 사실이다.
- ASSUMED(계획 제안): scheduler 등록 경계 포획과 실제 생성 bean spy로 네트워크를 막는
  방식, RSA service_account fixture, 두 롤백을 자동 context 테스트로 다루는 선택.
  이번 단계에는 해당 장치를 실행하거나 안전성을 실증하지 않았다.
- UNKNOWN: library 내부의 기동 중 무통신 실행 증거,
  실제 test-server/Google/Android 상태, provider 중복 요청 관측 가능성.
- BLOCKED 후보 B1: 실제 dev context에서 설정만으로 충족하지 못하는 production Java
  의존성/검증 문제가 나오면 범위 밖 변경 없이 반환한다. 현재 정적 조사만으로 확정한
  production 결함은 없다.
- BLOCKED 후보 B2: 승인된 테스트 파일 안에서 refresh 전 scheduler 포획이나 credential/
  HTTP 요청 차단을 구현할 수 없으면 외부 호출을 허용하지 않고 계획/소유 범위 결정을 요청한다.
- BLOCKED 후보 B3: Java/Docker 환경 부족 또는 승인 없는 `.env` 접근이 필수 회귀의
  전제라면 해당 명령·영향을 보고한다. 이번 단계 환경 검증은 하지 않았다.
- ANSWER_*는 제외 범위이지 다섯 worker의 기동 검증을 막는 항목이 아니다.
  이를 이번 실기기 수신 대상으로 다시 요구하면 범위 재결정이 필요하다.

## 12. 병합 후 수동 검증

서버 테스트로 실기기 FID의 FCM 수락·Android 수신을 증명할 수 없다. 자동 배포가
완료된 뒤 승인된 담당자가 아래 절차를 수행한다. 이번 단계에는 쿼리를 실행하거나
메시지를 발송하지 않는다. 실기기 FID 대신 등록 결과의 논리적 device ID를 바인딩한다.
아래 `<...>`는 placeholder이며 실제 값은 실행 환경에서만 바인딩하고 공개하지 않는다.

| ID | 담당 | 절차 | 완료 증거 |
| --- | --- | --- | --- |
| MANUAL-001 | 오케스트레이터 | merge commit에 해당하는 test-server 자동 배포 성공, provider=fcm 구성 확인; 등록된 Android 기기로 허용된 알림 경로를 발생시키고 아래 조회 | 해당 delivery SENT, sent_at 존재, provider_message_id 존재, noop 메시지 ID가 아님. 원문 provider ID/주소는 보고하지 않음 |
| MANUAL-002 | 오케스트레이터 + 모바일 담당 | 같은 notification/device의 DB 조회와 provider 요청·기기 수신 증거 대조 | 중복 row 0뿐 아니라 실제 중복 요청/수신 여부 기록. 접근 가능한 감사 근거가 없으면 중복 발송 없음은 UNKNOWN |
| MANUAL-003 | 모바일 담당 | Android foreground/background/앱 종료 각각 새로운 허용 알림으로 수신 및 표시 확인 | 앱 상태별 발생 시각·수락 여부·수신 횟수·수신/표시 결과. 기기 ID/FID·credential 원문 없는 요약을 승인된 PR/Issue 기록 경로로 전달 |

허용 경로는 `direction-matching → recipient-notification-fan-out`의
`DIRECTION_POST_RECEIVED` 및 기존 notification/report-resolution fan-out이 처리하는
허용 유형이다. ANSWER_*를 발생시키기 위해 moderation을 켜거나 답변 제출을 우회하지 않는다.
수신 미확인 시 quiet hours/global/type preference·일일 예산·기기 ACTIVE 상태·콘텐츠
유효성을 먼저 구분하고, 임의 원장 수정이나 반복 재전송을 하지 않는다.

```sql
-- 등록 과정에서 안전하게 확인한 device와 검증 notification만 조회한다.
SELECT d.status,
       d.sent_at IS NOT NULL AS has_sent_at,
       NULLIF(BTRIM(d.provider_message_id), '') IS NOT NULL AS has_provider_message_id,
       COALESCE(d.provider_message_id = 'noop-provider-message', false) AS is_noop_result,
       d.attempt_count
FROM notification_delivery d
JOIN notification n ON n.id = d.notification_id
JOIN push_device p ON p.id = d.push_device_id
WHERE p.id = <push-device-id>
  AND n.id = <notification-id>
  AND n.recipient_id = <test-user-id>;

-- 결과가 1행이고 SENT이며 has_sent_at/has_provider_message_id=true, is_noop_result=false인지 확인한다.
-- FID 원문·ciphertext·fingerprint·provider message ID는 조회하지 않는다.

SELECT COUNT(*) AS duplicate_pair_count
FROM (
    SELECT notification_id, push_device_id
    FROM notification_delivery
    WHERE push_device_id = <push-device-id>
      AND notification_id = <notification-id>
    GROUP BY notification_id, push_device_id
    HAVING COUNT(*) > 1
) duplicates;
```

두 번째 쿼리의 기대값 0은 schema상 행 중복 확인에 한정한다. attempt_count는 claim
세대이기도 하므로 값만으로 발송 횟수를 단정하지 않는다. HTTP 응답 유실 후 재시도 등은
단일 행에서도 발생할 수 있다. 수동 검증을 완료하려면 이용 가능한 provider 관측과 Android
수신 횟수를 함께 기록하고, 증명하지 못한 범위를 명시한다. PR/Issue 게시물은 별도로
명시적인 승인을 받은 담당자가 작성한다.
