package com.dnd.qello.answer.domain;

import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;

public record MediaAttachment(long mediaId, long ownerId, Long postId, Long answerId, int displayOrder) {

	public MediaAttachment {
		if (mediaId <= 0 || ownerId <= 0 || displayOrder < 0) {
			throw new AnswerException(AnswerErrorCode.INVALID_ID, null, "미디어 값이 유효하지 않습니다");
		}
		if ((postId == null ? 0 : 1) + (answerId == null ? 0 : 1) != 1) {
			throw new AnswerException(
				AnswerErrorCode.INVALID_MEDIA_TARGET, null, "미디어 대상은 정확히 하나여야 합니다");
		}
		if (postId != null && postId <= 0 || answerId != null && answerId <= 0) {
			throw new AnswerException(
				AnswerErrorCode.INVALID_MEDIA_TARGET, null, "미디어 대상 ID가 유효하지 않습니다");
		}
	}
}
