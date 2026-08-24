package com.dnd.qello.safety.web.response;

import java.time.Instant;

import com.dnd.qello.safety.domain.Report;

import io.swagger.v3.oas.annotations.media.Schema;

// 목록 항목. 상대 식별자·moderation_review 내부 판단은 담지 않는다(INV-RPT-005).
public record ReportSummaryResponse(
	@Schema(description = "신고의 식별자.") long reportId,
	@Schema(description = "신고에 선택한 상위 사유 코드.") String reasonCode,
	@Schema(description = "신고의 현재 처리 상태.") String status,
	@Schema(description = "신고가 접수된 시각.") Instant createdAt) {

	public static ReportSummaryResponse from(Report report) {
		return new ReportSummaryResponse(report.id(), report.reasonCode(), report.status().name(), report.createdAt());
	}
}
