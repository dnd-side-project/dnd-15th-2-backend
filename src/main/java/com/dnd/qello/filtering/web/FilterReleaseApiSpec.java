package com.dnd.qello.filtering.web;

import java.util.List;

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
import jakarta.validation.Valid;

// FilterReleaseController의 문서 계약. 모든 endpoint는 운영자 세션 인증이 필요하다.
@Tag(name = "필터링 검사 설정", description = "필터링 검사 설정 생성, 점검 단계 전환과 적용")
@SecurityRequirement(name = OpenApiConfiguration.OPERATOR_SESSION_SCHEME)
public interface FilterReleaseApiSpec {

	@Operation(
		summary = "새 검사 설정 만들기",
		description = """
			정규화 규칙, 로컬 규칙, 분류 매핑과 모델 snapshot을 가리키는 참조를 묶어 새 검사 설정을
			CANDIDATE 상태로 만듭니다.

			운영자 세션과 CSRF 토큰이 필요합니다. 네 참조 값은 비어 있지 않아야 하며, `latest`처럼
			움직이는 별칭을 사용할 수 없습니다.

			생성에 성공하면 CANDIDATE 상태의 검사 설정을 반환합니다.

			참조 값이 비어 있거나 허용 길이를 넘거나 `latest` 별칭이면 생성할 수 없습니다.

			검사 설정은 점검 단계를 거쳐 명시적으로 적용하기 전까지 사용자 상태나 판정에 영향을 주지
			않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "201",
			description = "CANDIDATE 상태의 검사 설정을 생성했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "400",
			description = "참조 값이 비어 있거나 허용 길이를 넘거나 \"latest\" 별칭입니다. (FLT-VAL-003, FLT-VAL-004)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "운영자 세션이 없거나 유효하지 않습니다. (CMN-VAL-003)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다. (CMN-DOM-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping
	ResponseEntity<ApiResponse<FilterReleaseResponse>> create(@RequestBody @Valid CreateFilterReleaseRequest request);

	@Operation(
		summary = "검사 설정 목록 조회",
		description = """
			등록된 필터링 검사 설정 목록을 조회합니다.

			운영자 세션이 필요합니다. 목록 조회는 GET 요청이므로 CSRF 토큰 없이 호출할 수 있습니다.

			각 설정의 상태와 참조 값을 반환하며, 등록된 설정이 없으면 빈 목록을 반환합니다.

			운영자 세션이 없거나 운영자 권한이 없으면 조회할 수 없습니다.

			목록을 조회해도 현재 적용 중인 설정이나 설정 상태는 바뀌지 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200",
			description = "검사 설정 목록을 조회했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "운영자 세션이 없거나 유효하지 않습니다. (CMN-VAL-003)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "운영자 권한이 없으면 조회할 수 없습니다. (CMN-DOM-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping
	ResponseEntity<ApiResponse<List<FilterReleaseResponse>>> findAll();

	@Operation(
		summary = "검사 설정 상세 조회",
		description = """
			지정한 필터링 검사 설정의 참조 값과 현재 상태를 조회합니다.

			운영자 세션이 필요합니다. 조회 요청은 CSRF 토큰 없이 호출할 수 있습니다.

			조회에 성공하면 해당 설정의 상태, 생성 시각과 적용 시각을 반환합니다.

			지정한 식별자의 설정이 없으면 조회할 수 없습니다.

			이 API는 설정을 생성하거나 적용하지 않고 현재 저장된 값을 보여주기만 합니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "운영자 세션이 없거나 유효하지 않습니다. (CMN-VAL-003)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "운영자 권한이 없으면 조회할 수 없습니다. (CMN-DOM-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404",
			description = "release를 찾을 수 없습니다. (FLT-DOM-005)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/{releaseId}")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> find(
		@Parameter(description = "조회할 검사 설정 식별자입니다.") @PathVariable long releaseId);

	@Operation(
		summary = "검사 결과 등록하기 (→ OFFLINE_EVALUATED)",
		description = """
			외부에서 완료한 검사 결과를 해당 검사 설정에 기록해 OFFLINE_EVALUATED 상태로 전환합니다.

			운영자 세션과 CSRF 토큰이 필요하며, 변경 사유를 함께 보내야 합니다. CANDIDATE 상태의
			설정만 전환할 수 있습니다.

			성공하면 OFFLINE_EVALUATED 상태로 바뀐 검사 설정을 반환합니다.

			설정이 없거나 CANDIDATE 상태가 아니면 전환할 수 없습니다.

			외부 검사를 실제로 실행하거나 합격 여부를 판정하지 않습니다. 이 API는 완료된 결과를
			기록하고 다음 점검 단계로 이동시키는 역할만 합니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OFFLINE_EVALUATED로 전이했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "운영자 세션이 없거나 유효하지 않습니다. (CMN-VAL-003)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다. (CMN-DOM-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404",
			description = "검사 설정을 찾을 수 없습니다. (FLT-DOM-005)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "현재 검사 설정 상태에서는 결과를 등록할 수 없습니다. (FLT-DOM-004)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{releaseId}/offline-evaluation")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> markOfflineEvaluated(
		@Parameter(description = "검사 결과를 등록할 설정 식별자입니다.") @PathVariable long releaseId,
		@RequestBody @Valid OperatorReasonRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "검사 설정을 shadow로 시험하기 (→ SHADOW)",
		description = """
			OFFLINE_EVALUATED 상태의 검사 설정을 실제 사용자 판정에 쓰지 않는 SHADOW 단계로 전환합니다.

			운영자 세션과 CSRF 토큰이 필요하며, 변경 사유를 함께 보내야 합니다. OFFLINE_EVALUATED
			상태의 설정만 전환할 수 있습니다.

			성공하면 SHADOW 상태로 바뀐 검사 설정을 반환합니다.

			설정이 없거나 현재 상태가 OFFLINE_EVALUATED가 아니면 전환할 수 없습니다.

			SHADOW 단계는 동작을 관찰하기 위한 비권위 단계이므로 사용자 상태나 닉네임 동기 용량을
			바꾸지 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "SHADOW로 전이했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "운영자 세션이 없거나 유효하지 않습니다. (CMN-VAL-003)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다. (CMN-DOM-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404",
			description = "검사 설정을 찾을 수 없습니다. (FLT-DOM-005)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "현재 검사 설정 상태에서는 SHADOW로 전환할 수 없습니다. (FLT-DOM-004)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{releaseId}/shadow")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> designateShadow(
		@Parameter(description = "SHADOW로 전환할 설정 식별자입니다.") @PathVariable long releaseId,
		@RequestBody @Valid OperatorReasonRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "검사 설정을 canary로 시험하기 (→ CANARY)",
		description = """
			SHADOW 상태의 검사 설정을 제한된 범위에서 확인하는 CANARY 단계로 전환합니다.

			운영자 세션과 CSRF 토큰이 필요하며, 변경 사유를 함께 보내야 합니다. SHADOW 상태의
			설정만 전환할 수 있습니다.

			성공하면 CANARY 상태로 바뀐 검사 설정을 반환합니다.

			설정이 없거나 현재 상태가 SHADOW가 아니면 전환할 수 없습니다.

			CANARY 단계도 아직 권위 있는 적용 상태가 아니므로, 이 호출만으로 모든 사용자 판정에
			새 설정을 사용하지 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "CANARY로 전이했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "운영자 세션이 없거나 유효하지 않습니다. (CMN-VAL-003)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다. (CMN-DOM-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404",
			description = "검사 설정을 찾을 수 없습니다. (FLT-DOM-005)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "현재 검사 설정 상태에서는 CANARY로 전환할 수 없습니다. (FLT-DOM-004)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{releaseId}/canary")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> designateCanary(
		@Parameter(description = "CANARY로 전환할 설정 식별자입니다.") @PathVariable long releaseId,
		@RequestBody @Valid OperatorReasonRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "이 설정을 실제로 적용하기 (→ PROMOTED)",
		description = """
			CANARY 단계까지 확인한 검사 설정을 지금부터 실제 판정에 사용하는 설정으로 적용합니다.

			운영자 세션과 CSRF 토큰이 필요하며, 적용 사유를 함께 보내야 합니다. CANARY 상태의
			설정만 적용할 수 있습니다.

			적용에 성공하면 해당 설정은 PROMOTED 상태가 됩니다. 기존에 적용 중인 설정이 있으면
			같은 작업 안에서 ROLLED_BACK 상태로 바뀌어 동시에 두 설정이 적용되지 않습니다.

			설정이 없거나 CANARY 상태가 아니거나 이미 적용 중인 설정을 다시 적용하려 하면 실패합니다.

			설정을 만들거나 점검 단계를 올리는 것만으로는 실제 판정 설정이 바뀌지 않습니다. 이 API를
			호출해야 적용 상태가 바뀝니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "PROMOTED로 전이했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "운영자 세션이 없거나 유효하지 않습니다. (CMN-VAL-003)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다. (CMN-DOM-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404",
			description = "검사 설정을 찾을 수 없습니다. (FLT-DOM-005)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "현재 검사 설정 상태에서는 적용할 수 없거나 이미 적용 중인 설정입니다. (FLT-DOM-004)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{releaseId}/promote")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> promote(
		@Parameter(description = "실제로 적용할 설정 식별자입니다.") @PathVariable long releaseId,
		@RequestBody @Valid OperatorReasonRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "이전에 적용한 설정을 다시 적용하기 (→ PROMOTED)",
		description = """
			이전에 적용했다가 ROLLED_BACK 상태가 된 검사 설정을 다시 실제 판정에 사용하는 설정으로
			적용합니다.

			운영자 세션과 CSRF 토큰이 필요하며, 재적용 사유를 함께 보내야 합니다. ROLLED_BACK 상태의
			설정만 재적용할 수 있습니다.

			성공하면 해당 설정은 PROMOTED 상태가 됩니다. 현재 적용 중인 다른 설정이 있으면 같은
			작업 안에서 ROLLED_BACK 상태로 바뀝니다.

			설정이 없거나 ROLLED_BACK 상태가 아니면 재적용할 수 없습니다.

			이 API는 새 설정을 만들거나 이전 설정을 삭제하지 않고, 이미 등록된 설정의 적용 상태만
			바꿉니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "다시 PROMOTED로 전이했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "운영자 세션이 없거나 유효하지 않습니다. (CMN-VAL-003)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다. (CMN-DOM-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404",
			description = "검사 설정을 찾을 수 없습니다. (FLT-DOM-005)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "ROLLED_BACK 상태가 아니어서 다시 적용할 수 없습니다. (FLT-DOM-004)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/{releaseId}/rollback")
	ResponseEntity<ApiResponse<FilterReleaseResponse>> rollback(
		@Parameter(description = "다시 적용할 설정 식별자입니다.") @PathVariable long releaseId,
		@RequestBody @Valid OperatorReasonRequest request,
		@Parameter(hidden = true) Authentication authentication);

}
