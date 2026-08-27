package com.dnd.qello.scheduling.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qello.worker.scheduling")
public record WorkerSchedulingProperties(
	boolean enabled,
	int poolSize,
	OutboxSettings directionMatching,
	OutboxSettings recipientNotificationFanOut,
	OutboxSettings notificationFanOut,
	OutboxSettings reportResolutionFanOut,
	SweepSettings recipientExpirationSweep,
	SweepSettings skipConfirmationSweep,
	PushSettings pushDeliveryDispatch
) {
	public WorkerSchedulingProperties {
		if (enabled && poolSize <= 0) {
			throw invalid("poolSize");
		}
		validateOutbox(directionMatching, "directionMatching");
		validateOutbox(recipientNotificationFanOut, "recipientNotificationFanOut");
		validateOutbox(notificationFanOut, "notificationFanOut");
		validateOutbox(reportResolutionFanOut, "reportResolutionFanOut");
		validateSweep(recipientExpirationSweep);
		validateSweep(skipConfirmationSweep);
		validatePush(pushDeliveryDispatch, "pushDeliveryDispatch");
	}

	public record OutboxSettings(boolean enabled, Duration fixedDelay, int batchSize, Duration leaseDuration,
		OutboxRetrySettings retry) {
	}

	public record SweepSettings(boolean enabled, Duration fixedDelay, int batchSize) {
	}

	public record PushSettings(boolean enabled, Duration fixedDelay, int batchSize, Duration leaseDuration,
		PushRetrySettings retry) {
	}

	public record OutboxRetrySettings(int maxAttempts, Duration baseDelay, Duration maxDelay) {
	}

	public record PushRetrySettings(int maxAttempts, Duration baseBackoff, Duration backoffCap) {
	}

	private static void validateOutbox(OutboxSettings settings, String field) {
		if (settings == null || !settings.enabled()) {
			return;
		}
		validateDuration(settings.fixedDelay(), "fixedDelay");
		validatePositive(settings.batchSize(), "batchSize");
		validateDuration(settings.leaseDuration(), "leaseDuration");
		if (settings.retry() == null) {
			throw invalid(field);
		}
		validatePositive(settings.retry().maxAttempts(), "maxAttempts");
		validateDuration(settings.retry().baseDelay(), "baseDelay");
		validateDuration(settings.retry().maxDelay(), "maxDelay");
		if (settings.retry().maxDelay().compareTo(settings.retry().baseDelay()) < 0) {
			throw invalid("maxDelay");
		}
	}

	private static void validateSweep(SweepSettings settings) {
		if (settings == null || !settings.enabled()) {
			return;
		}
		validateDuration(settings.fixedDelay(), "fixedDelay");
		validatePositive(settings.batchSize(), "batchSize");
	}

	private static void validatePush(PushSettings settings, String field) {
		if (settings == null || !settings.enabled()) {
			return;
		}
		validateDuration(settings.fixedDelay(), "fixedDelay");
		validatePositive(settings.batchSize(), "batchSize");
		validateDuration(settings.leaseDuration(), "leaseDuration");
		if (settings.retry() == null) {
			throw invalid(field);
		}
		validatePositive(settings.retry().maxAttempts(), "maxAttempts");
		validateDuration(settings.retry().baseBackoff(), "baseBackoff");
		validateDuration(settings.retry().backoffCap(), "backoffCap");
		if (settings.retry().backoffCap().compareTo(settings.retry().baseBackoff()) < 0) {
			throw invalid("backoffCap");
		}
	}

	private static void validatePositive(int value, String field) {
		if (value <= 0) {
			throw invalid(field);
		}
	}

	private static void validateDuration(Duration value, String field) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw invalid(field);
		}
	}

	private static IllegalArgumentException invalid(String field) {
		return new IllegalArgumentException(field);
	}
}
