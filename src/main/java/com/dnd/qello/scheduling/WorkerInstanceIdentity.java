package com.dnd.qello.scheduling;

import java.util.UUID;

public record WorkerInstanceIdentity(String owner) {
	private static final String PREFIX = "worker-";
	private static final int MAX_OWNER_LENGTH = 100;

	public WorkerInstanceIdentity {
		if (owner == null || owner.isBlank() || owner.length() > MAX_OWNER_LENGTH) {
			throw new IllegalArgumentException("worker owner는 1~100자여야 합니다");
		}
	}

	public static WorkerInstanceIdentity random() {
		return new WorkerInstanceIdentity(PREFIX + UUID.randomUUID());
	}
}
