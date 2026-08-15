package com.dnd.qello.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationPreference;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.PushDevice;

public interface NotificationRepository {

	Notification save(Notification notification);

	Notification saveIfAbsent(Notification notification);

	Optional<Notification> findById(long id);

	boolean update(Notification notification);

	NotificationDelivery saveDelivery(NotificationDelivery delivery);

	NotificationDelivery saveDeliveryIfAbsent(NotificationDelivery delivery);

	Optional<NotificationDelivery> claimDelivery(long id, Instant at);

	boolean updateDelivery(NotificationDelivery delivery);

	PushDevice saveDevice(PushDevice device);

	List<Long> findActiveDeviceIdsByUserId(long userId);

	NotificationPreference savePreference(NotificationPreference preference);

	boolean isPreferenceEnabled(long userId, NotificationType notificationType);
}
