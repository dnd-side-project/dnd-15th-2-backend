# Test Report: TEST-PLAN-GH-366-DEV-WORKER-FCM

> Created at: `2026-11-21T12:21:55.932Z`
> Finalized at: `2026-11-21T12:30:53.687Z`
> GitHub Issue: `#366`
> Branch: `chore/gh-366-dev-worker-fcm`
> Commit: `32ab7b59` 기준 미커밋 변경

## 1. Executive summary

- Result: `PASS` — 필수 대상 회귀·staged 게이트와 최종 전체 PR readiness 통과.
- Tested scope: 승인된 UNIT-001~006, INT-001~003. 단위 6개·통합 3개는 시나리오 ID 수이며,
  필수 회귀의 실제 JUnit 실행은 단위 404개·통합 34개다. 실패·오류·skip은 모두 0이다.
- Unverified scope: MANUAL-001~003은 병합 후 담당자가 수행한다. 실제 Google OAuth/FCM,
  원격 배포, 실기기 SENT/provider_message_id, 중복 발송·Android 수신은 실행하지 않았다.
- Release recommendation: 오케스트레이터 독립 검증과 사람의 PR 승인을 진행할 수 있다.
  이 로컬 검증이 실발송 검증이나 구현자의 자체 승인을 대신하지 않는다.
- BLOCKED 후보: 최종 B1/B2/B3 없음. 초기 테스트 장치·fixture 실패는 수정했고,
  첫 전체 검증의 LocalStack S3 404는 단독·dev 조합 및 변경 없는 전체 재실행에서 재현되지 않았다.
  이 간헐 실패의 근본 원인은 미확정이며 §5·7에 잔여 위험으로 기록한다.

### 저장소 결과 계약

| Field | Value |
| --- | --- |
| status | PASS — 로컬 구현·검증 완료 |
| issue_number | 366 |
| task_id | TEST-PLAN-GH-366-DEV-WORKER-FCM |
| design_id | 해당 없음 |
| changed_files | §8의 설정·문서·기존 테스트 4개·이 보고서 |
| executed_checks | §3의 명령과 종료 코드 |
| passed_checks | 필수 단위·통합, harness check/pr-ready, hooks:validate, staged pre-commit, diff 공백 검사 |
| failed_checks | 최종 실행 0; 초기 수정 및 전체 검증 실패·재실행 이력은 §5 |
| blocked_checks | 없음; 병합 후 MANUAL-001~003은 승인 계획에 따른 미실행 |
| assumptions | #364/#365 선행 배포·주입은 TASK의 제공 사실이며 live 재검증하지 않음 |
| risks | 실제 전달·중복 요청·수신·운영 수치 미확정, 최초 LocalStack 초기화 간헐 실패 원인 미확정 |
| required_human_decisions | 독립 검증과 PR 승인, 병합 후 담당자의 접근 권한·모바일 증거 수집 방식 확인 |

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | OpenJDK 21.0.13.0.3; 모든 Gradle 검증에 JAVA_HOME을 Java 21로 지정 |
| Gradle | 8.14.3 |
| Spring Boot | 3.5.16 |
| Database | 로컬 Testcontainers PostGIS, PostgreSQL 16.14 |
| Docker | 29.6.2 |
| Test runner | JUnit 5 / Gradle Test |
| FCM fixture | 메모리에서 생성한 RSA-2048 PKCS#8 가짜 service account, loopback token endpoint |
| Token protection fixture | 테스트 전용 32-byte AES/HMAC 값; 실제 secret 조회 없음 |

시스템 기본 Java는 25였으므로 각 검증 명령 앞에서
`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`을 적용했다. 실제 `.env`는 존재 여부만
확인했고 내용은 읽지 않았다. 실제 credential·FID·서버 식별자는 이 보고서에 없다.
Java/YAML LSP 서버가 설치되지 않아 LSP 진단은 실행하지 못했으며, 실제 javac·바인딩·
format·Checkstyle 및 애플리케이션 기동으로 검증했다.

## 3. Execution results

