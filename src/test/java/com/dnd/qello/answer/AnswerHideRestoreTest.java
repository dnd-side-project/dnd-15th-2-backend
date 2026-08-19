package com.dnd.qello.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;

/**
 * Created at: 2026-08-19T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-155-REPORT-SUPPRESSION-NOTIFICATIONS-UNIT-001 through UNIT-005
 */
class AnswerHideRestoreTest {

	private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

	@Test
	@DisplayName("PUBLISHED 답변을 hide하면 HIDDEN 상태가 되고 나머지 필드는 보존된다")
	void hidePublishedAnswerTransitionsToHidden() {
		Answer published = publishedAnswer();

		Answer hidden = published.hide(NOW.plusSeconds(10));

		assertThat(hidden.getStatus()).isEqualTo(AnswerStatus.HIDDEN);
		assertThat(hidden.getId()).isEqualTo(published.getId());
		assertThat(hidden.getBodyText()).isEqualTo(published.getBodyText());
		assertThat(hidden.getPublishedAt()).isEqualTo(published.getPublishedAt());
	}

	@Test
	@DisplayName("PUBLISHED가 아닌 답변은 hide할 수 없다")
	void hideRejectsNonPublishedAnswer() {
		Answer submitted = Answer.submit(1L, 2L, "key", "본문", "TEST", BigDecimal.valueOf(90), "NEAR", NOW, 100L);

		assertThatThrownBy(() -> submitted.hide(NOW.plusSeconds(10)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_ANSWER_STATUS);
	}

	@Test
	@DisplayName("HIDDEN 답변을 restore하면 PUBLISHED 상태로 돌아가고 최초 publishedAt을 보존한다")
	void restoreHiddenAnswerTransitionsToPublished() {
		Answer published = publishedAnswer();
		Instant hiddenAt = NOW.plusSeconds(10);
		Answer hidden = published.hide(hiddenAt);

		Answer restored = hidden.restore(NOW.plusSeconds(20));

		assertThat(restored.getStatus()).isEqualTo(AnswerStatus.PUBLISHED);
		assertThat(restored.getPublishedAt()).isEqualTo(published.getPublishedAt());
	}

	@Test
	@DisplayName("HIDDEN이 아닌 답변은 restore할 수 없다")
	void restoreRejectsNonHiddenAnswer() {
		Answer published = publishedAnswer();

		assertThatThrownBy(() -> published.restore(NOW.plusSeconds(10)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_ANSWER_STATUS);
	}

	@Test
	@DisplayName("hide 호출에 시각이 없으면 거절한다")
	void hideRequiresTimestamp() {
		Answer published = publishedAnswer();

		assertThatThrownBy(() -> published.hide(null))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.REQUIRED_VALUE_MISSING);
	}

	private static Answer publishedAnswer() {
		Answer submitted = Answer.submit(1L, 2L, "key", "본문", "TEST", BigDecimal.valueOf(90), "NEAR", NOW, 100L);
		Answer checking = submitted.startSafetyCheck().markSafetyPassed();
		return checking.publish(NOW.plusSeconds(5));
	}
}
