# Test Plan: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION

> Created at: `2026-11-24T17:04:41.836Z`
> GitHub Issue: `#388`
> Status: APPROVED — 2026-11-25, @Byuntil (D1~D5 추천안, D6 수정안 승인)
> Task: `TASK.md`, `feat/gh-388-coordinate-encryption`
> Base: 현재 worktree의 main 기반. 실행 전 #381 병합 여부와 migration 직전 버전을 다시 확인한다.
> 단위 14개 / 통합 24개 / 별도 성능 3개 시나리오.
> 표의 축약 ID 앞에는 `TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-`를 붙인다.
> ADR-0009와 이 테스트 계획의 승인을 확인했다. 구현·테스트 실행은 아직 하지 않았으며 Docker 설치는 측정 단계에서 별도 승인받는다.

## 1. Objective

정확 좌표를 DB에 암호문으로 저장하면서 기존 PostGIS 기반 매칭 후보 집합·순서와
방향별 개수를 보존한다. 키·AAD·이행 실패가 좌표 노출, 잘못된 수신자 선정,
부분 commit, 과거 글의 의도하지 않은 재매칭으로 이어지지 않음을 검증한다.
거리·방위 수치 오차와 후보 포함 판정의 정확 일치를 구분하고, 성능은 같은
환경의 평문 기준선과 비교해 별도 증거로 남긴다.

## 2. Scope

### Included

- 두 좌표 컬럼의 AES-256-GCM 저장·복원, 행·용도 결합 AAD, current/previous 키.
- GeographicLib Java 기반 회전타원체 계산과 기존 PostGIS SQL의 차등 비교.
- 비공간 후보 조건, 정렬, preview의 모든 segment와 빈 방향, worker 원자성·경합.
- 승인된 ADR D7의 단일 Flyway migration: 평문 폐기, presence 재수집,
  audience NULL 복원, 미완료 글 MATCH_FAILED, 멱등 재생, 제약·복구.
- fixture 키와 비테스트 설정의 주입 경계, 응답·로그·예외·SQL 오류의 민감값 비노출.
- 승인된 ADR D8의 측정 규모·예산, 기존 평문/GiST fixture의 의미 보존 갱신.

### Excluded

- 이 계획 단계의 production·migration·테스트 코드 작성과 테스트 실행.
- 실제 dev/운영 DB 조회·폐기·백업 복원, SSM secret 생성·조회, Terraform·배포.
- C6 보존 기간·주기 파기와 I2 매체 암호화 구현, 기존 백업의 실제 삭제.
- post_recipient 파생 값 암호화·저정밀화, 셀 인덱스 B, DB 복호화 C/D 도입.
- 암호학적 안전성의 수학적 증명, 실제 사용자 좌표·키·계정 자료 수집.
- 기존 테스트의 무관한 리팩터링, 동의 정책 변경, CI workflow 변경, commit/push.

## 3. Source requirements

| Source | Requirement / acceptance criterion | Scenario |
| --- | --- | --- |
| Issue #388 / TASK (CONFIRMED) | 저장 평문 부재·키 없는 복원 거부 | UNIT-001~008, INT-009,017,018,024 |
| ADR-0009 D1~D3 (accepted), 2026-11-25 D3 보정 승인 | WGS84 회전타원체, 거리 0.001m·각도 0.0000001도 이하 유지; 후보 집합·순서·방향별 개수 정확 일치와 아래 승인된 경계·동률·근거리 비교 규칙 적용 | UNIT-009~012, INT-011~016 |
| ADR D4~D6 (accepted) | 좌표 전용 키, 행 결합 AAD, current-write·previous-read, 누락·변조 fail-closed | UNIT-001~008,014, INT-008~010,018,024 |
| ADR D7 수정 승인 (CONFIRMED) | 기존 평문 폐기와 단일 migration, 재암호화 없음 | UNIT-013, INT-001~007 |
| ADR D8 및 테스트 계획 D6 수정 승인 | 1,000/10,000/100,000명과 반복 횟수 유지; OCI 동일 환경 상대 비교로 판정, 절대 지연은 참고값 | PERF-001~003 |
| ADR D9·D10 (accepted) | 파생 값 보호는 별도 Issue, B 도입하지 않음 | 범위 제한; INT-002,023에서 기존 파생 값·기능 보존 |
| ADR-0001·0002 (accepted) | Flyway 이력 불변·같은 DataSource transaction·repository 경계 | INT-001~004,019~022 |
| 기존 worker·서비스 관찰 | 수신 공정성, 용량 잠금·fencing, submit 멱등성, 최신 관측만 UPSERT | INT-006~008,019~022 |
| B1 #379 / #381 의존 관계 | 게이트 우회 없이 합성 fixture로 진입, 동의 기능 병합 시 정상 동의 fixture 명시 | INT-005,017,023 |

ADR 승인 근거는 Issue #388의 @Byuntil 댓글(2026-11-25, 댓글 식별자 7138508452)이다.
테스트 계획은 별도 댓글 7139174419(2026-11-25)에서 D1~D5 추천안과 D6 수정안으로 승인됐다.
ADR의 D1~D10과 아래 테스트 계획 D1~D6은 서로 다른 결정 목록이다.

### ADR D3 차등 테스트 보정 승인

@Byuntil이 2026-11-25 댓글 7150447072에서 다음 보정 1~6을 승인했다.
이 보정의 D3는 ADR-0009의 정확도 계약이며, 아래 테스트 계획 결정 표의 보안 증거
D3와 구분한다. 거리 `0.001m`, 각도 `0.0000001도`의 허용 오차와 후보 집합·
순서·방향별 개수 정확 일치 원칙은 유지하며 대안 D로 전환하지 않는다.

1. 경계 양옆의 fixture는 거리 `±1m`, 각도 `±0.0001도` 간격으로 두고
   후보 집합·순서·방향별 개수의 정확 일치를 검증한다.
2. 경계선 바로 위의 점은 양쪽 후보 결과의 정확 일치 대신 앱 수치가 PostGIS
   수치와 허용 오차 안인지, 앱이 자기 수치에 경계 비교 규칙을 일관되게
   적용하는지 검증한다. 섹터는 시작 포함·끝 제외의 반열린 구간 규칙을 따른다.
3. 거리 차이가 허용 오차 `0.001m` 이하인 후보는 동률로 보고 사용자 ID
   순서로 비교한다. 기존 수신 이력 우선순위는 유지한다.
4. 거리 `1m` 미만 점은 방위·역방위 수치 비교에서 제외하되 후보 포함 여부는
   비교한다. 좌표·거리 수치 검증 전체를 생략하는 예외가 아니다.
5. 최대 거리 fixture는 측지 최대 거리 안의 `20,000,000m`를 사용한다.
   설정 상한 `20,100,000m`는 유지하며 이 상한으로 점을 투영하지 않는다.
6. 모든 차등 fixture의 최대 절대 거리 차이와 최대 절대 각도 차이를 테스트
   보고서에 기록한다. 각도는 순환 각 차이를 사용하며 방위·역방위를 모두 관찰한다.
   1m 미만 제외와 NULL 방위는 비교 불가/제외로 표시하고 오차 0으로 채우지 않는다.

