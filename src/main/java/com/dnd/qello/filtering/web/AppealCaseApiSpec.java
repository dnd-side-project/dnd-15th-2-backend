package com.dnd.qello.filtering.web;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

// AppealCaseController의 문서 계약. 모든 endpoint는 운영자 세션 인증이 필요하다.
@Tag(name = "필터링 이의제기 검토", description = "이의제기 큐 조회, 수동 결정과 접수 기간 연장 (#112)")
@SecurityRequirement(name = "operatorSession")
public interface AppealCaseApiSpec {

	@Operation(
		summary = "이의제기 검토 큐 조회",
		description = "OPEN 상태의 이의제기를 접수 순서(FIFO)로 반환합니다.")
	@GetMapping
	ResponseEntity<ApiResponse<List<AppealCaseResponse>>> findQueue(
		@Parameter(description = "최대 반환 개수") @RequestParam(defaultValue = "50") int limit);

	@Operation(
		summary = "이의제기 결정 적용",
		description = """
			이의제기를 UPHOLD_HIDDEN(비공개 유지) 또는 OVERTURN_HIDDEN(비공개 취소)으로 종결합니다.

			OVERTURN_HIDDEN이면 복원 콜백을 내보내기 전에 다른 공개 금지 사유(계정 차단·삭제 등)를
			다시 확인합니다. 남아 있는 사유가 있으면 결정은 그대로 기록하되 복원 콜백을 내보내지 않고
            restoreBlockedReasonCode에 사유를 남깁니다.

			답변의 공개 상태를 실제로 되돌리는 것은 이 API가 아니라 콜백을 받는 답변 담당 코드입니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이의제기를 종결했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404",
			description = "appeal case를 찾을 수 없습니다. (FLT-DOM-011)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "이미 종료된 appeal case입니다. (FLT-DOM-012)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/{appealCaseId}/decide", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<AppealCaseResponse>> decide(
		@Parameter(description = "appeal case id") @PathVariable long appealCaseId,
		@RequestBody @Valid AppealDecisionRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "접수 기간 연장",
		description = """
			법률·정책상 필요한 경우 접수 기간을 연장합니다. 현재 만료 시각보다 늦은 값만 받습니다 —
			기간을 줄이는 경로는 이 API에도, 도메인에도, 스키마에도 없습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "접수 기간을 연장했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404",
			description = "appeal case를 찾을 수 없습니다. (FLT-DOM-011)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "만료 시각을 앞당길 수 없습니다. (FLT-DOM-016)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(value = "/{appealCaseId}/extend", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<AppealCaseResponse>> extendExpiry(
		@Parameter(description = "appeal case id") @PathVariable long appealCaseId,
		@RequestBody @Valid ExtendAppealExpiryRequest request,
		@Parameter(hidden = true) Authentication authentication);
}
