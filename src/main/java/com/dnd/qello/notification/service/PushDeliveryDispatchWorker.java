package com.dnd.qello.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.notification.config.PushPolicyProperties;
import com.dnd.qello.notification.domain.DeliveryStatus;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
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
import com.dnd.qello.notification.push.group.ClaimedPushDeviceDispatch;
import com.dnd.qello.notification.push.group.ClaimedPushDispatchGroup;
import com.dnd.qello.notification.push.group.PushBudgetReservation;
import com.dnd.qello.notification.push.group.PushDispatchGroupContext;
import com.dnd.qello.notification.push.group.PushDispatchMemberContext;
import com.dnd.qello.notification.push.policy.PushBudgetPolicy;
import com.dnd.qello.notification.push.policy.PushSuppressionPolicy;
import com.dnd.qello.notification.push.security.PushToken;
import com.dnd.qello.notification.push.security.PushTokenProtectionException;
import com.dnd.qello.notification.push.security.PushTokenProtector;

/**
 * 명시적으로 호출된 due batch를 group 단위로 격리해 처리한다.
 *
 * <p>편입/claim/context/device terminal은 짧은 transaction이고, 복호화와 provider 호출은
 * 그 transaction 경계 사이에서 수행한다. scheduler/poller 배선은 #182 범위이므로 이 클래스는
 * Spring bean으로 자동 활성화하지 않는다.</p>
 */
public final class PushDeliveryDispatchWorker {

	private static final Logger log = LoggerFactory.getLogger(PushDeliveryDispatchWorker.class);

	private final PushDispatchGroupPlanner groupPlanner;
	private final PushDispatchGroupClaimService groupClaims;
	private final TransactionTemplate transactions;
	private final PushDispatchEligibility eligibility;
	private final PushSuppressionPolicy suppressionPolicy;
	private final PushBudgetPolicy budgetPolicy;
	private final PushPolicyProperties policyProperties;
	private final PushPayloadFactory payloadFactory;
	private final PushDeliveryRetryPolicy retryPolicy;
	private final PushTokenProtector tokenProtector;
	private final PushProvider provider;
	private final Clock clock;

	public PushDeliveryDispatchWorker(
		PushDispatchGroupPlanner groupPlanner,
		PushDispatchGroupClaimService groupClaims,
		TransactionTemplate transactions,
		PushDispatchEligibility eligibility,
		PushSuppressionPolicy suppressionPolicy,
		PushBudgetPolicy budgetPolicy,
		PushPolicyProperties policyProperties,
		PushPayloadFactory payloadFactory,
		PushDeliveryRetryPolicy retryPolicy,
		PushTokenProtector tokenProtector,
		PushProvider provider,
		Clock clock
	) {
		this.groupPlanner = require(groupPlanner, "groupPlanner");
		this.groupClaims = require(groupClaims, "groupClaims");
		this.transactions = require(transactions, "transactions");
		this.eligibility = require(eligibility, "eligibility");
		this.suppressionPolicy = require(suppressionPolicy, "suppressionPolicy");
		this.budgetPolicy = require(budgetPolicy, "budgetPolicy");
		this.policyProperties = require(policyProperties, "policyProperties");
		this.payloadFactory = require(payloadFactory, "payloadFactory");
		this.retryPolicy = require(retryPolicy, "retryPolicy");
		this.tokenProtector = require(tokenProtector, "tokenProtector");
		this.provider = require(provider, "provider");
		this.clock = require(clock, "clock");
	}

	public BatchResult dispatchBatch(BatchCommand command) {
		require(command, "command");
		transactions.executeWithoutResult(status ->
			groupPlanner.collectUngrouped(command.batchSize(), command.at()));
		List<ClaimedPushDispatchGroup> claims = groupClaims.claimDueGroups(
			command.batchSize(), command.at(), command.leaseUntil());
		return new BatchResult(claims.size(), claims.stream().map(this::dispatchGroup).toList());
	}

	private DeliveryOutcome dispatchGroup(ClaimedPushDispatchGroup claim) {
		try {
			return dispatchCurrentGroup(claim);
		} catch (RuntimeException failure) {
			log.warn("Push group failure isolated groupId={} generation={} reasonCode={}",
				claim.groupId(), claim.generation(), "UNEXPECTED_FAILURE");
			return outcome(claim.groupId(), claim.generation(), Outcome.FAILURE_RECORDING_FAILED,
				"UNEXPECTED_FAILURE");
		}
	}

	private DeliveryOutcome dispatchCurrentGroup(ClaimedPushDispatchGroup claim) {
		Instant contextAt = clock.instant();
		return groupClaims.loadContext(claim.groupId(), claim.generation(), contextAt)
			.map(context -> dispatchLoadedGroup(claim, context))
			.orElseGet(() -> outcome(claim.groupId(), claim.generation(), Outcome.STALE_CLAIM, "STALE_CLAIM"));
	}

