# Test Plan: TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE

> Created at: `2026-08-03T18:03:23+09:00`
> GitHub Issue: `#37`
> Status: Draft — human approval required before implementation

## 1. Objective

Flyway가 소유하는 기존 `user_account` schema를 Hibernate가 변경하지 않은 채
Account domain model과 JPA adapter가 저장·조회·순차 갱신하고, enum·timestamp·FK와
check constraint를 실제 PostgreSQL에서 정확히 지킨다는 증거를 만든다.

## 2. Scope

### Included

- Spring Data JPA 의존성과 Hibernate schema validation 안전 설정
- `Instant` 기반 created/updated auditing과 문자열 enum 공통 규칙
- Account domain model, repository port, JPA Entity/mapper/adapter 경계
- `user_account` 저장·조회·순차 갱신과 raw database mapping 검증
- FK/check constraint 위반과 transaction 전체 rollback 검증
- 다른 feature의 Account JPA 구현 직접 참조 방지 검증
- JPA 실행 결과와 잠재 문제 보고서

### Excluded

- V1 수정, 새 Flyway migration, Hibernate schema 생성·수정
- Region Entity와 `user_private_attribute`, `active_user_presence`,
  `recipient_receive_state` persistence
- Account API, 인증·세션, 닉네임 고유성·변경 주기 정책
- Question/Direction/Answer/Safety/Notification persistence
- schema에 없는 version column과 `@Version` 낙관적 잠금
- 운영 DB, RDS 권한, 배포와 다중 인스턴스 동시 수정 시험

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #37 | Account 저장·조회·갱신, enum/timestamp, domain-port-adapter 경계, feature 격리 |
| ADR-0001 | Flyway만 schema를 변경하고 Hibernate는 `validate`만 수행 |
| ADR-0002 | 일반 aggregate CRUD는 JPA, domain/application은 repository port에만 의존, 외부 aggregate는 FK ID로 보관 |
| Flyway V1 / DBML | `user_account` identity PK, 문자열 role/status, scalar region FK, `TIMESTAMPTZ`, nickname 및 삭제 상태 checks |
| README package contract | Package-by-Feature, controller → service → repository, 다른 feature의 Entity/Repository 직접 참조 금지 |
| Spring Boot 3.5 reference | JPA entity/repository auto scan과 `spring.jpa.open-in-view=false` 명시 설정 |
| Spring Data JPA auditing | `@CreatedDate`, `@LastModifiedDate`, `@EnableJpaAuditing` 기반 timestamp 관리 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| Hibernate가 schema를 생성·변경함 | Flyway history와 실행 schema drift | Medium | P0 | `ddl-auto=validate`, generate DDL 금지, startup catalog 불변 |
| enum을 ordinal 또는 잘못된 문자열로 저장 | 권한·계정 상태 오판 | Medium | P0 | raw SQL로 `USER`/`OPERATOR`, `ACTIVE`/`BLOCKED`/`DELETED` 확인 |
| `TIMESTAMPTZ`를 local time으로 매핑 | 만료·감사 시각 오류 | Medium | P0 | 고정 audit clock과 `Instant` round-trip |
| domain이 JPA/Spring Data에 의존 | persistence 교체 불가와 feature 결합 | Medium | P0 | import/annotation boundary contract |
| Region을 Entity 관계로 연결 | 미구현 feature graph와 lazy/N+1 유입 | Medium | P0 | scalar `coarseRegionCode`, relation annotation 부재 |
| constraint 실패 전 변경이 commit됨 | 부분 Account 데이터 잔존 | Low | P0 | valid insert + invalid insert 단일 transaction rollback |
| audit clock이 test에서 비결정적 | 갱신 timestamp flaky test | Medium | P0 | test 전용 제어 가능한 `DateTimeProvider` |
| 낙관적 잠금 부재로 lost update | 동시 프로필 수정 덮어쓰기 | Medium | P1 | 이번 Issue의 명시적 제외와 후속 schema 결정 필요 |
| Open EntityManager in View 활성화 | web 계층 lazy query와 transaction 경계 누출 | Medium | P1 | 설정 contract에서 false 확인 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-UNIT-001 | 유효/무효 Account 값이 주어짐 | domain model을 생성·변경함 | 필수 region/locale/timezone, 길이, 공백 nickname, status/deletedAt 대응 규칙을 빠르게 검증함 | P0 | Account executor |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-UNIT-002 | domain model과 JPA entity 값이 주어짐 | mapper로 양방향 변환함 | ID, scalar region, nullable nickname, 문자열 enum 의미, `Instant`가 손실 없이 유지됨 | P0 | Account executor |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-UNIT-003 | account 소스와 compiled class가 주어짐 | persistence boundary를 검사함 | domain/port에 JPA·Spring Data 의존이 없고 다른 feature에 Account Entity/Repository 참조가 없으며 Entity에 relation/`@Version`이 없음 | P0 | Test executor |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-UNIT-004 | application JPA 설정이 주어짐 | properties를 로드함 | `ddl-auto=validate`, `generate-ddl=false`, `open-in-view=false`, UTC JDBC time zone이 고정됨 | P0 | Test executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-INT-001 | Spring Boot, Flyway, Hibernate, PostGIS | 빈 Testcontainers DB와 test profile | context startup | Flyway V1 한 건 적용 후 Hibernate validate 성공, 신규 table/migration 없음 | container 종료 |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-INT-002 | Account port/adapter, JPA auditing | region fixture와 고정 audit clock | Account 저장 후 ID 조회 | identity ID와 created/updated time이 생성되고 domain model이 동일 값으로 복원됨 | transaction rollback 또는 container 폐기 |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-INT-003 | JPA, raw JDBC | 저장된 USER/ACTIVE Account | raw row 조회 후 nickname/status 갱신 | enum은 문자열, timestamp는 같은 `Instant`, createdAt 불변·updatedAt 증가 | transaction rollback 또는 container 폐기 |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-INT-004 | PostgreSQL constraints, repository adapter | region fixture 없음 또는 schema-invalid 값 | save/flush 또는 native insert | FK, 공백 nickname, status/deletedAt 위반이 거절되고 정상으로 조회되지 않음 | failed transaction 폐기 |
| TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-INT-005 | Spring transaction, Account adapter | 같은 transaction의 valid Account와 invalid Account | 두 저장을 flush함 | 예외 발생 후 valid Account까지 rollback되어 부분 row가 남지 않음 | 새 transaction에서 count 확인 |

