package com.dnd.qello.feed.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.feed.repository.SentPostQueryRepository;
import com.dnd.qello.feed.repository.SentPostQueryRepository.SentPostCursor;
import com.dnd.qello.feed.view.SentPostCard;
import com.dnd.qello.feed.view.SentPostDetail;
import com.dnd.qello.feed.view.SentPostFilter;

/**
 * `내가 쓴 질문` 조회 진입점.
 * 답변 열람 시각 기록은 direction.service.DirectionPostService가 소유한다 — 여기서는 읽기만 한다.
 */
@Service
public class SentPostQueryService {

	private final SentPostQueryRepository sentPostQueryRepository;

	public SentPostQueryService(SentPostQueryRepository sentPostQueryRepository) {
		this.sentPostQueryRepository = sentPostQueryRepository;
	}

	@Transactional(readOnly = true)
	public List<SentPostCard> list(long senderId, SentPostFilter filter, SentPostCursor cursor, int limit, Instant at) {
		return sentPostQueryRepository.findSentPosts(senderId, filter, cursor, limit, at);
	}

	@Transactional(readOnly = true)
	public Optional<SentPostDetail> detail(long senderId, long postId) {
		return sentPostQueryRepository.findSentPostDetail(senderId, postId);
	}
}