	private DeliveryOutcome dispatchLoadedGroup(
		ClaimedPushDispatchGroup claim, PushDispatchGroupContext context) {
		long representativeId = representativeDeliveryId(context, List.of());
		List<Long> ineligibleIds = new ArrayList<>();
		List<String> ineligibleReasons = new ArrayList<>();
		Map<Long, Set<Long>> eligibleByDevice = new LinkedHashMap<>();
		for (PushDispatchMemberContext member : context.members()) {
			DeliveryStatus status = member.dispatchContext().delivery().status();
			if (status == DeliveryStatus.SENT || status == DeliveryStatus.DEAD
				|| status == DeliveryStatus.CANCELLED) {
				continue;
			}
			PushDispatchEligibility.Evaluation evaluation =
				eligibility.evaluate(evaluable(member.dispatchContext()));
			if (evaluation.decision() != PushDispatchDecision.SEND) {
				ineligibleIds.add(member.deliveryId());
				ineligibleReasons.add(evaluation.reasonCode().name());
				continue;
			}
			eligibleByDevice
				.computeIfAbsent(member.dispatchContext().device().id(), key -> new LinkedHashSet<>())
				.add(member.deliveryId());
		}

		Instant at = clock.instant();
		if (!ineligibleIds.isEmpty()
			&& !groupClaims.cancelMemberDeliveries(claim.groupId(), claim.generation(), ineligibleIds, at)) {
			return outcome(representativeId, claim.generation(), Outcome.STALE_CLAIM, "STALE_CLAIM");
		}
		if (eligibleByDevice.isEmpty()) {
			groupClaims.cancelGroup(claim.groupId(), claim.generation(), clock.instant());
			String reasonCode = ineligibleReasons.isEmpty() ? "NO_ELIGIBLE_MEMBERS" : ineligibleReasons.getFirst();
			return outcome(representativeId, claim.generation(), Outcome.CANCELLED, reasonCode);
		}

		PushSuppressionPolicy.Decision suppression = suppressionPolicy.evaluate(
			context.preference(),
			context.group().notificationType(),
			clock.instant(),
			context.group().policyExpiresAt(),
			context.lastRecommendationAttemptAt());
		if (suppression.action() == PushSuppressionPolicy.Action.DEFER) {
			groupClaims.deferGroup(
				claim.groupId(), claim.generation(), suppression.nextAttemptAt(), clock.instant());
			return outcome(representativeId, claim.generation(), Outcome.RETRY_SCHEDULED,
				suppression.reason().name());
		}
		if (suppression.action() == PushSuppressionPolicy.Action.CANCEL) {
			groupClaims.cancelGroup(claim.groupId(), claim.generation(), clock.instant());
			return outcome(representativeId, claim.generation(), Outcome.CANCELLED, suppression.reason().name());
		}

		List<ClaimedPushDeviceDispatch> devices = groupClaims.claimDevices(
			claim.groupId(), claim.generation(), eligibleByDevice, clock.instant(), claim.leaseUntil());
		if (devices.isEmpty()) {
			groupClaims.cancelGroup(claim.groupId(), claim.generation(), clock.instant());
			return outcome(representativeId, claim.generation(), Outcome.CANCELLED, "NO_CLAIMED_DEVICES");
		}
		representativeId = representativeDeliveryId(context, devices);

		PushBudgetReservation reservation = groupClaims.reserveBudget(
			claim.groupId(),
			claim.generation(),
			budgetPolicy.budgetDate(clock.instant(), context.budgetZone()),
			context.group().notificationType(),
			policyProperties,
			clock.instant());
		if (reservation == PushBudgetReservation.LIMIT_EXCEEDED
			|| reservation == PushBudgetReservation.STALE_GROUP) {
			groupClaims.cancelGroup(claim.groupId(), claim.generation(), clock.instant());
			return outcome(representativeId, claim.generation(),
				reservation == PushBudgetReservation.STALE_GROUP ? Outcome.STALE_CLAIM : Outcome.CANCELLED,
				reservation.name());
		}

		Outcome groupOutcome = null;
		String reasonCode = "ACCEPTED";
		for (ClaimedPushDeviceDispatch device : devices) {
			DeviceOutcome deviceOutcome = dispatchDevice(claim, context, device);
			if (groupOutcome == null) {
				groupOutcome = deviceOutcome.outcome();
				reasonCode = deviceOutcome.reasonCode();
			} else {
				groupOutcome = merge(groupOutcome, deviceOutcome.outcome());
				if (deviceOutcome.outcome() != Outcome.SENT) {
					reasonCode = deviceOutcome.reasonCode();
				}
			}
		}
		if (groupOutcome == null) {
			groupOutcome = Outcome.CANCELLED;
			reasonCode = "NO_CLAIMED_DEVICES";
		}
		try {
			groupClaims.finalizeGroup(claim.groupId(), claim.generation(), clock.instant());
		} catch (RuntimeException failure) {
			log.warn("Push group finalize isolated groupId={} generation={} reasonCode={}",
				claim.groupId(), claim.generation(), "FINALIZE_REJECTED");
		}
		return outcome(representativeId, claim.generation(), groupOutcome, reasonCode);
	}

