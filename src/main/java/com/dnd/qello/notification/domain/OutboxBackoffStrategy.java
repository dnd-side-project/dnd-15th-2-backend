package com.dnd.qello.notification.domain;

import java.time.Duration;

@FunctionalInterface
public interface OutboxBackoffStrategy {

	Duration delayForAttempt(int attemptCount);
}
