# API Docs Review: Filtering/Appeal

> Created at: `2026-08-24T02:22:39+09:00`
> GitHub Issue: `#189`
> Target: `src/main/java/com/dnd/qello/filtering/web/*ApiSpec.java`
> 문장 기준: `docs/api/OPENAPI_WRITING_GUIDE.md`

이 문서는 Filtering/Appeal 도메인 16개 엔드포인트의 문장·오류·인증·스키마 검토
결과다. `harness-api-docs` 스킬의 `review` 모드 산출물이며, 담당자 승인 전에는
`*ApiSpec` 원본과 DTO를 수정하지 않는다.

담당자 승인 후 제안을 반영했으며, `docs/api/openapi.json`은 통합 테스트로 재생성했다
(반영 완료: `2026-08-24`).

## 1. Executive summary

- 대상 엔드포인트 수: 16개
  - `AppealApiSpec` 2개
  - `AppealCaseApiSpec` 3개
  - `FilterReleaseApiSpec` 8개
  - `ManualReviewCaseApiSpec` 2개
  - `SnapshotHealthApiSpec` 1개
- 발견된 문제:
  - 5문단 설명 순서와 `합니다`체를 모든 엔드포인트에 적용할 필요가 있다. 기존
    `FilterRelease` 7건, `ManualReviewCase` 3건, `SnapshotHealth` 1건은 `한다체`다.
  - 운영자 세션으로 보호되는 14개 엔드포인트에 401·403 응답이 직접 선언되지 않았다.
  - `FilterRelease` 상태 전환 5개 API는 서비스가 먼저 release를 조회하므로 발생 가능한
    404 응답이 빠져 있다.
  - `FilterRelease` 생성 응답의 400 설명은 실제 `FLT-VAL-003`, `FLT-VAL-004` 중
    `FLT-VAL-003`을 누락하고 존재하지 않는 `FLT-VAL-001`을 적었다.
  - DTO record 10개에 클래스 `@Schema(description)`가 없고, 58개 필드 중 56개에
    `@Schema(description)`가 없다. `OperatorReasonRequest`의 두 필드만 설명이 있다.
  - `FilteringErrorCode`의 FLT 코드가 `docs/error-codes.md`에 표로 정리되어 있지 않다.
    오류 코드의 HTTP 상태는 enum과 대조했으며 문서 표 추가는 별도 문서 작업으로 남긴다.
- 6점 대조 중 실행하지 못한 항목: 없음. 기준선과 반영 후 OpenAPI 통합 테스트를 모두 통과했다.

## 2. 6점 대조 결과

`docs/api/OPENAPI_WRITING_GUIDE.md` §9 기준으로 도메인 전체를 대조했다.

| # | 대조 | 결과 | 근거 |
| --- | --- | --- | --- |
| 1 | Controller ↔ ApiSpec | 이상 없음 | `AppealController` 2개, `AppealCaseController` 3개, `FilterReleaseController` 8개, `ManualReviewCaseController` 2개, `SnapshotHealthController` 1개 메서드가 각 `*ApiSpec` 매핑과 일치한다. |
| 2 | ApiSpec ↔ DTO | 보강 필요 | `AppealCaseResponse`, `AppealDecisionRequest`, `FileAppealRequest`, `ManualReviewCaseResponse`, `SnapshotHealthResponse`, `CreateFilterReleaseRequest`, `ExtendAppealExpiryRequest`, `ManualReviewDecisionRequest`, `FilterReleaseResponse`에 클래스·필드 설명이 없다. `OperatorReasonRequest`만 두 필드 설명이 있다. |
| 3 | ApiSpec ↔ Service | 응답 제안에 반영 | `FilterReleaseRegistryService`, `AppealCaseService`, `ManualReviewDecisionService`, `SnapshotHealthService`와 도메인 전이 메서드의 `FilteringException`을 전수 확인했다. release 조회 404, 상태 전이 409, appeal 접수 400·403·404·409, 수동 검토 404·409, snapshot 승인 409를 확인했다. |
| 4 | ApiSpec ↔ `docs/error-codes.md` | 문서 표 보강 필요 | `FilteringErrorCode`에는 FLT 코드와 HTTP 상태가 정의되어 있으나 `docs/error-codes.md`에 filtering 절이 없다. 따라서 코드·상태의 기준은 enum으로 대조하고, 문서 표 부재는 미해결 항목으로 기록한다. |
| 5 | ApiSpec ↔ SecurityConfiguration | 이상 없음, 응답 선언 보강 필요 | `/api/v1/filtering/appeals`는 `appApiSecurityFilterChain`의 JWT 인증, `/admin/filtering/**`는 `backofficeSecurityFilterChain`의 운영자 세션·CSRF·OPERATOR 권한을 사용한다. `@SecurityRequirement`는 맞지만 operator 401·403 응답이 빠졌다. |
| 6 | ApiSpec ↔ `docs/api/openapi.json` | 반영 확인 | 기준선 테스트(`BUILD SUCCESSFUL in 42s`)와 반영 후 테스트(`BUILD SUCCESSFUL in 35s`)가 모두 통과했다. 반영 후 16개 경로의 설명·응답과 10개 DTO 스키마가 생성물에 반영됐다. |

