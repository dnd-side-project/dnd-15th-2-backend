package com.dnd.qello.notification.slack;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.dnd.qello.notification.domain.NotificationEvent;
import com.dnd.qello.notification.domain.NotificationRetryPolicy;
import com.dnd.qello.notification.domain.OutboxFailureKind;
import com.dnd.qello.notification.domain.OutboxRetryDecision;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.repository.NotificationEventRepository;

/**
 * {@code notification_event}를 claim해 Slack으로 발송한다(#111). claim(자체
 * 원자적 UPDATE) → send(DB 트랜잭션 밖) → complete/fail(자체 원자적 UPDATE)의
 * 세 단계로 나뉘어 있다 — send는 외부 HTTP 호출이라 DB 트랜잭션을 열어둘
 * 이유가 없고, 각 단계는 lease generation 검증으로 이미 개별적으로 안전하다.
 * {@code manual_review_case}/{@code filter_job} 어느 것도 참조·수정하지 않아
 * Slack 장애가 두 테이블에 영향을 줄 수 없다({@code INV-SLK-002}).
 *
 * webhook/secret과 실제 재시도 수치가 미결정이라 {@code AnswerModerationJobIntakeService}
 * (#105), {@code SnapshotHealthProbeRecorder}(#109)와 동일하게 Spring bean으로
 * 배선하지 않는다. #113이 production 활성화 게이트와 감사·지표를 갖췄지만 secret
 * 저장·rotation을 범위에서 제외해, 실제 {@link SlackNotifier} 구현체와 scheduler
 * 배선은 그 게이트를 통과한 뒤의 후속 이슈 몫으로 남았다
 * (docs/filtering-production-gate.md 3절).
 */
public class SlackManualReviewNotificationDispatchWorker {

	private final NotificationEventRepository notificationEventRepository;
	private final SlackNotifier slackNotifier;
	private final Clock clock;

	public SlackManualReviewNotificationDispatchWorker(NotificationEventRepository notificationEventRepository,
			SlackNotifier slackNotifier, Clock clock) {
		this.notificationEventRepository = notificationEventRepository;
		this.slackNotifier = slackNotifier;
		this.clock = clock;
	}

	public BatchResult processBatch(BatchCommand command) {
		requireCommand(command);
		Instant claimAt = resolveTime(command);
		requireOpenLeaseWindow(command, claimAt);
		List<NotificationEvent> claimed = notificationEventRepository.claimDue(
			command.limit(), command.leaseOwner(), claimAt, command.leaseExpiresAt());
		List<Outcome> outcomes = claimed.stream()
			.map(event -> processClaimedEvent(event, command, resolveTime(command)))
			.toList();
		return new BatchResult(claimed.size(), outcomes);
	}

	private Outcome processClaimedEvent(NotificationEvent event, BatchCommand command, Instant processingAt) {
		// fencing identity가 없으면 어떤 terminal UPDATE도 현재 owner의 상태를 안전하게
		// 식별할 수 없다. source를 추측해 갱신하지 않고 stale outcome으로 격리한다.
		if (!hasLeaseIdentity(event)) return Outcome.STALE_LEASE;
		try {
			slackNotifier.send(new SlackNotification(event.caseId(), event.adminLinkPath()));
			return complete(event, processingAt);
		} catch (RuntimeException failure) {
			// SlackDeliveryException만 잡으면 구현체가 다른 RuntimeException(네트워크
			// 예외 래핑, 직렬화 오류 등)을 던졌을 때 이 batch의 나머지 event 처리까지
			// 함께 중단된다. SlackDeliveryException이 아니면 재시도 정책이 maxAttempts로
			// 상한을 잡아주므로 RETRYABLE로 안전하게 처리한다.
			OutboxFailureKind failureKind = failure instanceof SlackDeliveryException slackFailure
				? (slackFailure.retryable() ? OutboxFailureKind.RETRYABLE : OutboxFailureKind.PERMANENT)
				: OutboxFailureKind.RETRYABLE;
			return recordFailure(event, command, processingAt, failureKind);
		}
	}

	private boolean hasLeaseIdentity(NotificationEvent event) {
		return event != null && event.id() != null && event.leaseOwner() != null
			&& !event.leaseOwner().isBlank() && event.leaseGeneration() > 0;
	}

	private Outcome complete(NotificationEvent event, Instant at) {
		if (!notificationEventRepository.complete(event.id(), event.leaseOwner(), event.leaseGeneration(), at)) {
			return Outcome.STALE_LEASE;
		}
		return Outcome.PROCESSED;
	}

	private Outcome recordFailure(NotificationEvent event, BatchCommand command, Instant at,
			OutboxFailureKind failureKind) {
		try {
			OutboxRetryDecision decision = command.retryPolicy().decide(event, failureKind, at);
			if (!notificationEventRepository.fail(event.id(), event.leaseOwner(), event.leaseGeneration(), at,
					decision)) {
				return Outcome.STALE_LEASE;
			}
			return decision.dead() ? Outcome.DEAD : Outcome.RETRYABLE;
		} catch (RuntimeException failureRecordingFailure) {
			return Outcome.FAILURE_RECORDING_FAILED;
		}
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
			NotificationRetryPolicy retryPolicy) {
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
}
