package com.dnd.qello.common.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

// 접근 기록에 남길 클라이언트 주소(#375).
//
// ClientAddressKey와 같은 원천(연결 주소 remoteAddr)만 쓰고 X-Forwarded-For 같은 헤더는 읽지 않는다.
// 앞단 프록시가 없는 지금은 클라이언트가 그 헤더를 마음대로 넣을 수 있다. 다만 ClientAddressKey와 달리
// IPv6를 /64로 묶지 않는다. 그쪽은 요청 한도의 키라 회선 단위로 묶어야 하고, 이쪽은 접속지 기록이라
// 주소 그대로 남겨야 한다.
public final class ClientAddress {

	public static final String UNKNOWN = "unknown";

	private ClientAddress() {
	}

	public static String of(HttpServletRequest request) {
		return fromAddress(request.getRemoteAddr());
	}

	static String fromAddress(String remoteAddress) {
		if (remoteAddress == null || remoteAddress.isBlank()) {
			return UNKNOWN;
		}
		String address = remoteAddress.strip();
		if (address.indexOf(':') < 0) {
			return address;
		}
		return normalizeIpv6(address);
	}

	private static String normalizeIpv6(String address) {
		String literal = withoutZone(address.startsWith("[") && address.endsWith("]")
				? address.substring(1, address.length() - 1)
				: address);
		try {
			// ':'가 들어간 문자열은 IPv6 리터럴로만 해석되고 DNS를 조회하지 않는다.
			// IPv4-mapped 주소(::ffff:a.b.c.d)는 Inet4Address로 해석돼 IPv4 표기가 된다.
			return InetAddress.getByName(literal).getHostAddress();
		} catch (UnknownHostException exception) {
			return literal.toLowerCase(Locale.ROOT);
		}
	}

	private static String withoutZone(String literal) {
		int zoneStart = literal.indexOf('%');
		return zoneStart < 0 ? literal : literal.substring(0, zoneStart);
	}
}
