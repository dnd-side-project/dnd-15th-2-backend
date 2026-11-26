# Test Report: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION

> 로컬 실행일: 2026-11-25 / OCI 최종 감사일: 2026-11-26
> Issue: #388 / task_id: TASK-GH-388-COORDINATE-ENCRYPTION / design_id: ADR-0009
> Branch: feat/gh-388-coordinate-encryption / 기존 로컬 검증 코드: 1800bca2 / OCI candidate: c131aa7268219106a78af826ce23567e0e098a52

## 1. Executive summary

- status: BLOCKED (Issue 전체의 필수 성능 증거 미완료). local_implementation_status: PASS.
- 승인된 C8 구현, 갱신 4의 통합 실패 20건 수정, 기능 회귀 및 로컬 필수 검사를 완료했다. 미해결 로컬 기능 실패는 없다.
- 단위 1,778건, 통합 967건: 실패 0, 오류 0, skip 0. 계획의 단위 14/통합 24는 계약 시나리오 수이며 JUnit invocation 수와 다르다.
- PERF-001~003은 OCI에서 32조건·64회 작업량을 완료했다. 필수 외부 지표 8개와 기존 성능 회귀가 BLOCKED이므로 #388 전체 PASS나 배포 가능 판정을 내리지 않는다. 전체 수치·해시는 §10에 기록했다.
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
| ./gradlew performanceTest (OCI, 64 invocations) | BLOCKED | 64회 작업량 완료, 필수 증거 assertion으로 nonzero 종료. 기존 회귀는 동일 DB 제한 경로 부재로 NOT_RUN/BLOCKED |

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
| PERF-001~003 | BLOCKED | CoordinatePerformanceRunner / CoordinateEncryptionPerformanceIntegrationTest | OCI 64회 완료, 32쌍·원자료·실제 동시 commit 감사. 필수 지표 8개와 기존 회귀 차단은 §10 참조 |

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

오케스트레이터가 준비한 OCI에서 동일 arm64 digest, DB/JVM 각 1코어·4GiB·swap 0과 512MiB heap을 직접 확인하고 64회 교대 측정을 완료했다. 100k 조건 24회는 고정 한도에서 완료됐다. 전체 환경·수치·원자료 해시·호출 경계 heap·CPU/GC·동시 처리량·EXPLAIN은 §10에 기록했다. 필수 외부 지표 8개가 없고 기존 회귀는 동일 DB 제한 경로가 없어 BLOCKED다.

### Database and migrations

V35 단일 migration은 정확 좌표 presence만 폐기하고 cell-only는 유지한다. audience 과거 원점은 NULL, 원점을 잃는 미완료 글만 MATCH_FAILED다. ACTIVE/종결 글과 실제 수신자·슬롯·Outbox·메타데이터를 유지하며 과거 migration은 불변이다. SQL SHA-256: b1b557ec7aad4dd5339ebeb5a9ee74939af17e11388a86df36da8fa4636f65c8. #381/PR #384도 V35이므로 나중에 병합하는 쪽이 재번호 후 검증해야 한다.

### Concurrency and idempotency

claim/reclaim·용량 row lock·stale fencing·재생·최신 관측 우선순위 회귀는 PASS다. 새 신호는 실제 claim/transaction 경계에 결합하며 고정 sleep이나 polling으로 통과시키지 않았다. OCI 동시 라운드와 실제 잠금/commit은 §10에 기록했다. lease 여유·DB lock 대기·transaction 시간은 UNKNOWN/BLOCKED다.

### Transactions and event ordering

실제 adapter 실패와 migration 실패 trigger로 전량 rollback을 검증했다. 합법적인 moderation와 recipient matching 두 Outbox 이벤트를 보존하며 matching 이벤트 범위만 정확히 조회했다.

### External APIs

매칭 계산은 외부 API를 쓰지 않는다. 테스트 moderation fixture를 사용했고 실제 외부 API·SSM·배포는 호출하지 않았다. Dev 좌표 키는 필수 주입이며 fallback이 없다. dev SSM 주입은 별도 인프라 Issue로 처리해야 한다.

### Failure recovery and reconciliation

합성 DB 백업을 별도 DB에 복원하는 검증만 수행했다. 앱 rollback만으로 폐기 좌표가 복구되지는 않는다. 실제 dev/운영 백업·WAL·replica·저장 매체 정리 및 C6/I2, post_recipient 파생 정보(D9)는 범위 밖이다.

## 7. Regression and residual risk

- local executed_checks / passed_checks: §3의 기존 PASS 명령과 §4 UNIT/INT. local failed_checks: 최종 미해결 없음. 이번 OCI 성능 실행·nonzero 종료·차단은 §10에 별도 기록했다.
- blocked_checks: OCI 필수 외부 성능 지표 8개 및 동일 DB 자원 제한 경로가 없는 기존 성능 회귀. 전체 성능 PASS는 성립하지 않는다. LSP 진단은 환경 미구성으로 미실행(기존 대체 컴파일·정적 검사 PASS).
- assumptions: 승인된 ADR-0009/D3 보정·TASK 소유 계약, 합성 데이터/fixture key, 이 브랜치 V34 다음 V35. 성능 결과를 기능 회귀에서 추정하지 않는다.
- risks: OCI 필수 외부 지표 누락과 일부 조건의 큰 상대 지연, 기존 성능 회귀 차단, dev key 주입 선행 필요, V35 충돌, 기존 OSV 예외/Moderate findings, 과거 복사본과 파생 위치 정보 별도 보호.
- required_human_decisions: 기존 성능 회귀에도 동일 DB 제한을 적용할 성능 전용 지원 코드/실행 경로의 범위 결정, 별도 dev 주입 인프라 작업, V35 병합 순서/재번호. OCI 측정·추가 인증은 이미 확인했다.
- 실제 접근/변경: 로컬 테스트 DB 및 승인된 OCI 합성 측정, archive 배치와 읽기 전용 진단. push·dev/운영 배포 없음.

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
- [x] OCI 측정 결과와 필수 지표·기존 회귀 차단, 실환경 복구/주입 미검증 범위를 명시했다.
- [x] 후속 위험을 #388/ADR D9 및 기존 #382, #381/PR #384와 연결했다. 별도 인프라 Issue는 아직 번호가 없다.
- [x] 실행 결과·중간 실패 수정·독립 검토·사람 승인 범위를 구분했다.
- [x] 기존 변경·과거 migration·승인 게이트를 보존했고 skip/suppression/hook 우회 및 Co-Authored-By를 추가하지 않았다.

## 10. OCI performance evidence

> 최종 원자료 감사: 2026-11-26T16:15:25.119Z
> performance_status: BLOCKED / issue_number: 388 / task_id: TASK-GH-388-COORDINATE-ENCRYPTION / design_id: ADR-0009
> test_plan_id: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION

### 10.1. 판정과 증거 범위

- round01의 32조건을 main 다음 candidate 순서로 교대 실행했다. 총 64회 모두 작업량을 완료했다. 최종 controller는 runs=64, nonzeroGradleExits=64를 기록했다. 실행 원장 62회는 exit=1이며 초기 1k/10km/DENSE/CURRENT 두 실행의 별도 로그도 같은 필수 증거 assertion과 BUILD FAILED를 기록했다. 원자료 status는 모두 BLOCKED다.
- cold 1회, warm-up 20회, measured 200회, seed 38820261124를 유지했다. 예열은 실제 서비스 및 검증을 실행하지만 runner.retain이 의도적으로 보존하지 않는다. cold와 measured 표본은 별도 보존했다.
- MEASURED 서비스 표본은 총 43,200개다. C1은 구간별 200개, C4는 repository/preview 각 200개와 worker 800개다. 총 보존 서비스 표본은 cold 포함 43,416개다.
- 내려받은 원자료 64개의 SHA-256이 원격 최종 요약의 64개 해시와 일치한다. p50/p95/p99, 96개 상대 비율, 32쌍 환경/계측 경계/잠금 프로토콜을 독립 재검산했다. GeographicLib의 baseline NOT_APPLICABLE/candidate 2.1만 승인된 차이다. JSON 객체 키 순서는 의미 비교에서 제외했다.
- 모든 기능 assertion을 지나 workloadCompleted=true 및 BLOCKED까지 도달했다. 후보 집합/순서/집계, commit된 수신자/예약 슬롯/확정 이벤트, 중복 확정 검사 실패는 0건이다. 이는 실제 assertion 실행에 근거한 판정이며 원자료가 별도 mismatch counter나 최대 거리/각도 오차를 직렬화한다는 뜻은 아니다.
- PERF-001 preview/worker의 8개 상대 p95는 모두 2 이하(0.212194~1.300430)다. 전체 PASS는 아니다. 기존 runner의 자동 2배 assertion은 10k/C1 preview/worker에만 적용된다. 다른 조건의 큰 비율도 아래 표에 모두 기록하며 승인된 D6 기준을 완화하지 않는다.
- PERF-002/003을 포함한 96개 구간 비율 중 41개가 2를 초과한다. 숫자를 삭제하거나 재측정 중 가장 빠른 결과만 선택하지 않았다. SQL oracle 실행 시간은 main 전체 흐름의 성능 기준선으로 사용하지 않았다.
- 100k 조건은 총 24회 모두 고정 자원에서 완료됐다. DB/JVM 한도는 각 4,294,967,296 bytes, JVM heap은 536,870,912 bytes다. 호출 경계에서 관측한 최대 heap은 356,159,120 bytes다. 이는 전체 실행의 peak RSS/peak heap이나 cgroup 전체 peak가 아니다. 메모리 한도 확대나 OOM 후 재측정은 없었다.
- 직접 원인: CoordinatePerformanceRunner.java:392의 D6 필수 증거 assertion. 마지막 GLOBAL/MIXED main은 3h 5m 8s, candidate는 3h 4m 20s였다. 전체 benchmark 실행 시간은 서비스 호출 지연과 구분한다.

필수 지표는 아래 8개가 64회 모두 UNKNOWN/BLOCKED다. 없는 값을 0으로 채우거나 순간 docker stats, barrier 시간, EXPLAIN의 진단값으로 대체하지 않았다.

| 필수 외부 지표 | 결과 |
| --- | --- |
| dbCpuNanos | UNKNOWN/BLOCKED |
| rowsRead | UNKNOWN/BLOCKED |
| wireBytes | UNKNOWN/BLOCKED |
| transactionNanos | UNKNOWN/BLOCKED |
| leaseHeadroomNanos | UNKNOWN/BLOCKED |
| lockWaitNanos | UNKNOWN/BLOCKED |
| dbBufferIo | UNKNOWN/BLOCKED |
| dbTempIo | UNKNOWN/BLOCKED |

### 10.2. 환경과 고정 자원

| 항목 | 값 |
| --- | --- |
| arch | aarch64 |
| cpuModel | Neoverse-N1 |
| hostCores | 2 |
| hostMemoryBytes | 12506693632 |
| dbCpuAllocation | 1 |
| jvmCpuAllocation | 1 |
| dbMemoryLimitBytes | 4294967296 |
| jvmMemoryLimitBytes | 4294967296 |
| jvmHeapBytes | 536870912 |
| kernel | 6.17.0-1020-oracle |
| jdk | 21.0.13.1+2-1-24.04.1-Ubuntu |
| postgres | 16.10 |
| postgis | 3.5.3 (GEOS 3.13.1) |
| proj | 9.6.0 |
| imageDigest | sha256:367f65cee655c1cc7ac2ed17b0c77ca516ab8634c53698eda4a799f23a575e89 |
| containerSettings | {"dbCpuset":"0","dbMemoryBytes":4294967296,"dbMemorySwapBytes":4294967296,"jvmCpuset":"1","jvmScopeMemoryBytes":4294967296,"jvmScopeSwapBytes":0,"jvmHeapBytes":536870912,"pgStatStatements":true} |
| network | same-host Testcontainers bridge and mapped JDBC |
| concurrentLoad | no unrelated workloads; PERF-003 four workers and one preview |
| executionOrder | each scenario/condition: fixed main baseline then fixed candidate; all attempts retained |
| geographicLib | main NOT_APPLICABLE / candidate 2.1 |
| DB memory+swap | 4294967296 bytes (= memory; swap disabled) |
| JVM scope MemorySwapMax | 0 bytes |

DB CPU 0, JVM/Gradle CPU 1과 실제 Docker/cgroup 한도를 직접 조회했다. 일반 CI의 amd64 PostgreSQL 16.14와 OCI arm64의 16.10은 patch 버전이 다르므로 이 측정을 CI 또는 운영의 절대 지연 보장으로 일반화하지 않는다. Tailscale SSH 추가 인증 시 진단 접속을 종료했고 사용자 재인증 확인 후 직접 SSH 성공을 확인하여 재개했다. 인증 URL, 서버 주소, 실제 계정/키는 기록하지 않는다.

### 10.3. 배치와 원자료 무결성

| Artifact | commit / SHA-256 |
| --- | --- |
| main commit | dae144f831224368538f754005c5c66401277e1e |
| candidate commit | c131aa7268219106a78af826ce23567e0e098a52 |
| main archive | 96674b4d679ec81c271d087582649c852df92d88a9ce281bec601bc1b5d234af |
| candidate archive | 7ca59845d6c1aa84750b85059697e8aa0a1fac1a94ed66939a46f87dbf1a8521 |
| baseline-only overlay patch | 37a904d061e7c621a5c0f06dae9c48bfeda647cb42186f230728efaef0b0eba0 |
| 공통 CoordinatePerformanceRunner.java (양쪽 동일) | eac650baacc7798135be3fb4750ba37e3584eb6d4593259760d0db1d68d8db62 |
| baseline COMMIT file | 23f85cb261be693db6e780c5c5364daff0a09065885cbbe7f99f81f1003b315a |
| candidate COMMIT file | 9677be4cfeeea6479aec8155795148c860bf6f26ceea7adaca86c964e2b1ec11 |
| 원격 최종 64회 요약 | bf643480407dd8523f27be72a43eb6f74c82ba95be1d64e442f6a9a300680ab5 |
| 원자료 기반 상세 요약 (CPU/GC/동시 처리량 포함) | cf8a58abcad8a8803b501e5235bc0ff0d6a70d1b1d058be20f53d7a508c981ea |
| execution-ledger.log (62회 시작/종료) | cb9442daa30bf2ed8ff9fd1d87bd68dc0db07a9df5adcbcf9d9ca9ad74a61d47 |
| 초기 1k/10km/DENSE/CURRENT main log | c90f371e174d6bcae7ca9e856d87551079a013e5d29c956095ac8535ca34269c |
| 초기 1k/10km/DENSE/CURRENT candidate log | defb1ace3e8e66449b678312accb8696896e86881c47ec7ecb93408689fe6b3e |
| SQL oracle baseline commit | a910241e140071b3b34e9e05f461efdc7e43e24d |
| SQL oracle original source | 9026c39ce7a7a393b713a5d7835a56b2ad21d746b576fd0319a161c668bf65ae |

