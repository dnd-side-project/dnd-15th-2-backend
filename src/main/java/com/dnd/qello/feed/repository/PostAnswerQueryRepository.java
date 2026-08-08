package com.dnd.qello.feed.repository;

import java.time.Instant;
import java.util.List;

import com.dnd.qello.feed.view.AnswerCard;

/**
 * 답변 열람 자격 판정과 답변 조회를 함께 소유한다.
 * 자격 판정을 이 한 곳에만 두는 이유는 AnswerReactionService의 공감 자격과 답변 목록
 * 조회 자격이 같은 규칙(질문자이거나 그 질문글의 수신 자격자)을 공유하기 때문이다 —
 * 두 곳에 각자 구현하면 갈라진다.
 */
public interface PostAnswerQueryRepository {

	/**
	 * 질문글 작성자이거나, 시점 기준으로 아직 열람 자격을 유지한 수신자인지 판정한다.
	 * 넘김·만료로 인한 자격 상실은 상태가 아니라 시각으로 판단한다 — SKIP_PENDING 확정
	 * 워커와 만료 전이 배치가 아직 없어 status만으로는 이미 만료된 수신자를 걸러낼 수
	 * 없다(V8 migration이 "수신자 집합 소속"까지만 DB로 강제하고 "지금 볼 수 있는가"는
	 * 조회 계층으로 넘긴 이유).
	 * ANSWERED 수신자는 만료 후에도 자격을 유지한다 — 답변한 사람은 자격을 잃지 않는다.
	 */
	boolean canViewAnswers(long viewerId, long postId, Instant at);

	/**
	 * 자격이 없는 뷰어는 예외가 아니라 빈 목록을 받는다. 질문글 존재 여부를 흘리지
	 * 않기 위함이다(옛 SentPostQueryRepository.findAnswers의 근거를 승계).
	 */
	List<AnswerCard> findAnswers(long viewerId, long postId, AnswerCursor cursor, int limit, Instant at);

	/** (published_at, id) 내림차순 커서. answer_recipient_idx를 탄다. */
	record AnswerCursor(Instant publishedAt, long answerId) { }
}
