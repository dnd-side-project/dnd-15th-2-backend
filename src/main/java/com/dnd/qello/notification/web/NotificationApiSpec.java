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

@Tag(name = "알림함", description = "받은 알림을 확인하고, 알림 점을 끄고, 알림에서 원래 글로 넘어갈 수 있는지 확인합니다. 알림을 어떻게 받을지 설정하는 것도 여기서 합니다.")
@SecurityRequirement(name = OpenApiConfiguration.APP_ACCESS_TOKEN_SCHEME)
public interface NotificationApiSpec {

	@Operation(
		summary = "알림함 목록 조회",
		description = """
			받은 알림을 최신순으로 보여줍니다.

			앱 로그인이 필요하며 본인이 받은 알림만 나옵니다.

			한 번에 1개에서 50개까지 받을 수 있고 기본값은 20개입니다.

			다음 쪽을 부를 때는 앞 응답 nextCursor의 두 값을 cursorCreatedAt과 \
			cursorNotificationId에 함께 넣습니다. 둘 중 하나만 넣으면 오류입니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "알림함 목록을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "limit 또는 cursor 파라미터가 올바르지 않습니다. (NOT-VAL-006, NOT-VAL-007)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다. (NOT-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다. (NOT-APP-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/notifications")
	ResponseEntity<ApiResponse<NotificationListingResponse>> list(
		@Parameter(description = "다음 쪽 조회에 쓸 알림 도착 시각. 앞 응답 nextCursor.createdAt을 그대로 넣습니다. cursorNotificationId와 함께 지정해야 합니다")
		@RequestParam(required = false) Instant cursorCreatedAt,
		@Parameter(description = "다음 쪽 조회에 쓸 알림 식별자. 앞 응답 nextCursor.notificationId를 그대로 넣습니다. cursorCreatedAt과 함께 지정해야 합니다")
		@RequestParam(required = false) Long cursorNotificationId,
		@Parameter(description = "한 번에 받을 알림 수. 1 이상 50 이하이며 기본값은 20입니다")
		@RequestParam(defaultValue = "20") int limit,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "알림 점과 안 읽은 알림 수 조회",
		description = """
			지도 홈에 띄울 알림 점과 아직 읽지 않은 알림 개수를 함께 돌려줍니다.

			앱 로그인이 필요합니다.

			두 값은 기준이 달라 서로 어긋나 보일 수 있습니다.
			알림 점(hasUnseen)은 알림함을 마지막으로 연 뒤에 새 알림이 왔는지만 봅니다.
			개수(unreadCount)는 아직 읽지 않은 알림을 전부 셉니다.

			알림함을 열기만 하고 아무것도 읽지 않으면 점은 꺼지지만 개수는 그대로입니다.""")
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
		description = """
			지금 저장된 알림 설정을 보여줍니다. 앱 푸시를 전부 받을지, 알림 6종을 \
			각각 받을지, 알림을 받지 않을 시간대를 언제로 둘지가 들어 있습니다.

			앱 로그인이 필요합니다.

			설정을 한 번도 저장한 적이 없어도 기본값이 채워져 돌아옵니다. \
			기본값은 전부 켜짐이고 알림을 받지 않을 시간대는 없습니다.

			푸시를 꺼도 알림함에는 그대로 쌓입니다. 이 설정은 푸시를 보낼지만 정합니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "현재 알림 설정을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다. (NOT-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다. (NOT-APP-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/notifications/preferences")
	ResponseEntity<ApiResponse<NotificationPreferenceResponse>> preferences(
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "알림 설정 통째로 바꾸기",
		description = """
			알림 설정을 보낸 값으로 통째로 바꿉니다.

			앱 로그인이 필요합니다.

			일부만 보내 고칠 수 없습니다. 푸시 전체 허용 여부와 알림 6종 설정을 매번 \
			전부 보내야 합니다. 6종을 빠뜨리거나 같은 종류를 두 번 보내면 저장하지 않습니다.

			알림을 받지 않을 시간대는 보내지 않거나 null로 두면 꺼집니다. 켜려면 시작 \
			시각, 종료 시각, 시간대를 모두 채워야 하고 시작과 종료가 같으면 안 됩니다.

			저장한 뒤에는 조회 API와 같은 형식으로 저장된 설정을 돌려줍니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장된 알림 설정을 반환합니다."),
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
		summary = "알림 점 끄기",
		description = """
			지도 홈에 떠 있는 알림 점을 끕니다.

			앱 로그인이 필요합니다.

			알림함을 마지막으로 연 시각을 지금 시각으로 올립니다. 이 시각은 서버가 \
			정하므로 요청 본문이 없고, 여러 번 불러도 시각이 과거로 돌아가지 않습니다.

			알림을 읽음으로 바꾸지는 않습니다. 목록의 알림은 그대로 남고 안 읽은 알림 \
			개수도 줄지 않습니다. 읽음으로 바꾸려면 읽음 처리 API를 따로 부릅니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "새로 기록된 시각을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다. (NOT-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "인증 사용자 계정을 찾을 수 없습니다. (NOT-APP-001)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/notifications/seen")
	ResponseEntity<ApiResponse<NotificationSeenResponse>> markSeen(
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "알림 읽음 처리",
		description = """
			알림 하나를 읽음으로 표시합니다.

			앱 로그인이 필요하고 본인이 받은 알림만 바꿀 수 있습니다.

			읽음으로 바뀐 알림과 읽은 시각을 돌려줍니다. 안 읽은 알림 개수도 그만큼 줄어듭니다.

			같은 알림을 여러 번 불러도 안전합니다. 이미 읽은 알림이면 읽은 시각을 \
			다시 쓰지 않고 지금 값을 그대로 돌려줍니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "읽음 처리된 알림을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다. (NOT-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그런 알림이 없거나 본인이 받은 알림이 아닙니다. 인증 사용자 계정을 찾을 수 없을 때도 같습니다. (NOT-APP-001, NOT-DOM-004)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "내려간 알림이라 읽음으로 바꿀 수 없습니다. (NOT-DOM-003)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@PutMapping("/notifications/{notificationId}/read")
	ResponseEntity<ApiResponse<NotificationCardResponse>> markRead(
		@Parameter(description = "알림 식별자", example = "1042") @PathVariable long notificationId,
		@Parameter(hidden = true) Authentication authentication);

	@Operation(
		summary = "알림에서 원래 글로 갈 수 있는지 확인",
		description = """
			이 알림이 가리키는 글로 지금 넘어갈 수 있는지 확인합니다.

			앱 로그인이 필요하고 본인이 받은 알림만 확인할 수 있습니다.

			넘어갈 수 있으면 navigable이 true입니다.
			갈 수 없으면 false와 함께 갈 수 없는 이유가 reason에 담기고, \
			대신 보여줄 화면이 fallback에 담깁니다.

			알림함을 연 뒤 시간이 지나 원래 글이 지워지거나 기간이 끝났을 수 있습니다. \
			목록에 담긴 판정을 그대로 쓰지 말고 누르는 순간 이 API로 다시 확인합니다.""")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지금 넘어갈 수 있는지에 대한 판정을 반환합니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "앱 액세스 토큰이 유효하지 않습니다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 계정은 알림함을 사용할 수 없습니다. (NOT-APP-002)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그런 알림이 없거나 본인이 받은 알림이 아닙니다. 인증 사용자 계정을 찾을 수 없을 때도 같습니다. (NOT-APP-001, NOT-DOM-004)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	@GetMapping("/notifications/{notificationId}/target")
	ResponseEntity<ApiResponse<NotificationTargetResponse>> target(
		@Parameter(description = "알림 식별자", example = "1042") @PathVariable long notificationId,
		@Parameter(hidden = true) Authentication authentication);
}