보안 스킴 문자열은 `AppealApiSpec`만 `OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME` 상수를
사용한다. `AppealCaseApiSpec`, `FilterReleaseApiSpec`, `ManualReviewCaseApiSpec`,
`SnapshotHealthApiSpec`의 `"operatorSession"` 리터럴은 같은 상수로 통일한다.

## 3. 엔드포인트별 제안

### `POST /api/v1/filtering/appeals` — `AppealApiSpec.file`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 상태 누락은 없다. 401·403 설명에 `CMN-VAL-003`, `CMN-DOM-001`을 명시하고, 404·409 설명을 사용자 용어로 다듬는다. |
| 누락된 `@Schema(description)` 필드 | `FileAppealRequest`의 `targetType`, `targetId`, `filterDecisionId`; `AppealCaseResponse`의 14개 필드 |
| 문장 기준 위반 | 5문단 순서가 부족하고 `filter decision` 같은 내부 용어를 그대로 노출한다. |

**Before**

```java
summary = "이의제기 접수",
description = """
	BLOCK 판정으로 비공개 처리된 자신의 답변에 대해 이의를 제기합니다.
	접수는 콘텐츠의 공개 상태를 바꾸지 않습니다 — 검토가 끝날 때까지 비공개로 남습니다.

	접수 기간은 판정 시각으로부터 6개월입니다. 판정 시각을 신뢰할 수 없는 경우에는
	거절하지 않고 접수하며, 그 사실을 acceptanceReasonCode=WINDOW_UNVERIFIABLE로 남깁니다."""
```

**After**

```java
summary = "비공개 답변에 이의제기 접수하기",
description = """
	BLOCK 판정으로 비공개 처리된 답변에 이의제기를 접수합니다. 요청 본문으로 대상 유형,
	답변 식별자와 필터 판정 식별자를 함께 지정합니다.

	앱 액세스 토큰이 필요하며, 지정한 답변의 작성자만 접수할 수 있습니다. 현재 대상 유형은
	ANSWER만 지원합니다.

	접수에 성공하면 이의제기가 OPEN 상태로 저장되어 반환됩니다. 답변은 검토가 끝날 때까지
	비공개 상태로 유지됩니다.

	지원하지 않는 대상 유형, 다른 사용자의 답변, 존재하지 않는 필터 판정, BLOCK이 아닌 판정,
	이미 접수한 대상 또는 접수 기간이 지난 판정이면 접수할 수 없습니다.

	접수 기간은 판정 시각부터 6개월입니다. 판정 시각을 확인할 수 없으면 접수를 거절하지 않고
	acceptanceReasonCode에 WINDOW_UNVERIFIABLE을 기록합니다."""
```

**변경 근거**

- `AppealCaseService.file()`의 대상 유형·소유권·필터 판정 존재·BLOCK 상태·중복·기간 검사를
  순서대로 반영한다.
- 403은 `FLT-DOM-015`, 404는 `FLT-DOM-017`, 409는 `FLT-INFRA-001`, `FLT-DOM-013`,
  `FLT-DOM-014`를 사용한다.

### `GET /api/v1/filtering/appeals` — `AppealApiSpec.findMine`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 설명에 `CMN-VAL-003`을 명시한다. |
| 누락된 `@Schema(description)` 필드 | `AppealCaseResponse`의 14개 필드 |
| 문장 기준 위반 | 한 문장으로 끝나 선행조건·결과·빈 목록·주의점을 알 수 없다. |

**Before**

```java
summary = "내 이의제기 목록 조회",
description = "본인이 접수한 이의제기를 최신순으로 반환합니다."
```

**After**

```java
summary = "내 이의제기 목록 조회",
description = """
	본인이 접수한 이의제기 목록을 조회합니다.

	앱 액세스 토큰이 필요하며, 인증된 사용자가 접수한 이의제기만 반환합니다.

	목록은 최근 접수한 이의제기부터 반환합니다. 접수한 항목이 없으면 빈 목록을 반환합니다.

	토큰이 없거나 유효하지 않으면 조회할 수 없습니다.

	이 API는 이의제기 상태를 바꾸지 않고 목록만 조회합니다."""
```

**변경 근거**

- `AppealCaseRepository.findByAppellantUserId()` 주석의 최신 접수 우선 규칙을 반영한다.
- 인증 실패는 `CMN-VAL-003`으로 문서화한다.

### `GET /admin/filtering/appeal-cases` — `AppealCaseApiSpec.findQueue`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001` |
| 누락된 `@Schema(description)` 필드 | `AppealCaseResponse`의 14개 필드 |
| 문장 기준 위반 | `OPEN`, `FIFO`, `created_at`과 `effectiveBand`를 설명 없이 사용하고 `한다체`다. |

**Before**

```java
summary = "이의제기 검토 큐 조회",
description = "OPEN 상태의 이의제기를 접수 순서(FIFO)로 반환합니다."
```

**After**

