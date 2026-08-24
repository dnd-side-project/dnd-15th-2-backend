package com.dnd.qello.safety.web.response;

import java.time.Instant;

import com.dnd.qello.safety.domain.Report;

import io.swagger.v3.oas.annotations.media.Schema;

// 단건 조회. 상대 식별자·moderation_review 내부 판단은 담지 않는다(INV-RPT-005).
public record ReportDetailResponse(
	@Schema(description = "신고의 식별자.") long reportId,
	@Schema(description = "신고에 선택한 상위 사유 코드.") String reasonCode,
	@Schema(description = "신고에 선택한 하위 사유 코드. 없으면 null입니다.") String subReasonCode,
	@Schema(description = "신고자가 입력한 추가 설명. 없으면 null입니다.") String detail,
	@Schema(description = "신고의 처리 상태.") String status,
	@Schema(description = "신고가 접수된 시각.") Instant createdAt,
	@Schema(description = "신고가 종결된 시각. 아직 종결되지 않았으면 null입니다.") Instant resolvedAt) {

	public static ReportDetailResponse from(Report report) {
		return new ReportDetailResponse(report.id(), report.reasonCode(), report.subReasonCode(), report.detail(),
			report.status().name(), report.createdAt(), report.resolvedAt());
	}
}
