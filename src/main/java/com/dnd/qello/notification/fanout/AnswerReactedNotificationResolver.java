package com.dnd.qello.notification.fanout;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerReaction;
import com.dnd.qello.answer.repository.AnswerReactionRepository;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
final class AnswerReactedNotificationResolver implements NotificationFanOutResolver {

	private final AnswerRepository answerRepository;
	private final AnswerReactionRepository reactionRepository;
	private final ObjectMapper objectMapper;

	AnswerReactedNotificationResolver(AnswerRepository answerRepository,
		AnswerReactionRepository reactionRepository, ObjectMapper objectMapper) {
		this.answerRepository = answerRepository;
		this.reactionRepository = reactionRepository;
		this.objectMapper = objectMapper;
	}

	@Override
	public Set<OutboxEventType> eventTypes() {
		return Set.of(OutboxEventType.ANSWER_REACTED);
	}

	@Override
	public FanOutInstruction resolve(OutboxEvent event) {
		NotificationFanOutPayloads.AnswerReacted payload = NotificationFanOutPayloads.read(
			objectMapper, event.payload(), NotificationFanOutPayloads.AnswerReacted.class);
		NotificationFanOutPayloads.requireEvent(event, OutboxAggregateType.ANSWER,
			OutboxEventType.ANSWER_REACTED, payload.answerId());
		Answer answer = answerRepository.findById(event.aggregateId())
			.orElseThrow(() -> NotificationFanOutPayloads.invalidPayload(null));
		Optional<AnswerReaction> reaction = reactionRepository.findByAnswerIdAndReactorId(
			answer.getId(), payload.reactorId());
		if (reaction.isEmpty()) return FanOutInstruction.suppress();
		return FanOutInstruction.notification(NotificationType.ANSWER_REACTED, answer.getAuthorId(),
			payload.reactorId(), answer.getId(), "answer-reacted:event:" + event.id());
	}
}
