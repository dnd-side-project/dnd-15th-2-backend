package com.dnd.qello.feed.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.feed.view.DirectionChip;
import com.dnd.qello.feed.view.InboxCard;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.view.InboxDetail;

public interface InboxQueryRepository {

	/**
	 * category에 맞는 수신 질문글만 반환한다. 두 카테고리 모두 만료 전(expires_at > at)
	 * 항목만 담는다 — 답변한 질문글도 만료되면 목록에서 빠진다. 수신 상한 이하이므로
	 * 페이징하지 않는다.
	 * directionSegmentKey가 null이거나 공백이면 방향 필터를 걸지 않는다 — 이때는
	 * 조회 시점의 ACTIVE direction_scheme 상태와 무관하게 결과가 정해진다. 구현체는
	 * 공백 문자열을 실제 구간 키로 바인딩해서는 안 된다.
	 */
	List<InboxCard> findInbox(long recipientId, InboxCategory category, String directionSegmentKey, Instant at);

	/**
	 * category 스코프 전체를 방향별로 집계한다. 방향 필터를 받지 않는다 — 칩 하나를
	 * 선택해도 나머지 방향으로 갈아탈 수 있어야 하기 때문이다. count가 0인 방향은
	 * 결과에 없다.
	 */
	List<DirectionChip> countByDirection(long recipientId, InboxCategory category, Instant at);

	/** 소유권을 쿼리 조건에 포함한다. 남의 항목이면 빈 결과다. */
	Optional<InboxDetail> findDetail(long recipientId, long postRecipientId);
}
