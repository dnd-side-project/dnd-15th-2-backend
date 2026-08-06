package com.dnd.qello.feed.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.feed.view.InboxCard;
import com.dnd.qello.feed.view.InboxDetail;

public interface InboxQueryRepository {

	/** 아직 답변·넘김 확정·만료되지 않은 수신 질문글. 수신 상한 이하이므로 페이징하지 않는다. */
	List<InboxCard> findInbox(long recipientId, Instant at);

	/** 소유권을 쿼리 조건에 포함한다. 남의 항목이면 빈 결과다. */
	Optional<InboxDetail> findDetail(long recipientId, long postRecipientId);
}
