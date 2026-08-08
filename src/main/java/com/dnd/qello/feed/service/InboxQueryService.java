package com.dnd.qello.feed.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.feed.repository.InboxQueryRepository;
import com.dnd.qello.feed.view.InboxCard;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.view.InboxDetail;

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

	public List<InboxCard> list(long recipientId, InboxCategory category, Instant at) {
		return inboxQueryRepository.findInbox(recipientId, category, at);
	}

	public Optional<InboxDetail> detail(long recipientId, long postRecipientId) {
		return inboxQueryRepository.findDetail(recipientId, postRecipientId);
	}
}