```java
summary = "이의제기 검토 큐 조회",
description = """
	운영자가 검토할 이의제기 큐를 조회합니다. 아직 종결되지 않은 이의제기만 포함합니다.

	운영자 세션이 필요합니다. 조회 요청은 CSRF 토큰 없이 호출할 수 있으며, limit으로 한 번에
	받을 최대 항목 수를 지정합니다.

	접수 시각이 빠른 이의제기부터 반환합니다. 검토할 항목이 없으면 빈 목록을 반환합니다.

	운영자 세션이 없거나 운영자 권한이 없으면 조회할 수 없습니다.

	이 API는 큐의 상태를 바꾸지 않으며, 결정 적용은 별도의 결정 API에서 수행합니다."""
```

**변경 근거**

- `AppealCaseService.findQueue()`와 `AppealCaseRepository.findOpenQueue()`의 OPEN·FIFO 규칙을
  사용자 관점의 문장으로 풀어 쓴다.
- `/admin/**` GET은 CSRF 검사를 통과할 토큰을 요구하지 않지만 운영자 세션과 권한은 필요하다.

### `POST /admin/filtering/appeal-cases/{appealCaseId}/decide` — `AppealCaseApiSpec.decide`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001` |
| 누락된 `@Schema(description)` 필드 | `AppealDecisionRequest`의 2개 필드, `OperatorReasonRequest`의 클래스 설명과 `AppealCaseResponse` 14개 필드 |
| 문장 기준 위반 | 선행조건·인증과 실패 조건이 없고, 복원 콜백의 주체가 내부 구현 용어로 남아 있다. path parameter가 `appeal case id`다. |

**Before**

```java
summary = "이의제기 결정 적용",
description = """
	이의제기를 UPHOLD_HIDDEN(비공개 유지) 또는 OVERTURN_HIDDEN(비공개 취소)으로 종결합니다.

	OVERTURN_HIDDEN이면 복원 콜백을 내보내기 전에 다른 공개 금지 사유(계정 차단·삭제 등)를
	다시 확인합니다. 남아 있는 사유가 있으면 결정은 그대로 기록하되 복원 콜백을 내보내지 않고
	restoreBlockedReasonCode에 사유를 남깁니다.

	답변의 공개 상태를 실제로 되돌리는 것은 이 API가 아니라 콜백을 받는 답변 담당 코드입니다."""
```

**After**

```java
summary = "이의제기 결정 적용",
description = """
	운영자가 이의제기를 비공개 유지(UPHOLD_HIDDEN) 또는 비공개 취소(OVERTURN_HIDDEN)로
	종결합니다. 결정 사유도 함께 기록합니다.

	운영자 세션과 CSRF 토큰이 필요하며, 아직 종결되지 않은 이의제기만 결정할 수 있습니다.

	결정에 성공하면 이의제기가 RESOLVED 상태로 반환됩니다. 비공개 취소 결정이고 다른 공개
	금지 사유가 없으면 답변 복원을 요청하는 이벤트를 발행합니다.

	존재하지 않는 이의제기이거나 이미 종결된 이의제기면 결정할 수 없습니다. 다른 공개 금지
	사유가 남아 있으면 결정은 저장하지만 답변 복원 요청은 발행하지 않습니다.

	답변의 공개 상태를 실제로 바꾸는 작업은 이 API가 아니라 복원 요청을 처리하는 답변 기능이
	수행합니다."""
```

**변경 근거**

- `AppealCaseService.decide()`의 행 잠금·RESOLVED 전이·`publicationBlockChecker` 결과·콜백
  발행 조건을 반영한다.
- `FLT-DOM-011`(404), `FLT-DOM-012`(409)와 공통 인증 오류를 선언한다.

### `POST /admin/filtering/appeal-cases/{appealCaseId}/extend` — `AppealCaseApiSpec.extendExpiry`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001` |
| 누락된 `@Schema(description)` 필드 | `ExtendAppealExpiryRequest`의 2개 필드, `OperatorReasonRequest`의 클래스 설명과 `AppealCaseResponse` 14개 필드 |
| 문장 기준 위반 | 선행조건·인증과 성공 결과가 생략되어 있고, `API`, `도메인`, `스키마`를 나열한다. path parameter가 내부 용어다. |

**Before**

```java
summary = "접수 기간 연장",
description = """
	법률·정책상 필요한 경우 접수 기간을 연장합니다. 현재 만료 시각보다 늦은 값만 받습니다 —
	기간을 줄이는 경로는 이 API에도, 도메인에도, 스키마에도 없습니다."""
```

**After**

```java
summary = "이의제기 접수 기간 연장",
description = """
	운영자가 이의제기의 접수 만료 시각을 뒤로 미룹니다. 연장 사유도 함께 기록합니다.

	운영자 세션과 CSRF 토큰이 필요합니다. 현재 만료 시각을 확인한 뒤 그보다 늦은 시각을
	지정해야 합니다.

	연장에 성공하면 변경된 만료 시각을 포함한 이의제기를 반환합니다.

	존재하지 않는 이의제기이거나 현재 만료 시각과 같거나 이른 시각을 보내면 연장할 수 없습니다.

	이 API는 접수 기간을 줄이지 않고 뒤로 미루기만 합니다. 연장 사유는 운영 기록으로 남습니다."""
```

