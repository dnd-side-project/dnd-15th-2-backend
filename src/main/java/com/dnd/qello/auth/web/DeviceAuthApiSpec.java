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
			설치 식별자와 검증된 국가·기준 지역으로 계정과 기기 자격증명을 만들고 첫 액세스 토큰을 발급한다.
			countryCode는 ISO 3166-1 alpha-2 국가 코드이며, coarseRegionCode의 최상위 국가와 일치해야 한다.

			deviceSecret은 이 응답에서만 평문으로 나간다. 서버는 해시만 보관하므로 다시 받을 수 없다.
			클라이언트가 이 값을 잃으면 재발급 경로를 쓸 수 없다.

			인증이 필요 없다. 등록 자체가 인증 수단을 얻는 과정이다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "201",
			description = "기기를 등록했습니다. deviceSecret과 첫 액세스 토큰이 함께 발급됩니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "400",
			description = "국가가 누락되었거나 지원되지 않거나 기준 지역과 일치하지 않습니다. (AUT-VAL-004)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "409",
			description = "이미 등록된 installationId입니다. (AUT-APP-005)",
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
			등록 때 받은 deviceSecret으로 새 액세스 토큰을 발급한다.

			installationId는 deviceSecret 조회 결과의 교차 검증용이다. 둘이 같은 기기를 가리키지
			않으면 자격증명 불일치와 같은 401로 나간다.

			인증이 필요 없다. 이 경로가 곧 토큰을 얻는 경로다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200",
			description = "새 액세스 토큰을 발급했습니다."),
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
