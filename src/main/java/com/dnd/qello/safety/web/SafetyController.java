package com.dnd.qello.safety.web;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.AuthenticatedUserId;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportSubmission;
import com.dnd.qello.safety.service.ReportOutcome;
import com.dnd.qello.safety.service.SafetyReportService;
import com.dnd.qello.safety.service.SafetyService;
import com.dnd.qello.safety.web.request.SubmitReportRequest;
import com.dnd.qello.safety.web.response.ReportDetailResponse;
import com.dnd.qello.safety.web.response.ReportPageResponse;
import com.dnd.qello.safety.web.response.ReportReasonResponse;
import com.dnd.qello.safety.web.response.ReportReceiptResponse;
import com.dnd.qello.safety.web.response.ReportSummaryResponse;

// 신고·차단 API. 경로와 문서 애노테이션은 SafetyApiSpec에 있다.
@RestController
public class SafetyController implements SafetyApiSpec {

	private static final int MAX_PAGE_LIMIT = 50;

	private final SafetyReportService safetyReportService;
	private final SafetyService safetyService;
	private final ApiResponseFactory responseFactory;
	private final Clock clock;

	public SafetyController(SafetyReportService safetyReportService, SafetyService safetyService,
		ApiResponseFactory responseFactory, Clock clock) {
		this.safetyReportService = safetyReportService;
		this.safetyService = safetyService;
		this.responseFactory = responseFactory;
		this.clock = clock;
	}

	@Override
	public ResponseEntity<ApiResponse<List<ReportReasonResponse>>> reportReasons(Authentication authentication) {
		AuthenticatedUserId.require(authentication);
		return ResponseEntity.ok(responseFactory.success(ReportReasonResponse.catalog()));
	}

	@Override
	public ResponseEntity<ApiResponse<ReportReceiptResponse>> reportAnswer(
		long answerId, SubmitReportRequest request, Authentication authentication) {
		long reporterId = AuthenticatedUserId.require(authentication);
		ReportOutcome outcome = safetyReportService.submitAnswerReport(
			reporterId, answerId, toSubmission(request), request.shouldBlockAuthor(), now());
		return receiptResponse(outcome);
	}

	@Override
	public ResponseEntity<ApiResponse<ReportReceiptResponse>> reportPost(
		long postId, SubmitReportRequest request, Authentication authentication) {
		long reporterId = AuthenticatedUserId.require(authentication);
		ReportOutcome outcome = safetyReportService.submitPostReport(
			reporterId, postId, toSubmission(request), request.shouldBlockAuthor(), now());
		return receiptResponse(outcome);
	}

	@Override
	public ResponseEntity<ApiResponse<ReportReceiptResponse>> reportUser(
		long userId, SubmitReportRequest request, Authentication authentication) {
		long reporterId = AuthenticatedUserId.require(authentication);
		ReportOutcome outcome = safetyReportService.submitUserReport(
			reporterId, userId, toSubmission(request), request.shouldBlockAuthor(), now());
		return receiptResponse(outcome);
	}

	@Override
	public ResponseEntity<ApiResponse<ReportPageResponse>> findMyReports(
		String cursor, int limit, Authentication authentication) {
		long reporterId = AuthenticatedUserId.require(authentication);
		int boundedLimit = Math.min(Math.max(limit, 1), MAX_PAGE_LIMIT);
		Instant cursorCreatedAt = null;
		Long cursorId = null;
		if (cursor != null && !cursor.isBlank()) {
			ReportCursor.Position position = ReportCursor.decode(cursor);
			cursorCreatedAt = position.createdAt();
			cursorId = position.id();
		}
		List<Report> reports = safetyReportService.findMyReports(reporterId, cursorCreatedAt, cursorId, boundedLimit);
		List<ReportSummaryResponse> items = reports.stream().map(ReportSummaryResponse::from).toList();
		String nextCursor = reports.size() < boundedLimit ? null
			: ReportCursor.encode(reports.get(reports.size() - 1).createdAt(), reports.get(reports.size() - 1).id());
		return ResponseEntity.ok(responseFactory.success(new ReportPageResponse(items, nextCursor)));
	}

	@Override
	public ResponseEntity<ApiResponse<ReportDetailResponse>> findReport(long reportId, Authentication authentication) {
		long viewerId = AuthenticatedUserId.require(authentication);
		Report report = safetyReportService.requireOwnReport(reportId, viewerId);
		return ResponseEntity.ok(responseFactory.success(ReportDetailResponse.from(report)));
	}

	@Override
	public ResponseEntity<ApiResponse<Void>> block(long userId, Authentication authentication) {
		long blockerId = AuthenticatedUserId.require(authentication);
		safetyService.block(blockerId, userId, now());
		return ResponseEntity.ok(responseFactory.success());
	}

	@Override
	public ResponseEntity<ApiResponse<Void>> releaseBlock(long userId, Authentication authentication) {
		long blockerId = AuthenticatedUserId.require(authentication);
		safetyService.releaseBlock(blockerId, userId, now());
		return ResponseEntity.ok(responseFactory.success());
	}

	private ResponseEntity<ApiResponse<ReportReceiptResponse>> receiptResponse(ReportOutcome outcome) {
		HttpStatus status = outcome.alreadyReceived() ? HttpStatus.OK : HttpStatus.CREATED;
		return ResponseEntity.status(status).body(responseFactory.success(ReportReceiptResponse.from(outcome)));
	}

	private static ReportSubmission toSubmission(SubmitReportRequest request) {
		return new ReportSubmission(request.reasonCode(), request.subReasonCode(), request.detail());
	}

	private Instant now() {
		return Instant.now(clock);
	}
}
