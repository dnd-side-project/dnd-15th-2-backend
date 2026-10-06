package com.dnd.qello.common.ratelimit;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

// 요청 한도의 키로 쓸 클라이언트 주소.
//
// 연결 주소(remoteAddr)만 쓰고 X-Forwarded-For 같은 헤더는 읽지 않는다. 앞단 프록시가 없는 지금은
// 클라이언트가 그 헤더를 마음대로 넣을 수 있어 한도를 우회하는 수단이 된다(#315). 프록시나 LB를 두면
// 모든 요청이 프록시 주소 하나로 보이므로 server.forward-headers-strategy와 신뢰할 프록시 범위를
// 함께 설정해야 한다.
//
// IPv6는 /64 접두사로 묶는다. 가입자 회선 하나가 보통 /64 이상을 받아 주소를 얼마든지 바꿀 수 있다.
public final class ClientAddressKey {

	private static final int IPV6_PREFIX_BYTES = 8;
	private static final String UNKNOWN = "unknown";

	private ClientAddressKey() {
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
		return ipv6Key(address);
	}

	private static String ipv6Key(String address) {
		String literal = withoutZone(address.startsWith("[") && address.endsWith("]")
				? address.substring(1, address.length() - 1)
				: address);
		try {
			// ':'가 들어간 문자열은 IPv6 리터럴로만 해석되고 DNS를 조회하지 않는다.
			InetAddress parsed = InetAddress.getByName(literal);
			if (!(parsed instanceof Inet6Address)) {
				// IPv4-mapped 주소(::ffff:a.b.c.d)는 IPv4 주소와 같은 키로 센다.
				return parsed.getHostAddress();
			}
			byte[] bytes = parsed.getAddress();
			return HexFormat.of().formatHex(bytes, 0, IPV6_PREFIX_BYTES) + "/64";
		} catch (UnknownHostException exception) {
			return literal.toLowerCase(Locale.ROOT);
		}
	}

	private static String withoutZone(String literal) {
		int zoneStart = literal.indexOf('%');
		return zoneStart < 0 ? literal : literal.substring(0, zoneStart);
	}
}
