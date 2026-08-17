package com.dnd.qello.answer.web.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 답변 제출에 필요한 사용자 의도만 표현한다(GitHub #125). 작성자, 지역·방위·거리와
 * 시각은 인증·PostRecipient 스냅샷·서버 Clock에서 결정하므로 요청 본문에 두지 않는다.
 */
public record SubmitAnswerRequest(
	@NotBlank(message = "bodyText는 필수입니다")
	@Schema(description = "답변 본문. 정규화 후 최대 300 Unicode code points", example = "저도 여기 자주 와요!")
	String bodyText,

	@Valid
	@Size(max = 1, message = "mediaIds는 최대 1개까지 허용됩니다")
	@Schema(description = "첨부할 READY 이미지 식별자. 0개 또는 1개")
	List<@NotNull @Positive Long> mediaIds
) {
	public SubmitAnswerRequest {
		mediaIds = mediaIds == null ? List.of() : List.copyOf(mediaIds);
	}
}
