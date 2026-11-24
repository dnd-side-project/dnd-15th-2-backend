/*
 * Created at: 2026-11-22T22:12:29+09:00
 * Source scenario: TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-014
 */
package com.dnd.qello.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressTest {

	@Test
	@DisplayName("연결 주소를 쓰고 X-Forwarded-For는 무시한다")
	void usesRemoteAddressAndIgnoresForwardedHeader() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("192.0.2.10");
		request.addHeader("X-Forwarded-For", "198.51.100.7");

		assertThat(ClientAddress.of(request)).isEqualTo("192.0.2.10");
	}

	@ParameterizedTest
	@DisplayName("IPv6는 /64로 묶지 않고 정규화한 전체 주소로 남긴다(D3)")
	@CsvSource({
			"2001:db8::1, 2001:db8:0:0:0:0:0:1",
			"[2001:db8::1], 2001:db8:0:0:0:0:0:1",
			"fe80::1%en0, fe80:0:0:0:0:0:0:1",
			"2001:DB8::ABCD, 2001:db8:0:0:0:0:0:abcd",
			"::ffff:192.0.2.10, 192.0.2.10"
	})
	void keepsFullNormalizedIpv6Address(String remoteAddress, String expected) {
		assertThat(ClientAddress.fromAddress(remoteAddress)).isEqualTo(expected);
	}

	@ParameterizedTest
	@DisplayName("연결 주소가 없으면 unknown이다")
	@NullAndEmptySource
	@ValueSource(strings = {"   "})
	void returnsUnknownWithoutRemoteAddress(String remoteAddress) {
		assertThat(ClientAddress.fromAddress(remoteAddress)).isEqualTo(ClientAddress.UNKNOWN);
	}
}
