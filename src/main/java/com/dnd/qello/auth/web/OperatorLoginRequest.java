package com.dnd.qello.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 로그인 요청 본문.
//
// toString을 재정의해 password가 로그에 실리지 않게 한다. record의 기본 toString은
// 모든 구성 요소를 그대로 찍는다.
//
// @Schema에 example을 넣지 않는다. 스펙 산출물이 저장소에 커밋되므로 예시로 적은
// 자격증명이 그대로 공개된다.
@Schema(description = "운영자 로그인 요청")
public record OperatorLoginRequest(
	@Schema(description = "운영자 로그인 식별자. 앞뒤 공백을 제거하고 소문자로 변환합니다.")
	@NotBlank(message = "loginId는 필수입니다") String loginId,

	@Schema(description = "평문 비밀번호입니다. 전송 구간은 TLS로 보호합니다.")
	@NotBlank(message = "password는 필수입니다") String password
) {

	@Override
	public String toString() {
		return "OperatorLoginRequest[loginId=%s, password=REDACTED]".formatted(loginId);
	}
}