overlay는 baseline 사본에만 적용했다. 적용 파일은 build.gradle, src/integrationTest/java/com/dnd/qello/CoordinatePerformanceRunner.java, CoordinatePlaintextPerformanceIntegrationTest.java, PostgisContainerIntegrationTestSupport.java다. 두 Java 파일의 경로 접두사도 src/integrationTest/java/com/dnd/qello/다. 원격 소스를 직접 편집하지 않고 로컬 준비 archive를 배치했다.

원자료와 모든 회차 로그는 OCI 작업 디렉터리의 results/round01/main 및 results/round01/candidate에 보존했다. execution-ledger.log와 이전 요약 snapshot도 보존했다. 아래에는 요약과 해시만 기록하며 raw samples를 복사하지 않는다.

원장에는 31쌍(62회)이 있고, 초기 1k/10km/DENSE/CURRENT 한 쌍은 별도 로그 두 개에 있다. 이를 합쳐 64회 증거를 유지했다. 초기 main/candidate의 BUILD FAILED 시간은 각각 3m 52s / 2m 27s이며 두 로그 모두 CoordinatePerformanceRunner.java:392의 필수 증거 assertion을 확인했다. 원자료 64개는 별도로 모두 해시 검증했다.


### 10.4. 전체 서비스 지연과 처리량

지연은 ms, 각 칸은 p50 / p95 / p99다. service calls/s는 서비스 시간 평균의 역수이며 fixture reset/oracle 등 전체 benchmark 벽시계 처리량 또는 C4 동시 처리량이 아니다.

