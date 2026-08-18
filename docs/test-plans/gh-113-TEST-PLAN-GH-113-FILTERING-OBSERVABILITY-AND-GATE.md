# Test Plan: TEST-PLAN-GH-113-FILTERING-OBSERVABILITY-AND-GATE

> Created at: `2026-08-18T20:04:43+09:00`
> GitHub Issue: `#113`
> Status: Approved

## 1. Objective

필터링 시스템이 운영 중에 무슨 일을 하는지 관측할 수 있게 하고, 사람이 내린
결정에 근거가 남게 하며, 법률·보안 확인이 끝나기 전에는 production에서 켜지지
않도록 막는다.

- **운영 지표가 원문이나 직접 식별정보를 복제하지 않는지 검증한다**
  (`INV-CMP-001`, `INV-CMP-002`). 깨지면 관측 백엔드가 콘텐츠와 사용자 식별자의
  2차 사본이 된다. 지표는 보존 기간·접근 통제가 원본 저장소보다 느슨한 곳으로
  흘러가므로, 여기서 새면 되돌릴 수 없다.
- **운영자 권한 변경에 actor·reason·policy version·시간이 모두 남는지 검증한다**
  (`INV-APL-012`). 깨지면 "누가 왜 이 판정을 뒤집었는가"에 답할 수 없고,
  이의제기·분쟁 대응과 내부 감사가 성립하지 않는다.
- **확인 항목이 비어 있을 때 필터링이 켜지지 않는지 검증한다**
  (`INV-CMP-005`, `INV-CMP-006`). 깨지면 법률·계약 검토가 끝나지 않은 상태로
  사용자 콘텐츠가 외부 moderation 공급자로 나간다.
- **감사 기록 실패가 운영자 결정을 조용히 통과시키지 않는지 확인한다.**
  깨지면 감사 이력이 있는 결정과 없는 결정이 섞여, 이력의 부재가 "행위가
  없었음"인지 "기록에 실패했음"인지 구분할 수 없게 된다.

## 2. Scope

### Included

- `operator_action_audit` 테이블(V20)과 `OperatorActionAudit` 도메인,
  `OperatorActionType`, repository, `OperatorActionAuditRecorder`.
- 필터링 운영자 경로 배선: release `offline-evaluation`/`shadow`/`canary`/
  `promote`/`rollback`, manual review `decide`, appeal `decide`/`extend`,
  snapshot health `confirm-permanent`, emergency migration.
- 위 endpoint의 요청 본문에 `reason` 필수 추가와 검증.
- `FilteringMetrics`(신규 계측 지점)와 metric tag 허용목록 가드.
- `FilteringProductionGate`와 확인 항목 설정, 체크리스트 문서.
- Micrometer 의존성 도입과 actuator web endpoint 비노출 설정.
- 단위 테스트, PostgreSQL 통합 테스트, 테스트 보고서.

### Excluded

