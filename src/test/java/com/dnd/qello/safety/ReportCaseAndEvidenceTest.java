package com.dnd.qello.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.dnd.qello.safety.domain.AutoSuppressionPolicy;
import com.dnd.qello.safety.domain.ModerationDecision;
import com.dnd.qello.safety.domain.ReportCase;
import com.dnd.qello.safety.domain.ReportCaseEvent;
import com.dnd.qello.safety.domain.ReportCaseEventType;
import com.dnd.qello.safety.domain.ReportCaseQueue;
import com.dnd.qello.safety.domain.ReportCaseSeverity;
import com.dnd.qello.safety.domain.ReportCaseStatus;
import com.dnd.qello.safety.domain.ReportContentHasher;
import com.dnd.qello.safety.domain.ReportContentSnapshot;
import com.dnd.qello.safety.domain.ReportSubReason;
import com.dnd.qello.safety.domain.ReportTargetType;
import com.dnd.qello.safety.domain.SlaPolicy;
import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

/**
 * Created at: 2026-08-17T20:15:00+09:00
 * Source scenario: TEST-PLAN-GH-153-REPORT-CASE-FOUNDATION-UNIT-011 through UNIT-021,
 * TEST-PLAN-GH-156-REPORT-SEVERITY-OPERATOR-REVIEW-UNIT-001 through UNIT-013
 */
class ReportCaseAndEvidenceTest {

	private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");
	private static final Instant SLA_DUE_AT = NOW.plus(Duration.ofDays(3));

	@Test
	@DisplayName("CSAM·NCII·CREDIBLE_THREAT는 CRITICAL 심각도로 산출된다")
	void criticalSubReasonsProduceCriticalSeverity() {
		assertThat(ReportCaseSeverity.of(ReportSubReason.CSAM)).isEqualTo(ReportCaseSeverity.CRITICAL);
		assertThat(ReportCaseSeverity.of(ReportSubReason.NCII)).isEqualTo(ReportCaseSeverity.CRITICAL);
		assertThat(ReportCaseSeverity.of(ReportSubReason.CREDIBLE_THREAT)).isEqualTo(ReportCaseSeverity.CRITICAL);
	}

	@Test
	@DisplayName("subReason이 없으면 NORMAL 심각도로 산출된다")
	void nullSubReasonProducesNormalSeverity() {
		assertThat(ReportCaseSeverity.of(null)).isEqualTo(ReportCaseSeverity.NORMAL);
	}

	@Test
	@DisplayName("심각도는 CRITICAL→URGENT, NORMAL→STANDARD로 큐에 라우팅된다")
	void severityRoutesToQueue() {
		assertThat(ReportCaseQueue.of(ReportCaseSeverity.CRITICAL)).isEqualTo(ReportCaseQueue.URGENT);
		assertThat(ReportCaseQueue.of(ReportCaseSeverity.NORMAL)).isEqualTo(ReportCaseQueue.STANDARD);
	}

	@Test
	@DisplayName("SlaPolicy는 0 또는 음수 Duration을 거절한다")
	void slaPolicyRejectsNonPositiveDuration() {
		assertThatThrownBy(() -> new SlaPolicy(Duration.ZERO, Duration.ofHours(4)))
			.isInstanceOf(SafetyException.class);
		assertThatThrownBy(() -> new SlaPolicy(Duration.ofHours(72), Duration.ofHours(-1)))
			.isInstanceOf(SafetyException.class);
	}

	@Test
	@DisplayName("SlaPolicy.of는 큐별 Duration을 돌려준다")
	void slaPolicyReturnsDurationPerQueue() {
		SlaPolicy policy = new SlaPolicy(Duration.ofHours(72), Duration.ofHours(4));

		assertThat(policy.of(ReportCaseQueue.STANDARD)).isEqualTo(Duration.ofHours(72));
		assertThat(policy.of(ReportCaseQueue.URGENT)).isEqualTo(Duration.ofHours(4));
	}

	@Test
	@DisplayName("AutoSuppressionPolicy는 0 또는 음수 임계값을 거절한다")
	void autoSuppressionPolicyRejectsNonPositiveThreshold() {
		assertThatThrownBy(() -> new AutoSuppressionPolicy(0)).isInstanceOf(SafetyException.class);
		assertThatThrownBy(() -> new AutoSuppressionPolicy(-1)).isInstanceOf(SafetyException.class);
	}

