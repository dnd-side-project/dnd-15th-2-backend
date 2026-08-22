package com.dnd.qello.direction.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.direction.web.request.UpdateActiveUserPresenceRequest;
import com.dnd.qello.direction.web.response.UpdateActiveUserPresenceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "방향 위치", description = "방향 preview와 매칭에 사용할 최신 위치 갱신")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface ActiveUserPresenceApiSpec {

	@Operation(
		summary = "현재 위치 갱신",
		description = """
			내 최신 위치와 질문을 받을지 여부를 저장합니다.

			앱 로그인이 필요합니다. 방향 미리보기와 질문글 보내기가 이 위치를 씁니다.

			이미 저장된 것보다 오래되거나 같은 시각의 위치는 적용하지 않고 applied를 \
			false로 돌려줍니다. 이때도 요청은 성공(200)입니다.

			정확한 좌표는 응답에 돌려주지 않습니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200", description = "갱신 결과를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "400", description = "위치 값이 없거나 정확도 또는 관측 시각이 허용 범위를 벗어났습니다. (DIR-VAL-002, DIR-VAL-007, DIR-VAL-008)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403", description = "현재 계정은 위치를 갱신할 수 없습니다. (DIR-APP-007)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404", description = "갱신할 계정을 찾을 수 없습니다. (DIR-APP-006)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping
	ResponseEntity<ApiResponse<UpdateActiveUserPresenceResponse>> update(
		@RequestBody @Valid UpdateActiveUserPresenceRequest request,
		@Parameter(hidden = true) Authentication authentication);
}
