/**
 * Created at: 2026-08-14T00:51:11+09:00
 * Source scenario: TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-UNIT-004, UNIT-007, UNIT-010
 */
package com.dnd.qello.direction.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.direction.web.request.UpdateActiveUserPresenceRequest;
import com.dnd.qello.direction.web.response.UpdateActiveUserPresenceResponse;

class ActiveUserPresenceWebContractTest {

	@Test
	@DisplayName("요청 문자열과 성공 응답은 정확 위치와 사용자·지역 필드를 노출하지 않는다")
	void redactsRequestAndLimitsResponseToApplied() {
		UpdateActiveUserPresenceRequest request = new UpdateActiveUserPresenceRequest(
			new BigDecimal("37.512345"), new BigDecimal("127.098765"), new BigDecimal("12.34"), true,
			Instant.parse("2026-08-13T15:00:00Z"));

		assertThat(request.toString()).doesNotContain("37.512345", "127.098765", "12.34");
		assertThat(UpdateActiveUserPresenceResponse.class.getRecordComponents())
			.extracting(java.lang.reflect.RecordComponent::getName)
			.containsExactly("applied");
	}

	@Test
	@DisplayName("ApiSpec과 Controller가 분리되고 request와 response는 하위 패키지에 있다")
	void keepsApiBoundaryTypesSeparated() {
		assertThat(ActiveUserPresenceApiSpec.class.isAssignableFrom(ActiveUserPresenceController.class)).isTrue();
		assertThat(UpdateActiveUserPresenceRequest.class.getPackageName())
			.isEqualTo("com.dnd.qello.direction.web.request");
		assertThat(UpdateActiveUserPresenceResponse.class.getPackageName())
			.isEqualTo("com.dnd.qello.direction.web.response");
	}
}
