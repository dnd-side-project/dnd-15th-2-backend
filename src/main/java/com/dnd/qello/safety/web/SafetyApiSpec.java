package com.dnd.qello.safety.web;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.safety.web.request.SubmitReportRequest;
import com.dnd.qello.safety.web.response.ReportDetailResponse;
import com.dnd.qello.safety.web.response.ReportPageResponse;
import com.dnd.qello.safety.web.response.ReportReasonResponse;
import com.dnd.qello.safety.web.response.ReportReceiptResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

// SafetyController의 문서 계약. 신고·차단은 더보기 메뉴의 같은 진입점에서 시작한다(#154).
@Tag(name = "신고·차단", description = "답변·질문글·사용자 신고 접수와 차단 (#154)")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface SafetyApiSpec {

	@Operation(summary = "신고 사유 목록 조회", description = "선택 가능한 신고 사유·하위 사유·설명 필수 여부를 반환합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping(path = "/api/v1/report-reasons")
	ResponseEntity<ApiResponse<List<ReportReasonResponse>>> reportReasons(
		@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "답변 신고", description = """
		답변을 신고합니다. 같은 대상에 대한 열린 신고가 이미 있으면 새로 만들지 않고
		기존 접수증을 반환합니다. 신고자가 열람할 수 없는 답변은 존재하지 않는 답변과
		동일하게 404로 응답합니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새로 접수했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이미 접수된 신고를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
			description = "요청 형식이 올바르지 않거나(SAF-VAL-*) 자기 자신을 신고했습니다(SAF-DOM-003).",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "답변을 찾을 수 없습니다(SAF-APP-002).",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "신고 요청이 너무 많습니다(SAF-APP-004).",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(path = "/api/v1/answers/{answerId}/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ReportReceiptResponse>> reportAnswer(
		@PathVariable long answerId, @RequestBody @Valid SubmitReportRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "질문글 신고", description = "방향 질문글을 신고합니다. 규칙은 답변 신고와 같습니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새로 접수했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이미 접수된 신고를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문글을 찾을 수 없습니다(SAF-APP-002).",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(path = "/api/v1/direction-posts/{postId}/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ReportReceiptResponse>> reportPost(
		@PathVariable long postId, @RequestBody @Valid SubmitReportRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "사용자 신고", description = """
		사용자를 신고합니다. 방향 질문글 송수신으로 실제 마주친 사용자만 신고할 수
		있습니다. 규칙은 답변 신고와 같습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새로 접수했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이미 접수된 신고를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다(SAF-APP-002).",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(path = "/api/v1/users/{userId}/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ReportReceiptResponse>> reportUser(
		@PathVariable long userId, @RequestBody @Valid SubmitReportRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "내 신고 내역 조회", description = "본인이 접수한 신고를 최신순으로 반환합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회했습니다.")
	})
	@GetMapping(path = "/api/v1/reports/me")
	ResponseEntity<ApiResponse<ReportPageResponse>> findMyReports(
		@RequestParam(required = false) String cursor,
		@RequestParam(required = false, defaultValue = "20") int limit,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "신고 상세 조회", description = "본인이 접수한 신고만 조회할 수 있습니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
			description = "신고를 찾을 수 없거나 본인 소유가 아닙니다(SAF-APP-003).",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping(path = "/api/v1/reports/{reportId}")
	ResponseEntity<ApiResponse<ReportDetailResponse>> findReport(
		@PathVariable long reportId, @Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "사용자 차단", description = "지정한 사용자를 차단합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "차단했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
			description = "자기 자신을 차단할 수 없습니다(SAF-DOM-001).",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(path = "/api/v1/users/{userId}/blocks")
	ResponseEntity<ApiResponse<Void>> block(
		@PathVariable long userId, @Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "사용자 차단 해제", description = "지정한 사용자에 대한 활성 차단을 해제합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "차단을 해제했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
			description = "활성 차단을 찾을 수 없습니다(SAF-APP-001).",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping(path = "/api/v1/users/{userId}/blocks")
	ResponseEntity<ApiResponse<Void>> releaseBlock(
		@PathVariable long userId, @Parameter(hidden = true) Authentication authentication);
}
