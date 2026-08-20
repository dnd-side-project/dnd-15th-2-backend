/**
 * Created at: 2026-08-20T15:30:00+09:00
 * Source scenario: TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ-UNIT-001, UNIT-003
 */
package com.dnd.qello.notification.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.domain.NotificationType;

class NotificationCardTest {

	private static final Instant NOW = Instant.parse("2026-08-20T06:00:00Z");

	@Test
	@DisplayName("AVAILABLE DIRECTION_POST 대상은 expiresAt을 허용한다")
	void allowsExpiresAtForAvailableDirectionPost() {
		NotificationCard card = card(NotificationTargetKind.DIRECTION_POST, 771L,
			NotificationTargetState.AVAILABLE, NOW.plusSeconds(3600));

		assertThat(card.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
	}

	@Test
	@DisplayName("ANSWER 대상에 expiresAt을 주면 거부한다")
	void rejectsExpiresAtForAnswerTarget() {
		assertThatThrownBy(() -> card(NotificationTargetKind.ANSWER, 5L,
			NotificationTargetState.AVAILABLE, NOW.plusSeconds(3600)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("state가 AVAILABLE이 아닌 DIRECTION_POST에 expiresAt을 주면 거부한다")
	void rejectsExpiresAtForNonAvailableState() {
		assertThatThrownBy(() -> card(NotificationTargetKind.DIRECTION_POST, 771L,
			NotificationTargetState.EXPIRED, NOW.plusSeconds(3600)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("NONE 대상에 id를 주면 거부한다")
	void rejectsIdForNoneTarget() {
		assertThatThrownBy(() -> new NotificationCard(
			1L, NotificationType.ANSWER_REACTED, NOW, null, true,
			NotificationTargetKind.NONE, 5L, null, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("NONE 대상에 state를 주면 거부한다")
	void rejectsStateForNoneTarget() {
		assertThatThrownBy(() -> new NotificationCard(
			1L, NotificationType.ANSWER_REACTED, NOW, null, true,
			NotificationTargetKind.NONE, null, NotificationTargetState.AVAILABLE, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("NONE 대상은 id와 state 없이 만들 수 있다")
	void allowsNoneTargetWithoutIdOrState() {
		NotificationCard card = new NotificationCard(
			1L, NotificationType.ANSWER_REACTED, NOW, null, true,
			NotificationTargetKind.NONE, null, null, null);

		assertThat(card.targetId()).isNull();
		assertThat(card.targetState()).isNull();
	}

	@Test
	@DisplayName("non-NONE 대상에 id가 없으면 거부한다")
	void rejectsMissingIdForNonNoneTarget() {
		assertThatThrownBy(() -> new NotificationCard(
			1L, NotificationType.DIRECTION_POST_RECEIVED, NOW, null, true,
			NotificationTargetKind.DIRECTION_POST, null, NotificationTargetState.AVAILABLE, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static NotificationCard card(
		NotificationTargetKind kind, long id, NotificationTargetState state, Instant expiresAt) {
		return new NotificationCard(
			1L, NotificationType.DIRECTION_POST_RECEIVED, NOW, null, true, kind, id, state, expiresAt);
	}
}
