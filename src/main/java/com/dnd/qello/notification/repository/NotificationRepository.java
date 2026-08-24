package com.dnd.qello.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.push.ClaimedPushDelivery;
import com.dnd.qello.notification.push.PushDeliveryTerminalResult;

public interface NotificationRepository {

	Notification save(Notification notification);

	Notification saveIfAbsent(Notification notification);

	Optional<Notification> findById(long id);

	boolean update(Notification notification);

	/** 그 답변을 가리키던 알림을 모두 REVOKED로 전이한다(#155 전역 숨김). 이미 REVOKED인 행은 건드리지 않는다 — 멱등. */
	int revokeByAnswerId(long answerId);

	/** 그 답변을 가리키던 알림의 미발송(PENDING·FAILED) push 전달을 모두 CANCELLED로 전이한다(#155 전역 숨김). */
	int cancelDeliveriesByAnswerId(long answerId);

	NotificationDelivery saveDelivery(NotificationDelivery delivery);

	NotificationDelivery saveDeliveryIfAbsent(NotificationDelivery delivery);

	List<ClaimedPushDelivery> claimDueDeliveries(int batchSize, Instant now, Instant leaseUntil);

	boolean completeClaim(long deliveryId, int generation, PushDeliveryTerminalResult result, Instant at);

	/** 기존 fan-out 회귀 경로와의 호환 API. 신규 push dispatch는 fenced batch API를 사용한다. */
	Optional<NotificationDelivery> claimDelivery(long id, Instant at);

	/** 기존 fan-out 회귀 경로와의 호환 API. 신규 push dispatch는 fenced terminal API를 사용한다. */
	boolean updateDelivery(NotificationDelivery delivery);

	PushDevice saveDevice(PushDevice device);

	PushDevice registerOrTransferDevice(
		long userId, String platform, byte[] tokenCiphertext, String tokenFingerprint, Instant at);

	int revokeOwnedDevice(long userId, String platform, String tokenFingerprint, Instant at);

	int cancelUndeliveredForDevice(long pushDeviceId, String reason, Instant at);

	List<Long> findActiveDeviceIdsByUserId(long userId);
}
