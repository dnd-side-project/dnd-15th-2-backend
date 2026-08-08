package com.dnd.qello.answer.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerReaction;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.AnswerReactionRepository;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.feed.service.PostAnswerQueryService;

import lombok.RequiredArgsConstructor;

/**
 * 답변 공감의 토글을 소유한다.
 * 자격(질문글 작성자 또는 그 질문글의 수신 자격자, 자기 답변 제외)은
 * ct_answer_reaction_reactor_can_view가 최종 강제하지만 이 trigger는
 * DEFERRABLE INITIALLY DEFERRED라 위반이 commit 시점에야 드러난다. 호출자가 원인을
 * 추적할 수 없는 위치에서 실패하지 않도록 여기서 먼저 확인한다. trigger는 경쟁 조건의
 * 최종 방어선으로 남긴다. 열람 자격 판정은 feed.service.PostAnswerQueryService를
 * 재사용한다 — 답변 목록 조회 자격과 같은 규칙이라 두 곳에 각자 두면 갈라진다.
 */
@Service
@RequiredArgsConstructor
public class AnswerReactionService {

	private final AnswerReactionRepository reactionRepository;
	private final AnswerRepository answerRepository;
	private final PostRecipientRepository recipientRepository;
	private final PostAnswerQueryService postAnswerQueryService;

	/** @return true면 공감을 남긴 상태, false면 취소된 상태 */
	@Transactional
	public boolean toggle(long answerId, long reactorId, Instant at) {
		Answer answer = answerRepository.findById(answerId)
			.orElseThrow(() -> new AnswerException(
				AnswerErrorCode.INVALID_ID, "answerId", "답변을 찾을 수 없습니다"));
		if (answer.getAuthorId() == reactorId) {
			throw new AnswerException(
				AnswerErrorCode.INELIGIBLE_REACTOR, "reactorId", "자기 답변에는 공감할 수 없습니다");
		}
		PostRecipient recipient = recipientRepository.findById(answer.getPostRecipientId())
			.orElseThrow(() -> new AnswerException(
				AnswerErrorCode.INVALID_ID, "postRecipientId", "수신 항목을 찾을 수 없습니다"));
		if (!postAnswerQueryService.canView(reactorId, recipient.getPostId(), at)) {
			throw new AnswerException(AnswerErrorCode.INELIGIBLE_REACTOR, "reactorId",
				"그 질문글을 볼 수 있는 질문글 작성자 또는 수신 자격자만 답변에 공감할 수 있습니다");
		}
		if (reactionRepository.findByAnswerIdAndReactorId(answerId, reactorId).isPresent()) {
			reactionRepository.cancel(answerId, reactorId);
			return false;
		}
		reactionRepository.react(AnswerReaction.create(answerId, reactorId, at));
		return true;
	}
}