### 관찰한 코드와 기존 테스트

- `ActiveUserPresenceSql.FIND_CANDIDATES_SQL`과
  `FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL`을 독립 PostGIS oracle의 기준으로 삼는다.
  현재 production은 아직 평문 SQL이므로 암호문 동작을 구현 사실로 취급하지 않는다.
- worker는 모든 후보 정렬 후 최대 수신자의 3배만 검사한다. 후보 조회를 먼저
  30명으로 자르는 회귀는 일반 차등 fixture에서도 검출해야 한다.
- `DirectionPostService.replayIfExists`는 audience의 거리 범위를 사용한다.
  `PostAudience`는 현재 원점·셀 모두 NULL을 거부하므로 D7 복원 계약 변경이 필요하다.
- 원점 누락은 현재 Outbox DEAD를 만들 뿐 글을 MATCH_FAILED로 바꾸지 않는다.
  migration의 명시적 MATCH_FAILED 처리와 worker의 비매칭 상태 건너뛰기를 함께 검증한다.
- `DirectionMatchingContractIntegrationTest`는 submit rollback·멱등성,
  `DirectionPreviewIntegrationTest`는 segment 집계·경계,
  `DirectionMatchingWorkerConcurrencyIntegrationTest`는 claim·용량·stale lease를 다룬다.
  기존 계약을 새 파일에 복제하지 않고 암호문 fixture와 필요한 조건을 확장한다.
- `FlywayMigrationIntegrationTest`는 현재 V34와 GiST inventory를 가정한다.
  추가 migration 때문에 stale해지는 기대만 변경하고 원래 틀린 테스트를 억지로 통과시키지 않는다.
- `DirectionMatchingPerformanceIntegrationTest`는 평문 SQL과 GiST/Seq Scan 금지를
  가정한다. A에서는 이를 그대로 요구할 수 없으므로 실행 계획 기록과 승인된
  결과·지연 기준으로 대체할 변경을 계획 D5에 올린다.
- `build.gradle`은 `performance` 태그를 일반 `integrationTest`에서 제외하고
  `performanceTest`에서만 포함한다. 새 CI 구성을 만들지 않고 이 분리를 활용한다.
- 공유 PostGIS 기반 클래스는 JVM별 컨테이너·DB와 Spring context를 재사용하며
  클래스 시작 시 DB를 초기화한다. migration 실험은 별도 테스트 DB/스키마에 격리한다.
- 기존 concurrency 파일의 `awaitLease`에는 `Thread.sleep(10)` polling이 있다.
  기존 테스트를 실행해 실패를 확인한 것은 아니다. 새·변경 시나리오는 이 방식을
  복제하지 않고 아래 §7의 선등록 신호로 바꾼다.

### 테스트 계획의 사람 결정 항목

| ID | 항목·선택지 | 추천 | 이유 |
| --- | --- | --- | --- |
| D1 | 차등 oracle: 변경 전 SQL을 고정한 테스트 전용 기준선 / 새 앱 계산으로 기대값 생성 | 변경 전 SQL 두 개를 테스트 전용 oracle로 고정 | 같은 구현의 오류를 양쪽에서 재현하는 자기 비교를 피한다. 기준 commit과 SQL 해시를 실행 보고에 기록한다. |
| D2 | 성능: 일반 CI마다 대규모 측정 / 기존 performanceTest에서 별도 측정 | 일반 CI는 UNIT·INT, PERF는 별도 필수 승인 증거 | 머신 잡음을 일반 기능 테스트에 섞지 않되 성능 미실행을 #388 PASS로 숨기지 않는다. |
| D3 | 보안 증거: 문자열 검색만 / 실제 암호 연산·키 없는 실패와 DB·로그 관찰 결합 | 실제 AES/JCE 대조·wrong/missing key·AAD·SQL 결과·로그 검증 | 문자열이 없다는 사실만으로 암호화나 key 독립성을 입증하지 않는다. |
| D4 | migration·복구: SQL mock / 실제 직전 버전 DB·별도 복원 DB | 실제 PostgreSQL/PostGIS에서 빈 DB·업그레이드·rollback·백업 복원 | 폐기 범위·제약·transaction·복구 가능성을 실행 결과로 확인한다. 운영 자료는 사용하지 않는다. |
| D5 | 기존 회귀: 기존 GiST 기대 유지 / A에 맞춘 계약·fixture 갱신 | 아래 파일 소유 범위에서 stale 기대·fixture만 갱신 | GiST 제거라는 승인된 변경을 반영하되 후보·공정성·보안 검증은 약화하지 않는다. |
| D6 | 측정 환경·기준선: 임의 머신 / 개인 OCI의 고정 자원·동일 이미지로 main과 #388 교대 측정 | 수정 승인: aarch64(Neoverse-N1) 2코어·11GB, PostGIS 1코어·JVM 1코어, 같은 arm64 이미지·digest와 메모리 한도 고정 | 상대 지연 2배 이내와 불일치 0건으로 판정한다. 절대 지연은 운영 CPU와 달라 참고값이며 Docker 설치는 별도 승인 대상이다. |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 평문 저장·키 공유·오류 노출 | 위치 복원·키 유출 | 중 | P0 | UNIT-001~008,014, INT-009,017,018,024 |
| 다른 행·용도 암호문 교체 | 타인 위치로 매칭 | 중 | P0 | UNIT-004,005, INT-009 |
| 잘못된 구면·역방위·반올림 | 후보·순서·방향 불일치 | 높음 | P0 | UNIT-009~012, INT-012~016 |
| 숫자 오차로 경계 정책 변경 | 방향·거리 경계에서 누락/중복 | 높음 | P0 | INT-014~016 |
| 키 회전 후 과거 행 읽기 실패 | 전체 매칭 장애 | 중 | P0 | UNIT-002,003, INT-008,010,018 |
| 폐기 migration 부분 반영 | 배포 실패·평문 잔존·글 재매칭 | 중 | P0 | INT-001~007 |
| 암호화 실패 후 부분 commit | post만 존재하거나 슬롯만 소비 | 중 | P0 | INT-008,019~022 |
| 대량 복호화·전송·GC | preview 지연·lease 만료 | 높음 | P0 | PERF-001~003 |
| 구 SQL·공유 DB fixture 잔존 | 회귀 전체 실패·테스트 간 오염 | 높음 | P0 | INT-023, §8·9 |
| 과거 백업·파생 값 노출 | 좌표 암호화만으로 해결되지 않음 | 중 | P1 | 범위 밖 잔여 위험을 보고, INT-004는 합성 복구만 검증 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| UNIT-001 | 합성 좌표 쌍·행 식별자·좌표 전용 키 | 실제 protector로 암호화하고 별도 JCE 경계로 복호화 | version·key ID·nonce·128-bit tag와 AAD 계약에 맞고 원래 좌표 수치가 보존된다; 자기 round-trip만으로 판정하지 않는다 | P0 | Unit |
| UNIT-002 | 이전 키로 만든 envelope와 새 current/previous ring | 새 write 및 두 세대 read | 새 write의 key ID는 current, 두 read 모두 원좌표 복원 | P0 | Unit |
| UNIT-003 | 과거 envelope, 이전 키가 제거되거나 같은 ID에 다른 키인 ring | 복호화 | 실패하며 좌표·키 값이 예외에 없고 평문 fallback이 없다 | P0 | Unit |
| UNIT-004 | 사용자 A 또는 글 A에 결합한 정상 envelope | 다른 행 ID로 복호화 | 인증 실패, 다른 행의 좌표로 사용되지 않는다 | P0 | Unit |
| UNIT-005 | 행 숫자는 같지만 presence·audience 용도가 다른 envelope | 다른 table/column 용도로 복호화 | 인증 실패, 행 ID만 결합하는 회귀를 검출한다 | P0 | Unit |
| UNIT-006 | 정상 envelope의 header·nonce·ciphertext·tag 각 변조 입력 | 복호화 | 각 입력이 제한된 오류로 실패; 한 바이트 무시·tag 미검증 회귀 검출 | P0 | Unit |
| UNIT-007 | 잘린 envelope, 미지원 version, 모르는 key ID | 해석·복호화 | 원문 없는 오류; 배열 범위 오류나 임의 기본키 read로 성공하지 않는다 | P0 | Unit |
| UNIT-008 | current 누락·잘못된 길이/Base64·previous 편측 설정·동일 ID | 설정 구성 | 잘못된 조합 거부, 올바른 두 슬롯 허용, 설정 문자열에 material 미노출 | P0 | Unit |
| UNIT-009 | 양·음·0·경계 위경도와 기존 double 변환 정밀도를 구별하는 값 | 직렬화·복원 | 승인된 좌표 정밀도 보존; 임의 소수 자릿수 반올림 없음 | P0 | Unit |
| UNIT-010 | 동일 좌표 두 점 | 순수 계산 경계 호출 | 방위 없음으로 판정해 후보 제외; 임의 0도 후보를 만들지 않는다 | P0 | Unit |
| UNIT-011 | 독립 기준값이 있는 장거리 비대칭 좌표쌍 | 순방위·역방위 계산 | 수신자 원점 역계산과 일치하며 단순 +180 또는 azi2 오사용을 검출 | P0 | Unit |
| UNIT-012 | 수신 이력 동률·NULL 시각·미세한 거리 차이·ID 동률 정렬 자료 | 후보 정렬·저장 거리 변환 | 기존 SQL 공정성 순서와 정렬 전 무반올림, 저장 시 HALF_UP 정수 계약 유지 | P0 | Unit |
| UNIT-013 | D7 이후 원점·셀 모두 NULL인 과거 audience 메타데이터 | 도메인 복원 | 메타데이터 조회 가능; 신규 발송·worker의 정확 원점 요구를 무효화하지 않는다 | P0 | Unit |
| UNIT-014 | 성공·잘못된 키·변조 결과와 설정/좌표 값 객체 | 문자열 표현·예외 변환 | 합성 좌표·키 sentinel 비노출; 오류 종류는 구별 가능하되 문구 전체를 고정하지 않는다 | P0 | Unit |

