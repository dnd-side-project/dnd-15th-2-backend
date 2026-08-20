package com.dnd.qello.notification.fanout;

import java.util.Set;

import org.springframework.stereotype.Service;

import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.repository.QuestionProposalRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
final class QuestionProposalReviewedNotificationResolver implements NotificationFanOutResolver {

	private final QuestionProposalRepository proposalRepository;
	private final ObjectMapper objectMapper;

	QuestionProposalReviewedNotificationResolver(QuestionProposalRepository proposalRepository,
		ObjectMapper objectMapper) {
		this.proposalRepository = proposalRepository;
		this.objectMapper = objectMapper;
	}

	@Override
	public Set<OutboxEventType> eventTypes() {
		return Set.of(OutboxEventType.QUESTION_PROPOSAL_REVIEWED);
	}

	@Override
	public FanOutInstruction resolve(OutboxEvent event) {
		NotificationFanOutPayloads.ProposalReviewed payload = NotificationFanOutPayloads.read(
			objectMapper, event.payload(), NotificationFanOutPayloads.ProposalReviewed.class);
		NotificationFanOutPayloads.requireEvent(event, OutboxAggregateType.QUESTION_PROPOSAL,
			OutboxEventType.QUESTION_PROPOSAL_REVIEWED, payload.proposalId());
		QuestionProposal proposal = proposalRepository.findById(event.aggregateId())
			.orElseThrow(() -> NotificationFanOutPayloads.invalidPayload(null));
		return FanOutInstruction.notification(NotificationType.QUESTION_PROPOSAL_REVIEWED,
			proposal.getProposerId(), null, null, "question-proposal-reviewed:" + proposal.getId());
	}
}
