/**
 * Created at: 2026-08-17T16:40:00+09:00
 * Source scenario: TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-008 through UNIT-010
 */
package com.dnd.qello.notification.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dnd.qello.notification.domain.NotificationEvent;
import com.dnd.qello.notification.domain.NotificationEventStatus;
import com.dnd.qello.notification.domain.NotificationRetryPolicy;
import com.dnd.qello.notification.repository.NotificationEventRepository;

class SlackManualReviewNotificationDispatchWorkerTest {

	private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
	private static final String ADMIN_LINK_PATH = "/admin/filtering/manual-review-cases/7";
	private static final NotificationRetryPolicy RETRY_POLICY =
		new NotificationRetryPolicy(3, attempt -> Duration.ofSeconds(30));

	private final NotificationEventRepository notificationEventRepository = mock(NotificationEventRepository.class);
	private final SlackNotifier slackNotifier = mock(SlackNotifier.class);
	private final SlackManualReviewNotificationDispatchWorker worker =
		new SlackManualReviewNotificationDispatchWorker(notificationEventRepository, slackNotifier,
			Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	@DisplayName("Slack 전송이 성공하면 caseId·adminLinkPath만 전달하고 event를 complete 처리한다")
	void completesEventOnSuccessfulSend() {
		NotificationEvent claimed = claimedEvent(1L, 0);
		when(notificationEventRepository.claimDue(10, "slack-worker-1", NOW, NOW.plusSeconds(30)))
			.thenReturn(List.of(claimed));
		when(notificationEventRepository.complete(1L, "slack-worker-1", 1L, NOW)).thenReturn(true);

		SlackManualReviewNotificationDispatchWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes())
			.containsExactly(SlackManualReviewNotificationDispatchWorker.Outcome.PROCESSED);
		ArgumentCaptor<SlackNotification> captor = ArgumentCaptor.forClass(SlackNotification.class);
		verify(slackNotifier).send(captor.capture());
		assertThat(captor.getValue().caseId()).isEqualTo(7L);
		assertThat(captor.getValue().adminLinkPath()).isEqualTo(ADMIN_LINK_PATH);
		verify(notificationEventRepository).complete(1L, "slack-worker-1", 1L, NOW);
	}

	@Test
	@DisplayName("Slack 전송이 재시도 가능한 실패면 retry policy 판정대로 fail 처리하고 다른 repository는 건드리지 않는다")
	void failsEventOnRetryableSendFailure() {
		NotificationEvent claimed = claimedEvent(1L, 0);
		when(notificationEventRepository.claimDue(10, "slack-worker-1", NOW, NOW.plusSeconds(30)))
			.thenReturn(List.of(claimed));
		doThrowOnSend(new SlackDeliveryException(true, "timeout", null));
		when(notificationEventRepository.fail(eq(1L), eq("slack-worker-1"), eq(1L), eq(NOW), any()))
			.thenReturn(true);

		SlackManualReviewNotificationDispatchWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes())
			.containsExactly(SlackManualReviewNotificationDispatchWorker.Outcome.RETRYABLE);
		verify(notificationEventRepository).fail(eq(1L), eq("slack-worker-1"), eq(1L), eq(NOW), any());
		verify(notificationEventRepository, never()).complete(anyLong(), any(), anyLong(), any());
	}

	@Test
	@DisplayName("Slack 전송이 영구 실패면 재시도 횟수와 무관하게 dead로 판정한다")
	void marksDeadOnPermanentSendFailure() {
		NotificationEvent claimed = claimedEvent(1L, 0);
		when(notificationEventRepository.claimDue(10, "slack-worker-1", NOW, NOW.plusSeconds(30)))
			.thenReturn(List.of(claimed));
		doThrowOnSend(new SlackDeliveryException(false, "invalid payload", null));
		when(notificationEventRepository.fail(eq(1L), eq("slack-worker-1"), eq(1L), eq(NOW), any()))
			.thenReturn(true);

		SlackManualReviewNotificationDispatchWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(SlackManualReviewNotificationDispatchWorker.Outcome.DEAD);
	}

	@Test
	@DisplayName("SlackDeliveryException이 아닌 다른 RuntimeException도 격리해 RETRYABLE로 처리하고 batch를 중단시키지 않는다")
	void isolatesUnexpectedRuntimeExceptionFromSend() {
		NotificationEvent claimed = claimedEvent(1L, 0);
		when(notificationEventRepository.claimDue(10, "slack-worker-1", NOW, NOW.plusSeconds(30)))
			.thenReturn(List.of(claimed));
		org.mockito.Mockito.doThrow(new IllegalStateException("unexpected client bug"))
			.when(slackNotifier).send(any());
		when(notificationEventRepository.fail(eq(1L), eq("slack-worker-1"), eq(1L), eq(NOW), any()))
			.thenReturn(true);

		SlackManualReviewNotificationDispatchWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes())
			.containsExactly(SlackManualReviewNotificationDispatchWorker.Outcome.RETRYABLE);
		verify(notificationEventRepository).fail(eq(1L), eq("slack-worker-1"), eq(1L), eq(NOW), any());
	}

	@Test
	@DisplayName("leaseGeneration이 0인 claim 결과는 fencing identity가 없는 것으로 보고 STALE_LEASE로 분류한다")
	void classifiesMissingLeaseIdentityAsStale() {
		NotificationEvent withoutLeaseGeneration = new NotificationEvent(1L, 7L, ADMIN_LINK_PATH,
			NotificationEventStatus.PROCESSING, 1, NOW, NOW, null, "slack-worker-1", NOW.plusSeconds(30), 0);
		when(notificationEventRepository.claimDue(10, "slack-worker-1", NOW, NOW.plusSeconds(30)))
			.thenReturn(List.of(withoutLeaseGeneration));

		SlackManualReviewNotificationDispatchWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes())
			.containsExactly(SlackManualReviewNotificationDispatchWorker.Outcome.STALE_LEASE);
		verify(notificationEventRepository, never()).complete(anyLong(), any(), anyLong(), any());
		verify(notificationEventRepository, never()).fail(anyLong(), any(), anyLong(), any(), any());
	}

	private void doThrowOnSend(SlackDeliveryException exception) {
		org.mockito.Mockito.doThrow(exception).when(slackNotifier).send(any());
	}

	private NotificationEvent claimedEvent(long id, int attemptCountBeforeClaim) {
		return new NotificationEvent(id, 7L, ADMIN_LINK_PATH, NotificationEventStatus.PROCESSING,
			attemptCountBeforeClaim + 1, NOW, NOW, null, "slack-worker-1", NOW.plusSeconds(30), 1);
	}

	private SlackManualReviewNotificationDispatchWorker.BatchCommand command() {
		return new SlackManualReviewNotificationDispatchWorker.BatchCommand(
			10, "slack-worker-1", NOW, NOW.plusSeconds(30), RETRY_POLICY);
	}
}
