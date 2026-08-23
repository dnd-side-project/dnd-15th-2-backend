package com.dnd.qello.auth.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

// DeviceAuthController의 문서 계약. 분리 근거는 OperatorLoginApiSpec에 있다.
//
// 두 경로 모두 permitAll이라 @SecurityRequirement를 붙이지 않는다. 등록과 재발급이
// 곧 인증 수단을 얻는 과정이라 그 전에는 인증할 수 없다. appAccessToken을 여기 적으면
// 열린 경로가 문서에서 거짓으로 인증 필요가 된다.
@Tag(name = "앱 기기 인증", description = "앱 기기 등록과 액세스 토큰 재발급")
public interface DeviceAuthApiSpec {

	@Operation(
		summary = "기기 등록",
		description = """
			설치 정보를 확인해 새 사용자 계정과 기기 자격증명을 만들고 첫 액세스 토큰을 발급합니다.

			인증 없이 호출할 수 있습니다. installationId, platform, countryCode, coarseRegionCode,
			locale, timezone이 필요하고 nickname은 선택입니다. countryCode는 ISO 3166-1 alpha-2
			국가 코드이며 coarseRegionCode의 최상위 국가와 일치해야 합니다.

			성공하면 새 계정 식별자와 첫 액세스 토큰, 만료까지 남은 시간, 재발급에 사용할
			deviceSecret을 반환합니다.

			이미 등록된 installationId이거나 국가·지역·계정 입력값이 정책에 맞지 않으면 등록하지 않습니다.
			nickname을 보낸 경우 중복·유해성 검사를 통과해야 합니다.

			deviceSecret은 이 응답에서만 평문으로 반환합니다. 서버에는 해시만 보관하므로 이 값을
			잃으면 새 기기를 등록해야 합니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "201",
			description = "기기를 등록했습니다. deviceSecret과 첫 액세스 토큰이 함께 발급됩니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "400",
			description = "필수 값·기기 식별자·국가·지역·계정 입력값이 올바르지 않거나 닉네임 검사를 통과하지 못했습니다. "
				+ "(CMN-VAL-001, AUT-VAL-002, AUT-VAL-003, AUT-VAL-004, ACC-VAL-002, ACC-VAL-003, ACC-VAL-004, ACC-DOM-005)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "이미 등록된 기기이거나 닉네임이 이미 사용 중입니다. (AUT-APP-005, ACC-APP-002)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "503",
			description = "닉네임 검증 서비스를 일시적으로 사용할 수 없습니다. (ACC-INFRA-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/devices")
	ResponseEntity<ApiResponse<DeviceRegistrationResponse>> register(
		@RequestBody @Valid DeviceRegistrationRequest request);

	@Operation(
		summary = "액세스 토큰 재발급",
		description = """
			기기 등록 때 받은 deviceSecret과 installationId로 새 액세스 토큰을 발급합니다.

			인증 없이 호출할 수 있습니다. 두 값은 같은 기기를 가리켜야 하며, 등록된 계정이
			사용 가능한 상태여야 합니다.

			성공하면 새 accessToken과 만료까지 남은 시간(초)을 반환하고 기기 자격증명의
			마지막 사용 시각을 갱신합니다.

			두 값이 일치하지 않거나 자격증명이 해지됐으면 재발급하지 않습니다. 계정이 사용할 수
			없는 상태여도 토큰을 발급하지 않습니다.

			deviceSecret은 기기 등록 성공 응답에서만 확인할 수 있습니다. 서버가 비밀값을
			복원해 주는 경로는 없습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200",
			description = "새 액세스 토큰을 발급했습니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "400",
			description = "installationId 또는 deviceSecret이 비어 있습니다. (CMN-VAL-001, AUT-VAL-002)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "기기 자격증명이 유효하지 않습니다. (AUT-APP-006)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "사용할 수 없는 계정입니다. (AUT-APP-003)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/token")
	ResponseEntity<ApiResponse<DeviceTokenResponse>> reissue(
		@RequestBody @Valid DeviceTokenRequest request);

}
