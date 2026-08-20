package com.dnd.qello.notification.fanout;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxFailureKind;
import com.dnd.qello.notification.domain.OutboxRetryDecision;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.domain.OutboxStatus;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.safety.domain.UserBlock;
import com.dnd.qello.safety.repository.SafetyRepository;

@Service
public class NotificationFanOutWorker {

	private static final Logger log = LoggerFactory.getLogger(NotificationFanOutWorker.class);

	private final OutboxEventRepository outboxEventRepository;
	private final NotificationRepository notificationRepository;
	private final Map<OutboxEventType, NotificationFanOutResolver> resolvers;
	private final AccountRepository accountRepository;
	private final SafetyRepository safetyRepository;
	private final PlatformTransactionManager transactionManager;
	private final Clock clock;

	public NotificationFanOutWorker(
		OutboxEventRepository outboxEventRepository,
		NotificationRepository notificationRepository,
		List<NotificationFanOutResolver> resolvers,
		AccountRepository accountRepository,
		SafetyRepository safetyRepository,
		PlatformTransactionManager transactionManager,
		Clock clock
	) {
		this.outboxEventRepository = outboxEventRepository;
		this.notificationRepository = notificationRepository;
		this.resolvers = indexResolvers(resolvers);
		this.accountRepository = accountRepository;
		this.safetyRepository = safetyRepository;
		this.transactionManager = transactionManager;
		this.clock = clock;
	}

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
		return outboxEventRepository.claimDue(
			resolvers.keySet(), command.limit(), command.leaseOwner(), claimAt, command.leaseExpiresAt());
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
			return recordFailure(event, command.retryPolicy(), processingAt, failureKind(failure));
		} catch (RuntimeException failureRecordingFailure) {
			log.warn("Failed to record notification fan-out failure for event id={} type={}",
				event.id(), event.eventType(), failureRecordingFailure);
			return Outcome.FAILURE_RECORDING_FAILED;
		}
	}

	private void executeEventTransaction(OutboxEvent event, Instant at) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> fanOutInTransaction(event, at));
	}

	private void fanOutInTransaction(OutboxEvent event, Instant at) {
		requireProcessingEvent(event);
		FanOutInstruction instruction = resolverFor(event).resolve(event);
		if (shouldPersistNotification(instruction)) {
			persistFanOut(event, instruction, at);
		}
		completeClaim(event, at);
	}

	private void requireProcessingEvent(OutboxEvent event) {
		if (event.status() != OutboxStatus.PROCESSING) {
			throw permanent("event must be PROCESSING");
		}
	}

	private NotificationFanOutResolver resolverFor(OutboxEvent event) {
		NotificationFanOutResolver resolver = resolvers.get(event.eventType());
		if (resolver == null) throw permanent("unsupported notification event type");
		return resolver;
	}

	private boolean shouldPersistNotification(FanOutInstruction instruction) {
		return !instruction.suppressed() && isRecordable(instruction);
	}

	private boolean isRecordable(FanOutInstruction instruction) {
		if (!isActiveAccount(instruction.recipientId())) return false;
		if (instruction.actorId() == null) return true;
		return isActiveAccount(instruction.actorId())
			&& !isActivelyBlocked(instruction.recipientId(), instruction.actorId())
			&& !isActivelyBlocked(instruction.actorId(), instruction.recipientId());
	}

	private boolean isActiveAccount(long accountId) {
		return accountRepository.findById(accountId)
			.map(Account::getStatus)
			.filter(AccountStatus.ACTIVE::equals)
			.isPresent();
	}

	private boolean isActivelyBlocked(long blockerId, long blockedId) {
		return safetyRepository.findBlock(blockerId, blockedId)
			.filter(UserBlock::active)
			.isPresent();
	}

	private void persistFanOut(OutboxEvent event, FanOutInstruction instruction, Instant at) {
		Notification notification = notificationRepository.saveIfAbsent(newNotification(event, instruction, at));
		long notificationId = requireNotificationId(notification);
		persistPendingDeliveries(notificationId, instruction, at);
	}

	private Notification newNotification(OutboxEvent event, FanOutInstruction instruction, Instant at) {
		return new Notification(null, instruction.recipientId(), event.id(), instruction.notificationType(),
			instruction.dedupKey(), null, instruction.answerId(), null, NotificationStatus.UNREAD, at, null);
	}

	private long requireNotificationId(Notification notification) {
		if (notification.id() == null) throw permanent("persisted notification has no id");
		return notification.id();
	}

	private void persistPendingDeliveries(long notificationId, FanOutInstruction instruction, Instant at) {
		if (!notificationRepository.isPreferenceEnabled(instruction.recipientId(), instruction.notificationType())) {
			return;
		}
		for (long deviceId : notificationRepository.findActiveDeviceIdsByUserId(instruction.recipientId())) {
			notificationRepository.saveDeliveryIfAbsent(NotificationDelivery.pending(notificationId, deviceId, at));
		}
	}

	private void completeClaim(OutboxEvent event, Instant at) {
		if (!outboxEventRepository.complete(event.id(), event.leaseOwner(), event.leaseGeneration(), at)) {
			throw new StaleLeaseException();
		}
	}

	private Outcome recordFailure(OutboxEvent event, OutboxRetryPolicy policy, Instant at,
		OutboxFailureKind kind) {
		OutboxRetryDecision decision = policy.decide(event, kind, at);
		if (!outboxEventRepository.fail(event.id(), event.leaseOwner(), event.leaseGeneration(), at, decision)) {
			return Outcome.STALE_LEASE;
		}
		return decision.dead() ? Outcome.DEAD : Outcome.RETRYABLE;
	}

	private OutboxFailureKind failureKind(RuntimeException failure) {
		if (failure instanceof TransientDataAccessException
			|| failure instanceof RecoverableDataAccessException) {
			return OutboxFailureKind.RETRYABLE;
		}
		return OutboxFailureKind.PERMANENT;
	}

	private static Map<OutboxEventType, NotificationFanOutResolver> indexResolvers(
		List<NotificationFanOutResolver> resolverList) {
		if (resolverList == null || resolverList.isEmpty()) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "resolvers",
				"notification fan-out resolver가 필요합니다");
		}
		Map<OutboxEventType, NotificationFanOutResolver> indexed = new LinkedHashMap<>();
		for (NotificationFanOutResolver resolver : resolverList) {
			for (OutboxEventType eventType : eventTypesOf(resolver)) {
				if (eventType == null || indexed.put(eventType, resolver) != null) {
					throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "eventTypes",
						"notification fan-out event type이 중복됩니다");
				}
			}
		}
		return Map.copyOf(indexed);
	}

	private static Set<OutboxEventType> eventTypesOf(NotificationFanOutResolver resolver) {
		if (resolver == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "resolvers",
				"유효한 notification fan-out resolver가 필요합니다");
		}
		Set<OutboxEventType> eventTypes = resolver.eventTypes();
		if (eventTypes == null || eventTypes.isEmpty()) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "resolvers",
				"유효한 notification fan-out resolver가 필요합니다");
		}
		return eventTypes;
	}

	private boolean hasLeaseIdentity(OutboxEvent event) {
		return event != null && event.id() != null && event.leaseOwner() != null
			&& !event.leaseOwner().isBlank() && event.leaseGeneration() > 0;
	}

	private Instant resolveTime(BatchCommand command) {
		return command.at() == null ? clock.instant() : command.at();
	}

	private static void requireCommand(BatchCommand command) {
		if (command == null) throw required("command");
	}

	private static void requireOpenLeaseWindow(BatchCommand command, Instant claimAt) {
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

	private static NotificationException permanent(String reason) {
		return new NotificationException(NotificationErrorCode.INVALID_PAYLOAD, "event", reason);
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
			if (claimed < 0 || outcomes == null) throw invalid("batch", "batch 결과가 유효하지 않습니다");
			outcomes = List.copyOf(outcomes);
		}
	}

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
			if (at != null && !leaseExpiresAt.isAfter(at)) throw invalid("leaseExpiresAt", "lease 시각이 유효하지 않습니다");
			if (retryPolicy == null) throw required("retryPolicy");
		}
	}

	private static final class StaleLeaseException extends RuntimeException {
	}
}
