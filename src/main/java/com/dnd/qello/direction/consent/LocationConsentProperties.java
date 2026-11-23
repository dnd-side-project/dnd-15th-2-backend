package com.dnd.qello.direction.consent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qello.location.consent")
public record LocationConsentProperties(String currentTermsVersion) {

	public void validate(boolean collectionEnabled) {
		if (currentTermsVersion == null && !collectionEnabled) {
			return;
		}
		if (currentTermsVersion == null || currentTermsVersion.isBlank() || currentTermsVersion.length() > 100
				|| !currentTermsVersion.equals(currentTermsVersion.strip())) {
			throw new IllegalStateException(
					"qello.location.consent.current-terms-version은 앞뒤 공백 없는 1~100자여야 합니다");
		}
	}
}
