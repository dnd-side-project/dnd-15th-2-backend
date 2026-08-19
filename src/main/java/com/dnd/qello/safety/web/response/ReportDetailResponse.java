package com.dnd.qello.safety.web.response;

import java.time.Instant;

import com.dnd.qello.safety.domain.Report;

// 단건 조회. 상대 식별자·moderation_review 내부 판단은 담지 않는다(INV-RPT-005).
public record ReportDetailResponse(long reportId, String reasonCode, String subReasonCode, String detail,
	String status, Instant createdAt, Instant resolvedAt) {

	public static ReportDetailResponse from(Report report) {
		return new ReportDetailResponse(report.id(), report.reasonCode(), report.subReasonCode(), report.detail(),
			report.status().name(), report.createdAt(), report.resolvedAt());
	}
}
