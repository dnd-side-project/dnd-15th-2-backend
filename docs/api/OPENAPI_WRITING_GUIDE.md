# OpenAPI 설명 작성 가이드

이 문서는 `*ApiSpec` 인터페이스에 적는 OpenAPI 문서 애노테이션의 문장 기준이다.
Codex와 Claude가 같은 기준으로 검토하도록 단일 원본으로 둔다. 두 도구의 스킬
(`.claude/skills/harness-api-docs/`, `.agents/skills/harness-api-docs/`)은 이 문서를
참조하며 본문을 복제하지 않는다.

애노테이션을 어디에 두는지(컨트롤러 vs `*ApiSpec`), 커스터마이저가 이미 채우는
공통 규칙은 `docs/api-response.md` §5를 따른다. 이 문서는 **무엇을 어떻게 쓰는가**만
다룬다.

오류 코드 목록과 예외 사용 규칙은 `docs/error-codes.md`를 따른다.

## 1. 문장 종결

`합니다`체로 통일한다. 기존 24개 `*ApiSpec` 중 18개가 이미 이 기준을 따른다.

| 종결 | 사용 |
| --- | --- |
| `~합니다`, `~됩니다`, `~입니다` | 사용 |
| `~한다`, `~된다`, `~이다` | 사용하지 않음 |

## 2. `summary`

기본은 쉬운 명사구다. 이미 익숙한 단어로만 이뤄져 있다면 명사를 이어 붙여도 된다.

```text
알림함 목록 조회        ○ (알림함·목록·조회 모두 익숙한 단어)
검사 설정 목록 보기      ○
```

명사구가 낯선 단어를 쌓으면 오히려 읽기 어려워진다. 이럴 때만 `-하기` 동사구를
허용한다.

```text
필터링 정책 릴리스 승격     ✗ (낯선 명사 4개가 쌓였다)
이 설정을 실제로 적용하기     ○ (동사구로 풀었다)
```

**판단 기준: 개수가 아니라 낯선 단어를 쌓았는가다.** `release 승격`처럼 명사가
둘뿐이어도 둘 다 낯설면 동사구로 푼다. `알림함 목록 조회`처럼 셋이어도 전부
익숙하면 명사구를 유지한다.

전면적으로 동사구나 명사구 한쪽으로 통일하지 않는다. 도메인마다, 심지어 같은
`*ApiSpec` 안에서도 개념의 낯섦에 따라 섞어 쓴다.

## 3. `description` 문단 순서

다섯 문단을 이 순서로 쓴다. 해당 사항이 없는 문단은 생략한다.

1. **무엇을 하는가** — 사용자(또는 운영자) 관점에서 이 호출이 실제로 무엇을
   바꾸거나 무엇을 보여주는지.
2. **선행 조건·인증** — 어떤 인증이 필요한지, 대상이 어떤 상태여야 하는지.
3. **성공 시 결과** — 호출 후 무엇이 달라지는지. 상태 enum 이름만 나열하지 않는다.
4. **주요 실패 조건** — 사람이 실수로 마주칠 만한 조건. 오류 코드 자체는
   `@ApiResponse`에 적고 여기서는 반복하지 않는다.
5. **주의점** — 흔히 오해하는 지점. 예: "설정을 만드는 것만으로는 아무것도
   바뀌지 않는다."

## 4. 낯선 단어를 쌓지 않는다

명사를 이어 붙이는 건 모든 단어가 익숙할 때만 괜찮다. 낯선 단어가 하나라도
끼면 조사나 동사로 끊는다.

```text
필터링 정책 릴리스 승격        ✗
이 설정을 실제로 적용하기        ○
```

## 5. 분류 이름 대신 하는 일로 부른다

읽는 사람이 궁금한 건 "이게 어느 범주냐"가 아니라 "이게 뭘 하냐"다.

```text
moderation release candidate 생성   ✗ (범주 이름)
새 검사 설정 만들기                  ○ (하는 일)
```

## 6. 용어를 바꿀 때는 코드로 확인한다

번역하거나 쉬운 말로 바꾸기 전에 실제 코드(서비스, 도메인, DTO)를 읽고 그 개념이
정확히 무엇을 가리키는지 확인한다. 이름만 쉬워지고 뜻이 달라지면 안 된다.

```text
normalizationRef   →  글자를 다듬는 규칙을 가리키는 값
                       (TextNormalizer.normalize()의 두 번째 인자 확인 후 결정)
```