### 실행 명령

아래는 실제 실행한 명령이다. Gradle 대상 회귀에는 출력 정리를 위한 `--console=plain`을
덧붙였다. 장시간 실행은 monitor로 시작하고 종료 코드와 JUnit XML을 확인했다.

```bash
env -u QELLO_TEST_COMPOSE -u DOCKER_HOST -u SSL_CERT_FILE -u SSL_CERT_DIR ./gradlew test --tests 'com.dnd.qello.scheduling.*' --tests 'com.dnd.qello.notification.*' --rerun --console=plain
env -u QELLO_TEST_COMPOSE -u DOCKER_HOST -u SSL_CERT_FILE -u SSL_CERT_DIR ./gradlew integrationTest --tests 'com.dnd.qello.DevProfileBootIntegrationTest' --tests 'com.dnd.qello.PushDeviceRegistrationIntegrationTest' --tests 'com.dnd.qello.PushDeliveryDispatchIntegrationTest' --rerun --console=plain
GIT_MASTER=1 env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness check
GIT_MASTER=1 env -u QELLO_TEST_COMPOSE -u DOCKER_HOST -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness pr-ready --project-tests
npm run hooks:validate
GIT_MASTER=1 env -u SSL_CERT_FILE -u SSL_CERT_DIR python3 scripts/run-hook.py pre-commit
GIT_MASTER=1 git diff --check
```

| Command / suite | Result | Exit | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- | --- |
| 필수 대상 단위 회귀 | PASS | 0 | 404, 실패/오류/skip 0 | Gradle 약 5초 | build/test-results/test의 필수 대상 실행 XML 집계 |
| 필수 대상 통합 회귀 | PASS | 0 | 34, 실패/오류/skip 0 | Gradle 약 12초 | build/test-results/integrationTest의 세 클래스 XML |
| harness check | PASS | 0 | 정책 검사 | 별도 측정 안 함 | secret/JUnit/branch/workflow/label/hooks/baseline 검사 통과 |
| harness pr-ready --project-tests 최종 | PASS | 0 | 단위 1506 + 통합 874, 실패/오류/skip 0 | 약 2분 35초 | Local PR readiness checks passed 및 전체 JUnit XML 집계 |
| 전체 Java 컨벤션 | PASS | 0 | 소스 3 + 구조 23, 실패/오류/skip 0 | Gradle check 포함 | javaConventionSourceTest / javaConventionArchitectureTest XML |
| npm run hooks:validate | PASS | 0 | Husky 정책 | 별도 측정 안 함 | Husky validation passed |
| staged pre-commit | PASS | 0 | staged Java 및 컨벤션 | Gradle 약 7초 | javaConventionStagedCheck, Checkstyle·format·소스/구조 검사 및 hook 통과 |
| git diff --check | PASS | 0 | 공백 검사 | 별도 측정 안 함 | 초기 검증 및 unstage 후 통과 |

필수 단위 회귀 중 PushConfigurationTest 97개, WorkerSchedulingConfigurationTest 9개,
WorkerSchedulingPropertiesTest 10개가 포함된다. 통합 회귀는 DevProfileBootIntegrationTest
4개, PushDeliveryDispatchIntegrationTest 22개, PushDeviceRegistrationIntegrationTest
8개다. INT-003의 기존 health와 노출 제한은 두 테스트 메서드로 유지했다.
전체 검증의 기존 checkstyleTest/checkstyleIntegrationTest 비활성 설정은 변경하지 않았으며,
수정 Java 파일은 별도의 staged Checkstyle로 검증했다. 위 skip 수는 JUnit 테스트 기준이다.

staged 게이트에는 이번에 수정한 설정·문서·기존 Java 4개 파일만 잠시 stage했다.
종료 시 EXIT trap으로 해당 파일을 모두 `git restore --staged`했고 이후 index가 비어
있음을 확인했다. 기존 TASK 및 승인 계획은 stage하지 않았다.
`harness test-run`의 자동 보고서 scaffold 대신 명시된 회귀 명령을 실행하고
`templates/test-report.md`에 따라 apply_patch로 이 보고서를 작성했다.

