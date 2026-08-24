package com.dnd.qello.safety.web.response;

import java.time.Instant;

import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.service.ReportOutcome;

import io.swagger.v3.oas.annotations.media.Schema;

// 신고 접수 응답. 상대 식별자·운영자 내부 판단을 담지 않는다(INV-RPT-005) —
// 이 레코드의 컴포넌트 집합이 바로 그 계약이다.
public record ReportReceiptResponse(
	@Schema(description = "접수된 신고의 식별자.") long reportId,
	@Schema(description = "접수 직후 신고의 처리 상태.") String status,
	@Schema(description = "신고가 접수된 시각.") Instant receivedAt,
	@Schema(description = "같은 대상에 대한 기존 신고를 반환했는지 여부.") boolean alreadyReceived,
	@Schema(description = "신고 접수 결과와 후속 안내.") String guidance) {

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