| 시나리오 / 조건 | 구간 | main ms | candidate ms | p95 ratio | main service calls/s | candidate service calls/s |
| --- | --- | --- | --- | --- | --- | --- |
| PERF-001 / 10000-20100000-DENSE-CURRENT-C1 | repository | 68.316404 / 69.919570 / 75.032192 | 100.519058 / 106.630283 / 119.698897 | 1.525042 | 14.646409 | 9.949000 |
| PERF-001 / 10000-20100000-DENSE-CURRENT-C1 | preview | 632.575308 / 638.272692 / 704.115404 | 116.294363 / 138.817496 / 220.347675 | 0.217489 | 1.575716 | 8.397841 |
| PERF-001 / 10000-20100000-DENSE-CURRENT-C1 | worker | 98.056407 / 113.849113 / 119.985298 | 131.305865 / 147.567893 / 183.278801 | 1.296171 | 9.917066 | 7.498867 |
| PERF-001 / 10000-20100000-DENSE-MIXED-C1 | repository | 69.973131 / 72.046059 / 80.134772 | 100.740117 / 103.203014 / 109.825163 | 1.432459 | 14.269739 | 10.035230 |
| PERF-001 / 10000-20100000-DENSE-MIXED-C1 | preview | 634.831036 / 639.168014 / 641.534784 | 115.571982 / 135.627355 / 185.442016 | 0.212194 | 1.572387 | 8.486871 |
| PERF-001 / 10000-20100000-DENSE-MIXED-C1 | worker | 100.227536 / 115.550680 / 126.083124 | 130.837447 / 147.265206 / 195.246374 | 1.274464 | 9.757139 | 7.508834 |
| PERF-001 / 10000-20100000-GLOBAL-CURRENT-C1 | repository | 83.432034 / 85.417403 / 95.170766 | 115.899368 / 119.725785 / 134.693768 | 1.401656 | 11.954721 | 8.651731 |
| PERF-001 / 10000-20100000-GLOBAL-CURRENT-C1 | preview | 730.069582 / 735.228361 / 740.296828 | 133.569203 / 159.691433 / 205.778066 | 0.217200 | 1.368373 | 7.280705 |
| PERF-001 / 10000-20100000-GLOBAL-CURRENT-C1 | worker | 113.211721 / 124.380212 / 132.475286 | 145.908254 / 161.747801 / 180.388761 | 1.300430 | 8.716540 | 6.747823 |
| PERF-001 / 10000-20100000-GLOBAL-MIXED-C1 | repository | 82.911268 / 84.461594 / 88.772572 | 115.176584 / 119.456341 / 129.137581 | 1.414327 | 12.056495 | 8.684770 |
| PERF-001 / 10000-20100000-GLOBAL-MIXED-C1 | preview | 717.983974 / 724.349557 / 780.447672 | 132.530516 / 155.638595 / 211.970008 | 0.214867 | 1.389599 | 7.371070 |
| PERF-001 / 10000-20100000-GLOBAL-MIXED-C1 | worker | 112.882194 / 126.414010 / 133.671921 | 145.540718 / 160.041151 / 174.977293 | 1.266008 | 8.699047 | 6.792413 |
| PERF-002 / 1000-10000-DENSE-CURRENT-C1 | repository | 0.871004 / 1.681447 / 3.106413 | 10.307683 / 27.429275 / 31.634092 | 16.312899 | 1002.506542 | 75.067813 |
| PERF-002 / 1000-10000-DENSE-CURRENT-C1 | preview | 3.133693 / 5.999345 / 7.101510 | 13.401816 / 30.026645 / 52.207818 | 5.004987 | 265.763130 | 57.278667 |
| PERF-002 / 1000-10000-DENSE-CURRENT-C1 | worker | 11.995490 / 20.873166 / 27.309473 | 24.027140 / 40.370568 / 50.506731 | 1.934089 | 74.477810 | 36.898690 |
| PERF-002 / 1000-10000-DENSE-MIXED-C1 | repository | 0.890524 / 1.834008 / 3.177254 | 10.461085 / 24.818667 / 34.573189 | 13.532475 | 978.749987 | 73.078464 |
| PERF-002 / 1000-10000-DENSE-MIXED-C1 | preview | 3.252974 / 6.461748 / 8.837918 | 14.102940 / 28.742563 / 39.935972 | 4.448110 | 254.026897 | 57.660031 |
| PERF-002 / 1000-10000-DENSE-MIXED-C1 | worker | 12.909575 / 20.243608 / 28.199882 | 23.917783 / 45.484636 / 55.284838 | 2.246864 | 71.321432 | 35.872911 |
| PERF-002 / 1000-10000-GLOBAL-CURRENT-C1 | repository | 0.710643 / 2.663052 / 4.390099 | 14.727063 / 33.617624 / 45.322594 | 12.623720 | 901.198873 | 55.371703 |
| PERF-002 / 1000-10000-GLOBAL-CURRENT-C1 | preview | 3.917216 / 6.841909 / 8.887638 | 20.032806 / 46.449478 / 61.967465 | 6.788965 | 237.122477 | 40.733954 |
| PERF-002 / 1000-10000-GLOBAL-CURRENT-C1 | worker | 9.563761 / 17.002153 / 44.692952 | 25.958911 / 54.449033 / 66.984966 | 3.202479 | 91.816063 | 32.509229 |
| PERF-002 / 1000-10000-GLOBAL-MIXED-C1 | repository | 0.641122 / 1.848088 / 2.506691 | 12.020012 / 25.093867 / 33.582383 | 13.578286 | 1261.499872 | 64.940452 |
| PERF-002 / 1000-10000-GLOBAL-MIXED-C1 | preview | 2.955333 / 5.058861 / 7.967554 | 15.965108 / 39.662408 / 49.341690 | 7.840185 | 308.755474 | 48.425094 |
| PERF-002 / 1000-10000-GLOBAL-MIXED-C1 | worker | 7.771753 / 12.261292 / 17.029553 | 20.321207 / 38.819125 / 58.390689 | 3.165990 | 122.898988 | 40.982830 |
| PERF-002 / 1000-100000-DENSE-CURRENT-C1 | repository | 7.022990 / 7.584232 / 13.598977 | 10.167203 / 23.026138 / 39.902169 | 3.036054 | 138.472236 | 75.662855 |
| PERF-002 / 1000-100000-DENSE-CURRENT-C1 | preview | 50.796776 / 54.600312 / 60.267096 | 14.030860 / 29.198283 / 39.963329 | 0.534764 | 19.367578 | 56.637350 |
| PERF-002 / 1000-100000-DENSE-CURRENT-C1 | worker | 37.056917 / 52.470983 / 63.873431 | 43.605705 / 66.160240 / 86.484007 | 1.260892 | 25.241787 | 20.819818 |
| PERF-002 / 1000-100000-DENSE-MIXED-C1 | repository | 7.089830 / 8.402836 / 13.813179 | 10.114963 / 21.944413 / 33.868143 | 2.611548 | 136.919183 | 82.059670 |
| PERF-002 / 1000-100000-DENSE-MIXED-C1 | preview | 51.425737 / 54.443750 / 76.581884 | 13.690458 / 28.815562 / 37.326798 | 0.529272 | 19.059297 | 59.193402 |
| PERF-002 / 1000-100000-DENSE-MIXED-C1 | worker | 38.227322 / 53.694147 / 61.600580 | 41.664936 / 68.125848 / 84.438917 | 1.268776 | 24.747733 | 21.390069 |
| PERF-002 / 1000-100000-GLOBAL-CURRENT-C1 | repository | 0.642763 / 1.897288 / 2.597051 | 11.997331 / 25.388947 / 32.470337 | 13.381704 | 1306.973251 | 67.260197 |
| PERF-002 / 1000-100000-GLOBAL-CURRENT-C1 | preview | 2.680372 / 5.664224 / 6.879509 | 14.924023 / 39.222886 / 48.564965 | 6.924671 | 306.116311 | 50.835042 |
| PERF-002 / 1000-100000-GLOBAL-CURRENT-C1 | worker | 7.580312 / 12.266132 / 15.058623 | 19.486082 / 42.790501 / 62.805105 | 3.488508 | 124.009166 | 40.558905 |
| PERF-002 / 1000-100000-GLOBAL-MIXED-C1 | repository | 0.624282 / 2.112809 / 2.639091 | 11.819170 / 25.979589 / 31.630573 | 12.296232 | 1289.924743 | 64.448755 |
| PERF-002 / 1000-100000-GLOBAL-MIXED-C1 | preview | 2.561811 / 5.654464 / 6.672348 | 15.827587 / 35.623591 / 47.678761 | 6.300083 | 315.232057 | 50.467981 |
| PERF-002 / 1000-100000-GLOBAL-MIXED-C1 | worker | 7.300071 / 11.840570 / 16.116228 | 20.804568 / 40.605411 / 55.790796 | 3.429346 | 127.827287 | 40.410767 |
| PERF-002 / 1000-20100000-DENSE-CURRENT-C1 | repository | 7.792953 / 8.634757 / 15.171864 | 10.972686 / 24.043781 / 42.100378 | 2.784535 | 123.436712 | 68.525024 |
| PERF-002 / 1000-20100000-DENSE-CURRENT-C1 | preview | 64.802793 / 68.018647 / 78.940012 | 14.613222 / 35.217708 / 46.321235 | 0.517765 | 15.216419 | 53.264665 |
| PERF-002 / 1000-20100000-DENSE-CURRENT-C1 | worker | 37.477918 / 50.027090 / 57.058200 | 44.797788 / 69.796255 / 95.636883 | 1.395169 | 25.111821 | 20.417430 |
| PERF-002 / 1000-20100000-DENSE-MIXED-C1 | repository | 7.866194 / 8.671276 / 9.897041 | 10.424803 / 23.384659 / 42.049537 | 2.696796 | 125.256437 | 70.140607 |
| PERF-002 / 1000-20100000-DENSE-MIXED-C1 | preview | 65.282275 / 68.304688 / 78.705572 | 15.347025 / 31.231771 / 38.877764 | 0.457242 | 15.102213 | 53.094720 |
| PERF-002 / 1000-20100000-DENSE-MIXED-C1 | worker | 37.865320 / 51.718458 / 60.855896 | 44.438547 / 71.333140 / 97.571171 | 1.379259 | 24.758229 | 20.282971 |
| PERF-002 / 1000-20100000-GLOBAL-CURRENT-C1 | repository | 9.547719 / 10.269921 / 11.877489 | 11.761128 / 24.751581 / 34.745622 | 2.410104 | 104.227257 | 67.602649 |
| PERF-002 / 1000-20100000-GLOBAL-CURRENT-C1 | preview | 74.200301 / 77.459154 / 88.314999 | 16.140626 / 34.186060 / 45.661787 | 0.441343 | 13.282095 | 49.504475 |
| PERF-002 / 1000-20100000-GLOBAL-CURRENT-C1 | worker | 38.913318 / 55.935987 / 75.999269 | 45.551186 / 67.458156 / 80.817890 | 1.205988 | 23.756653 | 20.189838 |
| PERF-002 / 1000-20100000-GLOBAL-MIXED-C1 | repository | 8.910037 / 9.914161 / 10.688084 | 11.894170 / 25.774186 / 30.021244 | 2.599734 | 109.332149 | 65.430612 |
| PERF-002 / 1000-20100000-GLOBAL-MIXED-C1 | preview | 75.556151 / 78.671883 / 96.125195 | 17.277752 / 31.784811 / 38.845440 | 0.404017 | 13.073592 | 48.331085 |
| PERF-002 / 1000-20100000-GLOBAL-MIXED-C1 | worker | 38.905920 / 51.308331 / 62.033375 | 45.819349 / 73.090501 / 95.415434 | 1.424535 | 24.280255 | 19.583463 |
| PERF-002 / 100000-10000-DENSE-CURRENT-C1 | repository | 9.695922 / 11.019527 / 11.451328 | 1263.571532 / 1440.841158 / 1539.584478 | 130.753449 | 100.842677 | 0.767185 |
| PERF-002 / 100000-10000-DENSE-CURRENT-C1 | preview | 55.931233 / 58.825645 / 64.106107 | 1346.080220 / 1492.141774 / 1558.814235 | 25.365498 | 17.620018 | 0.737435 |
| PERF-002 / 100000-10000-DENSE-CURRENT-C1 | worker | 39.261408 / 52.269346 / 56.588836 | 1339.388622 / 1486.322411 / 1519.133888 | 28.435833 | 24.341817 | 0.738823 |
| PERF-002 / 100000-10000-DENSE-MIXED-C1 | repository | 9.844642 / 10.989606 / 11.758170 | 1308.851939 / 1448.872688 / 1462.257629 | 131.840276 | 100.578008 | 0.755640 |
| PERF-002 / 100000-10000-DENSE-MIXED-C1 | preview | 55.674195 / 58.429408 / 60.323215 | 1325.273451 / 1477.462495 / 1508.478944 | 25.286282 | 17.777357 | 0.739066 |
| PERF-002 / 100000-10000-DENSE-MIXED-C1 | worker | 40.047569 / 56.411199 / 74.044352 | 1383.269893 / 1486.610248 / 1537.368947 | 26.353105 | 23.560836 | 0.729616 |
| PERF-002 / 100000-10000-GLOBAL-CURRENT-C1 | repository | 3.478414 / 3.762696 / 4.412179 | 1501.571694 / 1624.019141 / 1706.155766 | 431.610510 | 283.762519 | 0.666661 |
| PERF-002 / 100000-10000-GLOBAL-CURRENT-C1 | preview | 5.575344 / 8.899358 / 10.629844 | 1538.306061 / 1685.172118 / 1772.598628 | 189.358841 | 162.506525 | 0.644245 |
| PERF-002 / 100000-10000-GLOBAL-CURRENT-C1 | worker | 10.141882 / 14.573181 / 18.228996 | 1513.314113 / 1654.581389 / 1683.973134 | 113.536049 | 92.118936 | 0.656479 |
| PERF-002 / 100000-10000-GLOBAL-MIXED-C1 | repository | 3.325894 / 3.994377 / 5.741425 | 1485.057949 / 1631.617144 / 1695.325803 | 408.478505 | 291.676647 | 0.671360 |
| PERF-002 / 100000-10000-GLOBAL-MIXED-C1 | preview | 5.255342 / 8.849638 / 10.811208 | 1550.700040 / 1681.213166 / 1759.394684 | 189.975360 | 165.688467 | 0.647202 |
| PERF-002 / 100000-10000-GLOBAL-MIXED-C1 | worker | 9.399961 / 13.553020 / 15.569348 | 1511.798409 / 1629.355987 / 1715.803768 | 120.220880 | 99.520086 | 0.658510 |
| PERF-002 / 100000-100000-DENSE-CURRENT-C1 | repository | 654.098320 / 1118.743859 / 1147.593270 | 1311.631427 / 1441.297262 / 1492.614025 | 1.288317 | 1.403636 | 0.752633 |
| PERF-002 / 100000-100000-DENSE-CURRENT-C1 | preview | 5059.291499 / 5169.268494 / 5214.416233 | 1534.718109 / 1707.199793 / 1792.645093 | 0.330259 | 0.197974 | 0.645754 |
| PERF-002 / 100000-100000-DENSE-CURRENT-C1 | worker | 685.193822 / 1156.087851 / 1182.909119 | 1392.826694 / 1506.347814 / 1569.218613 | 1.302970 | 1.358511 | 0.714308 |
| PERF-002 / 100000-100000-DENSE-MIXED-C1 | repository | 653.787760 / 1118.830370 / 1244.640224 | 1308.928192 / 1469.398973 / 1498.818090 | 1.313335 | 1.408992 | 0.747813 |
| PERF-002 / 100000-100000-DENSE-MIXED-C1 | preview | 5024.480322 / 5115.453853 / 5166.003203 | 1548.249282 / 1694.494433 / 1778.699863 | 0.331250 | 0.199656 | 0.645339 |
| PERF-002 / 100000-100000-DENSE-MIXED-C1 | worker | 685.173927 / 1148.859082 / 1175.960589 | 1395.396367 / 1508.972327 / 1523.027679 | 1.313453 | 1.360206 | 0.715069 |
| PERF-002 / 100000-100000-GLOBAL-CURRENT-C1 | repository | 3.486494 / 3.724495 / 5.162141 | 1465.806111 / 1611.259582 / 1663.260436 | 432.611557 | 282.622093 | 0.677060 |
| PERF-002 / 100000-100000-GLOBAL-CURRENT-C1 | preview | 5.783704 / 8.785156 / 11.147445 | 1531.727803 / 1662.811353 / 1710.864509 | 189.275108 | 158.921935 | 0.649865 |
| PERF-002 / 100000-100000-GLOBAL-CURRENT-C1 | worker | 9.610759 / 13.761816 / 16.627188 | 1499.894728 / 1644.445114 / 1684.202879 | 119.493322 | 97.041937 | 0.660228 |
| PERF-002 / 100000-100000-GLOBAL-MIXED-C1 | repository | 3.431534 / 3.735016 / 5.077501 | 1475.902485 / 1626.934680 / 1704.455940 | 435.589748 | 286.283381 | 0.670276 |
| PERF-002 / 100000-100000-GLOBAL-MIXED-C1 | preview | 5.667823 / 8.630635 / 10.467483 | 1566.540695 / 1717.502609 / 1769.174702 | 199.000723 | 160.183011 | 0.643184 |
| PERF-002 / 100000-100000-GLOBAL-MIXED-C1 | worker | 9.541399 / 13.352136 / 16.098346 | 1508.563388 / 1658.376274 / 1691.820649 | 124.203069 | 96.864701 | 0.656794 |
| PERF-002 / 100000-20100000-DENSE-CURRENT-C1 | repository | 733.021236 / 1119.615699 / 1249.776860 | 1316.746223 / 1459.954957 / 1788.521921 | 1.303979 | 1.233332 | 0.749550 |
| PERF-002 / 100000-20100000-DENSE-CURRENT-C1 | preview | 6518.704900 / 6668.363262 / 6746.068959 | 1595.315564 / 1792.906011 / 1914.988882 | 0.268867 | 0.155549 | 0.620093 |
| PERF-002 / 100000-20100000-DENSE-CURRENT-C1 | worker | 763.184827 / 1148.220125 / 1168.707438 | 1386.262137 / 1502.856716 / 1523.671400 | 1.308858 | 1.214895 | 0.718107 |
| PERF-002 / 100000-20100000-DENSE-MIXED-C1 | repository | 716.938286 / 1090.819394 / 1237.318436 | 1311.434858 / 1469.056770 / 1812.924435 | 1.346746 | 1.330927 | 0.747332 |
| PERF-002 / 100000-20100000-DENSE-MIXED-C1 | preview | 6657.391546 / 6799.191944 / 6872.411704 | 1644.735342 / 1809.162123 / 1875.204283 | 0.266085 | 0.150916 | 0.614787 |
| PERF-002 / 100000-20100000-DENSE-MIXED-C1 | worker | 746.747384 / 1121.685702 / 1137.881542 | 1393.718492 / 1537.795757 / 1600.158089 | 1.370968 | 1.290462 | 0.711147 |
| PERF-002 / 100000-20100000-GLOBAL-CURRENT-C1 | repository | 846.558246 / 1242.639394 / 1365.321330 | 1599.591382 / 1657.806156 / 1768.178796 | 1.334101 | 1.066682 | 0.636753 |
| PERF-002 / 100000-20100000-GLOBAL-CURRENT-C1 | preview | 7440.790748 / 7563.249079 / 7610.362817 | 1821.894234 / 2001.708595 / 2055.711755 | 0.264663 | 0.136192 | 0.546552 |
| PERF-002 / 100000-20100000-GLOBAL-CURRENT-C1 | worker | 880.256576 / 1283.661306 / 1397.157644 | 1559.776321 / 1699.404925 / 1770.850557 | 1.323873 | 1.034296 | 0.634092 |
| PERF-002 / 100000-20100000-GLOBAL-MIXED-C1 | repository | 848.519239 / 1242.828527 / 1369.393074 | 1602.221494 / 1687.426673 / 2164.538970 | 1.357731 | 1.060544 | 0.633527 |
| PERF-002 / 100000-20100000-GLOBAL-MIXED-C1 | preview | 7325.016297 / 7480.969083 / 7536.778871 | 1866.602813 / 2036.668986 / 2082.946507 | 0.272247 | 0.138194 | 0.534566 |
| PERF-002 / 100000-20100000-GLOBAL-MIXED-C1 | worker | 880.218778 / 1276.454013 / 1301.008836 | 1558.551242 / 1681.318216 / 1757.916057 | 1.317179 | 1.040747 | 0.637800 |
| PERF-003 / 10000-20100000-DENSE-CURRENT-C4 | repository | 68.202484 / 70.330014 / 89.248931 | 101.308944 / 107.654811 / 111.025784 | 1.530709 | 14.537296 | 9.991807 |
| PERF-003 / 10000-20100000-DENSE-CURRENT-C4 | preview | 856.867329 / 8170.286693 / 8242.762294 | 498.909088 / 545.064639 / 646.086184 | 0.066713 | 0.601949 | 1.984622 |
| PERF-003 / 10000-20100000-DENSE-CURRENT-C4 | worker | 943.470541 / 8261.864487 / 8337.942490 | 753.478752 / 815.515493 / 914.703708 | 0.098708 | 0.587011 | 1.319264 |
| PERF-003 / 10000-20100000-DENSE-MIXED-C4 | repository | 68.921966 / 70.728735 / 84.080990 | 103.464591 / 108.618333 / 122.321710 | 1.535703 | 14.497537 | 9.752620 |
| PERF-003 / 10000-20100000-DENSE-MIXED-C4 | preview | 858.370249 / 8181.054430 / 8261.741497 | 507.463558 / 564.555873 / 788.904930 | 0.069008 | 0.749555 | 1.926909 |
| PERF-003 / 10000-20100000-DENSE-MIXED-C4 | worker | 945.756370 / 8241.095952 / 8320.360616 | 769.043609 / 821.921945 / 866.743692 | 0.099735 | 0.746193 | 1.298996 |
| PERF-003 / 10000-20100000-GLOBAL-CURRENT-C4 | repository | 83.317148 / 85.079516 / 90.422058 | 115.077356 / 120.425418 / 127.421288 | 1.415445 | 11.982247 | 8.757271 |
| PERF-003 / 10000-20100000-GLOBAL-CURRENT-C4 | preview | 994.440678 / 8450.553111 / 8528.805591 | 575.969502 / 633.809650 / 769.702180 | 0.075002 | 0.600281 | 1.718253 |
| PERF-003 / 10000-20100000-GLOBAL-CURRENT-C4 | worker | 1098.329110 / 8540.876367 / 8567.020128 | 858.577280 / 930.274290 / 1075.789342 | 0.108920 | 0.624761 | 1.156837 |
| PERF-003 / 10000-20100000-GLOBAL-MIXED-C4 | repository | 82.424383 / 83.967510 / 96.753964 | 116.782127 / 122.542152 / 134.095519 | 1.459400 | 12.122857 | 8.615522 |
| PERF-003 / 10000-20100000-GLOBAL-MIXED-C4 | preview | 990.596290 / 8346.465116 / 8446.660911 | 586.878210 / 630.083030 / 786.004321 | 0.075491 | 0.605470 | 1.694298 |
| PERF-003 / 10000-20100000-GLOBAL-MIXED-C4 | worker | 1089.978626 / 8424.730225 / 8490.492445 | 863.954726 / 917.083108 / 1029.550458 | 0.108856 | 0.644902 | 1.154278 |

### 10.5. 앱 CPU와 GC 분포

각 칸은 p50 / p95 / p99다. CPU는 ms, GC count는 회, GC time은 ms다. CPU/GC는 process-wide delta로 C4 동시 호출 사이에 겹치므로 합산하거나 CPU 사용률로 해석하지 않는다. 모든 값은 원자료 표본에서 계산했다.

