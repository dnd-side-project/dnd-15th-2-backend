---
id: ADR-0005
title: 성공 응답을 ApiResponse로 고정하고 controller가 직접 감싼다
status: proposed
category: API
date: 2026-08-07
tags:
  - api-contract
  - response
related:
  - "#74"
  - "ADR-0003"
---

# ADR-0005. 성공 응답을 ApiResponse로 고정하고 controller가 직접 감싼다

## 배경

ADR-0003으로 오류 응답은 `ApiErrorResponse`(status/message/errorDetail/timestamp)로
고정됐지만 성공 응답에는 계약이 없다. `ApiErrorResponse.status`가 `"error"` 리터럴을
쓰는 것에서 보듯 대칭되는 성공 형식을 전제한 설계인데 그쪽이 비어 있다.

controller가 아직 하나도 없다. 지금 정하면 이어질 모든 엔드포인트가 처음부터 같은
계약을 상속한다. 나중에 바꾸면 이미 작성된 API와 클라이언트 처리 코드를 함께 고쳐야
한다.

`ApiErrorResponse`가 `Instant.now()`를 직접 호출하는 문제도 함께 있다. 이 저장소에는
이미 `Clock` 빈이 있고 통합 테스트가 이를 고정해 쓰는데, 응답의 시각만 그 경로를
우회해서 테스트가 값을 고정할 수 없다.

## 고려한 선택지

1. controller가 `ApiResponseFactory`로 직접 감싸고, 반환 타입 규약을 테스트로 강제
2. `ResponseBodyAdvice`로 전역 자동 래핑
3. 성공 응답을 감싸지 않고 DTO를 그대로 반환 (오류만 공통 형식)

## 결정

1번을 선택한다. 적용 범위는 다음과 같다.

- `common/web/response`에 `ApiResponse<T>`(status/data/timestamp)와, 성공·오류가
  공유하는 `ApiStatus` enum을 둔다. Spring의 `@ResponseStatus`와 이름이 겹치지 않게
  `Api` 접두사를 쓴다.
- `ApiResponseFactory`를 유일한 생성 지점으로 두고 `Clock`을 주입받는다.
  `ApiErrorResponseFactory`도 같은 방식으로 정리한다.
- `Clock` 빈을 `common/persistence/JpaAuditingConfiguration`에서
  `common/time/ClockConfiguration`으로 옮긴다. web 계층이 persistence 설정에
  의존하지 않게 하고, 감사 시각과 응답 시각의 원천을 한곳으로 모은다.
- 204 No Content를 사용하지 않는다. 돌려줄 값이 없는 성공도 200과 `data: null`이다.
- `data`가 `null`이어도 키를 남긴다.
- `ApiResponseConventionTest`가 모든 `@RestController` handler의 반환 타입이
  `ApiResponse<T>` 또는 `ResponseEntity<ApiResponse<T>>`인지 검사한다.

## 선택 이유

- **오류 경로가 advice를 그대로 통과한다.** `ExceptionHandlerExceptionResolver`도
  `RequestResponseBodyMethodProcessor`를 거치므로 `GlobalExceptionHandler`가 반환한
  `ApiErrorResponse`가 `ResponseBodyAdvice`에 들어온다. 2번을 택하면
  `instanceof ApiErrorResponse` 제외 처리가 필수이고, 그게 빠지면 `data` 안에 오류가
  중첩된다. 계약을 지키기 위해 예외 규칙이 필요한 구조가 된다.
- **`String` 반환이 `ClassCastException`이 된다.** 메시지 컨버터는 선언 반환
  타입으로 먼저 선택되므로 `StringHttpMessageConverter`가 골라진 뒤 advice가
  `ApiResponse`를 돌려주면 그대로 터진다. 자동 래핑은 반환 타입 제약을 코드가 아니라
  런타임 실패로 알린다.
- **actuator와 springdoc이 지금은 없지만 붙는 순간 제외 목록이 늘어난다.** 2번의
  `supports()`는 시간이 지날수록 예외 목록이 자라는 자리다.
- **2번의 이점은 테스트로 대체할 수 있다.** "빠뜨릴 수 없다"가 자동 래핑의 유일한
  실질 이점인데, 반환 타입 규약 테스트가 같은 보장을 준다. 대신 실패가 런타임이 아니라
  빌드 시점에 난다.
- **저장소의 기존 패턴과 맞는다.** `ApiErrorResponseFactory`를 `@Component`로 두고
  `GlobalExceptionHandler`에 주입하는 구조가 이미 있다. 성공 쪽도 같은 모양이면
  읽는 사람이 새 개념을 배우지 않는다.
- **3번은 클라이언트가 성공과 실패에서 다른 파서를 쓰게 만든다.** `status` 하나로
  분기할 수 있다는 이점을 버리게 된다.
- **204를 쓰지 않는 이유**는 본문이 금지된 상태라 "모든 성공 응답이 같은 형식"과
  정면으로 충돌하기 때문이다. RESTful 관점에서 200 + 본문도 삭제 응답으로 적법하다.

## 결과

### 장점

- 성공·오류 응답이 `status`와 `timestamp`를 공유해 클라이언트가 본문만으로 분기한다.
- handler 시그니처에 `ApiResponse<T>`가 드러나므로 springdoc을 붙일 때 제네릭 스키마가
  그대로 나온다.
- 응답 시각과 감사 시각이 같은 `Clock` 빈에서 나와 테스트가 한 번에 고정한다.
- 상태 코드별로 본문 형식이 갈리지 않는다.

### 단점

- controller마다 `ApiResponseFactory`를 주입해야 한다.
- 반환 타입을 손으로 맞춰야 하고, 규약 테스트가 없으면 빠뜨릴 수 있다.
- 204를 쓰지 않으므로 본문이 필요 없는 응답에도 몇 바이트를 더 보낸다.
- 규약 테스트가 classpath를 스캔해 main 산출물만 걸러내므로 빌드 산출물 경로에
  의존한다.

## 관련 자료

- GitHub Issue: #74
- 문서: `docs/api-response.md`, `docs/error-codes.md`
- 선행 결정: `docs/adr/0003-global-exception-handling.md`
