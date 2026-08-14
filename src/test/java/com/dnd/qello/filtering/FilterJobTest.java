package com.dnd.qello.filtering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterJobStatus;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

/**
 * Created at: 2026-08-11T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-103-FILTERING-FOUNDATION-UNIT-001 through UNIT-010
 */
class FilterJobTest {

	private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
	private static final Instant DEADLINE = NOW.plusSeconds(600);
	private static final FilterTarget TARGET = FilterTarget.of(FilterTargetType.ANSWER, 1L);

	@Test
	@DisplayName("새로 생성된 job은 AUTOMATED 상태·1세대·비수동으로 시작한다")
	void createsInInitialState() {
		FilterJob job = FilterJob.create(TARGET, 10L, "idem-key", DEADLINE, NOW);

		assertThat(job.status()).isEqualTo(FilterJobStatus.AUTOMATED);
		assertThat(job.attemptGeneration()).isEqualTo(1);
		assertThat(job.manuallyResolved()).isFalse();
		assertThat(job.resolvedVerdict()).isNull();
	}

	@Test
	@DisplayName("현재 세대와 일치하는 자동 결과는 job을 RESOLVED로 전이시킨다")
	void appliesAutomatedDecisionAtCurrentGeneration() {
		FilterJob job = FilterJob.create(TARGET, 10L, "idem-key", DEADLINE, NOW);

		FilterJob resolved = job.applyAutomatedDecision(1, FilterVerdict.ALLOW, NOW.plusSeconds(1));

		assertThat(resolved.status()).isEqualTo(FilterJobStatus.RESOLVED);
		assertThat(resolved.resolvedVerdict()).isEqualTo(FilterVerdict.ALLOW);
		assertThat(resolved.manuallyResolved()).isFalse();
	}

