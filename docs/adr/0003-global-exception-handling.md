---
id: ADR-0003
title: 도메인 예외를 기능별 오류 코드로 정의하고 전역 처리기에서 응답으로 옮긴다
status: proposed
category: API
date: 2026-08-04
tags:
  - exception
  - error-code
  - api-contract
related:
  - "#51"
---

# ADR-0003. 도메인 예외를 기능별 오류 코드로 정의하고 전역 처리기에서 응답으로 옮긴다

## 배경

기능 코드가 던지는 예외가 모두 `IllegalArgumentException`(131건)과
`IllegalStateException`(19건), `Objects.requireNonNull`(57건)이었다. 이 상태에는
세 가지 문제가 있다.

- 호출자가 어떤 규칙을 위반했는지 예외 타입으로 구분할 수 없고 메시지 문자열에 의존해야 한다.
- 필수값 누락이 `NullPointerException`으로 새어 나가 400이어야 할 실패가 500이 된다.
- HTTP 상태를 결정할 근거가 없어 각 controller가 예외를 개별 처리하게 된다.

Controller 계층을 붙이기 전에 결정해야 한다. 나중에 바꾸면 이미 작성된 API의
오류 응답 형식과 클라이언트 처리 코드를 함께 고쳐야 한다.

## 고려한 선택지

1. 표준 예외를 유지하고 controller advice에서 메시지로 분기
2. Spring `ProblemDetail`(RFC 9457)을 응답 형식으로 사용하고 예외마다 상태를 지정
3. 기능별 오류 코드 enum과 공통 `DomainException`을 정의하고 전역 처리기에서 옮김

## 결정

3번을 선택한다. 적용 범위는 다음과 같다.

- `common/error`에 `ErrorCode` 계약, `ErrorCategory`, 추상 `DomainException`,
  공통 `CommonErrorCode`를 둔다.
- 기능 패키지는 `<feature>/error`에 `XxxErrorCode` enum과 `XxxException` 하나씩만 둔다.
  개별 실패는 예외 타입이 아니라 오류 코드로 구분한다.
- 오류 코드 형식은 `{BC}-{CATEGORY}-{SEQ}`다. 코드 목록은
  `docs/error-codes.md`에서 관리한다.
- `common/web/GlobalExceptionHandler` 한 곳에서 도메인 예외, 요청 검증 실패,
  DB 제약 위반, 미처리 예외를 모두 응답으로 옮긴다.
- 응답 본문은 `ApiErrorResponse`(status/message/errorDetail/timestamp)로 고정한다.

## 선택 이유

- 반복되는 값 검증까지 코드로 나누면 수백 개의 상수가 생긴다. 코드는 의미 단위로 두고
  어떤 값이 왜 틀렸는지는 예외의 `field`와 `reason`으로 전달하면 코드 수를 유지하면서
  구체적인 응답을 만들 수 있다.
- 오류 코드가 HTTP 상태를 함께 가지므로 상태 결정이 한 곳에 모인다. 같은 기능 안에서도
  값 검증은 400, 상태 전이 위반은 409로 자연스럽게 갈린다.
- `ErrorCategory`가 재시도 가치를 구분한다. `INFRA`와 `EXT`는 재시도 후보, `DOM`은
  같은 입력으로 재시도해도 해결되지 않는다는 뜻이므로 알림과 대응 방식을 나눌 근거가 된다.
- 응답과 로그가 같은 코드 값을 쓰므로 클라이언트가 보고한 코드로 로그를 바로 찾을 수 있다.
- `ProblemDetail`(2번)은 표준이지만 확장 필드를 `Map`으로 다뤄야 해서 `code`와 `field`가
  형식으로 강제되지 않는다. 지금은 코드 체계를 강제하는 편이 이득이 크다.

## 결과

### 장점

- 기능이 던지는 예외가 하나뿐이라 `catch` 대상이 명확하다.
- 처리되지 않은 예외가 500으로 수렴하고, 내부 메시지와 스택트레이스는 로그에만 남는다.
- DB 유일성 제약 위반을 제약 이름으로 기능 오류 코드에 연결할 수 있다.

### 단점

- 새 실패를 추가할 때 오류 코드 enum과 문서를 함께 고쳐야 한다.
- 오류 코드의 일련번호는 한 번 배포하면 바꿀 수 없으므로 삭제 대신 사용 중단으로 관리해야 한다.
- `ConstraintExceptionMapper`가 제약 이름을 알아야 해서 공통 패키지가 기능 오류 코드를
  참조한다. 이 방향 참조는 이 클래스에만 허용한다.
- RFC 9457을 따르지 않으므로 표준 형식을 기대하는 도구와는 맞지 않는다.

## 관련 자료

- GitHub Issue: #51
- 문서: `docs/error-codes.md`
