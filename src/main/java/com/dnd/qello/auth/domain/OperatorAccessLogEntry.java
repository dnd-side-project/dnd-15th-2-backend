package com.dnd.qello.auth.domain;

import java.time.Instant;
import java.util.Objects;

import com.dnd.qello.auth.error.AuthErrorCode;

/**
 * 운영자 접근 기록 한 건(#375). 한 번 만들어지면 바뀌지 않는다.
 *
 * <p>
 * 검증은 V34의 CHECK와 같은 조건이다. 애플리케이션에서 먼저 거절해 잘못된 값이 insert 오류로 바뀌지 않게 한다.
 *
 * @param operatorUserId
 *            운영자 계정 id. 계정을 특정하지 못한 로그인 실패만 null이다
 * @param resultCode
 *            로그인 실패의 오류 코드. 그 밖의 이벤트는 null이다
 */
public record OperatorAccessLogEntry(
		OperatorAccessEvent eventType,
		Long operatorUserId,
		OperatorAccessRequest request,
		int httpStatus,
		String resultCode,
		Instant occurredAt) {

	private static final int SUCCESS_STATUS = 200;

	public OperatorAccessLogEntry {
		Objects.requireNonNull(eventType, "eventType");
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(occurredAt, "occurredAt");
		if (operatorUserId == null && eventType.requiresOperator()) {
			throw new IllegalArgumentException(eventType + "에는 운영자 id가 필요합니다");
		}
		if (operatorUserId != null && operatorUserId <= 0) {
			throw new IllegalArgumentException("운영자 id는 양수여야 합니다");
		}
		if (httpStatus < 100 || httpStatus > 599) {
			throw new IllegalArgumentException("HTTP 상태가 범위를 벗어났습니다: " + httpStatus);
		}
		if (resultCode != null && resultCode.isBlank()) {
			throw new IllegalArgumentException("결과 코드는 비어 있을 수 없습니다");
		}
	}

	public static OperatorAccessLogEntry loginSucceeded(
			long operatorUserId, OperatorAccessRequest request, Instant occurredAt) {
		return new OperatorAccessLogEntry(
				OperatorAccessEvent.LOGIN_SUCCEEDED, operatorUserId, request, SUCCESS_STATUS, null, occurredAt);
	}

	public static OperatorAccessLogEntry loginFailed(
			Long operatorUserId, AuthErrorCode errorCode, OperatorAccessRequest request, Instant occurredAt) {
		return new OperatorAccessLogEntry(OperatorAccessEvent.LOGIN_FAILED, operatorUserId, request,
				errorCode.httpStatus().value(), errorCode.code(), occurredAt);
	}

	public static OperatorAccessLogEntry logout(long operatorUserId, OperatorAccessRequest request,
			Instant occurredAt) {
		return new OperatorAccessLogEntry(
				OperatorAccessEvent.LOGOUT, operatorUserId, request, SUCCESS_STATUS, null, occurredAt);
	}

	public static OperatorAccessLogEntry apiCall(
			long operatorUserId, OperatorAccessRequest request, int httpStatus, Instant occurredAt) {
		return new OperatorAccessLogEntry(
				OperatorAccessEvent.API_CALL, operatorUserId, request, httpStatus, null, occurredAt);
	}
}