| 조건 / 구간 | main CPU ms | candidate CPU ms | main GC count | candidate GC count | main GC ms | candidate GC ms |
| --- | --- | --- | --- | --- | --- | --- |
| 10000-20100000-DENSE-CURRENT-C1 / repository | 0.000000 / 10.000000 / 20.000000 | 90.000000 / 100.000000 / 110.000000 | 0 / 0 / 0 | 1 / 1 / 1 | 0 / 0 / 0 | 5 / 6 / 6 |
| 10000-20100000-DENSE-CURRENT-C1 / preview | 0.000000 / 30.000000 / 100.000000 | 100.000000 / 140.000000 / 220.000000 | 0 / 0 / 0 | 1 / 1 / 1 | 0 / 0 / 0 | 6 / 9 / 16 |
| 10000-20100000-DENSE-CURRENT-C1 / worker | 10.000000 / 40.000000 / 50.000000 | 100.000000 / 120.000000 / 160.000000 | 0 / 1 / 1 | 1 / 1 / 1 | 0 / 6 / 14 | 5 / 6 / 7 |
| 10000-20100000-DENSE-MIXED-C1 / repository | 0.000000 / 10.000000 / 10.000000 | 90.000000 / 100.000000 / 100.000000 | 0 / 0 / 1 | 1 / 1 / 1 | 0 / 0 / 9 | 5 / 6 / 6 |
| 10000-20100000-DENSE-MIXED-C1 / preview | 0.000000 / 30.000000 / 190.000000 | 100.000000 / 120.000000 / 170.000000 | 0 / 0 / 2 | 1 / 1 / 1 | 0 / 0 / 19 | 7 / 9 / 12 |
| 10000-20100000-DENSE-MIXED-C1 / worker | 10.000000 / 30.000000 / 50.000000 | 90.000000 / 120.000000 / 170.000000 | 0 / 0 / 1 | 1 / 1 / 1 | 0 / 0 / 10 | 5 / 6 / 7 |
| 10000-20100000-GLOBAL-CURRENT-C1 / repository | 0.000000 / 10.000000 / 10.000000 | 100.000000 / 110.000000 / 110.000000 | 0 / 0 / 1 | 1 / 1 / 1 | 0 / 0 / 8 | 5 / 6 / 6 |
| 10000-20100000-GLOBAL-CURRENT-C1 / preview | 0.000000 / 40.000000 / 180.000000 | 120.000000 / 160.000000 / 200.000000 | 0 / 0 / 2 | 1 / 1 / 1 | 0 / 0 / 24 | 7 / 10 / 10 |
| 10000-20100000-GLOBAL-CURRENT-C1 / worker | 10.000000 / 30.000000 / 50.000000 | 110.000000 / 140.000000 / 140.000000 | 0 / 0 / 1 | 1 / 1 / 1 | 0 / 0 / 8 | 5 / 6 / 6 |
| 10000-20100000-GLOBAL-MIXED-C1 / repository | 0.000000 / 10.000000 / 20.000000 | 100.000000 / 110.000000 / 110.000000 | 0 / 0 / 0 | 1 / 1 / 1 | 0 / 0 / 0 | 5 / 6 / 6 |
| 10000-20100000-GLOBAL-MIXED-C1 / preview | 0.000000 / 40.000000 / 170.000000 | 120.000000 / 140.000000 / 210.000000 | 0 / 0 / 2 | 1 / 1 / 1 | 0 / 0 / 19 | 7 / 10 / 11 |
| 10000-20100000-GLOBAL-MIXED-C1 / worker | 10.000000 / 30.000000 / 50.000000 | 110.000000 / 130.000000 / 160.000000 | 0 / 0 / 1 | 1 / 1 / 1 | 0 / 0 / 9 | 5 / 6 / 6 |
| 1000-10000-DENSE-CURRENT-C1 / repository | 0.000000 / 10.000000 / 10.000000 | 10.000000 / 30.000000 / 30.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 5 / 13 |
| 1000-10000-DENSE-CURRENT-C1 / preview | 0.000000 / 10.000000 / 10.000000 | 10.000000 / 30.000000 / 50.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 5 / 20 |
| 1000-10000-DENSE-CURRENT-C1 / worker | 10.000000 / 20.000000 / 30.000000 | 20.000000 / 40.000000 / 60.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 5 / 10 |
| 1000-10000-DENSE-MIXED-C1 / repository | 0.000000 / 0.000000 / 10.000000 | 10.000000 / 30.000000 / 30.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 8 / 11 |
| 1000-10000-DENSE-MIXED-C1 / preview | 0.000000 / 10.000000 / 20.000000 | 10.000000 / 30.000000 / 40.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 4 / 19 |
| 1000-10000-DENSE-MIXED-C1 / worker | 10.000000 / 20.000000 / 30.000000 | 20.000000 / 40.000000 / 60.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 5 / 20 |
| 1000-10000-GLOBAL-CURRENT-C1 / repository | 0.000000 / 0.000000 / 10.000000 | 10.000000 / 30.000000 / 50.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 8 / 15 |
| 1000-10000-GLOBAL-CURRENT-C1 / preview | 0.000000 / 10.000000 / 10.000000 | 20.000000 / 50.000000 / 60.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 11 / 22 |
| 1000-10000-GLOBAL-CURRENT-C1 / worker | 10.000000 / 20.000000 / 20.000000 | 20.000000 / 50.000000 / 60.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 9 / 22 |
| 1000-10000-GLOBAL-MIXED-C1 / repository | 0.000000 / 0.000000 / 10.000000 | 10.000000 / 30.000000 / 30.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 4 / 11 |
| 1000-10000-GLOBAL-MIXED-C1 / preview | 0.000000 / 10.000000 / 10.000000 | 10.000000 / 40.000000 / 50.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 12 / 21 |
| 1000-10000-GLOBAL-MIXED-C1 / worker | 10.000000 / 10.000000 / 20.000000 | 20.000000 / 40.000000 / 60.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 8 / 21 |
| 1000-100000-DENSE-CURRENT-C1 / repository | 0.000000 / 10.000000 / 10.000000 | 10.000000 / 30.000000 / 40.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 5 / 21 |
| 1000-100000-DENSE-CURRENT-C1 / preview | 0.000000 / 50.000000 / 60.000000 | 10.000000 / 30.000000 / 40.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 9 / 15 |
| 1000-100000-DENSE-CURRENT-C1 / worker | 10.000000 / 40.000000 / 60.000000 | 20.000000 / 60.000000 / 70.000000 | 0 / 0 / 1 | 0 / 1 / 1 | 0 / 0 / 1 | 0 / 5 / 12 |
| 1000-100000-DENSE-MIXED-C1 / repository | 0.000000 / 10.000000 / 10.000000 | 10.000000 / 20.000000 / 30.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 5 / 14 |
| 1000-100000-DENSE-MIXED-C1 / preview | 0.000000 / 40.000000 / 50.000000 | 10.000000 / 30.000000 / 30.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 5 / 11 |
| 1000-100000-DENSE-MIXED-C1 / worker | 10.000000 / 40.000000 / 50.000000 | 20.000000 / 60.000000 / 80.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 5 / 12 |
| 1000-100000-GLOBAL-CURRENT-C1 / repository | 0.000000 / 0.000000 / 10.000000 | 10.000000 / 30.000000 / 40.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 5 / 9 |
| 1000-100000-GLOBAL-CURRENT-C1 / preview | 0.000000 / 10.000000 / 10.000000 | 10.000000 / 40.000000 / 50.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 8 / 21 |
| 1000-100000-GLOBAL-CURRENT-C1 / worker | 10.000000 / 10.000000 / 20.000000 | 20.000000 / 40.000000 / 60.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 7 / 20 |
| 1000-100000-GLOBAL-MIXED-C1 / repository | 0.000000 / 10.000000 / 10.000000 | 10.000000 / 30.000000 / 40.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 4 / 12 |
| 1000-100000-GLOBAL-MIXED-C1 / preview | 0.000000 / 10.000000 / 10.000000 | 10.000000 / 30.000000 / 50.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 7 / 16 |
| 1000-100000-GLOBAL-MIXED-C1 / worker | 0.000000 / 10.000000 / 20.000000 | 20.000000 / 40.000000 / 50.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 7 / 20 |
| 1000-20100000-DENSE-CURRENT-C1 / repository | 0.000000 / 10.000000 / 10.000000 | 10.000000 / 30.000000 / 50.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 9 / 20 |
| 1000-20100000-DENSE-CURRENT-C1 / preview | 0.000000 / 40.000000 / 70.000000 | 10.000000 / 30.000000 / 50.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 8 / 16 |
| 1000-20100000-DENSE-CURRENT-C1 / worker | 10.000000 / 40.000000 / 50.000000 | 20.000000 / 70.000000 / 90.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 5 / 9 |
| 1000-20100000-DENSE-MIXED-C1 / repository | 0.000000 / 10.000000 / 10.000000 | 10.000000 / 20.000000 / 50.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 8 / 20 |
| 1000-20100000-DENSE-MIXED-C1 / preview | 0.000000 / 30.000000 / 60.000000 | 10.000000 / 30.000000 / 40.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 7 / 12 |
| 1000-20100000-DENSE-MIXED-C1 / worker | 10.000000 / 40.000000 / 50.000000 | 20.000000 / 70.000000 / 90.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 4 / 12 |
| 1000-20100000-GLOBAL-CURRENT-C1 / repository | 0.000000 / 10.000000 / 10.000000 | 10.000000 / 30.000000 / 40.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 4 / 9 |
| 1000-20100000-GLOBAL-CURRENT-C1 / preview | 0.000000 / 30.000000 / 70.000000 | 20.000000 / 30.000000 / 50.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 8 / 12 |
| 1000-20100000-GLOBAL-CURRENT-C1 / worker | 10.000000 / 50.000000 / 60.000000 | 20.000000 / 70.000000 / 80.000000 | 0 / 0 / 1 | 0 / 1 / 1 | 0 / 0 / 15 | 0 / 7 / 11 |
| 1000-20100000-GLOBAL-MIXED-C1 / repository | 0.000000 / 10.000000 / 10.000000 | 10.000000 / 30.000000 / 30.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 7 / 12 |
| 1000-20100000-GLOBAL-MIXED-C1 / preview | 0.000000 / 30.000000 / 80.000000 | 20.000000 / 30.000000 / 40.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 8 / 12 |
| 1000-20100000-GLOBAL-MIXED-C1 / worker | 10.000000 / 30.000000 / 50.000000 | 30.000000 / 70.000000 / 100.000000 | 0 / 0 / 0 | 0 / 1 / 1 | 0 / 0 / 0 | 0 / 8 / 13 |
| 100000-10000-DENSE-CURRENT-C1 / repository | 0.000000 / 10.000000 / 10.000000 | 1100.000000 / 1270.000000 / 1330.000000 | 0 / 0 / 0 | 8 / 10 / 12 | 0 / 0 / 0 | 78 / 106 / 127 |
| 100000-10000-DENSE-CURRENT-C1 / preview | 0.000000 / 30.000000 / 60.000000 | 1200.000000 / 1290.000000 / 1320.000000 | 0 / 0 / 0 | 9 / 10 / 11 | 0 / 0 / 0 | 80 / 115 / 133 |
| 100000-10000-DENSE-CURRENT-C1 / worker | 10.000000 / 30.000000 / 40.000000 | 1200.000000 / 1310.000000 / 1350.000000 | 0 / 0 / 0 | 9 / 10 / 11 | 0 / 0 / 0 | 81 / 106 / 114 |
| 100000-10000-DENSE-MIXED-C1 / repository | 0.000000 / 10.000000 / 10.000000 | 1140.000000 / 1270.000000 / 1300.000000 | 0 / 0 / 0 | 8 / 10 / 11 | 0 / 0 / 0 | 77 / 101 / 137 |
| 100000-10000-DENSE-MIXED-C1 / preview | 0.000000 / 30.000000 / 50.000000 | 1180.000000 / 1290.000000 / 1310.000000 | 0 / 0 / 0 | 9 / 10 / 10 | 0 / 0 / 0 | 78 / 103 / 118 |
| 100000-10000-DENSE-MIXED-C1 / worker | 10.000000 / 40.000000 / 60.000000 | 1230.000000 / 1310.000000 / 1370.000000 | 0 / 0 / 1 | 9 / 10 / 11 | 0 / 0 / 32 | 77 / 117 / 136 |
| 100000-10000-GLOBAL-CURRENT-C1 / repository | 0.000000 / 0.000000 / 10.000000 | 1340.000000 / 1450.000000 / 1570.000000 | 0 / 0 / 0 | 9 / 12 / 15 | 0 / 0 / 0 | 82 / 123 / 140 |
| 100000-10000-GLOBAL-CURRENT-C1 / preview | 0.000000 / 10.000000 / 10.000000 | 1410.000000 / 1520.000000 / 1610.000000 | 0 / 0 / 0 | 10 / 13 / 13 | 0 / 0 / 0 | 91 / 120 / 159 |
| 100000-10000-GLOBAL-CURRENT-C1 / worker | 0.000000 / 20.000000 / 20.000000 | 1390.000000 / 1510.000000 / 1570.000000 | 0 / 0 / 0 | 10 / 13 / 15 | 0 / 0 / 0 | 88 / 118 / 145 |
| 100000-10000-GLOBAL-MIXED-C1 / repository | 0.000000 / 0.000000 / 10.000000 | 1330.000000 / 1490.000000 / 1580.000000 | 0 / 0 / 0 | 9 / 12 / 14 | 0 / 0 / 0 | 80 / 119 / 129 |
| 100000-10000-GLOBAL-MIXED-C1 / preview | 0.000000 / 10.000000 / 10.000000 | 1390.000000 / 1500.000000 / 1590.000000 | 0 / 0 / 0 | 11 / 13 / 13 | 0 / 0 / 0 | 88 / 121 / 128 |
| 100000-10000-GLOBAL-MIXED-C1 / worker | 0.000000 / 20.000000 / 20.000000 | 1390.000000 / 1480.000000 / 1600.000000 | 0 / 0 / 0 | 10 / 12 / 13 | 0 / 0 / 0 | 93 / 133 / 151 |
| 100000-100000-DENSE-CURRENT-C1 / repository | 10.000000 / 30.000000 / 40.000000 | 1150.000000 / 1280.000000 / 1360.000000 | 0 / 1 / 1 | 8 / 10 / 10 | 0 / 12 / 16 | 84 / 104 / 122 |
| 100000-100000-DENSE-CURRENT-C1 / preview | 10.000000 / 150.000000 / 170.000000 | 1380.000000 / 1530.000000 / 1610.000000 | 0 / 2 / 2 | 10 / 11 / 13 | 0 / 10 / 25 | 151 / 177 / 182 |
| 100000-100000-DENSE-CURRENT-C1 / worker | 20.000000 / 50.000000 / 70.000000 | 1300.000000 / 1390.000000 / 1530.000000 | 0 / 1 / 1 | 9 / 10 / 10 | 0 / 13 / 21 | 99 / 135 / 153 |
| 100000-100000-DENSE-MIXED-C1 / repository | 10.000000 / 30.000000 / 40.000000 | 1150.000000 / 1300.000000 / 1330.000000 | 0 / 1 / 1 | 8 / 10 / 10 | 0 / 13 / 19 | 86 / 105 / 124 |
| 100000-100000-DENSE-MIXED-C1 / preview | 10.000000 / 150.000000 / 210.000000 | 1390.000000 / 1480.000000 / 1600.000000 | 0 / 2 / 2 | 10 / 11 / 11 | 0 / 9 / 25 | 147 / 173 / 182 |
| 100000-100000-DENSE-MIXED-C1 / worker | 20.000000 / 60.000000 / 100.000000 | 1280.000000 / 1350.000000 / 1380.000000 | 0 / 1 / 1 | 8 / 10 / 10 | 0 / 12 / 19 | 99 / 130 / 141 |
| 100000-100000-GLOBAL-CURRENT-C1 / repository | 0.000000 / 0.000000 / 10.000000 | 1310.000000 / 1460.000000 / 1550.000000 | 0 / 0 / 0 | 9 / 12 / 13 | 0 / 0 / 0 | 81 / 112 / 153 |
| 100000-100000-GLOBAL-CURRENT-C1 / preview | 0.000000 / 10.000000 / 10.000000 | 1390.000000 / 1500.000000 / 1550.000000 | 0 / 0 / 0 | 11 / 13 / 13 | 0 / 0 / 0 | 92 / 121 / 140 |
| 100000-100000-GLOBAL-CURRENT-C1 / worker | 0.000000 / 20.000000 / 20.000000 | 1380.000000 / 1560.000000 / 1600.000000 | 0 / 0 / 0 | 10 / 14 / 15 | 0 / 0 / 0 | 94 / 128 / 139 |
| 100000-100000-GLOBAL-MIXED-C1 / repository | 0.000000 / 0.000000 / 10.000000 | 1310.000000 / 1470.000000 / 1590.000000 | 0 / 0 / 0 | 9 / 12 / 14 | 0 / 0 / 0 | 79 / 112 / 136 |
| 100000-100000-GLOBAL-MIXED-C1 / preview | 0.000000 / 10.000000 / 10.000000 | 1400.000000 / 1530.000000 / 1760.000000 | 0 / 0 / 0 | 11 / 13 / 14 | 0 / 0 / 0 | 84 / 124 / 142 |
| 100000-100000-GLOBAL-MIXED-C1 / worker | 0.000000 / 10.000000 / 20.000000 | 1390.000000 / 1510.000000 / 1600.000000 | 0 / 0 / 0 | 10 / 13 / 14 | 0 / 0 / 0 | 84 / 120 / 141 |
| 100000-20100000-DENSE-CURRENT-C1 / repository | 20.000000 / 30.000000 / 40.000000 | 1150.000000 / 1290.000000 / 1780.000000 | 0 / 1 / 1 | 8 / 10 / 10 | 0 / 16 / 22 | 89 / 116 / 132 |
| 100000-20100000-DENSE-CURRENT-C1 / preview | 10.000000 / 130.000000 / 150.000000 | 1440.000000 / 1550.000000 / 1750.000000 | 0 / 0 / 2 | 10 / 12 / 13 | 0 / 0 / 8 | 173 / 192 / 200 |
| 100000-20100000-DENSE-CURRENT-C1 / worker | 30.000000 / 70.000000 / 100.000000 | 1290.000000 / 1370.000000 / 1410.000000 | 0 / 1 / 1 | 9 / 10 / 10 | 0 / 15 / 19 | 103 / 136 / 159 |
| 100000-20100000-DENSE-MIXED-C1 / repository | 20.000000 / 40.000000 / 50.000000 | 1150.000000 / 1290.000000 / 1800.000000 | 0 / 1 / 1 | 8 / 10 / 10 | 0 / 16 / 21 | 89 / 117 / 127 |
| 100000-20100000-DENSE-MIXED-C1 / preview | 10.000000 / 130.000000 / 160.000000 | 1450.000000 / 1580.000000 / 1710.000000 | 0 / 2 / 2 | 10 / 12 / 13 | 0 / 9 / 25 | 173 / 196 / 216 |
| 100000-20100000-DENSE-MIXED-C1 / worker | 30.000000 / 80.000000 / 120.000000 | 1310.000000 / 1400.000000 / 1490.000000 | 0 / 1 / 1 | 8 / 10 / 10 | 0 / 19 / 22 | 103 / 138 / 159 |
| 100000-20100000-GLOBAL-CURRENT-C1 / repository | 20.000000 / 30.000000 / 40.000000 | 1430.000000 / 1490.000000 / 1650.000000 | 0 / 1 / 1 | 10 / 11 / 12 | 0 / 17 / 21 | 96 / 127 / 156 |
| 100000-20100000-GLOBAL-CURRENT-C1 / preview | 10.000000 / 130.000000 / 180.000000 | 1630.000000 / 1740.000000 / 1870.000000 | 0 / 2 / 2 | 10 / 12 / 13 | 0 / 8 / 23 | 187 / 215 / 238 |
| 100000-20100000-GLOBAL-CURRENT-C1 / worker | 30.000000 / 70.000000 / 140.000000 | 1440.000000 / 1560.000000 / 1680.000000 | 0 / 1 / 1 | 10 / 11 / 12 | 0 / 19 / 23 | 110 / 148 / 154 |
| 100000-20100000-GLOBAL-MIXED-C1 / repository | 20.000000 / 40.000000 / 60.000000 | 1440.000000 / 1550.000000 / 2140.000000 | 0 / 1 / 1 | 10 / 11 / 12 | 0 / 16 / 19 | 95 / 128 / 155 |
| 100000-20100000-GLOBAL-MIXED-C1 / preview | 10.000000 / 130.000000 / 210.000000 | 1660.000000 / 1850.000000 / 2000.000000 | 0 / 2 / 2 | 10 / 13 / 15 | 0 / 7 / 9 | 185 / 210 / 216 |
| 100000-20100000-GLOBAL-MIXED-C1 / worker | 30.000000 / 60.000000 / 90.000000 | 1450.000000 / 1530.000000 / 1590.000000 | 0 / 1 / 1 | 10 / 10 / 11 | 0 / 15 / 24 | 107 / 135 / 153 |
| 10000-20100000-DENSE-CURRENT-C4 / repository | 0.000000 / 10.000000 / 20.000000 | 90.000000 / 100.000000 / 100.000000 | 0 / 0 / 1 | 1 / 1 / 1 | 0 / 0 / 20 | 5 / 9 / 10 |
| 10000-20100000-DENSE-CURRENT-C4 / preview | 20.000000 / 150.000000 / 310.000000 | 430.000000 / 490.000000 / 640.000000 | 0 / 1 / 3 | 3 / 3 / 3 | 0 / 14 / 46 | 43 / 59 / 74 |
| 10000-20100000-DENSE-CURRENT-C4 / worker | 30.000000 / 150.000000 / 320.000000 | 680.000000 / 750.000000 / 910.000000 | 0 / 1 / 3 | 4 / 5 / 5 | 0 / 18 / 46 | 71 / 92 / 108 |
| 10000-20100000-DENSE-MIXED-C4 / repository | 0.000000 / 10.000000 / 10.000000 | 90.000000 / 100.000000 / 100.000000 | 0 / 0 / 1 | 1 / 1 / 1 | 0 / 0 / 12 | 6 / 9 / 11 |
| 10000-20100000-DENSE-MIXED-C4 / preview | 20.000000 / 120.000000 / 240.000000 | 440.000000 / 490.000000 / 710.000000 | 0 / 1 / 2 | 3 / 3 / 4 | 0 / 12 / 47 | 45 / 57 / 75 |
| 10000-20100000-DENSE-MIXED-C4 / worker | 30.000000 / 140.000000 / 230.000000 | 700.000000 / 740.000000 / 800.000000 | 0 / 1 / 2 | 4 / 5 / 5 | 0 / 15 / 47 | 72 / 89 / 106 |
| 10000-20100000-GLOBAL-CURRENT-C4 / repository | 0.000000 / 10.000000 / 10.000000 | 100.000000 / 110.000000 / 120.000000 | 0 / 0 / 1 | 1 / 1 / 1 | 0 / 0 / 4 | 5 / 9 / 10 |
| 10000-20100000-GLOBAL-CURRENT-C4 / preview | 20.000000 / 180.000000 / 240.000000 | 510.000000 / 560.000000 / 760.000000 | 0 / 1 / 3 | 3 / 3 / 4 | 0 / 19 / 21 | 49 / 63 / 84 |
| 10000-20100000-GLOBAL-CURRENT-C4 / worker | 30.000000 / 180.000000 / 270.000000 | 790.000000 / 860.000000 / 1050.000000 | 0 / 1 / 3 | 5 / 6 / 6 | 0 / 19 / 21 | 80 / 104 / 117 |
| 10000-20100000-GLOBAL-MIXED-C4 / repository | 0.000000 / 10.000000 / 20.000000 | 100.000000 / 110.000000 / 120.000000 | 0 / 0 / 1 | 1 / 1 / 1 | 0 / 0 / 13 | 6 / 10 / 11 |
| 10000-20100000-GLOBAL-MIXED-C4 / preview | 20.000000 / 190.000000 / 270.000000 | 520.000000 / 580.000000 / 780.000000 | 0 / 2 / 3 | 3 / 3 / 4 | 0 / 18 / 41 | 53 / 66 / 93 |
| 10000-20100000-GLOBAL-MIXED-C4 / worker | 30.000000 / 200.000000 / 280.000000 | 790.000000 / 850.000000 / 1010.000000 | 0 / 2 / 3 | 5 / 6 / 6 | 0 / 19 / 41 | 78 / 101 / 117 |

