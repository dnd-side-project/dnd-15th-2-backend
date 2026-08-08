package com.dnd.qello.auth.web;

import com.dnd.qello.auth.domain.DevicePlatform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// 기기 최초 등록 요청 본문.
public record DeviceRegistrationRequest(
	@NotBlank(message = "installationId는 필수입니다") String installationId,
	@NotNull(message = "platform은 필수입니다") DevicePlatform platform,
	@NotBlank(message = "countryCode는 필수입니다") String countryCode,
	@NotBlank(message = "coarseRegionCode는 필수입니다") String coarseRegionCode,
	@NotBlank(message = "locale은 필수입니다") String locale,
	@NotBlank(message = "timezone은 필수입니다") String timezone,
	String nickname
) {
}