**변경 근거**

- `AppealCaseService.extendExpiry()`와 `AppealCase.extendExpiry()`의 단방향 시각 검증을
  반영한다.
- `FLT-DOM-011`(404), `FLT-DOM-016`(409)와 공통 인증 오류를 선언한다.

### `POST /admin/filtering/releases` — `FilterReleaseApiSpec.create`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001`; 기존 400은 `FLT-VAL-001` 대신 `FLT-VAL-003`을 사용해야 한다. |
| 누락된 `@Schema(description)` 필드 | `CreateFilterReleaseRequest`의 4개 필드, `FilterReleaseResponse`의 8개 필드 |
| 문장 기준 위반 | `release candidate`, `category mapping`, `model snapshot`을 설명 없이 쌓고 `한다체`다. |

**Before**

```java
summary = "release candidate 생성",
description = """
	정규화 규칙·로컬 사전·category mapping·model snapshot 참조를 묶어 새 candidate release를 만든다.

	이 시점에는 사용자 상태나 판정에 아무 영향을 주지 않는다. 승격 전까지는 비권위다."""
```

**After**

```java
summary = "새 검사 설정 만들기",
description = """
	정규화 규칙, 로컬 규칙, 분류 매핑과 모델 snapshot을 가리키는 참조를 묶어 새 검사 설정을
	CANDIDATE 상태로 만듭니다.

	운영자 세션과 CSRF 토큰이 필요합니다. 네 참조 값은 비어 있지 않아야 하며, `latest`처럼
	움직이는 별칭을 사용할 수 없습니다.

	생성에 성공하면 CANDIDATE 상태의 검사 설정을 반환합니다.

	참조 값이 비어 있거나 허용 길이를 넘거나 `latest` 별칭이면 생성할 수 없습니다.

	검사 설정은 점검 단계를 거쳐 명시적으로 적용하기 전까지 사용자 상태나 판정에 영향을 주지
	않습니다."""
```

**변경 근거**

- `FilterRelease` 생성자 `requiredRef()`의 `FLT-VAL-003`, `FLT-VAL-004`와
  `FilterReleaseRegistryService.createCandidate()`를 반영한다.
- CANDIDATE 생성 자체가 권위 있는 정책을 바꾸지 않는다는 도메인 주석을 사용자 관점으로
  풀어 쓴다.

### `GET /admin/filtering/releases` — `FilterReleaseApiSpec.findAll`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001` |
| 누락된 `@Schema(description)` 필드 | `FilterReleaseResponse`의 8개 필드 |
| 문장 기준 위반 | `summary`에 `release`만 남아 있고 `description`이 없다. |

**Before**

```java
@Operation(summary = "release 목록 조회")
```

**After**

```java
@Operation(
	summary = "검사 설정 목록 조회",
	description = """
	등록된 필터링 검사 설정 목록을 조회합니다.

	운영자 세션이 필요합니다. 목록 조회는 GET 요청이므로 CSRF 토큰 없이 호출할 수 있습니다.

	각 설정의 상태와 참조 값을 반환하며, 등록된 설정이 없으면 빈 목록을 반환합니다.

	운영자 세션이 없거나 운영자 권한이 없으면 조회할 수 없습니다.

	목록을 조회해도 현재 적용 중인 설정이나 설정 상태는 바뀌지 않습니다.""")
```

**변경 근거**

- `FilterReleaseRegistryService.findAll()`은 저장된 목록을 반환하고 상태를 변경하지 않는다.
- 정렬은 repository 계약에 명시되어 있지 않으므로 특정 순서를 문서에 추가하지 않는다.

### `GET /admin/filtering/releases/{releaseId}` — `FilterReleaseApiSpec.find`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001` |
| 누락된 `@Schema(description)` 필드 | `FilterReleaseResponse`의 8개 필드 |
| 문장 기준 위반 | `description`이 없고 path parameter가 `release id`다. |

**Before**

```java
@Operation(summary = "release 단건 조회")
@Parameter(description = "release id")
```

**After**

```java
@Operation(
	summary = "검사 설정 상세 조회",
	description = """
	지정한 필터링 검사 설정의 참조 값과 현재 상태를 조회합니다.

	운영자 세션이 필요합니다. 조회 요청은 CSRF 토큰 없이 호출할 수 있습니다.

	조회에 성공하면 해당 설정의 상태, 생성 시각과 적용 시각을 반환합니다.

	지정한 식별자의 설정이 없으면 조회할 수 없습니다.

	이 API는 설정을 생성하거나 적용하지 않고 현재 저장된 값을 보여주기만 합니다.""")
@Parameter(description = "조회할 검사 설정 식별자입니다.")
```

**변경 근거**

- `FilterReleaseRegistryService.find()`가 `FLT-DOM-005`를 던지는 경로를 반영한다.

### `POST /admin/filtering/releases/{releaseId}/offline-evaluation` — `FilterReleaseApiSpec.markOfflineEvaluated`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001`, 404 `FLT-DOM-005` |
| 누락된 `@Schema(description)` 필드 | `OperatorReasonRequest` 클래스 설명과 `FilterReleaseResponse` 8개 필드 |
| 문장 기준 위반 | `endpoint`와 외부 평가 결과를 설명 없이 사용하고 5문단 순서가 없다. path parameter가 내부 용어다. |

