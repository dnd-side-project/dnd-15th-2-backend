package com.dnd.qello.account.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 앱 탈퇴 유예(#337). 요청 후 gracePeriod가 지나면 sweep이 계정을 DELETED로 바꾸고, 그 전까지는 같은 기기로 철회할 수 있다.
@ConfigurationProperties(prefix = "qello.account.withdrawal")
public record AccountWithdrawalProperties(Duration gracePeriod) {

	public AccountWithdrawalProperties {
		if (gracePeriod == null || gracePeriod.isZero() || gracePeriod.isNegative()) {
			throw new IllegalArgumentException("qello.account.withdrawal.grace-period는 양수여야 합니다");
		}
	}
}