## 4. Scenario results

모든 ID의 접두사는 `TEST-PLAN-GH-366-DEV-WORKER-FCM-`이다.

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | WorkerSchedulingPropertiesTest.devConfigDataBindsApprovedWorkerSettingsAndExclusions | 실제 기본/dev ConfigData, 승인 수치 전체, pool=5·활성 5개, 제외 flags |
| UNIT-002 | PASS | PushConfigurationTest.devDefaultsToFcmAndWiresRealTokenProtector | provider 덮어쓰기 없이 dev=fcm, 실제 service account 생성자·AES, noop 부재 |
| UNIT-003 | PASS | PushConfigurationTest.devRejectsMissingOrBlankFcmSettings / devRejectsMalformedServiceAccountCredentials | 필수 FCM 두 설정의 누락·빈 값·공백 6회 + JSON/key 오류 2회; 원인 경계·출력 비노출 |
| UNIT-004 | PASS | WorkerSchedulingConfigurationTest.devGlobalOffRollsBackSchedulingWithoutChangingProvider | valid FCM/key 유지, global OFF로 scheduler·adapter 및 조건부 push worker/planner/retry 부재 |
| UNIT-005 | PASS | PushConfigurationTest.explicitNoOpStartsWithoutFcmPlaceholders | dev 포함 명시적 noop, FCM bean 부재, 실제 AES 왕복 보존 |
| UNIT-006 | PASS | WorkerSchedulingConfigurationTest.devSchedulerInvokesEachWorkerWithBoundCommandsAndShutsDown | 실제 scheduler, 다섯 mock worker의 사전 latch, batch/owner/시각/lease/retry 배선·종료 |
| INT-001 | PASS | DevProfileBootIntegrationTest.devProfileContextLoads | 실제 dev 전체 context, 다섯 서비스·adapter·예약 작업, pool·identity·metrics·FCM·AES, 여섯 제외 adapter 부재 |
| INT-002 | PASS | DevProfileBootIntegrationTest.registersAndRevokesFidWithAutoConfiguredProtector | HTTP 204, 실제 AES 보호·복호화/fingerprint·ACTIVE→REVOKED |
| INT-003 | PASS | DevProfileBootIntegrationTest.actuatorHealthIsOpenAndUp / onlyHealthIsExposed | health 공개/UP 및 나머지 actuator 비공개 유지 |
| MANUAL-001 | NOT_RUN | 오케스트레이터 | 병합 후 배포 및 실기기 delivery SENT/provider_message_id 확인 |
| MANUAL-002 | NOT_RUN | 오케스트레이터·모바일 담당 | 병합 후 중복 row 조사와 실제 provider 요청/수신 횟수 대조 |
| MANUAL-003 | NOT_RUN | 모바일 담당 | 병합 후 Android foreground/background/앱 종료 수신 |

### 외부 요청 차단 결과

- 실제 GoogleCredentialsFcmAccessTokenProvider와 FcmHttpV1PushProvider를 생성했다.
  가짜 credential의 token_uri는 context 기동 전에 시작한 loopback endpoint다.
- provider 단위 테스트와 dev 통합 테스트의 BeanPostProcessor가 실제 생성 bean의
  `accessToken()` 및 FCM RestClient `post()` 진입을 spy로 감시하고 I/O 전에 실패시킨다.
- 통합 결과: token endpoint 요청 0, OAuth 진입 시도 0, FCM 진입 시도 0.
  `verifiesNoExternalRequests()`가 매 테스트 후 검증하고 종료 시 다시 검증했다.
- 단위 결과: 생성자 token endpoint 요청 0, OAuth/FCM 차단 경계 진입 0을 각 fixture
  종료 시 검증했다. 잘못된 credential의 로그에도 fixture 원문이 없음을 확인했다.
