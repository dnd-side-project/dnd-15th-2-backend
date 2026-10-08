package com.dnd.qello.notification.web;

import java.time.Instant;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.dnd.qello.common.openapi.OpenApiConfiguration;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.notification.web.request.PushDeviceRequest;
import com.dnd.qello.notification.web.request.UpdateNotificationPreferencesRequest;
import com.dnd.qello.notification.web.response.NotificationCardResponse;
import com.dnd.qello.notification.web.response.NotificationDismissResponse;
import com.dnd.qello.notification.web.response.NotificationListingResponse;
import com.dnd.qello.notification.web.response.NotificationPreferenceApiResponseSchema;
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

@Tag(name = "알림함", description = "받은 알림을 확인하고, 알림 점을 끄고, 알림에서 원래 글로 넘어갈 수 있는지 확인합니다. 알림을 어떻게 받을지 설정하는 것도 여기서 합니다.")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface NotificationApiSpec {

	@Operation(summary = "알림함 목록 조회", description = "받은 알림을 최신순으로 조회\n앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)"
			+ "\ncursorCreatedAt과 cursorNotificationId는 둘 다 지정하거나 둘 다 생략해야 함")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "알림함 목록을 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "limit 또는 cursor 파라미터가 올바르지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/notifications")
	ResponseEntity<ApiResponse<NotificationListingResponse>> list(
			@Parameter(description = "다음 쪽 조회에 쓸 알림 도착 시각. 앞 응답 nextCursor.createdAt을 그대로 넣습니다. cursorNotificationId와 함께 지정해야 합니다") @RequestParam(required = false) Instant cursorCreatedAt,
			@Parameter(description = "다음 쪽 조회에 쓸 알림 식별자. 앞 응답 nextCursor.notificationId를 그대로 넣습니다. cursorCreatedAt과 함께 지정해야 합니다") @RequestParam(required = false) Long cursorNotificationId,
			@Parameter(description = "한 번에 받을 알림 수. 1 이상 50 이하이며 기본값은 20입니다") @RequestParam(defaultValue = "20") int limit,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "알림함 전체 지우기", description = "지금까지 받은 알림을 알림함에서 모두 지움\n"
			+ "앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)\n"
			+ "서버가 요청을 받은 시각(dismissedAt) 이전에 도착한 알림만 지우며, 그 뒤에 도착한 알림은 남음\n"
			+ "지울 알림이 없어도 성공이며 dismissedCount는 0")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지운 알림 수와 기준 시각을 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@DeleteMapping("/notifications")
	ResponseEntity<ApiResponse<NotificationDismissResponse>> dismissAll(
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "알림 점과 안 읽은 알림 수 조회", description = "알림함에 새로운 알림이 왔음을 알리는 신호(hasUnseen)와 아직 읽지 않은 알림 개수 조회\n"
			+ "앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "미읽음 신호를 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/notifications/unread-count")
	ResponseEntity<ApiResponse<UnreadSignalResponse>> unreadCount(
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "알림 설정 조회", description = "내 알림 설정을 조회(앱 푸시 전체 수신 여부, 알림 6종별 수신 여부, 알림을 받지 않을 시간대)\n"
			+ "앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)\n"
			+ "설정 저장한 적 없어도 기본값 반환(기본값: 모두 켜짐, 알림 받지 않을 시간대X)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "현재 알림 설정을 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/notifications/preferences")
	ResponseEntity<ApiResponse<NotificationPreferenceResponse>> preferences(
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "알림 설정 값들 수정", description = "알림 설정을 통째로 변경, 일부 설정을 수정하면 수정한 값이 반영된 전체 설정값이 전송\n"
			+ "앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)\n"
			+ "type : ANSWER_RECEIVED, ANSWER_REACTED, DIRECTION_POST_RECEIVED, REPORT_RESOLVED, "
			+ "QUESTION_PROPOSAL_REVIEWED, QUESTION_RECOMMENDED")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장된 알림 설정을 반환합니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = NotificationPreferenceApiResponseSchema.class), examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "설정 변경 응답", value = """
					{"status":"success","data":{"pushEnabled":true,"quietHours":{"start":"22:00","end":"07:00","zoneId":"Asia/Seoul"},"preferences":[{"type":"ANSWER_RECEIVED","enabled":true},{"type":"ANSWER_REACTED","enabled":true},{"type":"DIRECTION_POST_RECEIVED","enabled":false},{"type":"REPORT_RESOLVED","enabled":true},{"type":"QUESTION_PROPOSAL_REVIEWED","enabled":false},{"type":"QUESTION_RECOMMENDED","enabled":true}],"inboxRecordingPolicy":"ALWAYS_RECORD"},"timestamp":"2026-09-23T10:00:00Z"}
					"""))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청한 알림 설정 값이 계약을 만족하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/notifications/preferences")
	ResponseEntity<ApiResponse<NotificationPreferenceResponse>> replacePreferences(
			@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = UpdateNotificationPreferencesRequest.class), examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "설정 변경 요청", value = """
					{"pushEnabled":true,"quietHours":{"start":"22:00:00","end":"07:00:00","zoneId":"Asia/Seoul"},"preferences":[{"type":"ANSWER_RECEIVED","enabled":true},{"type":"ANSWER_REACTED","enabled":true},{"type":"DIRECTION_POST_RECEIVED","enabled":false},{"type":"REPORT_RESOLVED","enabled":true},{"type":"QUESTION_PROPOSAL_REVIEWED","enabled":false},{"type":"QUESTION_RECOMMENDED","enabled":true}]}
					"""))) @Valid @RequestBody(required = false) UpdateNotificationPreferencesRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "푸시 기기 등록 또는 최신화", description = "사용자의 FCM registration token을 등록\n앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "기기 등록 또는 최신화가 완료되었습니다.", content = @Content),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "platform 또는 token 값이 계약을 만족하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "푸시 token 보호 처리에 실패했습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/notifications/devices")
	ResponseEntity<ApiResponse<Void>> registerDevice(
			@RequestBody(required = true) PushDeviceRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "푸시 기기 해지", description = "사용자의 FCM registration token을 해지\n앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "기기 해지가 완료되었습니다.", content = @Content),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "platform 또는 token 값이 계약을 만족하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "푸시 token 보호 처리에 실패했습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PostMapping("/notifications/devices/revoke")
	ResponseEntity<ApiResponse<Void>> revokeDevice(
			@RequestBody(required = true) PushDeviceRequest request,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "알림 점 끄기", description = "알림함을 열어봤다는 신호(알림 점)을 끔\n앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "새로 기록된 시각을 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/notifications/seen")
	ResponseEntity<ApiResponse<NotificationSeenResponse>> markSeen(
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "알림 읽음 처리", description = "알림 하나를 읽음으로 표시\n앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "읽음 처리된 알림을 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그런 알림이 없거나 본인이 받은 알림이 아닙니다. 인증 사용자 계정을 찾을 수 없을 때도 같습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "내려간 알림이라 읽음으로 바꿀 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/notifications/{notificationId}/read")
	ResponseEntity<ApiResponse<NotificationCardResponse>> markRead(
			@Parameter(description = "알림 식별자", example = "1042") @PathVariable long notificationId,
			@Parameter(hidden = true) Authentication authentication);

	@Operation(summary = "알림에서 원래 글로 갈 수 있는지 확인", description = """
			알림이 가리키는 글로 넘어갈 수 있는지 확인
			앱 로그인 필요(Authorization 헤더에 앱 액세스 토큰이 필요)
			넘어갈 수 있으면 navigable이 true입니다. 갈 수 없으면 false와 함께 이유가 reason에, 대신 보여줄 화면이 fallback에 담깁니다.

			상태
			- GONE (가리키던 글이 없음)
			- BLOCKED (글은 있지만 차단 당함)
			- HIDDEN (글은 있지만 볼 수 없음 - 예: 검수 중)
			- EXPIRED (질문 글이 만료된 상태)
			- AVAILABLE (위에 상태에 모두 해당하지 않은 상태 - 글로 넘어갈 수 있는 상태)""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지금 넘어갈 수 있는지에 대한 판정을 반환합니다."),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그런 알림이 없거나 본인이 받은 알림이 아닙니다. 인증 사용자 계정을 찾을 수 없을 때도 같습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/notifications/{notificationId}/target")
	ResponseEntity<ApiResponse<NotificationTargetResponse>> target(
			@Parameter(description = "알림 식별자", example = "1042") @PathVariable long notificationId,
			@Parameter(hidden = true) Authentication authentication);
}
