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
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;

/**
 * Created at: 2026-08-03T21:10:00+09:00
 * Source scenario: TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION-UNIT-001 through UNIT-002,
 * TEST-PLAN-GH-78-SCHEMA-REVISION-V7-UNIT-005 through UNIT-006
 */
class AnswerPersistenceBoundaryTest {

	private static final Instant SUBMITTED = Instant.parse("2026-08-03T12:00:00Z");
	private static final long DISTANCE_M = 5_000L;

	@Test
	@DisplayName("Answer는 recipient-author scalar ID와 bearing 범위를 검증한다")
	void validatesAnswerScalarIdsAndBearing() {
		Answer answer = Answer.submit(10L, 20L, "answer-key", "답변", "TEST", BigDecimal.valueOf(45), "NEAR", SUBMITTED, DISTANCE_M);

		assertThat(answer.getId()).isNull();
		assertThat(answer.getPostRecipientId()).isEqualTo(10L);
		assertThat(answer.getAuthorId()).isEqualTo(20L);
		assertThat(answer.getStatus()).isEqualTo(AnswerStatus.SUBMITTED);
		assertThatThrownBy(() -> Answer.submit(10L, 20L, "key", "답변", "TEST", BigDecimal.valueOf(360), "NEAR", SUBMITTED, DISTANCE_M))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_BEARING);
	}

	@Test
	@DisplayName("Answer는 안전 검사 통과 후에만 공개되고 공개 시각을 보존한다")
	void publishesOnlyAfterSafetyPassed() {
		Answer answer = Answer.submit(10L, 20L, "answer-key", "답변", "TEST", BigDecimal.ZERO, "NEAR", SUBMITTED, DISTANCE_M)
			.startSafetyCheck();

		assertThatThrownBy(() -> answer.publish(SUBMITTED.plusSeconds(1)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.SAFETY_CHECK_NOT_PASSED);

		Answer published = answer.markSafetyPassed().publish(SUBMITTED.plusSeconds(1));
		assertThat(published.getStatus()).isEqualTo(AnswerStatus.PUBLISHED);
		assertThat(published.getModerationStatus()).isEqualTo(AnswerModerationStatus.PASSED);
		assertThat(published.getPublishedAt()).isEqualTo(SUBMITTED.plusSeconds(1));
	}

	@Test
	@DisplayName("이미 보류 또는 반려된 Answer는 안전 검사 통과로 되돌릴 수 없다")
	void doesNotBypassHeldModerationState() {
		Answer held = Answer.submit(10L, 20L, "answer-key", "답변", "TEST", BigDecimal.ZERO, "NEAR", SUBMITTED, DISTANCE_M)
			.startSafetyCheck();
		Answer rejected = held.rejectSafety();

		assertThatThrownBy(rejected::markSafetyPassed).isInstanceOf(AnswerException.class);
	}

	@Test
	@DisplayName("DELETED Answer는 deletedAt을 반드시 가지며 임의 상태 전이를 거절한다")
	void enforcesDeletedTimestampAndTransitions() {
		Answer answer = Answer.submit(10L, 20L, "answer-key", "답변", "TEST", BigDecimal.ZERO, "NEAR", SUBMITTED, DISTANCE_M);

		assertThatThrownBy(() -> answer.publish(SUBMITTED.plusSeconds(1)))
			.isInstanceOf(AnswerException.class);
		Answer deleted = answer.delete(SUBMITTED.plusSeconds(2));
		assertThat(deleted.getStatus()).isEqualTo(AnswerStatus.DELETED);
		assertThat(deleted.getDeletedAt()).isEqualTo(SUBMITTED.plusSeconds(2));
	}

	@Test
	@DisplayName("editCount와 editedAt은 동치여야 한다 — 하나만 채워지면 거절된다")
	void requiresEditCountAndEditedAtToAgree() {
		assertThatThrownBy(() -> Answer.restore(1L, 10L, 20L, AnswerStatus.PUBLISHED, "key", "답변", "TEST",
			BigDecimal.ZERO, "NEAR", AnswerModerationStatus.PASSED, SUBMITTED, SUBMITTED, null,
			DISTANCE_M, SUBMITTED.plusSeconds(60), 0))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_EDIT_STATE);
		assertThatThrownBy(() -> Answer.restore(1L, 10L, 20L, AnswerStatus.PUBLISHED, "key", "답변", "TEST",
			BigDecimal.ZERO, "NEAR", AnswerModerationStatus.PASSED, SUBMITTED, SUBMITTED, null,
			DISTANCE_M, null, 1))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_EDIT_STATE);

		Answer edited = Answer.restore(1L, 10L, 20L, AnswerStatus.PUBLISHED, "key", "답변", "TEST",
			BigDecimal.ZERO, "NEAR", AnswerModerationStatus.PASSED, SUBMITTED, SUBMITTED, null,
			DISTANCE_M, SUBMITTED.plusSeconds(60), 1);
		assertThat(edited.getEditCount()).isEqualTo(1);
		assertThat(edited.getEditedAt()).isEqualTo(SUBMITTED.plusSeconds(60));
	}

	@Test
	@DisplayName("distanceM은 음수면 거절된다")
	void rejectsNegativeDistanceM() {
		assertThatThrownBy(() -> Answer.submit(10L, 20L, "answer-key", "답변", "TEST", BigDecimal.ZERO, "NEAR", SUBMITTED, -1L))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_VALUE_RANGE);
	}

	@Test
	@DisplayName("editCount는 음수면 거절된다")
	void rejectsNegativeEditCount() {
		assertThatThrownBy(() -> Answer.restore(1L, 10L, 20L, AnswerStatus.PUBLISHED, "key", "답변", "TEST",
			BigDecimal.ZERO, "NEAR", AnswerModerationStatus.PASSED, SUBMITTED, SUBMITTED, null,
			DISTANCE_M, SUBMITTED.plusSeconds(60), -1))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_VALUE_RANGE);
	}
}