숫자만 다른 경계값은 parameterized 입력으로 묶는다. 14는 계약 행 수이며
JUnit method 수나 parameterized invocation 수를 뜻하지 않는다.
기존 push 테스트는 참고만 하며 좌표 키·AAD를 push mock으로 대체하지 않는다.

## 6. Integration scenarios

| Scenario ID | Components | Setup (Given) | Action (When) | Expected result (Then) | Cleanup |
| --- | --- | --- | --- | --- | --- |
| INT-001 | Flyway·PostGIS | 전용 빈 DB | 최신까지 migrate, validate, migrate 재호출 | 새 migration 성공 이력 1개, 두 암호문 컬럼·평문 컬럼 부재·GiST 제거, 기존 checksum 불변, 재호출 데이터 변화 없음 | 전용 DB 종료 |
| INT-002 | 직전 schema·신규 migration | 평문 presence(셀 유/무), 셀 전용 presence; 원점 유/무 audience와 글 상태별 fixture | 직전 버전에서 신규 migration 한 번 적용 | 평문 presence 행만 삭제; 셀 전용 유지; 모든 과거 원점 암호문 NULL; 원점을 잃는 미완료 글만 MATCH_FAILED; ACTIVE/종결 글·수신자·슬롯·Outbox 이력·메타데이터 불변 | 전용 DB 종료 |
| INT-003 | Flyway·실제 transaction | 직전 schema에 테스트 전용 실패 trigger를 설치해 D7 처리 중 실패 | migrate 실행 후 별도 connection 조회 | 삭제·상태 변경·DDL 모두 rollback, 성공 이력 없음; 실패 주입 해제 후 한 번 성공하고 중복 폐기 없음 | trigger·전용 DB 정리 |
| INT-004 | schema 제약·합성 백업 | 직전 버전 합성 DB의 테스트 전용 백업과 최신 DB | 제약 위반 DML 및 백업을 별도 DB에 복원 | presence 암호문/셀 둘 다 NULL 거부; audience 둘 다 NULL 허용; FK·거리·방향 제약 유지; 복원 DB만 옛 좌표 보유, 앱 rollback만으로 폐기 좌표가 복원되지 않음 | 별도 복원 DB·합성 백업 폐기 |
| INT-005 | 실제 위치 API·repository | D7로 presence가 제거된 사용자, B1 활성·필요한 동의 fixture | 다음 유효한 위치 갱신 | 같은 사용자 새 암호문 presence 생성, 만료·accuracy·수신 허용 계약 유지; 위치 갱신 전 preview/신규 발송은 기존 위치 부재 오류 | 소유 fixture 정리 |
| INT-006 | migration 후 worker·moderation | 원점을 잃어 MATCH_FAILED인 글, 기존 due/reclaim 가능한 이벤트 | worker 및 뒤늦은 판정 처리 | 수신자·슬롯·후속 알림 0건 증가, 글 상태 유지, claim된 이벤트 정상 종결; 만료 시각 전/후 모두 재매칭 없음 | 이벤트·fixture 정리 |
| INT-007 | 서비스 멱등 재생·JDBC | NULL 원점/셀 audience와 실패 글, fingerprint 유/무 | 같은 요청 재전송 및 다른 의도 요청 | 같은 요청은 기존 ID/실패 상태 재생, legacy fingerprint는 메타데이터로 복원; 다른 의도 거부, 신규 post/outbox 없음 | 소유 fixture 정리 |
| INT-008 | worker·암호화·DB | audience 또는 후보의 키 없음/변조, 정상 후보도 함께 있음 | 이벤트 처리 | 조용한 후보 누락·부분 수신자 확정 없음; 해당 transaction rollback, 현재 영구 실패 계약대로 이벤트 DEAD, 민감값 비노출 | 손상 fixture 정리 |
| INT-009 | 두 JDBC adapter·실제 암호문 | 서로 다른 사용자·글에 정상 암호문 저장 | DB에서 행 간·용도 간 bytea 교체 후 실제 read/매칭 | AAD 인증 거부, 타인 원점·후보로 반환되지 않음; 정상 대조 행은 복원됨 | 교체 fixture 정리 |
| INT-010 | 키 설정 context·저장소 | previous/current 혼합 저장 행 | reader를 두 키 context로 시작해 read와 새 write | 기존 행 전부 읽고 새 행은 current; 이전 키 없는 context에서는 명확한 실패, plaintext fallback 없음 | context 종료 |
| INT-011 | 비공간 SQL·앱 매칭 | 활성/비활성·수신 off·작성자·만료 경계·미래 관측·양방향 차단·해제·셀 전용·다른 지역 후보 | 후보 조회 및 집계 | 기존 SQL과 정확한 ID 집합; location_at=at 포함, expires_at=at 제외, GLOBAL에서 표시 지역으로 축소하지 않음 | 소유 fixture 정리 |
| INT-012 | PostGIS oracle·실제 adapter | 같은 좌표와 수신 이력 snapshot, 30명 초과 후보 및 거리·ID 동률 | 양쪽 후보 조회 | ID 순서까지 정확 일치, 거리·양방위는 승인 오차 이내; 선행 LIMIT·무작위 순서·선반올림 검출 | oracle·fixture 정리 |
| INT-013 | previewAll·PostGIS 집계 | 빈 방향 포함 활성 scheme·전체 후보 | 모든 방향 집계 | segment key·sort order·개수 정확 일치, 0명 방향 존재, 정렬/집계 경계가 worker와 다르게 누락되지 않음 | oracle·fixture 정리 |
| INT-014 | 거리 계산·기존 SQL 상한 | min/max 경계의 양옆 ±1m와 경계선 점, 0m·10km·100km·20,000,000m 최대 거리 fixture; 설정 상한은 20,100,000m 유지 | 후보와 집계를 §3 보정 1~6으로 비교 | 경계 양옆은 집합·순서·개수 정확 일치; 경계선 점은 허용 오차와 앱 자체 수치의 경계 판정 일관성 확인; 거리 차 ≤0.001m 동률은 사용자 ID 순 비교; 1m 미만은 각도 비교만 제외; 모든 차등 fixture 최대 거리·각도 차 보고, 포함 범위 epsilon 확대 없음 | oracle·fixture 정리 |
| INT-015 | 섹터·PostGIS 방위 | 각 섹터 시작/끝의 양옆 ±0.0001도와 경계선 점, 0/360 wrap, 인접 방향 | 후보 조회·previewAll을 §3 보정 1~6으로 비교 | 경계 양옆은 후보 집합·순서·방향별 개수 정확 일치; 경계선 점은 수치 허용 오차와 앱 자체 수치의 시작 포함·끝 제외 일관성 확인; 1m 미만 각도 비교는 제외하되 포함 여부 비교; 모든 차등 fixture 최대 거리·각도 차 보고 | oracle·fixture 정리 |
| INT-016 | 실제 PostGIS·GeographicLib | 동일점·양 극지·날짜변경선·대척/근대척점·전세계 고정 seed | 수치와 후보·집계 차등 비교 | 동일점 NULL 방위 제외; 유한 값과 순환 각 오차 기준 충족; 다해성 방위 불일치도 실패로 기록하고 예외 허용하지 않음 | oracle·fixture 정리 |
| INT-017 | MVC·service·DB | 합성 fixture 키와 고유 합성 좌표, 정상 사용자 | PUT→preview→submit→worker의 실제 경로 | 성공 응답 계약 유지, 두 컬럼 bytea 저장과 정확 좌표 외부 비노출; raw row만 읽는 연결로 geography 원문을 조회할 수 없고 다른 키로도 복원 불가 | 소유 fixture 정리 |
| INT-018 | 실제 profile 설정·context | local/test/integration fixture 및 dev/운영의 명시적 합성 secret 설정 | 누락·정상·잘못된 설정별 context 시작 | fixture는 지정 프로필에서만; dev/운영 key 누락/오류 fail-fast; push와 별도 키로 쓰고 복원; 실제 SSM 호출 없음 | context 종료 |
| INT-019 | Spring proxy·같은 DB transaction | 새 발송/갱신, 암호화 또는 audience/outbox 저장 실패를 분리 주입 | 실제 command 호출 후 별도 connection 조회 | 발송의 post/audience/outbox 부분 commit 없음; 갱신 실패 시 이전 presence 전체 유지 | 주입 해제·fixture 정리 |
| INT-020 | worker claim·lease·fencing | 동일 이벤트와 독립 connection, 사전 등록 lease 신호 | 중복 claim 또는 논리 시각을 진행시킨 reclaim | 논리 수신자/알림 1회, stale worker 쓰기 rollback, 새 generation만 commit; 고정 sleep 금지 | 신호·executor·fixture 정리 |
| INT-021 | 수신 용량·두 worker | 남은 슬롯 1개, 암호문 후보를 공유하는 두 글 | barrier로 같은 수신자 예약 경쟁 | 최종 수신자/슬롯 수 정확 일치, 상한 초과·중복 없음; 실제 잠금 사용 | executor·fixture 정리 |
| INT-022 | presence UPSERT·후보 snapshot | 서로 다른 관측 시각의 암호문 두 갱신, 조회 경계 신호 | 갱신 순서를 양방향으로 강제하고 후보 읽기 | 오래되거나 같은 관측값이 최신 암호문을 덮지 않음; 읽은 행의 좌표·상태가 단일 statement snapshot에 일관됨 | executor·fixture 정리 |
| INT-023 | 기존 전체 회귀·fixture | 아래 소유 파일의 평문 SQL fixture를 암호문 쓰기로 갱신 | 기존 단위·통합과 별도 기존 성능 suite 실행 | 기존 발송·preview·차단·B1·알림 계약 유지; stale GiST 기대만 승인된 새 계약으로 대체; #381 병합 시 동의 우회 없음 | 기존 정리 절차 |
| INT-024 | 로그·MVC 오류·JDBC bind 오류 | 고유 합성 좌표·키 sentinel, 정상/변조/설정 실패/SQL 예외 | 실제 입출력·실패 경로와 로그 capture | 응답·예외 cause chain·captured app/JDBC 로그·Outbox payload에 좌표·key material 없음; 단순 오류 문구 일치 테스트 금지 | appender·fixture·context 정리 |

