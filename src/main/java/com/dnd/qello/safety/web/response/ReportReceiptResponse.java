package com.dnd.qello.safety.web.response;

import java.time.Instant;

import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.service.ReportOutcome;

// 신고 접수 응답. 상대 식별자·운영자 내부 판단을 담지 않는다(INV-RPT-005) —
// 이 레코드의 컴포넌트 집합이 바로 그 계약이다.
public record ReportReceiptResponse(long reportId, String status, Instant receivedAt,
	boolean alreadyReceived, String guidance) {

	public static ReportReceiptResponse from(ReportOutcome outcome) {
		Report report = outcome.report();
		return new ReportReceiptResponse(report.id(), report.status().name(), report.createdAt(),
			outcome.alreadyReceived(), guidanceFor(outcome.alreadyReceived()));
	}

	private static String guidanceFor(boolean alreadyReceived) {
		return alreadyReceived
			? "이미 접수된 신고입니다. 검토 결과를 알림으로 보내드립니다."
			: "접수되었습니다. 검토 후 결과를 알림으로 보내드립니다.";
	}
}
