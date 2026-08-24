package com.dnd.qello.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.push.ClaimedPushDelivery;
import com.dnd.qello.notification.push.PushDeliveryRetryPolicy;
import com.dnd.qello.notification.push.PushDeliveryTerminalResult;
import com.dnd.qello.notification.push.PushDispatchContext;
import com.dnd.qello.notification.push.PushDispatchDecision;
import com.dnd.qello.notification.push.PushDispatchEligibility;
import com.dnd.qello.notification.push.PushPayload;
import com.dnd.qello.notification.push.PushPayloadFactory;
import com.dnd.qello.notification.push.PushProvider;
import com.dnd.qello.notification.push.PushProviderResult;
import com.dnd.qello.notification.push.PushSendCommand;
import com.dnd.qello.notification.push.security.PushToken;
import com.dnd.qello.notification.push.security.PushTokenProtectionException;
import com.dnd.qello.notification.push.security.PushTokenProtector;

/**
 * 명시적으로 호출된 due batch를 건별 격리해 처리한다.
 *
 * <p>claim/context/terminal은 {@link PushDeliveryClaimService}의 짧은 transaction이고,
 * 복호화와 provider 호출은 그 transaction 경계 사이에서 수행한다. scheduler/poller 배선은
 * #182 범위이므로 이 클래스는 Spring bean으로 자동 활성화하지 않는다.</p>
 */
public final class PushDeliveryDispatchWorker {

	private static final Logger log = LoggerFactory.getLogger(PushDeliveryDispatchWorker.class);

	private final PushDeliveryClaimService claimService;
	private final PushDispatchEligibility eligibility;
	private final PushPayloadFactory payloadFactory;
	private final PushDeliveryRetryPolicy retryPolicy;
	private final PushTokenProtector tokenProtector;
	private final PushProvider provider;
	private final Clock clock;

	public PushDeliveryDispatchWorker(
		PushDeliveryClaimService claimService,
		PushDispatchEligibility eligibility,
		PushPayloadFactory payloadFactory,
		PushDeliveryRetryPolicy retryPolicy,
		PushTokenProtector tokenProtector,
		PushProvider provider
	) {
		this(claimService, eligibility, payloadFactory, retryPolicy, tokenProtector, provider, Clock.systemUTC());
	}

	public PushDeliveryDispatchWorker(
		PushDeliveryClaimService claimService,
		PushDispatchEligibility eligibility,
		PushPayloadFactory payloadFactory,
		PushDeliveryRetryPolicy retryPolicy,
		PushTokenProtector tokenProtector,
		PushProvider provider,
		Clock clock
	) {
		this.claimService = require(claimService, "claimService");
		this.eligibility = require(eligibility, "eligibility");
		this.payloadFactory = require(payloadFactory, "payloadFactory");
		this.retryPolicy = require(retryPolicy, "retryPolicy");
		this.tokenProtector = require(tokenProtector, "tokenProtector");
		this.provider = require(provider, "provider");
		this.clock = require(clock, "clock");
	}

	public BatchResult dispatchBatch(BatchCommand command) {
		require(command, "command");
		List<ClaimedPushDelivery> claimed = claimService.claimDueDeliveries(
			command.batchSize(), command.at(), command.leaseUntil());
		List<DeliveryOutcome> outcomes = claimed.stream()
			.map(this::dispatchOne)
			.toList();
		return new BatchResult(claimed.size(), outcomes);
	}

	private DeliveryOutcome dispatchOne(ClaimedPushDelivery claim) {
		try {
			Instant contextAt = clock.instant();
			return claimService.findPushDispatchContext(claim.deliveryId(), claim.generation(), contextAt)
				.map(context -> dispatchCurrentClaim(claim, context, contextAt))
				.orElseGet(() -> stale(claim, "STALE_CLAIM"));
		} catch (PushTokenProtectionException failure) {
			return completeAfterTokenFailure(claim, clock.instant());
		} catch (RuntimeException failure) {
			return recordUnexpectedFailure(claim, clock.instant());
		}
	}

	private DeliveryOutcome dispatchCurrentClaim(
		ClaimedPushDelivery claim, PushDispatchContext context, Instant at) {
		PushDispatchEligibility.Evaluation evaluation = eligibility.evaluate(context);
		if (evaluation.decision() == PushDispatchDecision.CANCELLED) {
			return complete(claim, PushDeliveryTerminalResult.CANCELLED, at, at, null,
				Outcome.CANCELLED, evaluation.reasonCode().name());
		}
		if (evaluation.decision() == PushDispatchDecision.DEAD) {
			return complete(claim, PushDeliveryTerminalResult.DEAD, at, at, null,
				Outcome.DEAD, evaluation.reasonCode().name());
		}

		PushToken token = tokenProtector.decrypt(context.device().tokenCiphertext());
		PushPayload payload = payloadFactory.create(context);
		PushProviderResult providerResult = provider.send(new PushSendCommand(token, payload));
		Instant terminalAt = clock.instant();
		if (providerResult instanceof PushProviderResult.InvalidToken) {
			return invalidateDevice(claim, context, terminalAt);
		}
		return completeProviderResult(claim, providerResult, terminalAt);
	}

