package com.dnd.qello.safety.web.request;

import com.dnd.qello.safety.domain.ReportReason;
import com.dnd.qello.safety.domain.ReportSubReason;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 신고 접수 요청 본문. subReasonCode·detail·blockAuthor는 선택이다 — 조합
// 유효성은 ReportSubmission이 도메인 레벨에서 검증한다.
public record SubmitReportRequest(
	@NotNull(message = "reasonCode는 필수입니다")
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReportReason reasonCode,
	ReportSubReason subReasonCode,
	String detail,
	Boolean blockAuthor
) {

	public boolean shouldBlockAuthor() {
		return Boolean.TRUE.equals(blockAuthor);
	}
}
