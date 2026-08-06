package com.dnd.qello.direction.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.direction.domain.PostReaction;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.PostReactionRepository;
import com.dnd.qello.direction.repository.PostRecipientRepository;

/**
 * 질문글 공감의 토글을 소유한다.
 * 자격은 즉시 복합 FK fk_post_reaction_recipient가 최종 강제하지만,
 * raw DataIntegrityViolationException이 호출자에게 새어나가지 않도록 먼저 확인해
 * INELIGIBLE_REACTOR로 변환한다. FK는 경쟁 조건의 최종 방어선으로 남긴다.
 */
@Service
public class PostReactionService {

	private final PostReactionRepository reactionRepository;
	private final PostRecipientRepository recipientRepository;

	public PostReactionService(PostReactionRepository reactionRepository, PostRecipientRepository recipientRepository) {
		this.reactionRepository = reactionRepository;
		this.recipientRepository = recipientRepository;
	}

	/** @return true면 공감을 남긴 상태, false면 취소된 상태 */
	@Transactional
	public boolean toggle(long postId, long reactorId, Instant at) {
		if (recipientRepository.findByPostIdAndRecipientId(postId, reactorId).isEmpty()) {
			throw new DirectionException(
				DirectionErrorCode.INELIGIBLE_REACTOR, "reactorId", "수신 자격이 없는 사용자는 공감할 수 없습니다");
		}
		if (reactionRepository.exists(postId, reactorId)) {
			reactionRepository.cancel(postId, reactorId);
			return false;
		}
		reactionRepository.react(PostReaction.create(postId, reactorId, at));
		return true;
	}
}
