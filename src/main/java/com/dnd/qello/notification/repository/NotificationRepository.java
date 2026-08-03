package com.dnd.qello.notification.repository;

import java.time.Instant;
import java.util.Optional;

import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationPreference;
import com.dnd.qello.notification.domain.PushDevice;

public interface NotificationRepository {

	Notification save(Notification notification);

	Optional<Notification> findById(long id);

	boolean update(Notification notification);

	NotificationDelivery saveDelivery(NotificationDelivery delivery);

	Optional<NotificationDelivery> claimDelivery(long id, Instant at);

	boolean updateDelivery(NotificationDelivery delivery);

	PushDevice saveDevice(PushDevice device);

	NotificationPreference savePreference(NotificationPreference preference);
}
