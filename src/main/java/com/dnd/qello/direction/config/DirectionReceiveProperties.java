package com.dnd.qello.direction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.dnd.qello.direction.domain.RecipientReceiveState;

/**
 * 활성 미처리 수신 상한. 고정 상수가 아니라 운영 설정값이므로 DB CHECK가 아니라
 * 여기서 읽는다. DB는 어떤 설정에서도 넘을 수 없는 안전 상한 50만 강제한다.
 */
@ConfigurationProperties(prefix = "qello.direction")
public record DirectionReceiveProperties(int receiveCapacity) {

	public DirectionReceiveProperties {
		if (receiveCapacity < 1 || receiveCapacity > RecipientReceiveState.SAFETY_CEILING) {
			throw new IllegalArgumentException(
				"qello.direction.receive-capacity는 1과 " + RecipientReceiveState.SAFETY_CEILING + " 사이여야 합니다: " + receiveCapacity);
		}
	}
}
