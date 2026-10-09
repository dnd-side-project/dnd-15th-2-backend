package com.dnd.qello.account.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;

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

// AccountWithdrawalController의 문서 계약(#337). 인증된 본인 계정만 탈퇴하거나 철회할 수 있다.
@Tag(name = "계정", description = "내 닉네임 변경, 탈퇴 요청과 철회")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface AccountWithdrawalApiSpec {

	@Operation(summary = "탈퇴 요청", description = """
			내 계정의 탈퇴를 요청합니다. 계정은 바로 지워지지 않고 유예 기간(기본 30일)이 지난 뒤 삭제됩니다.

			요청하면 바로 질문 보내기, 답변하기, 위치 갱신 같은 쓰기 기능을 쓸 수 없고, 다른 사람에게 질문이 전달되거나 알림이 가지 않습니다. 푸시 알림 등록도 해지됩니다.

			유예 기간 안에는 같은 기기로 탈퇴를 철회할 수 있습니다. 유예가 끝나면 닉네임이 비워지고 기기 로그인 정보가 폐기되어 다시 들어올 수 없습니다. 이미 남긴 답변은 지워지지 않고 작성자가 "탈퇴한 사용자"로 표시됩니다.

			이미 탈퇴를 요청한 계정이면 처음 요청 때 정해진 삭제 예정 시각을 그대로 돌려줍니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 요청을 받았습니다. 응답의 scheduledDeletionAt에 계정이 삭제됩니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "지금 계정 상태로는 탈퇴를 요청할 수 없습니다. 차단되었거나 이미 삭제된 계정, 또는 같은 계정에 동시에 들어온 요청입니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping(path = "/api/v1/users/me/withdrawal")
	ResponseEntity<ApiResponse<AccountWithdrawalResponse>> requestWithdrawal(
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "탈퇴 철회", description = """
			탈퇴 요청을 철회하고 계정을 다시 쓸 수 있게 합니다. 닉네임은 그대로 남아 있습니다.

			철회한 뒤 푸시 알림 등록과 위치 갱신을 다시 보내야 알림과 질문을 받을 수 있습니다.

			탈퇴를 요청하지 않은 계정이면 아무것도 바꾸지 않고 200을 반환합니다. 삭제 예정 시각이 지났으면 철회할 수 없습니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴를 철회했습니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "철회할 수 없습니다. 삭제 예정 시각이 지났거나, 차단되었거나 삭제된 계정, 또는 같은 계정에 동시에 들어온 요청입니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping(path = "/api/v1/users/me/withdrawal")
	ResponseEntity<ApiResponse<AccountWithdrawalResponse>> cancelWithdrawal(
			@Parameter(hidden = true) Authentication authentication);
}
