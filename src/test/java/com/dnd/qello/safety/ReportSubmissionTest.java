package com.dnd.qello.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.safety.domain.ReportReason;
import com.dnd.qello.safety.domain.ReportSubReason;
import com.dnd.qello.safety.domain.ReportSubmission;
import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

/**
 * Created at: 2026-08-18T22:10:00+09:00
 * Source scenario: TEST-PLAN-GH-154-REPORT-INTAKE-API-UNIT-001 through UNIT-008,
 * TEST-PLAN-GH-157-REPORT-LEGAL-PRODUCTION-GATE-UNIT-001
 */
class ReportSubmissionTest {

	@Test
	@DisplayName("ILLEGAL_OR_DANGEROUS + SELF_HARM_RISK는 정상 생성된다(#157)")
	void acceptsIllegalOrDangerousWithSelfHarmRisk() {
		ReportSubmission submission =
			new ReportSubmission(ReportReason.ILLEGAL_OR_DANGEROUS, ReportSubReason.SELF_HARM_RISK, null);

		assertThat(submission.subReason()).isEqualTo(ReportSubReason.SELF_HARM_RISK);
	}

	@Test
	@DisplayName("SEXUAL_CONTENT + SELF_HARM_RISK처럼 잘못된 조합은 거절한다(#157)")
	void rejectsSelfHarmRiskWithMismatchedReason() {
		assertThatThrownBy(() ->
			new ReportSubmission(ReportReason.SEXUAL_CONTENT, ReportSubReason.SELF_HARM_RISK, null))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.INVALID_REPORT_SUB_REASON);
	}

	@Test
	@DisplayName("SEXUAL_CONTENT + CSAM은 정상 생성된다")
	void acceptsSexualContentWithCsam() {
		ReportSubmission submission = new ReportSubmission(ReportReason.SEXUAL_CONTENT, ReportSubReason.CSAM, null);

		assertThat(submission.subReason()).isEqualTo(ReportSubReason.CSAM);
	}

	@Test
	@DisplayName("VIOLENCE_OR_THREAT + CREDIBLE_THREAT은 정상 생성된다")
	void acceptsViolenceWithCredibleThreat() {
		ReportSubmission submission =
			new ReportSubmission(ReportReason.VIOLENCE_OR_THREAT, ReportSubReason.CREDIBLE_THREAT, null);

		assertThat(submission.subReason()).isEqualTo(ReportSubReason.CREDIBLE_THREAT);
	}

	@Test
	@DisplayName("SPAM_OR_ADVERTISING + CSAM처럼 잘못된 조합은 거절한다")
	void rejectsMismatchedPairing() {
		assertThatThrownBy(() ->
			new ReportSubmission(ReportReason.SPAM_OR_ADVERTISING, ReportSubReason.CSAM, null))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.INVALID_REPORT_SUB_REASON);
	}

	@Test
	@DisplayName("하위 사유 없이 HATE_OR_HARASSMENT만 선택해도 정상 생성된다")
	void acceptsReasonWithoutSubReason() {
		ReportSubmission submission = new ReportSubmission(ReportReason.HATE_OR_HARASSMENT, null, null);

		assertThat(submission.subReason()).isNull();
	}

	@Test
	@DisplayName("OTHER 사유는 설명이 없으면 거절한다")
	void rejectsOtherWithoutDetail() {
		assertThatThrownBy(() -> new ReportSubmission(ReportReason.OTHER, null, null))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.INVALID_REPORT_DETAIL);

		assertThatThrownBy(() -> new ReportSubmission(ReportReason.OTHER, null, "   "))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.INVALID_REPORT_DETAIL);
	}

	@Test
	@DisplayName("OTHER 사유는 설명이 있으면 정상 생성된다")
	void acceptsOtherWithDetail() {
		ReportSubmission submission = new ReportSubmission(ReportReason.OTHER, null, "구체적인 설명입니다");

		assertThat(submission.detail()).isEqualTo("구체적인 설명입니다");
	}

	@Test
	@DisplayName("OTHER가 아니면 설명 없이도 정상 생성된다")
	void detailIsOptionalForNonOtherReasons() {
		ReportSubmission submission = new ReportSubmission(ReportReason.SPAM_OR_ADVERTISING, null, null);

		assertThat(submission.detail()).isNull();
	}

	@Test
	@DisplayName("설명이 허용 길이를 초과하면 거절한다")
	void rejectsDetailExceedingMaxLength() {
		String tooLong = "a".repeat(501);

		assertThatThrownBy(() -> new ReportSubmission(ReportReason.OTHER, null, tooLong))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.INVALID_REPORT_DETAIL);
	}
}
