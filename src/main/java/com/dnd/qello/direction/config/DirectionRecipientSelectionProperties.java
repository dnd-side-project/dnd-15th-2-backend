package com.dnd.qello.direction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 발송 한 건에서 확정할 수 있는 최대 수신자 수.
 * 사용자별 미처리 수신 슬롯 상한인 {@link DirectionReceiveProperties}와는 별도 정책이다.
 */
@ConfigurationProperties(prefix = "qello.direction")
public record DirectionRecipientSelectionProperties(int maxRecipientsPerPost) {

	public DirectionRecipientSelectionProperties {
		if (maxRecipientsPerPost < 1) {
			throw new IllegalArgumentException(
				"qello.direction.max-recipients-per-post는 1 이상이어야 합니다: " + maxRecipientsPerPost);
		}
	}
}
