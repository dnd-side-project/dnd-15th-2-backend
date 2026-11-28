package com.dnd.qello.auth.web;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

// 운영자 비밀번호 변경 요청 본문(#376).
//
// OperatorLoginRequest와 같은 이유로 toString에서 두 비밀번호를 가리고, @Schema에 example을 넣지 않는다.
// 작성 규칙은 서비스(OperatorPasswordPolicy)가 검사한다. 여기서 길이를 막으면 규칙 위반이 AUT-VAL-005가 아닌
// 공통 검증 오류로 나간다.
@Schema(description = "운영자 비밀번호 변경 요청")
public record OperatorPasswordChangeRequest(
		@Schema(description = "현재 비밀번호입니다.") @NotBlank(message = "currentPassword는 필수입니다") String currentPassword,

		@Schema(description = "새 비밀번호입니다. 영문 대문자·소문자·숫자·특수문자 중 2종류 이상이면 10자 이상, "
				+ "3종류 이상이면 8자 이상이어야 하고 72자를 넘을 수 없습니다. 공백을 포함한 ASCII 문자만 쓸 수 있고, "
				+ "로그인 식별자나 현재 비밀번호와 같은 값은 쓸 수 없습니다.") @NotBlank(message = "newPassword는 필수입니다") String newPassword) {

	@Override
	public String toString() {
		return "OperatorPasswordChangeRequest[currentPassword=REDACTED, newPassword=REDACTED]";
	}
}
