/**
 * Created at: 2026-08-17T19:35:00+09:00
 * Source scenario: TEST-PLAN-GH-112-AUTHOR-APPEAL-AND-MANUAL-RESTORE-UNIT-016 ~ UNIT-019
 */
package com.dnd.qello.filtering.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.audit.OperatorActionAuditRecorder;
import com.dnd.qello.filtering.domain.AppealAcceptanceReasonCode;
import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.domain.AppealCaseStatus;
import com.dnd.qello.filtering.domain.AppealDecision;
import com.dnd.qello.filtering.domain.FilterDecision;
import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.domain.OperatorReason;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.AppealCaseRepository;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class AppealCaseServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
	private static final Instant DECIDED_AT = Instant.parse("2026-03-01T00:00:00Z");
	private static final FilterTarget TARGET = FilterTarget.of(FilterTargetType.ANSWER, 11L);
	private static final long APPEAL_CASE_ID = 5L;
	private static final long FILTER_DECISION_ID = 99L;
	private static final long FILTER_JOB_ID = 20L;
	private static final long RELEASE_ID = 2L;
	private static final long APPELLANT_USER_ID = 7L;
	private static final long OPERATOR_USER_ID = 3L;

	private final AppealCaseRepository appealCaseRepository = mock(AppealCaseRepository.class);
	private final FilterDecisionRepository filterDecisionRepository = mock(FilterDecisionRepository.class);
	private final FilterJobRepository filterJobRepository = mock(FilterJobRepository.class);
	private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
	private final AppealTargetOwnershipChecker ownershipChecker = mock(AppealTargetOwnershipChecker.class);
	private final PublicationBlockChecker publicationBlockChecker = mock(PublicationBlockChecker.class);
	private final OperatorActionAuditRecorder auditRecorder = mock(OperatorActionAuditRecorder.class);
	private final AppealCaseService service = new AppealCaseService(appealCaseRepository, filterDecisionRepository,
		filterJobRepository, outboxEventRepository, ownershipChecker, publicationBlockChecker, auditRecorder,
		new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	@DisplayName("UNIT-016: NICKNAME 대상 이의제기는 지원하지 않는 대상 유형으로 거절한다")
	void rejectsUnsupportedTargetType() {
		assertThatThrownBy(() -> service.file(FilterTargetType.NICKNAME, 11L, FILTER_DECISION_ID, APPELLANT_USER_ID))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.UNSUPPORTED_APPEAL_TARGET);

		// 대상 유형 검사가 가장 먼저이므로 소유권 조회나 저장이 일어나지 않는다.
		verifyNoInteractions(ownershipChecker, appealCaseRepository, filterDecisionRepository);
	}

	@Test
	@DisplayName("UNIT-017: 작성자가 아니면 decision 존재 여부를 알려주지 않고 거절한다")
	void rejectsFilingByNonOwner() {
		when(ownershipChecker.isOwnedBy(FilterTargetType.ANSWER, TARGET.targetId(), APPELLANT_USER_ID))
			.thenReturn(false);

		assertThatThrownBy(() ->
			service.file(FilterTargetType.ANSWER, TARGET.targetId(), FILTER_DECISION_ID, APPELLANT_USER_ID))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.APPEAL_NOT_OWNED);

		verifyNoInteractions(filterDecisionRepository, appealCaseRepository);
	}

	@Test
	@DisplayName("UNIT-018: OVERTURN_HIDDEN이라도 공개 금지 사유가 남아 있으면 복원 콜백을 내지 않는다")
	void doesNotEmitRestoreCallbackWhenPublicationIsBlocked() {
		when(appealCaseRepository.findByIdForUpdate(APPEAL_CASE_ID)).thenReturn(Optional.of(openCase()));
		when(appealCaseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(publicationBlockChecker.findPublicationBlockReason(FilterTargetType.ANSWER, TARGET.targetId()))
			.thenReturn(Optional.of("ACCOUNT_BLOCKED"));

		AppealCase resolved = service.decide(APPEAL_CASE_ID, AppealDecision.OVERTURN_HIDDEN, OPERATOR_USER_ID, new OperatorReason("TEST", "테스트 근거"));

		assertThat(resolved.status()).isEqualTo(AppealCaseStatus.RESOLVED);
		assertThat(resolved.decision()).isEqualTo(AppealDecision.OVERTURN_HIDDEN);
		assertThat(resolved.restoreBlockedReasonCode()).isEqualTo("ACCOUNT_BLOCKED");
		verify(outboxEventRepository, never()).save(any());
	}

	@Test
	@DisplayName("UNIT-019: 공개 금지 사유가 없는 OVERTURN_HIDDEN은 APPEAL_CASE aggregate로 복원 콜백을 발행한다")
	void emitsRestoreCallbackWhenPublicationIsClear() {
		when(appealCaseRepository.findByIdForUpdate(APPEAL_CASE_ID)).thenReturn(Optional.of(openCase()));
		when(appealCaseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(publicationBlockChecker.findPublicationBlockReason(FilterTargetType.ANSWER, TARGET.targetId()))
			.thenReturn(Optional.empty());
		when(filterDecisionRepository.findById(FILTER_DECISION_ID)).thenReturn(Optional.of(blockDecision()));
		when(filterJobRepository.findById(FILTER_JOB_ID)).thenReturn(Optional.of(job()));

		AppealCase resolved = service.decide(APPEAL_CASE_ID, AppealDecision.OVERTURN_HIDDEN, OPERATOR_USER_ID, new OperatorReason("TEST", "테스트 근거"));

		assertThat(resolved.restoreBlockedReasonCode()).isNull();
		org.mockito.ArgumentCaptor<OutboxEvent> captor = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository).save(captor.capture());
		OutboxEvent event = captor.getValue();
		assertThat(event.aggregateType()).isEqualTo(OutboxAggregateType.APPEAL_CASE);
		assertThat(event.aggregateId()).isEqualTo(APPEAL_CASE_ID);
		assertThat(event.eventType()).isEqualTo(OutboxEventType.MODERATION_APPEAL_RESOLVED);
		assertThat(event.dedupKey()).isEqualTo("appeal-case:" + APPEAL_CASE_ID + ":APPEAL_RESOLVED");
	}

	@Test
	@DisplayName("UNIT-020: UPHOLD_HIDDEN은 공개 금지 사유를 조회하지도, 복원 콜백을 내지도 않는다")
	void upholdSkipsPublicationBlockCheckAndCallback() {
		when(appealCaseRepository.findByIdForUpdate(APPEAL_CASE_ID)).thenReturn(Optional.of(openCase()));
		when(appealCaseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AppealCase resolved = service.decide(APPEAL_CASE_ID, AppealDecision.UPHOLD_HIDDEN, OPERATOR_USER_ID, new OperatorReason("TEST", "테스트 근거"));

		assertThat(resolved.decision()).isEqualTo(AppealDecision.UPHOLD_HIDDEN);
		verifyNoInteractions(publicationBlockChecker);
		verify(outboxEventRepository, never()).save(any());
	}

	private static AppealCase openCase() {
		return AppealCase.restore(APPEAL_CASE_ID, FilterTargetType.ANSWER, TARGET.targetId(), FILTER_DECISION_ID,
			APPELLANT_USER_ID, AppealCaseStatus.OPEN, DECIDED_AT, DECIDED_AT.plus(Duration.ofDays(184)),
			AppealAcceptanceReasonCode.WITHIN_WINDOW, null, null, null, null, NOW);
	}

	private static FilterDecision blockDecision() {
		return FilterDecision.restore(FILTER_DECISION_ID, FILTER_JOB_ID, 1, FilterVerdict.BLOCK, RELEASE_ID,
			"text-moderation-2026-08", DECIDED_AT);
	}

	private static FilterJob job() {
		return FilterJob.create(TARGET, RELEASE_ID, "appeal-service-test-key", NOW.plusSeconds(600), DECIDED_AT);
	}
}