**Before**

```java
summary = "offline evaluation 완료 처리",
description = "합격 기준 판단은 이 시스템 밖에서 이뤄진다. 이 endpoint는 그 결과를 등록만 한다."
```

**After**

```java
summary = "검사 결과 등록하기 (→ OFFLINE_EVALUATED)",
description = """
	외부에서 완료한 검사 결과를 해당 검사 설정에 기록해 OFFLINE_EVALUATED 상태로 전환합니다.

	운영자 세션과 CSRF 토큰이 필요하며, 변경 사유를 함께 보내야 합니다. CANDIDATE 상태의
	설정만 전환할 수 있습니다.

	성공하면 OFFLINE_EVALUATED 상태로 바뀐 검사 설정을 반환합니다.

	설정이 없거나 CANDIDATE 상태가 아니면 전환할 수 없습니다.

	외부 검사를 실제로 실행하거나 합격 여부를 판정하지 않습니다. 이 API는 완료된 결과를
	기록하고 다음 점검 단계로 이동시키는 역할만 합니다."""
```

**변경 근거**

- `FilterReleaseRegistryService.markOfflineEvaluated()`의 `find()` 404와
  `FilterRelease.markOfflineEvaluated()`의 `FLT-DOM-004`를 반영한다.

### `POST /admin/filtering/releases/{releaseId}/shadow` — `FilterReleaseApiSpec.designateShadow`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001`, 404 `FLT-DOM-005` |
| 누락된 `@Schema(description)` 필드 | `OperatorReasonRequest` 클래스 설명과 `FilterReleaseResponse` 8개 필드 |
| 문장 기준 위반 | `한다체`, 내부 불변식 ID 노출, `shadow` 단계의 의미 설명 부족, path parameter 설명 누락 |

**Before**

```java
summary = "shadow 지정",
description = "비권위 shadow 단계로 전이한다. 사용자 상태와 닉네임 동기 용량에 영향을 주지 않는다(INV-REL-007)."
```

**After**

```java
summary = "검사 설정을 shadow로 시험하기 (→ SHADOW)",
description = """
	OFFLINE_EVALUATED 상태의 검사 설정을 실제 사용자 판정에 쓰지 않는 SHADOW 단계로 전환합니다.

	운영자 세션과 CSRF 토큰이 필요하며, 변경 사유를 함께 보내야 합니다. OFFLINE_EVALUATED
	상태의 설정만 전환할 수 있습니다.

	성공하면 SHADOW 상태로 바뀐 검사 설정을 반환합니다.

	설정이 없거나 현재 상태가 OFFLINE_EVALUATED가 아니면 전환할 수 없습니다.

	SHADOW 단계는 동작을 관찰하기 위한 비권위 단계이므로 사용자 상태나 닉네임 동기 용량을
	바꾸지 않습니다."""
```

**변경 근거**

- `FilterRelease.designateShadow()`의 허용 상태와 비권위 단계 주석을 반영한다.
- `FLT-DOM-004`는 상태 전이 실패에만 사용하며, 존재하지 않는 설정은 `FLT-DOM-005`다.

### `POST /admin/filtering/releases/{releaseId}/canary` — `FilterReleaseApiSpec.designateCanary`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001`, 404 `FLT-DOM-005` |
| 누락된 `@Schema(description)` 필드 | `OperatorReasonRequest` 클래스 설명과 `FilterReleaseResponse` 8개 필드 |
| 문장 기준 위반 | `한다체`, 내부 불변식 ID 노출, `canary` 단계의 의미 설명 부족, path parameter 설명 누락 |

**Before**

```java
summary = "canary 지정",
description = "비권위 canary 단계로 전이한다. 사용자 상태와 닉네임 동기 용량에 영향을 주지 않는다(INV-REL-007)."
```

**After**

```java
summary = "검사 설정을 canary로 시험하기 (→ CANARY)",
description = """
	SHADOW 상태의 검사 설정을 제한된 범위에서 확인하는 CANARY 단계로 전환합니다.

	운영자 세션과 CSRF 토큰이 필요하며, 변경 사유를 함께 보내야 합니다. SHADOW 상태의
	설정만 전환할 수 있습니다.

	성공하면 CANARY 상태로 바뀐 검사 설정을 반환합니다.

	설정이 없거나 현재 상태가 SHADOW가 아니면 전환할 수 없습니다.

	CANARY 단계도 아직 권위 있는 적용 상태가 아니므로, 이 호출만으로 모든 사용자 판정에
	새 설정을 사용하지 않습니다."""
```

**변경 근거**

- `FilterRelease.designateCanary()`의 허용 상태와 비권위 단계 주석을 반영한다.
- `FLT-DOM-004`와 `FLT-DOM-005`, 공통 인증 오류를 응답에 선언한다.

