package com.dnd.qello.common.error;

// DB 제약 위반을 옮길 오류 코드와 관련 필드.
//
// errorCode - 응답에 사용할 오류 코드
// field     - 충돌한 값의 이름. 특정 불가 시 null
public record ConstraintMapping(
	ErrorCode errorCode,
	String field
) {
}
