package com.dnd.qello.notification.fanout;

import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

final class NotificationFanOutPayloads {

	private NotificationFanOutPayloads() { }

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AnswerPublished(long answerId) { }

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AnswerReacted(long answerId, long reactorId) { }

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ProposalReviewed(long proposalId, long proposerId, String decision) { }

	@JsonIgnoreProperties(ignoreUnknown = true)
	record QuestionRecommended(long assignmentId) { }

	static <T> T read(ObjectMapper objectMapper, String payload, Class<T> type) {
		try {
			return objectMapper.readValue(payload, type);
		} catch (JsonProcessingException e) {
			throw invalidPayload(e);
		}
	}

	static void requireEvent(OutboxEvent event, OutboxAggregateType aggregateType,
		OutboxEventType eventType, long payloadId) {
		if (event == null || event.aggregateType() != aggregateType || event.eventType() != eventType
			|| payloadId <= 0 || payloadId != event.aggregateId()) {
			throw invalidPayload(null);
		}
	}

	static NotificationException invalidPayload(Throwable cause) {
		return new NotificationException(NotificationErrorCode.INVALID_PAYLOAD, "payload",
			"notification fan-out payload가 유효하지 않습니다", cause);
	}
}
