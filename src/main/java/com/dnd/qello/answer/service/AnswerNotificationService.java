package com.dnd.qello.answer.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerStatus;
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
	 * 반환한다(멱등) — 이 멱등 분기 안쪽에서만 releaseSlot을 호출하므로 재호출로
	 * 슬롯이 중복 회수되지 않는다.
	 */
	@Transactional
	public Answer publish(long answerId, Instant publishedAt) {
		Answer answer = answerRepository.findById(answerId)
			.orElseThrow(() -> new IllegalArgumentException("답변을 찾을 수 없습니다: " + answerId));
		if (answer.getStatus() == AnswerStatus.PUBLISHED) {
			return answer;
		}
		Answer safetyChecked = answer.getStatus() == AnswerStatus.SUBMITTED ? answer.startSafetyCheck() : answer;
		Answer published = safetyChecked.markSafetyPassed().publish(publishedAt);
		Answer saved = answerRepository.save(published);
		releaseSlot(answer.getPostRecipientId(), publishedAt);
		outboxEventRepository.findByDedupKey("answer-published:" + answerId).orElseGet(() ->
			outboxEventRepository.save(OutboxEvent.pending(OutboxAggregateType.ANSWER, answerId,
				OutboxEventType.ANSWER_PUBLISHED, "answer-published:" + answerId,
				"{\"answerId\":" + answerId + "}", publishedAt)));
		return saved;
	}

	/**
	 * 답변한 질문글은 수신함에서 사라지고 슬롯 1개가 회수된다.
	 * post_recipient 전이와 카운터 감소가 갈라지면 ct_post_recipient_capacity_release가
	 * commit 시점에 거부하므로 같은 transaction 안에서 함께 수행한다.
	 */
	private void releaseSlot(long postRecipientId, Instant at) {
		PostRecipient recipient = recipientRepository.findById(postRecipientId)
			.orElseThrow(() -> new IllegalArgumentException("수신 항목을 찾을 수 없습니다: " + postRecipientId));
		if (recipient.getStatus() == PostRecipientStatus.ANSWERED) {
			return;
		}
		recipientRepository.save(recipient.answered(at));
		receiveStateRepository.release(recipient.getRecipientId(), at);
	}
}
