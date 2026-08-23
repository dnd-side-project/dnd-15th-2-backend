package com.dnd.qello.feed.web.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dnd.qello.direction.domain.PostRecipient;

/** 넘김 명령 결과. 서버가 관리하는 사용자·위치·저장소 정보는 포함하지 않는다. */
public record InboxCommandResponse(
	@Schema(description = "이 수신함 항목의 식별자") long postRecipientId,
	@Schema(description = "명령 적용 후의 수신 상태") String status,
	@Schema(description = "넘김을 요청한 시각. 넘김을 요청하지 않았거나 되돌렸으면 null입니다") Instant skipRequestedAt,
	@Schema(description = "이 넘김을 되돌릴 수 있는 마감 시각. skip 응답에만 채워지고 revertSkip 응답에서는 항상 null입니다") Instant revertibleUntil
) {
	public static InboxCommandResponse from(PostRecipient recipient, Instant revertibleUntil) {
		return new InboxCommandResponse(
			recipient.getId(), recipient.getStatus().name(), recipient.getSkipRequestedAt(), revertibleUntil);
	}
}
