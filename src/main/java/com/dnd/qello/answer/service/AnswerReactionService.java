package com.dnd.qello.answer.service;

import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerReaction;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.AnswerReactionRepository;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.feed.service.PostAnswerQueryService;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;

/**
 * 답변 공감의 남김과 취소를 소유한다.
 * 자격(질문글 작성자 또는 그 질문글의 수신 자격자, 자기 답변 제외)은
 * ct_answer_reaction_reactor_can_view가 최종 강제하지만 이 trigger는
 * DEFERRABLE INITIALLY DEFERRED라 위반이 commit 시점에야 드러난다. 호출자가 원인을
 * 추적할 수 없는 위치에서 실패하지 않도록 여기서 먼저 확인한다. trigger는 경쟁 조건의
 * 최종 방어선으로 남긴다. 열람 자격 판정은 feed.service.PostAnswerQueryService를
 * 재사용한다 — 답변 목록 조회 자격과 같은 규칙이라 두 곳에 각자 두면 갈라진다.
 * <p>
 * react와 cancel을 분리하면 AnswerReactionRepository.react가 경고한 "같은 transaction
 * 안에서 cancel 직후 같은 key로 react" 조합이 한 호출 안에서 만들어지지 않는다.
 */
@Service
public class AnswerReactionService {

	private final AnswerReactionRepository reactionRepository;
	private final AnswerRepository answerRepository;
	private final PostRecipientRepository recipientRepository;
	private final PostAnswerQueryService postAnswerQueryService;
	private final OutboxEventRepository outboxEventRepository;
	private final TransactionTemplate newReactionAttempt;

	public AnswerReactionService(
		AnswerReactionRepository reactionRepository,
		AnswerRepository answerRepository,
		PostRecipientRepository recipientRepository,
		PostAnswerQueryService postAnswerQueryService,
		OutboxEventRepository outboxEventRepository,
		PlatformTransactionManager transactionManager) {
		this.reactionRepository = reactionRepository;
		this.answerRepository = answerRepository;
		this.recipientRepository = recipientRepository;
		this.postAnswerQueryService = postAnswerQueryService;
		this.outboxEventRepository = outboxEventRepository;
		this.newReactionAttempt = new TransactionTemplate(transactionManager);
		// 동시에 도착한 두 PUT이 모두 findByAnswerIdAndReactorId를 빈 값으로 보고 함께
		// insert를 시도하면 PK 위반으로 한쪽이 실패한다(PostReactionService.react와 같은
		// 경합). REQUIRES_NEW로 그 삽입 시도를 별도 물리 트랜잭션에 가둬야, 실패해도
		// 호출자의 앰비언트 트랜잭션과 Postgres 커넥션이 오염되지 않고 react()가 재조회로
		// 복구할 수 있다. ct_answer_reaction_reactor_can_view의 지연 검사도 이 트랜잭션의
		// commit 시점에 함께 걸리므로, 진짜 자격 위반과 단순 삽입 경합은 재조회 결과로
		// 구분한다 — 실패 후에도 행이 존재하면 남이 이미 커밋한 성공이라 경합, 존재하지
		// 않으면 자격 위반이라 원래 예외를 그대로 전파한다.
		this.newReactionAttempt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * 이미 공감한 상태면 저장을 건너뛴다. 동시에 도착한 두 PUT이 함께 삽입을 시도해 PK
	 * 위반이 나면, 그 요청을 진 쪽으로 취급하지 않고 재조회해 이긴 쪽과 같은 결과로
	 * 수렴시킨다(둘 다 "공감함"으로 응답).
	 *
	 * @return 반영 후 그 답변의 공감 수
	 */
	public long react(long answerId, long reactorId, Instant at) {
		requireEligibleReactor(answerId, reactorId, at);
		try {
			return newReactionAttempt.execute(status -> insertIfAbsent(answerId, reactorId, at));
		} catch (DataIntegrityViolationException race) {
			if (reactionRepository.findByAnswerIdAndReactorId(answerId, reactorId).isEmpty()) {
				throw race;
			}
			return reactionRepository.countByAnswerId(answerId);
		}
	}

	private long insertIfAbsent(long answerId, long reactorId, Instant at) {
		if (reactionRepository.findByAnswerIdAndReactorId(answerId, reactorId).isEmpty()) {
			AnswerReaction reaction = AnswerReaction.create(answerId, reactorId, at);
			reactionRepository.react(reaction);
			publishReactionEvent(reaction);
		}
		return reactionRepository.countByAnswerId(answerId);
	}

	/**
	 * 공감 자격을 검사하지 않는다. 삭제 조건이 (answerId, reactorId)라 남의 공감에는 닿지
	 * 않고, 질문글이 만료돼 자격을 잃은 뒤에도 자기가 남긴 공감은 거둘 수 있어야 한다.
	 * 공감이 없는 상태에서 호출해도 실패하지 않는다.
	 *
	 * @return 반영 후 그 답변의 공감 수
	 */
	@Transactional
	public long cancel(long answerId, long reactorId) {
		reactionRepository.cancel(answerId, reactorId);
		return reactionRepository.countByAnswerId(answerId);
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
	public boolean toggle(long answerId, long reactorId, Instant at) {
		requireEligibleReactor(answerId, reactorId, at);
		if (reactionRepository.findByAnswerIdAndReactorId(answerId, reactorId).isPresent()) {
			reactionRepository.cancel(answerId, reactorId);
			return false;
		}
		AnswerReaction reaction = AnswerReaction.create(answerId, reactorId, at);
		reactionRepository.react(reaction);
		publishReactionEvent(reaction);
		return true;
	}

	private void publishReactionEvent(AnswerReaction reaction) {
		String dedupKey = answerReactedDedupKey(reaction);
		if (outboxEventRepository.findByDedupKey(dedupKey).isPresent()) return;
		outboxEventRepository.save(OutboxEvent.pending(
			OutboxAggregateType.ANSWER, reaction.getAnswerId(), OutboxEventType.ANSWER_REACTED,
			dedupKey, answerReactedPayload(reaction), reaction.getCreatedAt()));
	}

	private static String answerReactedDedupKey(AnswerReaction reaction) {
		return "answer-reacted:" + reaction.getAnswerId() + ":"
			+ reaction.getReactorId() + ":" + reaction.getCreatedAt();
	}

	private static String answerReactedPayload(AnswerReaction reaction) {
		return "{\"answerId\":" + reaction.getAnswerId()
			+ ",\"reactorId\":" + reaction.getReactorId() + "}";
	}

	private void requireEligibleReactor(long answerId, long reactorId, Instant at) {
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
	}
}
