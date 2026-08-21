/**
 * Created at: 2026-08-20T17:05:00+09:00
 * Source scenario: TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ-UNIT-018
 */
package com.dnd.qello.notification.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import com.dnd.qello.common.web.MockMvcTestSupport;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.service.NotificationInboxService;
import com.dnd.qello.notification.service.NotificationPreferenceService;
import com.dnd.qello.notification.view.NotificationCard;
import com.dnd.qello.notification.view.NotificationListing;
import com.dnd.qello.notification.view.NotificationTargetDecision;
import com.dnd.qello.notification.view.NotificationTargetKind;
import com.dnd.qello.notification.view.NotificationTargetState;
import com.dnd.qello.notification.view.UnreadSignal;

@ExtendWith(MockitoExtension.class)
class NotificationApiMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-08-20T06:00:00Z");
	private static final long USER_ID = 11L;
	private static final long NOTIFICATION_ID = 1042L;

	@Mock
	private NotificationInboxService inboxService;
	@Mock
	private NotificationPreferenceService preferenceService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		mockMvc = MockMvcTestSupport.standalone(
			new NotificationController(inboxService, preferenceService, new ApiResponseFactory(clock)), true, USER_ID, clock);
	}

	@Test
	@DisplayName("목록 조회는 200과 카드·nextCursor를 반환한다")
	void listReturnsCardsAndNextCursor() throws Exception {
		NotificationCard card = new NotificationCard(NOTIFICATION_ID, NotificationType.DIRECTION_POST_RECEIVED,
			NOW, null, true, NotificationTargetKind.DIRECTION_POST, 771L, NotificationTargetState.AVAILABLE,
			NOW.plusSeconds(3600));
		when(inboxService.list(USER_ID, null, null, 20))
			.thenReturn(new NotificationListing(List.of(card), new NotificationListing.Cursor(NOW, NOTIFICATION_ID)));

		mockMvc.perform(get("/api/v1/notifications"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.notifications[0].notificationId").value(NOTIFICATION_ID))
			.andExpect(jsonPath("$.data.notifications[0].target.kind").value("DIRECTION_POST"))
			.andExpect(jsonPath("$.data.nextCursor.notificationId").value(NOTIFICATION_ID));
	}

	@Test
	@DisplayName("limit이 범위를 벗어나면 NOT-VAL-006으로 400을 반환한다")
	void listRejectsOutOfRangeLimit() throws Exception {
		when(inboxService.list(USER_ID, null, null, 51))
			.thenThrow(new NotificationException(NotificationErrorCode.INVALID_LIMIT, "limit"));

		mockMvc.perform(get("/api/v1/notifications").param("limit", "51"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-VAL-006"));
	}

	@Test
	@DisplayName("cursor를 한쪽만 지정하면 NOT-VAL-007로 400을 반환한다")
	void listRejectsIncompleteCursor() throws Exception {
		when(inboxService.list(USER_ID, NOW, null, 20))
			.thenThrow(new NotificationException(NotificationErrorCode.INVALID_CURSOR, "cursor"));

		mockMvc.perform(get("/api/v1/notifications").param("cursorCreatedAt", NOW.toString()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-VAL-007"));
	}

	@Test
	@DisplayName("미읽음 신호 조회는 200과 hasUnseen·unreadCount·seenAt을 반환한다")
	void unreadCountReturnsSignal() throws Exception {
		when(inboxService.unreadSignal(USER_ID)).thenReturn(new UnreadSignal(true, 7L, NOW));

		mockMvc.perform(get("/api/v1/notifications/unread-count"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.hasUnseen").value(true))
			.andExpect(jsonPath("$.data.unreadCount").value(7));
	}

	@Test
	@DisplayName("열람 기준선 전진은 200과 seenAt을 반환한다")
	void markSeenReturnsSeenAt() throws Exception {
		when(inboxService.markSeen(USER_ID)).thenReturn(NOW);

		mockMvc.perform(put("/api/v1/notifications/seen"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.seenAt").isString());
	}

	@Test
	@DisplayName("REVOKED 알림 읽음 처리는 NOT-DOM-003으로 409를 반환한다")
	void markReadRejectsRevokedNotificationWith409() throws Exception {
		when(inboxService.markRead(USER_ID, NOTIFICATION_ID))
			.thenThrow(new NotificationException(NotificationErrorCode.INVALID_NOTIFICATION_STATUS, "status"));

		mockMvc.perform(put("/api/v1/notifications/{notificationId}/read", NOTIFICATION_ID))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-DOM-003"));
	}

	@Test
	@DisplayName("남의 알림이나 존재하지 않는 알림은 markRead·target 모두 NOT-DOM-004로 404를 반환한다")
	void markReadAndTargetReturn404ForMissingOrUnownedNotification() throws Exception {
		when(inboxService.markRead(USER_ID, NOTIFICATION_ID))
			.thenThrow(new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
		when(inboxService.target(USER_ID, NOTIFICATION_ID))
			.thenThrow(new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

		mockMvc.perform(put("/api/v1/notifications/{notificationId}/read", NOTIFICATION_ID))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-DOM-004"));
		mockMvc.perform(get("/api/v1/notifications/{notificationId}/target", NOTIFICATION_ID))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-DOM-004"));
	}

	@Test
	@DisplayName("계정 없음은 NOT-APP-001로 404, 자격 없음은 NOT-APP-002로 403을 반환한다")
	void accountGateErrorsMapToNotApp001And002() throws Exception {
		when(inboxService.unreadSignal(USER_ID))
			.thenThrow(new NotificationException(NotificationErrorCode.ACCOUNT_NOT_FOUND));

		mockMvc.perform(get("/api/v1/notifications/unread-count"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-APP-001"));
	}

	@Test
	@DisplayName("진입 판정은 200과 navigable·reason·target·fallback을 반환한다")
	void targetReturnsDecision() throws Exception {
		when(inboxService.target(USER_ID, NOTIFICATION_ID)).thenReturn(new NotificationTargetDecision(
			NotificationTargetKind.DIRECTION_POST, 771L, NotificationTargetState.EXPIRED));

		mockMvc.perform(get("/api/v1/notifications/{notificationId}/target", NOTIFICATION_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.navigable").value(false))
			.andExpect(jsonPath("$.data.reason").value("EXPIRED"))
			.andExpect(jsonPath("$.data.fallback").value("INBOX"));
	}
}
