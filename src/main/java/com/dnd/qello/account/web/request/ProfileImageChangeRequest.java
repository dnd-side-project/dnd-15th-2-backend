package com.dnd.qello.account.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 프로필 이미지 변경 요청. 대상 계정은 본문이 아니라 인증 주체에서 결정한다. */
public record ProfileImageChangeRequest(
	@NotNull(message = "mediaId는 필수입니다")
	@Positive(message = "mediaId는 양수여야 합니다")
	@Schema(description = "프로필 이미지로 지정할 미디어 식별자.", example = "123")
	Long mediaId
) {
}