### `POST /admin/filtering/releases/{releaseId}/promote` — `FilterReleaseApiSpec.promote`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001`, 404 `FLT-DOM-005` |
| 누락된 `@Schema(description)` 필드 | `OperatorReasonRequest` 클래스 설명과 `FilterReleaseResponse` 8개 필드 |
| 문장 기준 위반 | #190 before 문장을 그대로 사용해 `한다체`, 내부 불변식 ID와 낯선 명사구를 노출한다. |

**Before**

```java
summary = "release 승격",
description = """
	CANARY를 통과한 candidate를 명시적으로 승격한다. 기존에 PROMOTED인 release가 있으면
	이 요청과 같은 트랜잭션에서 ROLLED_BACK으로 내린다. 이 endpoint를 호출하지 않으면
	어떤 release도 자동으로 승격되지 않는다(INV-REL-001, INV-REL-008)."""
```

**After**

```java
summary = "이 설정을 실제로 적용하기 (→ PROMOTED)",
description = """
	CANARY 단계까지 확인한 검사 설정을 지금부터 실제 판정에 사용하는 설정으로 적용합니다.

	운영자 세션과 CSRF 토큰이 필요하며, 적용 사유를 함께 보내야 합니다. CANARY 상태의
	설정만 적용할 수 있습니다.

	적용에 성공하면 해당 설정은 PROMOTED 상태가 됩니다. 기존에 적용 중인 설정이 있으면
	같은 작업 안에서 ROLLED_BACK 상태로 바뀌어 동시에 두 설정이 적용되지 않습니다.

	설정이 없거나 CANARY 상태가 아니거나 이미 적용 중인 설정을 다시 적용하려 하면 실패합니다.

	설정을 만들거나 점검 단계를 올리는 것만으로는 실제 판정 설정이 바뀌지 않습니다. 이 API를
	호출해야 적용 상태가 바뀝니다."""
```

**변경 근거**

- #190 §11의 `promote()` after 제안을 실제 `FilterReleaseRegistryService.promote()`와
  `demoteCurrentlyPromoted()`에 대조했다.
- `find()` 404와 `FilterRelease.promote()` 및 이미 PROMOTED인 대상의 `FLT-DOM-004`를
  반영한다.

### `POST /admin/filtering/releases/{releaseId}/rollback` — `FilterReleaseApiSpec.rollback`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001`, 404 `FLT-DOM-005` |
| 누락된 `@Schema(description)` 필드 | `OperatorReasonRequest` 클래스 설명과 `FilterReleaseResponse` 8개 필드 |
| 문장 기준 위반 | `rollback`이라는 이름과 실제 재승격 동작의 관계가 설명되지 않고 path parameter 설명이 없다. 기존 문장은 `한다체`다. |

**Before**

```java
summary = "release rollback",
description = "이전에 PROMOTED였다가 ROLLED_BACK으로 내려간 release를 다시 승격한다."
```

**After**

```java
summary = "이전에 적용한 설정을 다시 적용하기 (→ PROMOTED)",
description = """
	이전에 적용했다가 ROLLED_BACK 상태가 된 검사 설정을 다시 실제 판정에 사용하는 설정으로
	적용합니다.

	운영자 세션과 CSRF 토큰이 필요하며, 재적용 사유를 함께 보내야 합니다. ROLLED_BACK 상태의
	설정만 재적용할 수 있습니다.

	성공하면 해당 설정은 PROMOTED 상태가 됩니다. 현재 적용 중인 다른 설정이 있으면 같은
	작업 안에서 ROLLED_BACK 상태로 바뀝니다.

	설정이 없거나 ROLLED_BACK 상태가 아니면 재적용할 수 없습니다.

	이 API는 새 설정을 만들거나 이전 설정을 삭제하지 않고, 이미 등록된 설정의 적용 상태만
	바꿉니다."""
```

**변경 근거**

- `FilterReleaseRegistryService.rollback()`이 `FilterRelease.rePromote()`를 호출하므로,
  실제 동작인 재승격을 summary와 description에 드러낸다.
- `FLT-DOM-005`와 `FLT-DOM-004`, 공통 인증 오류를 응답에 선언한다.

### `GET /admin/filtering/manual-review-cases` — `ManualReviewCaseApiSpec.findQueue`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001` |
| 누락된 `@Schema(description)` 필드 | `ManualReviewCaseResponse`의 12개 필드 |
| 문장 기준 위반 | `한다체`, `effectiveBand`, `created_at`, `agingThresholdSeconds`를 내부 명칭 그대로 사용한다. |

**Before**

```java
description = """
	OPEN case를 effectiveBand 내림차순 + band 내 created_at 오름차순(FIFO)으로 반환한다.
	agingThresholdSeconds는 저장된 band가 STANDARD인 case를 얼마나 오래 대기하면 HIGH로
	취급할지를 호출자가 명시한다 — 실제 운영 aging 시간이 미결정이라 서버가 값을 고정하지
	않는다."""
```

**After**

```java
description = """
	아직 종결되지 않은 수동 검토 건을 우선순위 큐로 조회합니다.

	운영자 세션이 필요합니다. agingThresholdSeconds로 STANDARD 건을 HIGH 우선순위로 볼 대기
	시간을 지정하고, limit으로 반환할 최대 건수를 지정합니다.

	높은 우선순위 건을 먼저 반환하고, 같은 우선순위에서는 오래 열린 건부터 반환합니다. 대상이
	없으면 빈 목록을 반환합니다.

	운영자 세션이 없거나 운영자 권한이 없으면 조회할 수 없습니다.

	대기 시간에 따른 우선순위는 조회할 때만 계산하며 저장된 case의 band를 바꾸지 않습니다."""
```

