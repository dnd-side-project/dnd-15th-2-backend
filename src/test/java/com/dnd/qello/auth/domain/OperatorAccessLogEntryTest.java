/*
 * Created at: 2026-11-22T22:02:56+09:00
 * Source scenario: TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-016
 */
package com.dnd.qello.auth.domain;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.dnd.qello.auth.error.AuthErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorAccessLogEntryTest {

	private static final Instant NOW = Instant.parse("2026-11-22T13:00:00Z");
	private static final OperatorAccessRequest REQUEST = new OperatorAccessRequest("192.0.2.10", "POST",
			"/admin/login");

	@Test
	@DisplayName("로그인 실패 항목은 오류 코드의 HTTP 상태와 코드를 그대로 담는다")
	void loginFailureCarriesErrorStatusAndCode() {
		OperatorAccessLogEntry entry = OperatorAccessLogEntry.loginFailed(7L, AuthErrorCode.CREDENTIAL_LOCKED, REQUEST,
				NOW);

		assertThat(entry.eventType()).isEqualTo(OperatorAccessEvent.LOGIN_FAILED);
		assertThat(entry.operatorUserId()).isEqualTo(7L);
		assertThat(entry.httpStatus()).isEqualTo(423);
		assertThat(entry.resultCode()).isEqualTo("AUT-APP-002");
		assertThat(entry.request()).isEqualTo(REQUEST);
		assertThat(entry.occurredAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("계정을 특정하지 못한 로그인 실패는 운영자 id 없이 만들 수 있다")
	void loginFailureAllowsUnknownOperator() {
		OperatorAccessLogEntry entry = OperatorAccessLogEntry.loginFailed(null, AuthErrorCode.LOGIN_FAILED, REQUEST,
				NOW);

		assertThat(entry.operatorUserId()).isNull();
		assertThat(entry.httpStatus()).isEqualTo(401);
		assertThat(entry.resultCode()).isEqualTo("AUT-APP-001");
	}

	@Test
	@DisplayName("성공·로그아웃·API 호출 항목은 결과 코드가 없고 주어진 상태를 담는다")
	void nonFailureEntriesHaveNoResultCode() {
		OperatorAccessLogEntry succeeded = OperatorAccessLogEntry.loginSucceeded(7L, REQUEST, NOW);
		OperatorAccessLogEntry logout = OperatorAccessLogEntry.logout(7L, REQUEST, NOW);
		OperatorAccessLogEntry apiCall = OperatorAccessLogEntry.apiCall(7L, REQUEST, 404, NOW);

		assertThat(succeeded.eventType()).isEqualTo(OperatorAccessEvent.LOGIN_SUCCEEDED);
		assertThat(succeeded.httpStatus()).isEqualTo(200);
		assertThat(logout.eventType()).isEqualTo(OperatorAccessEvent.LOGOUT);
		assertThat(logout.httpStatus()).isEqualTo(200);
		assertThat(apiCall.eventType()).isEqualTo(OperatorAccessEvent.API_CALL);
		assertThat(apiCall.httpStatus()).isEqualTo(404);
		assertThat(succeeded.resultCode()).isNull();
		assertThat(logout.resultCode()).isNull();
		assertThat(apiCall.resultCode()).isNull();
	}

	@ParameterizedTest
	@DisplayName("로그인 실패가 아닌 이벤트는 운영자 id가 없으면 거절한다")
	@EnumSource(value = OperatorAccessEvent.class, names = {"LOGIN_SUCCEEDED", "LOGOUT", "API_CALL"})
	void rejectsMissingOperatorForAuthenticatedEvents(OperatorAccessEvent eventType) {
		assertThatThrownBy(() -> new OperatorAccessLogEntry(eventType, null, REQUEST, 200, null, NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@DisplayName("0 이하의 운영자 id는 거절한다")
	@ValueSource(longs = {0L, -1L})
	void rejectsNonPositiveOperatorId(long operatorUserId) {
		assertThatThrownBy(() -> new OperatorAccessLogEntry(
				OperatorAccessEvent.LOGIN_FAILED, operatorUserId, REQUEST, 401, "AUT-APP-001", NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@DisplayName("100~599 밖의 HTTP 상태는 거절한다")
	@ValueSource(ints = {99, 600})
	void rejectsOutOfRangeHttpStatus(int httpStatus) {
		assertThatThrownBy(() -> new OperatorAccessLogEntry(
				OperatorAccessEvent.API_CALL, 7L, REQUEST, httpStatus, null, NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("공백뿐인 결과 코드는 거절한다")
	void rejectsBlankResultCode() {
		assertThatThrownBy(() -> new OperatorAccessLogEntry(
				OperatorAccessEvent.LOGIN_FAILED, 7L, REQUEST, 401, "  ", NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("이벤트 유형·요청·발생 시각이 null이면 NullPointerException이다")
	void rejectsNullRequiredComponents() {
		assertThatThrownBy(() -> new OperatorAccessLogEntry(null, 7L, REQUEST, 200, null, NOW))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new OperatorAccessLogEntry(
				OperatorAccessEvent.API_CALL, 7L, null, 200, null, NOW))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new OperatorAccessLogEntry(
				OperatorAccessEvent.API_CALL, 7L, REQUEST, 200, null, null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("요청 정보는 method를 대문자로 바꾸고 값을 그대로 보관한다")
	void requestKeepsValuesAndUppercasesMethod() {
		OperatorAccessRequest request = new OperatorAccessRequest("192.0.2.10", "get", "/admin/questions");

		assertThat(request.clientAddress()).isEqualTo("192.0.2.10");
		assertThat(request.httpMethod()).isEqualTo("GET");
		assertThat(request.requestPath()).isEqualTo("/admin/questions");
	}

	@Test
	@DisplayName("요청 정보는 null이면 NullPointerException, 공백이면 IllegalArgumentException이다")
	void requestRejectsMissingValues() {
		assertThatThrownBy(() -> new OperatorAccessRequest(null, "GET", "/admin/questions"))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new OperatorAccessRequest("192.0.2.10", " ", "/admin/questions"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OperatorAccessRequest("192.0.2.10", "GET", ""))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("요청 정보는 컬럼 길이를 넘는 값을 상한까지 잘라 둔다")
	void requestTruncatesToColumnLimits() {
		OperatorAccessRequest request = new OperatorAccessRequest(
				"a".repeat(100), "X".repeat(40), "/" + "p".repeat(700));

		assertThat(request.clientAddress()).hasSize(OperatorAccessRequest.MAX_CLIENT_ADDRESS_LENGTH);
		assertThat(request.httpMethod()).hasSize(OperatorAccessRequest.MAX_HTTP_METHOD_LENGTH);
		assertThat(request.requestPath()).hasSize(OperatorAccessRequest.MAX_REQUEST_PATH_LENGTH);
	}
}