모든 INT는 P0다. 소유자는 §9에 지정한다. 24는 시나리오 행 수이며 복수 입력은
하나의 assertion 목적별 parameterized test로 나누고 실패 원인을 분리한다.
INT-004의 합성 백업 복원은 실제 운영 복구 승인이나 과거 백업 파기 완료를 뜻하지 않는다.
INT-017/024는 “암호학적으로 해독 불가능함”의 증명이 아니라 승인된 보안 경계의 실행 증거다.

## 7. Cross-cutting scenarios

### Database and transactions

- D7은 새 migration 하나로 끝나야 한다. 직전 버전은 실행 시 확정하고 과거
  migration checksum을 비교한다. V35를 추정해 쓰거나 기존 V1을 바꾸지 않는다.
- INT-002는 원점이 원래 없던 cell-only audience와 이미 종결된 글을 대조군으로
  두어 폐기 대상 밖 상태 변경을 검출한다. NULL 원점은 이행된 snapshot의 부재
  의미이며 모르는 키/변조를 NULL로 치환해서는 안 된다.
- 실제 service proxy를 호출하고 commit/rollback 이후 별도 connection으로
  상태를 확인한다. 테스트 전체 rollback 안에서 부분 commit을 감추지 않는다.
- 실패 주입은 테스트 소유 DB trigger나 실제 adapter를 위임하는 테스트 경계에서
  수행한다. repository 전체를 mock해 transaction을 검증하지 않는다.