**변경 근거**

- `ManualReviewDecisionService.findQueue()`와 repository 주석의 effective band·FIFO·조회 시점
  aging 계산을 반영한다.
- 이 조회 서비스에는 별도 `FilteringException`이 없으므로 인증 오류 외 응답은 추측하지 않는다.

### `POST /admin/filtering/manual-review-cases/{caseId}/decide` — `ManualReviewCaseApiSpec.decide`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001` |
| 누락된 `@Schema(description)` 필드 | `ManualReviewDecisionRequest`의 2개 필드와 `ManualReviewCaseResponse` 12개 필드 |
| 문장 기준 위반 | `한다체`, 내부 불변식 ID 노출, 선행조건·인증·성공 결과가 없다. path parameter가 내부 용어다. |

**Before**

```java
summary = "검토자 결정 적용",
description = """
	case를 ALLOW 또는 BLOCK으로 종결한다. 자동 결과가 이미 도착해 job이 RESOLVED라면
	job은 건드리지 않고 그 기존 판정으로 case만 종료한다(INV-MAN-003)."""
```

**After**

```java
summary = "수동 검토 결정 적용",
description = """
	운영자가 수동 검토 건을 ALLOW 또는 BLOCK으로 종결하고 결정 사유를 기록합니다.

	운영자 세션과 CSRF 토큰이 필요합니다. 아직 종결되지 않은 검토 건을 대상으로 결정합니다.

	성공하면 RESOLVED 상태와 결정 결과를 포함한 검토 건을 반환합니다.

	검토 건이 없거나 이미 종결된 검토 건이면 결정할 수 없습니다. 자동 결과가 먼저 도착한
	경우에는 자동 결과를 유지하고 운영자 결정으로 덮어쓰지 않습니다.

	이 API는 검토 건과 연결된 필터 작업의 최종 결과를 기록합니다. 자동 결과가 이미 확정된
	경우에는 그 결과를 유지하는 것이 주의점입니다."""
```

**변경 근거**

- `ManualReviewDecisionService.decide()`의 case 조회, job 잠금, RESOLVED 분기와
  `ManualReviewCase.resolve()`의 `FLT-DOM-009`, `FLT-DOM-010`을 반영한다.

### `POST /admin/filtering/snapshot-health/{modelSnapshot}/confirm-permanent` — `SnapshotHealthApiSpec.confirmPermanent`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 401 `CMN-VAL-003`, 403 `CMN-DOM-001` |
| 누락된 `@Schema(description)` 필드 | `OperatorReasonRequest` 클래스 설명과 `SnapshotHealthResponse`의 9개 필드 |
| 문장 기준 위반 | `한다체`, 내부 불변식 ID와 `emergency migration`을 설명 없이 노출한다. |

**Before**

```java
summary = "snapshot PERMANENT_CONFIRMED 승인",
description = """
	PERMANENT_SUSPECTED 상태의 snapshot을 운영자가 명시적으로 영구 장애로 확정한다.
	이 승인 없이는 어떤 자동 경로도 PERMANENT_CONFIRMED에 도달하거나 emergency
	migration을 실행할 수 없다(INV-HLT-005)."""
```

**After**

```java
summary = "모델 snapshot을 영구 장애로 확정하기 (→ PERMANENT_CONFIRMED)",
description = """
	운영자가 반복적인 장애가 확인된 모델 snapshot을 영구 장애 상태로 확정합니다.

	운영자 세션과 CSRF 토큰이 필요하며, 변경 사유를 함께 보내야 합니다. PERMANENT_SUSPECTED
	상태의 snapshot만 확정할 수 있습니다.

	확정에 성공하면 PERMANENT_CONFIRMED 상태와 확정 시각·운영자 식별자를 반환합니다.

	snapshot이 아직 의심 상태가 아니면 확정할 수 없습니다.

	자동 probe는 이 상태로 확정하지 않으며, 영구 장애 확정은 이 운영자 API에서만 수행합니다.
	확정 이후 자동 경로가 상태를 되돌리지 않습니다."""
```

**변경 근거**

- `SnapshotHealthService.confirmPermanent()`와 `SnapshotHealth.confirmPermanent()`의
  `FLT-DOM-007` 및 운영자 전용 전이 규칙을 반영한다.
- `emergency migration`은 이 API가 직접 실행하지 않으므로 결과처럼 쓰지 않고, 자동 전이
  제한으로만 설명한다.

## 4. DTO·스키마 보강 제안

모든 대상 record에 클래스 설명을 추가하고, 아래 필드에 `@Schema(description = "...")`를
추가한다. `OperatorReasonRequest`의 기존 두 필드도 `합니다`체 문장으로 다듬는다.