	private DeviceOutcome dispatchDevice(
		ClaimedPushDispatchGroup claim,
		PushDispatchGroupContext context,
		ClaimedPushDeviceDispatch device
	) {
		try {
			PushDispatchContext sample = memberContext(context, device.deviceId());
			PushToken token = tokenProtector.decrypt(sample.device().tokenCiphertext());
			if (!clock.instant().isBefore(device.leaseUntil())) {
				return new DeviceOutcome(Outcome.STALE_CLAIM, "LEASE_EXPIRED");
			}
			int count = distinctNotificationCount(context, device);
			boolean hasRemainingTime = claimedMembers(context, device).stream()
				.anyMatch(member -> member.dispatchContext().targetValidity().hasRemainingTime());
			PushPayload payload = payloadFactory.create(
				context.group().notificationType(), count, hasRemainingTime);
			PushProviderResult providerResult = provider.send(new PushSendCommand(token, payload));
			Instant terminalAt = clock.instant();
			if (providerResult instanceof PushProviderResult.InvalidToken) {
				return invalidateDevice(claim, device, terminalAt);
			}
			return completeProviderResult(claim, device, providerResult, terminalAt);
		} catch (PushTokenProtectionException failure) {
			return completeDevice(claim, device, PushDeliveryTerminalResult.DEAD, clock.instant(),
				clock.instant(), null, Outcome.DEAD, "TOKEN_DECRYPTION_FAILED");
		} catch (RuntimeException failure) {
			return recordUnexpectedFailure(claim, device, clock.instant());
		}
	}

	private DeviceOutcome completeProviderResult(
		ClaimedPushDispatchGroup claim,
		ClaimedPushDeviceDispatch device,
		PushProviderResult providerResult,
		Instant at
	) {
		PushDeliveryRetryPolicy.Decision decision =
			retryPolicy.decide(deviceGeneration(device), providerResult, at);
		if (decision.result() == PushDeliveryTerminalResult.SENT) {
			String providerMessageId = ((PushProviderResult.Accepted)providerResult).providerMessageId();
			return completeDevice(claim, device, decision.result(), at, decision.nextAttemptAt(),
				providerMessageId, Outcome.SENT, "ACCEPTED");
		}
		if (decision.result() == PushDeliveryTerminalResult.FAILED) {
			return completeDevice(claim, device, decision.result(), at, decision.nextAttemptAt(),
				null, Outcome.RETRY_SCHEDULED, "RETRYABLE_FAILURE");
		}
		String reasonCode = providerResult instanceof PushProviderResult.PermanentFailure
			? "PERMANENT_FAILURE"
			: "MAX_ATTEMPTS";
		return completeDevice(claim, device, decision.result(), at, decision.nextAttemptAt(),
			null, Outcome.DEAD, reasonCode);
	}

	private DeviceOutcome invalidateDevice(
		ClaimedPushDispatchGroup claim, ClaimedPushDeviceDispatch device, Instant at) {
		try {
			boolean completed = groupClaims.invalidateClaimedDevice(
				claim.groupId(), claim.generation(), device.deviceId(), device.deliveryGenerations(), at);
			return completed
				? new DeviceOutcome(Outcome.DEAD, "INVALID_TOKEN")
				: new DeviceOutcome(Outcome.STALE_CLAIM, "TERMINAL_FENCE_REJECTED");
		} catch (RuntimeException failure) {
			return recordUnexpectedFailure(claim, device, clock.instant());
		}
	}

	private DeviceOutcome recordUnexpectedFailure(
		ClaimedPushDispatchGroup claim, ClaimedPushDeviceDispatch device, Instant at) {
		log.warn("Push device failure isolated groupId={} deviceId={} generation={} reasonCode={}",
			claim.groupId(), device.deviceId(), claim.generation(), "UNEXPECTED_FAILURE");
		try {
			PushDeliveryRetryPolicy.Decision decision = retryPolicy.decide(
				deviceGeneration(device), new PushProviderResult.RetryableFailure(null), at);
			Outcome outcome = decision.result() == PushDeliveryTerminalResult.FAILED
				? Outcome.RETRY_SCHEDULED
				: Outcome.DEAD;
			return completeDevice(claim, device, decision.result(), at, decision.nextAttemptAt(),
				null, outcome, "UNEXPECTED_FAILURE");
		} catch (RuntimeException failureRecordingFailure) {
			log.warn("Push device failure recording isolated groupId={} deviceId={} generation={} reasonCode={}",
				claim.groupId(), device.deviceId(), claim.generation(), "FAILURE_RECORDING_FAILED");
			return new DeviceOutcome(Outcome.FAILURE_RECORDING_FAILED, "FAILURE_RECORDING_FAILED");
		}
	}

