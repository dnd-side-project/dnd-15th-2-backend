# API Docs Review: Safety/Report

> Created at: `2026-08-24T10:54:09+09:00`
> GitHub Issue: `#189`
> Target: `SafetyApiSpec`, `OperatorReportCaseApiSpec`
> 문장 기준: `docs/api/OPENAPI_WRITING_GUIDE.md` (#190)

이 문서는 신고자용 신고·차단 API와 운영자용 신고 사건 API를 #190 가이드의 review
절차로 대조한 결과다. Account/Profile은 최신 `origin/main`의 #195 선행 반영과
`docs/reports/gh-189-API-DOCS-REVIEW-ACCOUNT-PROFILE.md`를 재확인했으며, 별도
추가 제안은 발견하지 않았다.

## 1. Executive summary

- 대상 엔드포인트 수: 13개(Safety 8개, OperatorReportCase 5개)
- 발견된 문제 수: 4개 범주
  - 설명 구조·사용자 관점 부족: 13개
  - 실제 오류 응답·오류 코드 누락 또는 불명확: 10개
  - path/query parameter 설명 누락: 9개
  - request/response DTO 필드 설명 누락: 24개
- 6점 대조 중 실행하지 못한 항목: 없음

## 2. 6점 대조 결과

| # | 대조 | 결과 | 근거 |
| --- | --- | --- | --- |
| 1 | Controller ↔ ApiSpec | 이상 없음 | `SafetyController` 8개 구현 메서드와 `SafetyApiSpec` 8개 매핑, `OperatorReportCaseController` 5개 구현 메서드와 `OperatorReportCaseApiSpec` 5개 매핑이 일치한다. |
| 2 | ApiSpec ↔ DTO | 보강 필요 | `SubmitReportRequest`의 선택 필드와 Safety 응답 DTO 대부분에 `@Schema(description)`이 없다. 운영자 요청 DTO는 설명이 있으나 응답 DTO와 일부 파라미터 설명이 부족하다. |
| 3 | ApiSpec ↔ Service | 보강 필요 | `SafetyReportService`는 대상 미열람, 자기 신고, rate limit, CRITICAL 일일 quota, 사건 병합 충돌, 본인 소유가 아닌 신고를 각각 처리한다. `OperatorReportCaseService`와 `SafetyCaseResolutionService`는 잘못된 사건 상태·대상·사건/답변 부재를 처리한다. 현재 설명은 이 조건을 충분히 표현하지 않는다. |
| 4 | ApiSpec ↔ `docs/error-codes.md` | 보강 필요 | 신고 접수의 `SAF-VAL-002/006/007`, `SAF-DOM-003`, `SAF-APP-005`, `SAF-INFRA-002`와 운영자 API의 `SAF-VAL-001/003`, `SAF-DOM-002/005`, `SAF-APP-002`가 명세 설명에 누락되거나 `SAF-VAL-*`처럼 내부 코드 묶음으로만 적혀 있다. |
| 5 | ApiSpec ↔ SecurityConfiguration | 이상 없음 | 신고자 API는 `/api/**` 앱 액세스 토큰 인증이며 `SafetyApiSpec`의 `appAccessToken` 요구사항과 일치한다. 운영자 API는 `/api/v1/operator/**` 별도 체인의 운영자 세션 인증이며 `OperatorReportCaseApiSpec`의 `operatorSession` 요구사항과 일치한다. |
| 6 | ApiSpec ↔ `docs/api/openapi.json` | 이상 없음 | `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`와 전체 `./harness pr-ready --project-tests`가 통과했다. 산출물은 Safety/Report 13개 operation의 설명·응답·parameter와 DTO 필드 설명을 반영한다. |

## 3. 엔드포인트별 제안

### `GET /api/v1/report-reasons` — `reportReasons`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음. 인증 실패 401은 선언되어 있다. |
| 누락된 `@Schema(description)` 필드 | `ReportReasonResponse`의 4개 필드 |
| 문장 기준 위반 | 사용자가 무엇을 선택할 수 있는지와 각 필드의 의미가 짧게만 설명되어 있다. |

**After**

```java
summary = "신고 사유 목록 조회"
description = """
  신고할 때 선택할 수 있는 사유와 하위 사유를 조회합니다.

  앱 액세스 토큰이 필요합니다.

  사유 코드, 화면에 표시할 이름, 선택 가능한 하위 사유와 추가 설명 필요 여부를 반환합니다.

  이 API는 사유 목록만 조회하며 신고를 접수하지 않습니다.
  """
```

**변경 근거**: `ReportReasonResponse.catalog()`가 반환하는 `code`, `label`, `subReasons`,
`detailRequired`의 실제 의미를 기준으로 작성한다.

### `POST /api/v1/answers/{answerId}/reports` — `reportAnswer`
### `POST /api/v1/direction-posts/{postId}/reports` — `reportPost`
### `POST /api/v1/users/{userId}/reports` — `reportUser`

세 신고 접수 엔드포인트는 대상 종류만 다르고 `SafetyReportService.submit*Report()`의
공통 흐름을 사용하므로 같은 설명 구조와 오류 응답을 적용한다.

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 400의 구체적인 입력·사유 조합, 429의 CRITICAL 일일 한도, 409의 사건 병합 충돌 |
| 누락된 `@Schema(description)` 필드 | `SubmitReportRequest`의 `subReasonCode`, `detail`, `blockAuthor`; `ReportReceiptResponse` 5개 필드 |
| 문장 기준 위반 | `SAF-VAL-*` 묶음과 상태 코드만으로 설명하고, 인증·열람 가능 대상·멱등 반환·선택적 차단 조건이 엔드포인트별로 드러나지 않는다. |

**After**

```java
summary = "답변 신고" // 대상에 따라 질문글 신고, 사용자 신고
description = """
  {대상}을 신고합니다. 신고 대상의 작성자를 함께 차단할 수도 있습니다.

  앱 액세스 토큰이 필요합니다. 신고 대상은 현재 사용자가 열람할 수 있어야 하며,
  신고 사유와 하위 사유·설명 조합이 허용되어야 합니다. 자기 자신이 작성한 대상은
  신고할 수 없습니다.

  새 신고면 201과 접수 결과를 반환하고, 같은 대상에 이미 열린 신고가 있으면 새로 만들지
  않고 200과 기존 접수 결과를 반환합니다.

  대상을 찾을 수 없거나 열람할 수 없으면 404로 응답합니다. 사유 조합이 올바르지 않거나
  자기 자신을 신고하면 400으로, 신고 한도를 넘으면 429로 응답합니다.

  신고 접수는 검토 결과를 즉시 반환하지 않습니다. 검토 결과는 별도 알림으로 전달됩니다.
  """
```

오류 응답은 다음 실제 코드 경로를 반영한다.

```text
400: CMN-VAL-001, SAF-VAL-002, SAF-VAL-006, SAF-VAL-007, SAF-DOM-003
404: SAF-APP-002
409: SAF-INFRA-002
429: SAF-APP-004, SAF-APP-005
```

**변경 근거**: `ReportSubmission`의 필수 사유·하위 사유 조합·OTHER 설명 검증,
`SafetyReportService.resolveTarget`, `SELF_REPORT_NOT_ALLOWED`, rate limit·CRITICAL
quota·`CASE_MERGE_CONFLICT`, `ReportOutcome.alreadyReceived()`를 대조했다.

### `GET /api/v1/reports/me` — `findMyReports`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 잘못된 cursor의 400 |
| 누락된 `@Schema(description)` 필드 | `ReportPageResponse`, `ReportSummaryResponse` 전체 필드 |
| 문장 기준 위반 | 최신순·커서·limit 상한과 본인 신고만 조회한다는 조건이 설명되지 않았다. |

**After**

```java
summary = "내 신고 내역 조회"
description = """
  현재 사용자가 접수한 신고 내역을 최신 접수 순서로 조회합니다.

  앱 액세스 토큰이 필요합니다.

  신고 요약 목록과 다음 페이지를 요청할 때 사용할 불투명 커서를 반환합니다. limit는
  1에서 50 사이로 보정됩니다.

  cursor 형식이 올바르지 않으면 요청을 처리하지 않습니다.

  다른 사용자가 접수한 신고는 이 목록에 포함되지 않습니다.
  """
```

**변경 근거**: `SafetyController`의 `MAX_PAGE_LIMIT`, `ReportCursor.decode()`와
`findReportsByReporter()`를 대조했다.

### `GET /api/v1/reports/{reportId}` — `findReport`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음. 404 설명을 사용자 관점으로 다듬는다. |
| 누락된 `@Schema(description)` 필드 | `ReportDetailResponse` 7개 필드 |
| 문장 기준 위반 | 상세 응답에 포함되지 않는 상대방 정보·운영자 판단을 명시하지 않고, 본인 소유 조건만 짧게 적었다. |

**After**

```java
summary = "내 신고 상세 조회"
description = """
  현재 사용자가 접수한 신고 한 건의 접수·처리 상태를 조회합니다.

  앱 액세스 토큰이 필요합니다. 요청한 신고가 현재 사용자가 접수한 신고여야 합니다.

  신고 사유, 설명, 상태와 접수·종결 시각을 반환합니다.

  신고가 없거나 다른 사용자가 접수한 신고면 404로 응답합니다.

  신고 대상의 상대방 식별자와 운영자의 내부 판단 내용은 반환하지 않습니다.
  """
```

**변경 근거**: `SafetyReportService.requireOwnReport()`가 `SAF-APP-003`으로 존재와
소유권을 함께 숨기며 `ReportDetailResponse`가 반환하는 필드를 전수 확인했다.

### `POST /api/v1/users/{userId}/blocks` — `block`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음. 자기 차단 400의 설명을 보강한다. |
| 누락된 `@Schema(description)` 필드 | 응답 본문 없음 |
| 문장 기준 위반 | 인증 주체가 차단자라는 점, 차단 시 수신 항목이 함께 정리될 수 있다는 점이 빠졌다. |

**After**

```java
summary = "사용자 차단"
description = """
  현재 사용자가 지정한 사용자를 차단합니다.

  앱 액세스 토큰이 필요합니다. 차단 대상은 경로의 userId로 지정합니다.

  성공하면 두 사용자 사이의 활성 차단을 만들고, 차단 대상이 보낸 미종결 수신 항목을
  차단 상태로 전환해 현재 사용자의 수신 가능 슬롯을 정리합니다.

  자기 자신은 차단할 수 없습니다.

  차단은 신고 접수와 별개의 동작입니다.
  """
```

### `DELETE /api/v1/users/{userId}/blocks` — `releaseBlock`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 없음. 404의 의미를 현재 사용자의 활성 차단 부재로 명확히 한다. |
| 누락된 `@Schema(description)` 필드 | 응답 본문 없음 |
| 문장 기준 위반 | 활성 차단만 해제된다는 조건과 차단 해제 후 대상 사용자의 콘텐츠가 자동 복원되지 않는다는 주의점이 없다. |

**After**

```java
summary = "사용자 차단 해제"
description = """
  현재 사용자가 지정한 사용자에 대한 활성 차단을 해제합니다.

  앱 액세스 토큰이 필요합니다.

  활성 차단을 해제하면 해당 관계의 차단 상태를 종료합니다.

  현재 사용자가 만든 활성 차단이 없으면 404로 응답합니다.

  차단 해제는 과거에 차단 상태가 된 수신 항목이나 신고를 되돌리지 않습니다.
  """
```

### `GET /api/v1/operator/report-cases` — `findQueue`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 잘못된 cursor의 400 |
| 누락된 `@Schema(description)` 필드 | `ReportCasePageResponse`, `ReportCaseResponse` 전체 필드 |
| 문장 기준 위반 | 운영자 세션 인증, queue 값, 커서와 limit 보정, SLA 임박순 결과가 설명되지 않았다. |

**After**

```java
summary = "신고 사건 대기열 조회"
description = """
  운영자가 아직 처리하지 않은 신고 사건을 SLA 마감 시각이 가까운 순서로 조회합니다.

  운영자 세션 인증이 필요합니다. queue를 생략하면 STANDARD와 URGENT 사건을 모두 조회하며,
  지정하면 해당 대기열만 조회합니다.

  사건 목록, 처리 상태, 심각도, SLA 마감 시각과 다음 페이지 커서를 반환합니다. limit는
  1에서 50 사이로 보정됩니다.

  queue 값이 허용 목록이 아니거나 cursor 형식이 올바르지 않으면 요청을 처리하지 않습니다.

  이 API는 신고자용 신고 내역이 아니라 운영자 처리 대기열입니다.
  """
```

### `POST /api/v1/operator/report-cases/{caseId}/review` — `startReview`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 사건 식별자 오류 400 `SAF-VAL-001`, 이미 종결된 사건 400 `SAF-DOM-005` |
| 누락된 `@Schema(description)` 필드 | `ReportCaseResponse` 전체 필드 |
| 문장 기준 위반 | 검토를 시작할 수 있는 사건 상태와 운영자 인증 조건이 설명되지 않았다. |

**After**

```java
summary = "신고 사건 검토 시작"
description = """
  운영자가 열린 신고 사건을 검토 중으로 표시합니다.

  운영자 세션 인증이 필요합니다. 아직 종결되지 않은 사건만 검토를 시작할 수 있습니다.

  사건 상태가 검토 중으로 바뀐 결과를 반환합니다.

  사건 식별자가 올바르지 않거나 이미 종결된 사건이면 요청을 처리하지 않습니다.

  이 호출만으로 사건의 최종 판정이나 답변 숨김이 실행되지는 않습니다.
  """
```

### `POST /api/v1/operator/report-cases/{caseId}/decision` — `decide`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | `SAF-VAL-001`, `SAF-DOM-002`, `SAF-DOM-005` 및 답변 조치 실패 404 `SAF-APP-002` |
| 누락된 `@Schema(description)` 필드 | `ReportCaseResponse` 전체 필드 |
| 문장 기준 위반 | ACTIONED의 답변 숨김 부수효과, NO_VIOLATION, MORE_INFO_REQUIRED 분리 조건, 운영자 메모의 비공개 성격이 부족하다. |

**After**

```java
summary = "신고 사건 최종 판정"
description = """
  운영자가 신고 사건을 ACTIONED 또는 NO_VIOLATION으로 종결합니다.

  운영자 세션 인증이 필요합니다. 사건 식별자가 올바르고 아직 종결되지 않아야 하며,
  decision은 두 최종 판정 중 하나여야 합니다. 추가 정보가 필요하면 별도 API를 사용합니다.

  판정, 종결 시각과 사건 처리 결과를 반환합니다. ACTIONED이고 대상이 답변이면 그 답변을
  숨기고 관련 알림을 취소합니다. internalNote는 신고자에게 공개하지 않는 운영자 메모입니다.

  이미 종결된 사건, 허용되지 않은 판정 또는 사건 대상과 맞지 않는 답변 조치면 요청을
  처리하지 않습니다.

  판정은 사건을 종결하므로 같은 사건에 다시 최종 판정을 내릴 수 없습니다.
  """
```

### `POST /api/v1/operator/report-cases/{caseId}/more-info` — `requestMoreInfo`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | `SAF-VAL-001`, `SAF-DOM-005` |
| 누락된 `@Schema(description)` 필드 | `ReportCaseResponse` 전체 필드 |
| 문장 기준 위반 | 추가 정보 요청이 사건을 종결하지 않는다는 점 외에 인증·메모 목적·재처리 조건이 없다. |

**After**

```java
summary = "신고 사건 추가 정보 요청"
description = """
  운영자가 신고 사건에 추가 정보가 필요하다고 표시하고 내부 메모를 남깁니다.

  운영자 세션 인증이 필요합니다. 사건이 아직 종결되지 않았고, 무엇이 더 필요한지 적은
  메모를 보내야 합니다.

  사건을 MORE_INFO_REQUIRED 상태로 표시한 결과를 반환합니다.

  사건 식별자가 올바르지 않거나 이미 종결된 사건이면 요청을 처리하지 않습니다.

  이 호출은 최종 판정을 내리지 않으며, 메모는 신고자에게 공개되지 않습니다.
  """
```

### `POST /api/v1/operator/report-cases/{caseId}/restore` — `restore`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | 400 `SAF-VAL-001`, `SAF-VAL-003`, `SAF-DOM-002`; 404 `SAF-APP-002` |
| 누락된 `@Schema(description)` 필드 | `AnswerRestoreResponse` 2개 필드 |
| 문장 기준 위반 | ACTIONED 답변 사건만 복원할 수 있다는 선행 조건과 복원 후 상태가 설명되지 않았다. |

**After**

```java
summary = "숨김 답변 복원"
description = """
  운영자가 ACTIONED 판정으로 숨겨진 답변을 다시 공개 상태로 복원합니다.

  운영자 세션 인증이 필요합니다. 사건이 답변을 대상으로 하고 ACTIONED로 종결되어 있어야
  합니다.

  복원한 답변의 식별자와 현재 상태를 반환합니다.

  사건 식별자·대상 답변이 없거나 사건 조건이 ACTIONED 복원 조건과 맞지 않으면 요청을
  처리하지 않습니다.

  질문글·사용자 사건은 이 API로 복원할 수 없습니다.
  """
```

## 4. 반영하지 않은 제안

- `queue`와 `decision`의 `Enum.valueOf`가 만드는 잘못된 문자열 오류는 현재 컨트롤러가
  도메인 오류 코드로 변환하지 않으므로 전용 오류 응답으로 단정하지 않는다. parameter와
  request DTO의 허용 값 설명으로 계약을 명확히 하고, 실제로 변환되는 validation·도메인
  오류만 응답 설명에 적는다.
- `SAF-INFRA-001`은 현재 신고 접수 서비스가 열린 신고를 먼저 반환하는 멱등 경로를
  사용하므로 정상 API 오류 응답으로 추가하지 않는다.
- Account/Profile의 기존 18개 제안은 이미 #195에서 반영되었으므로 재작성하지 않는다.

## 5. 검증 결과

- `./gradlew compileJava`: PASS.
- `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`: PASS.
- `./harness check`: PASS.
- `./harness pr-ready --project-tests`: PASS. 전체 unit/integration test와 하네스 검사를
  포함해 `BUILD SUCCESSFUL in 41m`을 확인했다.
- `git diff --check`: PASS.
- `jq -e . docs/api/openapi.json`: PASS. 생성된 JSON을 파싱하고 13개 Safety/Report
  operation의 summary·response 및 대상 DTO 필드 설명을 대조했다.
- `npm run hooks:validate`: PASS. WSL 2 Ubuntu 환경에서 `Husky validation passed`를
  확인했다. 기본 WSL 1 환경에서는 Node.js 설치 디렉터리 확인 오류가 있었으나 대체
  검증 환경에서 재실행해 해소했다.

## 6. Reviewer checklist

- [x] 모든 제안 문장이 실제 Controller/Service/DTO 코드로 근거를 확인했다.
- [x] 제안 반영 후 `*ApiSpec`과 DTO 변경이 이 보고서 범위와 일치하는지 확인했다.
- [x] `@Schema(example)`에 비밀값·계정 식별자·토큰을 쓰지 않았다.
- [x] 내부 불변식 ID(`INV-*`)를 API 설명에 노출하지 않았다.
- [x] 6점 대조와 필수 hook 검증을 모두 실행했다.
