package com.dnd.qello.auth.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * 접근 기록에 남길 요청 정보. 웹 계층이 HTTP 요청에서 만들어 넘긴다.
 *
 * <p>
 * query string, 헤더, 쿠키, 본문은 담지 않는다. 컬럼 길이를 넘는 값은 상한까지 잘라 둔다. 길이 초과로 insert가
 * 실패하면 그 실패가 로그인 실패로 번지기 때문이다.
 */
public record OperatorAccessRequest(String clientAddress, String httpMethod, String requestPath) {

	public static final int MAX_CLIENT_ADDRESS_LENGTH = 64;
	public static final int MAX_HTTP_METHOD_LENGTH = 16;
	public static final int MAX_REQUEST_PATH_LENGTH = 512;

	public OperatorAccessRequest {
		clientAddress = truncate(requireText(clientAddress, "clientAddress"), MAX_CLIENT_ADDRESS_LENGTH);
		httpMethod = truncate(requireText(httpMethod, "httpMethod").toUpperCase(Locale.ROOT), MAX_HTTP_METHOD_LENGTH);
		requestPath = truncate(requireText(requestPath, "requestPath"), MAX_REQUEST_PATH_LENGTH);
	}

	private static String requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + "은 비어 있을 수 없습니다");
		}
		return value;
	}

	private static String truncate(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}
}