	private DeliveryOutcome completeProviderResult(
		ClaimedPushDelivery claim, PushProviderResult providerResult, Instant at) {
		PushDeliveryRetryPolicy.Decision decision = retryPolicy.decide(claim.generation(), providerResult, at);
		if (decision.result() == PushDeliveryTerminalResult.SENT) {
			String providerMessageId = ((PushProviderResult.Accepted)providerResult).providerMessageId();
			return complete(claim, decision.result(), at, decision.nextAttemptAt(), providerMessageId,
				Outcome.SENT, "ACCEPTED");
		}
		if (decision.result() == PushDeliveryTerminalResult.FAILED) {
			return complete(claim, decision.result(), at, decision.nextAttemptAt(), null,
				Outcome.RETRY_SCHEDULED, "RETRYABLE_FAILURE");
		}
		String reasonCode = providerResult instanceof PushProviderResult.PermanentFailure
			? "PERMANENT_FAILURE"
			: "MAX_ATTEMPTS";
		return complete(claim, decision.result(), at, decision.nextAttemptAt(), null, Outcome.DEAD, reasonCode);
	}

	private DeliveryOutcome invalidateDevice(
		ClaimedPushDelivery claim, PushDispatchContext context, Instant at) {
		boolean completed = claimService.invalidatePushDeviceAndCancelUndelivered(
			claim.deliveryId(), context.device().id(), claim.generation(), at);
		return completed
			? outcome(claim, Outcome.DEAD, "INVALID_TOKEN")
			: stale(claim, "TERMINAL_FENCE_REJECTED");
	}

	private DeliveryOutcome completeAfterTokenFailure(ClaimedPushDelivery claim, Instant at) {
		try {
			return complete(claim, PushDeliveryTerminalResult.DEAD, at, at, null,
				Outcome.DEAD, "TOKEN_DECRYPTION_FAILED");
		} catch (RuntimeException terminalFailure) {
			return recordUnexpectedFailure(claim, clock.instant());
		}
	}

	private DeliveryOutcome recordUnexpectedFailure(ClaimedPushDelivery claim, Instant at) {
		log.warn("Push delivery failure isolated deliveryId={} generation={} reasonCode={}",
			claim.deliveryId(), claim.generation(), "UNEXPECTED_FAILURE");
		try {
			PushDeliveryRetryPolicy.Decision decision = retryPolicy.decide(
				claim.generation(), new PushProviderResult.RetryableFailure(null), at);
			Outcome outcome = decision.result() == PushDeliveryTerminalResult.FAILED
				? Outcome.RETRY_SCHEDULED
				: Outcome.DEAD;
			return complete(claim, decision.result(), at, decision.nextAttemptAt(), null,
				outcome, "UNEXPECTED_FAILURE");
		} catch (RuntimeException failureRecordingFailure) {
			log.warn("Push delivery failure recording isolated deliveryId={} generation={} reasonCode={}",
				claim.deliveryId(), claim.generation(), "FAILURE_RECORDING_FAILED");
			return outcome(claim, Outcome.FAILURE_RECORDING_FAILED, "FAILURE_RECORDING_FAILED");
		}
	}

	private DeliveryOutcome complete(
		ClaimedPushDelivery claim,
		PushDeliveryTerminalResult result,
		Instant at,
		Instant nextAttemptAt,
		String providerMessageId,
		Outcome completedOutcome,
		String safeReasonCode
	) {
		boolean completed = claimService.completeClaim(
			claim.deliveryId(), claim.generation(), result, at, nextAttemptAt, providerMessageId);
		return completed
			? outcome(claim, completedOutcome, safeReasonCode)
			: stale(claim, "TERMINAL_FENCE_REJECTED");
	}

	private static DeliveryOutcome stale(ClaimedPushDelivery claim, String safeReasonCode) {
		return outcome(claim, Outcome.STALE_CLAIM, safeReasonCode);
	}

	private static DeliveryOutcome outcome(
		ClaimedPushDelivery claim, Outcome outcome, String safeReasonCode) {
		return new DeliveryOutcome(claim.deliveryId(), claim.generation(), outcome, safeReasonCode);
	}

	private static <T> T require(T value, String field) {
		if (value == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, field,
				field + "은 필수입니다");
		}
		return value;
	}

	public enum Outcome {
		SENT,
		RETRY_SCHEDULED,
		DEAD,
		CANCELLED,
		STALE_CLAIM,
		FAILURE_RECORDING_FAILED
	}

	public record DeliveryOutcome(
		long deliveryId,
		int generation,
		Outcome outcome,
		String safeReasonCode
	) {
		public DeliveryOutcome {
			if (deliveryId <= 0 || generation <= 0 || outcome == null
				|| safeReasonCode == null || safeReasonCode.isBlank()) {
				throw new IllegalArgumentException("delivery outcome 값이 올바르지 않습니다");
			}
		}
	}

	public record BatchResult(int claimed, List<DeliveryOutcome> outcomes) {
		public BatchResult {
			if (claimed < 0 || outcomes == null || claimed != outcomes.size()) {
				throw new IllegalArgumentException("dispatch batch 결과가 올바르지 않습니다");
			}
			outcomes = List.copyOf(outcomes);
		}
	}

	public record BatchCommand(int batchSize, Instant at, Instant leaseUntil) {
		public BatchCommand {
			if (batchSize <= 0) {
				throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "batchSize",
					"batchSize는 양수여야 합니다");
			}
			if (at == null || leaseUntil == null || !leaseUntil.isAfter(at)) {
				throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "leaseUntil",
					"현재 시각 이후의 leaseUntil이 필요합니다");
			}
		}
	}
}
