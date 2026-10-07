package com.dnd.qello.account.web;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

// 닉네임 변경 요청 본문.
public record ChangeNicknameRequest(
		@NotBlank(message = "nickname은 필수입니다") @Schema(description = "변경할 닉네임. 보이지 않는 문자(zero-width·제어 문자)를 지우고 연속 공백(전각 공백 포함)을 한 칸으로 줄이고 앞뒤 공백을 제거한 뒤 저장합니다. 이모지 결합에 쓰인 ZWJ는 남깁니다. 결과가 비거나 50자를 넘으면 400입니다.", requiredMode = Schema.RequiredMode.REQUIRED, example = "여름바람") String nickname) {
}