### Concurrency and idempotency

- worker claim·수신 용량·fencing·최신 관측 계약을 유지한다. 매칭 시각은 주입
  Clock/command.at으로 고정하고 lease 만료는 논리 시각 이동으로 발생시킨다.
- latch/future와 테스트 DB 알림을 동작 전에 등록한다. 필요하면 테스트 전용
  trigger의 commit 알림을 받아 lease generation 진입을 확인한다. 제한 시간은
  실패 안전장치일 뿐 순서 제어 수단이 아니다. sleep·반복 조회 polling은 금지한다.
- 신호는 실제 DB 갱신·잠금 뒤 발생해야 한다. 시작 barrier만으로 잠금 획득을
  주장하지 않는다. 테스트용 공개 production 메서드·플래그는 추가하지 않는다.
- 기존 `awaitLease` polling은 INT-020을 맡은 Integration-Flow 소유 파일에서
  신호 기반으로 갱신한다. 관련 없는 concurrency 파일까지 정리하지 않는다.
- 여러 chunk가 필요한 구현이라면 각 chunk가 다른 최신 상태를 읽어 혼합하지
  않는 snapshot 계약을 먼저 명시하고 INT-022에서 검증한다.

### External APIs

- 매칭 계산 자체는 외부 API를 호출하지 않는다. 기존 moderation/FCM fake는
  유지하되 crypto·PostGIS·JDBC를 fake로 대체하지 않는다.
- production SSM/I2 주입은 별도 인프라 승인 대상이다. 여기서는 환경 설정의
  binding·fail-fast만 합성 값으로 검증한다. 실제 secret과 서버 주소는 기록하지 않는다.
- 기존 API 상태 코드·응답 형태와 B1·동의 gate는 유지한다. post_recipient 보호의
  API/정렬 영향 분석은 별도 Issue다.

### Failure recovery and reconciliation

- 잘못된 키·변조 행을 건너뛰어 일부 수신자만 확정하는 동작을 허용하지 않는다.
  오류 발생 transaction의 원자성과 Outbox의 제한된 실패 결과를 확인한다.
- D7 완료 뒤 이전 데이터 복구는 전환 전 백업 복원뿐이다. 합성 백업 복원 DB에서
  평문이 돌아옴을 확인한 뒤 별도 폐기한다. 현재 DB나 실제 백업을 변경하지 않는다.
- key 회전은 reader 준비→current 전환→previous 잔존 검증의 순서다.
  추가 회전 전 행·백업 잔존 확인은 운영 절차이며 DB fixture read만으로 완료를 주장하지 않는다.
- 기존 실패 글의 멱등 재생은 과거 결과 재생이지 신규 매칭 성공이 아니다.
  계획·구현·검증 실패 시 임계값이나 후보 포함 정책을 바꿔 통과시키지 않는다.

### 독립 PostGIS oracle

- 승인 후 변경 전 두 SQL의 의미를 테스트 fixture에 고정한다. 합성 좌표·계정·
  차단·수신 상태가 같은 별도 테스트 schema에서 실행하며 기준 commit과 SQL 해시를
  보고한다. 테스트 oracle은 새 앱 계산·새 SQL의 예상값을 호출하지 않는다.
- 기존 SQL은 거리 비교·정렬·집계 전체를 실행한다. 숫자만 GeographicLib로
  계산해 oracle에 주입하거나 미리 정답 후보만 심는 방식은 금지한다.
- 평문 oracle 자료는 테스트 DB에만 두고 production 평문 컬럼을 보존하지 않는다.
  production write는 실제 protector/repository로 수행한다.
- 일반 경계는 고정 합성 corpus, 거리·섹터 경계는 PostGIS의 독립 생성/계산
  기준으로 작성하고 §3 보정의 거리 ±1m·각도 ±0.0001도 간격을 사용한다.
  경계 양옆과 경계선 바로 위의 점을 구분해 전자는 정확 일치, 후자는 허용 오차와
  앱 자기 수치에 대한 경계 규칙 일관성으로 검증한다.
  도출된 좌표가 실제 임계값의 어느 쪽인지 oracle로 확인하며,
  부동소수점 때문에 exact 경계가 없는 입력을 억지로 같다고 라벨링하지 않는다.
- 거리 차이는 절댓값, 각 차이는 `min(abs(a-b), 360-abs(a-b))`로 비교한다.
  `[0,360)` 정규화와 NULL 방위를 따로 확인한다. 숫자 tolerance를 WHERE 판정에
  적용하지 않는다. 후보 ID/순서/개수 비교에는 §3 보정의 경계선·거리 동률 규칙을
  적용하고 1m 미만 점은 각도 수치 비교만 제외한다.
  모든 차등 fixture의 최대 절대 거리 차이·순환 각 차이와 각도 제외 여부를
  `docs/reports/tests/gh-388-TEST-PLAN-GH-388-COORDINATE-ENCRYPTION.md`에 기록한다.

### 별도 성능 시나리오와 기록

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| PERF-001 | D6의 OCI 고정 자원·동일 arm64 이미지/digest, 10,000 비공간 적격 후보, 밀집/전세계 분포, current-only와 혼합 키 | main 고정 commit과 #388을 번갈아 후보 repository·previewAll·단일 worker 전체 흐름별 warm-up 20회 후 200회 측정 | 동시성 1에서 매칭·previewAll 각각 대응 평문 전체 흐름 p95 대비 2배 이내, 후보 집합·순서·집계 불일치 0건; 매칭 p95 1초·previewAll p95 500ms는 참고값으로만 기록 | P0 | Performance |
| PERF-002 | 같은 OCI 환경의 1,000·100,000 후보와 10km·100km·20,100km 거리 | 후보 규모·거리·분포별 같은 반복 측정 | 고정비와 한계의 p50/p95/p99·처리량·CPU·GC·전송·DB I/O 기록; 100,000명이 고정 메모리 안에서 실행되지 않으면 자원 한계 결과로 기록하며 성공으로 간주하지 않는다; 실행된 결과의 오류·누락은 FAIL | P0 | Performance |
| PERF-003 | 같은 후보 자료, 매칭 동시성 4와 preview 혼합 | 미리 등록한 barrier로 부하 시작, commit 완료 관찰 | 후보 집합·순서·집계 불일치 0, 중복 확정 0; lock 대기·transaction 시간·lease 여유·성능 저하 기록 | P0 | Performance |

