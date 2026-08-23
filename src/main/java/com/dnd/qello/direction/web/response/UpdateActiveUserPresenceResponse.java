package com.dnd.qello.direction.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateActiveUserPresenceResponse(
	@Schema(description = "보낸 위치가 실제로 저장됐는지 여부. 더 오래된 위치라 반영하지 않았으면 false입니다") boolean applied
) {
}
