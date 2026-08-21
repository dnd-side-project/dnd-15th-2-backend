package com.dnd.qello.safety.web;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.safety.domain.ModerationDecision;
import com.dnd.qello.safety.domain.ReportCase;
import com.dnd.qello.safety.domain.ReportCaseQueue;
import com.dnd.qello.safety.service.OperatorReportCaseService;
import com.dnd.qello.safety.web.request.ReportCaseDecisionRequest;
import com.dnd.qello.safety.web.request.ReportCaseMoreInfoRequest;
import com.dnd.qello.safety.web.response.AnswerRestoreResponse;
import com.dnd.qello.safety.web.response.ReportCasePageResponse;
import com.dnd.qello.safety.web.response.ReportCaseResponse;

// 신고 사건 운영자 판정 API. SecurityConfiguration의
// operatorReportCaseSecurityFilterChain(/api/v1/operator/**, hasRole("OPERATOR"))이
// 인가를 맡는다.
//
// 경로와 문서 애노테이션은 OperatorReportCaseApiSpec에 있다.
@RestController
public class OperatorReportCaseController implements OperatorReportCaseApiSpec {

	private static final int MAX_PAGE_LIMIT = 50;

	private final OperatorReportCaseService reportCaseService;
	private final ApiResponseFactory responseFactory;
	private final Clock clock;

	public OperatorReportCaseController(
		OperatorReportCaseService reportCaseService, ApiResponseFactory responseFactory, Clock clock) {
		this.reportCaseService = reportCaseService;
		this.responseFactory = responseFactory;
		this.clock = clock;
	}

	@Override
	public ResponseEntity<ApiResponse<ReportCasePageResponse>> findQueue(String queue, String cursor, int limit) {
		Instant now = clock.instant();
		int boundedLimit = Math.min(Math.max(limit, 1), MAX_PAGE_LIMIT);
		ReportCaseQueue queueFilter = queue == null ? null : ReportCaseQueue.valueOf(queue);
		ReportCaseCursor.Position position = cursor == null ? null : ReportCaseCursor.decode(cursor);
		Instant cursorSlaDueAt = position == null ? null : position.slaDueAt();
		Long cursorId = position == null ? null : position.id();

		List<ReportCase> page = reportCaseService.findQueue(queueFilter, cursorSlaDueAt, cursorId, boundedLimit + 1);
		boolean hasMore = page.size() > boundedLimit;
		List<ReportCase> items = hasMore ? page.subList(0, boundedLimit) : page;
		String nextCursor = hasMore
			? ReportCaseCursor.encode(items.get(items.size() - 1).slaDueAt(), items.get(items.size() - 1).id())
			: null;

		List<ReportCaseResponse> responses = items.stream().map(item -> ReportCaseResponse.from(item, now)).toList();
		return ResponseEntity.ok(responseFactory.success(new ReportCasePageResponse(responses, nextCursor)));
	}

	@Override
	public ResponseEntity<ApiResponse<ReportCaseResponse>> startReview(long caseId) {
		ReportCase updated = reportCaseService.startReview(caseId);
		return ResponseEntity.ok(responseFactory.success(ReportCaseResponse.from(updated, clock.instant())));
	}

	@Override
	public ResponseEntity<ApiResponse<ReportCaseResponse>> decide(
		long caseId, ReportCaseDecisionRequest request, Authentication authentication) {
		Instant now = clock.instant();
		ReportCase resolved = reportCaseService.decide(
			caseId, ModerationDecision.valueOf(request.decision()), operatorUserId(authentication),
			request.internalNote(), now);
		return ResponseEntity.ok(responseFactory.success(ReportCaseResponse.from(resolved, now)));
	}

	@Override
	public ResponseEntity<ApiResponse<ReportCaseResponse>> requestMoreInfo(
		long caseId, ReportCaseMoreInfoRequest request, Authentication authentication) {
		Instant now = clock.instant();
		ReportCase updated = reportCaseService.requestMoreInfo(
			caseId, operatorUserId(authentication), request.internalNote(), now);
		return ResponseEntity.ok(responseFactory.success(ReportCaseResponse.from(updated, now)));
	}

	@Override
	public ResponseEntity<ApiResponse<AnswerRestoreResponse>> restore(long caseId) {
		Answer restored = reportCaseService.restore(caseId, clock.instant());
		return ResponseEntity.ok(responseFactory.success(AnswerRestoreResponse.from(restored)));
	}

	// OperatorLoginController가 로그인 시 String.valueOf(userId)를 principal 이름으로 심는다.
	private long operatorUserId(Authentication authentication) {
		return Long.parseLong(authentication.getName());
	}
}
