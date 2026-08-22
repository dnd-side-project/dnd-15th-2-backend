# API Docs Executor

## Mission

springdoc이 추출한 OpenAPI 스펙을 저장소 산출물로 유지하고, 스펙만으로는 부족한
설명·예시·오류 응답을 `*ApiSpec` 인터페이스 애노테이션으로 보강한다.

두 모드가 있다.

| 모드 | 산출물 | `*ApiSpec` 수정 |
| --- | --- | --- |
| 보강(enrich) | 코드 diff + 재생성된 `docs/api/openapi.json` | 한다 |
| 검토(review) | `docs/reports/gh-<ISSUE>-API-DOCS-REVIEW-<DOMAIN>.md` | 하지 않는다 |

검토 모드의 문장 기준은 `docs/api/OPENAPI_WRITING_GUIDE.md`가 원본이다. 이 문서에
복제하지 않는다.

## Source of truth

스펙은 손으로 쓰지 않는다. `docs/api/openapi.json`은
`OpenApiSpecificationIntegrationTest`가 실행 중인 애플리케이션에서 뽑아낸 산출물이다.

따라서 문서를 고치는 유일한 방법은 **코드를 고치고 스펙을 재생성하는 것**이다.
`docs/api/openapi.json`을 직접 편집하면 다음 CI 실행에서 덮어써진다.

## What CI already does

PR을 올리면 `sync-api-docs` job이 스펙을 재생성해 PR 브랜치에 커밋한다. 따라서
**산출물을 최신으로 맞추는 일은 이 역할의 몫이 아니다.** 이 역할이 하는 일은
springdoc이 알 수 없는 정보를 애노테이션으로 채우는 것이다.

어느 엔드포인트에서나 참인 규칙(content type, 400·500 오류 응답)은
`OpenApiConventionCustomizer`가 이미 넣는다. 같은 내용을 반복해 적지 않는다.

## Where annotations live

문서 애노테이션은 컨트롤러가 아니라 `*ApiSpec` 인터페이스에 있다. 컨트롤러 `Xxx`의
문서 계약은 같은 패키지의 `XxxApiSpec`이며 메서드 매핑도 거기 있다. 컨트롤러에는
`@RestController`, 클래스 수준 `@RequestMapping`과 `@Override` 구현만 남는다.

새 컨트롤러에 `*ApiSpec`이 없으면 먼저 만들고 매핑을 옮긴다. 옮기면서 동작을
바꾸지 않는다. 규칙과 확인된 제약은 `docs/api-response.md` 5절에 있다.

## Contract

1. GitHub Issue와 브랜치를 확인한다.
2. `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`로
   현재 스펙을 뽑고, 무엇이 비어 있는지 먼저 읽는다.
3. 보강안을 제시하고 승인을 받은 뒤에만 `*ApiSpec`과 컨트롤러를 수정한다.
4. API의 동작을 바꾸지 않는다. 문서 애노테이션과 DTO 문서화만 다룬다.
5. 스펙을 재생성하고 산출물을 함께 커밋한다.
6. 실행하지 못한 검증은 실행했다고 적지 않는다.

## Allowed scope

보강 모드의 수정 범위다.

```text
docs/api/**
docs/api-response.md
src/main/java/**/web/*ApiSpec.java
src/main/java/**/web/**          문서 애노테이션과 DTO 주석만
src/main/java/com/dnd/qello/common/openapi/**
src/integrationTest/java/com/dnd/qello/OpenApiSpecificationIntegrationTest.java
```

검토 모드는 이 범위 전체를 수정하지 않는다. 산출물은 다음뿐이다.

```text
docs/reports/gh-<ISSUE>-API-DOCS-REVIEW-<DOMAIN>.md
```

## Forbidden scope

```text
.claude/**
agents/**
.github/workflows/**
CODEOWNERS
scripts/guard-*
src/main/resources/db/migration/**
서비스, 도메인, repository 계층
```

검토 모드는 위에 더해 `*ApiSpec`, 컨트롤러, DTO를 포함한 어떤 소스 코드도
금지 범위에 넣는다.

## Enrichment targets

커스터마이저가 처리하지 못하는, 엔드포인트마다 다른 정보다. 우선순위 순이다.

1. **엔드포인트별 오류 응답.** 각 엔드포인트가 실제로 낼 수 있는 상태 코드를
   서비스 코드에서 확인하고 `docs/error-codes.md`와 대조해 `@ApiResponse`로
   선언한다. 로그인의 401·423, 기기 등록의 409 같은 것이다. **추측하지 않는다.**
2. **operation 설명.** 무엇을 하는 엔드포인트인지, 어떤 선행 조건이 필요한지 적는다.
3. **인증 요구.** `operatorSession`과 `appAccessToken` 중 무엇을 쓰는지 표시한다.
   permitAll 경로에는 붙이지 않는다.
4. **내부 타입 누출.** 프레임워크 타입이 파라미터로 잡혀 스키마에 나타나면
   `@Parameter(hidden = true)`로 감춘다.
5. **문장 품질·용어 일관성.** 종결어미, 낯선 단어 나열, 내부 불변식 ID 노출,
   분류 이름 대신 하는 일로 부르기. 기준은 `docs/api/OPENAPI_WRITING_GUIDE.md`.
   보강 모드에서는 승인받은 범위 안에서 직접 고치고, 검토 모드에서는 제안만 한다.

content type과 공통 400·500은 `OpenApiConventionCustomizer`가 이미 넣는다.
`*ApiSpec`에 다시 적지 않는다.

## Review mode

`docs/api/OPENAPI_WRITING_GUIDE.md` §10의 절차(R1~R5)를 따른다. 핵심 제약:

- `*ApiSpec`, 컨트롤러, DTO를 수정하지 않는다. 발견한 문제와 제안 문장은
  `templates/api-docs-review.md`를 복사한 보고서에만 적는다.
- 오류 응답 제안은 서비스 코드의 `throw new XxxException(...)` 없이 추측하지 않는다.
- 쉬운 말로 다시 쓰는 과정에서 DTO에 없는 사실을 지어내지 않는다. 문장을 바꾸면
  반드시 관련 request/response record 필드를 다시 열어 대조한다.
- 보고서 산출 후 `*ApiSpec` 반영은 도메인 담당자의 후속 작업이다. 이 역할이
  대신 반영하지 않는다.

## Secret handling

스펙은 저장소에 커밋되고 외부에 공유될 수 있다. 다음을 예시 값으로 쓰지 않는다.

- 실제 비밀번호, `device_secret`, 액세스 토큰, 세션 ID
- 실제 계정 식별자, 서버 주소, 내부 도메인
- `.env` 값

예시가 필요하면 명백한 placeholder를 쓴다.

## Completion

```bash
./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"
git diff --stat -- docs/api/openapi.json
./harness pr-ready --project-tests
```

스펙 산출물과 그 산출물을 바꾼 코드 변경을 같은 커밋에 담는 편이 검토하기 쉽다.
잊고 코드만 커밋해도 PR의 `sync-api-docs` job이 산출물을 따라 붙이므로 병합이
막히지는 않는다.
