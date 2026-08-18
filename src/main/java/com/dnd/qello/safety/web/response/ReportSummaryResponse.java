package com.dnd.qello.safety.web.response;

import java.time.Instant;

import com.dnd.qello.safety.domain.Report;

// 목록 항목. 상대 식별자·moderation_review 내부 판단은 담지 않는다(INV-RPT-005).
public record ReportSummaryResponse(long reportId, String reasonCode, String status, Instant createdAt) {

	public static ReportSummaryResponse from(Report report) {
		return new ReportSummaryResponse(report.id(), report.reasonCode(), report.status().name(), report.createdAt());
	}
}
