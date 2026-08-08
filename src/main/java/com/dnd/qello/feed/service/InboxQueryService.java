package com.dnd.qello.feed.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.feed.repository.InboxQueryRepository;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.view.InboxDetail;
import com.dnd.qello.feed.view.InboxListing;

import lombok.RequiredArgsConstructor;

/**
 * `내게 온 질문` 조회 진입점.
 * 상태 전이는 direction.service.PostRecipientService가 소유한다 — 여기서는 읽기만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InboxQueryService {

	private final InboxQueryRepository inboxQueryRepository;

	/**
	 * 목록과 방향 칩을 같은 시각 기준으로 함께 반환한다. 칩은 directionSegmentKey와
	 * 무관하게 category 스코프 전체를 집계한다 — 필터를 건 상태에서도 다른 방향으로
	 * 갈아탈 수 있어야 하기 때문이다.
	 */
	public InboxListing list(long recipientId, InboxCategory category, String directionSegmentKey, Instant at) {
		return new InboxListing(
			inboxQueryRepository.findInbox(recipientId, category, directionSegmentKey, at),
			inboxQueryRepository.countByDirection(recipientId, category, at));
	}

	public Optional<InboxDetail> detail(long recipientId, long postRecipientId) {
		return inboxQueryRepository.findDetail(recipientId, postRecipientId);
	}
}
