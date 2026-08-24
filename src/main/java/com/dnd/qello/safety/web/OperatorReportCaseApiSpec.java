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

// OperatorReportCaseController의 문서 계약. 모든 endpoint는 운영자 세션 인증이 필요하다.
@Tag(name = "신고 사건 운영자 판정", description = "신고 사건 대기열 조회와 검토·판정·복원")
@SecurityRequirement(name = OpenApiConfiguration.OPERATOR_SESSION_SCHEME)
public interface OperatorReportCaseApiSpec {

	@Operation(
		summary = "신고 사건 대기열 조회",
		description = """
			운영자가 아직 처리하지 않은 신고 사건을 SLA 마감 시각이 가까운 순서로 조회합니다.

			운영자 세션 인증이 필요합니다. queue를 생략하면 STANDARD와 URGENT 사건을 모두 조회하며,
			지정하면 해당 대기열만 조회합니다.

			사건 목록, 처리 상태, 심각도, SLA 마감 시각과 다음 페이지 커서를 반환합니다. limit는
			1에서 50 사이로 보정됩니다.

			cursor 형식이 올바르지 않으면 요청을 처리하지 않습니다. queue는 STANDARD 또는 URGENT만
			사용할 수 있습니다.

			이 API는 신고자용 신고 내역이 아니라 운영자 처리 대기열입니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 사건 대기열을 조회했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "cursor 형식이 올바르지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/api/v1/operator/report-cases")
	ResponseEntity<ApiResponse<ReportCasePageResponse>> findQueue(
		@Parameter(description = "STANDARD 또는 URGENT. 생략하면 두 대기열을 모두 조회합니다.") @RequestParam(required = false) String queue,
		@Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략합니다.") @RequestParam(required = false) String cursor,
		@Parameter(description = "페이지 크기. 기본값은 20이며 1~50으로 보정됩니다.") @RequestParam(required = false, defaultValue = "20") int limit);

	@Operation(
		summary = "신고 사건 검토 시작",
		description = """
			운영자가 열린 신고 사건을 검토 중으로 표시합니다.

			운영자 세션 인증이 필요합니다. 아직 종결되지 않은 사건만 검토를 시작할 수 있습니다.

			사건 상태가 검토 중으로 바뀐 결과를 반환합니다.

			사건 식별자가 올바르지 않거나 이미 종결된 사건이면 요청을 처리하지 않습니다.

			이 호출만으로 사건의 최종 판정이나 답변 숨김이 실행되지는 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 사건 검토를 시작했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "사건 식별자가 올바르지 않거나 이미 종결된 사건입니다. (SAF-VAL-001, SAF-DOM-005)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/api/v1/operator/report-cases/{caseId}/review")
	ResponseEntity<ApiResponse<ReportCaseResponse>> startReview(
		@Parameter(description = "검토를 시작할 신고 사건의 식별자.") @PathVariable long caseId);

	@Operation(
		summary = "신고 사건 최종 판정",
		description = """
			운영자가 신고 사건을 ACTIONED 또는 NO_VIOLATION으로 종결합니다.

			운영자 세션 인증이 필요합니다. 사건 식별자가 올바르고 아직 종결되지 않아야 하며,
			decision은 두 최종 판정 중 하나여야 합니다. 추가 정보가 필요하면 별도 API를 사용합니다.

			판정, 종결 시각과 사건 처리 결과를 반환합니다. ACTIONED이고 대상이 답변이면 그 답변을
			숨기고 관련 알림을 취소합니다. internalNote는 신고자에게 공개하지 않는 운영자 메모입니다.

			이미 종결된 사건, 허용되지 않은 판정 또는 사건 대상과 맞지 않는 답변 조치면 요청을
			처리하지 않습니다.

			판정은 사건을 종결하므로 같은 사건에 다시 최종 판정을 내릴 수 없습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 사건 최종 판정을 기록했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "사건 식별자가 올바르지 않거나 판정 값이 없고, 이미 종결된 사건이거나 추가 정보 요청을 이 API로 보낼 수 없습니다. (CMN-VAL-001, SAF-VAL-001, SAF-DOM-002, SAF-DOM-005)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "ACTIONED 조치 대상 답변을 찾을 수 없습니다. (SAF-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/api/v1/operator/report-cases/{caseId}/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ReportCaseResponse>> decide(
		@Parameter(description = "최종 판정할 신고 사건의 식별자.") @PathVariable long caseId,
		@RequestBody @Valid ReportCaseDecisionRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "신고 사건 추가 정보 요청",
		description = """
			운영자가 신고 사건에 추가 정보가 필요하다고 표시하고 내부 메모를 남깁니다.

			운영자 세션 인증이 필요합니다. 사건이 아직 종결되지 않았고, 무엇이 더 필요한지 적은
			메모를 보내야 합니다.

			사건을 MORE_INFO_REQUIRED 상태로 표시한 결과를 반환합니다.

			사건 식별자가 올바르지 않거나 이미 종결된 사건이면 요청을 처리하지 않습니다.

			이 호출은 최종 판정을 내리지 않으며, 메모는 신고자에게 공개되지 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 사건에 추가 정보를 요청했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 메모가 올바르지 않거나 사건 식별자가 올바르지 않고, 사건이 이미 종결되었습니다. (CMN-VAL-001, SAF-VAL-001, SAF-DOM-005)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/api/v1/operator/report-cases/{caseId}/more-info", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<ReportCaseResponse>> requestMoreInfo(
		@Parameter(description = "추가 정보가 필요한 신고 사건의 식별자.") @PathVariable long caseId,
		@RequestBody @Valid ReportCaseMoreInfoRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "숨김 답변 복원",
		description = """
			운영자가 ACTIONED 판정으로 숨겨진 답변을 다시 공개 상태로 복원합니다.

			운영자 세션 인증이 필요합니다. 사건이 답변을 대상으로 하고 ACTIONED로 종결되어 있어야
			합니다.

			복원한 답변의 식별자와 현재 상태를 반환합니다.

			사건 식별자·대상 답변이 없거나 사건 조건이 ACTIONED 복원 조건과 맞지 않으면 요청을
			처리하지 않습니다.

			질문글·사용자 사건은 이 API로 복원할 수 없습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "숨김 답변을 복원했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "사건 식별자·대상이 올바르지 않거나 ACTIONED로 종결된 사건이 아닙니다. (SAF-VAL-001, SAF-VAL-003, SAF-DOM-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "복원할 답변을 찾을 수 없습니다. (SAF-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/api/v1/operator/report-cases/{caseId}/restore")
	ResponseEntity<ApiResponse<AnswerRestoreResponse>> restore(
		@Parameter(description = "복원할 답변이 연결된 신고 사건의 식별자.") @PathVariable long caseId);
}