## 7. 괄호로 맥락 보강

쉬운 말로 풀면 정확한 개념이 흐려질 때만 괄호를 쓴다. 세 경우로 제한한다.

| 경우 | 형식 | 예 |
| --- | --- | --- |
| 상태를 바꾸는 API의 결과 상태값 | `(→ STATUS_VALUE)` | `이 설정을 실제로 적용하기 (→ PROMOTED)` |
| 팀이 영어로 부르는 개념. 처음 나오는 곳에 한 번만 | `(원어)` | `평가를 마쳤다고 표시하기 (offline evaluation)` |
| 오류 응답의 오류 코드 | `(코드)` | `해당 릴리스를 찾을 수 없습니다. (FLT-DOM-005)` |

규칙:

- **붙일 값이 없으면 비워 둔다.** 빈 괄호를 채우려고 말을 지어내지 않는다.
  두 조회 API(`findAll`, `find`)처럼 상태 전이도 팀 용어도 없으면 괄호를 쓰지 않는다.
- **한 줄에 괄호는 하나만.** `summary`에 하나 썼으면 같은 줄에 더 쌓지 않는다.
- 상태값 괄호는 응답 본문의 `status` 필드와 반드시 일치해야 한다. 일치 여부는
  §9의 6점 체크리스트로 확인한다.

## 8. 금지 문장

- **내부 정책 코드·상태값만으로 의미를 설명하지 않는다.**
  `REVOKED 줄은 제외됩니다` ✗ → `신고로 내려간 알림은 목록에 나오지 않습니다` ○
- **미번역 내부 불변식 ID를 노출하지 않는다.** `(INV-REL-007)` 같은 표기는
  API 소비자에게 의미가 없다. 근거가 필요하면 제품 설계 문서에 남기고 문장으로
  풀어 쓴다.
- **DTO에 없는 사실을 지어내지 않는다.** 쉬운 말로 바꾸는 과정에서 요청·응답
  필드에 실제로 없는 내용을 있는 것처럼 쓰기 쉽다. 다음이 실제로 있었던 오류다.

  ```text
  markOfflineEvaluated의 요청 본문은 OperatorReasonRequest(사유 코드·사유 문구)뿐이고
  평가 점수 필드가 없다. 그런데 "성능 검사 결과 등록하기"라고 쓰면 결과 데이터를
  보내는 것처럼 읽힌다. 실제로는 "평가를 마쳤다"는 사실만 기록한다.
  ```

  **쉽게 쓰다가 사실을 바꾸면 어려운 문장보다 나쁘다.** 쉬운 말로 바꿨으면 반드시
  요청·응답 DTO를 다시 열어 대조한다.
- `@Schema(example = ...)`에 실제 비밀번호, 토큰, 계정 식별자, 세션 ID를 쓰지
  않는다. 스펙 산출물이 저장소에 커밋되고 통합 테스트가 금지 문자열을 검사한다.

## 9. 6점 대조 체크리스트

도메인 하나를 검토할 때 엔드포인트마다 여섯 가지를 코드와 대조한다. 추측하지
않는다 — 각 항목의 근거는 반드시 실제 코드다.

| # | 대조 | 확인 대상 | 근거를 찾을 곳 |
| --- | --- | --- | --- |
| 1 | Controller ↔ ApiSpec | 매핑·파라미터 누락 | `implements XxxApiSpec` 메서드 수와 `@Xxx Mapping` 개수 일치 |
| 2 | ApiSpec ↔ DTO | 필드별 `@Schema(description)` | request/response record 필드 전수 확인 |
| 3 | ApiSpec ↔ Service | 실제로 낼 수 있는 오류 응답 | 서비스 코드의 `throw new XxxException(...)` 전수 확인 |
| 4 | ApiSpec ↔ `docs/error-codes.md` | 오류 코드·HTTP 상태 일치 | 코드 표 대조 |
| 5 | ApiSpec ↔ SecurityConfiguration | 인증 스킴 정확성 | `permitAll` 경로에는 `@SecurityRequirement`를 붙이지 않는다 |
| 6 | ApiSpec ↔ `docs/api/openapi.json` | 문서 애노테이션이 실제 산출물에 반영됐는가 | `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"` 재생성 후 diff |