## 7. Cross-cutting scenarios

### Database and transactions

- PostgreSQL 16/PostGIS 3.5 컨테이너와 실제 Flyway V1을 사용한다.
- constraint 최종 경계는 native SQL 또는 flush 시점까지 확인한다.
- integration test가 Hibernate DDL 생성 기능으로 schema를 보정하지 못하게 한다.

### Concurrency and idempotency

- identity insert와 순차 update만 P0로 검증한다.
- 현재 schema에는 version column이 없으므로 `@Version`을 임의 추가하지 않는다.
- 두 detached writer의 lost update는 P1 잔여 위험이며 version column migration 또는
  조건부 update 정책이 승인된 후 별도 검증한다.

### External APIs

- 외부 API 호출은 없다. Docker/Testcontainers image 실행만 사용한다.
- 실제 자격 증명, 운영 주소, `.env` 값은 사용하지 않는다.

### Failure recovery and reconciliation

- DB constraint 예외 뒤 transaction이 rollback-only가 되고 부분 row가 남지 않는지
  새 transaction에서 확인한다.
- Hibernate validation 실패를 자동 DDL로 복구하지 않으며 schema 변경은 새 Flyway
  migration 검토로 돌린다.

## 8. Test data and isolation

- Fixtures: test-only COUNTRY `region_code`, 유효 Account, FK/check 위반 Account 값
- Database isolation: class-scoped disposable PostGIS container, test transaction 또는
  명시적 rollback 검증용 새 transaction
- Clock/randomness: test 전용 mutable `DateTimeProvider`로 created/updated 시각 고정
- External API doubles: 없음
- Cleanup: Testcontainers가 DB 전체를 폐기하며 Flyway clean/repair는 호출하지 않음

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Configuration executor | `build.gradle`, `src/main/resources/application.properties`, `src/main/java/com/dnd/qello/common/persistence/**` | UNIT-004, INT-001 prerequisite | `compileJava`, context startup |
| 2 | Account executor | `src/main/java/com/dnd/qello/account/**` | UNIT-001~002, INT-002~003 prerequisite | `compileJava`, account unit tests |
| 3 | Test executor | `src/test/**`, `src/integrationTest/**`, `docs/reports/tests/gh-37-*.md` | UNIT-003~004, INT-001~005 | `test`, `integrationTest`, report |
| 4 | Orchestrator | `TASK.md`, test plan/report review only | all | Harness, Hook, diff checks |

각 executor는 앞 순서의 계약을 입력으로 사용하고 다른 executor의 소유 파일을
수정하지 않는다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성
- [ ] JDK 21로 `./harness pr-ready --project-tests` 통과

## 11. Human approval

- Reviewer: pending
- Decision: pending
- Approved at: pending
