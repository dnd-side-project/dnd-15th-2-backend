package com.dnd.qello.common.error;

import org.springframework.http.HttpStatus;

// 기능 패키지가 정의하는 오류 코드 계약.
//
// 구현은 기능별 enum(예: AccountErrorCode), GlobalExceptionHandler에는
// 이 인터페이스로만 노출.
public interface ErrorCode {

	// 이 오류를 HTTP 응답으로 옮길 때 사용할 상태 코드
	HttpStatus httpStatus();

	// {BC}-{CATEGORY}-{SEQ} 형식의 안정적인 식별자. 로그와 응답에서 동일한 값 사용
	String code();

	// 운영 대응 방식을 가르는 분류
	ErrorCategory category();

	// 사용자와 운영 로그에 노출해도 안전한 메시지
	String message();
}