- full dev context에서 scheduler 설정·초기화와 다섯 실제 adapter는 유지한다. 등록 경계에서
  Runnable·delay를 포획하고 executor에는 제출하지 않는다. 초기 refresh 이후나
  BeforeEach에서 늦게 scheduling을 끄는 방식은 사용하지 않았다.
- 자동 예약 실행을 막는 통합 fixture와 별도로 UNIT-006은 실제 scheduler callback을
  검증한다. 실제 Google/FCM 호출이나 원격 발송 결과로 해석하지 않는다.

## 5. Failures and diagnostics

초기 실패는 다음과 같이 구분하고 수정했다. assertion 약화·skip·suppression·고정 sleep은
추가하지 않았으며 production Java는 수정하지 않았다.

| 분류 | 재현 조건 / 안전한 오류 요약 | 조치 | 최종 상태 |
| --- | --- | --- | --- |
| 테스트 컴파일 | Class 와일드카드 추출 목록의 AssertJ varargs 타입 불일치 | 실제 등록 대상 bean 인스턴스의 정확한 집합 비교 | 수정 후 통합 PASS |
| 테스트 장치 | Spring 6.2 Task.OutcomeTrackingRunnable을 직접 ScheduledMethodRunnable로 취급해 기동 실패 | 현재 Spring의 wrapper 내부 Runnable을 확인해 실제 예약 대상/메서드를 검증 | 수정 후 통합 PASS |
| 테스트 fixture | rollback fixture에서 AES/HMAC 값을 동일하게 줘 key separation 검증 실패 | 서로 다른 테스트 전용 32-byte 값 사용 | 수정 후 단위 PASS |
| 테스트 fixture | 미완성 JSON은 라이브러리 파서의 다른 예외 경로를 타 기존 오류 타입 assertion과 불일치 | 기존 파싱 실패 계약을 검증하는 잘못된 JSON 문서 fixture로 교체; 오류 경계 검증 유지 | 수정 후 단위 PASS |
| 형식 | import 정렬·줄바꿈 위반 | formatter 비교 산출물의 차이만 apply_patch 반영 | staged format PASS |
| 비밀 검사 오탐 | 생성 key 자체가 아니라 소스의 PEM 구분자 리터럴을 private-key로 탐지 | PEM 형식과 메모리 내 생성 body를 포맷으로 조립; 검사 규칙 변경·예외·suppression 없음 | preflight 및 staged hook PASS |
| 전체 테스트 환경의 간헐 실패 | 첫 pr-ready의 공통 LocalStack fixture에서 S3 listBuckets가 404를 반환해 media 네 클래스가 initializationError | 변경 없이 단독 Exif, dev+Exif 조합, 전체 pr-ready 순서로 분리 확인; 소스·설정·assertion 수정이나 재시도 코드 추가 없음 | 단독 exit 0, 조합 11개 exit 0, 최종 전체 exit 0 |

초기 대상 단위 실행은 404개 중 2개 실패, 초기 통합 실행은 기동 오류 1개였다.
수정 후 동일 필수 명령의 최종 실행은 각각 404/34개, 실패·오류·skip 0이다.
실제 secret을 조회하거나 키 원문·전체 credential을 파일 또는 보고서에 기록하지 않았다.
첫 전체 pr-ready는 단위 1506개 통과 후 통합 846개 중 4개 기동 오류로 exit 1이었다.
실패 클래스는 ExifStripIntegrationTest, FeedMediaViewUrlIntegrationTest,
MediaAssetStorageIntegrationTest, ProfileImageIntegrationTest다. 공통 위치는 변경하지 않은
LocalStackContainerIntegrationTestSupport.createTestBucket의 listBuckets 호출이다.
단독 Exif 실행과 dev+Exif 같은 JVM 실행은 각각 exit 0이었고, 소스 변경 없이 다시 실행한
전체 pr-ready는 통합 874개를 모두 통과했다. 최초 실행에서 기동에 실패한 클래스의 테스트
본문이 최종 실행에서는 수행되므로 전체 테스트 수가 다르다.

추가 진단 명령은 필수 명령과 동일한 네 환경변수 해제 및 Java 21 조건에서 실행했다.

