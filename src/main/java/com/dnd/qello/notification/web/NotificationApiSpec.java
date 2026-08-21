package com.dnd.qello.notification.web;

import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.notification.web.request.UpdateNotificationPreferencesRequest;
import com.dnd.qello.notification.web.response.NotificationCardResponse;
import com.dnd.qello.notification.web.response.NotificationListingResponse;
import com.dnd.qello.notification.web.response.NotificationPreferenceResponse;
import com.dnd.qello.notification.web.response.NotificationSeenResponse;
import com.dnd.qello.notification.web.response.NotificationTargetResponse;
import com.dnd.qello.notification.web.response.UnreadSignalResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "알림함", description = "알림함 목록·미읽음 신호 조회, 열람 기준선 전진, 줄 단위 읽음, 진입 판정 (#176)")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface NotificationApiSpec {

	@Operation(
		summary = "알림함 목록 조회",
		description = "인증 사용자의 알림을 최신순으로 조회합니다. REVOKED·DISMISSED 줄은 제외됩니다. "
			+ "cursorCreatedAt과 cursorNotificationId는 둘 다 지정하거나 둘 다 생략해야 합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "알림함 목록을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "limit 또는 cursor 파라미터가 올바르지 않습니다. (NOT-VAL-006, NOT-VAL-007)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다. (NOT-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다. (NOT-APP-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/notifications")
	ResponseEntity<ApiResponse<NotificationListingResponse>> list(
		@RequestParam(required = false) Instant cursorCreatedAt,
		@RequestParam(required = false) Long cursorNotificationId,
		@RequestParam(defaultValue = "20") int limit,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "미읽음 신호 조회",
		description = "지도 홈의 알림 점(hasUnseen)과 카운터(unreadCount)를 조회합니다. 두 값의 기준선이 다릅니다 — "
			+ "hasUnseen은 열람 기준선(seenAt) 이후 도착한 UNREAD 존재 여부이고, unreadCount는 톱하지 않은 줄의 개수입니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "미읽음 신호를 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다. (NOT-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다. (NOT-APP-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/notifications/unread-count")
	ResponseEntity<ApiResponse<UnreadSignalResponse>> unreadCount(
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "알림 설정 조회",
		description = "인증 사용자 본인의 전역 push, 6종별 push, 방해 금지 시간과 알림함 원장 정책을 조회합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "현재 알림 설정 snapshot을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다. (NOT-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다. (NOT-APP-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/notifications/preferences")
	ResponseEntity<ApiResponse<NotificationPreferenceResponse>> preferences(
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "알림 설정 전체 교체",
		description = "인증 사용자 본인의 전역 push, 6종별 push와 방해 금지 시간 snapshot을 완전 교체하고 canonical 응답을 반환합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장된 알림 설정 snapshot을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청한 알림 설정 값이 계약을 만족하지 않습니다. (NOT-VAL-008)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다. (NOT-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다. (NOT-APP-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/notifications/preferences")
	ResponseEntity<ApiResponse<NotificationPreferenceResponse>> replacePreferences(
		@Valid @RequestBody(required = false) UpdateNotificationPreferencesRequest request,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "알림함 열람 기준선 전진",
		description = "지도 홈의 알림 점을 해제합니다. 서버 시각으로만 전진하며(GREATEST), 반복·역순 호출이 "
			+ "기준선을 과거로 되돌리지 않습니다. 목록의 줄 자체는 지워지지 않습니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "전진된 열람 기준선을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다. (NOT-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다. (NOT-APP-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/notifications/seen")
	ResponseEntity<ApiResponse<NotificationSeenResponse>> markSeen(
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "알림 읽음 처리",
		description = "알림 한 줄을 읽음 처리합니다. 멱등입니다 — 이미 READ면 상태를 바꾸지 않고 현재 값을 반환합니다. "
			+ "REVOKED 줄은 409입니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "읽음 처리된 알림 줄을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다. (NOT-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "알림이 없거나 남의 알림입니다. (NOT-APP-001, NOT-DOM-004)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "취소된(REVOKED) 알림은 읽음 처리할 수 없습니다. (NOT-DOM-003)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/notifications/{notificationId}/read")
	ResponseEntity<ApiResponse<NotificationCardResponse>> markRead(
		@Parameter(description = "알림 식별자", example = "1042") @PathVariable long notificationId,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "알림 진입 판정",
		description = "알림 한 줄의 대상 생존 상태를 다시 평가합니다. 알림함을 열고 수 분 뒤 톱했을 수 있으므로 "
			+ "목록의 판정을 그대로 믿지 않습니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "진입 판정을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다. (NOT-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "알림이 없거나 남의 알림입니다. (NOT-APP-001, NOT-DOM-004)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/notifications/{notificationId}/target")
	ResponseEntity<ApiResponse<NotificationTargetResponse>> target(
		@Parameter(description = "알림 식별자", example = "1042") @PathVariable long notificationId,
		@Parameter(hidden = true) Authentication authentication);
}
