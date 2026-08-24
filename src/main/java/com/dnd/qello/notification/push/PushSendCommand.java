package com.dnd.qello.notification.push;

import com.dnd.qello.notification.push.security.PushToken;

/** provider 경계에는 복호화된 token과 allowlisted payload만 전달한다. */
public record PushSendCommand(PushToken token, PushPayload payload) {

	public PushSendCommand {
		if (token == null || payload == null) {
			throw new IllegalArgumentException("push send command는 token과 payload가 모두 필요합니다");
		}
	}

}
