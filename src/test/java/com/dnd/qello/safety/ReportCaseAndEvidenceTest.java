package com.dnd.qello.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.safety.domain.ModerationDecision;
import com.dnd.qello.safety.domain.ReportCase;
import com.dnd.qello.safety.domain.ReportCaseEvent;
import com.dnd.qello.safety.domain.ReportCaseEventType;
import com.dnd.qello.safety.domain.ReportCaseQueue;
import com.dnd.qello.safety.domain.ReportCaseSeverity;
import com.dnd.qello.safety.domain.ReportCaseStatus;
import com.dnd.qello.safety.domain.ReportContentHasher;
import com.dnd.qello.safety.domain.ReportContentSnapshot;
import com.dnd.qello.safety.domain.ReportTargetType;
import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

/**
 * Created at: 2026-08-17T20:15:00+09:00
 * Source scenario: TEST-PLAN-GH-153-REPORT-CASE-FOUNDATION-UNIT-011 through UNIT-021
 */
class ReportCaseAndEvidenceTest {

	private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");

	@Test
	@DisplayName("ReportCase.open은 NORMAL/STANDARD로 OPEN 사건을 연다")
	void opensCaseWithDefaultSeverityAndQueue() {
		ReportCase reportCase = ReportCase.open(null, null, 9L, NOW);

		assertThat(reportCase.status()).isEqualTo(ReportCaseStatus.OPEN);
		assertThat(reportCase.severity()).isEqualTo(ReportCaseSeverity.NORMAL);
		assertThat(reportCase.queue()).isEqualTo(ReportCaseQueue.STANDARD);
		assertThat(reportCase.decision()).isNull();
	}

	@Test
	@DisplayName("사건 대상이 정확히 하나가 아니면 거절한다")
	void rejectsCaseWithoutExactlyOneTarget() {
		assertThatThrownBy(() -> ReportCase.open(null, null, null, NOW))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.INVALID_REPORT_TARGET);

		assertThatThrownBy(() -> ReportCase.open(1L, 2L, null, NOW))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.INVALID_REPORT_TARGET);
	}

	@Test
	@DisplayName("startReview는 OPEN 사건을 UNDER_REVIEW로 전이한다")
	void startReviewTransitionsToUnderReview() {
		ReportCase reportCase = ReportCase.open(null, null, 9L, NOW).startReview();

		assertThat(reportCase.status()).isEqualTo(ReportCaseStatus.UNDER_REVIEW);
	}

	@Test
	@DisplayName("resolve는 사건을 종결하고 판정·종결 시각을 기록한다")
	void resolveClosesCaseWithDecision() {
		ReportCase resolved = ReportCase.open(null, null, 9L, NOW).startReview()
			.resolve(ModerationDecision.ACTIONED, NOW.plusSeconds(10));

		assertThat(resolved.status()).isEqualTo(ReportCaseStatus.RESOLVED);
		assertThat(resolved.decision()).isEqualTo(ModerationDecision.ACTIONED);
		assertThat(resolved.resolvedAt()).isEqualTo(NOW.plusSeconds(10));
	}

	@Test
	@DisplayName("종결된 사건은 재종결할 수 없다 — 재발은 새 사건이다")
	void resolvedCaseCannotBeResolvedAgain() {
		ReportCase resolved = ReportCase.open(null, null, 9L, NOW)
			.resolve(ModerationDecision.NO_VIOLATION, NOW.plusSeconds(10));

		assertThatThrownBy(() -> resolved.resolve(ModerationDecision.ACTIONED, NOW.plusSeconds(20)))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.REPORT_CASE_ALREADY_RESOLVED);
	}

	@Test
	@DisplayName("종결된 사건은 재개방할 수 없다")
	void resolvedCaseCannotStartReviewAgain() {
		ReportCase resolved = ReportCase.open(null, null, 9L, NOW)
			.resolve(ModerationDecision.NO_VIOLATION, NOW.plusSeconds(10));

		assertThatThrownBy(resolved::startReview)
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.REPORT_CASE_ALREADY_RESOLVED);
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
