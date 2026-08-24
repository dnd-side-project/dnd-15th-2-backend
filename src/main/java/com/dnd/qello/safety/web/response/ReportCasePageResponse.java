package com.dnd.qello.safety.web.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

// 커서 페이지네이션 목록. nextCursor가 null이면 더 이상 항목이 없다.
public record ReportCasePageResponse(
	@Schema(description = "운영자 처리 대기열에 포함된 신고 사건 목록.") List<ReportCaseResponse> items,
	@Schema(description = "다음 페이지 조회에 사용할 커서. 마지막 페이지면 null입니다.") String nextCursor) {
}
