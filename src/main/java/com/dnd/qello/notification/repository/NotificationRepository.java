package com.dnd.qello.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.PushDevice;

public interface NotificationRepository {

	Notification save(Notification notification);

	Notification saveIfAbsent(Notification notification);

	Optional<Notification> findById(long id);

	boolean update(Notification notification);

	/**
	 * recipientId 소유이고 {@code at} 이전(포함)에 생성된 UNREAD·READ 줄을 UPDATE 한 번으로 모두
	 * DISMISSED로 전이하고 그 건수를 반환한다. read_at은 바꾸지 않는다. 이미 DISMISSED거나 REVOKED인 줄은 건드리지
	 * 않으므로 같은 {@code at}으로 다시 호출하면 0건이다 — 멱등.
	 */
	int dismissAll(long recipientId, Instant at);

	/**
	 * 그 답변을 가리키던 알림을 모두 REVOKED로 전이한다(#155 전역 숨김). 이미 REVOKED인 행은 건드리지 않는다 — 멱등.
	 */
	int revokeByAnswerId(long answerId);

	/**
	 * 그 답변을 가리키던 알림의 미발송(PENDING·FAILED) push 전달을 모두 CANCELLED로 전이한다(#155 전역 숨김).
	 */
	int cancelDeliveriesByAnswerId(long answerId);

	NotificationDelivery saveDelivery(NotificationDelivery delivery);

	NotificationDelivery saveDeliveryIfAbsent(NotificationDelivery delivery);

	/** 기존 fan-out 회귀 경로와의 호환 API. 신규 push dispatch는 group claim API를 사용한다. */
	Optional<NotificationDelivery> claimDelivery(long id, Instant at);

	/** 기존 fan-out 회귀 경로와의 호환 API. 신규 push dispatch는 group terminal API를 사용한다. */
	boolean updateDelivery(NotificationDelivery delivery);

	PushDevice saveDevice(PushDevice device);

	PushDevice registerOrTransferDevice(
			long userId, String platform, byte[] tokenCiphertext, String tokenFingerprint, Instant at);

	int revokeOwnedDevice(long userId, String platform, String tokenFingerprint, Instant at);

	/**
	 * 사용자의 ACTIVE 푸시 기기를 모두 해지하고 PENDING·FAILED 전달을 취소한다(#337 탈퇴). 해지한 기기 수를 돌려준다.
	 */
	int revokeAllDevicesByUserId(long userId, Instant at);

	List<Long> findActiveDeviceIdsByUserId(long userId);
}
