package com.dnd.qello.auth.web;

import com.dnd.qello.auth.domain.DevicePlatform;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// 기기 최초 등록 요청 본문.
@Schema(description = "새 계정과 기기 자격증명을 만들 때 보내는 등록 정보")
public record DeviceRegistrationRequest(
	@Schema(description = "앱 설치를 식별하는 값")
	@NotBlank(message = "installationId는 필수입니다") String installationId,
	@Schema(description = "기기를 등록한 앱 플랫폼입니다. IOS 또는 ANDROID입니다.")
	@NotNull(message = "platform은 필수입니다") DevicePlatform platform,
	@Schema(description = "ISO 3166-1 alpha-2 국가 코드입니다. 기준 지역의 최상위 국가와 일치해야 합니다.")
	@NotBlank(message = "countryCode는 필수입니다") String countryCode,
	@Schema(description = "국가 안의 대략적인 지역 코드")
	@NotBlank(message = "coarseRegionCode는 필수입니다") String coarseRegionCode,
	@Schema(description = "계정의 언어·지역 설정")
	@NotBlank(message = "locale은 필수입니다") String locale,
	@Schema(description = "계정의 IANA 시간대 식별자")
	@NotBlank(message = "timezone은 필수입니다") String timezone,
	@Schema(description = "선택할 수 있는 계정 닉네임")
	String nickname
) {
}
