/**
 * Created at: 2026-08-17T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-UNIT-009, UNIT-010
 */
package com.dnd.qello.filtering.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.audit.OperatorActionAuditRecorder;
import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterJobStatus;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.domain.ManualReviewBand;
import com.dnd.qello.filtering.domain.ManualReviewCase;
import com.dnd.qello.filtering.domain.ManualReviewPriorityDecision;
import com.dnd.qello.filtering.domain.ManualReviewPriorityReasonCode;
import com.dnd.qello.filtering.domain.OperatorReason;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterJobStatusHistoryRepository;
import com.dnd.qello.filtering.repository.ManualReviewCaseRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class ManualReviewDecisionServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
	private static final FilterTarget TARGET = FilterTarget.of(FilterTargetType.ANSWER, 1L);
	private static final Instant DEADLINE = NOW.plusSeconds(600);

	private final ManualReviewCaseRepository manualReviewCaseRepository = mock(ManualReviewCaseRepository.class);
	private final FilterJobRepository filterJobRepository = mock(FilterJobRepository.class);
	private final FilterJobStatusHistoryRepository filterJobStatusHistoryRepository =
		mock(FilterJobStatusHistoryRepository.class);
	private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
	private final OperatorActionAuditRecorder auditRecorder = mock(OperatorActionAuditRecorder.class);
	private final ManualReviewDecisionService service = new ManualReviewDecisionService(manualReviewCaseRepository,
		filterJobRepository, filterJobStatusHistoryRepository, outboxEventRepository, auditRecorder,
		new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	@DisplayName("job이 이미 자동 결과로 RESOLVED면 job은 건드리지 않고 case만 종료한다")
	void closesCaseWithoutTouchingAlreadyResolvedJob() {
		ManualReviewCase openCase = openCase();
		FilterJob resolvedByAutomation = automatedJob().applyAutomatedDecision(1, FilterVerdict.ALLOW, NOW);
		when(manualReviewCaseRepository.findById(1L)).thenReturn(java.util.Optional.of(openCase));
		when(filterJobRepository.findByIdForUpdate(20L)).thenReturn(java.util.Optional.of(resolvedByAutomation));
		when(manualReviewCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		ManualReviewCase result = service.decide(1L, FilterVerdict.BLOCK, 9L, new OperatorReason("TEST", "테스트 근거"));

		assertThat(result.resolvedVerdict()).isEqualTo(FilterVerdict.ALLOW);
		assertThat(result.resolvedByOperatorUserId()).isEqualTo(9L);
		verify(filterJobRepository, never()).save(any());
		verify(outboxEventRepository, never()).save(any());
	}

	@Test
	@DisplayName("job이 MANUAL_REVIEW_REQUIRED면 수동 결정을 적용하고 MODERATION_VERDICT_READY를 발행한다")
	void appliesManualDecisionAndPublishesVerdictReady() {
		ManualReviewCase openCase = openCase();
		FilterJob manualReviewRequired = FilterJob.restore(20L, TARGET, 10L, FilterJobStatus.MANUAL_REVIEW_REQUIRED,
			1, 1, false, null, "idem-key", DEADLINE, NOW, NOW);
		when(manualReviewCaseRepository.findById(1L)).thenReturn(java.util.Optional.of(openCase));
		when(filterJobRepository.findByIdForUpdate(20L)).thenReturn(java.util.Optional.of(manualReviewRequired));
		when(filterJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(manualReviewCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		ManualReviewCase result = service.decide(1L, FilterVerdict.BLOCK, 9L, new OperatorReason("TEST", "테스트 근거"));

		assertThat(result.resolvedVerdict()).isEqualTo(FilterVerdict.BLOCK);
		verify(filterJobRepository).save(argThatResolved());
		verify(filterJobStatusHistoryRepository).save(any());
		verify(outboxEventRepository).save(any());
	}

	private static org.mockito.ArgumentMatcher<FilterJob> resolvedMatcher() {
		return job -> job.status() == FilterJobStatus.RESOLVED && job.resolvedVerdict() == FilterVerdict.BLOCK;
	}

	private static FilterJob argThatResolved() {
		return org.mockito.ArgumentMatchers.argThat(resolvedMatcher());
	}

	private static ManualReviewCase openCase() {
		return ManualReviewCase.open(TARGET, 10L, 20L,
			new ManualReviewPriorityDecision(ManualReviewBand.STANDARD, ManualReviewPriorityReasonCode.DEFAULT), 0,
			"v1", NOW);
	}

	private static FilterJob automatedJob() {
		return FilterJob.create(TARGET, 10L, "idem-key", DEADLINE, NOW);
	}
}
