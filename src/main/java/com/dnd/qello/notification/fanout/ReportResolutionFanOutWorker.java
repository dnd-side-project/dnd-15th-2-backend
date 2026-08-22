package com.dnd.qello.notification.fanout;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxFailureKind;
import com.dnd.qello.notification.domain.OutboxRetryDecision;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.domain.OutboxStatus;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.repository.NotificationPreferenceRepository;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.repository.SafetyRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@code REPORT_RESOLVED} event를 신고자별 결과 알림으로 fan-out한다(#155).
 * {@link RecipientNotificationFanOutWorker}와 같은 claim·lease-fencing·재시도·
 * stale 처리 골격을 따르되, 선호 설정 게이트는 다르게 동작한다 — 인앱 알림은
 * 항상 만들고, {@code notification_delivery}(push)만 선호로 게이트한다.
 */
@Service
@RequiredArgsConstructor
public class ReportResolutionFanOutWorker {

	private static final NotificationType FAN_OUT_TYPE = NotificationType.REPORT_RESOLVED;
	private static final Set<OutboxEventType> CLAIMED_EVENT_TYPES = Set.of(OutboxEventType.REPORT_RESOLVED);
	private static final String DEDUP_KEY_PREFIX = "report-resolved:";

	private final OutboxEventRepository outboxEventRepository;
	private final NotificationRepository notificationRepository;
	private final NotificationPreferenceRepository preferenceRepository;
	private final SafetyRepository safetyRepository;
	private final PlatformTransactionManager transactionManager;
	private final Clock clock;

	public BatchResult processBatch(BatchCommand command) {
		requireCommand(command);
		Instant claimAt = resolveTime(command);
		requireOpenLeaseWindow(command, claimAt);
		List<OutboxEvent> claimed = claimDueEvents(command, claimAt);
		List<Outcome> outcomes = claimed.stream()
			.map(event -> processClaimedEvent(event, command, resolveTime(command)))
			.toList();
		return new BatchResult(claimed.size(), outcomes);
	}

	private List<OutboxEvent> claimDueEvents(BatchCommand command, Instant claimAt) {
		return outboxEventRepository.claimDue(CLAIMED_EVENT_TYPES, command.limit(), command.leaseOwner(), claimAt,
			command.leaseExpiresAt());
	}

	private Outcome processClaimedEvent(OutboxEvent event, BatchCommand command, Instant processingAt) {
		if (!hasLeaseIdentity(event)) return Outcome.STALE_LEASE;
		try {
			executeEventTransaction(event, processingAt);
			return Outcome.PROCESSED;
		} catch (RuntimeException failure) {
			return handleProcessingFailure(event, command, processingAt, failure);
		}
	}

	private Outcome handleProcessingFailure(OutboxEvent event, BatchCommand command, Instant processingAt,
		RuntimeException failure) {
		if (failure instanceof StaleLeaseException) return Outcome.STALE_LEASE;
		try {
			return recordFailure(event, command, processingAt, failureKind(failure));
		} catch (RuntimeException failureRecordingFailure) {
			return Outcome.FAILURE_RECORDING_FAILED;
		}
	}

	private OutboxFailureKind failureKind(RuntimeException failure) {
		return failure instanceof TransientDataAccessException
			? OutboxFailureKind.RETRYABLE
			: OutboxFailureKind.PERMANENT;
	}

	private boolean hasLeaseIdentity(OutboxEvent event) {
		return event != null && event.id() != null && event.leaseOwner() != null
			&& !event.leaseOwner().isBlank() && event.leaseGeneration() > 0;
	}

	private void executeEventTransaction(OutboxEvent event, Instant at) {
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		transaction.executeWithoutResult(status -> {
			fanOutIfEligible(event, at);
			completeClaim(event, at);
		});
	}

	private void fanOutIfEligible(OutboxEvent event, Instant at) {
		requireFanOutEvent(event);
		Report report = safetyRepository.findReportById(event.aggregateId())
			.orElseThrow(() -> new PermanentFanOutException("report does not exist"));
		persistFanOut(event, report, at);
	}

	private void requireFanOutEvent(OutboxEvent event) {
		if (event.aggregateType() != OutboxAggregateType.REPORT
			|| event.eventType() != OutboxEventType.REPORT_RESOLVED
			|| event.status() != OutboxStatus.PROCESSING) {
			throw new PermanentFanOutException("report resolution fan-out event contract is invalid");
		}
	}

	private void persistFanOut(OutboxEvent event, Report report, Instant at) {
		Notification notification = notificationRepository.saveIfAbsent(newNotification(event, report, at));
		long notificationId = requireNotificationId(notification);
		// 인앱 알림은 선호와 무관하게 항상 만든다 — push 전달만 선호로 게이트한다(#155).
		persistPendingDeliveries(notificationId, report.reporterId(), at);
	}

	private Notification newNotification(OutboxEvent event, Report report, Instant at) {
		return new Notification(null, report.reporterId(), event.id(), FAN_OUT_TYPE,
			DEDUP_KEY_PREFIX + report.id(), null, null, report.id(),
			NotificationStatus.UNREAD, at, null);
	}

	private long requireNotificationId(Notification notification) {
		if (notification.id() == null) {
			throw new PermanentFanOutException("persisted notification has no id");
		}
		return notification.id();
	}

	private void persistPendingDeliveries(long notificationId, long recipientId, Instant at) {
		if (!preferenceRepository.isPushEnabled(recipientId, FAN_OUT_TYPE)) {
			return;
		}
		for (long deviceId : notificationRepository.findActiveDeviceIdsByUserId(recipientId)) {
			notificationRepository.saveDeliveryIfAbsent(NotificationDelivery.pending(notificationId, deviceId, at));
		}
	}

	private void completeClaim(OutboxEvent event, Instant at) {
		if (!outboxEventRepository.complete(event.id(), event.leaseOwner(), event.leaseGeneration(), at)) {
			throw new StaleLeaseException();
		}
	}

	private Outcome recordFailure(OutboxEvent event, BatchCommand command, Instant at,
		OutboxFailureKind failureKind) {
		OutboxRetryDecision decision = failureDecision(event, command.retryPolicy(), failureKind, at);
		if (!outboxEventRepository.fail(event.id(), event.leaseOwner(), event.leaseGeneration(), at, decision)) {
			return Outcome.STALE_LEASE;
		}
		return decision.dead() ? Outcome.DEAD : Outcome.RETRYABLE;
	}

	private OutboxRetryDecision failureDecision(OutboxEvent event, OutboxRetryPolicy retryPolicy,
		OutboxFailureKind failureKind, Instant at) {
		if (event.status() != OutboxStatus.PROCESSING) return new OutboxRetryDecision(true, at);
		return retryPolicy.decide(event, failureKind, at);
	}

	private Instant resolveTime(BatchCommand command) {
		return command.at() == null ? clock.instant() : command.at();
	}

	private void requireCommand(BatchCommand command) {
		if (command == null) {
			throw required("command");
		}
	}

	private void requireOpenLeaseWindow(BatchCommand command, Instant claimAt) {
		if (!command.leaseExpiresAt().isAfter(claimAt)) {
			throw invalid("leaseExpiresAt", "lease 만료 시각은 claim 시각 이후여야 합니다");
		}
	}

	private static NotificationException required(String field) {
		return new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, field,
			field + "은 필수입니다");
	}

	private static NotificationException invalid(String field, String reason) {
		return new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, field, reason);
	}

	public enum Outcome {
		PROCESSED,
		RETRYABLE,
		DEAD,
		STALE_LEASE,
		FAILURE_RECORDING_FAILED
	}

	public record BatchResult(int claimed, List<Outcome> outcomes) {
		public BatchResult {
			if (claimed < 0) throw invalid("claimed", "claimed는 음수일 수 없습니다");
			if (outcomes == null) throw required("outcomes");
			outcomes = List.copyOf(outcomes);
		}
	}

	/** at이 null이면 claim 시각과 각 event 처리 시각을 Clock에서 각각 읽는다. */
	public record BatchCommand(int limit, String leaseOwner, Instant at, Instant leaseExpiresAt,
		OutboxRetryPolicy retryPolicy) {
		public BatchCommand {
			if (limit <= 0) throw invalid("limit", "limit는 양수여야 합니다");
			if (leaseOwner == null) throw required("leaseOwner");
			if (leaseOwner.isBlank()) {
				throw new NotificationException(NotificationErrorCode.INVALID_TEXT, "leaseOwner",
					"leaseOwner는 공백일 수 없습니다");
			}
			if (leaseExpiresAt == null) throw required("leaseExpiresAt");
			if (at != null && !leaseExpiresAt.isAfter(at)) {
				throw invalid("leaseExpiresAt", "lease 만료 시각은 claim 시각 이후여야 합니다");
			}
			if (retryPolicy == null) throw required("retryPolicy");
		}
	}

	private static final class PermanentFanOutException extends RuntimeException {
		private PermanentFanOutException(String message) {
			super(message);
		}
	}

	private static final class StaleLeaseException extends RuntimeException {
	}
}
