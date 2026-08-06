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
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.PostRecipientRepository;

/**
 * 답변 공감의 토글을 소유한다.
 * 자격은 ct_answer_reaction_reactor_is_sender가 최종 강제하지만 이 trigger는
 * DEFERRABLE INITIALLY DEFERRED라 위반이 commit 시점에야 드러난다. 호출자가 원인을
 * 추적할 수 없는 위치에서 실패하지 않도록 여기서 먼저 확인한다. trigger는 경쟁 조건의
 * 최종 방어선으로 남긴다.
 */
@Service
public class AnswerReactionService {

	private final AnswerReactionRepository reactionRepository;
	private final AnswerRepository answerRepository;
	private final PostRecipientRepository recipientRepository;
	private final DirectionPostRepository postRepository;

	public AnswerReactionService(AnswerReactionRepository reactionRepository, AnswerRepository answerRepository,
		PostRecipientRepository recipientRepository, DirectionPostRepository postRepository) {
		this.reactionRepository = reactionRepository;
		this.answerRepository = answerRepository;
		this.recipientRepository = recipientRepository;
		this.postRepository = postRepository;
	}

	/** @return true면 공감을 남긴 상태, false면 취소된 상태 */
	@Transactional
	public boolean toggle(long answerId, long reactorId, Instant at) {
		if (senderOf(answerId) != reactorId) {
			throw new AnswerException(
				AnswerErrorCode.INELIGIBLE_REACTOR, "reactorId", "질문한 사람만 답변에 공감할 수 있습니다");
		}
		if (reactionRepository.findByAnswerId(answerId).isPresent()) {
			reactionRepository.cancel(answerId);
			return false;
		}
		reactionRepository.react(AnswerReaction.create(answerId, reactorId, at));
		return true;
	}

	/** answer → post_recipient → direction_post 세 단계를 거슬러 그 질문글의 발신자(질문자) ID를 구한다. */
	private long senderOf(long answerId) {
		Answer answer = answerRepository.findById(answerId)
			.orElseThrow(() -> new AnswerException(
				AnswerErrorCode.INVALID_ID, "answerId", "답변을 찾을 수 없습니다"));
		PostRecipient recipient = recipientRepository.findById(answer.getPostRecipientId())
			.orElseThrow(() -> new AnswerException(
				AnswerErrorCode.INVALID_ID, "postRecipientId", "수신 항목을 찾을 수 없습니다"));
		return postRepository.findById(recipient.getPostId())
			.orElseThrow(() -> new AnswerException(
				AnswerErrorCode.INVALID_ID, "postId", "질문글을 찾을 수 없습니다"))
			.getSenderId();
	}
}
