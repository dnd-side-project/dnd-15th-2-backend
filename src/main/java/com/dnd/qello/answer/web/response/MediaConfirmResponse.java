package com.dnd.qello.answer.web.response;

import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;

import io.swagger.v3.oas.annotations.media.Schema;

/** 업로드 확인 결과. 저장소 경로와 URL은 포함하지 않는다. */
public record MediaConfirmResponse(
	@Schema(description = "확인한 이미지 식별자") long mediaId,
	@Schema(description = "확인 뒤의 이미지 상태") String status
) {
	public static MediaConfirmResponse from(MediaAsset asset) {
		Long mediaId = asset.getId();
		if (mediaId == null || mediaId <= 0) {
			throw new AnswerException(AnswerErrorCode.INVALID_ID, "mediaId", "업로드 확인 결과의 mediaId가 유효하지 않습니다");
		}
		return new MediaConfirmResponse(mediaId, asset.getStatus().name());
	}
}