승인된 측정 환경은 개인 OCI 인스턴스의 aarch64(Neoverse-N1), 2코어, 11GB다.
Docker는 미설치 상태이며 측정 단계에서 설치를 별도 승인받는다.
이 문서 승인 반영 작업에서는 인스턴스에 접속하거나 환경을 변경하지 않는다.
측정 중 다른 작업을 실행하지 않고 합성 데이터와 fixture key만 사용한다.

인계의 Docker Hub 확인 결과, `postgis/postgis:16-3.5-alpine`은 amd64만 제공한다.
성능 측정에는 arm64를 제공하는 `imresamu/postgis:16-3.5-alpine`을 전용으로 사용한다.
main 기준선과 #388은 측정 전에 확정한 동일 이미지·동일 digest로 실행한다.
일반 CI·로컬 통합 테스트의 `postgis/postgis:16-3.5-alpine` 기본값은 바꾸지 않는다.
성능 전용 이미지 선택 방식(시스템 속성 등)은 구현 단계에서 정하고 기본 동작을 유지한다.
실제 사용 digest는 측정 시 기록하며 현재 확인한 값처럼 만들어 쓰지 않는다.

PostGIS 컨테이너는 1코어, JVM은 1코어로 고정한다. 각각의 메모리 한도와 JVM heap
설정을 실행 전에 정해 두 버전에 동일하게 적용하고 기록한다.
호스트 총 11GB와 각 실행 단위의 한도는 구분한다. 100,000명 한계 측정이 이 한도에서
실행되지 않으면 OOM 등 원인·중단 지점·자원 사용을 결과로 남기고 한도를 몰래 늘리지 않는다.

계측 구간은 원점 read/복호화부터 후보 전송·복호화·계산·정렬·수신 예약·commit까지다.
repository·previewAll 구간도 분리해 기록한다. SQL 한 문장의 시간과 worker 전체 시간은
비율 비교하지 않는다. 평문 성능 기준선은 main의 고정 commit, 비교 대상은 #388의
고정 commit으로 기록하고 같은 인스턴스에서 두 버전을 번갈아 측정한다.
두 버전 모두 동일한 worker·예약·commit 계측 경계를 사용한다.
이 경계가 달라지면 비교 불가로 BLOCKED다. §7의 테스트 전용 SQL oracle은 기능
차등 비교용으로 유지하며 main commit 전체 흐름의 성능 기준선을 대신하지 않는다.
버전별 warm-up 20회·측정 200회는 유지하고 실행 순서도 기록한다.

D6 수정 승인에 따라 이 환경의 합격 판정은 대응 기준선 p95 대비 2배 이내와
후보 집합·순서·집계 불일치 0건을 사용한다. 중복 확정 0건도 유지한다.
매칭 p95 1초·previewAll p95 500ms 등 절대 지연은 운영 CPU가 다르므로 참고값으로만
기록하며 이 수치만으로 PASS/FAIL을 결정하지 않는다.

반복마다 상태를 원래 fixture로 되돌린 뒤 새로운 논리 작업을 수행한다. 이미 처리된
이벤트의 멱등 no-op을 200회 측정하지 않는다. fixture reset·warm-up은 측정 구간에서
제외하고 cold 시작 결과는 별도로 기록한다. GC나 동시 작업의 잡음 때문에 실패한
표본을 버리거나 재실행 중 가장 빠른 것만 보고하지 않는다.

기록 항목은 p50/p95/p99, 처리량, 앱·DB CPU, heap/GC, 읽은 행·전송 bytes,
transaction 시간, lease 여유, lock 대기, DB buffer/temp I/O와 합성
`EXPLAIN (ANALYZE, BUFFERS)` 요약이다. 앱/JDK·DB·PROJ/GeographicLib 버전,
arch, CPU 모델, 코어 수·할당, 호스트 메모리·각 메모리 한도, 커널, JDK,
PostGIS 이미지 digest, 컨테이너 설정·네트워크·동시 부하·seed도 남긴다.
성능 측정은 arm64 이미지로 수행하며 인스턴스 이름·주소는 기록하지 않는다.
측정할 수 없는 필수 지표는 UNKNOWN/BLOCKED로 표시하고 통과로 간주하지 않는다.

최종 기록 위치는
`docs/reports/tests/gh-388-TEST-PLAN-GH-388-COORDINATE-ENCRYPTION.md`다.
합성 성능 측정 원자료는 OCI 인스턴스의 작업 디렉터리 내
`build/reports/coordinate-encryption/`에 보관한다.
성능 보고서에는 위 환경 메타데이터·요약 수치·원자료 해시만 남기며 원자료를 복사하지 않는다.
JUnit 결과는 각 실행 환경의 기존
`build/test-results/test`, `build/test-results/integrationTest`,
`build/test-results/performanceTest`를 사용한다. 실제 좌표·키·계정 식별자를
원자료나 failure dump에 넣지 않는다.

## 8. Test data and isolation

- Fixtures: 고정 seed의 합성 위경도·계정·question·scheme·수신 상태·block,
  current/previous/wrong 목적의 테스트 전용 키. 키 값은 계획·보고에 쓰지 않는다.
- 정확도 corpus는 일반점·동일점·극지·날짜변경선·대척/근대척점·섹터/거리
  경계별로 구분한다. 실패 보고에는 case ID와 오차만 남기며 좌표 dump를 피한다.
- 일반 기능 fixture는 기존 PostGIS support를 사용하고 공유 DB 클래스 병렬 실행은
  하지 않는다. 독립 executor도 같은 DB를 동시에 소유하지 않도록 실행 순서를 지킨다.
- migration·oracle·복원용 schema/DB는 테스트 전용 이름으로 격리하며 운영 접속
  설정을 사용하지 않는다. 데이터 제거는 그 테스트가 만든 자원에만 한정한다.
- Clock은 고정·명시적 이동, 합성 corpus는 고정 seed다. 암호문의 무작위 bytes
  자체를 snapshot으로 고정하지 않는다. 실제 nonce 처리를 검증하되 우연한
  랜덤 차이 하나만 성공 조건으로 삼지 않는다.
- 로그 capture는 작업 전에 등록하고 finally에서 해제한다. SQL 예외나
  assertion 메시지가 sentinel 원문을 다시 출력하지 않도록 성공·실패 결과를 redaction한다.
- 트랜잭션·동시성 테스트는 real connection/proxy를 쓰고 executor·listener·
  테스트 trigger·appender를 반드시 종료한다. 외래키 역순으로 테스트 소유 행만 정리한다.
- #381가 병합되면 필요한 동의 fixture를 정상 진입 사례에만 명시한다.
  B1 off/무동의 거부 시나리오에 자동 동의를 넣지 않는다.

## 9. Execution contracts

