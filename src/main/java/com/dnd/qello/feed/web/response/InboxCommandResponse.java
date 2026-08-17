package com.dnd.qello.feed.web.response;

import java.time.Instant;

import com.dnd.qello.direction.domain.PostRecipient;

/** 넘김 명령 결과. 서버가 관리하는 사용자·위치·저장소 정보는 포함하지 않는다. */
public record InboxCommandResponse(
	long postRecipientId,
	String status,
	Instant skipRequestedAt,
	Instant revertibleUntil
) {
	public static InboxCommandResponse from(PostRecipient recipient, Instant revertibleUntil) {
		return new InboxCommandResponse(
			recipient.getId(), recipient.getStatus().name(), recipient.getSkipRequestedAt(), revertibleUntil);
	}
}