| DTO | 클래스 설명 제안 | 필드 설명 대상 |
| --- | --- | --- |
| `AppealCaseResponse` | 이의제기 접수와 검토 결과를 담은 응답입니다. | `id`, `targetType`, `targetId`, `filterDecisionId`, `appellantUserId`, `status`, `windowStartedAt`, `expiresAt`, `acceptanceReasonCode`, `decision`, `decidedAt`, `decidedByOperatorUserId`, `restoreBlockedReasonCode`, `createdAt` |
| `AppealDecisionRequest` | 운영자 이의제기 결정 요청입니다. | `decision`, `reason` |
| `FileAppealRequest` | 이의제기 접수 요청입니다. | `targetType`, `targetId`, `filterDecisionId` |
| `ManualReviewCaseResponse` | 수동 검토 건과 결정 결과를 담은 응답입니다. | `id`, `filterJobId`, `filterReleaseId`, `status`, `band`, `validatedReportSignalCount`, `priorityPolicyVersion`, `priorityReasonCode`, `resolvedAt`, `resolvedByOperatorUserId`, `resolvedVerdict`, `createdAt` |
| `OperatorReasonRequest` | 운영자 행위의 근거를 담은 요청입니다. | `reasonCode`, `reasonText`의 종결어미 보강 |
| `SnapshotHealthResponse` | 모델 snapshot의 장애 상태와 운영자 확정 정보를 담은 응답입니다. | `modelSnapshot`, `status`, `targetOnlyFailureCount`, `firstTargetOnlyFailureAt`, `lastTargetOnlyFailureAt`, `officialAnnouncement`, `confirmedAt`, `confirmedByOperatorUserId`, `updatedAt` |
| `CreateFilterReleaseRequest` | 새 필터링 검사 설정을 만드는 요청입니다. | `normalizationRef`, `localRulesetRef`, `categoryMappingRef`, `modelSnapshot` |
| `ExtendAppealExpiryRequest` | 이의제기 접수 기간을 연장하는 요청입니다. | `expiresAt`, `reason` |
| `ManualReviewDecisionRequest` | 수동 검토 결정을 적용하는 요청입니다. | `verdict`, `reason` |
| `FilterReleaseResponse` | 필터링 검사 설정과 적용 상태를 담은 응답입니다. | `id`, `normalizationRef`, `localRulesetRef`, `categoryMappingRef`, `modelSnapshot`, `status`, `promotedAt`, `createdAt` |

`@Schema(requiredMode = REQUIRED)`만 있는 `FileAppealRequest.targetId`와
`filterDecisionId`에도 값의 의미를 설명하는 `description`을 함께 추가한다. 실제
요청·응답에 없는 예시와 상태 전이는 추가하지 않는다.

## 5. 반영하지 않은 제안

- `docs/error-codes.md`에 filtering 절을 추가하는 작업은 코드 애노테이션 검토 범위를
  넘어 별도 문서 변경으로 남긴다. 현재 보고서는 `FilteringErrorCode` enum을 기준으로
  오류 코드와 HTTP 상태를 대조했다.
- `limit`과 `agingThresholdSeconds`의 허용 범위를 서비스나 DTO에 명시적으로 검증하지
  않으므로, 코드에 없는 범위 오류 응답을 제안하지 않는다.
- `FilterReleaseResponse`의 참조 문자열이 가리키는 실제 정책 내용은 이 API가 반환하지
  않으므로 설명에 합격 기준이나 내부 정책을 지어내지 않는다.
- `SnapshotHealth` 확정 뒤의 emergency migration 실행은 이 API의 동작이 아니므로 성공
  결과로 설명하지 않는다.

## 6. 실행하지 못한 검증

- 실행하지 못한 명령: 없음
- 기준선 명령: `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`
- 기준선 결과: `BUILD SUCCESSFUL in 42s`, 기준선 `docs/api/openapi.json` diff 없음
- 반영 후 명령: `./gradlew compileJava --console=plain`,
  `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`
- 반영 후 결과: compileJava `BUILD SUCCESSFUL in 5s`, 통합 테스트 `BUILD SUCCESSFUL in 35s`
- 추가 확인: Filtering/Appeal 16개 경로의 응답 코드·보안 스킴과 10개 DTO의 클래스·필드
  description 누락 0건을 `jq`로 확인했고 `git diff --check`도 통과했다.
- 전체 PR readiness: `./harness pr-ready --project-tests`가 `BUILD SUCCESSFUL in 12m 40s`로
  통과했다. Secret preflight, JUnit 정책, convention/workflow/label/Husky 게이트와 전체
  unit/integration test를 포함한다.

## 7. Reviewer checklist

- [x] 모든 제안 문장이 실제 서비스·도메인·DTO 코드로 근거를 확인했다 (추측 없음)
- [x] 승인 전 검토 모드에서 `*ApiSpec` 원본을 수정하지 않았고, 승인 후 제안 범위만 반영했다
- [x] `@Schema(example)`에 비밀값·계정 식별자를 쓰지 않았다
- [x] 내부 불변식 ID(`INV-*`)를 최종 제안 문장에 남기지 않았다
- [x] 6점 대조를 모두 실행했고 실행하지 못한 항목이 없다
- [x] 담당자 승인 후 제안을 반영했다
