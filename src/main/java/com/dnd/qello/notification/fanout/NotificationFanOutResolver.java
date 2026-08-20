package com.dnd.qello.notification.fanout;

import java.util.Set;

import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;

interface NotificationFanOutResolver {

	Set<OutboxEventType> eventTypes();

	FanOutInstruction resolve(OutboxEvent event);
}
