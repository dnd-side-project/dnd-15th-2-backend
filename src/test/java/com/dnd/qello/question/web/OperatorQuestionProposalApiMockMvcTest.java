/**
 * Created at: 2026-08-16T01:20:00+09:00
 * Source scenario: TEST-PLAN-GH-144-QUESTION-PROPOSAL-API-INT-006 through INT-010
 * (임시 식별자 — /harness-test-plan 승인 전까지 이 시나리오 번호만 사용)
 */
package com.dnd.qello.question.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.domain.ApprovedQuestionSourceType;
import com.dnd.qello.question.domain.ApprovedQuestionStatus;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.domain.QuestionProposalReview;
import com.dnd.qello.question.domain.QuestionProposalReviewDecision;
import com.dnd.qello.question.domain.QuestionProposalStatus;
import com.dnd.qello.question.service.QuestionReviewService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class OperatorQuestionProposalApiMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");
	private static final long PROPOSAL_ID = 3L;
	private static final long OPERATOR_ID = 42L;

	@Mock
	private QuestionReviewService reviewService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		OperatorQuestionProposalController controller = new OperatorQuestionProposalController(
			reviewService, new ApiResponseFactory(Clock.fixed(NOW, ZoneOffset.UTC)), Clock.fixed(NOW, ZoneOffset.UTC));
		ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
			.setCustomArgumentResolvers(new AuthenticationResolver())
			.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
			.setControllerAdvice(new GlobalExceptionHandler(
				new ApiErrorResponseFactory(Clock.fixed(NOW, ZoneOffset.UTC)), new ConstraintExceptionMapper()))
			.build();
	}

	@Test
	@DisplayName("검수 시작은 200과 UNDER_REVIEW 제안을 반환한다")
	void startReviewReturnsUnderReviewProposal() throws Exception {
		QuestionProposal underReview = QuestionProposal.restore(
			PROPOSAL_ID, 11L, QuestionProposalStatus.UNDER_REVIEW, "제안 문구", null, NOW, NOW, NOW);
		when(reviewService.startReview(PROPOSAL_ID)).thenReturn(underReview);

		mockMvc.perform(post("/admin/questions/proposals/{proposalId}/review", PROPOSAL_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"));
	}

	@Test
	@DisplayName("승인 요청은 answerFormat·activeFrom·activeUntil과 인증 사용자 id를 그대로 QuestionReviewService.approve에 전달한다")
	void approveDelegatesWithExactArguments() throws Exception {
		Instant activeFrom = Instant.parse("2026-09-01T00:00:00Z");
		Instant activeUntil = Instant.parse("2026-12-01T00:00:00Z");
		ApprovedQuestion approved = ApprovedQuestion.restore(9L, PROPOSAL_ID, ApprovedQuestionSourceType.USER_PROPOSAL,
			ApprovedQuestionStatus.ACTIVE, "제안 문구", AnswerFormat.TEXT, activeFrom, activeUntil, NOW, OPERATOR_ID, NOW);
		when(reviewService.approve(PROPOSAL_ID, OPERATOR_ID, AnswerFormat.TEXT, activeFrom, activeUntil, NOW))
			.thenReturn(approved);

		mockMvc.perform(post("/admin/questions/proposals/{proposalId}/approve", PROPOSAL_ID)
				.contentType("application/json")
				.content("{\"answerFormat\":\"TEXT\",\"activeFrom\":\"2026-09-01T00:00:00Z\",\"activeUntil\":\"2026-12-01T00:00:00Z\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(9))
			.andExpect(jsonPath("$.data.status").value("ACTIVE"));

		verify(reviewService).approve(
			eq(PROPOSAL_ID), eq(OPERATOR_ID), eq(AnswerFormat.TEXT), eq(activeFrom), eq(activeUntil), eq(NOW));
	}

	@Test
	@DisplayName("answerFormat이 없으면 승인 요청은 400을 반환하고 service를 호출하지 않는다")
	void approveRejectsMissingAnswerFormat() throws Exception {
		mockMvc.perform(post("/admin/questions/proposals/{proposalId}/approve", PROPOSAL_ID)
				.contentType("application/json")
				.content("{\"activeFrom\":\"2026-09-01T00:00:00Z\"}"))
			.andExpect(status().isBadRequest());

		verify(reviewService, never()).approve(
			anyLong(), anyLong(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("반려 요청은 reason과 인증 사용자 id를 그대로 QuestionReviewService.reject에 전달한다")
	void rejectDelegatesWithExactArguments() throws Exception {
		QuestionProposalReview review = QuestionProposalReview.restore(
			5L, PROPOSAL_ID, OPERATOR_ID, QuestionProposalReviewDecision.REJECTED, "중복된 질문입니다", NOW);
		when(reviewService.reject(PROPOSAL_ID, OPERATOR_ID, "중복된 질문입니다", NOW)).thenReturn(review);

		mockMvc.perform(post("/admin/questions/proposals/{proposalId}/reject", PROPOSAL_ID)
				.contentType("application/json")
				.content("{\"reason\":\"중복된 질문입니다\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.decision").value("REJECTED"))
			.andExpect(jsonPath("$.data.reason").value("중복된 질문입니다"));

		verify(reviewService).reject(eq(PROPOSAL_ID), eq(OPERATOR_ID), eq("중복된 질문입니다"), eq(NOW));
	}

	@Test
	@DisplayName("reason이 비어 있으면 반려 요청은 400을 반환하고 service를 호출하지 않는다")
	void rejectRejectsBlankReason() throws Exception {
		mockMvc.perform(post("/admin/questions/proposals/{proposalId}/reject", PROPOSAL_ID)
				.contentType("application/json")
				.content("{\"reason\":\"\"}"))
			.andExpect(status().isBadRequest());

		verify(reviewService, never()).reject(anyLong(), anyLong(), any(), any());
	}

	// OperatorLoginController가 로그인 시 String.valueOf(userId)를 principal 이름으로 심는 규칙을 재현한다.
	private static final class AuthenticationResolver implements HandlerMethodArgumentResolver {

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return Authentication.class.isAssignableFrom(parameter.getParameterType());
		}

		@Override
		public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
			return UsernamePasswordAuthenticationToken.authenticated(String.valueOf(OPERATOR_ID), null, List.of());
		}
	}
}
