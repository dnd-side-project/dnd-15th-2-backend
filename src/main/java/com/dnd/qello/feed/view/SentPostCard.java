package com.dnd.qello.feed.view;

import java.time.Instant;
import java.util.List;

/**
 * `내가 쓴 질문` 카드 1장.
 * 질문자 전용 화면이므로 답변 수와 공감 수를 모두 노출한다.
 * unreadAnswerCount는 direction_post.answers_read_at 이후 공개된 답변 수다.
 */
public record SentPostCard(
	long postId,
	String questionText,
	String bodyText,
	List<Long> mediaIds,
	String coarseRegionCode,
	Instant submittedAt,
	Instant expiresAt,
	long answerCount,
	long reactionCount,
	long unreadAnswerCount
) {
	public SentPostCard { mediaIds = List.copyOf(mediaIds); }
}
