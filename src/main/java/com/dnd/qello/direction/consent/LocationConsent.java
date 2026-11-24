package com.dnd.qello.direction.consent;

import java.time.Instant;
import java.util.Objects;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

public record LocationConsent(
		Long id, long userId, String termsVersion, LocationConsentAction action, Instant recordedAt) {

	public LocationConsent {
		if ((id != null && id <= 0) || userId <= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_ID, null, "동의 이력 식별자가 유효하지 않습니다");
		}
		if (termsVersion == null || termsVersion.isBlank() || termsVersion.length() > 100
				|| !termsVersion.equals(termsVersion.strip())) {
			throw new DirectionException(DirectionErrorCode.INVALID_TEXT, "termsVersion", "약관 버전 형식이 올바르지 않습니다");
		}
		Objects.requireNonNull(action);
		Objects.requireNonNull(recordedAt);
	}

	public boolean isConsentedTo(String currentTermsVersion) {
		return action == LocationConsentAction.CONSENT && termsVersion.equals(currentTermsVersion);
	}
}