- metric exporter 선택, 대시보드, 경보 임계값 — 도구가 미정이다.
- `SlackNotifier` 구현체와 worker scheduler 배선.
- 질문 제안 검토·신고 처리 등 필터링 밖 도메인의 운영자 행위 배선.
- 법률·계약 항목의 실제 검토와 승인. 이 계획은 게이트가 동작하는지만 본다.
- 감사 로그의 보관 기간·삭제·익명화·legal hold.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #113 | 운영 지표에 원문·직접 식별정보를 불필요하게 복제하지 않는다 (`INV-CMP-001`, `INV-CMP-002`) |
| GitHub Issue #113 | 모든 수동·운영자 권한 변경에 actor, reason, policy version과 시간이 남는다 (`INV-APL-012`) |
| GitHub Issue #113 | 법률·계약상 확인 항목은 production 활성화 전에 승인을 받는다 (`INV-CMP-005`, `INV-CMP-006`) |
| GitHub Issue #113 | 경로별 latency·timeout·오류, 판정 분포, queue 체류, attempt 대비 SDK 호출 수, Slack delivery 결과 |
| `ReleasePromotionHistoryEntry`, `ManualReviewCase`, `AppealCase` | 현재 actor·시간만 있고 reason·policyVersion이 없다 |
| `V16`, `V18` | 프로덕션 행이 없는 필터링 테이블에 기존 행 보정 없이 NOT NULL을 추가한 선례 |
| `SecurityConfiguration` | 미매칭 경로를 막는 fallback `denyAll` 체인 |
| `TASK.md` Design decisions | 구현 전 확정한 7개 판단과 근거 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| metric tag에 답변 원문이나 사용자 식별자가 실림 | 관측 백엔드가 콘텐츠·PII의 2차 사본이 됨 | Medium | P0 | 허용목록 밖 tag 키 거부, 실제 계측 호출이 원문·userId를 넘기지 않음을 전수 확인 |
| 운영자 경로 중 일부가 감사 기록 없이 통과 | 감사 이력의 부재를 해석할 수 없게 됨 | High | P0 | 배선 대상 경로 전부에 대해 행위 1건당 감사 1행 생성 확인 |
| 감사 기록이 별도 트랜잭션이라 결정만 커밋됨 | 결정은 남고 근거는 사라짐 | Medium | P0 | 감사 저장 실패 시 결정도 롤백되는지 확인 |
| 확인 항목이 비었는데 게이트가 열림 | 법률 검토 전 콘텐츠 외부 전송 | Low | P0 | 항목 누락·부분 충족 각각에서 기동 실패 확인 |
| `reason` 필수화가 기존 운영자 API를 깨뜨림 | 백오피스 호출 실패 | High | P1 | 기존 통합 테스트 갱신 후 통과, `reason` 없는 요청이 400인지 확인 |
| actuator 추가로 관리 endpoint가 외부 노출 | 운영 정보 유출 | Medium | P0 | 노출 설정이 비어 있고 실제 요청이 차단되는지 확인 |
| V20이 기존 스키마를 깨뜨림 | 전면 장애 | Low | P0 | 마이그레이션 적용과 기존 테이블 무변경 확인 |
| 지표 계측이 판정 경로의 예외를 새로 만듦 | 관측 때문에 본 기능이 실패 | Medium | P1 | 계측이 던지는 예외가 판정 결과를 바꾸지 않음을 확인 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| ...-UNIT-001 | 허용목록 밖 tag 키 | `FilteringMetrics` tag 생성 | 거부한다 (`INV-CMP-001`) | P0 | Feature executor |
| ...-UNIT-002 | 허용목록 안 tag 키와 값 | tag 생성 | 통과시킨다 | P0 | Feature executor |
| ...-UNIT-003 | 값이 비었거나 과도하게 긴 tag | tag 생성 | 거부해 cardinality 폭증과 원문 유입을 막는다 | P1 | Feature executor |
| ...-UNIT-004 | 필수값이 빠진 감사 기록 | `OperatorActionAudit` 생성 | actor·actionType·reason·policyVersion·시간을 각각 필수로 검증한다 | P0 | Feature executor |
| ...-UNIT-005 | 공백뿐인 `reason` | `OperatorActionAudit` 생성 | 거부한다 — 형식만 채운 근거를 허용하지 않는다 | P0 | Feature executor |
| ...-UNIT-006 | 길이 상한을 넘는 `reasonText` | 생성 | 거부한다 | P2 | Feature executor |
| ...-UNIT-007 | 확인 항목이 전부 채워진 설정 | `FilteringProductionGate` 평가 | 활성화를 허용한다 | P0 | Feature executor |
| ...-UNIT-008 | 확인 항목 중 하나가 빈 설정 | 평가 | 활성화를 거부한다 (`INV-CMP-005`) | P0 | Feature executor |
| ...-UNIT-009 | 확인 항목이 전부 비었지만 활성화 요청 없음 | 평가 | 거부하지 않는다 — 게이트는 활성화 시도에만 개입한다 | P1 | Feature executor |
| ...-UNIT-010 | 판정 결과와 release·model | 판정 분포 계측 | release와 actual model을 tag로 분리해 기록한다 | P1 | Feature executor |
| ...-UNIT-011 | logical attempt와 실제 공급자 호출 | 두 카운터 계측 | 각각 독립적으로 증가한다 | P1 | Feature executor |
| ...-UNIT-012 | 계측 대상 registry가 예외를 던지는 상황 | 판정 경로 실행 | 판정 결과가 바뀌지 않는다 | P1 | Feature executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| ...-INT-001 | Flyway, PostgreSQL | V20까지 적용 | `operator_action_audit` 컬럼·제약·인덱스 조회 | 신규 테이블이 존재하고 기존 필터링 테이블은 변경되지 않았다 | 트랜잭션 롤백 |
| ...-INT-002 | release registry, 감사 | 승격 가능한 release | `promote`를 `reason`과 함께 호출 | release 상태 전이와 감사 1행이 같은 트랜잭션으로 남는다 | 명시적 삭제 |
| ...-INT-003 | release registry | 같은 구성 | `reason` 없이 호출 | 400으로 거절되고 상태 전이도 감사도 없다 | 트랜잭션 롤백 |
| ...-INT-004 | 감사 배선 전 경로 | 각 운영자 endpoint 1회씩 | `offline-evaluation`/`shadow`/`canary`/`promote`/`rollback`/manual `decide`/appeal `decide`/appeal `extend`/`confirm-permanent` | 경로마다 정확히 1행, `action_type`이 서로 다르다 | 명시적 삭제 |
| ...-INT-005 | 감사 저장 실패 주입 | 감사 저장이 실패하는 상태 | 운영자 결정 호출 | 결정도 함께 롤백되어 결정만 남지 않는다 | 트랜잭션 롤백 |
| ...-INT-006 | 감사 테이블 | 기록된 행 | UPDATE·DELETE 시도 | append-only 계약을 지킨다 | 트랜잭션 롤백 |
| ...-INT-007 | Spring context, actuator | 기본 설정 | `/actuator/**` 요청 | 노출되지 않는다 | 없음 |
| ...-INT-008 | Spring context, 게이트 | 확인 항목 누락 + 활성화 요청 | 컨텍스트 기동 | 기동이 실패한다 (`INV-CMP-005`) | 없음 |
| ...-INT-009 | Spring context, 게이트 | 확인 항목 충족 + 활성화 요청 | 컨텍스트 기동 | 정상 기동한다 | 없음 |
| ...-INT-010 | moderation pipeline, MeterRegistry | 실제 판정 1회 | 지표 조회 | latency·판정 분포가 기록되고 tag 값에 원문·userId가 없다 | 트랜잭션 롤백 |
| ...-INT-011 | 전체 metric 이름·tag | 컨텍스트 기동 후 registry 전수 조회 | 모든 meter의 tag 키 확인 | 전부 허용목록 안이다 (`INV-CMP-002`) | 없음 |
| ...-INT-012 | 기존 운영자 API 통합 테스트 | `reason` 추가 후 | 기존 시나리오 재실행 | 회귀 없이 통과한다 | 기존과 동일 |
| ...-INT-017 | 감사 테이블 CHECK 제약 | V20 적용 후 | 정의되지 않은 `action_type`·`target_type`, 공백 `reason_code` 삽입 | DB가 셋 다 거절한다 | 트랜잭션 롤백 |