### 10.6. PERF-003 실제 동시 라운드

각 조건/버전은 측정 라운드 200개, commit된 worker 호출 800개다. 1,600개 측정 라운드 모두 lockedRows > 0과 committedWorkerCalls=4를 확인했다. 아래 calls/s는 라운드 벽시계 합계에 대한 실제 commit worker 호출 수다. barrier synchronization은 DB lock 대기나 lease 여유를 대신하지 않는다.

| 조건 | main round ms p50/p95/p99 | candidate round ms p50/p95/p99 | main worker calls/s | candidate worker calls/s | main locked rows p50/p95/p99 | candidate locked rows p50/p95/p99 | main barrier ms p50/p95/p99 | candidate barrier ms p50/p95/p99 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 10000-20100000-DENSE-CURRENT-C4 | 959.571481 / 8273.186315 / 8350.505925 | 772.197912 / 829.983552 / 915.144750 | 2.268827 | 5.155206 | 30 / 30 / 30 | 30 / 30 / 30 | 630.350833 / 7938.949994 / 7999.879962 | 236.835631 / 263.482782 / 288.748529 |
| 10000-20100000-DENSE-MIXED-C4 | 961.994877 / 8266.162444 / 8336.913045 | 786.228680 / 844.801680 / 925.908865 | 2.783493 | 5.060768 | 30 / 30 / 30 | 30 / 30 / 30 | 633.406945 / 663.595859 / 7986.987972 | 239.094756 / 264.945826 / 288.107922 |
| 10000-20100000-GLOBAL-CURRENT-C4 | 1112.217526 / 8558.804734 / 8586.222048 | 871.269408 / 942.684021 / 1077.987231 | 2.245567 | 4.543533 | 30 / 30 / 30 | 30 / 30 / 30 | 733.163183 / 8158.174307 / 8193.787408 | 266.208943 / 301.994450 / 339.752621 |
| 10000-20100000-GLOBAL-MIXED-C4 | 1105.388690 / 8447.588034 / 8491.579811 | 879.190149 / 934.721141 / 1030.514542 | 2.265278 | 4.528182 | 30 / 30 / 30 | 30 / 30 / 30 | 722.526973 / 814.173034 / 8102.025908 | 264.109662 / 298.786287 / 321.777023 |

### 10.7. 조건별 메모리와 원자료 SHA-256

heap은 retained call boundary의 관측 최댓값(bytes)이다. cold와 measured 관측을 포함하고 예열 또는 호출 사이의 전체 peak를 보장하지 않는다.

