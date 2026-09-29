package com.dnd.qello.safety.web;

import java.util.List;

import jakarta.validation.Valid;

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

// SafetyController의 문서 계약. 신고와 차단은 앱 사용자가 콘텐츠 더보기 메뉴에서 시작한다.
@Tag(name = "신고·차단", description = "신고 사유 조회, 답변·질문글·사용자 신고와 사용자 차단")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface SafetyApiSpec {

	@Operation(summary = "신고 사유 목록 조회", description = """
			신고할 때 고를 수 있는 사유와 하위 사유를 조회합니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 사유 목록을 조회했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping(path = "/api/v1/report-reasons")
	ResponseEntity<ApiResponse<List<ReportReasonResponse>>> reportReasons(
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "답변 신고", description = """
			답변을 신고합니다. 작성자를 함께 차단할 수도 있습니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.

			내가 이미 신고해 처리 중인 답변이면 새로 접수하지 않고 200과 기존 접수 결과를 반환합니다. 검토 결과는 나중에 알림으로 전달됩니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새 신고를 접수했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내가 접수한 신고가 아직 처리 중이어서 그 접수 결과를 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이나 신고 사유 조합이 올바르지 않거나 자기 자신을 신고했습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "답변을 찾을 수 없거나 현재 사용자가 열람할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "신고 처리가 일시적으로 지연되고 있습니다. 잠시 후 다시 시도해 주세요.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "신고 요청 또는 긴급 신고 일일 한도를 초과했습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(path = "/api/v1/answers/{answerId}/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ReportReceiptResponse>> reportAnswer(
			@Parameter(description = "신고할 답변의 식별자.") @PathVariable long answerId,
			@RequestBody @Valid SubmitReportRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "질문글 신고", description = """
			방향 질문글을 신고합니다. 작성자를 함께 차단할 수도 있습니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.

			내가 이미 신고해 처리 중인 질문글이면 새로 접수하지 않고 200과 기존 접수 결과를 반환합니다. 검토 결과는 나중에 알림으로 전달됩니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새 신고를 접수했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내가 접수한 신고가 아직 처리 중이어서 그 접수 결과를 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이나 신고 사유 조합이 올바르지 않거나 자기 자신을 신고했습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문글을 찾을 수 없거나 현재 사용자가 열람할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "신고 처리가 일시적으로 지연되고 있습니다. 잠시 후 다시 시도해 주세요.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "신고 요청 또는 긴급 신고 일일 한도를 초과했습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(path = "/api/v1/direction-posts/{postId}/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ReportReceiptResponse>> reportPost(
			@Parameter(description = "신고할 질문글의 식별자.") @PathVariable long postId,
			@RequestBody @Valid SubmitReportRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "사용자 신고", description = """
			사용자를 신고합니다. 신고한 사용자를 함께 차단할 수도 있습니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.

			질문글을 주고받았거나 같은 질문글을 함께 받은 사용자만 신고할 수 있습니다. 내가 이미 신고해 처리 중인 사용자면 200과 기존 접수 결과를 반환합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새 신고를 접수했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내가 접수한 신고가 아직 처리 중이어서 그 접수 결과를 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이나 신고 사유 조합이 올바르지 않거나 자기 자신을 신고했습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "신고할 사용자를 찾을 수 없거나 신고할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "신고 처리가 일시적으로 지연되고 있습니다. 잠시 후 다시 시도해 주세요.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "신고 요청 또는 긴급 신고 일일 한도를 초과했습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(path = "/api/v1/users/{userId}/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ReportReceiptResponse>> reportUser(
			@Parameter(description = "신고할 사용자의 식별자.") @PathVariable long userId,
			@RequestBody @Valid SubmitReportRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "내 신고 내역 조회", description = """
			내가 접수한 신고 목록을 최근 접수한 순으로 조회합니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.

			다음 페이지는 응답의 nextCursor를 cursor에 넣어 요청합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 신고 내역을 조회했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "cursor 형식이 올바르지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping(path = "/api/v1/reports/me")
	ResponseEntity<ApiResponse<ReportPageResponse>> findMyReports(
			@Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략합니다.") @RequestParam(required = false) String cursor,
			@Parameter(description = "페이지 크기. 기본값은 20이며 1~50으로 보정됩니다.") @RequestParam(required = false, defaultValue = "20") int limit,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "내 신고 상세 조회", description = """
			내가 접수한 신고 하나의 처리 상태를 조회합니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 신고 상세를 조회했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "신고를 찾을 수 없거나 현재 사용자가 접수한 신고가 아닙니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping(path = "/api/v1/reports/{reportId}")
	ResponseEntity<ApiResponse<ReportDetailResponse>> findReport(
			@Parameter(description = "조회할 신고의 식별자.") @PathVariable long reportId,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "사용자 차단", description = """
			사용자를 차단합니다. 차단한 사용자가 보낸 질문 중 아직 처리하지 않은 것은 수신함에서 차단 상태로 바뀝니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.

			차단해도 신고는 접수되지 않습니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사용자를 차단했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "자기 자신은 차단할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(path = "/api/v1/users/{userId}/blocks")
	ResponseEntity<ApiResponse<Void>> block(
			@Parameter(description = "차단할 사용자의 식별자.") @PathVariable long userId,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "사용자 차단 해제", description = """
			사용자 차단을 해제합니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.

			이미 차단 상태로 바뀐 수신 항목은 되돌아오지 않습니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사용자 차단을 해제했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "활성 차단을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping(path = "/api/v1/users/{userId}/blocks")
	ResponseEntity<ApiResponse<Void>> releaseBlock(
			@Parameter(description = "차단을 해제할 사용자의 식별자.") @PathVariable long userId,
			@Parameter(hidden = true) Authentication authentication);
}