> INT-017은 코드 리뷰에서 드러난 구멍을 메우려고 추가했다. 애플리케이션 검증을
> 우회해 들어온 값이 있으면 읽을 때 `valueOf`가 실패해 감사 조회 자체가 막히므로,
> 쓰기 시점에 DB가 거절하는지 확인한다.

## 7. Cross-cutting scenarios

### Database and transactions

- 감사 기록은 결정과 같은 트랜잭션이다. 별도 트랜잭션이면 결정만 커밋되고
  근거가 사라지는 조합이 생긴다(INT-002, INT-005).
- `operator_action_audit`은 append-only다. 애플리케이션에 UPDATE·DELETE 경로를
  두지 않고, 그 사실을 INT-006이 확인한다.
- V20은 신규 테이블 생성만 한다. 기존 필터링 테이블을 건드리지 않으므로
  기존 행 보정 문제가 없다.

### Concurrency and idempotency

- 감사 기록은 append-only라 경합해도 서로를 덮어쓰지 않는다. 유일성 제약을
  두지 않는 이유이기도 하다 — 같은 운영자가 같은 행위를 두 번 하면 두 행이
  남는 것이 맞다.
- metric 계측은 Micrometer의 스레드 안전 계약에 의존하며 별도 동시성
  시나리오를 두지 않는다.