	@Test
	@DisplayName("ReportCase.open은 호출자가 넘긴 severity/queue/slaDueAt으로 OPEN 사건을 연다")
	void opensCaseWithGivenSeverityAndQueue() {
		ReportCase reportCase = open(9L);

		assertThat(reportCase.status()).isEqualTo(ReportCaseStatus.OPEN);
		assertThat(reportCase.severity()).isEqualTo(ReportCaseSeverity.NORMAL);
		assertThat(reportCase.queue()).isEqualTo(ReportCaseQueue.STANDARD);
		assertThat(reportCase.slaDueAt()).isEqualTo(SLA_DUE_AT);
		assertThat(reportCase.linkedManualReviewCaseId()).isNull();
		assertThat(reportCase.decision()).isNull();
	}

	@Test
	@DisplayName("CRITICAL severity/URGENT queue로도 열 수 있다")
	void opensCaseWithCriticalSeverity() {
		ReportCase reportCase = ReportCase.open(null, null, 9L,
			ReportCaseSeverity.CRITICAL, ReportCaseQueue.URGENT, NOW, SLA_DUE_AT);

		assertThat(reportCase.severity()).isEqualTo(ReportCaseSeverity.CRITICAL);
		assertThat(reportCase.queue()).isEqualTo(ReportCaseQueue.URGENT);
	}

	@Test
	@DisplayName("사건 대상이 정확히 하나가 아니면 거절한다")
	void rejectsCaseWithoutExactlyOneTarget() {
		assertThatThrownBy(() -> open(null, null, null))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.INVALID_REPORT_TARGET);

