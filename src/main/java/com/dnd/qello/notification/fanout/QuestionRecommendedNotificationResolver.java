package com.dnd.qello.notification.fanout;

import java.util.Set;

import org.springframework.stereotype.Service;

import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.question.domain.QuestionAssignment;
import com.dnd.qello.question.domain.QuestionAssignmentCycle;
import com.dnd.qello.question.repository.QuestionAssignmentCycleRepository;
import com.dnd.qello.question.repository.QuestionAssignmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
final class QuestionRecommendedNotificationResolver implements NotificationFanOutResolver {

	private final QuestionAssignmentRepository assignmentRepository;
	private final QuestionAssignmentCycleRepository cycleRepository;
	private final ObjectMapper objectMapper;

	QuestionRecommendedNotificationResolver(QuestionAssignmentRepository assignmentRepository,
		QuestionAssignmentCycleRepository cycleRepository, ObjectMapper objectMapper) {
		this.assignmentRepository = assignmentRepository;
		this.cycleRepository = cycleRepository;
		this.objectMapper = objectMapper;
	}

	@Override
	public Set<OutboxEventType> eventTypes() {
		return Set.of(OutboxEventType.QUESTION_RECOMMENDED);
	}

	@Override
	public FanOutInstruction resolve(OutboxEvent event) {
		NotificationFanOutPayloads.QuestionRecommended payload = NotificationFanOutPayloads.read(
			objectMapper, event.payload(), NotificationFanOutPayloads.QuestionRecommended.class);
		NotificationFanOutPayloads.requireEvent(event, OutboxAggregateType.QUESTION_ASSIGNMENT,
			OutboxEventType.QUESTION_RECOMMENDED, payload.assignmentId());
		QuestionAssignment assignment = assignmentRepository.findById(event.aggregateId())
			.orElseThrow(() -> NotificationFanOutPayloads.invalidPayload(null));
		QuestionAssignmentCycle cycle = cycleRepository.findById(assignment.getCycleId())
			.orElseThrow(() -> NotificationFanOutPayloads.invalidPayload(null));
		return FanOutInstruction.notification(NotificationType.QUESTION_RECOMMENDED,
			cycle.getUserId(), null, null, "question-recommended:" + assignment.getId());
	}
}
