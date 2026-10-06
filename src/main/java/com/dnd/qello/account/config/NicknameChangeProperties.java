package com.dnd.qello.account.config;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.dnd.qello.common.ratelimit.RateLimitPolicy;

// 닉네임 변경 제한(F01, #315).
//
// cooldown은 마지막 성공 변경부터 다음 변경까지의 최소 간격이다. attemptRateLimit은 성공 여부와
// 무관하게 사용자 단위로 시도를 센다. 변경마다 외부 moderation을 호출하므로 실패한 시도도 비용이다.
@ConfigurationProperties(prefix = "qello.account.nickname-change")
public record NicknameChangeProperties(Duration cooldown, RateLimitPolicy attemptRateLimit) {

	public NicknameChangeProperties {
		if (cooldown == null || cooldown.isZero() || cooldown.isNegative()) {
			throw new IllegalArgumentException("qello.account.nickname-change.cooldown은 양수여야 합니다");
		}
		Objects.requireNonNull(attemptRateLimit, "qello.account.nickname-change.attempt-rate-limit은 필수입니다");
	}
}
