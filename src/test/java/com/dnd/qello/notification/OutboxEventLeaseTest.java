package com.dnd.qello.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxStatus;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

/**
 * Created at: 2026-08-11T20:04:06+09:00
 * Source scenario: TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-UNIT-004 through UNIT-006
 */
class OutboxEventLeaseTest {

	private static final Instant CREATED_AT = Instant.parse("2026-08-11T11:00:00Z");
	private static final Instant CLAIMED_AT = CREATED_AT.plusSeconds(10);
	private static final Instant LEASE_EXPIRES_AT = CLAIMED_AT.plusSeconds(30);

	@Test
	@DisplayName("방향글 매칭 이벤트는 최초 라운드 1을 갖고 다른 이벤트는 라운드를 갖지 않는다")
	void assignsMatchingRoundOnlyToDirectionMatchingEvent() {
		OutboxEvent matching = OutboxEvent.pending(OutboxAggregateType.DIRECTION_POST, 7L,
			OutboxEventType.RECIPIENT_MATCH_REQUESTED, "match:7:1", "{\"postId\":7}", CREATED_AT);
		OutboxEvent answer = OutboxEvent.pending(OutboxAggregateType.ANSWER, 7L,
			OutboxEventType.ANSWER_PUBLISHED, "answer:7", "{\"answerId\":7}", CREATED_AT);

		assertThat(matching.matchRound()).isEqualTo(1);
		assertThat(answer.matchRound()).isNull();
	}

	@Test
	@DisplayName("새 lease claim은 processing 상태와 시도 횟수 및 fencing generation을 함께 증가시킨다")
	void claimsWithLeaseFence() {
		OutboxEvent event = OutboxEvent.pending(OutboxAggregateType.DIRECTION_POST, 7L,
			OutboxEventType.RECIPIENT_MATCH_REQUESTED, "match:7:1", "{\"postId\":7}", CREATED_AT);

		OutboxEvent claimed = event.claimed("worker-a", CLAIMED_AT, LEASE_EXPIRES_AT);

		assertThat(claimed.status()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(claimed.attemptCount()).isEqualTo(1);
		assertThat(claimed.leaseOwner()).isEqualTo("worker-a");
		assertThat(claimed.leaseExpiresAt()).isEqualTo(LEASE_EXPIRES_AT);
		assertThat(claimed.leaseGeneration()).isEqualTo(1);
	}

	@Test
	@DisplayName("만료된 processing lease만 새 worker가 generation을 올려 회수할 수 있다")
	void reclaimsOnlyExpiredLease() {
		OutboxEvent expired = new OutboxEvent(11L, OutboxAggregateType.DIRECTION_POST, 7L,
			OutboxEventType.RECIPIENT_MATCH_REQUESTED, "match:7:1", "{\"postId\":7}",
			OutboxStatus.PROCESSING, 1, CLAIMED_AT, CREATED_AT, null, 1,
			"worker-a", CLAIMED_AT.plusSeconds(1), 4);

		OutboxEvent reclaimed = expired.claimed("worker-b", CLAIMED_AT.plusSeconds(2),
			CLAIMED_AT.plusSeconds(40));

		assertThat(reclaimed.leaseOwner()).isEqualTo("worker-b");
		assertThat(reclaimed.leaseGeneration()).isEqualTo(5);
		assertThat(reclaimed.attemptCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("유효한 processing lease의 재점유와 terminal 상태의 재claim은 거절한다")
	void rejectsUnsafeClaims() {
		OutboxEvent processing = OutboxEvent.pending(OutboxAggregateType.ANSWER, 7L,
			OutboxEventType.ANSWER_PUBLISHED, "answer:7", "{\"answerId\":7}", CREATED_AT)
			.claimed("worker-a", CLAIMED_AT, LEASE_EXPIRES_AT);

		assertThatThrownBy(() -> processing.claimed("worker-b", CLAIMED_AT.plusSeconds(1),
			CLAIMED_AT.plusSeconds(40)))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_NOTIFICATION_STATUS);

		OutboxEvent processed = processing.processed(CLAIMED_AT.plusSeconds(2));
		assertThatThrownBy(() -> processed.claimed("worker-b", CLAIMED_AT.plusSeconds(3),
			CLAIMED_AT.plusSeconds(40)))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_NOTIFICATION_STATUS);
	}

	@Test
	@DisplayName("완료와 실패 전환은 lease 정보를 해제하고 generation을 보존한다")
	void clearsLeaseOnTerminalTransition() {
		OutboxEvent processing = OutboxEvent.pending(OutboxAggregateType.ANSWER, 7L,
			OutboxEventType.ANSWER_PUBLISHED, "answer:7", "{\"answerId\":7}", CREATED_AT)
			.claimed("worker-a", CLAIMED_AT, LEASE_EXPIRES_AT);

		OutboxEvent processed = processing.processed(CLAIMED_AT.plusSeconds(1));
		OutboxEvent failed = processing.failed(CLAIMED_AT.plusSeconds(60), false);

		assertThat(processed.status()).isEqualTo(OutboxStatus.PROCESSED);
		assertThat(processed.leaseOwner()).isNull();
		assertThat(processed.leaseExpiresAt()).isNull();
		assertThat(processed.leaseGeneration()).isEqualTo(1);
		assertThat(failed.status()).isEqualTo(OutboxStatus.FAILED);
		assertThat(failed.leaseOwner()).isNull();
		assertThat(failed.leaseExpiresAt()).isNull();
	}

	@Test
	@DisplayName("Outbox payload는 JSON object만 허용하고 배열은 거절한다")
	void acceptsOnlyJsonObjectPayload() {
		assertThat(OutboxEvent.pending(OutboxAggregateType.DIRECTION_POST, 7L,
			OutboxEventType.RECIPIENT_MATCH_REQUESTED, "match:7:1", "{\"postId\":7}", CREATED_AT).payload())
			.isEqualTo("{\"postId\":7}");
		assertThatThrownBy(() -> OutboxEvent.pending(OutboxAggregateType.DIRECTION_POST, 7L,
			OutboxEventType.RECIPIENT_MATCH_REQUESTED, "bad", "[]", CREATED_AT))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_PAYLOAD);
	}
}
