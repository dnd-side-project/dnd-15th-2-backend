# Test Report: TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS

> Created at: `2026-10-07T11:31:47+09:00`
> GitHub Issue: `#317`
> Branch: `fix/gh-317-nickname-invisible-chars`
> Commit: base `aae33fa`에 이번 변경을 더한 작업 트리(커밋 전)

## 1. Executive summary

- Result: `PASS`. 승인된 계획의 단위 10개, 통합 4개 시나리오가 모두 통과했다. `./harness test-run`의 전체 단위 1215건(skipped 2)과
  통합 788건이 실패 없이 끝났다.
- Tested scope:
  - 닉네임 정규화 규칙(NFC, Cc·Cf 제거, 이모지 사이 ZWJ 보존, 유니코드 공백 축소, 앞뒤 제거)과 NFKC 미적용 경계
  - 정규화 후 빈 값 `ACC-VAL-002`, 50자 초과 `ACC-VAL-003`과 정규화 뒤 길이 계산
  - 새 입력 경로의 정규화 저장과 `restore`의 저장값 보존(D1)
  - 중복 검사·moderation 입력·저장값이 같은 정규화 값이고, 빈 값·길이 초과는 DB 조회와 moderation 전에 거절
  - PostgreSQL 유일 인덱스가 정규화한 저장값을 막는 것, HTTP 400 응답 코드, 이전 규칙 행의 복원
- Unverified scope:
  - 실제 OpenAI moderation 호출. 테스트는 `NicknameModerationChecker` 테스트 대역을 쓴다.
  - 운영·개발 DB에 이미 있는 보이지 않는 문자 닉네임의 수. Issue 제외 범위다(7절).
  - iOS·Android 클라이언트가 실제로 보내는 정규화 형태(NFC/NFD). 서버는 둘 다 NFC로 맞춘다.