| 시나리오 / 조건 | main heap bytes | candidate heap bytes | main raw SHA-256 | candidate raw SHA-256 |
| --- | --- | --- | --- | --- |
| PERF-001 / 10000-20100000-DENSE-CURRENT-C1 | 146346800 | 224786760 | 718b50a7fce9ee5c19fac097b5752a3635c0eb0eceb73f47875122f4e5fc351f | 9a2fbf8eda720d878256cda5527dc4e2d0b290ddcbdc4a44c799a3f9dd6f6d06 |
| PERF-001 / 10000-20100000-DENSE-MIXED-C1 | 154047104 | 220858408 | 6ad3fc67a6c720b24dcd96c61326596682ab7cffea30c0ab78c1ae14ec3d88fa | b93a3e0c9ac7ecffea5ffb993f14108ca6ef9df70b58f0593f09c8425486eefc |
| PERF-001 / 10000-20100000-GLOBAL-CURRENT-C1 | 151931384 | 232116472 | f03ebd236492b1c2b8468c27126067d1e85d5b9bf98eb180f5d5f699f2e5ef86 | 224c15f339fc65a1c5273b0b23ed50a174b9c2b534c86172929f0018854836fa |
| PERF-001 / 10000-20100000-GLOBAL-MIXED-C1 | 141397568 | 229012840 | 5f4b0a00fef658d8510abb9d74cd6e7fcd3f73b3228cf589f3e3f1e68537f3c3 | ec7d78c08e579e50e9dcad7957eac1bc98f2450ec1a6783dc82c104a5f2ede30 |
| PERF-002 / 1000-10000-DENSE-CURRENT-C1 | 135536080 | 231072448 | d40ecc2cd9f8d6ba5ab1c71bf92bea33cad6b422b257748d5e1c306bdbefec0d | ebffecf0fa5e9480d1274fcc3169f58aa0efa0e558e7e1909e0c4da3d5cb1d27 |
| PERF-002 / 1000-10000-DENSE-MIXED-C1 | 132643520 | 193105800 | c3aba11f9e150eb938ae3652298b0f98b2bf981a84193b956c0222b14a04ff52 | 77c2f2877f23d6c496dd5cbcdc6b9b0c5b7c8a1cfbe05e5fef22f0e5261200f2 |
| PERF-002 / 1000-10000-GLOBAL-CURRENT-C1 | 136092960 | 191265648 | ea3a635de5201b5119007f466bed6982956893b6449fcd95045b38b3baff80ab | a272ae765d93c29a2d0fb0f426416f7a8364201be7d76646ffcb294969cac7b3 |
| PERF-002 / 1000-10000-GLOBAL-MIXED-C1 | 163577856 | 230288912 | 75baa501c29c8f1f02681b36bffcc8e3f575d41be597edc0d60dbb4a7d73ecb2 | 76bdb621c817178212d5a844afcf571b80b9a90d07c0ccc86c0dc6e8c1bef06d |
| PERF-002 / 1000-100000-DENSE-CURRENT-C1 | 137887744 | 184982536 | dfce05038d6cfe8f1afccf1e8e78e80974576af3d44844c1457ca0d20073f395 | bb933c7b60a522f7e5312e80a0875f7f1f22d3e6910361599122e7830e7be0b9 |
| PERF-002 / 1000-100000-DENSE-MIXED-C1 | 135548056 | 248251584 | 85a9a6c2d57d688466394e3e4a7a054abf579350cd6e5e386df78c39037c6d55 | c53a564a1ebbd7b271e3ad9995f6bdc0681631bccae4a9ea79d774b951b2aec2 |
| PERF-002 / 1000-100000-GLOBAL-CURRENT-C1 | 133693440 | 210060528 | bdbc9ed12696a03409911e002a5f9065799b437381992af0198f80374c6d72d3 | 9b48a21cbb8090752f87861a54c38974dde0b90c3c5b2ef1b8643d3894e8d7a5 |
| PERF-002 / 1000-100000-GLOBAL-MIXED-C1 | 137336864 | 213694088 | c43c69cd2e083d40a6997694920544ec0071d17b752e32c300f270bba052b7ea | 2e13c359cc97ab3237d89250b46ffe10c718382e44203269279a7e3981e30b0c |
| PERF-002 / 1000-20100000-DENSE-CURRENT-C1 | 133052648 | 141949040 | 41414e98af1181004792cdf9120d6872f6bfd3045fc7060b1df8ec847e65c0f8 | 9d0046cd427eab6a717083a5d84e03e53605b59b095074ad0c2a2abf9d74fa23 |
| PERF-002 / 1000-20100000-DENSE-MIXED-C1 | 136268704 | 153413384 | c6bcfe2fc49b280f44b5565c936e5fdea7356a3f2385c751ff356e18f24f5266 | f51fb01613daedb338365d4339bae2fa4135eb4dcd0cbc9ceb409dd33e196dea |
| PERF-002 / 1000-20100000-GLOBAL-CURRENT-C1 | 175636480 | 166188136 | 1689980ca7850d3313932112580a8c01037450ae856171bc3e5045b5a234690b | 865c1dce8518638147298da0a006aad6871c399a93516b666b33369d579c8c50 |
| PERF-002 / 1000-20100000-GLOBAL-MIXED-C1 | 141557760 | 147611752 | aaa3878117ff60805526dcfac2407b8279cf589ffcc4b06a37fa94a2cb76c964 | 793d35de600fc3f1428ab46fbbf460cbd50e1f191a4a68630c613351b62070f4 |
| PERF-002 / 100000-10000-DENSE-CURRENT-C1 | 300417024 | 310859896 | 29f7be238a11c4b50497cd64a5a7d82c8e0acffd3dead533c7f670a62893aac1 | fadb2bd51696cd0b937405117b6afe423f365ad204aca659fa371f75c19a8505 |
| PERF-002 / 100000-10000-DENSE-MIXED-C1 | 315621376 | 307448688 | cf742b5c8f4f074f0eae835d02308ef875b8021a4e57d6d3c141c75fefc5e971 | 440443323fc258bd03774832ee439d4fc3b018540ea2fda68b1df864d9617d36 |
| PERF-002 / 100000-10000-GLOBAL-CURRENT-C1 | 276202344 | 300997704 | d44cf6e676b0686d6ef1f989f8a6127bf1dab91f5876a1969d6a7c85402a4cf8 | e66c2169e1d51ebd56219bf5e788390b31cb66aa774a10cdf31a24b2349f1f46 |
| PERF-002 / 100000-10000-GLOBAL-MIXED-C1 | 261095424 | 302570616 | c953c9ecc34074245d6b73865c9ff308a26b5343840e667e9e173d0da992a258 | 6bb839bcbbea516b74dca7b055d25b7d2cb2f9189c50b622472b831b82d09158 |
| PERF-002 / 100000-100000-DENSE-CURRENT-C1 | 272786056 | 321108808 | fa977b62e82594395dfbcb9e07f7563b888d472cdb5b941a7980204f75190b25 | 16905cfd5a9af151d752f1cb44d7d31a09333df04fae28f51311911745d71e52 |
| PERF-002 / 100000-100000-DENSE-MIXED-C1 | 265392208 | 344457216 | 11ad98d35f6e4bbd13924ef7fcfeadfb9567cb30baa49a1dfb9a0ed4065b610c | 6afe0fa7472f4a73a77dc89c8abb0c6455b92b2ba3972ade4f432546f77b1b13 |
| PERF-002 / 100000-100000-GLOBAL-CURRENT-C1 | 272532328 | 299949264 | 80b2975c1571a6ed5201d17cbcb01add4e3e3adafc87f1a5321007a5f484a03c | 9198b6bd2eac6481858e016b5aa6fb27a1e1cabf6d345b8df51196e412bc0c13 |
| PERF-002 / 100000-100000-GLOBAL-MIXED-C1 | 294125568 | 296277576 | cb31e4f5b0099bdabf28e3933e41d7ff76bda6b4722fdd059d6b1507be72696f | c5708f7a10cec3870554438a2df6f41fc47d5c27bb4e4d193fec14755e7c3f82 |
| PERF-002 / 100000-20100000-DENSE-CURRENT-C1 | 262189704 | 345485792 | 92fd3c799fe95c5a5a82dec01e3be6c35df4b6e3fed4575001ea0651a1ed6133 | 87ef53ed443f716c9ddb28a1e03616bd18cdf60a96445680b374bd87e112db2b |
| PERF-002 / 100000-20100000-DENSE-MIXED-C1 | 257301504 | 337641472 | d3b8b0754779294bf0132b4e61a185c46260af27fc553ab03807a1452f161309 | 2396068c9afc53e993ecb2da66830fb4f661f7ff8845a900b5e3e65bc79aac77 |
| PERF-002 / 100000-20100000-GLOBAL-CURRENT-C1 | 271483752 | 349395128 | a4bd052cd0ea073377fd8161e853156e8ca39639498ef16bcae0007fda875ae6 | 267a00594e5d8b4295c76aef27af74a6ffa14797dfc444afc841654beca5f763 |
| PERF-002 / 100000-20100000-GLOBAL-MIXED-C1 | 255852544 | 356159120 | 823673ff86aa6b83083e2a5f7141452ae7d1ca56848a87c80c694d64c399a8bb | 0791e39c16c7f5a2a5661ce57f1acc135aea1a6a405c4ea2fc0ce3bb765e703a |
| PERF-003 / 10000-20100000-DENSE-CURRENT-C4 | 181863616 | 298753696 | 47c98aae986eff4f2f24186e749db52b9d131c9c507b691acf9f910f63d3a8e6 | 1a2cc4a192b17e2f6e724d5c768c5b351d5c76e5868b266c4524af2f6fc4ad31 |
| PERF-003 / 10000-20100000-DENSE-MIXED-C4 | 151860096 | 296747008 | 397486b04ba2a976aa422cb8b2c79594e2e24757f69e4f0f8d52f5d38bd0ae53 | 14eeffc58c59084296aa0685d6e8f458ab8d5cdb601bae193eb614bf71b26022 |
| PERF-003 / 10000-20100000-GLOBAL-CURRENT-C4 | 158048896 | 302854352 | 172bd51d9462566ac38e15c5f825a0d80cc85eb23c14d3eca7a283f3aa30310e | 930dd85c85b2def19150c8f040dbe288c2206af64df8b7abfd1c3df761c6d471 |
| PERF-003 / 10000-20100000-GLOBAL-MIXED-C4 | 150970688 | 294125568 | cda4bfd58f300055acd8576e43c76ac1379897e6c3df2d5ab608d53a4583fb17 | 814ab65da43b1eb1e0801575f2af61c99bdddf4a451a452eb60c2ba009fac78c |

### 10.8. 합성 EXPLAIN 요약

repository/preview의 별도 EXPLAIN ANALYZE 진단이다. target node별 actualRows/actualLoops, shared hit/read 및 temp read/write blocks를 기록한다. node별 buffer를 합산하지 않는다. 진단 query의 반환 행/블록 수가 전체 측정 호출의 rowsRead/dbBufferIo/dbTempIo를 충족한다는 뜻은 아니다.

