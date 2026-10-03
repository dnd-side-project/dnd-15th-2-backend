package com.dnd.qello.question.web;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.question.web.response.RecommendedQuestionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "추천 질문", description = "질문 전송에 사용할 질문 목록 조회")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface QuestionRecommendationApiSpec {

	@Operation(summary = "추천 질문 목록 조회 (임시: 전체 질문 반환)", description = """
			질문 전송 화면에서 고를 수 있는 질문 목록을 조회합니다.

			앱 로그인이 필요합니다. Authorization 헤더에 앱 액세스 토큰이 필요합니다.

			현재는 사용자 구분 없이 지금 사용할 수 있는 승인 질문 전체를 식별자 순으로 반환합니다. \
			사용자별 추천 배정이 완성되면 이 사용자에게 이번 주기에 배정된 질문으로 바뀝니다.

			approvedQuestionId는 질문 전송 요청에 그대로 사용합니다. \
			목록을 받은 뒤 질문의 사용 기간이 끝나면 전송이 거부될 수 있으니 목록을 다시 조회하세요.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "추천 질문 목록을 반환합니다. 사용할 수 있는 질문이 없으면 빈 목록입니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 없거나 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 질문 기능을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/recommendations")
	ResponseEntity<ApiResponse<List<RecommendedQuestionResponse>>> findRecommendations(
			@Parameter(hidden = true) Authentication authentication);
}