- Release recommendation: 병합할 수 있다. #318과 `NicknameRegistrationService`를 함께 건드리므로 먼저 머지된 쪽에 맞춰 rebase한다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| OS | Windows 11 Enterprise, Git Bash |
| Java | OpenJDK 21.0.12 (Microsoft build) |
| Spring Boot | 3.5.16 |
| Database | Testcontainers `postgis/postgis` (Docker Desktop) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./harness test-run --id TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS` | PASS | 단위 1215(skipped 2), 통합 788, 실패 0 | 16m 30s | skipped 2건은 Windows에서 심볼릭 링크를 만들 수 없어 건너뛰는 기존 `RepoMapToolTest` 2건이다 |
| 관련 단위 5개 클래스 | PASS | 76 | — | `AccountNicknameNormalizationTest` 29, `NicknameRegistrationServiceTest` 18, `DeviceRegistrationServiceTest` 13, `AccountTest` 9, `AccountNicknameChangeTest` 7 |
| 관련 통합 4개 클래스 | PASS | 42 | 1m 2s | `NicknameDuplicateModerationIntegrationTest` 10, `NicknameChangeLimitIntegrationTest` 6, `OpenApiSpecificationIntegrationTest` 13, `DeviceAuthIntegrationTest` 13 |
| `./harness check` | PASS | — | — | JUnit 정책, 컨벤션, workflow, label, Husky 검사 |
| `./gradlew javaConventionCheck` | PASS | — | — | 변경한 `@Service`(`NicknameRegistrationService`) 포함 |
| `./harness pr-ready --project-tests` | PASS | 단위 1215(skipped 2), 통합 788, 실패 0 | — | 보고서를 쓴 뒤 최종 트리에서 실행했다. secret preflight, `./harness check`, `./gradlew check`(Checkstyle, Spotless, 컨벤션, 단위, 통합)를 포함한다. 소스 입력이 바로 앞 `test-run`과 같아 Gradle `test`·`integrationTest`는 그 실행 결과를 재사용했다(UP-TO-DATE) |
| `npm run hooks:validate`, `git diff --check` | PASS | — | — | |

`docs/api/openapi.json`은 `OpenApiSpecificationIntegrationTest`로 다시 생성했다. 요청 스키마 설명 2곳과 닉네임 변경 400 설명
1곳만 바뀌었다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `AccountNicknameNormalizationTest.removesInvisibleCharactersAndOuterUnicodeSpaces` | 8개 입력 |
| UNIT-002 | PASS | `AccountNicknameNormalizationTest.collapsesInnerWhitespaceRuns` | 3개 입력 |
| UNIT-003 | PASS | `AccountNicknameNormalizationTest.composesWithNfcButKeepsCompatibilityCharacters` | 분해 자모 조합, 호환 자모·전각 영문 유지 |
| UNIT-004 | PASS | `AccountNicknameNormalizationTest.rejectsNicknameThatBecomesEmpty`, `countsLengthAfterNormalization` | 빈 값 4개 입력, 51자 거절, 50자 + zero-width 10개 통과 |
| UNIT-005 | PASS | `AccountNicknameNormalizationTest.newInputPathsStoreNormalizedNickname` | 네 경로, 멱등성, null 유지 |
| UNIT-006 | PASS | `AccountNicknameNormalizationTest.restoreKeepsStoredNickname` | 보이지 않는 문자만 있는 저장값 포함 |
| UNIT-007 | PASS | `NicknameRegistrationServiceTest.usesOneNormalizedNicknameForDuplicateCheckModerationAndPersist` | |
| UNIT-008 | PASS | `NicknameRegistrationServiceTest.rejectsNicknameThatBecomesEmptyBeforeDuplicateCheckAndModeration`, `rejectsTooLongNicknameBeforeDuplicateCheckAndModeration` | 중복 검사·moderation·저장 0회 |
| UNIT-009 | PASS | `DeviceRegistrationServiceTest.rejectsRegistrationWhenNicknameBecomesEmpty`, `storesNormalizedNicknameOnRegistration` | `DeviceRegistrationService`는 코드 변경 없음 |
| UNIT-010 | PASS | `AccountNicknameNormalizationTest.keepsJoinersInsideEmojiSequences`, `removesJoinersOutsideEmojiSequences` | 보존 3개, 제거·연속 7개 |
| INT-001 | PASS | `NicknameDuplicateModerationIntegrationTest.rejectsNicknamesThatDifferOnlyByInvisibleCharacters` | 등록·변경 모두 409 코드, 행 수·기존 값 불변 |
| INT-002 | PASS | `NicknameDuplicateModerationIntegrationTest.storesNormalizedNicknameThatTheUniqueIndexProtects` | 직접 insert도 `uq_user_account_nickname_ci` 위반 |
| INT-003 | PASS | `NicknameChangeLimitIntegrationTest.rejectsNicknameThatBecomesEmptyOverHttp` | HTTP 400 `ACC-VAL-002`, moderation 0회 |
| INT-004 | PASS | `NicknameDuplicateModerationIntegrationTest.restoresLegacyNicknameRowsWithoutNormalizing` | |

## 5. Failures and diagnostics

- 테스트 실패는 없었다.
- 작성 중 환경 문제가 하나 있었다. 편집 도구로 쓴 `\uXXXX` 이스케이프가 파일에 실제 보이지 않는 문자로 저장됐다. 백슬래시를
  두 번 쓴 정규식 이스케이프(`\\u200D`)는 남았다. 테스트 문자열을 모두 코드 포인트 상수(`Character.toString(0x200B)` 등)로
  바꿨다. 이렇게 하면 소스에 이스케이프도 보이지 않는 문자도 없다. 변경 파일 전체에서 Cf·Cc(탭·개행 제외)·Zs(ASCII 공백 제외)·
  변형 선택자가 0개인 것을 스크립트로 확인했다. `TASK.md`, 테스트 계획, Issue 본문도 `\uXXXX` 표기로 되돌렸다.
- 첫 통합 테스트 시도는 Docker Desktop이 꺼져 있어 실행하지 못했다. 사용자가 켠 뒤 실행했다.

## 6. Potential issues

### Application code

- `Account.restore`와 상태 전이 복사본은 trim·길이 검사만 하므로, 이전 규칙으로 저장된 보이지 않는 문자 닉네임은 응답에도
  그대로 나간다. 해당 사용자가 닉네임을 다시 바꾸면 정규화된다.
- `changeNickname(null)`은 여전히 닉네임을 null로 만든다. 웹 경로는 `@NotBlank`로 막히고 이번 범위의 동작 변경은 아니다.
- `ChangeNicknameRequest`의 `@NotBlank`는 `trim()` 기준이라 `\u200B`를 통과시킨다. 그 값은 서비스의 정규화 검증에서
  `ACC-VAL-002`가 된다. 요청 검증 오류 코드가 아니라 계정 오류 코드로 응답한다.

### Infrastructure and resource limits

- 정규화는 요청마다 문자열 몇 번 순회하는 수준이고, 입력 길이가 요청 본문 한도 안에 있다. 추가 자원 위험은 찾지 못했다.

### Database and migrations

- 스키마 변경이 없다. 유일 인덱스는 정규화한 저장값에 그대로 걸린다(INT-002).
- 기존 행은 바꾸지 않았다. 새 닉네임 `바람`과 기존 행 `바람\u200B`는 여전히 서로 중복으로 보지 않는다(7절).

### Concurrency and idempotency

- 정규화는 순수 함수이고 두 번 적용해도 같다(UNIT-005). 동시 등록 경합은 #168의 기존 INT-003과 인덱스가 계속 막는다.
- 정규화 거절도 시도 한도 1회로 센다(D3). 주기 안이면 정규화보다 429 `ACC-APP-004`가 먼저 나온다.

### Transactions and event ordering

- 변경 경로의 정규화 검증은 쓰기 트랜잭션 밖, 중복 조회 전에 끝난다. 등록 경로에서는 기존 등록 트랜잭션 안에서 예외로 끝나
  계정·자격증명이 함께 롤백된다(UNIT-009).

### External APIs

- moderation 입력이 정규화 값이 됐다(UNIT-007). moderation 파이프라인은 NFKC와 ZWJ 제거를 다시 적용하므로, 이모지 결합
  닉네임은 낱개 이모지로 판정된다. 판정 결과에 영향은 없다고 본다.
- 빈 값·길이 초과 닉네임은 moderation을 부르지 않는다(UNIT-008, INT-003). 50자 초과 닉네임이 OpenAI를 부른 뒤 거절되던
  비용도 사라졌다.

### Failure recovery and reconciliation

- 보이지 않는 문자만 있는 이전 행도 읽기에서 실패하지 않는다(UNIT-006, INT-004). 복구 절차가 필요한 상태를 만들지 않는다.

## 7. Regression and residual risk

- 받아들인 위험(D2): 결합 이모지로 등록되지 않은 조합(🔥 + ZWJ + 🔥)은 🔥🔥과 똑같이 보이지만 저장값이 달라 중복 검사를 통과한다.
  이모지 두 개가 붙은 닉네임에 한정된다. 막으려면 중복 검사와 유일 인덱스를 ZWJ를 뺀 값으로 비교해야 하고 마이그레이션이 필요하다.
- 받아들인 위험: 지역 깃발 태그 문자(U+E0020~U+E007F, Cf)를 지워 잉글랜드·스코틀랜드·웨일스 깃발이 검은 깃발로 저장된다.
- 남은 위험: 이 변경 전에 저장된 보이지 않는 문자 닉네임은 그대로 남고, 새 닉네임과 중복으로 판정되지 않는다. 기존 행 정규화
  마이그레이션은 Issue 제외 범위다. 운영 데이터가 생기기 전이면 위험이 작다.
- 남은 위험: 한글 채움 문자(U+3164) 같은 Lo 투명 문자는 막지 않는다. Issue 제외 범위다.
- 회귀: 전체 단위·통합 테스트가 통과했다. 기존 `trimsNicknameWhitespace`(앞뒤 공백 제거) 동작도 유지된다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-317-TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS.md`
- CI run: PR 생성 후 GitHub Actions `check`
- Related ADR: 없음
- PR: 생성 전. 선행 관계에 #318(게이트 예외 분류)을 적는다.

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 — 7절의 받아들인 위험과 기존 행 정리는 후속 Issue 여부를 사용자가 정한다
- [ ] 실행 결과와 PR 설명이 일치함 — PR 생성 시 확인
