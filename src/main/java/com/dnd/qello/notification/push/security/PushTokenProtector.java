package com.dnd.qello.notification.push.security;

public interface PushTokenProtector {

	ProtectedPushToken protect(PushToken token);

	PushToken decrypt(byte[] envelope);

	String fingerprint(PushToken token);
}
