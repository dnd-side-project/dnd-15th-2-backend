package com.dnd.qello.notification.web;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.notification.service.NotificationInboxService;
import com.dnd.qello.notification.web.response.NotificationCardResponse;
import com.dnd.qello.notification.web.response.NotificationListingResponse;
import com.dnd.qello.notification.web.response.NotificationSeenResponse;
import com.dnd.qello.notification.web.response.NotificationTargetResponse;
import com.dnd.qello.notification.web.response.UnreadSignalResponse;

import lombok.RequiredArgsConstructor;

/** 인증 subject만 application 경계에 전달하는 알림함 HTTP 어댑터다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class NotificationController implements NotificationApiSpec {

	private final NotificationInboxService inboxService;
	private final ApiResponseFactory responseFactory;

	@Override
	public ResponseEntity<ApiResponse<NotificationListingResponse>> list(
		Instant cursorCreatedAt, Long cursorNotificationId, int limit, Authentication authentication) {
		long recipientId = AuthenticatedUserId.require(authentication);
		return ResponseEntity.ok(responseFactory.success(NotificationListingResponse.from(
			inboxService.list(recipientId, cursorCreatedAt, cursorNotificationId, limit))));
	}

	@Override
	public ResponseEntity<ApiResponse<UnreadSignalResponse>> unreadCount(Authentication authentication) {
		long recipientId = AuthenticatedUserId.require(authentication);
		return ResponseEntity.ok(responseFactory.success(
			UnreadSignalResponse.from(inboxService.unreadSignal(recipientId))));
	}

	@Override
	public ResponseEntity<ApiResponse<NotificationSeenResponse>> markSeen(Authentication authentication) {
		long recipientId = AuthenticatedUserId.require(authentication);
		return ResponseEntity.ok(responseFactory.success(
			NotificationSeenResponse.from(inboxService.markSeen(recipientId))));
	}

	@Override
	public ResponseEntity<ApiResponse<NotificationCardResponse>> markRead(
		long notificationId, Authentication authentication) {
		long recipientId = AuthenticatedUserId.require(authentication);
		return ResponseEntity.ok(responseFactory.success(
			NotificationCardResponse.from(inboxService.markRead(recipientId, notificationId))));
	}

	@Override
	public ResponseEntity<ApiResponse<NotificationTargetResponse>> target(
		long notificationId, Authentication authentication) {
		long recipientId = AuthenticatedUserId.require(authentication);
		return ResponseEntity.ok(responseFactory.success(
			NotificationTargetResponse.from(inboxService.target(recipientId, notificationId))));
	}
}
