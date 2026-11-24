package com.dnd.qello.direction.web.response;

import java.time.Instant;

import com.dnd.qello.direction.consent.LocationConsentAction;
import com.dnd.qello.direction.consent.LocationConsentService.CurrentConsent;

import io.swagger.v3.oas.annotations.media.Schema;

public record LocationConsentResponse(
		@Schema(description = "현재 위치정보 이용 약관 버전. 미게시 상태에서는 null입니다.", nullable = true) String currentTermsVersion,
		@Schema(description = "최신 이력이 현재 버전에 대한 동의인지 여부") boolean consented,
		@Schema(description = "마지막 동의 또는 철회. 이력이 없으면 null입니다.", nullable = true) LocationConsentAction lastAction,
		@Schema(description = "마지막 이력의 약관 버전. 이력이 없으면 null입니다.", nullable = true) String lastTermsVersion,
		@Schema(description = "마지막 이력의 서버 기록 시각. 이력이 없으면 null입니다.", nullable = true) Instant recordedAt) {

	public static LocationConsentResponse from(CurrentConsent consent) {
		return new LocationConsentResponse(consent.currentTermsVersion(), consent.consented(), consent.lastAction(),
				consent.lastTermsVersion(), consent.recordedAt());
	}
}