	@Test
	@DisplayName("오래된 attempt generation의 자동 결과는 거절되고 job 상태를 바꾸지 않는다")
	void rejectsStaleAttemptGeneration() {
		FilterJob job = FilterJob.create(TARGET, 10L, "idem-key", DEADLINE, NOW).advanceAttemptGeneration(NOW.plusSeconds(1));

		assertThat(job.attemptGeneration()).isEqualTo(2);
		assertThatThrownBy(() -> job.applyAutomatedDecision(1, FilterVerdict.ALLOW, NOW.plusSeconds(2)))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.STALE_ATTEMPT_GENERATION);
	}

	@Test
	@DisplayName("수동으로 이미 종결된 job에는 자동 결과가 적용되지 않는다")
	void rejectsAutomatedDecisionAfterManualResolution() {
		FilterJob job = FilterJob.create(TARGET, 10L, "idem-key", DEADLINE, NOW)
			.applyManualDecision(FilterVerdict.BLOCK, NOW.plusSeconds(1));

		assertThatThrownBy(() -> job.applyAutomatedDecision(1, FilterVerdict.ALLOW, NOW.plusSeconds(2)))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.ALREADY_MANUALLY_RESOLVED);
	}

	@Test
	@DisplayName("이미 RESOLVED된 job에 또 다른 자동 결과를 적용할 수 없다")
	void rejectsAutomatedDecisionOnAlreadyResolvedJob() {
		FilterJob resolved = FilterJob.create(TARGET, 10L, "idem-key", DEADLINE, NOW)
			.applyAutomatedDecision(1, FilterVerdict.ALLOW, NOW.plusSeconds(1));

		assertThatThrownBy(() -> resolved.applyAutomatedDecision(1, FilterVerdict.BLOCK, NOW.plusSeconds(2)))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_JOB_STATUS);
	}

	@Test
	@DisplayName("AUTOMATED 상태의 job만 retry 소진으로 전이할 수 있다")
	void exhaustsRetriesOnlyFromAutomated() {
		FilterJob exhausted = FilterJob.create(TARGET, 10L, "idem-key", DEADLINE, NOW).exhaustRetries(NOW.plusSeconds(1));
		assertThat(exhausted.status()).isEqualTo(FilterJobStatus.RETRY_EXHAUSTED);

		assertThatThrownBy(() -> exhausted.exhaustRetries(NOW.plusSeconds(2)))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_JOB_STATUS);
	}

	@Test
	@DisplayName("수동 검토 개설은 멱등하고, RETRY_EXHAUSTED가 아닌 상태에서는 거절된다")
	void opensManualReviewIdempotently() {
		FilterJob exhausted = FilterJob.create(TARGET, 10L, "idem-key", DEADLINE, NOW).exhaustRetries(NOW.plusSeconds(1));

		FilterJob opened = exhausted.openManualReview(NOW.plusSeconds(2));
		FilterJob openedAgain = opened.openManualReview(NOW.plusSeconds(3));

		assertThat(opened.status()).isEqualTo(FilterJobStatus.MANUAL_REVIEW_REQUIRED);
		assertThat(openedAgain).isEqualTo(opened);

		FilterJob fresh = FilterJob.create(TARGET, 10L, "idem-key-2", DEADLINE, NOW);
		assertThatThrownBy(() -> fresh.openManualReview(NOW.plusSeconds(1)))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_JOB_STATUS);
	}

	@Test
	@DisplayName("수동 결정 전 도착한 자동 결과는 MANUAL_REVIEW_REQUIRED 상태의 job을 종료할 수 있다")
	void automatedDecisionResolvesJobAwaitingManualReview() {
		FilterJob awaiting = FilterJob.create(TARGET, 10L, "idem-key", DEADLINE, NOW)
			.exhaustRetries(NOW.plusSeconds(1))
			.openManualReview(NOW.plusSeconds(2));

		FilterJob resolved = awaiting.applyAutomatedDecision(1, FilterVerdict.ALLOW, NOW.plusSeconds(3));

		assertThat(resolved.status()).isEqualTo(FilterJobStatus.RESOLVED);
		assertThat(resolved.manuallyResolved()).isFalse();
	}

	@Test
	@DisplayName("수동 결정은 현재 job 상태와 무관하게 authoritative하게 적용되지만 중복 적용은 거절된다")
	void manualDecisionIsAuthoritativeButNotDuplicated() {
		FilterJob resolved = FilterJob.create(TARGET, 10L, "idem-key", DEADLINE, NOW)
			.exhaustRetries(NOW.plusSeconds(1))
			.openManualReview(NOW.plusSeconds(2))
			.applyManualDecision(FilterVerdict.BLOCK, NOW.plusSeconds(3));

		assertThat(resolved.status()).isEqualTo(FilterJobStatus.RESOLVED);
		assertThat(resolved.manuallyResolved()).isTrue();
		assertThat(resolved.resolvedVerdict()).isEqualTo(FilterVerdict.BLOCK);

		assertThatThrownBy(() -> resolved.applyManualDecision(FilterVerdict.ALLOW, NOW.plusSeconds(4)))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.ALREADY_MANUALLY_RESOLVED);
	}

	@Test
	@DisplayName("attempt generation 전환은 세대를 올리고 상태를 AUTOMATED로 재설정하며, RESOLVED job은 전환할 수 없다")
	void advancesAttemptGenerationOnlyFromAutomated() {
		FilterJob migrated = FilterJob.create(TARGET, 10L, "idem-key", DEADLINE, NOW).advanceAttemptGeneration(NOW.plusSeconds(1));

		assertThat(migrated.attemptGeneration()).isEqualTo(2);
		assertThat(migrated.status()).isEqualTo(FilterJobStatus.AUTOMATED);

		FilterJob resolved = migrated.applyAutomatedDecision(2, FilterVerdict.ALLOW, NOW.plusSeconds(2));
		assertThatThrownBy(() -> resolved.advanceAttemptGeneration(NOW.plusSeconds(3)))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_JOB_STATUS);
	}

	@Test
	@DisplayName("RESOLVED 상태와 resolvedVerdict의 불일치, manuallyResolved와 상태의 불일치는 복원 시점에 거절된다")
	void rejectsInconsistentRestoredState() {
		assertThatThrownBy(() -> FilterJob.restore(1L, TARGET, 10L, FilterJobStatus.RESOLVED, 1, false, null,
			"idem-key", DEADLINE, NOW, NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_JOB_STATUS);

		assertThatThrownBy(() -> FilterJob.restore(1L, TARGET, 10L, FilterJobStatus.AUTOMATED, 1, true, null,
			"idem-key", DEADLINE, NOW, NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_JOB_STATUS);
	}
}
