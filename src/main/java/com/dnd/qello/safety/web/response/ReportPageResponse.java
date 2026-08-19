package com.dnd.qello.safety.web.response;

import java.util.List;

// 커서 페이지네이션 목록. nextCursor가 null이면 더 이상 항목이 없다.
public record ReportPageResponse(List<ReportSummaryResponse> items, String nextCursor) {
}
