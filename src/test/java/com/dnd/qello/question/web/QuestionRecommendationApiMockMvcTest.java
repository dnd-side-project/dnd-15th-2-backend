/**
 * Created at: 2026-10-02T16:41:59+09:00
 * Source scenario: TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING-UNIT-003, UNIT-004
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
import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.domain.ApprovedQuestionSourceType;
import com.dnd.qello.question.domain.ApprovedQuestionStatus;
import com.dnd.qello.question.service.QuestionRecommendationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class QuestionRecommendationApiMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-10-02T07:00:00Z");
	private static final long USER_ID = 11L;

	@Mock
	private QuestionRecommendationService recommendationService;

	@Test
	@DisplayName("추천 질문 조회는 200과 approvedQuestionId·questionText·answerFormat만 담은 목록을 반환한다")
	void returnsRecommendedQuestions() throws Exception {
		ApprovedQuestion question = ApprovedQuestion.restore(101L, null, ApprovedQuestionSourceType.OPERATOR,
				ApprovedQuestionStatus.ACTIVE, "오늘 하늘은 어떤가요?", AnswerFormat.BOTH,
				NOW.minusSeconds(3600), null, NOW.minusSeconds(3600), 1L, NOW.minusSeconds(3600));
		when(recommendationService.findRecommendations(USER_ID)).thenReturn(List.of(question));

		buildMockMvc(true).perform(get("/api/v1/questions/recommendations"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].approvedQuestionId").value(101))
				.andExpect(jsonPath("$.data[0].questionText").value("오늘 하늘은 어떤가요?"))
				.andExpect(jsonPath("$.data[0].answerFormat").value("BOTH"))
				.andExpect(jsonPath("$.data[0].id").doesNotExist())
				.andExpect(jsonPath("$.data[0].status").doesNotExist())
				.andExpect(jsonPath("$.data[0].sourceType").doesNotExist())
				.andExpect(jsonPath("$.data[0].approvedBy").doesNotExist())
				.andExpect(jsonPath("$.data[0].activeFrom").doesNotExist());
	}

	@Test
	@DisplayName("인증 정보가 없으면 추천 질문 조회는 401을 반환하고 서비스를 호출하지 않는다")
	void requiresAuthentication() throws Exception {
		buildMockMvc(false).perform(get("/api/v1/questions/recommendations"))
				.andExpect(status().isUnauthorized());

		verify(recommendationService, never()).findRecommendations(anyLong());
	}

	private MockMvc buildMockMvc(boolean authenticated) {
		QuestionRecommendationController controller = new QuestionRecommendationController(
				recommendationService, new ApiResponseFactory(Clock.fixed(NOW, ZoneOffset.UTC)));
		ObjectMapper objectMapper = new ObjectMapper()
				.registerModule(new JavaTimeModule())
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return MockMvcBuilders.standaloneSetup(controller)
				.setCustomArgumentResolvers(new AuthenticationResolver(authenticated))
				.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
				.setControllerAdvice(new GlobalExceptionHandler(
						new ApiErrorResponseFactory(Clock.fixed(NOW, ZoneOffset.UTC)),
						new ConstraintExceptionMapper()))
				.build();
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
				NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
			return authenticated
					? UsernamePasswordAuthenticationToken.authenticated(String.valueOf(USER_ID), null, List.of())
					: null;
		}
	}
}