아래 테스트 계획과 파일 소유 범위는 §11의 사람 승인을 받았다. 이 계획은 production 구현 권한을
부여하지 않는다. production 변경은 별도 실행 계약에서 Java 지침과 파일 목록을
확정한 뒤 진행한다. 테스트 실행 전 `harness-test-run`과 reporting 계약을 읽는다.
새 test 이름은 제안이며 공개 production 타입·테스트 전용 seam 생성을 강제하지 않는다.

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Unit | 아래 Unit 목록만 | UNIT-001~014 | `./gradlew test` |
| 2 | Integration-Storage | 아래 Storage 목록과 공통 암호문 fixture만 | INT-001~005,009,010,018 | 해당 클래스 표적 실행 후 전체 integrationTest |
| 3 | Integration-Matching | 아래 Matching 목록과 PostGIS oracle만 | INT-011~016 | 해당 클래스 표적 실행 후 전체 integrationTest |
| 4 | Integration-Flow | 아래 Flow 목록만 | INT-006~008,017,019~024 | 해당 클래스 표적 실행 후 전체 integrationTest |
| 5 | Performance | 아래 Performance 목록, oracle/fixture는 읽기 전용; 일반 CI·로컬 이미지 기본값 유지 | PERF-001~003 및 기존 성능 회귀 | Docker 설치 별도 승인 후 OCI에서 main/#388 교대로 `./gradlew performanceTest`, 같은 digest·CPU·메모리 한도 기록 |
| 6 | 독립 검증 executor | source 수정 금지, 승인 후 테스트 보고서만 | 전체 | §10 명령·실제 diff·실행 증거 검토 |

공통 Unit prefix는 `src/test/java/com/dnd/qello/`, Integration prefix는
`src/integrationTest/java/com/dnd/qello/`다. 서로 다른 executor가 같은 파일을
고치지 않는다. 공통 fixture/oracle을 먼저 완성하고 읽기 전용으로 전달한다.

Unit 소유:
- 신규 `direction/security/CoordinateProtectorTest.java`(001~007),
  `direction/config/CoordinateProtectionPropertiesTest.java`(008),
  `direction/security/CoordinateCodecTest.java`(009),
  `direction/matching/CoordinateCalculationTest.java`(010~012),
  `direction/security/CoordinateRedactionTest.java`(014).
- 신규 `direction/domain/PostAudienceTest.java`(013; 현재 worktree에 없음).
- 기존 `direction/DirectionPostSubmissionServiceTest.java`의 발견된 평문 fixture
  관련 경계만 확인·갱신한다. 문자열 일치만 고정하는 테스트로 대체하지 않는다.

Integration-Storage 소유:
- 신규 `CoordinateMigrationIntegrationTest.java`(001~004),
  `CoordinatePersistenceIntegrationTest.java`(009,010),
  `CoordinateProtectionConfigurationIntegrationTest.java`(018),
  `CoordinateEncryptionTestFixture.java`(합성 키·암호문 공통 fixture).
- 기존 `FlywayMigrationIntegrationTest.java`의 migration/catalog 기대,
  `ActiveUserPresenceApiIntegrationTest.java`(005),
  `src/integrationTest/resources/application-test.yml`의 fixture 주입.

Integration-Matching 소유:
- 신규 `CoordinateMatchingDifferentialIntegrationTest.java`(011,012,014~016),
  `CoordinatePostgisOracle.java`(기준 SQL·합성 자료 조회만).
- 기존 `DirectionPreviewIntegrationTest.java`(013, 평문 seed 대체 포함).
- §3의 D3 보정 1~6에 따라 INT-014·015의 경계 fixture와 비교를 갱신한다.
  거리 동률·1m 미만 각도 제외·최대 오차 집계는 차등 fixture 공통 비교에도
  적용하되 다른 시나리오의 입력·목적과 기존 허용 오차 수치는 바꾸지 않는다.
- 구현 담당이 모든 차등 fixture의 최대 절대 거리 차이·각도 차이와 제외 내역을
  독립 검증 담당에게 전달하고, 독립 검증 담당은 지정 테스트 보고서에 기록한다.
  보고 가능한 오차 관측 없이 통과만 기록하지 않는다.

Integration-Flow 소유:
- 신규 `CoordinateMigrationRecoveryIntegrationTest.java`(006,007),
  `CoordinateProtectionFailureIntegrationTest.java`(008,024).
- 기존 `DirectionMatchingContractIntegrationTest.java`(019·멱등 회귀),
  `DirectionMatchingWorkerIntegrationTest.java`(017·worker 암호문 회귀),
  `DirectionMatchingWorkerConcurrencyIntegrationTest.java`(020,021),
  `ActiveUserPresenceUpdateIntegrationTest.java`(022).
- INT-023 fixture 영향 목록 중 Storage/Matching/Performance 소유를 제외한
  `LocationCollectionGateDisabledIntegrationTest.java`,
  `PushDispatchSuppressionIntegrationTest.java`,
  `RecipientNotificationFanOutWorkerIntegrationTest.java`.
- INT-017의 MVC 경로는 Storage의 위치 API test를 재작성하지 않고 새 저장값
  assertion을 그 owner에게 전달한다. Flow는 발송·worker 통합 경로를 소유한다.

Performance 소유:
- 신규 `CoordinateEncryptionPerformanceIntegrationTest.java`(PERF-001~003).
- 기존 `DirectionMatchingPerformanceIntegrationTest.java`,
  `DirectionMatchingE3PerformanceIntegrationTest.java`,
  `DirectionMatchingIndexPlanPerformanceIntegrationTest.java`,
  `DirectionMatchingPerformanceProbe.java`,
  `DirectionMatchingPerformanceProbeIntegrationTest.java`.
- GiST 강제 assertion은 승인된 A의 계산·지연 계약으로 갱신한다.
  `build.gradle`의 기존 performance 분리 설정은 사용만 하며 변경하지 않는다.
- 성능 전용 arm64 이미지 선택 방식과 필요한 수정 파일은 구현 단계에서 확정한다.
  일반 CI·로컬 통합 테스트의 이미지 기본값을 바꾸지 않으며, 추가 파일 소유는
  실행 계약에 기록한 뒤 진행한다. 두 버전에 같은 이미지·digest·자원 제한을 적용한다.
- OCI 접속·Docker 설치·측정은 이 문서 반영 작업 범위가 아니다. 측정 담당자는
  별도 설치 승인 뒤 1코어 PostGIS·1코어 JVM과 동일 메모리 한도를 확인하고
  다른 작업 없이 main 고정 commit과 #388을 번갈아 측정한다.
- 원자료는 인스턴스에 보관하고 보고서에는 요약 수치·해시와 지정 환경 메타데이터만
  남긴다. 100,000명 자원 한계와 절대 지연 참고값을 상대 합격 판정과 구분한다.

신규 테스트의 실제 분할은 기존 파일에 같은 계약이 있으면 그 owner에 합치는
방향으로 확정한다. 추가 파일 영향이 발견되면 구체 목록과 이유를 승인자에게
보고하고 소유 계약을 갱신한다. 기존 failing test를 skip·삭제하거나 입력을
약화해 통과시키지 않는다. 테스트용 상수·fixture 이름은 production 설계 승인이 아니다.

