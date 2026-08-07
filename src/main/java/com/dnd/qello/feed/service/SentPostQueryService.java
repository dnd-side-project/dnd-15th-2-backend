package com.dnd.qello.feed.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.feed.repository.SentPostQueryRepository;
import com.dnd.qello.feed.repository.SentPostQueryRepository.AnswerCursor;
import com.dnd.qello.feed.repository.SentPostQueryRepository.SentPostCursor;
import com.dnd.qello.feed.view.AnswerCard;
import com.dnd.qello.feed.view.SentPostCard;
import com.dnd.qello.feed.view.SentPostDetail;
import com.dnd.qello.feed.view.SentPostFilter;

import lombok.RequiredArgsConstructor;

/**
 * `내가 쓴 질문` 조회 진입점.
 * 답변 열람 시각 기록은 direction.service.DirectionPostService가 소유한다 — 여기서는 읽기만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SentPostQueryService {

	private final SentPostQueryRepository sentPostQueryRepository;

	public List<SentPostCard> list(long senderId, SentPostFilter filter, SentPostCursor cursor, int limit, Instant at) {
		return sentPostQueryRepository.findSentPosts(senderId, filter, cursor, limit, at);
	}

	public Optional<SentPostDetail> detail(long senderId, long postId) {
		return sentPostQueryRepository.findSentPostDetail(senderId, postId);
	}

	/** 질문자가 아니면 빈 목록을 받는다. 권한 예외를 던지지 않는 이유는 질문글 존재 여부를 흘리지 않기 위함이다. */
	public List<AnswerCard> answers(long senderId, long postId, AnswerCursor cursor, int limit) {
		return sentPostQueryRepository.findAnswers(senderId, postId, cursor, limit);
	}
}
