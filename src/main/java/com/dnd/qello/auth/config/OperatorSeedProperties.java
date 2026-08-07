package com.dnd.qello.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 초기 운영자 계정 시드 설정.
//
// 값은 환경변수로만 주입한다. 저장소의 properties 파일이나 migration에 비밀번호를
// 적지 않는다. 운영자 자체 가입 API를 두지 않기로 했으므로(ADR-0006) 첫 계정은
// 이 경로로만 만들어진다.
@ConfigurationProperties(prefix = "qello.auth.operator-seed")
public record OperatorSeedProperties(
	boolean enabled,
	String loginId,
	String password,
	String nickname,
	String coarseRegionCode,
	String locale,
	String timezone
) {

	@Override
	public String toString() {
		// 설정 진단 로그나 actuator에 password가 실리지 않게 한다.
		return "OperatorSeedProperties[enabled=%s, loginId=%s, password=REDACTED]"
			.formatted(enabled, loginId);
	}
}
