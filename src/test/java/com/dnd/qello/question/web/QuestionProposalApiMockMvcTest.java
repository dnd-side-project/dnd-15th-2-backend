/**
 * Created at: 2026-08-16T01:10:00+09:00
 * Source scenario: TEST-PLAN-GH-144-QUESTION-PROPOSAL-API-INT-001 through INT-005
 * (임시 식별자 — /harness-test-plan 승인 전까지 이 시나리오 번호만 사용)
 */
package com.dnd.qello.question.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.dnd.qello.common.error.ApiErrorResponseFactory;
import com.dnd.qello.common.error.ConstraintExceptionMapper;
import com.dnd.qello.common.web.GlobalExceptionHandler;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.domain.QuestionProposalStatus;
import com.dnd.qello.question.service.QuestionProposalApplicationService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class QuestionProposalApiMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");
	private static final long USER_ID = 11L;

	@Mock
	private QuestionProposalApplicationService applicationService;

	private MockMvc buildMockMvc(boolean authenticated) {
		QuestionProposalController controller = new QuestionProposalController(
			applicationService, new ApiResponseFactory(java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC)));
		ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return MockMvcBuilders.standaloneSetup(controller)
			.setCustomArgumentResolvers(new AuthenticationResolver(authenticated))
			.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
			.setControllerAdvice(new GlobalExceptionHandler(
				new ApiErrorResponseFactory(java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC)),
				new ConstraintExceptionMapper()))
			.build();
	}

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = buildMockMvc(true);
	}

	@Test
	@DisplayName("정상 제출은 201과 SUBMITTED 제안을 반환한다")
	void submitReturnsCreatedProposal() throws Exception {
		QuestionProposal submitted = QuestionProposal.restore(
			1L, USER_ID, QuestionProposalStatus.SUBMITTED, "제안 문구", null, NOW, NOW, NOW);
		when(applicationService.submit(USER_ID, "제안 문구")).thenReturn(submitted);

		mockMvc.perform(post("/api/v1/questions/proposals")
				.contentType("application/json")
				.content("{\"proposedText\":\"제안 문구\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.id").value(1))
			.andExpect(jsonPath("$.data.status").value("SUBMITTED"))
			.andExpect(jsonPath("$.data.proposedText").value("제안 문구"));
	}

	@Test
	@DisplayName("proposedText가 비어 있으면 제출은 400을 반환하고 application service를 호출하지 않는다")
	void submitRejectsBlankText() throws Exception {
		mockMvc.perform(post("/api/v1/questions/proposals")
				.contentType("application/json")
				.content("{\"proposedText\":\"\"}"))
			.andExpect(status().isBadRequest());

		verify(applicationService, never()).submit(anyLong(), anyString());
	}

	@Test
	@DisplayName("인증 정보가 없으면 제출은 401을 반환하고 application service를 호출하지 않는다")
	void submitRequiresAuthentication() throws Exception {
		buildMockMvc(false).perform(post("/api/v1/questions/proposals")
				.contentType("application/json")
				.content("{\"proposedText\":\"제안 문구\"}"))
			.andExpect(status().isUnauthorized());

		verify(applicationService, never()).submit(anyLong(), anyString());
	}

	@Test
	@DisplayName("내 제안 목록 조회는 200과 목록을 반환한다")
	void findMineReturnsList() throws Exception {
		QuestionProposal proposal = QuestionProposal.restore(
			2L, USER_ID, QuestionProposalStatus.DRAFT, "초안", null, null, NOW, NOW);
		when(applicationService.findMine(USER_ID)).thenReturn(List.of(proposal));

		mockMvc.perform(get("/api/v1/questions/proposals/me"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].id").value(2))
			.andExpect(jsonPath("$.data[0].status").value("DRAFT"));
	}

	@Test
	@DisplayName("인증 정보가 없으면 목록 조회는 401을 반환하고 application service를 호출하지 않는다")
	void findMineRequiresAuthentication() throws Exception {
		buildMockMvc(false).perform(get("/api/v1/questions/proposals/me"))
			.andExpect(status().isUnauthorized());

		verify(applicationService, never()).findMine(anyLong());
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
