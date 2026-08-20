package com.dnd.qello.notification.fanout;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionPostStatus;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.PostRecipientRepository;
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
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.safety.domain.UserBlock;
import com.dnd.qello.safety.repository.SafetyRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@code RECIPIENTS_CONFIRMED} event를 인앱 알림과 기기별 전달 작업으로 fan-out한다.
 * Provider 호출과 scheduler/polling은 이 클래스의 책임이 아니다.
 */
@Service
@RequiredArgsConstructor
public class RecipientNotificationFanOutWorker {

	private static final NotificationType FAN_OUT_TYPE = NotificationType.DIRECTION_POST_RECEIVED;
	private static final Set<OutboxEventType> CLAIMED_EVENT_TYPES =
		Set.of(OutboxEventType.RECIPIENTS_CONFIRMED);
	private static final Set<PostRecipientStatus> ELIGIBLE_RECIPIENT_STATUSES =
		Set.of(PostRecipientStatus.AVAILABLE, PostRecipientStatus.DISCOVERED,
			PostRecipientStatus.OPENED, PostRecipientStatus.SKIP_PENDING);
	private static final String DEDUP_KEY_PREFIX = "direction-post-received:";

	private final OutboxEventRepository outboxEventRepository;
	private final NotificationRepository notificationRepository;
	private final PostRecipientRepository recipientRepository;
	private final DirectionPostRepository postRepository;
	private final AccountRepository accountRepository;
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
		// fencing identity가 없으면 어떤 terminal UPDATE도 현재 owner의 상태를 안전하게
		// 식별할 수 없다. source를 추측해 갱신하지 않고 stale outcome으로 격리한다.
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
		PostRecipient recipient = lockAuthoritativeRecipient(event.aggregateId());
		if (!hasDeliverableRecipientStatus(recipient)) return;

		FanOutTarget target = new FanOutTarget(recipient, loadPost(recipient));
		if (!isRecordable(target, at)) return;

		persistFanOut(event, target, at);
	}

	private void requireFanOutEvent(OutboxEvent event) {
		if (event.aggregateType() != OutboxAggregateType.POST_RECIPIENT
			|| event.eventType() != OutboxEventType.RECIPIENTS_CONFIRMED
			|| event.status() != OutboxStatus.PROCESSING) {
			throw new PermanentFanOutException("notification fan-out event contract is invalid");
		}
	}

	private PostRecipient lockAuthoritativeRecipient(long aggregateId) {
		PostRecipient recipient = recipientRepository.findByIdForUpdate(aggregateId)
			.orElseThrow(() -> new PermanentFanOutException("post recipient does not exist"));
		if (recipient.getId() == null || recipient.getId() != aggregateId) {
			throw new PermanentFanOutException("post recipient aggregate identity is invalid");
		}
		return recipient;
	}

	private boolean hasDeliverableRecipientStatus(PostRecipient recipient) {
		return ELIGIBLE_RECIPIENT_STATUSES.contains(recipient.getStatus());
	}

	private DirectionPost loadPost(PostRecipient recipient) {
		return postRepository.findById(recipient.getPostId())
			.orElseThrow(() -> new PermanentFanOutException("direction post does not exist"));
	}

	/**
	 * 알림함에 기록을 남길지 판정한다(#176 결정 10). preference는 여기서 검사하지
	 * 않는다 — 차단·계정·만료는 기록 자체를 막아야 하지만, preference는 푸시 전달
	 * 자격({@link #isDeliverable})만 막아야 한다. 사용자가 알림을 전부 꺼도 알림함은
	 * 채워진다.
	 */
	private boolean isRecordable(FanOutTarget target, Instant at) {
		if (!isDeliverablePost(target.post(), at)) return false;
		if (!bothAccountsAreActive(target)) return false;
		return hasNoActiveBlock(target);
	}

	private boolean isDeliverablePost(DirectionPost post, Instant at) {
		return post.getStatus() == DirectionPostStatus.ACTIVE
			&& post.getDeletedAt() == null
			&& post.getExpiresAt().isAfter(at);
	}

	private boolean bothAccountsAreActive(FanOutTarget target) {
		return isActiveAccount(target.senderId()) && isActiveAccount(target.recipientId());
	}

	private boolean isActiveAccount(long accountId) {
		return accountRepository.findById(accountId)
			.map(Account::getStatus)
			.filter(AccountStatus.ACTIVE::equals)
			.isPresent();
	}

	private boolean hasNoActiveBlock(FanOutTarget target) {
		return !isActivelyBlocked(target.recipientId(), target.senderId())
			&& !isActivelyBlocked(target.senderId(), target.recipientId());
	}

	private boolean isActivelyBlocked(long blockerId, long blockedId) {
		return safetyRepository.findBlock(blockerId, blockedId)
			.filter(UserBlock::active)
			.isPresent();
	}

	/** 알림함 기록과 별개로, 이 수신자에게 실제로 푸시를 시도해도 되는지 판정한다. */
	private boolean isPushEnabled(long recipientId) {
		return notificationRepository.isPreferenceEnabled(recipientId, FAN_OUT_TYPE);
	}

	private void persistFanOut(OutboxEvent event, FanOutTarget target, Instant at) {
		Notification notification = notificationRepository.saveIfAbsent(newNotification(event, target, at));
		long notificationId = requireNotificationId(notification);
		persistPendingDeliveries(notificationId, target.recipientId(), at);
	}

	private Notification newNotification(OutboxEvent event, FanOutTarget target, Instant at) {
		return new Notification(null, target.recipientId(), event.id(), FAN_OUT_TYPE,
			DEDUP_KEY_PREFIX + event.aggregateId(), target.postId(), null,
			NotificationStatus.UNREAD, at, null);
	}

	private long requireNotificationId(Notification notification) {
		if (notification.id() == null) {
			throw new PermanentFanOutException("persisted notification has no id");
		}
		return notification.id();
	}

	/** preference가 꺼져 있으면 알림함 행은 이미 만들어졌어도 delivery는 만들지 않는다. */
	private void persistPendingDeliveries(long notificationId, long recipientId, Instant at) {
		if (!isPushEnabled(recipientId)) return;
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

	private record FanOutTarget(PostRecipient recipient, DirectionPost post) {
		private long recipientId() {
			return recipient.getRecipientId();
		}

		private long senderId() {
			return post.getSenderId();
		}

		private long postId() {
			return recipient.getPostId();
		}
	}
}
