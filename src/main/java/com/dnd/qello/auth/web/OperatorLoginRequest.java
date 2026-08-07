package com.dnd.qello.auth.web;

import jakarta.validation.constraints.NotBlank;

// 로그인 요청 본문.
//
// toString을 재정의해 password가 로그에 실리지 않게 한다. record의 기본 toString은
// 모든 구성 요소를 그대로 찍는다.
public record OperatorLoginRequest(
	@NotBlank(message = "loginId는 필수입니다") String loginId,
	@NotBlank(message = "password는 필수입니다") String password
) {

	@Override
	public String toString() {
		return "OperatorLoginRequest[loginId=%s, password=REDACTED]".formatted(loginId);
	}
}