| 조건 / 버전 / 구간 | planning ms | execution ms | partial GiST | target nodes: relation; node/index; rows x loops; shared hit/read; temp read/write | sorts |
| --- | --- | --- | --- | --- | --- |
| 10000-20100000-DENSE-CURRENT-C1 / main / repository | 2.084 | 1397.86 | false | active_user_presence; Seq Scan/N/A; 1272 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 4840 x 1272; 112553/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1272}] |
| 10000-20100000-DENSE-CURRENT-C1 / main / preview | 2.267 | 11416.518 | false | active_user_presence; Seq Scan/N/A; 10000 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 5000 x 10000; 914095/0; 0/0 | [] |
| 10000-20100000-DENSE-CURRENT-C1 / candidate / repository | 0.663 | 12.725 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [] |
| 10000-20100000-DENSE-CURRENT-C1 / candidate / preview | 1.043 | 18.39 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":1713,"actualRows":10008}] |
| 10000-20100000-DENSE-MIXED-C1 / main / repository | 2.181 | 1419.014 | false | active_user_presence; Seq Scan/N/A; 1272 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 4840 x 1272; 112553/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1272}] |
| 10000-20100000-DENSE-MIXED-C1 / main / preview | 2.377 | 11622.61 | false | active_user_presence; Seq Scan/N/A; 10000 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 5000 x 10000; 914095/0; 0/0 | [] |
| 10000-20100000-DENSE-MIXED-C1 / candidate / repository | 0.705 | 12.699 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [] |
| 10000-20100000-DENSE-MIXED-C1 / candidate / preview | 1.12 | 18.322 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":1713,"actualRows":10008}] |
| 10000-20100000-GLOBAL-CURRENT-C1 / main / repository | 2.239 | 1416.39 | false | active_user_presence; Seq Scan/N/A; 1252 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 4873 x 1252; 111540/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":156,"actualRows":1252}] |
| 10000-20100000-GLOBAL-CURRENT-C1 / main / preview | 2.529 | 11845.495 | false | active_user_presence; Seq Scan/N/A; 10000 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 5000 x 10000; 914095/0; 0/0 | [] |
| 10000-20100000-GLOBAL-CURRENT-C1 / candidate / repository | 0.644 | 12.936 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [] |
| 10000-20100000-GLOBAL-CURRENT-C1 / candidate / preview | 0.986 | 18.026 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":1713,"actualRows":10008}] |
| 10000-20100000-GLOBAL-MIXED-C1 / main / repository | 2.266 | 1400.714 | false | active_user_presence; Seq Scan/N/A; 1252 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 4873 x 1252; 111540/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":156,"actualRows":1252}] |
| 10000-20100000-GLOBAL-MIXED-C1 / main / preview | 2.532 | 11515.663 | false | active_user_presence; Seq Scan/N/A; 10000 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 5000 x 10000; 914095/0; 0/0 | [] |
| 10000-20100000-GLOBAL-MIXED-C1 / candidate / repository | 0.627 | 12.749 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [] |
| 10000-20100000-GLOBAL-MIXED-C1 / candidate / preview | 1 | 17.881 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":1713,"actualRows":10008}] |
| 1000-10000-DENSE-CURRENT-C1 / main / repository | 1.326 | 0.334 | true | active_user_presence; Bitmap Heap Scan/active_user_presence_position_gix; 2 x 1; 14/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 2; 6/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":2}] |
| 1000-10000-DENSE-CURRENT-C1 / main / preview | 1.18 | 0.869 | true | active_user_presence; Bitmap Heap Scan/active_user_presence_position_gix; 9 x 1; 14/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 9; 27/0; 0/0 | [] |
| 1000-10000-DENSE-CURRENT-C1 / candidate / repository | 0.664 | 1.493 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [] |
| 1000-10000-DENSE-CURRENT-C1 / candidate / preview | 1.041 | 1.996 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1008}] |
| 1000-10000-DENSE-MIXED-C1 / main / repository | 1.127 | 0.331 | true | active_user_presence; Bitmap Heap Scan/active_user_presence_position_gix; 2 x 1; 14/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 2; 6/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":2}] |
| 1000-10000-DENSE-MIXED-C1 / main / preview | 1.228 | 0.949 | true | active_user_presence; Bitmap Heap Scan/active_user_presence_position_gix; 9 x 1; 14/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 9; 27/0; 0/0 | [] |
| 1000-10000-DENSE-MIXED-C1 / candidate / repository | 0.62 | 1.453 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [] |
| 1000-10000-DENSE-MIXED-C1 / candidate / preview | 1.009 | 2.089 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1008}] |
| 1000-10000-GLOBAL-CURRENT-C1 / main / repository | 3.416 | 0.124 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 0 x 1; 2/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 0 x 0; 0/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":0}] |
| 1000-10000-GLOBAL-CURRENT-C1 / main / preview | 1.282 | 0.248 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 0 x 1; 2/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 0 x 0; 0/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":8}] |
| 1000-10000-GLOBAL-CURRENT-C1 / candidate / repository | 1.062 | 1.83 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [] |
| 1000-10000-GLOBAL-CURRENT-C1 / candidate / preview | 1.004 | 4.287 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1008}] |
| 1000-10000-GLOBAL-MIXED-C1 / main / repository | 1.383 | 0.102 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 0 x 1; 2/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 0 x 0; 0/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":0}] |
| 1000-10000-GLOBAL-MIXED-C1 / main / preview | 1.129 | 0.189 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 0 x 1; 2/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 0 x 0; 0/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":8}] |
| 1000-10000-GLOBAL-MIXED-C1 / candidate / repository | 0.913 | 1.667 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [] |
| 1000-10000-GLOBAL-MIXED-C1 / candidate / preview | 1.028 | 2.335 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1008}] |
| 1000-100000-DENSE-CURRENT-C1 / main / repository | 1.681 | 15.197 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 95 x 1; 128/0; 0/0 ; user_account; Seq Scan/N/A; 441 x 95; 809/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":33,"actualRows":95}] |
| 1000-100000-DENSE-CURRENT-C1 / main / preview | 1.761 | 130.787 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 767 x 1; 128/0; 0/0 ; user_account; Seq Scan/N/A; 509 x 767; 7478/0; 0/0 | [] |
| 1000-100000-DENSE-CURRENT-C1 / candidate / repository | 0.614 | 1.712 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [] |
| 1000-100000-DENSE-CURRENT-C1 / candidate / preview | 0.903 | 2.175 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1008}] |
| 1000-100000-DENSE-MIXED-C1 / main / repository | 1.671 | 15.329 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 95 x 1; 128/0; 0/0 ; user_account; Seq Scan/N/A; 441 x 95; 809/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":33,"actualRows":95}] |
| 1000-100000-DENSE-MIXED-C1 / main / preview | 1.787 | 130.899 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 767 x 1; 128/0; 0/0 ; user_account; Seq Scan/N/A; 509 x 767; 7478/0; 0/0 | [] |
| 1000-100000-DENSE-MIXED-C1 / candidate / repository | 0.658 | 1.54 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [] |
| 1000-100000-DENSE-MIXED-C1 / candidate / preview | 1.021 | 2.222 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1008}] |
| 1000-100000-GLOBAL-CURRENT-C1 / main / repository | 1.362 | 0.167 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 0 x 1; 2/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 0 x 0; 0/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":0}] |
| 1000-100000-GLOBAL-CURRENT-C1 / main / preview | 1.318 | 0.203 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 0 x 1; 2/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 0 x 0; 0/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":8}] |
| 1000-100000-GLOBAL-CURRENT-C1 / candidate / repository | 0.857 | 1.649 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [] |
| 1000-100000-GLOBAL-CURRENT-C1 / candidate / preview | 0.905 | 2.207 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1008}] |
| 1000-100000-GLOBAL-MIXED-C1 / main / repository | 1.261 | 0.097 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 0 x 1; 2/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 0 x 0; 0/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":0}] |
| 1000-100000-GLOBAL-MIXED-C1 / main / preview | 1.207 | 0.189 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 0 x 1; 2/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 0 x 0; 0/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":8}] |
| 1000-100000-GLOBAL-MIXED-C1 / candidate / repository | 0.912 | 1.703 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [] |
| 1000-100000-GLOBAL-MIXED-C1 / candidate / preview | 0.979 | 2.338 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1008}] |
| 1000-20100000-DENSE-CURRENT-C1 / main / repository | 1.084 | 20.252 | false | active_user_presence; Seq Scan/N/A; 134 x 1; 15/0; 0/0 ; user_account; Seq Scan/N/A; 461 x 134; 1190/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":36,"actualRows":134}] |
| 1000-20100000-DENSE-CURRENT-C1 / main / preview | 1.235 | 169.971 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 15/0; 0/0 ; user_account; Seq Scan/N/A; 500 x 1000; 9595/0; 0/0 | [] |
| 1000-20100000-DENSE-CURRENT-C1 / candidate / repository | 0.644 | 1.627 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [] |
| 1000-20100000-DENSE-CURRENT-C1 / candidate / preview | 1.013 | 2.158 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1008}] |
| 1000-20100000-DENSE-MIXED-C1 / main / repository | 1.08 | 20.492 | false | active_user_presence; Seq Scan/N/A; 134 x 1; 15/0; 0/0 ; user_account; Seq Scan/N/A; 461 x 134; 1190/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":36,"actualRows":134}] |
| 1000-20100000-DENSE-MIXED-C1 / main / preview | 1.319 | 168.364 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 15/0; 0/0 ; user_account; Seq Scan/N/A; 500 x 1000; 9595/0; 0/0 | [] |
| 1000-20100000-DENSE-MIXED-C1 / candidate / repository | 0.664 | 1.781 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [] |
| 1000-20100000-DENSE-MIXED-C1 / candidate / preview | 1.067 | 2.217 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1008}] |
| 1000-20100000-GLOBAL-CURRENT-C1 / main / repository | 1.153 | 24.477 | false | active_user_presence; Seq Scan/N/A; 142 x 1; 15/0; 0/0 ; user_account; Seq Scan/N/A; 513 x 142; 1395/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":37,"actualRows":142}] |
| 1000-20100000-GLOBAL-CURRENT-C1 / main / preview | 1.277 | 176.575 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 15/0; 0/0 ; user_account; Seq Scan/N/A; 500 x 1000; 9595/0; 0/0 | [] |
| 1000-20100000-GLOBAL-CURRENT-C1 / candidate / repository | 0.665 | 1.798 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [] |
| 1000-20100000-GLOBAL-CURRENT-C1 / candidate / preview | 1.036 | 2.275 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1008}] |
| 1000-20100000-GLOBAL-MIXED-C1 / main / repository | 1.18 | 23.509 | false | active_user_presence; Seq Scan/N/A; 142 x 1; 15/0; 0/0 ; user_account; Seq Scan/N/A; 513 x 142; 1395/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":37,"actualRows":142}] |
| 1000-20100000-GLOBAL-MIXED-C1 / main / preview | 1.35 | 177.573 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 15/0; 0/0 ; user_account; Seq Scan/N/A; 500 x 1000; 9595/0; 0/0 | [] |
| 1000-20100000-GLOBAL-MIXED-C1 / candidate / repository | 0.645 | 1.515 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [] |
| 1000-20100000-GLOBAL-MIXED-C1 / candidate / preview | 0.953 | 2.067 | false | active_user_presence; Seq Scan/N/A; 1000 x 1; 21/0; 0/0 ; user_account; Seq Scan/N/A; 1001 x 1; 19/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1008}] |
| 100000-10000-DENSE-CURRENT-C1 / main / repository | 2.031 | 7.909 | true | active_user_presence; Bitmap Heap Scan/active_user_presence_position_gix; 113 x 1; 867/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 113; 339/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":34,"actualRows":113}] |
| 100000-10000-DENSE-CURRENT-C1 / main / preview | 2.13 | 49.808 | true | active_user_presence; Bitmap Heap Scan/active_user_presence_position_gix; 790 x 1; 867/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 790; 2370/0; 0/0 | [] |
| 100000-10000-DENSE-CURRENT-C1 / candidate / repository | 0.615 | 134.384 | false | active_user_presence; Index Scan/active_user_presence_pkey; 100000 x 1; 2316/0; 0/0 ; user_account; Seq Scan/N/A; 100001 x 1; 1819/0; 0/0 | [] |
| 100000-10000-DENSE-CURRENT-C1 / candidate / preview | 1.072 | 349.692 | false | user_account; Seq Scan/N/A; 33334 x 3; 1819/0; 0/0 ; active_user_presence; Seq Scan/N/A; 33333 x 3; 2041/0; 0/0 | [{"method":"external merge","spaceType":"Disk","spaceUsedKb":4120,"actualRows":33336}] |
| 100000-10000-DENSE-MIXED-C1 / main / repository | 2.037 | 7.604 | true | active_user_presence; Bitmap Heap Scan/active_user_presence_position_gix; 113 x 1; 870/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 113; 339/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":34,"actualRows":113}] |
| 100000-10000-DENSE-MIXED-C1 / main / preview | 2.204 | 49.398 | true | active_user_presence; Bitmap Heap Scan/active_user_presence_position_gix; 790 x 1; 870/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 790; 2370/0; 0/0 | [] |
| 100000-10000-DENSE-MIXED-C1 / candidate / repository | 0.65 | 142.166 | false | active_user_presence; Index Scan/active_user_presence_pkey; 100000 x 1; 2316/0; 0/0 ; user_account; Seq Scan/N/A; 100001 x 1; 1819/0; 0/0 | [] |
| 100000-10000-DENSE-MIXED-C1 / candidate / preview | 0.946 | 359.414 | false | user_account; Seq Scan/N/A; 33334 x 3; 1819/0; 0/0 ; active_user_presence; Seq Scan/N/A; 33333 x 3; 2041/0; 0/0 | [{"method":"external merge","spaceType":"Disk","spaceUsedKb":4008,"actualRows":33336}] |
| 100000-10000-GLOBAL-CURRENT-C1 / main / repository | 2.508 | 0.19 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 0 x 1; 8/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 0 x 0; 0/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":0}] |
| 100000-10000-GLOBAL-CURRENT-C1 / main / preview | 2.598 | 0.314 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 0 x 1; 8/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 0 x 0; 0/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":8}] |
| 100000-10000-GLOBAL-CURRENT-C1 / candidate / repository | 0.73 | 132.843 | false | active_user_presence; Index Scan/active_user_presence_pkey; 100000 x 1; 2316/0; 0/0 ; user_account; Seq Scan/N/A; 100001 x 1; 1819/0; 0/0 | [] |
| 100000-10000-GLOBAL-CURRENT-C1 / candidate / preview | 1.156 | 360.916 | false | user_account; Seq Scan/N/A; 33334 x 3; 1819/0; 0/0 ; active_user_presence; Seq Scan/N/A; 33333 x 3; 2041/0; 0/0 | [{"method":"external merge","spaceType":"Disk","spaceUsedKb":4272,"actualRows":33336}] |
| 100000-10000-GLOBAL-MIXED-C1 / main / repository | 2.237 | 0.139 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 0 x 1; 3/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 0 x 0; 0/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":0}] |
| 100000-10000-GLOBAL-MIXED-C1 / main / preview | 2.358 | 0.277 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 0 x 1; 3/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 0 x 0; 0/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":8}] |
| 100000-10000-GLOBAL-MIXED-C1 / candidate / repository | 0.74 | 137.835 | false | active_user_presence; Index Scan/active_user_presence_pkey; 100000 x 1; 2316/0; 0/0 ; user_account; Seq Scan/N/A; 100001 x 1; 1819/0; 0/0 | [] |
| 100000-10000-GLOBAL-MIXED-C1 / candidate / preview | 1.155 | 354.856 | false | user_account; Seq Scan/N/A; 33334 x 3; 1819/0; 0/0 ; active_user_presence; Seq Scan/N/A; 33333 x 3; 2041/0; 0/0 | [{"method":"external merge","spaceType":"Disk","spaceUsedKb":4016,"actualRows":33336}] |
| 100000-100000-DENSE-CURRENT-C1 / main / repository | 6.299 | 1152.167 | true | active_user_presence; Bitmap Heap Scan/active_user_presence_position_gix; 5052 x 2; 2605/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 10103; 30310/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":1235,"actualRows":5052},{"method":"quicksort","spaceType":"Memory","spaceUsedKb":1235,"actualRows":5052}] |
| 100000-100000-DENSE-CURRENT-C1 / main / preview | 6.346 | 5088.038 | true | active_user_presence; Bitmap Heap Scan/active_user_presence_position_gix; 38182 x 2; 2611/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 76363; 229089/0; 0/0 | [] |
| 100000-100000-DENSE-CURRENT-C1 / candidate / repository | 0.758 | 132.524 | false | active_user_presence; Index Scan/active_user_presence_pkey; 100000 x 1; 2316/0; 0/0 ; user_account; Seq Scan/N/A; 100001 x 1; 1819/0; 0/0 | [] |
| 100000-100000-DENSE-CURRENT-C1 / candidate / preview | 1.214 | 355.391 | false | user_account; Seq Scan/N/A; 33334 x 3; 1819/0; 0/0 ; active_user_presence; Seq Scan/N/A; 33333 x 3; 2041/0; 0/0 | [{"method":"external merge","spaceType":"Disk","spaceUsedKb":4112,"actualRows":33336}] |
| 100000-100000-DENSE-MIXED-C1 / main / repository | 6.282 | 1140.042 | true | active_user_presence; Bitmap Heap Scan/active_user_presence_position_gix; 5052 x 2; 2616/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 10103; 30310/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":1219,"actualRows":5052},{"method":"quicksort","spaceType":"Memory","spaceUsedKb":1219,"actualRows":5052}] |
| 100000-100000-DENSE-MIXED-C1 / main / preview | 6.465 | 5040.492 | true | active_user_presence; Bitmap Heap Scan/active_user_presence_position_gix; 38182 x 2; 2622/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 76363; 229089/0; 0/0 | [] |
| 100000-100000-DENSE-MIXED-C1 / candidate / repository | 0.776 | 132.246 | false | active_user_presence; Index Scan/active_user_presence_pkey; 100000 x 1; 2316/0; 0/0 ; user_account; Seq Scan/N/A; 100001 x 1; 1819/0; 0/0 | [] |
| 100000-100000-DENSE-MIXED-C1 / candidate / preview | 1.147 | 352.432 | false | user_account; Seq Scan/N/A; 33334 x 3; 1819/0; 0/0 ; active_user_presence; Seq Scan/N/A; 33333 x 3; 2041/0; 0/0 | [{"method":"external merge","spaceType":"Disk","spaceUsedKb":4176,"actualRows":33336}] |
| 100000-100000-GLOBAL-CURRENT-C1 / main / repository | 2.275 | 0.261 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 0 x 1; 20/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 0 x 0; 0/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":0}] |
| 100000-100000-GLOBAL-CURRENT-C1 / main / preview | 2.451 | 0.697 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 4 x 1; 20/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 4; 12/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":9}] |
| 100000-100000-GLOBAL-CURRENT-C1 / candidate / repository | 0.694 | 142.148 | false | active_user_presence; Index Scan/active_user_presence_pkey; 100000 x 1; 2316/0; 0/0 ; user_account; Seq Scan/N/A; 100001 x 1; 1819/0; 0/0 | [] |
| 100000-100000-GLOBAL-CURRENT-C1 / candidate / preview | 0.991 | 434.233 | false | user_account; Seq Scan/N/A; 33334 x 3; 1819/0; 0/0 ; active_user_presence; Seq Scan/N/A; 33333 x 3; 2041/0; 0/0 | [{"method":"external merge","spaceType":"Disk","spaceUsedKb":4104,"actualRows":33336}] |
| 100000-100000-GLOBAL-MIXED-C1 / main / repository | 2.073 | 0.214 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 0 x 1; 16/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 0 x 0; 0/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":25,"actualRows":0}] |
| 100000-100000-GLOBAL-MIXED-C1 / main / preview | 2.264 | 0.599 | true | active_user_presence; Index Scan/active_user_presence_position_gix; 4 x 1; 16/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 4; 12/0; 0/0 | [] |
| 100000-100000-GLOBAL-MIXED-C1 / candidate / repository | 0.778 | 147.305 | false | active_user_presence; Index Scan/active_user_presence_pkey; 100000 x 1; 2316/0; 0/0 ; user_account; Seq Scan/N/A; 100001 x 1; 1819/0; 0/0 | [] |
| 100000-100000-GLOBAL-MIXED-C1 / candidate / preview | 1.145 | 370.952 | false | user_account; Seq Scan/N/A; 33334 x 3; 1819/0; 0/0 ; active_user_presence; Seq Scan/N/A; 33333 x 3; 2041/0; 0/0 | [{"method":"external merge","spaceType":"Disk","spaceUsedKb":3888,"actualRows":33336}] |
| 100000-20100000-DENSE-CURRENT-C1 / main / repository | 2.312 | 1149.35 | false | active_user_presence; Index Scan/active_user_presence_pkey; 6561 x 2; 2312/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 13122; 39367/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":880,"actualRows":6561}] |
| 100000-20100000-DENSE-CURRENT-C1 / main / preview | 2.228 | 6464.173 | false | active_user_presence; Index Scan/active_user_presence_pkey; 50000 x 2; 2145/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 100000; 300001/0; 0/0 | [] |
| 100000-20100000-DENSE-CURRENT-C1 / candidate / repository | 0.69 | 136.697 | false | active_user_presence; Index Scan/active_user_presence_pkey; 100000 x 1; 2316/0; 0/0 ; user_account; Seq Scan/N/A; 100001 x 1; 1819/0; 0/0 | [] |
| 100000-20100000-DENSE-CURRENT-C1 / candidate / preview | 1.132 | 359.18 | false | user_account; Seq Scan/N/A; 33334 x 3; 1819/0; 0/0 ; active_user_presence; Seq Scan/N/A; 33333 x 3; 2041/0; 0/0 | [{"method":"external merge","spaceType":"Disk","spaceUsedKb":3992,"actualRows":33336}] |
| 100000-20100000-DENSE-MIXED-C1 / main / repository | 2.304 | 1158.879 | false | active_user_presence; Index Scan/active_user_presence_pkey; 6561 x 2; 2306/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 13122; 39367/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":889,"actualRows":6561}] |
| 100000-20100000-DENSE-MIXED-C1 / main / preview | 2.313 | 6518.351 | false | active_user_presence; Index Scan/active_user_presence_pkey; 50000 x 2; 2140/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 100000; 300001/0; 0/0 | [] |
| 100000-20100000-DENSE-MIXED-C1 / candidate / repository | 0.77 | 134.185 | false | active_user_presence; Index Scan/active_user_presence_pkey; 100000 x 1; 2316/0; 0/0 ; user_account; Seq Scan/N/A; 100001 x 1; 1819/0; 0/0 | [] |
| 100000-20100000-DENSE-MIXED-C1 / candidate / preview | 1.232 | 368.634 | false | user_account; Seq Scan/N/A; 33334 x 3; 1819/0; 0/0 ; active_user_presence; Seq Scan/N/A; 33333 x 3; 2041/0; 0/0 | [{"method":"external merge","spaceType":"Disk","spaceUsedKb":4032,"actualRows":33336}] |
| 100000-20100000-GLOBAL-CURRENT-C1 / main / repository | 2.556 | 1264.027 | false | active_user_presence; Index Scan/active_user_presence_pkey; 6277 x 2; 2306/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 12554; 37663/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":824,"actualRows":6277}] |
| 100000-20100000-GLOBAL-CURRENT-C1 / main / preview | 2.652 | 7312.149 | false | active_user_presence; Index Scan/active_user_presence_pkey; 50000 x 2; 2140/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 100000; 300001/0; 0/0 | [] |
| 100000-20100000-GLOBAL-CURRENT-C1 / candidate / repository | 0.788 | 135.712 | false | active_user_presence; Index Scan/active_user_presence_pkey; 100000 x 1; 2316/0; 0/0 ; user_account; Seq Scan/N/A; 100001 x 1; 1819/0; 0/0 | [] |
| 100000-20100000-GLOBAL-CURRENT-C1 / candidate / preview | 1.149 | 356.283 | false | user_account; Seq Scan/N/A; 33334 x 3; 1819/0; 0/0 ; active_user_presence; Seq Scan/N/A; 33333 x 3; 2041/0; 0/0 | [{"method":"external merge","spaceType":"Disk","spaceUsedKb":4112,"actualRows":33336}] |
| 100000-20100000-GLOBAL-MIXED-C1 / main / repository | 2.558 | 1271.521 | false | active_user_presence; Index Scan/active_user_presence_pkey; 6277 x 2; 2288/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 12554; 37663/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":833,"actualRows":6277}] |
| 100000-20100000-GLOBAL-MIXED-C1 / main / preview | 2.63 | 7212.546 | false | active_user_presence; Index Scan/active_user_presence_pkey; 50000 x 2; 2140/0; 0/0 ; user_account; Index Scan/uq_user_account_id_role; 1 x 100000; 300001/0; 0/0 | [] |
| 100000-20100000-GLOBAL-MIXED-C1 / candidate / repository | 0.695 | 132.724 | false | active_user_presence; Index Scan/active_user_presence_pkey; 100000 x 1; 2316/0; 0/0 ; user_account; Seq Scan/N/A; 100001 x 1; 1819/0; 0/0 | [] |
| 100000-20100000-GLOBAL-MIXED-C1 / candidate / preview | 1.157 | 352.739 | false | user_account; Seq Scan/N/A; 33334 x 3; 1819/0; 0/0 ; active_user_presence; Seq Scan/N/A; 33333 x 3; 2041/0; 0/0 | [{"method":"external merge","spaceType":"Disk","spaceUsedKb":4136,"actualRows":33336}] |
| 10000-20100000-DENSE-CURRENT-C4 / main / repository | 2.126 | 1396.164 | false | active_user_presence; Seq Scan/N/A; 1272 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 4840 x 1272; 112553/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1272}] |
| 10000-20100000-DENSE-CURRENT-C4 / main / preview | 2.291 | 11432.941 | false | active_user_presence; Seq Scan/N/A; 10000 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 5000 x 10000; 914095/0; 0/0 | [] |
| 10000-20100000-DENSE-CURRENT-C4 / candidate / repository | 0.812 | 12.76 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":26,"actualRows":30}] |
| 10000-20100000-DENSE-CURRENT-C4 / candidate / preview | 1.149 | 18.871 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":1713,"actualRows":10008},{"method":"quicksort","spaceType":"Memory","spaceUsedKb":26,"actualRows":30}] |
| 10000-20100000-DENSE-MIXED-C4 / main / repository | 2.106 | 1392.818 | false | active_user_presence; Seq Scan/N/A; 1272 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 4840 x 1272; 112553/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":158,"actualRows":1272}] |
| 10000-20100000-DENSE-MIXED-C4 / main / preview | 2.321 | 11362.04 | false | active_user_presence; Seq Scan/N/A; 10000 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 5000 x 10000; 914095/0; 0/0 | [] |
| 10000-20100000-DENSE-MIXED-C4 / candidate / repository | 0.645 | 13.152 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [] |
| 10000-20100000-DENSE-MIXED-C4 / candidate / preview | 0.984 | 18.327 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":1713,"actualRows":10008}] |
| 10000-20100000-GLOBAL-CURRENT-C4 / main / repository | 2.455 | 1511.863 | false | active_user_presence; Seq Scan/N/A; 1252 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 4873 x 1252; 111540/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":156,"actualRows":1252}] |
| 10000-20100000-GLOBAL-CURRENT-C4 / main / preview | 2.571 | 11625.895 | false | active_user_presence; Seq Scan/N/A; 10000 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 5000 x 10000; 914095/0; 0/0 | [] |
| 10000-20100000-GLOBAL-CURRENT-C4 / candidate / repository | 0.739 | 12.855 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [] |
| 10000-20100000-GLOBAL-CURRENT-C4 / candidate / preview | 1.056 | 18.124 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":1713,"actualRows":10008}] |
| 10000-20100000-GLOBAL-MIXED-C4 / main / repository | 2.316 | 1399.195 | false | active_user_presence; Seq Scan/N/A; 1252 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 4873 x 1252; 111540/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":156,"actualRows":1252}] |
| 10000-20100000-GLOBAL-MIXED-C4 / main / preview | 2.574 | 11458.401 | false | active_user_presence; Seq Scan/N/A; 10000 x 1; 143/0; 0/0 ; user_account; Seq Scan/N/A; 5000 x 10000; 914095/0; 0/0 | [] |
| 10000-20100000-GLOBAL-MIXED-C4 / candidate / repository | 0.712 | 12.852 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":26,"actualRows":30}] |
| 10000-20100000-GLOBAL-MIXED-C4 / candidate / preview | 0.991 | 18.313 | false | active_user_presence; Index Scan/active_user_presence_pkey; 10000 x 1; 234/0; 0/0 ; user_account; Seq Scan/N/A; 10001 x 1; 182/0; 0/0 | [{"method":"quicksort","spaceType":"Memory","spaceUsedKb":1713,"actualRows":10008},{"method":"quicksort","spaceType":"Memory","spaceUsedKb":26,"actualRows":30}] |

