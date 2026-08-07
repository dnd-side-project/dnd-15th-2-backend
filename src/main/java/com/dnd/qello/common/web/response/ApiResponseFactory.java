package com.dnd.qello.common.web.response;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;

// 성공 응답 본문을 만드는 유일한 지점.
//
// controller가 반환값을 직접 감싼다. 전역 자동 래핑을 쓰지 않는 이유는
// docs/adr/0005-api-success-response-contract.md에 있다.
@Component
public class ApiResponseFactory {

	private final Clock clock;

	public ApiResponseFactory(Clock clock) {
		this.clock = clock;
	}

	public <T> ApiResponse<T> success(T data) {
		return ApiResponse.success(data, Instant.now(clock));
	}

	// 돌려줄 값이 없는 성공. 204가 아니라 200과 data: null로 나간다.
	public ApiResponse<Void> success() {
		return success(null);
	}
}