### External APIs

- 외부 호출을 새로 만들지 않는다. exporter를 붙이지 않으므로 지표는 프로세스
  안에만 머문다.
- 기존 moderation 공급자 호출 경로에 계측만 덧붙인다. UNIT-012가 계측 실패가
  판정을 바꾸지 못함을 확인한다.

### Failure recovery and reconciliation

- 게이트는 fail-closed다. 확인 항목을 읽을 수 없거나 일부만 채워졌으면
  활성화를 거부한다(INT-008).
- 감사 저장 실패는 결정 전체를 롤백한다. 운영자는 실패를 인지하고 재시도하며,
  "기록 없이 반영된 결정"이 생기지 않는다.

## 8. Test data and isolation

- Fixtures: 기존 `ManualReviewPriorityIntegrationTest`·`AppealCaseIntegrationTest`의
  release → job → decision → case 생성 흐름과 운영자 로그인 헬퍼를 재사용한다.
- Database isolation: Testcontainers PostgreSQL. 기본은 `@Transactional`
  롤백이며, 커밋이 필요한 감사 배선 시나리오만 명시적으로 삭제한다.
- Clock/randomness: 고정 `Clock`을 주입한다.
- External API doubles: moderation 공급자는 기존 테스트의 스텁 pipeline을 쓴다.
- 게이트 시나리오는 `@SpringBootTest` 프로퍼티로 확인 항목을 조작하며, 실제
  운영 값은 쓰지 않는다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Feature executor | `V20__add_operator_action_audit.sql` | INT-001 | `./gradlew integrationTest --tests '*FlywayMigration*'` |
| 2 | Feature executor | `filtering/domain/{OperatorActionAudit,OperatorActionType}.java`, repository와 JDBC 구현 | UNIT-004~006, INT-006 | `./gradlew test integrationTest --tests '*OperatorAction*'` |
| 3 | Feature executor | `filtering/audit/OperatorActionAuditRecorder.java`와 각 운영자 서비스 배선 | INT-002~005 | `./gradlew integrationTest --tests '*Audit*'` |
| 4 | Feature executor | `filtering/web/**` 요청 DTO의 `reason` 추가, `docs/api/openapi.json` | INT-003, INT-012 | `./harness pr-ready --project-tests` |
| 5 | Feature executor | `filtering/observability/FilteringMetrics.java`와 tag 가드, `build.gradle` | UNIT-001~003, UNIT-010~012, INT-010, INT-011 | `./gradlew test --tests '*Metrics*'` |
| 6 | Feature executor | `filtering/gate/FilteringProductionGate.java`, 설정, `docs/` 체크리스트 | UNIT-007~009, INT-007~009 | `./gradlew integrationTest --tests '*Gate*'` |
| 7 | Feature executor | 기존 테스트 갱신과 `#113` 이연 주석 정정 | INT-012 | 전체 스위트 |

파일 단위로 소유를 분리했고 단일 실행자가 순서대로 진행하므로 충돌이 없다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer: `@tkv00`
- Decision: Approved — 계획 전체와 `TASK.md`의 Design decisions 7개 판단을 함께 승인했다.
- Approved at: `2026-08-18`
