# Test Plan: TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS

> Created at: `2026-10-07T10:43:25+09:00`
> GitHub Issue: `#317`
> Status: Approved

## 1. Objective

새로 입력받는 닉네임이 저장, 중복 검사, moderation 입력에서 같은 정규화 값 하나를 쓰는지 검증한다.

실패하면 zero-width 문자나 전각 공백을 넣어 다른 사용자와 똑같아 보이는 닉네임을 만들 수 있다. 사칭 신고(F08)의
근거가 흐려진다. 반대로 정규화를 저장된 행 복원에도 적용하면, 이전 규칙으로 저장된 보이지 않는 문자만 있는 닉네임
계정을 읽을 수 없게 되어 인증과 프로필 조회가 깨진다.

## 2. Scope

### Included

- 닉네임 정규화 규칙: NFC → Cc·Cf 제거(공백류 제외, 이모지 사이 ZWJ는 남김: D2) → 유니코드 공백을 한 칸으로 축소 → 앞뒤 제거
- 이모지 사이 ZWJ 판정: 앞쪽이 Extended_Pictographic(뒤따르는 변형 선택자·결합 부호·피부색 수식자는 건너뜀)이고
  바로 다음 문자가 Extended_Pictographic일 때만 U+200D를 남긴다(UAX #29 GB11과 같은 기준). ZWNJ는 항상 지운다
- 정규화 후 빈 값 `ACC-VAL-002`, 50자 초과 `ACC-VAL-003`(둘 다 400)과 길이를 정규화 뒤에 세는 규칙
- NFKC를 적용하지 않는다는 경계(호환 자모·전각 영문 유지)
- 새 입력 경로(`createUser`, `createOperator`, `changeNickname`, `updateProfile`)의 정규화 저장
- `restore`가 저장된 값을 그대로 복원하는 읽기 호환(D1)
- `ensureAvailable`의 중복 검사·moderation 입력이 정규화 값이고, 빈 값·길이 초과는 DB 조회와 moderation 전에 거절
- 기기 등록 경로의 같은 동작
- PostgreSQL 유일 인덱스가 정규화 값 기준으로 중복을 막는지
- HTTP 경로의 400 응답 코드

### Excluded

- 기존 행 정규화 마이그레이션과 기존 보이지 않는 문자 닉네임과의 중복 판정
- NFKC·동형 문자(전각 영문, 키릴 문자 등) 중복 판정
- 한글 채움 문자(U+3164) 같은 Lo 투명 문자 차단
- moderation 게이트의 예외 분류(#318)

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #317 | `바람\u200B`, `바\u200B람`, `\u3000바람`이 `바람`으로 저장되고, `바람`이 있으면 409 |
| GitHub Issue #317 | 정규화 후 빈 값·50자 초과는 moderation 호출 없이 400 |
| GitHub Issue #317 | 응답 닉네임이 정규화한 값이다 |
| `UnicodeTextNormalizer` | moderation은 NFKC·Cc/Cf 제거·공백 축소 값으로 판정한다. 저장 규칙은 NFKC만 빼고 같다 |
| V21 | `uq_user_account_nickname_ci`는 `lower(nickname)`이고 삭제·NULL 행은 제외한다 |
| `TASK.md` / #315 D2 | 변경 순서는 시도 한도 → 계정 조회·주기 → 중복 → moderation → 저장. 정규화 검증은 중복 직전에 둔다 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 보이지 않는 문자만 다른 닉네임이 중복 검사·인덱스를 통과 | 사칭 | High(현재 재현) | P0 | UNIT-001, UNIT-007, INT-001, INT-002 |
| 정규화 후 빈 닉네임이 moderation까지 가서 503 또는 저장 | 잘못된 오류·투명 닉네임 저장 | High(현재 재현) | P0 | UNIT-004, UNIT-008, UNIT-009, INT-003 |
| `restore`에서 정규화해 이전 규칙의 저장 행이 읽기 실패 | 해당 계정 인증·조회 불가 | Medium(gate off 환경에서 저장 가능) | P0 | UNIT-006, INT-004 |
| NFKC를 저장에 적용해 호환 자모(ㄱ→ᄀ)가 깨짐 | 한글 표시 깨짐 | Medium | P1 | UNIT-003 |
| 길이를 정규화 전에 세어 보이지 않는 문자로 50자 제한이 흔들림 | 저장 실패(varchar 50) 또는 오거절 | Low | P1 | UNIT-004 |
| 50자 초과 닉네임이 moderation을 호출한 뒤 거절 | OpenAI 비용 | High(현재 재현) | P1 | UNIT-008 |
| ZWJ를 지워 결합 이모지(👨\u200D👩\u200D👧)가 낱개로 나뉘어 저장 | 표시가 입력과 달라짐 | Medium | P1 | UNIT-010 |
| 이모지 사이 ZWJ를 남겨 `바\u200D람` 같은 우회가 다시 열림 | 사칭 | Medium | P0 | UNIT-010(글자 옆 ZWJ 제거) |
| 결합 이모지로 등록되지 않은 조합(🔥+ZWJ+🔥)은 🔥🔥과 똑같이 보이는데 저장값이 다르다 | 이모지 두 개가 붙은 닉네임에 한정된 사칭 | Low | P2 | 받아들인 위험. 보고서에 기록(D2) |
| 지역 깃발 태그 문자(U+E0020~E007F, Cf)를 지워 🏴\uE0067\uE0062\uE0065\uE006E\uE0067\uE007F가 🏴로 바뀜 | 깃발 3종 표시 변경 | Low | P2 | 받아들인 위험. 보고서에 기록 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-UNIT-001 | `바람\u200B`, `바\u200B람`, `\uFEFF바람`, `바람\u2060`, `\u202E바람`, `\u3000바람`, `바람\u3000`, `\u00A0바람` | `Account.normalizeNickname` | 모두 `바람` | P0 | 실행 에이전트 |
| TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-UNIT-002 | `여름\u3000\u3000바람`, `여름\t바람`, `여름  \u00A0바람` | 정규화 | 모두 `여름 바람`(공백 한 칸) | P0 | 실행 에이전트 |
| TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-UNIT-003 | 분해된 자모 `바람`(U+1107 U+1161 U+1105 U+1161 U+11B7), `ㅋㅋ`(U+314B), `ＡＢＣ` | 정규화 | 첫 값은 완성형 `바람`(U+BC14 U+B78C), 나머지는 입력 그대로(NFKC 미적용) | P1 | 실행 에이전트 |
| TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-UNIT-004 | `\u200B`, `\uFEFF`, `\u3000`, `\u200B\u3000\u200B` / 보이는 글자 51자 / 보이는 글자 50자 + `\u200B` 10개 | 정규화 | 빈 값은 `REQUIRED_VALUE_MISSING`, 51자는 `TEXT_TOO_LONG`, 50자 + 보이지 않는 문자는 통과 | P0 | 실행 에이전트 |
| TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-UNIT-005 | `바\u200B람` | `createUser`, `createOperator`, `changeNickname`, `updateProfile` | 모두 `getNickname()`이 `바람`. `createUser`의 null 닉네임은 계속 null | P0 | 실행 에이전트 |
| TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-UNIT-006 | 저장된 닉네임 `바람\u200B`, `\u200B` | `Account.restore` 뒤 `block()`·`withProfileImage()` | 예외 없이 저장값 그대로 유지(D1) | P0 | 실행 에이전트 |
| TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-UNIT-007 | 계정 1, moderation Allowed | `changeNickname(1, "\u3000바\u200B람 ")` | 중복 검사 입력·moderation 입력·저장값이 모두 `바람` | P0 | 실행 에이전트 |
| TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-UNIT-008 | 계정 1 | `changeNickname(1, "\u200B")`, `changeNickname(1, 51자)` | 각각 `REQUIRED_VALUE_MISSING`, `TEXT_TOO_LONG`. 중복 검사·moderation·저장 호출 0회 | P0 | 실행 에이전트 |
| TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-UNIT-009 | 등록 요청 | `register(..., "\u200B")` / `register(..., "바람\u200B")` | 첫 요청은 `REQUIRED_VALUE_MISSING`이고 계정·자격증명·moderation 0회. 둘째는 저장 닉네임 `바람` | P0 | 실행 에이전트 |
| TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-UNIT-010 | 남길 값: `👨\u200D👩\u200D👧`, `👩🏻\u200D💻`(피부색 수식자), `🏳\uFE0F\u200D🌈`(변형 선택자) / 지울 값: `바\u200D람`, `👨\u200D바람`, `바람\u200D👨`, `\u200D👨`, `👨\u200D`, `👨\u200C👩`(ZWNJ) / 연속: `👨\u200D\u200D👩` | 정규화 | 남길 값은 입력 그대로. 지울 값은 각각 `바람`, `👨바람`, `바람👨`, `👨`, `👨`, `👨👩`. 연속 ZWJ는 하나만 남아 `👨\u200D👩` | P0 | 실행 에이전트 |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-INT-001 | 등록·변경 서비스, PostgreSQL | `바람` 계정 등록 | 다른 설치가 `바람\u200B`로 등록하고, 다른 계정이 `\u3000바람`으로 변경 | 둘 다 `DUPLICATED_NICKNAME`, `user_account` 행 수와 기존 닉네임 불변 | `@BeforeEach` DELETE |
| TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-INT-002 | 변경 서비스, 유일 인덱스 | 계정 하나 | `\u3000여름\u200B바람 `으로 변경한 뒤 다른 설치가 `여름바람`으로 등록 | DB 원값 `여름바람`, 두 번째 등록은 `DUPLICATED_NICKNAME` | 동일 |
| TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-INT-003 | HTTP `PATCH /api/v1/users/me/nickname`, 전역 예외 처리 | 등록한 사용자 | 본문 닉네임 `\u200B` | 400 `ACC-VAL-002`, moderation 호출 0회, 닉네임 불변 | 동일 |
| TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-INT-004 | `JpaAccountRepository`·mapper | JDBC로 닉네임 `바람\u200B` 행 직접 insert | `findById` | 예외 없이 복원되고 닉네임은 저장값 그대로 | 동일 |

INT-001·002·004는 `NicknameDuplicateModerationIntegrationTest`, INT-003은 MockMvc가 있는
`NicknameChangeLimitIntegrationTest`에 추가한다. 둘 다 기존 컨텍스트를 그대로 써서 새 Spring 컨텍스트를 만들지 않는다.

## 7. Cross-cutting scenarios

### Database and transactions

- 스키마 변경이 없다. 유일 인덱스는 정규화한 저장값에 그대로 걸린다(INT-002).
- 정규화 검증은 `changeNickname`의 쓰기 트랜잭션 밖, 중복 조회 전에 끝난다. 등록 경로는 기존 트랜잭션 안에서
  예외로 끝나 계정·자격증명이 함께 롤백된다(UNIT-009).

### Concurrency and idempotency

- 정규화는 순수 함수이고 두 번 적용해도 같다(UNIT-005에서 `changeNickname` 결과를 다시 정규화해 확인).
- 같은 정규화 값의 동시 등록 경합은 기존 INT-003(#168) 경로와 인덱스가 막는다. 새 시나리오는 두지 않는다.

### External APIs

- moderation 입력이 정규화 값이 된다(UNIT-007). 파이프라인이 NFKC를 다시 적용해도 판정 대상 의미는 같다.
- 빈 값·길이 초과는 moderation을 부르지 않는다(UNIT-008, INT-003).

### Failure recovery and reconciliation

- 저장된 이전 규칙의 행은 바꾸지 않고 읽을 수 있어야 한다(UNIT-006, INT-004). 복구 절차가 필요한 상태를 만들지 않는다.

## 8. Test data and isolation

- Fixtures: 단위는 기존 `FakeAccountRepository`·`FakeNicknameModerationChecker`. 중복 검사 입력은 기존 `lastCheckedNickname`, moderation 입력은 `lastNickname` 필드를 추가해 기록한다
- Database isolation: 각 통합 테스트의 기존 `@BeforeEach` DELETE를 따른다
- Clock/randomness: 해당 없음. 시도 한도 카운터는 테스트마다 새 계정을 만들어 격리한다(기존 방식)
- External API doubles: 기존 mock `NicknameModerationChecker`
- Cleanup: 기존 `@BeforeEach`
- 유니코드 입력은 소스에 `\uXXXX` 이스케이프로만 쓴다. 보이지 않는 문자를 리터럴로 넣지 않는다

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | 실행 에이전트 | `src/main/java/com/dnd/qello/account/domain/Account.java`, `src/test/java/com/dnd/qello/account/domain/AccountNicknameNormalizationTest.java`(신규) | UNIT-001~006, UNIT-010 | `./gradlew test --tests "*AccountNicknameNormalizationTest" --tests "*AccountTest"` |
| 2 | 실행 에이전트 | `src/main/java/com/dnd/qello/account/service/NicknameRegistrationService.java`, `src/test/java/com/dnd/qello/account/service/NicknameRegistrationServiceTest.java`, `src/test/java/com/dnd/qello/auth/service/DeviceRegistrationServiceTest.java` | UNIT-007~009 | `./gradlew test --tests "*NicknameRegistrationServiceTest" --tests "*DeviceRegistrationServiceTest"` |
| 3 | 실행 에이전트 | `src/integrationTest/java/com/dnd/qello/NicknameDuplicateModerationIntegrationTest.java`, `src/integrationTest/java/com/dnd/qello/NicknameChangeLimitIntegrationTest.java` | INT-001~004 | `./gradlew integrationTest --tests "*NicknameDuplicateModerationIntegrationTest" --tests "*NicknameChangeLimitIntegrationTest"` |
| 4 | 실행 에이전트 | `ChangeNicknameRequest.java`, `DeviceRegistrationRequest.java`(스키마 설명), `AccountApiSpec.java`(400 설명), `docs/api/openapi.json`, 테스트 보고서 | - | `OpenApiSpecificationIntegrationTest`, `./harness check`, `./harness pr-ready --project-tests` |

`DeviceRegistrationService`는 코드 변경이 필요 없다. 보이지 않는 문자만 있는 값은 `isBlank()`를 통과해
`ensureAvailable`로 가고, 거기서 정규화 검증이 먼저 400을 낸다. UNIT-009로 확인한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

| ID | 결정 | 권장안 |
| --- | --- | --- |
| D1 | 정규화 적용 범위 | 새로 입력받는 값(`createUser`·`createOperator`·`changeNickname`·`updateProfile`)에만 적용한다. `restore`와 상태 전이 복사본은 저장값을 그대로 둔다 |
| D2 | ZWJ·ZWNJ | 이모지 사이 ZWJ만 남긴다(2절 판정 기준). ZWNJ와 그 밖의 ZWJ는 지운다. 결합 이모지로 등록되지 않은 조합의 사칭(🔥+ZWJ+🔥)은 받아들인다 |
| D3 | 정규화 거절과 시도 한도 | 정규화 400은 시도 한도·주기 검사 뒤에 나오므로 시도 1회로 센다(#315 D2와 같음) |

- Reviewer: 사용자
- Decision: D1 승인(2026-10-07). D2는 (b) 이모지 사이 ZWJ만 남기기로 사용자 결정(2026-10-07). D3는 사용자가 실행 에이전트에 위임했고 "센다"로 정했다. 계획 전체 승인
- Approved at: 2026-10-07T10:58:27+09:00