```bash
./gradlew integrationTest --tests 'com.dnd.qello.ExifStripIntegrationTest' --rerun --console=plain
./gradlew integrationTest --tests 'com.dnd.qello.DevProfileBootIntegrationTest' --tests 'com.dnd.qello.ExifStripIntegrationTest' --rerun --console=plain
```

S3 404의 근본 원인이나 baseline에서의 재현 여부는 확정하지 못했다. dev 변경의 간섭이
입증됐다고도, 기존 버그가 확정됐다고도 주장하지 않는다. 최종 필수 게이트는 통과했으므로
B1/B2/B3 미해결 차단은 없지만 이 간헐 실패 관찰은 독립 검증자에게 인계한다.

## 6. Potential issues

### Application code

- production Java는 변경하지 않았다. provider 구성, 조건부 worker 등록과 dev 설정의
  기동 계약은 실제 context로 확인했다.
- AnswerModerationIntake 미등록과 ANSWER_* 생성·발송 제외는 TASK/지시의 범위다.
  테스트를 통과시키기 위해 moderation bean이나 production fallback을 추가하지 않았다.

### Infrastructure and resource limits

- infra/workflow·배포·secret 주입 경로는 변경하지 않았다. 실제 서버 상태를 확인하지 않았다.
- pool 5는 승인된 dev fixture다. 운영 처리량·DB 연결 수·실제 batch 지연의 보장은 아니다.
- push lease PT2M은 connect 3s + read 5s × 10건의 80s보다 길게 잡은 승인 근거다.
  실제 부하·OAuth 갱신·네트워크 지연을 측정한 운영 수치로 해석하지 않는다.
- LSP 미설치는 검사 환경의 제약이며 필수 컴파일·실행·staged 검사로 대체 확인했다.

### Database and migrations

- migration 변경 없음. 실제 PostGIS와 기존 Flyway schema에서 FID 등록·해지 회귀를 수행했다.
- full dev scheduler는 refresh 전 포획하므로 공유 baseline 복원과 fixture 생성 중
  batch claim·원장 변경을 수행하지 않는다.
- notification/device 유일 행 제약은 실제 provider 요청이 한 번뿐임을 증명하지 않는다.

### Concurrency and idempotency

- UNIT-006은 고정 Clock과 시작 전 등록한 다섯 latch로 callback을 관찰한다.
  시간창에서 호출 횟수를 추측하지 않으며 context 종료 후 scheduler shutdown을 검증한다.
- dev 통합 context는 클래스 종료 시 폐기해 다음 클래스의 공유 DB에 영향을 주지 않는다.
- 다중 실제 instance와 네트워크 응답 유실 후 재전송은 병합 후 별도 관측이 필요하다.

### Transactions and event ordering

- 기존 FID HTTP→서비스→암호화 저장·해지 및 dispatch DB 회귀를 유지했다.
- 트랜잭션·outbox·lease/fencing production 로직은 바꾸지 않았다.
- 자동 scheduler를 이용한 실제 dev 알림 생성부터 원격 FCM 수락까지의 종단 검증은
  MANUAL-001에서 수행한다. mock worker callback과 이를 혼동하지 않는다.

### External APIs

- 실제 Google OAuth와 FCM은 호출하지 않았다. 정상/오류 credential의 파싱과 bean 생성만
  실제 구현으로 실행했고 네트워크 진입은 차단했다.
- 테스트가 의존하는 Spring 예약 wrapper 구조는 현재 Boot 의존성에서 확인한 계약이다.
  의존성 갱신 시 예약 포획 장치의 호환성을 다시 확인해야 한다.
- 실제 FCM 수락·provider_message_id 및 Android 수신은 MANUAL-001~003 미실행 범위다.

### Failure recovery and reconciliation

- UNIT-004 global OFF는 scheduling을 멈추되 fcm/AES 구성을 유지한다.
  UNIT-005 explicit noop은 FCM credential 요구를 없애되 보호 key를 유지한다.
