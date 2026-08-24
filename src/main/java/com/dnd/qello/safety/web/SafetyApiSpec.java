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

// SafetyController의 문서 계약. 신고와 차단은 앱 사용자가 콘텐츠 더보기 메뉴에서 시작한다.
@Tag(name = "신고·차단", description = "신고 사유 조회, 답변·질문글·사용자 신고와 사용자 차단")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface SafetyApiSpec {

	@Operation(
		summary = "신고 사유 목록 조회",
		description = """
			신고할 때 선택할 수 있는 사유와 하위 사유를 조회합니다.

			앱 액세스 토큰이 필요합니다.

			사유 코드, 화면에 표시할 이름, 선택 가능한 하위 사유와 추가 설명 필요 여부를 반환합니다.

			이 API는 사유 목록만 조회하며 신고를 접수하지 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 사유 목록을 조회했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping(path = "/api/v1/report-reasons")
	ResponseEntity<ApiResponse<List<ReportReasonResponse>>> reportReasons(
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "답변 신고",
		description = """
			답변을 신고합니다. 신고 대상의 작성자를 함께 차단할 수도 있습니다.

			앱 액세스 토큰이 필요합니다. 답변은 현재 사용자가 열람할 수 있어야 하며, 신고 사유와
			하위 사유·설명 조합이 허용되어야 합니다. 자기 자신이 작성한 답변은 신고할 수 없습니다.

			새 신고면 201과 접수 결과를 반환하고, 같은 답변에 이미 열린 신고가 있으면 새로 만들지
			않고 200과 기존 접수 결과를 반환합니다.

			답변을 찾을 수 없거나 열람할 수 없으면 404로 응답합니다. 사유 조합이 올바르지 않거나
			자기 자신을 신고하면 400으로, 신고 한도를 넘으면 429로 응답합니다.

			신고 접수는 검토 결과를 즉시 반환하지 않습니다. 검토 결과는 별도 알림으로 전달됩니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새 신고를 접수했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이미 접수된 신고의 결과를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이나 신고 사유 조합이 올바르지 않거나 자기 자신을 신고했습니다. (CMN-VAL-001, SAF-VAL-002, SAF-VAL-006, SAF-VAL-007, SAF-DOM-003)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "답변을 찾을 수 없거나 현재 사용자가 열람할 수 없습니다. (SAF-APP-002)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "신고 사건을 일시적으로 병합하지 못했습니다. (SAF-INFRA-002)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "신고 요청 또는 긴급 신고 일일 한도를 초과했습니다. (SAF-APP-004, SAF-APP-005)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(path = "/api/v1/answers/{answerId}/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ReportReceiptResponse>> reportAnswer(
		@Parameter(description = "신고할 답변의 식별자.") @PathVariable long answerId,
		@RequestBody @Valid SubmitReportRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "질문글 신고",
		description = """
			방향 질문글을 신고합니다. 신고 대상의 작성자를 함께 차단할 수도 있습니다.

			앱 액세스 토큰이 필요합니다. 질문글은 현재 사용자가 열람할 수 있어야 하며, 신고 사유와
			하위 사유·설명 조합이 허용되어야 합니다. 자기 자신이 작성한 질문글은 신고할 수 없습니다.

			새 신고면 201과 접수 결과를 반환하고, 같은 질문글에 열린 신고가 있으면 200과 기존
			접수 결과를 반환합니다.

			질문글을 찾을 수 없거나 열람할 수 없으면 404로, 잘못된 사유 조합은 400으로, 신고 한도
			초과는 429로 응답합니다.

			신고 접수는 검토 결과를 즉시 반환하지 않습니다. 검토 결과는 별도 알림으로 전달됩니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새 신고를 접수했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이미 접수된 신고의 결과를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이나 신고 사유 조합이 올바르지 않거나 자기 자신을 신고했습니다. (CMN-VAL-001, SAF-VAL-002, SAF-VAL-006, SAF-VAL-007, SAF-DOM-003)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "질문글을 찾을 수 없거나 현재 사용자가 열람할 수 없습니다. (SAF-APP-002)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "신고 사건을 일시적으로 병합하지 못했습니다. (SAF-INFRA-002)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "신고 요청 또는 긴급 신고 일일 한도를 초과했습니다. (SAF-APP-004, SAF-APP-005)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(path = "/api/v1/direction-posts/{postId}/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ReportReceiptResponse>> reportPost(
		@Parameter(description = "신고할 질문글의 식별자.") @PathVariable long postId,
		@RequestBody @Valid SubmitReportRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "사용자 신고",
		description = """
			사용자를 신고합니다. 방향 질문글 송수신으로 현재 사용자가 실제로 마주친 사용자만
			신고할 수 있으며, 신고 대상 사용자를 함께 차단할 수도 있습니다.

			앱 액세스 토큰이 필요합니다. 신고 사유와 하위 사유·설명 조합이 허용되어야 하며 자기
			자신은 신고할 수 없습니다.

			새 신고면 201과 접수 결과를 반환하고, 같은 사용자에 열린 신고가 있으면 200과 기존
			접수 결과를 반환합니다.

			사용자를 찾을 수 없거나 신고할 수 없는 관계면 404로, 잘못된 사유 조합은 400으로,
			신고 한도 초과는 429로 응답합니다.

			신고 접수는 검토 결과를 즉시 반환하지 않습니다. 검토 결과는 별도 알림으로 전달됩니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새 신고를 접수했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이미 접수된 신고의 결과를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이나 신고 사유 조합이 올바르지 않거나 자기 자신을 신고했습니다. (CMN-VAL-001, SAF-VAL-002, SAF-VAL-006, SAF-VAL-007, SAF-DOM-003)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "신고할 사용자를 찾을 수 없거나 신고할 수 없습니다. (SAF-APP-002)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "신고 사건을 일시적으로 병합하지 못했습니다. (SAF-INFRA-002)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "신고 요청 또는 긴급 신고 일일 한도를 초과했습니다. (SAF-APP-004, SAF-APP-005)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(path = "/api/v1/users/{userId}/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ReportReceiptResponse>> reportUser(
		@Parameter(description = "신고할 사용자의 식별자.") @PathVariable long userId,
		@RequestBody @Valid SubmitReportRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "내 신고 내역 조회",
		description = """
			현재 사용자가 접수한 신고 내역을 최신 접수 순서로 조회합니다.

			앱 액세스 토큰이 필요합니다.

			신고 요약 목록과 다음 페이지를 요청할 때 사용할 불투명 커서를 반환합니다. limit는
			1에서 50 사이로 보정됩니다.

			cursor 형식이 올바르지 않으면 요청을 처리하지 않습니다.

			다른 사용자가 접수한 신고는 이 목록에 포함되지 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 신고 내역을 조회했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "cursor 형식이 올바르지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping(path = "/api/v1/reports/me")
	ResponseEntity<ApiResponse<ReportPageResponse>> findMyReports(
		@Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략합니다.") @RequestParam(required = false) String cursor,
		@Parameter(description = "페이지 크기. 기본값은 20이며 1~50으로 보정됩니다.") @RequestParam(required = false, defaultValue = "20") int limit,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "내 신고 상세 조회",
		description = """
			현재 사용자가 접수한 신고 한 건의 접수·처리 상태를 조회합니다.

			앱 액세스 토큰이 필요합니다. 요청한 신고가 현재 사용자가 접수한 신고여야 합니다.

			신고 사유, 설명, 상태와 접수·종결 시각을 반환합니다.

			신고가 없거나 다른 사용자가 접수한 신고면 404로 응답합니다.

			신고 대상의 상대방 식별자와 운영자의 내부 판단 내용은 반환하지 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 신고 상세를 조회했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "신고를 찾을 수 없거나 현재 사용자가 접수한 신고가 아닙니다. (SAF-APP-003)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping(path = "/api/v1/reports/{reportId}")
	ResponseEntity<ApiResponse<ReportDetailResponse>> findReport(
		@Parameter(description = "조회할 신고의 식별자.") @PathVariable long reportId,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "사용자 차단",
		description = """
			현재 사용자가 지정한 사용자를 차단합니다.

			앱 액세스 토큰이 필요합니다. 차단 대상은 경로의 userId로 지정합니다.

			성공하면 두 사용자 사이의 활성 차단을 만들고, 차단 대상이 보낸 미종결 수신 항목을
			차단 상태로 전환해 현재 사용자의 수신 가능 슬롯을 정리합니다.

			자기 자신은 차단할 수 없습니다.

			차단은 신고 접수와 별개의 동작입니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사용자를 차단했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "자기 자신은 차단할 수 없습니다. (SAF-DOM-001)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(path = "/api/v1/users/{userId}/blocks")
	ResponseEntity<ApiResponse<Void>> block(
		@Parameter(description = "차단할 사용자의 식별자.") @PathVariable long userId,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "사용자 차단 해제",
		description = """
			현재 사용자가 지정한 사용자에 대한 활성 차단을 해제합니다.

			앱 액세스 토큰이 필요합니다.

			활성 차단을 해제하면 해당 관계의 차단 상태를 종료합니다.

			현재 사용자가 만든 활성 차단이 없으면 404로 응답합니다.

			차단 해제는 과거에 차단 상태가 된 수신 항목이나 신고를 되돌리지 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사용자 차단을 해제했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "활성 차단을 찾을 수 없습니다. (SAF-APP-001)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping(path = "/api/v1/users/{userId}/blocks")
	ResponseEntity<ApiResponse<Void>> releaseBlock(
		@Parameter(description = "차단을 해제할 사용자의 식별자.") @PathVariable long userId,
		@Parameter(hidden = true) Authentication authentication);
}
