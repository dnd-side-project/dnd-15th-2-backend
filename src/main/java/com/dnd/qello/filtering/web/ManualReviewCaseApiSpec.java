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

// ManualReviewCaseController의 문서 계약. 모든 endpoint는 운영자 세션 인증이 필요하다.
@Tag(name = "필터링 수동 검토 case", description = "검토자 큐 조회와 결정. band·aging·FIFO 우선순위(#110)")
@SecurityRequirement(name = "operatorSession")
public interface ManualReviewCaseApiSpec {

	@Operation(
		summary = "검토자 큐 조회",
		description = """
			OPEN case를 effectiveBand 내림차순 + band 내 created_at 오름차순(FIFO)으로 반환한다.
			agingThresholdSeconds는 저장된 band가 STANDARD인 case를 얼마나 오래 대기하면 HIGH로
			취급할지를 호출자가 명시한다 — 실제 운영 aging 시간이 미결정이라 서버가 값을 고정하지
			않는다.""")
	@GetMapping
	ResponseEntity<ApiResponse<List<ManualReviewCaseResponse>>> findQueue(
		@Parameter(description = "aging 임계값(초)") @RequestParam long agingThresholdSeconds,
		@Parameter(description = "최대 반환 개수") @RequestParam(defaultValue = "50") int limit);

	@Operation(
		summary = "검토자 결정 적용",
		description = """
			case를 ALLOW 또는 BLOCK으로 종결한다. 자동 결과가 이미 도착해 job이 RESOLVED라면
			job은 건드리지 않고 그 기존 판정으로 case만 종료한다(INV-MAN-003).""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "case가 종료됐습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404",
			description = "case를 찾을 수 없습니다. (FLT-DOM-010)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "이미 종료된 case입니다. (FLT-DOM-009)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{caseId}/decide")
	ResponseEntity<ApiResponse<ManualReviewCaseResponse>> decide(
		@Parameter(description = "manual review case id") @PathVariable long caseId,
		@RequestBody @Valid ManualReviewDecisionRequest request,
		Authentication authentication);
}