## 10. Completion criteria

- [x] 테스트 계획 D1~D5 추천안과 D6 수정안, 실행 파일 소유 범위를 사람이 승인했다(§11).
- [ ] 모든 P0 UNIT-001~014, INT-001~024를 구현하고 기존 회귀를 통과했다.
- [ ] 모든 테스트 메서드에 `@DisplayName`, 클래스에 정확한 생성 timestamp와 source scenario ID가 있다.
- [ ] 새/변경 테스트마다 관찰 계약·실제 실패 회귀·기존 coverage 부족·불필요한 production seam 부재를 확인했다.
- [ ] 가능한 대표 회귀 mutation으로 AAD 무시·비교 연산 변경·state 처리 누락이 의도대로 실패하는지 확인하고 원상 복구했다.
- [ ] `./gradlew test`, `./gradlew integrationTest`가 단일 실행에서 통과했다.
- [ ] `./gradlew performanceTest`에서 PERF-001~003과 기존 성능 suite를 실행하고 지표·원자료·기준선 정보를 보고했다.
- [ ] 후보 ID 집합·순서·집계가 정확히 같고 승인 수치 오차 이내다.
- [ ] 단일 migration의 빈 DB·직전 버전 업그레이드·실패 rollback·합성 백업 복원과 멱등 재생을 확인했다.
- [ ] `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check`를 통과했다.
- [ ] 애플리케이션·DB·동시성·transaction·외부 API·장애 복구 잠재 문제와 남은 운영 인프라 위험을 분석했다.
- [ ] 승인 후 `templates/test-report.md` 형식의 지정 테스트 보고서를 생성했다.

`gh`·`harness`에는 `env -u SSL_CERT_FILE -u SSL_CERT_DIR`를 앞에 붙인다.
단위·통합 성공만으로 performance 통과를 대신하지 않는다.
필수 P0·기존 회귀 실패는 FAIL, 승인·Docker·자원·기준선 부재는 BLOCKED다.
수치·성능 실패를 A 대신 D 자동 적용이나 허용 오차 완화로 해결하지 않는다.
보고서는 실패·skip·미실행을 구분하고 코드·문서 문구를 pin하는 테스트는 만들지 않는다.

### 계획 단계의 확인 상태

- 템플릿 1~11절, Issue/ADR 계약과 시나리오 대응, source와 기존 테스트를 읽었다.
- #381 계획은 현재 worktree에 없어 PR #384의 head 브랜치에서 읽기 전용으로 가져와 형식만 참고했다.
- 운영 데이터 없음은 ADR 승인 댓글의 사람 확인이다. 실제 dev 행 수·운영 환경·SSM 주입은 조회하지 않았다.
- benchmark 환경과 CPU 배분은 D6대로 확정했다. GeographicLib 버전·라이선스,
  실행 시 migration 직전 버전, baseline/#388 commit, 이미지 digest, 각 메모리 한도는 실행 전에 확정·기록한다.
- 새 코드가 없으므로 JUnit·PostGIS 차등·성능 측정을 실행하지 않았다.
- 현재 세션의 파일 작성 규칙에 따라 scaffold 명령 대신 읽은 `templates/test-plan.md`의 모든 절을 `apply_patch`로 작성했다.

## 11. Human approval

- Reviewer: @Byuntil
- Decision: `APPROVED` — 단위 14·통합 24·성능 3 시나리오, D1~D5 추천안과 D6 수정안 승인
- Approved at: 2026-11-25
- Evidence: `https://github.com/dnd-side-project/dnd-15th-2-backend/issues/388#issuecomment-7139174419`
- 댓글 생성 시각은 2026-11-24T17:25:31Z이며 한국 시각으로 2026-11-25다. 계획 최초 생성 시각은 유지한다.
- D1: 테스트 전용 PostGIS oracle을 차등 기준으로 사용한다.
- D2: 기존 performanceTest로 일반 CI와 분리하되 성능 증거를 #388 완료 조건에 포함한다.
- D3: 실제 암호화·저장·로그 경계를 결합해 보안을 검증한다.
- D4: 합성 데이터의 실제 PostGIS DB에서 migration·복원을 검증한다.
- D5: ADR-0009로 stale해진 fixture·기대값만 갱신하고 후보·정렬·보안 검증은 유지한다.
- D6 수정안: 개인 OCI의 arm64 2코어·11GB 격리 환경에서 같은 arm64 PostGIS
  이미지·digest와 고정 CPU·메모리로 main/#388을 교대 측정한다. 상대 비교로 합격을
  판정하고 절대 지연은 참고값으로 남긴다. Docker 설치는 측정 단계에서 별도 승인받는다.
- 승인과 실행의 구분: 테스트 계획은 승인됐지만 구현·검증 성공을 의미하지 않는다.
- D6 필수 지표 조정(@Byuntil, 2026-11-27, https://github.com/dnd-side-project/dnd-15th-2-backend/issues/388#issuecomment-7209939195): 미계측 `dbCpuNanos`·`rowsRead`·`wireBytes`·`dbBufferIo`·`dbTempIo`는 합성 EXPLAIN 요약과 기존 64회 wall time·heap·GC 분포로 대체 수용하고, `transactionNanos`·`leaseHeadroomNanos`·`lockWaitNanos`는 PERF-003 worker p95 최대 약 930ms와 중복 확정 0건을 근거로 추가 측정 없이 수용한다. 값은 0·추정치로 채우지 않고 미계측·사람 결정으로 표시하며, 64회 재측정 및 runner 필수 증거 assertion 변경은 하지 않는다.
  이 승인 반영 작업은 코드 작성·테스트 실행·OCI 접속·설치·commit/push를 수행하지 않는다.

### 2026-11-25 — ADR D3 차등 테스트 보정 1~6

- Reviewer: @Byuntil
- Decision: `APPROVED` — §3 보정 1~6을 INT-014·015와 관련 차등 비교·보고 계약에 적용한다.
- Approved at: 2026-11-25
- Evidence: `https://github.com/dnd-side-project/dnd-15th-2-backend/issues/388#issuecomment-7150447072`
- 댓글 생성 시각: 2026-11-25T02:12:42Z.
- 승인 배경은 댓글에 기록된 차등 테스트 19건 중 9건 실패와 fixture 구성이다.
  이는 승인자가 제공한 실행 근거이며 이 문서 반영 세션의 재실행 결과가 아니다.
- 승인 내용: 경계 간격 거리 ±1m·각도 ±0.0001도, 경계선 점의 수치 오차와
  자체 수치 기반 반열린 구간 검증, 허용 오차 이하 거리 동률의 사용자 ID 비교,
  1m 미만 방위·역방위 수치 비교 제외와 포함 여부 비교 유지,
  최대 거리 fixture 20,000,000m·설정 상한 20,100,000m 유지,
  모든 차등 fixture의 최대 절대 거리·각도 차 보고.
- 허용 오차 거리 0.001m·각도 0.0000001도와 정확 일치 원칙을 유지한다.
  대안 D 전환이나 다른 시나리오·성능 수치 변경 승인이 아니다.
