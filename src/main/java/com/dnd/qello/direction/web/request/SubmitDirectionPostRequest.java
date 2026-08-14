package com.dnd.qello.direction.web.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 방향 질문글 제출에 필요한 사용자 의도만 표현한다. 발신자, 위치, 거리와 시각은
 * 인증·정책·서버 Clock에서 결정하므로 요청 본문에 두지 않는다.
 */
public record SubmitDirectionPostRequest(
	@NotNull(message = "approvedQuestionId는 필수입니다")
	@Positive(message = "approvedQuestionId는 양수여야 합니다")
	@Schema(description = "승인된 질문 식별자", example = "101")
	Long approvedQuestionId,

	@NotNull(message = "schemeId는 필수입니다")
	@Positive(message = "schemeId는 양수여야 합니다")
	@Schema(description = "방향 구획 체계 식별자", example = "7")
	Long schemeId,

	@NotBlank(message = "segmentKey는 필수입니다")
	@Schema(description = "선택한 방향 구획 키", example = "N")
	String segmentKey,

	@Schema(description = "선택적 본문. 정규화 후 최대 300 Unicode code points")
	String bodyText,

	@Valid
	@Size(max = 1, message = "mediaIds는 최대 1개까지 허용됩니다")
	@Schema(description = "첨부할 READY 이미지 식별자. 0개 또는 1개")
	List<@NotNull @Positive Long> mediaIds
) {
	public SubmitDirectionPostRequest {
		mediaIds = mediaIds == null ? List.of() : List.copyOf(mediaIds);
	}
}
