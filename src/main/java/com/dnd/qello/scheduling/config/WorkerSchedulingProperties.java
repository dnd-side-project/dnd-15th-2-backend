package com.dnd.qello.scheduling.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.dnd.qello.notification.domain.ExponentialJitterBackoffStrategy;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.push.PushDeliveryRetryPolicy;

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
			throw invalid("poolSize", "양수여야 합니다");
		}
		validateOutbox(directionMatching, "directionMatching");
		validateOutbox(recipientNotificationFanOut, "recipientNotificationFanOut");
		validateOutbox(notificationFanOut, "notificationFanOut");
		validateOutbox(reportResolutionFanOut, "reportResolutionFanOut");
		validateSweep(recipientExpirationSweep, "recipientExpirationSweep");
		validateSweep(skipConfirmationSweep, "skipConfirmationSweep");
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

		// 네 Outbox adapter가 같은 설정에서 같은 정책을 만들도록 생성 지점을 한 곳에 둔다.
		public OutboxRetryPolicy toPolicy() {
			return new OutboxRetryPolicy(maxAttempts,
				ExponentialJitterBackoffStrategy.withRandomJitter(baseDelay, maxDelay));
		}
	}

	public record PushRetrySettings(int maxAttempts, Duration baseBackoff, Duration backoffCap) {

		public PushDeliveryRetryPolicy toPolicy() {
			return new PushDeliveryRetryPolicy(maxAttempts, baseBackoff, backoffCap);
		}
	}

	private static void validateOutbox(OutboxSettings settings, String block) {
		if (settings == null || !settings.enabled()) {
			return;
		}
		validateDuration(settings.fixedDelay(), block + ".fixedDelay");
		validatePositive(settings.batchSize(), block + ".batchSize");
		validateDuration(settings.leaseDuration(), block + ".leaseDuration");
		OutboxRetrySettings retry = settings.retry();
		if (retry == null) {
			throw invalid(block + ".retry", "필수입니다");
		}
		validatePositive(retry.maxAttempts(), block + ".retry.maxAttempts");
		validateDuration(retry.baseDelay(), block + ".retry.baseDelay");
		validateDuration(retry.maxDelay(), block + ".retry.maxDelay");
		if (retry.maxDelay().compareTo(retry.baseDelay()) < 0) {
			throw invalid(block + ".retry.maxDelay", "baseDelay 이상이어야 합니다");
		}
	}

	private static void validateSweep(SweepSettings settings, String block) {
		if (settings == null || !settings.enabled()) {
			return;
		}
		validateDuration(settings.fixedDelay(), block + ".fixedDelay");
		validatePositive(settings.batchSize(), block + ".batchSize");
	}

	private static void validatePush(PushSettings settings, String block) {
		if (settings == null || !settings.enabled()) {
			return;
		}
		validateDuration(settings.fixedDelay(), block + ".fixedDelay");
		validatePositive(settings.batchSize(), block + ".batchSize");
		validateDuration(settings.leaseDuration(), block + ".leaseDuration");
		PushRetrySettings retry = settings.retry();
		if (retry == null) {
			throw invalid(block + ".retry", "필수입니다");
		}
		validatePositive(retry.maxAttempts(), block + ".retry.maxAttempts");
		validateDuration(retry.baseBackoff(), block + ".retry.baseBackoff");
		validateDuration(retry.backoffCap(), block + ".retry.backoffCap");
		if (retry.backoffCap().compareTo(retry.baseBackoff()) < 0) {
			throw invalid(block + ".retry.backoffCap", "baseBackoff 이상이어야 합니다");
		}
	}

	private static void validatePositive(int value, String field) {
		if (value <= 0) {
			throw invalid(field, "양수여야 합니다");
		}
	}

	private static void validateDuration(Duration value, String field) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw invalid(field, "양수인 기간이어야 합니다");
		}
	}

	// 어느 worker 블록의 어느 값이 왜 거절됐는지 설정 파일만 보고 알 수 있어야 한다.
	private static IllegalArgumentException invalid(String field, String reason) {
		return new IllegalArgumentException("qello.worker.scheduling." + field + ": " + reason);
	}
}
