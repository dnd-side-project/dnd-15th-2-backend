---
name: "harness-api-docs"
description: "OpenAPI \uc2a4\ud399 \uc0b0\ucd9c\ubb3c\uc744 \uc7ac\uc0dd\uc131\ud558\uace0, \ube60\uc9c4 \uc624\ub958 \uc751\ub2f5\u00b7\uc124\uba85\u00b7\uc778\uc99d \uc694\uad6c\ub97c `*ApiSpec` \uc778\ud130\ud398\uc774\uc2a4 \uc560\ub178\ud14c\uc774\uc158\uc73c\ub85c \ubcf4\uac15\ud55c\ub2e4. \"API \ubb38\uc11c \uac31\uc2e0\", \"\uc2a4\uc6e8\uac70 \ubb38\uc11c \ub9cc\ub4e4\uc5b4\uc918\", \"OpenAPI \uc2a4\ud399 \ucd5c\uc2e0\ud654\" \uc694\uccad\uc5d0 \uc0ac\uc6a9\ud55c\ub2e4."
---

# API Docs Workflow

OpenAPI 스펙을 코드와 일치시키고, 스펙만으로는 알 수 없는 정보를 `*ApiSpec`
인터페이스 애노테이션으로 채운다. 역할 계약은 `agents/api-docs-executor.md`다.

## 핵심 전제

`docs/api/openapi.json`은 **생성물**이다. 직접 편집하지 않는다. springdoc이 실행 중인
애플리케이션에서 뽑아낸다.

**산출물 동기화는 이 스킬의 일이 아니다.** PR을 올리면 `harness-policy.yml`의
`sync-api-docs` job이 스펙을 재생성해 PR 브랜치에 커밋한다. 개발자가 잊어도 CI가
따라 붙인다.

이 스킬이 하는 일은 springdoc이 알 수 없는 정보를 애노테이션으로 채우는 것이다.
공통 규칙(content type, 400·500)은 `OpenApiConventionCustomizer`가 이미 넣으므로
반복해 적지 않는다.

**문서 애노테이션은 컨트롤러가 아니라 `*ApiSpec` 인터페이스에 적는다.** 컨트롤러
`Xxx`의 계약은 같은 패키지의 `XxxApiSpec`이며, 메서드 매핑과 `@Tag`·`@Operation`·
`@ApiResponses`·`@SecurityRequirement`·`@Parameter`가 모두 거기 있다. 컨트롤러에는
`@RestController`, 클래스 수준 `@RequestMapping`과 `@Override` 구현만 남는다.
근거와 확인된 제약은 `docs/api-response.md` 5절에 있다.

## 0. 컨텍스트 수집

```bash
git branch --show-current
git status --short
```

- 브랜치가 `<type>/gh-<ISSUE>-<slug>`가 아니면 중단한다.
- 미커밋 변경이 있으면 알린다. 스펙 재생성이 그 위에 섞이므로 사용자가 먼저
  정리할지 선택하게 한다.

## 1. 현재 스펙 재생성

```bash
./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"
```

Docker가 필요하다. 실패하면 원인을 그대로 보고하고 멈춘다. 스펙을 손으로 만들어
대체하지 않는다.

```bash
git diff --stat -- docs/api/openapi.json
```

재생성만으로 diff가 생겼다면 누군가 코드를 바꾸고 산출물을 갱신하지 않은 것이다.
그 사실을 먼저 보고한다.

## 2. 격차 분석

스펙을 읽고 엔드포인트별로 빠진 항목을 표로 만든다. 커스터마이저가 처리하는
항목은 제외하고, 엔드포인트마다 달라지는 것만 본다.

| 항목 | 확인 방법 | 흔한 결함 |
| --- | --- | --- |
| 엔드포인트별 오류 응답 | `responses` 키 | 공통 400·500만 있고 401·409·423이 없다 |
| operation 설명 | `summary`, `description` | 비어 있다 |
| 인증 요구 | `security` | 선언이 없다 |
| 내부 타입 누출 | `components.schemas` | 프레임워크 타입이 섞인다 |

오류 응답은 **추측하지 않는다.** 근거는 두 곳이다.

1. 해당 서비스가 던지는 `XxxException`과 그 `XxxErrorCode`
2. `docs/error-codes.md`의 코드·상태 표

컨트롤러가 없는 도메인은 "문서화 대상 없음"으로 남긴다. 문서를 채우려고 컨트롤러를
만들지 않는다. 그것은 별도 feature 이슈다.

## 3. 보강안 제시

적용 전에 무엇을 어디에 넣을지 보여주고 승인을 받는다.

```text
POST /admin/login  (OperatorLoginApiSpec)
  + @Operation(summary = "백오피스 운영자 로그인")
  + 401 AUT-APP-001  로그인 실패        근거: OperatorLoginService.loginFailed()
  + 423 AUT-APP-002  잠긴 자격증명      근거: OperatorLoginService.login()
  + 403 AUT-APP-003  비활성 계정        근거: requireActiveAccount()
  + content type을 application/json으로 좁힘
```

사용자가 수정하면 반영한 안을 다시 보여주고 재승인을 받는다.

## 4. 적용과 재생성

승인된 범위만 고친다.

- 문서 애노테이션(`@Operation`, `@ApiResponse`, `@Schema`, `@Parameter`)만 추가한다.
- 대상 컨트롤러에 `*ApiSpec` 인터페이스가 없으면 먼저 만들고 컨트롤러의 매핑을
  옮긴다. 이때 컨트롤러의 동작은 한 줄도 바꾸지 않는다.
- 경로, 상태 코드, 응답 본문 구조를 바꾸지 않는다. 그것은 기능 변경이다.
- 예시 값에 실제 비밀번호, `device_secret`, 토큰, 계정 식별자를 쓰지 않는다.

```bash
./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"
git diff -- docs/api/openapi.json
```

diff에 의도하지 않은 변경이 섞였으면 멈추고 보고한다.

## 5. 검증

```bash
./harness pr-ready --project-tests
```

## 6. 완료 보고

보강한 항목, 여전히 비어 있는 항목, 실행하지 못한 검증을 구분해 보고한다.

커밋은 이 스킬의 범위가 아니다. `/harness-commit`을 제안만 한다. 스펙 산출물과
그것을 바꾼 코드 변경을 같은 커밋에 담는 편이 검토하기 쉽지만, 잊어도 PR의
`sync-api-docs` job이 산출물을 따라 붙인다.

## 금지

- `docs/api/openapi.json`을 직접 편집하지 않는다.
- 승인 없이 컨트롤러나 `*ApiSpec` 인터페이스를 수정하지 않는다.
- 문서 애노테이션을 컨트롤러 본문 쪽에 되돌려 붙이지 않는다.
- 문서화 대상을 만들려고 컨트롤러를 신설하지 않는다.
- 스펙 생성 테스트가 실패한 상태에서 산출물을 손으로 채우지 않는다.
- 서비스, 도메인, repository, 마이그레이션을 수정하지 않는다.
- 예시 값에 `.env` 값, 토큰, 계정 식별자를 쓰지 않는다.