- 발송 중지는 enabled=false 우선, noop은 FCM 자격 증명 문제일 때만 사용하도록 문서화했다.
  noop에서 대기 delivery가 실제 발송 없이 SENT가 되는 차이를 경고했다.
- dev YAML이 기본 placeholder보다 우선하므로 환경변수 하나만으로 OFF가 되지 않음을
  문서화했다. 두 경로 모두 설정 변경 후 재배포가 필요하며 실제 롤백 배포는 하지 않았다.
- 문서 10절의 기존 fencing 설명도 늦은 결과 저장 거절과 provider 요청 중복 위험을
  구분하도록 수정했다. 원장을 수동 변경하거나 재발송하는 절차는 추가하지 않았다.

## 7. Regression and residual risk

- 필수 대상 회귀는 단위 404·통합 34개, 실패·오류·skip 0이다.
- 최종 전체 PR readiness는 단위 1506개·통합 874개와 컨벤션 소스 3개·구조 23개가 통과했다.
  최종 JUnit 실패·오류·skip은 모두 0이다.
- 첫 전체 실행의 LocalStack S3 404는 변경 없이 수행한 진단과 재실행에서 재현되지 않았다.
  초기 실패를 삭제하거나 성공으로 바꿔 기록하지 않으며, 원인 미확정 위험은 독립 검증에서 확인한다.
- 실발송·실기기·중복 요청 증거는 병합 후 수동 검증으로 남는다. 승인된 수동 담당자는
  오케스트레이터와 모바일 담당이며, 접근 권한·증거 수집 방식을 확인해야 한다.
- 승인된 소유 파일 범위를 지켰고 새 테스트 파일·의존성·production seam은 추가하지 않았다.
  기존 TASK 변경 및 APPROVED 계획은 보존했다. 커밋·push·대인 게시·배포는 수행하지 않았다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-366-TEST-PLAN-GH-366-DEV-WORKER-FCM.md` (`APPROVED`).
- CI run: 미실행, 로컬 검증만 수행. PR 생성·코멘트·push 없음.
- Related ADR: ADR-0008.
- PR: 미생성.

| Changed file | 항목별 요약 / 결정 반영 위치 |
| --- | --- |
| `src/main/resources/application-dev.yml` | qello.worker.scheduling의 global ON·pool=5·다섯 worker 승인 수치, push.provider=fcm, dev 전용/lease/제외 사유 주석 |
| `docs/push-production-setup.md` | 10.1 활성화·환경변수 한계·롤백 권고/경고, 10.2 dev 수치 표·lease 근거·fencing 의미 정정; 다른 절 변경 없음 |
| `src/test/java/com/dnd/qello/scheduling/config/WorkerSchedulingPropertiesTest.java` | UNIT-001 실제 dev ConfigData 바인딩 |
| `src/test/java/com/dnd/qello/scheduling/WorkerSchedulingConfigurationTest.java` | UNIT-004 global OFF·FCM/AES 유지, UNIT-006 실제 다섯 callback 배선 |
| `src/test/java/com/dnd/qello/notification/config/PushConfigurationTest.java` | UNIT-002~003·005, 기존 dev noop 기대 교체, 가짜 credential·호출/출력 차단 |
| `src/integrationTest/java/com/dnd/qello/DevProfileBootIntegrationTest.java` | INT-001~003, 기존 FID·health 유지, 예약 포획·외부 I/O 차단·context 종료 |
| `docs/reports/tests/gh-366-TEST-PLAN-GH-366-DEV-WORKER-FCM.md` | 이 실행 보고서 |

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음.
- [x] MANUAL-001~003 및 실제 외부 호출·배포 미실행이 명시됨.
- [x] 잠재 문제는 현재 Issue #366 및 승인 계획의 범위·수동 검증으로 추적.
- [x] 전체 PR readiness 최종 통과 및 최초 실패·재실행 이력 명시.
- [ ] 오케스트레이터 독립 검증 및 PR 설명과의 대조 — 이번 실행자의 자체 승인 대상 아님.
