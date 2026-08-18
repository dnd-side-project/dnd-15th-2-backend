package com.dnd.qello.account.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 프로필 이미지 변경 요청. 대상 계정은 본문이 아니라 인증 주체에서 결정한다. */
public record ProfileImageChangeRequest(
	@NotNull(message = "mediaId는 필수입니다")
	@Positive(message = "mediaId는 양수여야 합니다")
	Long mediaId
) {
}
