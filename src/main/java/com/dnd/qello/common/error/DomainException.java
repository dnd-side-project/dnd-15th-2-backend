package com.dnd.qello.common.error;

// 기능 패키지 예외의 최상위 타입.
//
// 기능 패키지는 이 클래스를 상속한 예외 하나만 두고, 개별 실패는 ErrorCode로 구분.
// 반복되는 값 검증은 코드를 늘리지 않고 field와 reason으로 어느 값이 왜 틀렸는지 전달.
public abstract class DomainException extends RuntimeException {

	private final ErrorCode errorCode;
	private final String field;
	private final String reason;

	protected DomainException(ErrorCode errorCode) {
		this(errorCode, null, null, null, null);
	}

	protected DomainException(ErrorCode errorCode, String field, String reason) {
		this(errorCode, field, reason, null, null);
	}

	protected DomainException(ErrorCode errorCode, String field, String reason, String message) {
		this(errorCode, field, reason, message, null);
	}

	// field   - 실패한 값의 이름. 값 단위 실패가 아니면 null
	// reason  - 응답 errorDetail.reason에 쓸 값. null이면 코드 기본 메시지
	// message - 응답 최상단 message에 쓸 값. null이면 코드 기본 메시지
	// cause   - 원래 예외. 없으면 null
	protected DomainException(
		ErrorCode errorCode,
		String field,
		String reason,
		String message,
		Throwable cause
	) {
		super(message != null ? message : errorCode.message(), cause);
		this.errorCode = errorCode;
		this.field = field;
		this.reason = reason != null ? reason : errorCode.message();
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public String getField() {
		return field;
	}

	public String getReason() {
		return reason;
	}
}
