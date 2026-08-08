package com.dnd.qello.direction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 방향 구간 파생에 쓸 ACTIVE direction_scheme의 code.
 * uq_direction_scheme_active가 (code) WHERE status = 'ACTIVE'라 code별로만
 * 유일하다 — code를 고정하지 않으면 다른 code의 스킴이 동시에 ACTIVE일 때
 * 한 수신 항목이 두 스킴의 구간에 각각 잡혀 칩 집계가 중복된다.
 * 8방향에서 16방향으로 전환할 때는 새 code의 스킴을 ACTIVE로 만들고 이 값만
 * 바꾸면 된다 — 고정 상수가 아니라 운영 설정값이므로 코드에 박지 않는다.
 */
@ConfigurationProperties(prefix = "qello.direction")
public record DirectionSchemeProperties(String schemeCode) {

	public DirectionSchemeProperties {
		if (schemeCode == null || schemeCode.isBlank()) {
			throw new IllegalArgumentException("qello.direction.scheme-code는 필수입니다");
		}
	}
}
