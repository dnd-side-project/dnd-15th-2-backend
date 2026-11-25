# Test Report: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION

> 실행일: 2026-11-25
> Issue: #388 / task_id: TASK-GH-388-COORDINATE-ENCRYPTION / design_id: ADR-0009
> Branch: feat/gh-388-coordinate-encryption / 검증 코드: 1800bca2

## 1. Executive summary

- status: BLOCKED (Issue 전체의 필수 성능 증거 미완료). local_implementation_status: PASS.
- 승인된 C8 구현, 갱신 4의 통합 실패 20건 수정, 기능 회귀 및 로컬 필수 검사를 완료했다. 미해결 로컬 기능 실패는 없다.
- 단위 1,778건, 통합 967건: 실패 0, 오류 0, skip 0. 계획의 단위 14/통합 24는 계약 시나리오 수이며 JUnit invocation 수와 다르다.
- PERF-001~003은 코드 작성·컴파일만 완료했다. 성능은 **OCI 측정 대기**이며 #388 전체 PASS나 배포 가능 판정을 내리지 않는다.
- 독립 검토: INT-002 이력 보존 coverage 지적을 비어 있지 않은 실제 행의 전후 비교로 보완한 뒤 로컬 코드 준비 상태 APPROVE. 사람의 PR 승인·OCI 측정 승인을 대신하지 않는다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| OS / architecture | macOS arm64, 로컬 공유 worktree |
| Java | OpenJDK Homebrew 21.0.13.0.3 |
| Spring Boot | 3.5.16 |
| Database | 격리된 PostgreSQL 16 / PostGIS 3.5 Testcontainers, 합성 데이터만 사용 |
| Test runner | JUnit 5, Gradle wrapper |
| GeographicLib | net.sf.geographiclib:GeographicLib-Java:2.1, MIT |
| OSV scanner | 공식 macOS arm64 2.7.1, SCALIBR 0.5.3 |

GeographicLib는 착수 시 Maven Central metadata/POM과 upstream 라이선스를 확인하여 안정 버전 2.1로 고정했다. 기존 의존성 스캔 init script를 사용하며 영구 dependency lock 정책을 새로 만들지 않았다. 런타임 lock은 build/dependency-scan/gradle.lockfile에 보관하고 커밋하지 않았다.

## 3. Execution results

| Command / suite | Result | Evidence |
| --- | --- | --- |
| ./gradlew test integrationTest | PASS | 단위 1778 / 통합 967, 실패·오류·skip 0 |
| ./harness test-run --id TEST-PLAN-GH-388-COORDINATE-ENCRYPTION | PASS | 공식 wrapper로 전체 회귀와 보고서 생성 |
| ./harness check | PASS | JUnit·secret·convention·workflow·label·hook self-test |
| ./harness pr-ready --project-tests | PASS | 최종 code 기준 확인, staged hook은 각 커밋에서 별도 실행 |
| npm run hooks:validate | PASS | Husky validation |
| ./gradlew spotlessJavaCheck javaConventionCheck | PASS | 포맷·Checkstyle·Java source/architecture 검사 |
| python3 scripts/run-hook.py pre-commit | PASS | 실제 staged manifest 검사, hook 우회 없음 |
| git diff --check | PASS | 소스·문서 공백 오류 없음 |
| dependencyScanLock / OSV policy | PASS | 173 packages, blocked 0, 기존 예외 2 |
| ./gradlew performanceTest | NOT_RUN | 사용자 지시대로 OCI 측정 대기 |