		assertThatThrownBy(() -> open(1L, 2L, null))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.INVALID_REPORT_TARGET);
	}

	@Test
	@DisplayName("startReview는 OPEN 사건을 UNDER_REVIEW로 전이한다")
	void startReviewTransitionsToUnderReview() {
		ReportCase reportCase = open(9L).startReview();

		assertThat(reportCase.status()).isEqualTo(ReportCaseStatus.UNDER_REVIEW);
	}

	@Test
	@DisplayName("resolve는 사건을 종결하고 판정·종결 시각을 기록한다")
	void resolveClosesCaseWithDecision() {
		ReportCase resolved = open(9L).startReview()
			.resolve(ModerationDecision.ACTIONED, NOW.plusSeconds(10));

		assertThat(resolved.status()).isEqualTo(ReportCaseStatus.RESOLVED);
		assertThat(resolved.decision()).isEqualTo(ModerationDecision.ACTIONED);
		assertThat(resolved.resolvedAt()).isEqualTo(NOW.plusSeconds(10));
	}

	@Test
	@DisplayName("종결된 사건은 재종결할 수 없다 — 재발은 새 사건이다")
	void resolvedCaseCannotBeResolvedAgain() {
		ReportCase resolved = open(9L)
			.resolve(ModerationDecision.NO_VIOLATION, NOW.plusSeconds(10));

		assertThatThrownBy(() -> resolved.resolve(ModerationDecision.ACTIONED, NOW.plusSeconds(20)))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.REPORT_CASE_ALREADY_RESOLVED);
	}

	@Test
	@DisplayName("종결된 사건은 재개방할 수 없다")
	void resolvedCaseCannotStartReviewAgain() {
		ReportCase resolved = open(9L)
			.resolve(ModerationDecision.NO_VIOLATION, NOW.plusSeconds(10));

		assertThatThrownBy(resolved::startReview)
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.REPORT_CASE_ALREADY_RESOLVED);
	}

	@Test
	@DisplayName("escalate는 OPEN·UNDER_REVIEW 사건을 CRITICAL/URGENT로 승격하고 slaDueAt을 갱신한다")
	void escalateRaisesSeverityAndQueue() {
		Instant urgentSla = NOW.plus(Duration.ofHours(4));

		ReportCase escalated = open(9L).escalate(NOW.plusSeconds(10), urgentSla);

		assertThat(escalated.severity()).isEqualTo(ReportCaseSeverity.CRITICAL);
		assertThat(escalated.queue()).isEqualTo(ReportCaseQueue.URGENT);
		assertThat(escalated.slaDueAt()).isEqualTo(urgentSla);
		assertThat(escalated.status()).isEqualTo(ReportCaseStatus.OPEN);
	}

	@Test
	@DisplayName("종결된 사건은 승격할 수 없다")
	void resolvedCaseCannotEscalate() {
		ReportCase resolved = open(9L).resolve(ModerationDecision.NO_VIOLATION, NOW.plusSeconds(10));

		assertThatThrownBy(() -> resolved.escalate(NOW.plusSeconds(20), NOW.plusSeconds(30)))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.REPORT_CASE_ALREADY_RESOLVED);
	}

	@Test
	@DisplayName("deescalate는 CRITICAL/URGENT 사건을 NORMAL/STANDARD로 강등한다")
	void deescalateLowersSeverityAndQueue() {
		Instant standardSla = NOW.plus(Duration.ofDays(3));
		ReportCase escalated = ReportCase.open(null, null, 9L,
			ReportCaseSeverity.CRITICAL, ReportCaseQueue.URGENT, NOW, NOW.plus(Duration.ofHours(4)));

		ReportCase deescalated = escalated.deescalate(NOW.plusSeconds(10), standardSla);

		assertThat(deescalated.severity()).isEqualTo(ReportCaseSeverity.NORMAL);
		assertThat(deescalated.queue()).isEqualTo(ReportCaseQueue.STANDARD);
		assertThat(deescalated.slaDueAt()).isEqualTo(standardSla);
	}

	@Test
	@DisplayName("requestMoreInfo는 상태를 바꾸지 않고 decision만 MORE_INFO_REQUIRED로 세팅한다")
	void requestMoreInfoKeepsStatusButSetsDecision() {
		ReportCase underReview = open(9L).startReview();

		ReportCase moreInfo = underReview.requestMoreInfo(NOW.plusSeconds(10));

		assertThat(moreInfo.status()).isEqualTo(ReportCaseStatus.UNDER_REVIEW);
		assertThat(moreInfo.decision()).isEqualTo(ModerationDecision.MORE_INFO_REQUIRED);
		assertThat(moreInfo.resolvedAt()).isNull();
	}

	@Test
	@DisplayName("종결된 사건에는 추가 정보를 요청할 수 없다")
	void resolvedCaseCannotRequestMoreInfo() {
		ReportCase resolved = open(9L).resolve(ModerationDecision.NO_VIOLATION, NOW.plusSeconds(10));

		assertThatThrownBy(() -> resolved.requestMoreInfo(NOW.plusSeconds(20)))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.REPORT_CASE_ALREADY_RESOLVED);
	}

	@Test
	@DisplayName("withLinkedManualReviewCase는 상관관계 id만 기록하고 나머지 상태는 보존한다")
	void withLinkedManualReviewCaseRecordsCorrelationOnly() {
		ReportCase linked = open(9L).withLinkedManualReviewCase(42L);

		assertThat(linked.linkedManualReviewCaseId()).isEqualTo(42L);
		assertThat(linked.status()).isEqualTo(ReportCaseStatus.OPEN);
		assertThat(linked.severity()).isEqualTo(ReportCaseSeverity.NORMAL);
	}

	private static ReportCase open(Long answerId) {
		return open(null, null, answerId);
	}

	private static ReportCase open(Long targetUserId, Long directionPostId, Long answerId) {
		return ReportCase.open(targetUserId, directionPostId, answerId,
			ReportCaseSeverity.NORMAL, ReportCaseQueue.STANDARD, NOW, SLA_DUE_AT);
	}

	@Test
	@DisplayName("content hash는 media key 순서와 무관하다")
	void contentHashIsOrderIndependent() {
		String hashA = ReportContentHasher.hash("본문", List.of("media-2", "media-1"));
		String hashB = ReportContentHasher.hash("본문", List.of("media-1", "media-2"));

		assertThat(hashA).isEqualTo(hashB);
	}

	@Test
	@DisplayName("본문이 다르면 content hash도 다르다")
	void contentHashDiffersForDifferentBody() {
		String hashA = ReportContentHasher.hash("본문 A", List.of("media-1"));
		String hashB = ReportContentHasher.hash("본문 B", List.of("media-1"));

		assertThat(hashA).isNotEqualTo(hashB);
	}

	@Test
	@DisplayName("스냅샷의 editCount가 음수이면 거절한다")
	void rejectsNegativeEditCount() {
		assertThatThrownBy(() -> ReportContentSnapshot.capture(1L, NOW, ReportTargetType.ANSWER, 9L, 5L,
			"본문", List.of(), -1, NOW))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.INVALID_SNAPSHOT_EDIT_COUNT);
	}

	@Test
	@DisplayName("ReportCaseEvent.of는 caseId·eventType·occurredAt으로 이벤트를 만든다")
	void createsReportCaseEvent() {
		ReportCaseEvent event = ReportCaseEvent.of(7L, ReportCaseEventType.CASE_OPENED, NOW);

		assertThat(event.caseId()).isEqualTo(7L);
		assertThat(event.eventType()).isEqualTo(ReportCaseEventType.CASE_OPENED);
		assertThat(event.occurredAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("caseId가 0 이하이면 이벤트 생성을 거절한다")
	void rejectsNonPositiveCaseIdForEvent() {
		assertThatThrownBy(() -> ReportCaseEvent.of(0L, ReportCaseEventType.CASE_OPENED, NOW))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.INVALID_ID);
	}
}
