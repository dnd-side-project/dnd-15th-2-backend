package com.dnd.qello.notification.web;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.service.NotificationInboxService;
import com.dnd.qello.notification.service.NotificationPreferenceService;
import com.dnd.qello.notification.service.PushDeviceService;
import com.dnd.qello.notification.web.request.PushDeviceRequest;
import com.dnd.qello.notification.web.request.UpdateNotificationPreferencesRequest;
import com.dnd.qello.notification.web.response.NotificationCardResponse;
import com.dnd.qello.notification.web.response.NotificationListingResponse;
import com.dnd.qello.notification.web.response.NotificationPreferenceResponse;
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
	private final NotificationPreferenceService preferenceService;
	private final PushDeviceService pushDeviceService;
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
	public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> preferences(Authentication authentication) {
		return ResponseEntity.ok(responseFactory.success(NotificationPreferenceResponse.from(
			preferenceService.findMine(AuthenticatedUserId.require(authentication)))));
	}

	@Override
	public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> replacePreferences(
		UpdateNotificationPreferencesRequest request, Authentication authentication) {
		return ResponseEntity.ok(responseFactory.success(NotificationPreferenceResponse.from(
			preferenceService.replaceMine(AuthenticatedUserId.require(authentication), requireRequest(request).toCommand()))));
	}

	@Override
	public ResponseEntity<Void> registerDevice(PushDeviceRequest request, Authentication authentication) {
		pushDeviceService.registerOrTransferDevice(
			AuthenticatedUserId.require(authentication),
			requirePushDeviceRequest(request).toCommand());
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<Void> revokeDevice(PushDeviceRequest request, Authentication authentication) {
		pushDeviceService.revokeOwnedDevice(
			AuthenticatedUserId.require(authentication),
			requirePushDeviceRequest(request).toCommand());
		return ResponseEntity.noContent().build();
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

	private UpdateNotificationPreferencesRequest requireRequest(UpdateNotificationPreferencesRequest request) {
		if (request == null) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_PREFERENCE,
				"request",
				"알림 설정 요청 본문은 필수입니다.");
		}
		return request;
	}

	private PushDeviceRequest requirePushDeviceRequest(PushDeviceRequest request) {
		if (request == null) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_PUSH_DEVICE_REQUEST,
				"request",
				"push device 요청 본문은 필수입니다.");
		}
		return request;
	}
}
