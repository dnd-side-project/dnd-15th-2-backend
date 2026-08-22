# API Docs Review: <DOMAIN>

> Created at: `<CREATED-AT>`
> GitHub Issue: `#<GITHUB-ISSUE>`
> Target: `<XxxApiSpec 경로>`
> 문장 기준: `docs/api/OPENAPI_WRITING_GUIDE.md`

이 문서는 `*ApiSpec`의 문장 검토 결과다. `harness-api-docs` 스킬의 `review` 모드
산출물이며 `*ApiSpec` 원본은 수정하지 않는다. 여기 담긴 제안을 도메인 담당자가
검토·승인한 뒤 직접 코드에 반영한다.

## 1. Executive summary

- 대상 엔드포인트 수:
- 발견된 문제 수:
- 6점 대조 중 실행하지 못한 항목:

## 2. 6점 대조 결과

`docs/api/OPENAPI_WRITING_GUIDE.md` §9 기준. 항목별로 도메인 전체를 훑어 문제가
있는 엔드포인트만 나열한다. 문제가 없으면 "이상 없음"으로 남긴다.

| # | 대조 | 결과 | 근거 |
| --- | --- | --- | --- |
| 1 | Controller ↔ ApiSpec | | |
| 2 | ApiSpec ↔ DTO | | |
| 3 | ApiSpec ↔ Service | | |
| 4 | ApiSpec ↔ `docs/error-codes.md` | | |
| 5 | ApiSpec ↔ SecurityConfiguration | | |
| 6 | ApiSpec ↔ `docs/api/openapi.json` | | |

## 3. 엔드포인트별 제안

엔드포인트마다 하나의 절로 기록한다. 문제가 없는 엔드포인트는 표만 채우고
before/after는 생략한다.

### `<HTTP Method> <path>` — `<method명>`

| 항목 | 내용 |
| --- | --- |
| 누락된 오류 응답 | |
| 누락된 `@Schema(description)` 필드 | |
| 문장 기준 위반 | 종결어미 / 낯선 단어 나열 / 내부 불변식 ID 노출 / 사실 오류 중 해당 항목 |

**Before**

```java

```

**After**

```java

```

**변경 근거**

-

## 4. 반영하지 않은 제안

가이드 기준과 다르게 보이지만 도메인 특성상 예외로 남긴 경우와 그 이유를 적는다.

-

## 5. 실행하지 못한 검증

`./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"` 등
실행하지 못한 명령이 있으면 명령, 이유, 남은 위험을 구분해 적는다.

-

## 6. Reviewer checklist

- [ ] 모든 제안 문장이 실제 서비스/DTO 코드로 근거를 확인했다 (추측 없음)
- [ ] `*ApiSpec` 원본을 수정하지 않았다
- [ ] `@Schema(example)`에 비밀값·계정 식별자를 쓰지 않았다
- [ ] 내부 불변식 ID(`INV-*`)가 제안 문장에 남아 있지 않다
- [ ] 6점 대조를 모두 실행했거나, 실행하지 못한 항목을 §5에 기록했다
