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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

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
@Tag(name = "백오피스 인증", description = "운영자 세션 로그인과 로그아웃")
public interface OperatorLoginApiSpec {

	@Operation(
		summary = "운영자 로그인",
		description = """
			운영자 자격증명을 확인하고 세션 쿠키를 발급합니다.

			로그인 자체에는 운영자 세션이 필요하지 않지만, CSRF 보호가 켜져 있으므로
			GET /admin/csrf에서 받은 토큰을 함께 보내야 합니다. loginId와 password는 필수입니다.

			성공하면 세션 쿠키와 로그인한 운영자의 계정 식별자를 반환합니다.

			존재하지 않는 loginId와 잘못된 비밀번호는 같은 401로 응답합니다. 사용할 수 없는 계정은
			403, 반복 실패로 잠긴 자격증명은 423, CSRF 토큰이 없거나 유효하지 않으면 403입니다.

			로그인 응답 본문에는 비밀번호나 액세스 토큰을 담지 않습니다. 이후 백오피스 요청은
			발급된 세션 쿠키를 사용합니다.""")
	@ApiResponses({
		// content를 비워 두면 springdoc이 반환 타입으로 채운다. 200을 아예 적지 않으면
		// 선언한 오류 응답만 남고 성공 응답이 통째로 빠진다.
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200",
			description = "로그인에 성공했습니다. 세션 쿠키가 발급됩니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "400",
			description = "loginId 또는 password가 비어 있거나 loginId 길이가 허용 범위를 벗어났습니다. "
				+ "(CMN-VAL-001, AUT-VAL-001, AUT-VAL-002)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "로그인 정보가 올바르지 않습니다. (AUT-APP-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "403",
			description = "사용할 수 없는 계정이거나 CSRF 토큰이 유효하지 않습니다. (AUT-APP-003, CMN-DOM-001)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "423",
			description = "연속 실패로 잠긴 계정입니다. 잠금이 풀리면 해소됩니다. (AUT-APP-002)",
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON_VALUE,
				schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/login")
	ResponseEntity<ApiResponse<OperatorSessionResponse>> login(
		@RequestBody @Valid OperatorLoginRequest request,
		HttpServletRequest httpRequest,
		HttpServletResponse httpResponse
	);

	@Operation(
		summary = "운영자 로그아웃",
		description = """
			현재 운영자 세션을 무효화합니다.

			운영자 세션과 CSRF 토큰이 필요합니다. 토큰은 GET /admin/csrf에서 받을 수 있습니다.

			성공하면 세션 쿠키가 더 이상 권한을 갖지 않으며 응답 본문은 비어 있습니다.

			세션이 없거나 만료됐으면 401, 운영자 권한이 없거나 CSRF 토큰이 유효하지 않으면
			403으로 응답합니다.

			이 요청은 운영자 세션만 무효화하며 앱 기기 자격증명과 액세스 토큰에는 영향을 주지 않습니다.""")
	@SecurityRequirement(name = "operatorSession")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200",
			description = "로그아웃했습니다. 세션이 무효화됩니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "401",
			description = "세션이 없거나 만료되었습니다. (CMN-VAL-003)",
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
	@PostMapping("/logout")
	ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest);

}
