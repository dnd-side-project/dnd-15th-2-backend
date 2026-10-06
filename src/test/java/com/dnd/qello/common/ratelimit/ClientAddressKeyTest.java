/*
 * Created at: 2026-10-06T14:26:52+09:00
 * Source scenario: TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-007
 */
package com.dnd.qello.common.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressKeyTest {

	@Test
	@DisplayName("UNIT-007: IPv4 연결 주소는 그대로 키가 된다")
	void usesIpv4AddressAsIs() {
		assertThat(ClientAddressKey.of(requestFrom("192.0.2.10"))).isEqualTo("192.0.2.10");
	}

	@Test
	@DisplayName("UNIT-007: X-Forwarded-For와 X-Real-IP 헤더는 키에 영향을 주지 않는다")
	void ignoresForwardedHeaders() {
		MockHttpServletRequest request = requestFrom("192.0.2.10");
		request.addHeader("X-Forwarded-For", "198.51.100.7");
		request.addHeader("X-Real-IP", "198.51.100.8");

		assertThat(ClientAddressKey.of(request)).isEqualTo("192.0.2.10");
	}

	@Test
	@DisplayName("UNIT-007: 같은 /64에 속한 IPv6 주소는 같은 키로 센다")
	void groupsIpv6AddressesBySlash64() {
		String first = ClientAddressKey.of(requestFrom("2001:db8:0:1::1"));
		String second = ClientAddressKey.of(requestFrom("2001:db8:0:1:ffff:ffff:ffff:ffff"));
		String bracketedWithZone = ClientAddressKey.of(requestFrom("[2001:db8:0:1::2%3]"));

		assertThat(first).isEqualTo(second).isEqualTo(bracketedWithZone).endsWith("/64");
	}

	@Test
	@DisplayName("UNIT-007: 다른 /64의 IPv6 주소는 다른 키다")
	void separatesDifferentIpv6Prefixes() {
		String first = ClientAddressKey.of(requestFrom("2001:db8:0:1::1"));
		String other = ClientAddressKey.of(requestFrom("2001:db8:0:2::1"));

		assertThat(first).isNotEqualTo(other);
	}

	@Test
	@DisplayName("UNIT-007: IPv4-mapped IPv6 주소는 IPv4 주소와 같은 키로 센다")
	void treatsIpv4MappedAddressAsIpv4() {
		assertThat(ClientAddressKey.of(requestFrom("::ffff:192.0.2.10"))).isEqualTo("192.0.2.10");
	}

	@Test
	@DisplayName("UNIT-007: 연결 주소가 비어 있으면 하나의 unknown 키로 센다")
	void fallsBackToSingleKeyWhenAddressMissing() {
		assertThat(ClientAddressKey.of(requestFrom(""))).isEqualTo("unknown");
	}

	private static MockHttpServletRequest requestFrom(String remoteAddress) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(remoteAddress);
		return request;
	}
}
