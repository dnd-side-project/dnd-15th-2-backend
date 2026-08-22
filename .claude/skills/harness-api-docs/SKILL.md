---
name: harness-api-docs
description: OpenAPI 스펙 산출물을 재생성하고, 빠진 오류 응답·설명·인증 요구를 `*ApiSpec` 인터페이스 애노테이션으로 보강한다. "API 문서 갱신", "스웨거 문서 만들어줘", "OpenAPI 스펙 최신화" 요청에 사용한다.
allowed-tools:
   - Read
   - Glob
   - Grep
   - Edit
   - Write
   - Bash(./gradlew *)
   - Bash(git diff *)
   - Bash(git status *)
   - Bash(./harness *)
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

## 모드

| 모드 | 무엇을 하는가 | 산출물 | `*ApiSpec` 수정 |
| --- | --- | --- | --- |
| 보강(enrich) | 코드에 실제로 있는데 스펙에 없는 오류 응답·설명·인증 요구를 승인받아 애노테이션으로 채운다 | 코드 diff, 재생성된 `docs/api/openapi.json` | 한다 |
| 검토(review) | `docs/api/OPENAPI_WRITING_GUIDE.md` 기준으로 기존 설명 문장·용어를 점검하고 제안만 만든다 | `templates/api-docs-review.md` 사본 (`docs/reports/gh-<ISSUE>-API-DOCS-REVIEW-<DOMAIN>.md`) | 하지 않는다 |

기본은 보강 모드다. "검토", "리뷰", "문장 점검", "가이드대로 봐줘" 같은 요청이면
검토 모드로 전환한다. 두 모드 모두 0단계(컨텍스트 수집)를 공유한다.

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

## 검토 모드

문장 기준은 `docs/api/OPENAPI_WRITING_GUIDE.md`가 원본이다. 이 절은 절차만
정의하고 기준 자체를 복제하지 않는다.

### R1. 대상 도메인 확인

검토할 `*ApiSpec` 하나 또는 도메인 하나를 사용자와 확정한다. 대상 컨트롤러,
DTO, 서비스 파일 경로를 함께 확인한다.

### R2. 6점 대조

`docs/api/OPENAPI_WRITING_GUIDE.md` §9 체크리스트를 순서대로 실행한다.

1. Controller ↔ ApiSpec: 메서드·매핑 수 일치 확인
2. ApiSpec ↔ DTO: 필드별 `@Schema(description)` 존재 확인
3. ApiSpec ↔ Service: `throw new XxxException(...)`를 전수 grep해 실제로 낼 수
   있는 오류 응답을 확인한다. **DTO나 상상만으로 오류 응답을 만들지 않는다.**
4. ApiSpec ↔ `docs/error-codes.md`: 코드·HTTP 상태 일치 확인
5. ApiSpec ↔ SecurityConfiguration: 인증 스킴과 `permitAll` 경로 확인
6. ApiSpec ↔ `docs/api/openapi.json`: 재생성 후 diff로 실제 반영 여부 확인

### R3. 문장 재작성 제안

`docs/api/OPENAPI_WRITING_GUIDE.md` §1~§8 기준(종결어미, `summary` 형식, 문단
순서, 낯선 단어, 괄호 규칙, 금지 문장)으로 엔드포인트별 before/after를 작성한다.

쉬운 말로 바꾸는 과정에서 DTO에 없는 사실을 지어내지 않는다. 필드를 다시 확인하지
않고 요청·응답에 없는 내용을 있는 것처럼 쓰면 문장은 쉬워져도 틀린 문서가 된다.

### R4. 보고서 작성

`templates/api-docs-review.md`를 복사해
`docs/reports/gh-<ISSUE>-API-DOCS-REVIEW-<DOMAIN>.md`로 저장하고 R2·R3 결과를
채운다. **`*ApiSpec` 원본은 이 모드에서 수정하지 않는다.** 제안 문장은 보고서
안에만 있는다.

### R5. 완료 보고

대조 결과, 제안 건수, 실행하지 못한 검증(예: Docker 미가용으로 스펙 재생성
불가)을 구분해 보고한다. `*ApiSpec` 적용은 도메인 담당자의 후속 작업임을 명시한다.

## 금지

- `docs/api/openapi.json`을 직접 편집하지 않는다.
- 승인 없이 컨트롤러나 `*ApiSpec` 인터페이스를 수정하지 않는다.
- 문서 애노테이션을 컨트롤러 본문 쪽에 되돌려 붙이지 않는다.
- 문서화 대상을 만들려고 컨트롤러를 신설하지 않는다.
- 스펙 생성 테스트가 실패한 상태에서 산출물을 손으로 채우지 않는다.
- 서비스, 도메인, repository, 마이그레이션을 수정하지 않는다.
- 예시 값에 `.env` 값, 토큰, 계정 식별자를 쓰지 않는다.
- 검토 모드에서는 `*ApiSpec`, 컨트롤러, DTO, 어떤 소스 코드도 수정하지 않는다.
  산출물은 `docs/reports/**`의 보고서 하나뿐이다.
- 검토 모드에서 오류 응답을 제안할 때 서비스 코드의 `throw` 없이 추측해 적지 않는다.
