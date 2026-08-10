package com.dnd.qello.direction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 넘김(SKIP_PENDING) 되돌리기 유예 시간. 이 시간이 지나야 SKIPPED로 확정되고
 * 슬롯이 해제된다. 고정 상수가 아니라 운영 설정값이므로 DirectionReceiveProperties가
 * 수신 상한을 다루는 방식과 같게 코드에 박지 않는다.
 */
@ConfigurationProperties(prefix = "qello.direction")
public record SkipConfirmationProperties(int skipConfirmationGraceSeconds) {

	public SkipConfirmationProperties {
		if (skipConfirmationGraceSeconds < 0) {
			throw new IllegalArgumentException(
				"qello.direction.skip-confirmation-grace-seconds는 음수일 수 없습니다: " + skipConfirmationGraceSeconds);
		}
	}
}
