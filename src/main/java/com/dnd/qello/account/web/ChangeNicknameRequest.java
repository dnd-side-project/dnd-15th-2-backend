package com.dnd.qello.account.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 닉네임 변경 요청 본문.
public record ChangeNicknameRequest(
	@NotBlank(message = "nickname은 필수입니다")
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "여름바람")
	String nickname
) {
}
