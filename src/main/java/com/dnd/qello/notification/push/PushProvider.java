package com.dnd.qello.notification.push;

public interface PushProvider {

	PushProviderResult send(PushSendCommand command);

}