1·5는 형식 대조이고, 2·3·4는 사실 대조다. **3을 건너뛰고 2만으로 오류 응답을
채우면 추측이 된다.** 서비스가 실제로 던지는 예외를 먼저 확인한 뒤 DTO와
오류 코드 표로 교차 검증한다.

체크리스트 결과는 `templates/api-docs-review.md` 양식에 기록한다.

## 10. 실행 절차

도메인 담당자가 Codex 또는 Claude 중 무엇을 쓰든 같은 절차를 따른다.

1. `.claude/skills/harness-api-docs/`(Claude) 또는
   `.agents/skills/harness-api-docs/`(Codex)의 `review` 모드를 실행한다.
2. 대상 도메인의 `*ApiSpec`, 컨트롤러, DTO, 서비스를 읽고 §9의 6점을 대조한다.
3. §1~§8 기준으로 문장을 다시 쓴 제안을 `templates/api-docs-review.md`에 기록한다.
   `review` 모드는 `*ApiSpec`을 직접 수정하지 않는다.
4. 제안을 검토하고 승인되면 도메인 담당자가 직접 `*ApiSpec`을 수정한다
   (`#189`의 각 도메인 항목).
5. 수정 후 `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`로
   스펙을 재생성하고 `docs/api/openapi.json` diff를 확인한다.

## 11. 예시 — before/after

`FilterReleaseApiSpec.promote()`(실제 저장소 코드 기준):

**Before**

```java
@Operation(
	summary = "release 승격",
	description = """
		CANARY를 통과한 candidate를 명시적으로 승격한다. 기존에 PROMOTED인 release가 있으면
		이 요청과 같은 트랜잭션에서 ROLLED_BACK으로 내린다. 이 endpoint를 호출하지 않으면
		어떤 release도 자동으로 승격되지 않는다(INV-REL-001, INV-REL-008).""")
@ApiResponses({
	@ApiResponse(responseCode = "200", description = "PROMOTED로 전이했습니다."),
	@ApiResponse(responseCode = "409", description = "CANARY 상태가 아니어서 승격할 수 없습니다. (FLT-DOM-004)")
})
```

**After**

```java
@Operation(
	summary = "이 설정을 실제로 적용하기 (→ PROMOTED)",
	description = """
		사용자가 쓴 답변과 닉네임을 검사할 때, 지금부터 이 설정을 쓰게 합니다.

		운영자 로그인이 필요합니다. 마지막 점검 단계(CANARY)까지 간 설정만 적용할 수 있습니다.
		왜 적용하는지 사유를 함께 보내야 합니다. 나중에 누가 왜 바꿨는지 확인해야 해서
		서버가 대신 채워주지 않습니다.

		지금 쓰고 있던 설정이 있으면 같이 내립니다. 두 설정이 동시에 쓰이는 순간은 없습니다.

		설정을 만들거나 점검 단계를 올리는 것만으로는 아무것도 바뀌지 않습니다.
		이 API를 불러야 그때 바뀝니다.""")
@ApiResponses({
	@ApiResponse(responseCode = "200", description = "이제부터 이 설정으로 검사합니다."),
	@ApiResponse(responseCode = "401", description = "운영자 로그인이 안 되어 있거나 로그인이 풀렸습니다."),
	@ApiResponse(responseCode = "403", description = "운영자 권한이 없는 계정입니다."),
	@ApiResponse(responseCode = "404", description = "그런 설정이 없습니다. (FLT-DOM-005)"),
	@ApiResponse(responseCode = "409", description = "마지막 점검 단계를 지나지 않았거나 이미 쓰고 있는 설정입니다. (FLT-DOM-004)")
})
```

바뀐 점: 종결어미(`한다`→`합니다`), 낯선 명사구를 동사구로 정리, 내부 불변식 ID
제거, 서비스 코드(`find(releaseId)` → `RELEASE_NOT_FOUND`)를 근거로 누락된 404 추가,
`demoteCurrentlyPromoted()`의 두 번째 409 경로를 반영해 409 설명 보강.

이 예시를 포함해 `FilterReleaseApiSpec` 8개 엔드포인트 전체에 §1~§9 절차를
시험 적용해 흐름이 실제로 작동함을 확인했다(GitHub Issue #190). 도메인별 실제
검토 결과는 `templates/api-docs-review.md`를 복사해 각 담당자가
`docs/reports/gh-<ISSUE>-API-DOCS-REVIEW-<DOMAIN>.md`로 만든다.
