package com.dnd.qello.safety.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.safety.web.request.ReportCaseDecisionRequest;
import com.dnd.qello.safety.web.request.ReportCaseMoreInfoRequest;
import com.dnd.qello.safety.web.response.AnswerRestoreResponse;
import com.dnd.qello.safety.web.response.ReportCasePageResponse;
import com.dnd.qello.safety.web.response.ReportCaseResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

// OperatorReportCaseController의 문서 계약. 모든 endpoint는 운영자 세션 인증이 필요하다(#156).
// 신고자 경로(SafetyController)와 완전히 분리된 필터체인(SecurityConfiguration의
// operatorReportCaseSecurityFilterChain)이 인가를 맡는다 — 앱 액세스 토큰(JWT)으로는
// 이 경로에 도달할 수 없다.
@Tag(name = "신고 사건 운영자 판정", description = "심각도·긴급 대기열 조회와 사건 검토·판정 (#156)")
@SecurityRequirement(name = OpenApiConfiguration.OPERATOR_SESSION_SCHEME)
public interface OperatorReportCaseApiSpec {

	@Operation(
		summary = "대기열 조회",
		description = "OPEN·UNDER_REVIEW 사건만 SLA가 급한 순으로 반환합니다. queue를 생략하면 STANDARD·URGENT 모두 포함합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회했습니다.")
	})
	@GetMapping("/api/v1/operator/report-cases")
	ResponseEntity<ApiResponse<ReportCasePageResponse>> findQueue(
		@Parameter(description = "STANDARD 또는 URGENT. 생략 시 전체") @RequestParam(required = false) String queue,
		@RequestParam(required = false) String cursor,
		@RequestParam(required = false, defaultValue = "20") int limit);

	@Operation(summary = "검토 시작", description = "OPEN 사건을 UNDER_REVIEW로 전이합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검토를 시작했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
			description = "이미 종결된 사건입니다(SAF-DOM-005).",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/api/v1/operator/report-cases/{caseId}/review")
	ResponseEntity<ApiResponse<ReportCaseResponse>> startReview(@PathVariable long caseId);

	@Operation(
		summary = "사건 판정",
		description = "ACTIONED 또는 NO_VIOLATION으로 사건을 종결합니다. ACTIONED고 대상이 답변이면 그 답변을 전역 숨김합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "판정을 기록했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
			description = "이미 종결된 사건이거나(SAF-DOM-005) decision이 올바르지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/api/v1/operator/report-cases/{caseId}/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ReportCaseResponse>> decide(
		@PathVariable long caseId,
		@RequestBody @Valid ReportCaseDecisionRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "추가 정보 요청", description = "사건을 종결하지 않고 MORE_INFO_REQUIRED로 표시합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "추가 정보를 요청했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
			description = "이미 종결된 사건입니다(SAF-DOM-005).",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/api/v1/operator/report-cases/{caseId}/more-info", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ReportCaseResponse>> requestMoreInfo(
		@PathVariable long caseId,
		@RequestBody @Valid ReportCaseMoreInfoRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "숨김 답변 복원", description = "ACTIONED로 전역 숨김된 답변을 다시 PUBLISHED로 되돌립니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "복원했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
			description = "답변이 HIDDEN 상태가 아니거나 답변 대상이 아닌 사건입니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
			description = "사건 또는 답변을 찾을 수 없습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/api/v1/operator/report-cases/{caseId}/restore")
	ResponseEntity<ApiResponse<AnswerRestoreResponse>> restore(@PathVariable long caseId);
}
