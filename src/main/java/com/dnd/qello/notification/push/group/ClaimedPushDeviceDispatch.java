package com.dnd.qello.notification.push.group;

import java.time.Instant;
import java.util.Map;

/** worker가 provider 호출 전에 받은 device별 delivery generation fence다. */
public record ClaimedPushDeviceDispatch(
	long deviceId,
	Map<Long, Integer> deliveryGenerations,
	Instant leaseUntil
) {

	public ClaimedPushDeviceDispatch {
		if (deviceId <= 0 || leaseUntil == null) {
			throw new IllegalArgumentException("device claim 값이 올바르지 않습니다");
		}
		if (deliveryGenerations == null || deliveryGenerations.isEmpty()) {
			throw new IllegalArgumentException("device claim delivery는 비어 있을 수 없습니다");
		}
		for (Map.Entry<Long, Integer> entry : deliveryGenerations.entrySet()) {
			if (entry.getKey() == null || entry.getKey() <= 0
				|| entry.getValue() == null || entry.getValue() <= 0) {
				throw new IllegalArgumentException("device claim delivery generation이 올바르지 않습니다");
			}
		}
		deliveryGenerations = Map.copyOf(deliveryGenerations);
	}
}
