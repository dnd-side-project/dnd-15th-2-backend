package com.dnd.qello.direction.web;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.direction.web.request.LocationConsentRequest;
import com.dnd.qello.direction.web.response.LocationConsentResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "위치정보 동의", description = "인증 사용자 본인의 위치정보 이용 동의와 철회")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface LocationConsentApiSpec {

	@Operation(summary = "위치정보 이용 동의", description = """
			현재 약관 버전에 대한 동의를 서버 시각으로 기록합니다.

			앱 액세스 토큰이 필요합니다. 같은 버전으로 다시 동의해도 요청마다 이력이 한 건 추가됩니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "동의를 기록하고 현재 상태를 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "위치정보 동의 기능을 사용할 수 없는 계정입니다. (DIR-APP-007)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "계정을 찾을 수 없습니다.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 약관 버전과 일치하지 않습니다. (DIR-APP-010)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ApiResponse<LocationConsentResponse>> consent(
			@RequestBody @Valid LocationConsentRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "위치정보 이용 동의 철회", description = """
			모든 약관 버전의 동의를 철회하고 저장된 현재 위치를 같은 트랜잭션에서 삭제합니다.

			앱 액세스 토큰이 필요합니다. 반복 요청도 성공마다 이력이 한 건 추가됩니다.
			철회 전에 동의를 확인한 미리보기와 발송 요청은 완료될 수 있습니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "철회를 기록하고 현재 위치를 삭제했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "위치정보 동의 기능을 사용할 수 없는 계정입니다. (DIR-APP-007)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "계정을 찾을 수 없습니다.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/withdrawals")
	ResponseEntity<ApiResponse<LocationConsentResponse>> withdraw(
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "현재 위치정보 동의 상태 조회", description = """
			사용자 전체 이력 중 가장 최근에 추가된 한 건과 현재 약관 버전으로 상태를 판단합니다.

			앱 액세스 토큰이 필요합니다. 조회는 이력을 추가하지 않습니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "현재 동의 상태를 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "위치정보 동의 기능을 사용할 수 없는 계정입니다. (DIR-APP-007)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "계정을 찾을 수 없습니다.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/current")
	ResponseEntity<ApiResponse<LocationConsentResponse>> current(
			@Parameter(hidden = true) Authentication authentication);
}
