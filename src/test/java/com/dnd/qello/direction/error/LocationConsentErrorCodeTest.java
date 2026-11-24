/**
 * Created at: 2026-11-23T13:41:12Z
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-UNIT-009
 */
package com.dnd.qello.direction.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.dnd.qello.common.error.ErrorCategory;

import static org.assertj.core.api.Assertions.assertThat;

class LocationConsentErrorCodeTest {

	@Test
	@DisplayName("동의 부족은 403 DIR-APP-009이고 버전 불일치는 409 DIR-APP-010이다")
	void exposesApprovedConsentErrorMappings() {
		assertThat(DirectionErrorCode.LOCATION_CONSENT_REQUIRED.httpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(DirectionErrorCode.LOCATION_CONSENT_REQUIRED.code()).isEqualTo("DIR-APP-009");
		assertThat(DirectionErrorCode.LOCATION_CONSENT_REQUIRED.category()).isEqualTo(ErrorCategory.APP);
		assertThat(DirectionErrorCode.LOCATION_CONSENT_VERSION_MISMATCH.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(DirectionErrorCode.LOCATION_CONSENT_VERSION_MISMATCH.code()).isEqualTo("DIR-APP-010");
		assertThat(DirectionErrorCode.LOCATION_CONSENT_VERSION_MISMATCH.category()).isEqualTo(ErrorCategory.APP);
	}

	@Test
	@DisplayName("동의 오류를 포함한 direction wire 오류 코드는 중복되지 않는다")
	void directionErrorCodesAreUnique() {
		assertThat(DirectionErrorCode.values()).extracting(DirectionErrorCode::code).doesNotHaveDuplicates();
	}
}
