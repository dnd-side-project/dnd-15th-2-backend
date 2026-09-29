package com.dnd.qello.filtering.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

// FilterReleaseController의 문서 계약. 모든 endpoint는 운영자 세션 인증이 필요하다.
@Tag(name = "필터링 검사 설정", description = "필터링 검사 설정 생성, 점검 단계 전환과 적용")
@SecurityRequirement(name = OpenApiConfiguration.OPERATOR_SESSION_SCHEME)
public interface FilterReleaseApiSpec {

	@Operation(summary = "새 검사 설정 만들기", description = """
			검사에 쓸 규칙과 모델을 묶어 새 검사 설정을 CANDIDATE 상태로 만듭니다.

			운영자 로그인과 CSRF 토큰이 필요합니다.

			참조 값에는 `latest`처럼 가리키는 대상이 바뀌는 별칭을 쓸 수 없습니다. 만든 설정은 점검 단계를 거쳐 적용하기 전까지 검사에 쓰이지 않습니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "CANDIDATE 상태의 검사 설정을 생성했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "참조 값이 비어 있거나 허용 길이를 넘거나 \"latest\" 별칭입니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping
	ResponseEntity<ApiResponse<FilterReleaseResponse>> create(@RequestBody @Valid CreateFilterReleaseRequest request);

	@Operation(summary = "검사 설정 목록 조회", description = """
			등록된 검사 설정 목록을 조회합니다.

			운영자 로그인이 필요합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검사 설정 목록을 조회했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없으면 조회할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping
	ResponseEntity<ApiResponse<List<FilterReleaseResponse>>> findAll();

	@Operation(summary = "검사 설정 상세 조회", description = """
			검사 설정 하나를 조회합니다.

			운영자 로그인이 필요합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검사 설정을 조회했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없으면 조회할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "검사 설정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/{releaseId}")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> find(
			@Parameter(description = "조회할 검사 설정 식별자입니다.") @PathVariable long releaseId);

	@Operation(summary = "외부 평가를 마쳤다고 표시하기 (→ OFFLINE_EVALUATED)", description = """
			CANDIDATE 상태의 검사 설정에 외부 평가를 마쳤다고 표시합니다. 평가 결과 자체는 보내지 않습니다.

			운영자 로그인과 CSRF 토큰이 필요하고 사유(reasonCode, reasonText)를 함께 보내야 합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OFFLINE_EVALUATED로 전이했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "검사 설정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 검사 설정 상태에서는 평가 완료로 표시할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{releaseId}/offline-evaluation")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> markOfflineEvaluated(
			@Parameter(description = "평가 완료로 표시할 설정 식별자입니다.") @PathVariable long releaseId,
			@RequestBody @Valid OperatorReasonRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "검사 설정을 shadow로 시험하기 (→ SHADOW)", description = """
			OFFLINE_EVALUATED 상태의 설정을 SHADOW 단계로 올립니다. 이 단계에서는 결과를 관찰만 하고 실제 판정에는 쓰지 않습니다.

			운영자 로그인과 CSRF 토큰이 필요하고 사유(reasonCode, reasonText)를 함께 보내야 합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "SHADOW로 전이했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "검사 설정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 검사 설정 상태에서는 SHADOW로 전환할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{releaseId}/shadow")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> designateShadow(
			@Parameter(description = "SHADOW로 전환할 설정 식별자입니다.") @PathVariable long releaseId,
			@RequestBody @Valid OperatorReasonRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "검사 설정을 canary로 시험하기 (→ CANARY)", description = """
			SHADOW 상태의 설정을 제한된 범위에서 확인하는 CANARY 단계로 올립니다. 이 호출만으로 모든 판정에 새 설정이 쓰이지는 않습니다.

			운영자 로그인과 CSRF 토큰이 필요하고 사유(reasonCode, reasonText)를 함께 보내야 합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "CANARY로 전이했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "검사 설정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 검사 설정 상태에서는 CANARY로 전환할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{releaseId}/canary")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> designateCanary(
			@Parameter(description = "CANARY로 전환할 설정 식별자입니다.") @PathVariable long releaseId,
			@RequestBody @Valid OperatorReasonRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "이 설정을 실제로 적용하기 (→ PROMOTED)", description = """
			CANARY 단계까지 확인한 설정을 실제 판정에 적용합니다. 이미 적용 중인 설정은 ROLLED_BACK 상태로 내려가므로 적용 중인 설정은 항상 하나입니다.

			운영자 로그인과 CSRF 토큰이 필요하고 적용 사유(reasonCode, reasonText)를 함께 보내야 합니다.

			설정을 만들거나 단계를 올리는 것만으로는 판정이 바뀌지 않습니다. 이 API를 호출해야 바뀝니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "PROMOTED로 전이했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "검사 설정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 검사 설정 상태에서는 적용할 수 없거나 이미 적용 중인 설정입니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{releaseId}/promote")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> promote(
			@Parameter(description = "실제로 적용할 설정 식별자입니다.") @PathVariable long releaseId,
			@RequestBody @Valid OperatorReasonRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "이전에 적용한 설정을 다시 적용하기 (→ PROMOTED)", description = """
			ROLLED_BACK 상태인 이전 설정을 다시 실제 판정에 적용합니다. 지금 적용 중인 설정은 ROLLED_BACK 상태로 내려갑니다.

			운영자 로그인과 CSRF 토큰이 필요하고 사유(reasonCode, reasonText)를 함께 보내야 합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "다시 PROMOTED로 전이했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영자 세션이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "검사 설정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "ROLLED_BACK 상태가 아니어서 다시 적용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{releaseId}/rollback")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> rollback(
			@Parameter(description = "다시 적용할 설정 식별자입니다.") @PathVariable long releaseId,
			@RequestBody @Valid OperatorReasonRequest request,
			@Parameter(hidden = true) Authentication authentication);

}
