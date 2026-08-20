package com.dnd.qello.notification.fanout;

import java.util.Set;

import org.springframework.stereotype.Service;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
final class AnswerPublishedNotificationResolver implements NotificationFanOutResolver {

	private final AnswerRepository answerRepository;
	private final PostRecipientRepository recipientRepository;
	private final DirectionPostRepository postRepository;
	private final ObjectMapper objectMapper;

	AnswerPublishedNotificationResolver(AnswerRepository answerRepository,
		PostRecipientRepository recipientRepository, DirectionPostRepository postRepository,
		ObjectMapper objectMapper) {
		this.answerRepository = answerRepository;
		this.recipientRepository = recipientRepository;
		this.postRepository = postRepository;
		this.objectMapper = objectMapper;
	}

	@Override
	public Set<OutboxEventType> eventTypes() {
		return Set.of(OutboxEventType.ANSWER_PUBLISHED);
	}

	@Override
	public FanOutInstruction resolve(OutboxEvent event) {
		NotificationFanOutPayloads.AnswerPublished payload = NotificationFanOutPayloads.read(
			objectMapper, event.payload(), NotificationFanOutPayloads.AnswerPublished.class);
		NotificationFanOutPayloads.requireEvent(event, OutboxAggregateType.ANSWER,
			OutboxEventType.ANSWER_PUBLISHED, payload.answerId());
		Answer answer = answerRepository.findById(event.aggregateId())
			.orElseThrow(() -> NotificationFanOutPayloads.invalidPayload(null));
		if (answer.getStatus() != AnswerStatus.PUBLISHED) {
			throw NotificationFanOutPayloads.invalidPayload(null);
		}
		PostRecipient recipient = recipientRepository.findById(answer.getPostRecipientId())
			.orElseThrow(() -> NotificationFanOutPayloads.invalidPayload(null));
		DirectionPost post = postRepository.findById(recipient.getPostId())
			.orElseThrow(() -> NotificationFanOutPayloads.invalidPayload(null));
		return FanOutInstruction.notification(NotificationType.ANSWER_RECEIVED, post.getSenderId(),
			answer.getAuthorId(), answer.getId(), "answer-received:" + answer.getId());
	}
}