- Unit: 217 XML suites / 1,778 tests. Integration: 122 XML suites / 967 tests. XML의 failures/errors/skipped는 모두 0이다.
- 공식 harness test-run도 전체 회귀를 실행했다. 이후 최종 코드 커밋 기준 전체 회귀와 pr-ready를 다시 확인했다. Gradle의 UP-TO-DATE는 직전 동일 입력 성공 결과를 재사용한 것이며 별도 재실행으로 세지 않았다.
- 근거: build/test-results/test, build/test-results/integrationTest, build/reports/tests, build/dependency-scan/summary.json, build/dependency-scan/osv.json. 로컬 build 산출물은 커밋하지 않는다.
- pre-commit은 실제 staged manifest로 Spotless·Checkstyle·source/architecture·JUnit·secret 검사를 실행했다. NO-SOURCE 출력만으로 staged 검사 성공을 주장하지 않았다. hook 우회·baseline 수정·suppression은 없다.
- pr-ready가 다른 worktree에 checkout된 로컬 main의 자동 fast-forward를 건너뛰었다. 그 브랜치는 건드리지 않았으며 로컬 PR 준비 검사 자체는 통과했다.
- Java/YAML LSP가 없어 LSP 진단은 실행하지 못했다. 실제 컴파일, 테스트 및 repository 정적 검사를 사용했다.

### 의존성 스캔

