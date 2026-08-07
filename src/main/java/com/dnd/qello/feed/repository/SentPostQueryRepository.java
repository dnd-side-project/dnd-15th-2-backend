package com.dnd.qello.feed.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.feed.view.AnswerCard;
import com.dnd.qello.feed.view.SentPostCard;
import com.dnd.qello.feed.view.SentPostDetail;
import com.dnd.qello.feed.view.SentPostFilter;

public interface SentPostQueryRepository {

	/** 만료된 질문글이 계속 쌓이므로 커서 페이징이 필요하다. cursor가 null이면 첫 페이지다. */
	List<SentPostCard> findSentPosts(long senderId, SentPostFilter filter, SentPostCursor cursor, int limit, Instant at);

	/** 소유권을 쿼리 조건에 포함한다. 남의 질문글이면 빈 결과다. */
	Optional<SentPostDetail> findSentPostDetail(long senderId, long postId);

	/**
	 * 질문자만 조회할 수 있다. senderId 검증을 쿼리 조건에 포함하며 조회 후 비교하지 않는다.
	 * 근거: 답변은 질문을 보낸 사람에게만 도달한다(제품 규칙). 전용 ADR 문서는 아직 없다.
	 */
	List<AnswerCard> findAnswers(long senderId, long postId, AnswerCursor cursor, int limit);

	/** (submitted_at, id) 내림차순 커서. direction_post_sender_idx를 탄다. */
	record SentPostCursor(Instant submittedAt, long postId) { }

	/** (published_at, id) 내림차순 커서. answer_recipient_idx를 탄다. */
	record AnswerCursor(Instant publishedAt, long answerId) { }
}
