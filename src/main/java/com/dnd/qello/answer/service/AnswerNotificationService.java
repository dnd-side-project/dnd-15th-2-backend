package com.dnd.qello.answer.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.direction.repository.RecipientReceiveStateRepository;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

/** 답변 상태 변경, 수신 슬롯 회수와 outbox 기록을 한 transaction으로 묶는 persistence application service. */
@Service
@RequiredArgsConstructor
public class AnswerNotificationService {

	private final AnswerRepository answerRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final PostRecipientRepository recipientRepository;
	private final RecipientReceiveStateRepository receiveStateRepository;

	/** 같은 (authorId, idempotencyKey)로 재호출되면 새로 저장하지 않고 기존 답변을 그대로 반환한다. */
	@Transactional
	public Answer submit(Answer answer) {
		return answerRepository.findByAuthorAndIdempotencyKey(answer.getAuthorId(), answer.getIdempotencyKey())
			.orElseGet(() -> answerRepository.save(answer));
	}

	/**
	 * 안전 검사를 통과시키고 공개한다. 이미 PUBLISHED면 아무 부수효과 없이 그대로
	 * 반환한다(멱등).
	 *
	 * releaseSlot을 Answer 저장보다 먼저 시도한다(#93 이전에는 순서가 반대였다) —
	 * #93으로 만료·넘김확정·차단 전이가 post_recipient를 놓고 이 메서드와 경쟁하게
	 * 됐으므로, 슬롯 확보에 실패한 뒤에 Answer를 PUBLISHED로 저장해버리면 "게시된
	 * 답변인데 그 질문글은 EXPIRED/BLOCKED"인 불일치가 생긴다. 슬롯을 먼저 확보하고
	 * 성공했을 때만 Answer를 공개해 두 상태가 항상 함께 확정되게 한다.
	 */
	@Transactional
	public Answer publish(long answerId, Instant publishedAt) {
		Answer answer = answerRepository.findById(answerId)
			.orElseThrow(() -> new IllegalArgumentException("답변을 찾을 수 없습니다: " + answerId));
		if (answer.getStatus() == AnswerStatus.PUBLISHED) {
			return answer;
		}
		if (!releaseSlot(answer.getPostRecipientId(), publishedAt)) {
			throw new AnswerException(AnswerErrorCode.INVALID_ANSWER_STATUS, "postRecipientId",
				"질문글이 이미 만료되었거나 차단되어 답변을 게시할 수 없습니다");
		}
		Answer published = ensureSafetyChecking(answer).markSafetyPassed().publish(publishedAt);
		Answer saved = answerRepository.save(published);
		outboxEventRepository.findByDedupKey("answer-published:" + answerId).orElseGet(() ->
			outboxEventRepository.save(OutboxEvent.pending(OutboxAggregateType.ANSWER, answerId,
				OutboxEventType.ANSWER_PUBLISHED, "answer-published:" + answerId,
				"{\"answerId\":" + answerId + "}", publishedAt)));
		return saved;
	}

	/**
	 * 안전 검사 BLOCK 판정을 반영한다. 이미 REJECTED면(재시도) 아무 부수효과 없이 그대로
	 * 반환한다(멱등). 슬롯과 수신 자격은 건드리지 않는다 — BLOCK은 콘텐츠 거부일 뿐
	 * 수신 항목 자체의 자격을 바꾸지 않는다(GitHub #125).
	 */
	@Transactional
	public Answer reject(long answerId, Instant at) {
		Answer answer = answerRepository.findById(answerId).orElseThrow(this::answerNotFound);
		if (answer.getStatus() == AnswerStatus.REJECTED) {
			return answer;
		}
		return answerRepository.save(ensureSafetyChecking(answer).rejectSafety());
	}

	// markSafetyPassed()/rejectSafety()는 SAFETY_CHECKING만 받으므로, 최초 판정(SUBMITTED)이면
	// 먼저 그 상태로 전이시킨다. 재시도로 이미 SAFETY_CHECKING이면 그대로 둔다.
	private Answer ensureSafetyChecking(Answer answer) {
		return answer.getStatus() == AnswerStatus.SUBMITTED ? answer.startSafetyCheck() : answer;
	}

	/**
	 * post_recipient를 ANSWERED로 전이하고 슬롯 1개를 회수한다. 이미 ANSWERED면(재시도)
	 * 부수효과 없이 true를 반환한다. #93이 도입한 다른 전이(만료·넘김확정·차단)가 먼저
	 * 이 항목을 선점했으면(EXPIRED/SKIPPED/BLOCKED) false를 반환한다 — 호출자는 이
	 * 경우 답변 게시 자체를 거절해야 한다.
	 *
	 * post_recipient 전이와 카운터 감소가 갈라지면 ct_post_recipient_capacity_release가
	 * commit 시점에 거부하므로 같은 transaction 안에서 함께 수행한다.
	 *
	 * transitionToAnswered는 status가 여전히 recipient.getStatus()일 때만 성공하는
	 * 조건부 UPDATE다. 같은 답변에 대해 publish()가 동시에 재시도되면, 두 트랜잭션
	 * 모두 전이 전 상태(OPENED 등)를 읽은 뒤 조건부 UPDATE를 시도할 수 있다 — 먼저
	 * 커밋한 쪽만 성공하고, 나중 트랜잭션의 UPDATE는 행이 이미 ANSWERED로 바뀐 뒤
	 * 재평가되어 0행을 매치한다. 이 0행 결과만으로는 "이미 같은 전이로 선점됨(멱등,
	 * 성공 취급)"과 "다른 전이(만료·넘김확정·차단)로 선점됨(진짜 충돌, 거절)"을 구분할
	 * 수 없으므로 재조회해서 판별한다.
	 */
	private boolean releaseSlot(long postRecipientId, Instant at) {
		PostRecipient recipient = recipientRepository.findById(postRecipientId)
			.orElseThrow(() -> new IllegalArgumentException("수신 항목을 찾을 수 없습니다: " + postRecipientId));
		if (recipient.getStatus() == PostRecipientStatus.ANSWERED) {
			return true;
		}
		if (!recipient.isOpenForTransition()) {
			return false;
		}
		PostRecipient answered = recipient.answered(at);
		return recipientRepository.transitionToAnswered(answered, recipient.getStatus())
			.map(saved -> {
				receiveStateRepository.release(recipient.getRecipientId(), at);
				return true;
			})
			.orElseGet(() -> alreadyAnswered(postRecipientId));
	}

	private boolean alreadyAnswered(long postRecipientId) {
		return recipientRepository.findById(postRecipientId)
			.map(PostRecipient::getStatus)
			.filter(PostRecipientStatus.ANSWERED::equals)
			.isPresent();
	}

	private AnswerException answerNotFound() {
		return new AnswerException(AnswerErrorCode.ANSWER_NOT_FOUND, "answerId", "답변을 찾을 수 없습니다");
	}
}
