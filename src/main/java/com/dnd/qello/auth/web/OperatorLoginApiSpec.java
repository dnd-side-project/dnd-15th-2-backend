package com.dnd.qello.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

// OperatorLoginController의 문서 계약.
//
// 문서 애노테이션은 본문보다 훨씬 길어서 구현과 같은 파일에 두면 로직이 애노테이션
// 사이에 파묻힌다. 계약을 인터페이스로 분리해 컨트롤러에는 동작만 남긴다.
//
// 매핑도 여기에 둔다. 경로와 그 경로의 문서가 갈라지면 한쪽만 고치는 변경이 나온다.
// 클래스 수준 @RequestMapping과 @RestController는 빈 정의라 구현에 남긴다.
//
// swagger의 @ApiResponse는 이 저장소의 응답 래퍼(ApiResponse)와 이름이 겹친다.
// 래퍼가 반환 타입으로 훨씬 자주 쓰이므로 그쪽을 import하고 애노테이션은 정규화한다.
@Tag(name = "백오피스 인증", description = "운영자 로그인과 로그아웃")
public interface OperatorLoginApiSpec {

	@Operation(summary = "운영자 로그인", description = """
			운영자 계정으로 로그인하고 세션 쿠키를 발급합니다.

			로그인 전에 GET /admin/csrf로 CSRF 토큰을 받아 함께 보내야 합니다.

			이후 백오피스 요청은 발급된 세션 쿠키로 보냅니다. 같은 IP에서 짧은 시간에 너무 많이 호출하면 429를 반환합니다.""")
	@ApiResponses({
			// content를 비워 두면 springdoc이 반환 타입으로 채운다. 200을 아예 적지 않으면
			// 선언한 오류 응답만 남고 성공 응답이 통째로 빠진다.
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인에 성공했습니다. 세션 쿠키가 발급됩니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "loginId 또는 password가 비어 있거나 loginId 길이가 허용 범위를 벗어났습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 정보가 올바르지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "사용할 수 없는 계정이거나 CSRF 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "로그인을 연속으로 실패해 잠긴 계정입니다. 잠시 후 다시 시도해 주세요.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "같은 IP의 로그인 요청이 한도를 넘었습니다. 잠시 후 다시 시도해 주세요.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/login")
	ResponseEntity<ApiResponse<OperatorSessionResponse>> login(
			@RequestBody @Valid OperatorLoginRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse);

	@Operation(summary = "운영자 로그아웃", description = """
			운영자 세션을 종료합니다.

			운영자 로그인과 CSRF 토큰이 필요합니다.""")
	@SecurityRequirement(name = "operatorSession")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃했습니다. 세션이 무효화됩니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "세션이 없거나 만료되었습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "운영자 권한이 없거나 CSRF 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/logout")
	ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest);

}
