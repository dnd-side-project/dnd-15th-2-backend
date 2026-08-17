package com.dnd.qello.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.domain.NotificationEvent;
import com.dnd.qello.notification.domain.NotificationEventStatus;
import com.dnd.qello.notification.error.NotificationException;

/**
 * Created at: 2026-08-17T16:10:00+09:00
 * Source scenario: TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-001 through UNIT-005
 */
class NotificationEventTest {

	private static final Instant CREATED_AT = Instant.parse("2026-08-17T00:00:00Z");
	private static final Instant CLAIMED_AT = CREATED_AT.plusSeconds(10);
	private static final Instant LEASE_EXPIRES_AT = CLAIMED_AT.plusSeconds(30);
	private static final String ADMIN_LINK_PATH = "/admin/filtering/manual-review-cases/7";

	@Test
	@DisplayName("pending은 시도 0회의 PENDING 상태를 만들고 lease 필드를 비운다")
	void createsPendingEventWithoutLease() {
		NotificationEvent event = NotificationEvent.pending(7L, ADMIN_LINK_PATH, CREATED_AT);

		assertThat(event.status()).isEqualTo(NotificationEventStatus.PENDING);
		assertThat(event.attemptCount()).isZero();
		assertThat(event.leaseOwner()).isNull();
		assertThat(event.leaseExpiresAt()).isNull();
		assertThat(event.leaseGeneration()).isZero();
	}

	@Test
	@DisplayName("caseId가 0 이하면 생성을 거절한다")
	void rejectsNonPositiveCaseId() {
		assertThatThrownBy(() -> NotificationEvent.pending(0L, ADMIN_LINK_PATH, CREATED_AT))
			.isInstanceOf(NotificationException.class);
	}

	@Test
	@DisplayName("adminLinkPath가 공백이면 생성을 거절한다")
	void rejectsBlankAdminLinkPath() {
		assertThatThrownBy(() -> NotificationEvent.pending(7L, " ", CREATED_AT))
			.isInstanceOf(NotificationException.class);
	}

	@Test
	@DisplayName("attemptCount가 음수면 생성을 거절한다")
	void rejectsNegativeAttemptCount() {
		assertThatThrownBy(() -> new NotificationEvent(null, 7L, ADMIN_LINK_PATH, NotificationEventStatus.PENDING,
			-1, CREATED_AT, CREATED_AT, null, null, null, 0))
			.isInstanceOf(NotificationException.class);
	}

	@Test
	@DisplayName("PENDING/FAILED 상태 event는 claim되면 PROCESSING으로 전이하고 lease와 시도 횟수를 갱신한다")
	void claimsPendingEvent() {
		NotificationEvent event = NotificationEvent.pending(7L, ADMIN_LINK_PATH, CREATED_AT);

		NotificationEvent claimed = event.claimed("worker-a", CLAIMED_AT, LEASE_EXPIRES_AT);

		assertThat(claimed.status()).isEqualTo(NotificationEventStatus.PROCESSING);
		assertThat(claimed.attemptCount()).isEqualTo(1);
		assertThat(claimed.leaseOwner()).isEqualTo("worker-a");
		assertThat(claimed.leaseExpiresAt()).isEqualTo(LEASE_EXPIRES_AT);
		assertThat(claimed.leaseGeneration()).isEqualTo(1);
	}

	@Test
	@DisplayName("PROCESSED/DEAD 상태 event는 claim을 거절한다")
	void rejectsClaimFromTerminalStatus() {
		NotificationEvent processed = new NotificationEvent(1L, 7L, ADMIN_LINK_PATH, NotificationEventStatus.PROCESSED,
			1, CREATED_AT, CREATED_AT, CREATED_AT, null, null, 1);
		NotificationEvent dead = new NotificationEvent(1L, 7L, ADMIN_LINK_PATH, NotificationEventStatus.DEAD,
			3, CREATED_AT, CREATED_AT, null, null, null, 1);

		assertThatThrownBy(() -> processed.claimed("worker-a", CLAIMED_AT, LEASE_EXPIRES_AT))
			.isInstanceOf(NotificationException.class);
		assertThatThrownBy(() -> dead.claimed("worker-a", CLAIMED_AT, LEASE_EXPIRES_AT))
			.isInstanceOf(NotificationException.class);
	}

	@Test
	@DisplayName("PROCESSING 상태만 완료할 수 있고 완료 시 lease를 비운다")
	void completesProcessingEvent() {
		NotificationEvent processing = NotificationEvent.pending(7L, ADMIN_LINK_PATH, CREATED_AT)
			.claimed("worker-a", CLAIMED_AT, LEASE_EXPIRES_AT);

		NotificationEvent processed = processing.processed(CLAIMED_AT.plusSeconds(1));

		assertThat(processed.status()).isEqualTo(NotificationEventStatus.PROCESSED);
		assertThat(processed.processedAt()).isEqualTo(CLAIMED_AT.plusSeconds(1));
		assertThat(processed.leaseOwner()).isNull();
		assertThat(processed.leaseExpiresAt()).isNull();
	}

	@Test
	@DisplayName("PENDING 상태 event는 완료할 수 없다")
	void rejectsCompletingNonProcessingEvent() {
		NotificationEvent pending = NotificationEvent.pending(7L, ADMIN_LINK_PATH, CREATED_AT);

		assertThatThrownBy(() -> pending.processed(CREATED_AT))
			.isInstanceOf(NotificationException.class);
	}

	@Test
	@DisplayName("PROCESSING 상태에서 실패 처리하면 dead 여부에 따라 FAILED 또는 DEAD로 전이한다")
	void failsProcessingEvent() {
		NotificationEvent processing = NotificationEvent.pending(7L, ADMIN_LINK_PATH, CREATED_AT)
			.claimed("worker-a", CLAIMED_AT, LEASE_EXPIRES_AT);

		NotificationEvent retryable = processing.failed(CLAIMED_AT.plusSeconds(60), false);
		NotificationEvent dead = processing.failed(CLAIMED_AT.plusSeconds(60), true);

		assertThat(retryable.status()).isEqualTo(NotificationEventStatus.FAILED);
		assertThat(retryable.leaseOwner()).isNull();
		assertThat(dead.status()).isEqualTo(NotificationEventStatus.DEAD);
	}
}
