package com.dnd.qello.direction.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.direction.domain.PostReaction;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.PostReactionRepository;
import com.dnd.qello.direction.repository.PostRecipientRepository;

import lombok.RequiredArgsConstructor;

/**
 * 질문글 공감의 남김과 취소를 소유한다.
 * 자격은 즉시 복합 FK fk_post_reaction_recipient가 최종 강제하지만,
 * raw DataIntegrityViolationException이 호출자에게 새어나가지 않도록 먼저 확인해
 * INELIGIBLE_REACTOR로 변환한다. FK는 경쟁 조건의 최종 방어선으로 남긴다.
 * <p>
 * react와 cancel은 각각 몇 번 호출해도 최종 상태가 하나로 정해진다. HTTP의 PUT과
 * DELETE가 이 둘에 대응하며, 재시도로 도착한 중복 요청이 공감을 뒤집지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PostReactionService {

	private final PostReactionRepository reactionRepository;
	private final PostRecipientRepository recipientRepository;

	/**
	 * 이미 공감한 상태면 저장을 건너뛴다.
	 *
	 * @return 반영 후 그 질문글의 공감 수
	 */
	@Transactional
	public long react(long postId, long reactorId, Instant at) {
		requireEligibleReactor(postId, reactorId);
		if (!reactionRepository.exists(postId, reactorId)) {
			reactionRepository.react(PostReaction.create(postId, reactorId, at));
		}
		return reactionRepository.countByPostId(postId);
	}

	/**
	 * 공감 자격을 검사하지 않는다. 삭제 조건이 (postId, reactorId)라 남의 공감에는 닿지
	 * 않고, 질문글이 만료돼 자격을 잃은 뒤에도 자기가 남긴 공감은 거둘 수 있어야 한다.
	 * 공감이 없는 상태에서 호출해도 실패하지 않는다.
	 *
	 * @return 반영 후 그 질문글의 공감 수
	 */
	@Transactional
	public long cancel(long postId, long reactorId) {
		reactionRepository.cancel(postId, reactorId);
		return reactionRepository.countByPostId(postId);
	}

	/**
	 * 한 번의 호출이 현재 상태를 뒤집는다. HTTP로는 노출하지 않는다 — 재시도가 결과를
	 * 반대로 만들기 때문이다. 기존 호출자를 위해 react·cancel과 같은 자격 검사·저장
	 * 연산으로 남기되, 공감 수까지 다시 세는 react·cancel을 그대로 호출하지 않는다 —
	 * toggle은 그 값을 쓰지 않으므로 매번 낭비되는 count 조회 하나를 더할 이유가 없다.
	 *
	 * @return true면 공감을 남긴 상태, false면 취소된 상태
	 */
	@Transactional
	public boolean toggle(long postId, long reactorId, Instant at) {
		requireEligibleReactor(postId, reactorId);
		if (reactionRepository.exists(postId, reactorId)) {
			reactionRepository.cancel(postId, reactorId);
			return false;
		}
		reactionRepository.react(PostReaction.create(postId, reactorId, at));
		return true;
	}

	private void requireEligibleReactor(long postId, long reactorId) {
		if (recipientRepository.findByPostIdAndRecipientId(postId, reactorId).isEmpty()) {
			throw new DirectionException(
				DirectionErrorCode.INELIGIBLE_REACTOR, "reactorId", "수신 자격이 없는 사용자는 공감할 수 없습니다");
		}
	}
}
