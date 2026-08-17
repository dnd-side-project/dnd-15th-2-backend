package com.dnd.qello.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportReason;
import com.dnd.qello.safety.domain.ReportStatus;
import com.dnd.qello.safety.domain.ReportSubReason;
import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

/**
 * Created at: 2026-08-17T20:15:00+09:00
 * Source scenario: TEST-PLAN-GH-153-REPORT-CASE-FOUNDATION-UNIT-001 through UNIT-010
 */
class ReportLifecycleTest {

	private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");

	@Test
	@DisplayName("ReportReason은 정확히 8종이다")
	void reportReasonHasExactlyEightValues() {
		assertThat(ReportReason.values()).extracting(Enum::name).containsExactlyInAnyOrder(
			"SEXUAL_CONTENT", "VIOLENCE_OR_THREAT", "HATE_OR_HARASSMENT", "PRIVACY_VIOLATION",
			"SPAM_OR_ADVERTISING", "IMPERSONATION", "ILLEGAL_OR_DANGEROUS", "OTHER");
	}

	@Test
	@DisplayName("ReportSubReason은 즉시 대응 항목 3종뿐이다")
	void reportSubReasonHasExactlyThreeValues() {
		assertThat(ReportSubReason.values()).extracting(Enum::name)
			.containsExactlyInAnyOrder("CSAM", "NCII", "CREDIBLE_THREAT");
	}

	@Test
	@DisplayName("attachToCase는 caseId를 설정하고 나머지 필드를 보존한다")
	void attachToCaseSetsCaseId() {
		Report report = Report.forAnswer(1L, 9L, "SPAM_OR_ADVERTISING", null, NOW);
		Report attached = report.attachToCase(42L);

		assertThat(attached.caseId()).isEqualTo(42L);
		assertThat(attached.reporterId()).isEqualTo(1L);
		assertThat(attached.answerId()).isEqualTo(9L);
		assertThat(attached.status()).isEqualTo(ReportStatus.RECEIVED);
	}

	@Test
	@DisplayName("이미 다른 사건에 연결된 신고를 재연결하면 거절한다")
	void attachToCaseRejectsRelinkingToDifferentCase() {
		Report attached = Report.forAnswer(1L, 9L, "SPAM_OR_ADVERTISING", null, NOW).attachToCase(42L);

		assertThatThrownBy(() -> attached.attachToCase(99L))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.REPORT_ALREADY_LINKED_TO_CASE);
	}

	@Test
	@DisplayName("같은 사건으로의 재연결은 멱등하게 처리한다")
	void attachToCaseIsIdempotentForSameCase() {
		Report attached = Report.forAnswer(1L, 9L, "SPAM_OR_ADVERTISING", null, NOW).attachToCase(42L);

		Report reattached = attached.attachToCase(42L);

		assertThat(reattached.caseId()).isEqualTo(42L);
	}

	@Test
	@DisplayName("requestMoreInfo는 종결이 아니다 — resolvedAt을 설정하지 않는다")
	void requestMoreInfoDoesNotResolve() {
		Report report = Report.forAnswer(1L, 9L, "SPAM_OR_ADVERTISING", null, NOW);

		Report moreInfo = report.requestMoreInfo(NOW.plusSeconds(10));

		assertThat(moreInfo.status()).isEqualTo(ReportStatus.MORE_INFO_REQUIRED);
		assertThat(moreInfo.resolvedAt()).isNull();
	}

	@Test
	@DisplayName("MORE_INFO_REQUIRED 상태에서도 정상적으로 종결할 수 있다 — 막다른 상태가 아니다")
	void moreInfoRequestedReportCanStillResolve() {
		Report moreInfo = Report.forAnswer(1L, 9L, "SPAM_OR_ADVERTISING", null, NOW)
			.requestMoreInfo(NOW.plusSeconds(10));

		Report resolved = moreInfo.resolve(ReportStatus.ACTIONED, NOW.plusSeconds(20));

		assertThat(resolved.status()).isEqualTo(ReportStatus.ACTIONED);
		assertThat(resolved.resolvedAt()).isEqualTo(NOW.plusSeconds(20));
	}

	@Test
	@DisplayName("resolve()는 더 이상 MORE_INFO_REQUIRED를 종결 상태로 받아들이지 않는다")
	void resolveRejectsMoreInfoRequiredAsTerminalStatus() {
		Report report = Report.forAnswer(1L, 9L, "SPAM_OR_ADVERTISING", null, NOW);

		assertThatThrownBy(() -> report.resolve(ReportStatus.MORE_INFO_REQUIRED, NOW.plusSeconds(1)))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.INVALID_REPORT_STATUS);
	}

	@Test
	@DisplayName("이미 MORE_INFO_REQUIRED인 신고에 추가 정보를 재요청하면 거절한다")
	void requestMoreInfoRejectsWhenAlreadyMoreInfoRequired() {
		Report moreInfo = Report.forAnswer(1L, 9L, "SPAM_OR_ADVERTISING", null, NOW)
			.requestMoreInfo(NOW.plusSeconds(10));

		assertThatThrownBy(() -> moreInfo.requestMoreInfo(NOW.plusSeconds(20)))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.INVALID_REPORT_STATUS);
	}

	@Test
	@DisplayName("caseId가 0 이하이면 신고 생성을 거절한다")
	void rejectsNonPositiveCaseId() {
		assertThatThrownBy(() -> new Report(null, 1L, null, null, 9L, "SPAM_OR_ADVERTISING", null,
			ReportStatus.RECEIVED, NOW, null, 0L, null))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.INVALID_ID);
	}
}
