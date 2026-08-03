package com.dnd.qello.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerModerationStatus;
import com.dnd.qello.answer.domain.AnswerStatus;

/**
 * Created at: 2026-08-03T21:10:00+09:00
 * Source scenario: TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION-UNIT-001 through UNIT-002
 */
class AnswerPersistenceBoundaryTest {

	private static final Instant SUBMITTED = Instant.parse("2026-08-03T12:00:00Z");

	@Test
	@DisplayName("Answer는 recipient-author scalar ID와 bearing 범위를 검증한다")
	void validatesAnswerScalarIdsAndBearing() {
		Answer answer = Answer.submit(10L, 20L, "answer-key", "답변", "TEST", BigDecimal.valueOf(45), "NEAR", SUBMITTED);

		assertThat(answer.getId()).isNull();
		assertThat(answer.getPostRecipientId()).isEqualTo(10L);
		assertThat(answer.getAuthorId()).isEqualTo(20L);
		assertThat(answer.getStatus()).isEqualTo(AnswerStatus.SUBMITTED);
		assertThatThrownBy(() -> Answer.submit(10L, 20L, "key", "답변", "TEST", BigDecimal.valueOf(360), "NEAR", SUBMITTED))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Answer는 안전 검사 통과 후에만 공개되고 공개 시각을 보존한다")
	void publishesOnlyAfterSafetyPassed() {
		Answer answer = Answer.submit(10L, 20L, "answer-key", "답변", "TEST", BigDecimal.ZERO, "NEAR", SUBMITTED)
			.startSafetyCheck();

		assertThatThrownBy(() -> answer.publish(SUBMITTED.plusSeconds(1)))
			.isInstanceOf(IllegalStateException.class);

		Answer published = answer.markSafetyPassed().publish(SUBMITTED.plusSeconds(1));
		assertThat(published.getStatus()).isEqualTo(AnswerStatus.PUBLISHED);
		assertThat(published.getModerationStatus()).isEqualTo(AnswerModerationStatus.PASSED);
		assertThat(published.getPublishedAt()).isEqualTo(SUBMITTED.plusSeconds(1));
	}

	@Test
	@DisplayName("이미 보류 또는 반려된 Answer는 안전 검사 통과로 되돌릴 수 없다")
	void doesNotBypassHeldModerationState() {
		Answer held = Answer.submit(10L, 20L, "answer-key", "답변", "TEST", BigDecimal.ZERO, "NEAR", SUBMITTED)
			.startSafetyCheck();
		Answer rejected = held.rejectSafety();

		assertThatThrownBy(rejected::markSafetyPassed).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("DELETED Answer는 deletedAt을 반드시 가지며 임의 상태 전이를 거절한다")
	void enforcesDeletedTimestampAndTransitions() {
		Answer answer = Answer.submit(10L, 20L, "answer-key", "답변", "TEST", BigDecimal.ZERO, "NEAR", SUBMITTED);

		assertThatThrownBy(() -> answer.publish(SUBMITTED.plusSeconds(1)))
			.isInstanceOf(IllegalStateException.class);
		Answer deleted = answer.delete(SUBMITTED.plusSeconds(2));
		assertThat(deleted.getStatus()).isEqualTo(AnswerStatus.DELETED);
		assertThat(deleted.getDeletedAt()).isEqualTo(SUBMITTED.plusSeconds(2));
	}
}
