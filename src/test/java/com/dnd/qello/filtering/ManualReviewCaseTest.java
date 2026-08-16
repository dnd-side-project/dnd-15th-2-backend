/**
 * Created at: 2026-08-17T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-UNIT-001 through UNIT-006, UNIT-011, UNIT-012
 */
package com.dnd.qello.filtering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.domain.ManualReviewBand;
import com.dnd.qello.filtering.domain.ManualReviewCase;
import com.dnd.qello.filtering.domain.ManualReviewPriorityDecision;
import com.dnd.qello.filtering.domain.ManualReviewPriorityPolicy;
import com.dnd.qello.filtering.domain.ManualReviewPriorityReasonCode;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

class ManualReviewCaseTest {

	private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
	private static final FilterTarget TARGET = FilterTarget.of(FilterTargetType.ANSWER, 1L);
	private static final ManualReviewPriorityPolicy POLICY =
		new ManualReviewPriorityPolicy(3, Duration.ofHours(24), "v1");

	@Test
	@DisplayName("report signal이 threshold 이상이면 HIGH+REPORT_SIGNAL로 평가된다")
	void evaluatesHighBandWhenReportSignalMeetsThreshold() {
		ManualReviewPriorityDecision decision = ManualReviewCase.evaluatePriority(3, POLICY);

		assertThat(decision.band()).isEqualTo(ManualReviewBand.HIGH);
		assertThat(decision.reasonCode()).isEqualTo(ManualReviewPriorityReasonCode.REPORT_SIGNAL);
	}

	@Test
	@DisplayName("report signal이 threshold 미만이면 STANDARD+DEFAULT로 평가된다")
	void evaluatesStandardBandWhenReportSignalBelowThreshold() {
		ManualReviewPriorityDecision decision = ManualReviewCase.evaluatePriority(2, POLICY);

		assertThat(decision.band()).isEqualTo(ManualReviewBand.STANDARD);
		assertThat(decision.reasonCode()).isEqualTo(ManualReviewPriorityReasonCode.DEFAULT);
	}

	@Test
	@DisplayName("aging threshold를 넘으면 저장된 band가 STANDARD여도 effectiveBand는 HIGH다")
	void agedCaseIsEffectivelyHigh() {
		ManualReviewCase standardCase = openCase(ManualReviewBand.STANDARD, NOW);

		ManualReviewBand effective = standardCase.effectiveBand(NOW.plus(POLICY.agingThreshold()), POLICY);

		assertThat(effective).isEqualTo(ManualReviewBand.HIGH);
	}

	@Test
	@DisplayName("aging threshold 미만이면 effectiveBand는 저장된 band 그대로다")
	void notYetAgedCaseKeepsStoredBand() {
		ManualReviewCase standardCase = openCase(ManualReviewBand.STANDARD, NOW);

		ManualReviewBand effective =
			standardCase.effectiveBand(NOW.plus(POLICY.agingThreshold()).minusSeconds(1), POLICY);

		assertThat(effective).isEqualTo(ManualReviewBand.STANDARD);
	}

	@Test
	@DisplayName("effectiveBand 호출은 저장된 band 필드를 바꾸지 않는다(순수 함수)")
	void effectiveBandDoesNotMutateStoredBand() {
		ManualReviewCase standardCase = openCase(ManualReviewBand.STANDARD, NOW);

		standardCase.effectiveBand(NOW.plus(POLICY.agingThreshold()), POLICY);

		assertThat(standardCase.band()).isEqualTo(ManualReviewBand.STANDARD);
	}

	@Test
	@DisplayName("validatedReportSignalCount가 음수면 evaluatePriority가 예외를 던진다(호출 서비스의 fallback 대상)")
	void evaluatePriorityRejectsNegativeSignalCount() {
		assertThatThrownBy(() -> ManualReviewCase.evaluatePriority(-1, POLICY))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);
	}

	@Test
	@DisplayName("이미 RESOLVED된 case는 재종결이 거절된다")
	void rejectsResolvingAlreadyResolvedCase() {
		ManualReviewCase resolved = openCase(ManualReviewBand.STANDARD, NOW).resolve(FilterVerdict.ALLOW, 7L, NOW);

		assertThatThrownBy(() -> resolved.resolve(FilterVerdict.BLOCK, 7L, NOW.plusSeconds(1)))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_MANUAL_REVIEW_CASE_STATUS);
	}

	@Test
	@DisplayName("큐 정렬은 effectiveBand 내림차순, band 내에서는 createdAt 오름차순(FIFO)이다")
	void queueOrdersByEffectiveBandThenFifo() {
		Instant queryTime = NOW.plusSeconds(10);
		ManualReviewCase standardOld = openCase(ManualReviewBand.STANDARD, NOW.minusSeconds(30));
		ManualReviewCase standardNew = openCase(ManualReviewBand.STANDARD, NOW.minusSeconds(10));
		ManualReviewCase highCase = openCase(ManualReviewBand.HIGH, NOW.minusSeconds(5));

		List<ManualReviewCase> ordered = List.of(standardNew, highCase, standardOld).stream()
			.sorted(Comparator
				.comparing((ManualReviewCase c) -> c.effectiveBand(queryTime, POLICY) == ManualReviewBand.HIGH)
				.reversed()
				.thenComparing(ManualReviewCase::createdAt))
			.toList();

		assertThat(ordered).containsExactly(highCase, standardOld, standardNew);
	}

	private static ManualReviewCase openCase(ManualReviewBand band, Instant createdAt) {
		ManualReviewPriorityReasonCode reasonCode =
			band == ManualReviewBand.HIGH ? ManualReviewPriorityReasonCode.REPORT_SIGNAL
				: ManualReviewPriorityReasonCode.DEFAULT;
		return ManualReviewCase.open(
			TARGET, 10L, 20L, new ManualReviewPriorityDecision(band, reasonCode), 0, "v1", createdAt);
	}
}
