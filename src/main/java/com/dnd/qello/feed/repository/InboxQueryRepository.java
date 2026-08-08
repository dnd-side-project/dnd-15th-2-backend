package com.dnd.qello.feed.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.feed.view.InboxCard;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.view.InboxDetail;

public interface InboxQueryRepository {

	/**
	 * category에 맞는 수신 질문글만 반환한다. 두 카테고리 모두 만료 전(expires_at > at)
	 * 항목만 담는다 — 답변한 질문글도 만료되면 목록에서 빠진다. 수신 상한 이하이므로
	 * 페이징하지 않는다.
	 */
	List<InboxCard> findInbox(long recipientId, InboxCategory category, Instant at);

	/** 소유권을 쿼리 조건에 포함한다. 남의 항목이면 빈 결과다. */
	Optional<InboxDetail> findDetail(long recipientId, long postRecipientId);
}
