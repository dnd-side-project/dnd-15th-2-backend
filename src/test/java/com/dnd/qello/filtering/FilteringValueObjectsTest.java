package com.dnd.qello.filtering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.domain.FilterDecision;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.domain.ManualReviewBand;
import com.dnd.qello.filtering.domain.ManualReviewCase;
import com.dnd.qello.filtering.domain.ManualReviewPriorityDecision;
import com.dnd.qello.filtering.domain.ManualReviewPriorityReasonCode;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

/**
 * Created at: 2026-08-11T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-103-FILTERING-FOUNDATION-UNIT-011, UNIT-012, UNIT-014, UNIT-015
 */
class FilteringValueObjectsTest {

	private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

	@Test
	@DisplayName("FilterTarget.of는 targetVersion을 0으로 고정하고 양수가 아닌 targetId를 거절한다")
	void validatesFilterTarget() {
		FilterTarget target = FilterTarget.of(FilterTargetType.NICKNAME, 5L);
		assertThat(target.targetVersion()).isZero();

		assertThatThrownBy(() -> new FilterTarget(FilterTargetType.ANSWER, 0L, 0L))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);
		assertThatThrownBy(() -> new FilterTarget(FilterTargetType.ANSWER, 1L, -1L))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);
	}

	@Test
	@DisplayName("FilterDecision은 attemptGeneration·verdict·decidedAt을 필수로 검증한다")
	void validatesFilterDecision() {
		FilterDecision decision = FilterDecision.of(1L, 1, FilterVerdict.ALLOW, 10L, "text-moderation-2026-08", NOW);
		assertThat(decision.verdict()).isEqualTo(FilterVerdict.ALLOW);

		assertThatThrownBy(() -> FilterDecision.of(1L, 0, FilterVerdict.ALLOW, 10L, null, NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);
		assertThatThrownBy(() -> FilterDecision.of(1L, 1, null, 10L, null, NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.REQUIRED_VALUE_MISSING);
	}

	@Test
	@DisplayName("ManualReviewCase는 target·filterReleaseId·filterJobId를 필수로 검증한다")
	void validatesManualReviewCase() {
		FilterTarget target = FilterTarget.of(FilterTargetType.ANSWER, 1L);
		ManualReviewPriorityDecision decision =
			new ManualReviewPriorityDecision(ManualReviewBand.STANDARD, ManualReviewPriorityReasonCode.DEFAULT);
		assertThat(ManualReviewCase.open(target, 10L, 20L, decision, 0, "v1", NOW).target()).isEqualTo(target);

		assertThatThrownBy(() -> ManualReviewCase.open(target, 0L, 20L, decision, 0, "v1", NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);
		assertThatThrownBy(() -> ManualReviewCase.open(target, 10L, 0L, decision, 0, "v1", NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);
	}

	@Test
	@DisplayName("AppealCase는 targetType·targetId·filterDecisionId를 필수로 검증한다")
	void validatesAppealCase() {
		AppealCase appeal = AppealCase.file(FilterTargetType.ANSWER, 1L, 99L, NOW);
		assertThat(appeal.filterDecisionId()).isEqualTo(99L);

		assertThatThrownBy(() -> AppealCase.file(FilterTargetType.ANSWER, 1L, 0L, NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);
	}
}