### 10.9. 기존 성능 회귀와 남은 제한

기존 성능 회귀 5개는 NOT_RUN/BLOCKED다. 사용자가 DB 1코어/4GiB/swap 0 조건을 기존 회귀에도 동일하게 적용한다고 확인했다. 다섯 클래스는 PostgisContainerIntegrationTestSupport를 직접 상속하며 DB 제한은 CoordinatePerformanceRunner.configureDatabaseResources에만 있다. 현재 회귀 실행 경로는 그 메서드를 거치지 않는다. 공통 support의 승인된 변경 범위는 performance-only image/platform이며 자원 제한 경로 추가는 현 소유 계약 밖이다. 제한 없는 실행이나 원격 소스 편집으로 우회하지 않았다.

대상: DirectionMatchingPerformanceIntegrationTest, DirectionMatchingE3PerformanceIntegrationTest, DirectionMatchingIndexPlanPerformanceIntegrationTest, DirectionMatchingPerformanceProbeIntegrationTest, PgStatStatementsPerformanceIntegrationTest. 이 다섯 회귀를 실행했다고 또는 통과했다고 기록하지 않는다.

- executed_checks: 64회 OCI 교대 실행, 고정 자원/arm64 digest/COMMIT/공통 runner 직접 대조, 원자료 64개 SHA-256, phase별 retained 표본 수, p50/p95/p99 및 96개 비율, 32쌍 환경/경계/잠금 프로토콜, CPU/GC 분포, PERF-003 실제 라운드 처리량 독립 재검산.
- passed_checks: 원자료/원격 요약 해시 일치; measured 43,200개와 cold 포함 43,416개; 32쌍 환경/경계/프로토콜 일치; 100k 24회 작업량 완료; PERF-001의 preview/worker 상대 p95 8개 모두 2 이하; 기능 및 중복 검증 assertion 실패 0건.
- failed_checks: 64회 performanceTest가 필수 D6 증거 assertion으로 nonzero 종료했다(원장 62회 exit=1, 초기 2회 별도 BUILD FAILED 로그). PERF-002의 큰 성능 저하는 표에 그대로 기록했다.
- blocked_checks: 필수 외부 지표 8개, 동일 DB 자원 제한을 적용할 수 없는 기존 성능 회귀 5개, 전체 성능 PASS 및 배포 가능 판정.
- changed_files: 이번 성능 증거 보고 변경은 이 추적 보고서 한 개다. 원자료·로컬 감사 산출물은 커밋하지 않는다. 기존 production·테스트·승인 계획·이미지 기본값을 수정하지 않았다.
- assumptions: 원자료 status/workloadCompleted와 실제 runner assertion 경계를 함께 사용했다. 예열 표본은 의도적 미보존이다. 기능 불일치 0 판정은 별도 직렬화 counter가 아니라 성공한 실제 assertion에서 도출했다.
- risks: 필수 DB/transaction/lease/lock 계측 누락; 1k/100k의 큰 상대 지연; 기존 회귀 미실행; OCI와 CI DB patch/arch 차이; 전체 peak RSS 미계측. 기존 §7의 dev key 주입, V35 충돌 및 OSV 잔여 위험도 남는다.
- required_human_decisions: 기존 회귀에 동일 DB 제한을 적용할 수 있도록 성능 전용 지원 코드의 소유 범위/실행 경로 확장에 대한 별도 결정이 필요하다. 현 보고는 그 변경을 승인하지 않는다.

### 10.10. 이번 보고서 변경의 검증

§3의 로컬 기능 검증은 이전 실행 기록이다. 이번 문서 변경의 검사는 아래와 구분한다.

| Command / gate | Result | Scope / cause |
| --- | --- | --- |
| ./harness check | PASS | exit 0; secret/JUnit/convention/workflow/label/Husky 및 hook self-test |
| npm run hooks:validate | PASS | exit 0; Husky validation |
| git diff --check | PASS | exit 0; 문서 공백 검사 |
| ./harness pr-ready --project-tests | FAIL | exit 2; branch is behind origin/main |
| pr-ready의 full project check | NOT_RUN/BLOCKED | 위 선행 동기화 게이트에서 종료되어 실행되지 않음 |

local main은 다른 worktree에 checkout돼 자동 갱신이 거부됐고, 현재 브랜치는 origin/main보다 뒤처져 있다. 자동 rebase/merge 또는 승인 우회를 하지 않았다. 측정 코드 commit c131aa7268219106a78af826ce23567e0e098a52와 OCI 증거는 그대로 보존했다. 이 보고서의 검증 통과를 PR readiness나 전체 성능 PASS로 표현하지 않는다.

목적 커밋 범위는 이 보고서 한 개다. 원자료·감사 사본·다른 파일은 포함하지 않는다. staged hook 및 커밋 생성 결과는 지정 인계 보고와 최종 전달에서 실제 hash로 기록한다. Co-Authored-By, push, dev/운영 배포는 하지 않는다.
