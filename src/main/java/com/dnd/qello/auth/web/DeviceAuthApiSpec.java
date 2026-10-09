package com.dnd.qello.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

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

// DeviceAuthController의 문서 계약. 분리 근거는 OperatorLoginApiSpec에 있다.
//
// 두 경로 모두 permitAll이라 @SecurityRequirement를 붙이지 않는다. 등록과 재발급이
// 곧 인증 수단을 얻는 과정이라 그 전에는 인증할 수 없다. appAccessToken을 여기 적으면
// 열린 경로가 문서에서 거짓으로 인증 필요가 된다.
@Tag(name = "앱 기기 인증", description = "앱 기기 등록과 액세스 토큰 재발급")
public interface DeviceAuthApiSpec {

	@Operation(summary = "기기 등록", description = """
			기기를 등록하고 새 계정을 만듭니다. 첫 액세스 토큰과, 토큰을 다시 발급받을 때 쓸 deviceSecret을 함께 반환합니다.

			로그인 없이 호출할 수 있습니다. 같은 IP에서 짧은 시간에 너무 많이 호출하면 429를 반환합니다.

			deviceSecret은 이 응답에서만 받을 수 있으니 앱에 저장해야 합니다. 잃어버리면 기기를 새로 등록해야 합니다. countryCode는 지원 국가의 ISO 3166-1 alpha-2 코드여야 합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "기기를 등록했습니다. deviceSecret과 첫 액세스 토큰이 함께 발급됩니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수 값·기기 식별자·국가·계정 입력값이 올바르지 않거나 닉네임 검사를 통과하지 못했습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 등록된 기기이거나 닉네임이 이미 사용 중입니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "같은 IP의 기기 등록 요청이 한도를 넘었습니다. 잠시 후 다시 시도해 주세요.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "닉네임 검증 서비스를 일시적으로 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/devices")
	ResponseEntity<ApiResponse<DeviceRegistrationResponse>> register(
			@RequestBody @Valid DeviceRegistrationRequest request,
			HttpServletRequest httpRequest);

	@Operation(summary = "액세스 토큰 재발급", description = """
			기기 등록 때 받은 installationId와 deviceSecret으로 새 액세스 토큰을 발급합니다.

			탈퇴 유예 중인 계정도 발급합니다. 응답의 accountStatus가 WITHDRAWAL_PENDING이면 탈퇴 철회 화면을 보여 주세요.
			차단되거나 탈퇴가 끝난 계정은 발급하지 않습니다.

			로그인 없이 호출할 수 있습니다. 같은 IP에서 짧은 시간에 너무 많이 호출하면 429를 반환합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "새 액세스 토큰을 발급했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "installationId 또는 deviceSecret이 비어 있습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "기기 자격증명이 유효하지 않습니다. 탈퇴가 끝난 계정의 자격증명도 여기에 해당합니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "차단되었거나 사용할 수 없는 계정입니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "같은 IP의 토큰 재발급 요청이 한도를 넘었습니다. 잠시 후 다시 시도해 주세요.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/token")
	ResponseEntity<ApiResponse<DeviceTokenResponse>> reissue(
			@RequestBody @Valid DeviceTokenRequest request,
			HttpServletRequest httpRequest);

}
