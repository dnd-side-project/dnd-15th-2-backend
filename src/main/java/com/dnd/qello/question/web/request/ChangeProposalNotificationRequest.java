package com.dnd.qello.question.web.request;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "질문 제안 1건의 검토 결과 알림을 끄거나 켤 때 보내는 입력입니다.")
public record ChangeProposalNotificationRequest(
		@NotNull(message = "muted는 필수입니다") @Schema(description = "true이면 이 제안의 검토 결과 push를 보내지 않습니다.", example = "true", requiredMode = Schema.RequiredMode.REQUIRED) Boolean muted) {
}
