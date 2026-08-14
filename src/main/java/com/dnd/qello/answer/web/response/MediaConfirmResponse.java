package com.dnd.qello.answer.web.response;

import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;

/** 업로드 확인 결과. 저장소 경로와 URL은 포함하지 않는다. */
public record MediaConfirmResponse(long mediaId, String status) {
	public static MediaConfirmResponse from(MediaAsset asset) {
		Long mediaId = asset.getId();
		if (mediaId == null || mediaId <= 0) {
			throw new AnswerException(AnswerErrorCode.INVALID_ID, "mediaId", "업로드 확인 결과의 mediaId가 유효하지 않습니다");
		}
		return new MediaConfirmResponse(mediaId, asset.getStatus().name());
	}
}