OSV 정책 검사 PASS: 런타임 패키지 173개, blocked 0개. Moderate 3개와 기존 Spring WebMVC Critical 예외 2개(#382, 만료 2026-12-07)가 남는다. 취약점 0건이라는 의미가 아니며 예외 파일은 변경하지 않았다. raw scanner의 findings exit 1과 정책 검사 exit 0을 구분했다.

- 실행: Gradle dependencyScanLock --write-locks (config/security/dependency-lock.init.gradle), scripts/check-dependency-scan.py의 실제 scanner/lockfile/runtime-modules/exceptions 인자.
- scanner 공식 SHA-256: b22c02877a811c8f78956e8643efa554932b7378f590d13f8e97d4e9448a29f3.
- 생성 lock SHA-256: c9a292e22238b6bd3c3e308efe6d160c7fe9ef64a775e3abc5b319d267297ac7.
- 스캔 시작: 2026-11-25T02:59:57.633809Z, live OSV 응답 사용. 고정 DB snapshot의 재현성을 주장하지 않는다.

## 4. Scenario results

아래 ID에는 TEST-PLAN-GH-388-COORDINATE-ENCRYPTION- 접두사를 붙인다. 괄호 숫자는 해당 클래스의 실행 invocation 수다.

| Scenario ID | Result | Test class / boundary | Notes |
| --- | --- | --- | --- |
| UNIT-001~007 | PASS | CoordinateProtectorTest | 실제 JCE 대조, 키 회전·AAD·변조·해석 실패 |
| UNIT-008 | PASS | CoordinateProtectionPropertiesTest | 누락·형식·두 슬롯 검증 |
| UNIT-009 | PASS | CoordinateCodecTest | double 정밀도 계약 |
| UNIT-010~012 | PASS | CoordinateCalculationTest | 동일점·역방위·공정성·저장 반올림 |
| UNIT-013 | PASS | PostAudienceTest | 과거 NULL 복원과 신규 생성 검증 분리 |
| UNIT-014 | PASS | CoordinateRedactionTest | 좌표·키 문자열 비노출 |
| INT-001~004 | PASS | CoordinateMigrationIntegrationTest (4) | 빈 DB, 이력 보존, 실제 rollback, 합성 복구 |
| INT-005,017,023 | PASS | ActiveUserPresenceApi/Update, LocationCollectionGateDisabled, 기존 API 회귀 | 수집 게이트·실제 JDBC·응답 유지 |
| INT-006~007 | PASS | CoordinateMigrationRecoveryIntegrationTest (6) | 과거 글 비재매칭·재생 멱등성 |
| INT-008,019,024 | PASS | CoordinateProtectionFailureIntegrationTest (7) | 실제 adapter 실패·원자성·DEAD·비노출 |
| INT-009~010 | PASS | CoordinatePersistenceIntegrationTest (3) | 암호문 저장·row/purpose 교체 거부·키 회전 |
| INT-011~012,014~016 | PASS | CoordinateMatchingDifferentialIntegrationTest (32) | PostGIS oracle 차등·D3 보정 |
| INT-013,022 | PASS | DirectionPreviewIntegrationTest (9), DirectionPreviewPersistenceBoundaryTest | 모든 segment·빈 방향·단일 statement snapshot |
| INT-018 | PASS | CoordinateProtectionConfigurationIntegrationTest (6), DevProfileBootIntegrationTest (5) | 실제 설정 binding·fail-fast·합성 키 부팅 |
| INT-020~021 | PASS | DirectionMatchingWorkerConcurrencyIntegrationTest (3), DirectionMatchingWorkerIntegrationTest (20), DirectionMatchingContractIntegrationTest | claim/reclaim·용량·stale fencing·상위 후보·rollback |
| PERF-001~003 | NOT_RUN | CoordinateEncryptionPerformanceIntegrationTest 및 기존 performance source set | OCI 측정 대기; compileIntegrationTestJava 통과, benchmark 미실행 |

### D3 차등 수치

독립 PostGIS oracle 기준 commit은 a910241e140071b3b34e9e05f461efdc7e43e24d, 원 SQL source SHA-256은 9026c39ce7a7a393b713a5d7835a56b2ad21d746b576fd0319a161c668bf65ae이다. 테스트 oracle은 기존 PostGIS 거리·방위·역방위·필터·정렬·집계 SQL을 실행하며 앱 계산으로 정답을 만들지 않는다.

- 차등 32건 PASS. 전 fixture 최대 절대 거리 차: 0.0000000100000000m (한도 0.001m).
- 거리 1m 이상에서 순·역방위를 모두 관찰한 최대 순환 각 차: 0.0000000538063176236392도 (한도 0.0000001도).
- 1m 미만의 각도는 승인된 D3에 따라 수치 비교에서 제외했다. 관찰된 raw 최대 각 차는 0.0000174160027768266도이며 0으로 채우거나 한도 통과로 표시하지 않았다. 후보 포함·거리 비교는 유지했다. NULL 방위는 비교 불가로 다뤘다.
- 경계 양옆은 ±1m/±0.0001도, 경계선 위는 수치 오차와 자기 수치의 반열린 규칙을 검증했다. 공정성 이력 우선순위를 유지한 거리 동률(0.001m 이하)만 ID로 정규화했다. 최대 투영은 20,000,000m이며 설정 상한 20,100,000m는 유지했다.
- 최초 9건은 승인 댓글 7150447072의 fixture/비교 계약 보정으로 해결했다. production 계산이나 허용 오차를 바꾸지 않았다.
- oracle 파일 SHA-256: ea14a56837c73115ddb24a7e50da2cbdfba06364b8318a1a8ec2b26c890dfe64.
- differential 파일 SHA-256: 0fbf9db2979e9eae5d228aae6e6f7998a540af32b8dda5ecfc192429bfe436aa.
- V35 DBML SHA-256: 12c8ee803ec1cfff65a3d482962022c058317fa19cd5ab6972e81920f9462a20.
- V35 ERD SHA-256: b1ebb8421a21a756d1d252017ca52d14fc6b52b1b1cf314162a8ccf23bbfa2b3.

## 5. Failures and diagnostics

갱신 4의 20건은 아래 테스트 진입 조건·fixture·신호 문제로 해결했다. 승인 목록 밖 production 변경은 필요하지 않았다. 중간 실패를 최종 미해결 실패와 구분한다.

| File / group | Cause | Correction / preserved assertion |
| --- | --- | --- |
| DevProfileBootIntegrationTest | 필수 좌표 키 미주입 | DynamicPropertySource 합성 키만 추가, production dev fallback 없음 |
| NotificationPreferenceMigrationIntegrationTest | V35 추가로 적용 수 11 | 기대 10→11 및 설명만 갱신, preference 검증 유지 |
| CoordinateMigrationRecoveryIntegrationTest | 유효 내용 없는 command와 합법적 moderation 이벤트 누락 | 본문·release fixture, 두 Outbox 타입 정확 보존 및 매칭 event 상태 범위 지정 |
| CoordinateProtectionFailureIntegrationTest | 본문 누락·moderation PENDING·Spring 예외 번역 | 유효한 gate precondition, 실제 변조/unknown key DEAD 및 전량 rollback assertion 유지 |
| CoordinateProtectionConfigurationIntegrationTest | BigDecimal scale와 미주입 push placeholder | 수치 비교, 별도 합성 push fixture로 외부 환경 완전 분리 |
| DirectionMatchingWorkerIntegrationTest | HTTP 요청 전 초 단위 at이 실제 next_attempt_at보다 이른 시각 | 실제 DB의 due 시각을 사용, PROCESSED 정확 assertion 유지 |
| DirectionMatchingWorkerConcurrencyIntegrationTest | NOT_SUPPORTED synchronization을 실제 tx로 오인 | actual transaction 여부에 맞는 commit 신호, 실제 claim/reclaim·잠금·latch 유지 |
| DirectionPreviewIntegrationTest | 정확 90도/2000m 경계 고정 기대 | 승인 D3 여유 fixture와 별도 exact-boundary 수치·반열린 규칙 3입력, 기대 완화 없음 |
| CoordinateMigrationIntegrationTest INT-002 | 기존 수신자·슬롯·Outbox 보존 coverage 누락 | 비어 있지 않은 V34 실제 행을 seed하고 V35 전후 모든 컬럼 정확 비교 |

Worker의 빈 결과는 HTTP enqueue보다 이른 command.at에서 실제 due 작업이 없었기 때문이다. 실제 next_attempt_at으로 호출하고 PROCESSED 정확 기대를 유지했다. 경합 테스트는 NOT_SUPPORTED의 synchronization 존재를 actual transaction으로 오인해 첫 claim 신호를 놓쳤다. 실제 transaction이면 afterCommit, 아니면 이미 완료된 claim 직후 신호를 전달하며 lock·fencing assertion은 유지했다. Preview 두 실패는 경계점 고정 기대와 수치 오차 문제로, 승인 D3 여유 fixture와 정확 경계 별도 검증으로 분리했다. 세 경우 모두 production worker/service/web 수정 없이 전체 실제 DB 회귀가 통과했다.

독립 검토가 처음 지적한 INT-002는 기존 수신자·슬롯·Outbox가 없는 fixture라 이력 보존을 입증하지 못했다. 이제 ACTIVE 과거 글의 실제 수신자, 0이 아닌 슬롯/수신 이력, 처리된 유효 matching Outbox를 V34에서 생성하고 존재를 확인한 뒤 V35 전후 전체 컬럼을 비교한다. migration 클래스 4/4 통과 후 검토자가 지적 해소를 확인했다.

초기 Source scenario metadata 형식, Spotless 포맷, staged Checkstyle 50줄 제한 실패도 승인 파일 안의 header/배치·메서드 분리로 수정했다. 기준선이나 검증 규칙은 수정하지 않았다.

### 기존 테스트 파일별 변경 이유

경로 접두사는 통합 src/integrationTest/java/com/dnd/qello, 단위 src/test/java/com/dnd/qello다.

| Existing file | Approved change |
| --- | --- |
| ActiveUserPresenceApiIntegrationTest | 평문 JDBC 조회를 암호문 복원으로 전환, API 응답·게이트 유지 |
| ActiveUserPresenceUpdateIntegrationTest | 암호문 fixture와 최신 관측 우선순위 유지 |
| DirectionMatchingContractIntegrationTest | 원점 fixture를 실제 protector로 저장, rollback·멱등성 유지 |
| DirectionMatchingWorkerIntegrationTest | 암호문 origin/presence, 실제 due 시각 사용, PROCESSED 유지 |
| DirectionMatchingWorkerConcurrencyIntegrationTest | 암호문 fixture, sleep polling 대신 실제 transaction/claim 신호 |
| DirectionPreviewIntegrationTest | 암호문 fixture, 승인 D3 경계 분리, 정확 segment 개수 유지 |
| FlywayMigrationIntegrationTest | V35 컬럼·제약·GiST 제거 inventory 반영, 과거 checksum 유지 |
| LocationCollectionGateDisabledIntegrationTest | 기존 차단 assertion을 암호문 스키마에 맞춤 |
| RecipientNotificationFanOutWorkerIntegrationTest | 수신자 매칭용 origin/presence fixture만 암호화 |
| DevProfileBootIntegrationTest | DynamicPropertySource에 좌표 전용 합성 키만 추가 |
| NotificationPreferenceMigrationIntegrationTest | V25~V35 11개 적용과 설명만 변경 |
| FlywayMigrationContractTest | 최신 V35 목록, V1/V2/V8 checksum 유지 |
| DirectionPreviewPersistenceBoundaryTest | SQL 문구 대신 단일 snapshot·빈 방향 행동 검증 |
| DirectionRecipientSelectionBoundaryTest | SQL 정렬 문구 대신 필터·앱 공정성 정렬 검증 |
| PostAudienceTest | 원점 없는 과거 행 복원, 신규 생성 엄격성 유지 |
| DirectionMatchingPerformanceIntegrationTest | GiST 필수 기대를 암호문 후보/계산 계약으로 교체 |
| DirectionMatchingE3PerformanceIntegrationTest | 암호문 fixture·성능 전용 설정 유지 |
| DirectionMatchingIndexPlanPerformanceIntegrationTest | 제거된 GiST 강제 대신 실제 변경 후 계획 기록 |
| DirectionMatchingPerformanceProbe | 암호문 쓰기·앱 계산 경계로 probe 갱신 |
| PostgisContainerIntegrationTestSupport | performanceTest에서만 이미지/platform 선택, 일반 기본값 유지 |

## 6. Potential issues

### Application code

AES-256-GCM 전용 key ring과 version/key ID/nonce/tag envelope, 행 ID 및 용도 AAD를 사용한다. current-write/previous-read이며 잘못된 키·변조는 평문 fallback 없이 실패한다. WGS84 순·역방위 계산, 무반올림 공정성 정렬, 단일 statement preview snapshot을 적용했다. API/port 시그니처와 worker/service/web production은 변경하지 않았다.

### Infrastructure and resource limits

OCI·Docker 설치·고정 CPU/메모리·이미지 digest·외부 자원 계측은 미실행이다. performance harness는 1k/10k/100k, warm-up 20/측정 200, current/mixed key, 분포·거리, 실제 worker 4개와 preview 혼합·lock 관측을 준비했다. main 전체 흐름 baseline commit/hash/protocol과 원시 표본·외부 지표가 없으면 BLOCKED이며 임의 성능 수치를 만들지 않는다. 절대 지연은 참고값, 같은 OCI 환경 상대 p95 2배 이내 등 승인 기준 판정은 남아 있다.

### Database and migrations

V35 단일 migration은 정확 좌표 presence만 폐기하고 cell-only는 유지한다. audience 과거 원점은 NULL, 원점을 잃는 미완료 글만 MATCH_FAILED다. ACTIVE/종결 글과 실제 수신자·슬롯·Outbox·메타데이터를 유지하며 과거 migration은 불변이다. SQL SHA-256: b1b557ec7aad4dd5339ebeb5a9ee74939af17e11388a86df36da8fa4636f65c8. #381/PR #384도 V35이므로 나중에 병합하는 쪽이 재번호 후 검증해야 한다.

### Concurrency and idempotency

claim/reclaim·용량 row lock·stale fencing·재생·최신 관측 우선순위 회귀는 PASS다. 새 신호는 실제 claim/transaction 경계에 결합하며 고정 sleep이나 polling으로 통과시키지 않았다. OCI 동시 부하 성능·lease 여유는 미검증이다.

### Transactions and event ordering

실제 adapter 실패와 migration 실패 trigger로 전량 rollback을 검증했다. 합법적인 moderation와 recipient matching 두 Outbox 이벤트를 보존하며 matching 이벤트 범위만 정확히 조회했다.

### External APIs

매칭 계산은 외부 API를 쓰지 않는다. 테스트 moderation fixture를 사용했고 실제 외부 API·SSM·배포는 호출하지 않았다. Dev 좌표 키는 필수 주입이며 fallback이 없다. dev SSM 주입은 별도 인프라 Issue로 처리해야 한다.

### Failure recovery and reconciliation

합성 DB 백업을 별도 DB에 복원하는 검증만 수행했다. 앱 rollback만으로 폐기 좌표가 복구되지는 않는다. 실제 dev/운영 백업·WAL·replica·저장 매체 정리 및 C6/I2, post_recipient 파생 정보(D9)는 범위 밖이다.

## 7. Regression and residual risk

- executed_checks / passed_checks: §3의 PASS 명령과 §4 UNIT/INT. failed_checks: 최종 미해결 없음.
- blocked_checks: PERF-001~003 OCI 측정 및 상대 성능 판정. LSP 진단은 환경 미구성으로 미실행(대체 컴파일·정적 검사 PASS).
- assumptions: 승인된 ADR-0009/D3 보정·TASK 소유 계약, 합성 데이터/fixture key, 이 브랜치 V34 다음 V35. 성능 결과를 기능 회귀에서 추정하지 않는다.
- risks: OCI 미측정, dev key 주입 선행 필요, V35 충돌, 기존 OSV 예외/Moderate findings, 과거 복사본과 파생 위치 정보 별도 보호.
- required_human_decisions: OCI Docker 설치 및 측정 단계 승인, 별도 dev 주입 인프라 작업, V35 병합 순서/재번호. 현재 로컬 구현 추가 범위 승인은 필요 없다.
- 실제 접근/변경: 로컬 테스트 DB만 사용. push·OCI 접속·dev/운영 배포 없음.

## 8. Artifacts

- Test plan: docs/test-plans/gh-388-TEST-PLAN-GH-388-COORDINATE-ENCRYPTION.md (사람 승인, 수정하지 않음).
- Related ADR: docs/adr/0009-encrypt-coordinate-storage-and-replace-spatial-matching.md (accepted).
- Contract: TASK.md 소유 승인 a910241e, 추가 승인 81b4d177/b62f7afe, D3 계획 cb4af36d.
- 실행 보고: 본 문서와 지정 scratchpad report-c8-impl.md. CI run / PR: 이 작업에서 생성하지 않음.
- 구현 목적 커밋: edf0b05d (의존성/성능 실행 환경), d243aca1 (암호화/앱 계산/기능 회귀), 1800bca2 (성능 시나리오). 본 보고서와 스키마 3개는 이후 문서 목적 커밋으로 묶는다.
- 독립 검토: 별도 검증 에이전트의 실제 변경/증거 확인 및 INT-002 재검토 APPROVE(로컬 코드 준비 한정). 예전 ignored review 파일의 20건 실패 기록은 과거 상태이며 최종 판정이 아니다.

### changed_files

실행 작업 59개 파일(기존 계약/ADR/계획 커밋 제외). 승인된 파일만 변경했다.

- build.gradle
- docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md
- docs/product/data-model/direction_communication.dbml
- docs/product/data-model/schema-manifest.md
- docs/reports/tests/gh-388-TEST-PLAN-GH-388-COORDINATE-ENCRYPTION.md
- src/integrationTest/java/com/dnd/qello/ActiveUserPresenceApiIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/ActiveUserPresenceUpdateIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/CoordinateEncryptionPerformanceIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/CoordinateEncryptionTestFixture.java
- src/integrationTest/java/com/dnd/qello/CoordinateMatchingDifferentialIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/CoordinateMigrationIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/CoordinateMigrationRecoveryIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/CoordinatePersistenceIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/CoordinatePostgisOracle.java
- src/integrationTest/java/com/dnd/qello/CoordinateProtectionConfigurationIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/CoordinateProtectionFailureIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/DevProfileBootIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/DirectionMatchingContractIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/DirectionMatchingE3PerformanceIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/DirectionMatchingIndexPlanPerformanceIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/DirectionMatchingPerformanceIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/DirectionMatchingPerformanceProbe.java
- src/integrationTest/java/com/dnd/qello/DirectionMatchingWorkerConcurrencyIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/DirectionMatchingWorkerIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/DirectionPreviewIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/FlywayMigrationIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/LocationCollectionGateDisabledIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/NotificationPreferenceMigrationIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/PostgisContainerIntegrationTestSupport.java
- src/integrationTest/java/com/dnd/qello/RecipientNotificationFanOutWorkerIntegrationTest.java
- src/integrationTest/resources/application-test.yml
- src/main/java/com/dnd/qello/direction/config/CoordinateProtectionProperties.java
- src/main/java/com/dnd/qello/direction/domain/PostAudience.java
- src/main/java/com/dnd/qello/direction/geo/CoordinateGeometry.java
- src/main/java/com/dnd/qello/direction/geo/RankedDirectionCandidate.java
- src/main/java/com/dnd/qello/direction/repository/jdbc/JdbcActiveUserPresenceRepository.java
- src/main/java/com/dnd/qello/direction/repository/jdbc/JdbcPostAudienceRepository.java
- src/main/java/com/dnd/qello/direction/repository/jdbc/sql/ActiveUserPresenceSql.java
- src/main/java/com/dnd/qello/direction/repository/jdbc/sql/PostAudienceSql.java
- src/main/java/com/dnd/qello/direction/security/AesGcmCoordinateProtector.java
- src/main/java/com/dnd/qello/direction/security/Coordinate.java
- src/main/java/com/dnd/qello/direction/security/CoordinateCodec.java
- src/main/java/com/dnd/qello/direction/security/CoordinateKeyRing.java
- src/main/java/com/dnd/qello/direction/security/CoordinateProtectionException.java
- src/main/java/com/dnd/qello/direction/security/CoordinateProtector.java
- src/main/java/com/dnd/qello/direction/security/CoordinatePurpose.java
- src/main/resources/application-dev.yml
- src/main/resources/application-local.yml
- src/main/resources/application.yml
- src/main/resources/db/migration/V35__encrypt_exact_coordinates.sql
- src/test/java/com/dnd/qello/FlywayMigrationContractTest.java
- src/test/java/com/dnd/qello/direction/DirectionPreviewPersistenceBoundaryTest.java
- src/test/java/com/dnd/qello/direction/DirectionRecipientSelectionBoundaryTest.java
- src/test/java/com/dnd/qello/direction/config/CoordinateProtectionPropertiesTest.java
- src/test/java/com/dnd/qello/direction/domain/PostAudienceTest.java
- src/test/java/com/dnd/qello/direction/matching/CoordinateCalculationTest.java
- src/test/java/com/dnd/qello/direction/security/CoordinateCodecTest.java
- src/test/java/com/dnd/qello/direction/security/CoordinateProtectorTest.java
- src/test/java/com/dnd/qello/direction/security/CoordinateRedactionTest.java

## 9. Reviewer checklist

- [x] 실제 좌표·키·환경변수 값·서버/계정 식별자 없이 기록했다.
- [x] 미실행 OCI 성능과 실환경 복구/주입 범위를 명시했다.
- [x] 후속 위험을 #388/ADR D9 및 기존 #382, #381/PR #384와 연결했다. 별도 인프라 Issue는 아직 번호가 없다.
- [x] 실행 결과·중간 실패 수정·독립 검토·사람 승인 범위를 구분했다.
- [x] 기존 변경·과거 migration·승인 게이트를 보존했고 skip/suppression/hook 우회 및 Co-Authored-By를 추가하지 않았다.
