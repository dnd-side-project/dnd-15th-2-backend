package com.dnd.qello.answer.repository.jpa;

import com.dnd.qello.answer.domain.Answer;

final class AnswerJpaMapper {

	private AnswerJpaMapper() { }

	static Answer toDomain(AnswerJpaEntity entity) {
		return Answer.restore(entity.getId(), entity.getPostRecipientId(), entity.getAuthorId(),
			entity.getStatus(), entity.getIdempotencyKey(), entity.getBodyText(),
			entity.getCoarseRegionCode(), entity.getBearingFromSenderDegrees(), entity.getDistanceBand(),
			entity.getModerationStatus(), entity.getSubmittedAt(), entity.getPublishedAt(), entity.getDeletedAt());
	}
}
