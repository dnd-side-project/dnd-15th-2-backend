/**
 * Created at: 2026-08-21T20:20:00+09:00
 * Source scenario: TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-014 through
 * TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-016
 */
package com.dnd.qello.notification.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.dnd.qello.common.error.ApiErrorResponseFactory;
import com.dnd.qello.common.error.ConstraintExceptionMapper;
import com.dnd.qello.common.web.GlobalExceptionHandler;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.notification.domain.NotificationPreferenceSnapshot;
import com.dnd.qello.notification.domain.NotificationQuietHours;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.service.NotificationInboxService;
import com.dnd.qello.notification.service.NotificationPreferenceService;
import com.dnd.qello.notification.service.PushDeviceService;
import com.dnd.qello.notification.service.UpdateNotificationPreferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceApiMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-08-21T11:20:00Z");
	private static final long USER_ID = 11L;

	@Mock
	private NotificationInboxService inboxService;

	@Mock
	private NotificationPreferenceService preferenceService;

	@Mock
	private PushDeviceService pushDeviceService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = buildMockMvc(true);
	}

	private MockMvc buildMockMvc(boolean authenticated) {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		return MockMvcBuilders.standaloneSetup(
				new NotificationController(inboxService, preferenceService, pushDeviceService, new ApiResponseFactory(clock)))
			.setCustomArgumentResolvers(new AuthenticationResolver(authenticated))
			.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper()))
			.setValidator(new LocalValidatorFactoryBean())
			.setControllerAdvice(new GlobalExceptionHandler(
				new ApiErrorResponseFactory(clock), new ConstraintExceptionMapper()))
			.build();
	}

	@Test
	@DisplayName("인증 사용자는 전체·6종·quiet와 ALWAYS_RECORD 정책을 조회한다")
	void getsOwnPreferences() throws Exception {
		when(preferenceService.findMine(eq(USER_ID))).thenReturn(snapshot(true, overnightQuietHours(), mixedTypes()));

		mockMvc.perform(get("/api/v1/notifications/preferences"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.pushEnabled").value(true))
			.andExpect(jsonPath("$.data.quietHours.start").value("22:00"))
			.andExpect(jsonPath("$.data.quietHours.end").value("07:00"))
			.andExpect(jsonPath("$.data.quietHours.zoneId").value("Asia/Seoul"))
			.andExpect(jsonPath("$.data.preferences.length()").value(6))
			.andExpect(jsonPath("$.data.preferences[0].type").value("ANSWER_RECEIVED"))
			.andExpect(jsonPath("$.data.preferences[1].type").value("ANSWER_REACTED"))
			.andExpect(jsonPath("$.data.preferences[2].type").value("DIRECTION_POST_RECEIVED"))
			.andExpect(jsonPath("$.data.preferences[3].type").value("REPORT_RESOLVED"))
			.andExpect(jsonPath("$.data.preferences[4].type").value("QUESTION_PROPOSAL_REVIEWED"))
			.andExpect(jsonPath("$.data.preferences[5].type").value("QUESTION_RECOMMENDED"))
			.andExpect(jsonPath("$.data.inboxRecordingPolicy").value("ALWAYS_RECORD"));

		verify(preferenceService).findMine(USER_ID);
	}

	@Test
	@DisplayName("유효한 PUT은 인증 subject만 service에 전달하고 canonical snapshot과 ALWAYS_RECORD 정책을 반환한다")
	void replacesOwnPreferences() throws Exception {
		ArgumentCaptor<UpdateNotificationPreferences> commandCaptor =
			ArgumentCaptor.forClass(UpdateNotificationPreferences.class);
		when(preferenceService.replaceMine(eq(USER_ID), org.mockito.ArgumentMatchers.any()))
			.thenReturn(snapshot(false, overnightQuietHours(), mixedTypes()));

		mockMvc.perform(put("/api/v1/notifications/preferences")
				.contentType("application/json")
				.content(validRequestJson()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.pushEnabled").value(false))
			.andExpect(jsonPath("$.data.preferences.length()").value(6))
			.andExpect(jsonPath("$.data.inboxRecordingPolicy").value("ALWAYS_RECORD"));

		verify(preferenceService).replaceMine(eq(USER_ID), commandCaptor.capture());
		UpdateNotificationPreferences command = commandCaptor.getValue();
		org.assertj.core.api.Assertions.assertThat(command.pushEnabled()).isFalse();
		org.assertj.core.api.Assertions.assertThat(command.quietHours()).isEqualTo(overnightQuietHours());
		org.assertj.core.api.Assertions.assertThat(command.typeEnabled()).isEqualTo(mixedTypes());
	}

	@Test
	@DisplayName("quietHours가 null이면 방해 금지를 끈 canonical snapshot으로 저장한다")
	void replacesOwnPreferencesWithQuietHoursOff() throws Exception {
		ArgumentCaptor<UpdateNotificationPreferences> commandCaptor =
			ArgumentCaptor.forClass(UpdateNotificationPreferences.class);
		when(preferenceService.replaceMine(eq(USER_ID), any()))
			.thenReturn(snapshot(true, null, mixedTypes()));

		mockMvc.perform(put("/api/v1/notifications/preferences")
				.contentType("application/json")
				.content(noQuietHoursRequestJson()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.pushEnabled").value(true))
			.andExpect(jsonPath("$.data.quietHours").doesNotExist())
			.andExpect(jsonPath("$.data.preferences.length()").value(6))
			.andExpect(jsonPath("$.data.inboxRecordingPolicy").value("ALWAYS_RECORD"));

		verify(preferenceService).replaceMine(eq(USER_ID), commandCaptor.capture());
		org.assertj.core.api.Assertions.assertThat(commandCaptor.getValue().quietHours()).isNull();
	}

	@Test
	@DisplayName("인증 정보가 없으면 GET·PUT 모두 401이고 service를 호출하지 않는다")
	void getAndPutRequireAuthentication() throws Exception {
		MockMvc unauthenticated = buildMockMvc(false);

		unauthenticated.perform(get("/api/v1/notifications/preferences"))
			.andExpect(status().isUnauthorized());
		unauthenticated.perform(put("/api/v1/notifications/preferences")
				.contentType("application/json")
				.content(validRequestJson()))
			.andExpect(status().isUnauthorized());

		verify(preferenceService, never()).findMine(anyLong());
		verify(preferenceService, never()).replaceMine(anyLong(), any());
	}

	@Test
	@DisplayName("계정 없음은 404 NOT-APP-001, 자격 없음은 403 NOT-APP-002로 매핑한다")
	void mapsAccountGateErrors() throws Exception {
		when(preferenceService.findMine(USER_ID))
			.thenThrow(new NotificationException(NotificationErrorCode.ACCOUNT_NOT_FOUND));
		when(preferenceService.replaceMine(eq(USER_ID), any()))
			.thenThrow(new NotificationException(NotificationErrorCode.ACCOUNT_NOT_ELIGIBLE));

		mockMvc.perform(get("/api/v1/notifications/preferences"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-APP-001"));
		mockMvc.perform(put("/api/v1/notifications/preferences")
				.contentType("application/json")
				.content(validRequestJson()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-APP-002"));
	}

	@Test
	@DisplayName("pushEnabled null, enabled null, quiet 일부 누락, invalid Zone ID, same-time은 400 NOT-VAL-008이다")
	void rejectsInvalidQuietHoursAndMissingPushEnabled() throws Exception {
		mockMvc.perform(put("/api/v1/notifications/preferences")
				.contentType("application/json")
				.content(validRequestJson().replace("\"pushEnabled\": false", "\"pushEnabled\": null")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-VAL-008"));

		mockMvc.perform(put("/api/v1/notifications/preferences")
				.contentType("application/json")
				.content(validRequestJson().replace(
					"{\"type\": \"ANSWER_RECEIVED\", \"enabled\": true}",
					"{\"type\": \"ANSWER_RECEIVED\", \"enabled\": null}")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-VAL-008"));

		mockMvc.perform(put("/api/v1/notifications/preferences")
				.contentType("application/json")
				.content(validRequestJson().replace("\"zoneId\": \"Asia/Seoul\"", "\"zoneId\": null")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-VAL-008"));

		mockMvc.perform(put("/api/v1/notifications/preferences")
				.contentType("application/json")
				.content(validRequestJson().replace("\"zoneId\": \"Asia/Seoul\"", "\"zoneId\": \"Mars/Olympus\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-VAL-008"));

		mockMvc.perform(put("/api/v1/notifications/preferences")
				.contentType("application/json")
				.content(validRequestJson().replace("\"zoneId\": \"Asia/Seoul\"", "\"zoneId\": \"+09:00\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-VAL-008"));

		mockMvc.perform(put("/api/v1/notifications/preferences")
				.contentType("application/json")
				.content(validRequestJson().replace("\"end\": \"07:00:00\"", "\"end\": \"22:00:00\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-VAL-008"));

		verify(preferenceService, never()).replaceMine(anyLong(), any());
	}

	@Test
	@DisplayName("literal JSON null 본문은 400 NOT-VAL-008이며 service를 호출하지 않는다")
	void rejectsLiteralJsonNullBody() throws Exception {
		mockMvc.perform(put("/api/v1/notifications/preferences")
				.contentType("application/json")
				.content("null"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-VAL-008"));

		verify(preferenceService, never()).replaceMine(anyLong(), any());
	}

	@Test
	@DisplayName("중복·누락·비정상 type 목록은 400 NOT-VAL-008이며 중복은 duplicate reason으로 구분한다")
	void rejectsInvalidPreferenceTypeLists() throws Exception {
		mockMvc.perform(put("/api/v1/notifications/preferences")
				.contentType("application/json")
				.content(duplicateTypeRequestJson()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-VAL-008"))
			.andExpect(jsonPath("$.errorDetail.reason").value("알림 종류를 중복 지정할 수 없습니다."));

		mockMvc.perform(put("/api/v1/notifications/preferences")
				.contentType("application/json")
				.content(missingTypeRequestJson()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-VAL-008"));

		mockMvc.perform(put("/api/v1/notifications/preferences")
				.contentType("application/json")
				.content(invalidTypeRequestJson()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-VAL-008"));

		verify(preferenceService, never()).replaceMine(anyLong(), any());
	}

	private static ObjectMapper objectMapper() {
		return new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	private static NotificationPreferenceSnapshot snapshot(
		boolean pushEnabled,
		NotificationQuietHours quietHours,
		Map<NotificationType, Boolean> typeEnabled) {
		return new NotificationPreferenceSnapshot(USER_ID, pushEnabled, quietHours, typeEnabled);
	}

	private static Map<NotificationType, Boolean> mixedTypes() {
		EnumMap<NotificationType, Boolean> values = new EnumMap<>(NotificationType.class);
		values.put(NotificationType.ANSWER_RECEIVED, true);
		values.put(NotificationType.ANSWER_REACTED, false);
		values.put(NotificationType.DIRECTION_POST_RECEIVED, true);
		values.put(NotificationType.REPORT_RESOLVED, false);
		values.put(NotificationType.QUESTION_PROPOSAL_REVIEWED, true);
		values.put(NotificationType.QUESTION_RECOMMENDED, false);
		return values;
	}

	private static NotificationQuietHours overnightQuietHours() {
		return new NotificationQuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("Asia/Seoul"));
	}

	private static String validRequestJson() {
		return """
			{
			  "pushEnabled": false,
			  "quietHours": {
			    "start": "22:00:00",
			    "end": "07:00:00",
			    "zoneId": "Asia/Seoul"
			  },
			  "preferences": [
			    {"type": "ANSWER_RECEIVED", "enabled": true},
			    {"type": "ANSWER_REACTED", "enabled": false},
			    {"type": "DIRECTION_POST_RECEIVED", "enabled": true},
			    {"type": "REPORT_RESOLVED", "enabled": false},
			    {"type": "QUESTION_PROPOSAL_REVIEWED", "enabled": true},
			    {"type": "QUESTION_RECOMMENDED", "enabled": false}
			  ]
			}
			""";
	}

	private static String duplicateTypeRequestJson() {
		return validRequestJson()
			.replace("\"QUESTION_RECOMMENDED\"", "\"ANSWER_RECEIVED\"");
	}

	private static String noQuietHoursRequestJson() {
		return """
			{
			  "pushEnabled": true,
			  "quietHours": null,
			  "preferences": [
			    {"type": "ANSWER_RECEIVED", "enabled": true},
			    {"type": "ANSWER_REACTED", "enabled": false},
			    {"type": "DIRECTION_POST_RECEIVED", "enabled": true},
			    {"type": "REPORT_RESOLVED", "enabled": false},
			    {"type": "QUESTION_PROPOSAL_REVIEWED", "enabled": true},
			    {"type": "QUESTION_RECOMMENDED", "enabled": false}
			  ]
			}
			""";
	}

	private static String missingTypeRequestJson() {
		return """
			{
			  "pushEnabled": true,
			  "quietHours": null,
			  "preferences": [
			    {"type": "ANSWER_RECEIVED", "enabled": true},
			    {"type": "ANSWER_REACTED", "enabled": false},
			    {"type": "DIRECTION_POST_RECEIVED", "enabled": true},
			    {"type": "REPORT_RESOLVED", "enabled": false},
			    {"type": "QUESTION_PROPOSAL_REVIEWED", "enabled": true}
			  ]
			}
			""";
	}

	private static String invalidTypeRequestJson() {
		return validRequestJson()
			.replace("\"QUESTION_RECOMMENDED\"", "\"UNKNOWN_TYPE\"");
	}

	private static final class AuthenticationResolver implements HandlerMethodArgumentResolver {
		private final boolean authenticated;

		private AuthenticationResolver(boolean authenticated) {
			this.authenticated = authenticated;
		}

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return Authentication.class.isAssignableFrom(parameter.getParameterType());
		}

		@Override
		public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
			return authenticated
				? UsernamePasswordAuthenticationToken.authenticated(String.valueOf(USER_ID), null, List.of())
				: null;
		}
	}
}
