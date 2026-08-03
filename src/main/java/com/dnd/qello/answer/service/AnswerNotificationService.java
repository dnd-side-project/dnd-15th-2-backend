package com.dnd.qello.answer.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;

/** 답변 상태 변경과 outbox 기록을 한 transaction으로 묶는 persistence application service. */
@Service
public class AnswerNotificationService {

	private final AnswerRepository answerRepository;
	private final OutboxEventRepository outboxEventRepository;

	public AnswerNotificationService(AnswerRepository answerRepository, OutboxEventRepository outboxEventRepository) {
		this.answerRepository = answerRepository;
		this.outboxEventRepository = outboxEventRepository;
	}

	@Transactional
	public Answer submit(Answer answer) {
		return answerRepository.findByAuthorAndIdempotencyKey(answer.getAuthorId(), answer.getIdempotencyKey())
			.orElseGet(() -> answerRepository.save(answer));
	}

	@Transactional
	public Answer publish(long answerId, Instant publishedAt) {
		Answer answer = answerRepository.findById(answerId).orElseThrow(() -> new IllegalArgumentException("답변을 찾을 수 없습니다: " + answerId));
		if (answer.getStatus() == com.dnd.qello.answer.domain.AnswerStatus.PUBLISHED) {
			return answer;
		}
		Answer safetyChecked = answer.getStatus() == com.dnd.qello.answer.domain.AnswerStatus.SUBMITTED
			? answer.startSafetyCheck() : answer;
		Answer published = safetyChecked.markSafetyPassed().publish(publishedAt);
		Answer saved = answerRepository.save(published);
		outboxEventRepository.findByDedupKey("answer-published:" + answerId).orElseGet(() ->
			outboxEventRepository.save(OutboxEvent.pending(OutboxAggregateType.ANSWER, answerId,
				OutboxEventType.ANSWER_PUBLISHED, "answer-published:" + answerId,
				"{\"answerId\":" + answerId + "}", publishedAt)));
		return saved;
	}
}
