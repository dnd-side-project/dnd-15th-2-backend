package com.dnd.qello.safety.web.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

// 커서 페이지네이션 목록. nextCursor가 null이면 더 이상 항목이 없다.
public record ReportPageResponse(
	@Schema(description = "현재 사용자가 접수한 신고 요약 목록.") List<ReportSummaryResponse> items,
	@Schema(description = "다음 페이지 조회에 사용할 커서. 마지막 페이지면 null입니다.") String nextCursor) {
}
