package com.dnd.qello.answer.domain;

public record MediaAttachment(long mediaId, long ownerId, Long postId, Long answerId, int displayOrder) {

	public MediaAttachment {
		if (mediaId <= 0 || ownerId <= 0 || displayOrder < 0) throw new IllegalArgumentException("미디어 값이 유효하지 않습니다");
		if ((postId == null ? 0 : 1) + (answerId == null ? 0 : 1) != 1) throw new IllegalArgumentException("미디어 대상은 정확히 하나여야 합니다");
		if (postId != null && postId <= 0 || answerId != null && answerId <= 0) throw new IllegalArgumentException("미디어 대상 ID가 유효하지 않습니다");
	}
}
