package com.dnd.qello.direction.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record LocationConsentRequest(
		@Schema(description = "서버의 현재 위치정보 이용 약관 버전", example = "dev-v1") @NotBlank @Size(max = 100) @Pattern(regexp = "\\S(?:[\\s\\S]*\\S)?") String termsVersion) {
}
