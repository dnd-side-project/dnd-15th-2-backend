package com.dnd.qello.feed.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.feed.repository.PostAnswerQueryRepository;
import com.dnd.qello.feed.repository.PostAnswerQueryRepository.AnswerCursor;
import com.dnd.qello.feed.view.AnswerCard;

import lombok.RequiredArgsConstructor;

/**
 * 답변 열람 자격 판정과 조회 진입점. 질문글 작성자와 그 질문글의 수신 자격자가
 * 볼 수 있다(2026-08-07 개정, ADR 0002). AnswerReactionService의 공감 자격도
 * 이 서비스의 canViewAnswers를 재사용한다 — 자격 규칙은 여기 한 곳에만 둔다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostAnswerQueryService {

	private final PostAnswerQueryRepository postAnswerQueryRepository;

	public boolean canView(long viewerId, long postId, Instant at) {
		return postAnswerQueryRepository.canViewAnswers(viewerId, postId, at);
	}

	/** 자격이 없으면 빈 목록을 받는다. 권한 예외를 던지지 않는 이유는 질문글 존재 여부를 흘리지 않기 위함이다. */
	public List<AnswerCard> answers(long viewerId, long postId, AnswerCursor cursor, int limit, Instant at) {
		return postAnswerQueryRepository.findAnswers(viewerId, postId, cursor, limit, at);
	}
}