	private DeviceOutcome completeDevice(
		ClaimedPushDispatchGroup claim,
		ClaimedPushDeviceDispatch device,
		PushDeliveryTerminalResult result,
		Instant at,
		Instant nextAttemptAt,
		String providerMessageId,
		Outcome completedOutcome,
		String safeReasonCode
	) {
		boolean completed = groupClaims.completeDevice(
			claim.groupId(), claim.generation(), device.deviceId(), device.deliveryGenerations(),
			result, at, nextAttemptAt, providerMessageId);
		return completed
			? new DeviceOutcome(completedOutcome, safeReasonCode)
			: new DeviceOutcome(Outcome.STALE_CLAIM, "TERMINAL_FENCE_REJECTED");
	}

	private static PushDispatchContext evaluable(PushDispatchContext context) {
		NotificationDelivery delivery = context.delivery();
		if (delivery.status() == DeliveryStatus.PROCESSING) {
			return context;
		}
		NotificationDelivery processing = new NotificationDelivery(
			delivery.id(),
			delivery.notificationId(),
			delivery.pushDeviceId(),
			DeliveryStatus.PROCESSING,
			delivery.attemptCount(),
			delivery.nextAttemptAt(),
			delivery.createdAt(),
			null,
			null);
		return new PushDispatchContext(
			processing, context.notification(), context.device(), context.actorId(), context.targetValidity());
	}

	private static PushDispatchContext memberContext(PushDispatchGroupContext context, long deviceId) {
		return claimedMembers(context, deviceId).stream()
			.findFirst()
			.map(PushDispatchMemberContext::dispatchContext)
			.orElseThrow(() -> new NotificationException(
				NotificationErrorCode.INVALID_VALUE_RANGE, "deviceId", "해당 기기의 member가 없습니다"));
	}

	private static List<PushDispatchMemberContext> claimedMembers(
		PushDispatchGroupContext context, ClaimedPushDeviceDispatch device) {
		Set<Long> claimedIds = device.deliveryGenerations().keySet();
		return context.members().stream()
			.filter(member -> claimedIds.contains(member.deliveryId()))
			.toList();
	}

	private static List<PushDispatchMemberContext> claimedMembers(
		PushDispatchGroupContext context, long deviceId) {
		return context.members().stream()
			.filter(member -> member.dispatchContext().device().id() == deviceId)
			.toList();
	}

	private static int distinctNotificationCount(
		PushDispatchGroupContext context, ClaimedPushDeviceDispatch device) {
		return (int) claimedMembers(context, device).stream()
			.map(member -> member.dispatchContext().notification().id())
			.distinct()
			.count();
	}

	private static int deviceGeneration(ClaimedPushDeviceDispatch device) {
		return device.deliveryGenerations().values().stream().mapToInt(Integer::intValue).max().orElse(1);
	}

	private static long representativeDeliveryId(
		PushDispatchGroupContext context, List<ClaimedPushDeviceDispatch> devices) {
		return devices.stream()
			.flatMap(device -> device.deliveryGenerations().keySet().stream())
			.min(Long::compareTo)
			.orElseGet(() -> context.members().stream()
				.mapToLong(PushDispatchMemberContext::deliveryId)
				.min()
				.orElse(context.group().id() == null ? 1L : context.group().id()));
	}

	private static Outcome merge(Outcome current, Outcome next) {
		if (current == Outcome.FAILURE_RECORDING_FAILED || next == Outcome.FAILURE_RECORDING_FAILED) {
			return Outcome.FAILURE_RECORDING_FAILED;
		}
		if (current == Outcome.RETRY_SCHEDULED || next == Outcome.RETRY_SCHEDULED) {
			return Outcome.RETRY_SCHEDULED;
		}
		if (current == Outcome.STALE_CLAIM || next == Outcome.STALE_CLAIM) {
			return Outcome.STALE_CLAIM;
		}
		if (current == Outcome.SENT || next == Outcome.SENT) {
			return Outcome.SENT;
		}
		if (current == Outcome.DEAD || next == Outcome.DEAD) {
			return Outcome.DEAD;
		}
		return next;
	}

	private static DeliveryOutcome outcome(
		long deliveryId, int generation, Outcome outcome, String safeReasonCode) {
		return new DeliveryOutcome(deliveryId, generation, outcome, safeReasonCode);
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

	private record DeviceOutcome(Outcome outcome, String reasonCode) {
	}
}
