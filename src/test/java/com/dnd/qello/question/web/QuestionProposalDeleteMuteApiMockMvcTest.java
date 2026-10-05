/**
 * Created at: 2026-10-05T18:10:06+09:00
 * Source scenario: TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-011, UNIT-014
 */
package com.dnd.qello.question.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.dnd.qello.common.error.ApiErrorResponseFactory;
import com.dnd.qello.common.error.ConstraintExceptionMapper;
import com.dnd.qello.common.web.GlobalExceptionHandler;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.domain.QuestionProposalStatus;
import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;
import com.dnd.qello.question.service.QuestionProposalApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class QuestionProposalDeleteMuteApiMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");
	private static final long USER_ID = 11L;
	private static final long PROPOSAL_ID = 7L;

	@Mock
	private QuestionProposalApplicationService applicationService;

	@Test
	@DisplayName("본인 제안 삭제는 204와 빈 본문을 반환한다")
	void deleteReturnsNoContent() throws Exception {
		mockMvc(true).perform(delete("/api/v1/questions/proposals/{id}", PROPOSAL_ID))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		verify(applicationService).delete(USER_ID, PROPOSAL_ID);
	}

	@Test
	@DisplayName("본인 제안이 아니면 삭제는 404와 QUE-APP-002 오류를 반환한다")
	void deleteMapsNotFound() throws Exception {
		doThrow(new QuestionException(QuestionErrorCode.PROPOSAL_NOT_FOUND, "proposalId"))
				.when(applicationService).delete(USER_ID, PROPOSAL_ID);

		mockMvc(true).perform(delete("/api/v1/questions/proposals/{id}", PROPOSAL_ID))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorDetail.code").value("QUE-APP-002"));
	}

	@Test
	@DisplayName("인증 정보가 없으면 삭제·알림 설정은 401이고 application service를 호출하지 않는다")
	void requiresAuthentication() throws Exception {
		MockMvc unauthenticated = mockMvc(false);

		unauthenticated.perform(delete("/api/v1/questions/proposals/{id}", PROPOSAL_ID))
				.andExpect(status().isUnauthorized());
		unauthenticated.perform(put("/api/v1/questions/proposals/{id}/notification", PROPOSAL_ID)
				.contentType("application/json")
				.content("{\"muted\":true}"))
				.andExpect(status().isUnauthorized());

		verify(applicationService, never()).delete(anyLong(), anyLong());
		verify(applicationService, never()).changeNotificationMuted(anyLong(), anyLong(), anyBoolean());
	}

	@Test
	@DisplayName("알림 끄기는 200과 notificationMuted=true인 제안을 반환한다")
	void changeNotificationReturnsMutedProposal() throws Exception {
		when(applicationService.changeNotificationMuted(USER_ID, PROPOSAL_ID, true)).thenReturn(
			QuestionProposal.restore(PROPOSAL_ID, USER_ID, QuestionProposalStatus.UNDER_REVIEW, "제안 문구", null,
				NOW, NOW, NOW, null, true));

		mockMvc(true).perform(put("/api/v1/questions/proposals/{id}/notification", PROPOSAL_ID)
				.contentType("application/json")
				.content("{\"muted\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(PROPOSAL_ID))
			.andExpect(jsonPath("$.data.notificationMuted").value(true));
	}

	@Test
	@DisplayName("muted 값이 없으면 알림 설정은 400이고 application service를 호출하지 않는다")
	void changeNotificationRequiresMuted() throws Exception {
		mockMvc(true).perform(put("/api/v1/questions/proposals/{id}/notification", PROPOSAL_ID)
				.contentType("application/json")
				.content("{}"))
				.andExpect(status().isBadRequest());

		verify(applicationService, never()).changeNotificationMuted(anyLong(), anyLong(), anyBoolean());
	}

	@Test
	@DisplayName("삭제한 제안이거나 본인 제안이 아니면 알림 설정은 404를 반환한다")
	void changeNotificationMapsNotFound() throws Exception {
		when(applicationService.changeNotificationMuted(USER_ID, PROPOSAL_ID, false))
			.thenThrow(new QuestionException(QuestionErrorCode.PROPOSAL_NOT_FOUND, "proposalId"));

		mockMvc(true).perform(put("/api/v1/questions/proposals/{id}/notification", PROPOSAL_ID)
				.contentType("application/json")
				.content("{\"muted\":false}"))
			.andExpect(status().isNotFound());
	}

	private MockMvc mockMvc(boolean authenticated) {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		QuestionProposalController controller = new QuestionProposalController(
				applicationService, new ApiResponseFactory(clock));
		ObjectMapper objectMapper = new ObjectMapper()
				.registerModule(new JavaTimeModule())
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return MockMvcBuilders.standaloneSetup(controller)
				.setCustomArgumentResolvers(new AuthenticationResolver(authenticated))
				.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
				.setControllerAdvice(new GlobalExceptionHandler(
						new ApiErrorResponseFactory(clock), new ConstraintExceptionMapper()))
				.build();
	}

	private record AuthenticationResolver(boolean authenticated) implements HandlerMethodArgumentResolver {

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return Authentication.class.isAssignableFrom(parameter.getParameterType());
		}

		@Override
		public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
				NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
			return authenticated
					? UsernamePasswordAuthenticationToken.authenticated(String.valueOf(USER_ID), null, List.of())
					: null;
		}
	}
}
