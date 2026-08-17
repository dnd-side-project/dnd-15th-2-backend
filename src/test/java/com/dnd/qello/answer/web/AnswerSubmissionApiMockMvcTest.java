/**
 * Created at: 2026-08-17T16:45:00+09:00
 * Source scenario: TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-016 through UNIT-018
 */
package com.dnd.qello.answer.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.service.AnswerSubmissionApplicationService;
import com.dnd.qello.common.error.ApiErrorResponseFactory;
import com.dnd.qello.common.error.ConstraintExceptionMapper;
import com.dnd.qello.common.web.GlobalExceptionHandler;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class AnswerSubmissionApiMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-08-17T05:00:00Z");
	private static final String VALID_BODY = "{\"bodyText\":\"저도 여기 자주 와요!\",\"mediaIds\":[]}";

	@Mock
	private AnswerSubmissionApplicationService applicationService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = buildMockMvc(true);
	}

	private MockMvc buildMockMvc(boolean authenticated) {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		AnswerSubmissionController controller =
			new AnswerSubmissionController(applicationService, new ApiResponseFactory(clock));
		ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return MockMvcBuilders.standaloneSetup(controller)
			.setCustomArgumentResolvers(new AuthenticationResolver(authenticated))
			.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
			.setValidator(new LocalValidatorFactoryBean())
			.setControllerAdvice(new GlobalExceptionHandler(
				new ApiErrorResponseFactory(clock), new ConstraintExceptionMapper()))
			.build();
	}

	@Test
	@DisplayName("UNIT-016: 정상 요청은 인증 subject·header·path·body를 service command로 전달하고 202 공통 wrapper를 반환한다")
	void submitReturnsAcceptedWithCommonWrapper() throws Exception {
		Answer submitted = Answer.submit(9L, 11L, "key", "저도 여기 자주 와요!", "TEST",
			BigDecimal.valueOf(90), "NEAR", NOW, 5000L);
		submitted = Answer.restore(101L, submitted.getPostRecipientId(), submitted.getAuthorId(),
			submitted.getStatus(), submitted.getIdempotencyKey(), submitted.getBodyText(),
			submitted.getCoarseRegionCode(), submitted.getBearingFromSenderDegrees(), submitted.getDistanceBand(),
			submitted.getModerationStatus(), submitted.getSubmittedAt(), null, null, submitted.getDistanceM(),
			null, 0);
		when(applicationService.submit(eq(11L), eq("key"), eq(9L), eq("저도 여기 자주 와요!"), eq(List.of())))
			.thenReturn(submitted);

		mockMvc.perform(post("/api/v1/direction/inbox/9/answers")
				.header("Idempotency-Key", "key")
				.contentType("application/json")
				.content(VALID_BODY))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.answerId").value(101))
			.andExpect(jsonPath("$.data.submissionStatus").value("SUBMITTED"))
			.andExpect(jsonPath("$.data.submittedAt").isString());
	}

	@Test
	@DisplayName("UNIT-017: 인증 정보가 없으면 401을 반환하고 application service를 호출하지 않는다")
	void submitRequiresAuthentication() throws Exception {
		buildMockMvc(false).perform(post("/api/v1/direction/inbox/9/answers")
				.header("Idempotency-Key", "key")
				.contentType("application/json")
				.content(VALID_BODY))
			.andExpect(status().isUnauthorized());

		verify(applicationService, never()).submit(anyLong(), anyString(), anyLong(), anyString(), any());
	}

	@Test
	@DisplayName("UNIT-017: Idempotency-Key 헤더가 없으면 400이며 application service를 호출하지 않는다")
	void submitRequiresIdempotencyKeyHeader() throws Exception {
		mockMvc.perform(post("/api/v1/direction/inbox/9/answers")
				.contentType("application/json")
				.content(VALID_BODY))
			.andExpect(status().isBadRequest());

		verify(applicationService, never()).submit(anyLong(), anyString(), anyLong(), anyString(), any());
	}

	@Test
	@DisplayName("UNIT-017: 200자를 넘는 Idempotency-Key는 400이며 application service를 호출하지 않는다")
	void submitRejectsOversizedIdempotencyKeyHeader() throws Exception {
		mockMvc.perform(post("/api/v1/direction/inbox/9/answers")
				.header("Idempotency-Key", "k".repeat(201))
				.contentType("application/json")
				.content(VALID_BODY))
			.andExpect(status().isBadRequest());

		verify(applicationService, never()).submit(anyLong(), anyString(), anyLong(), anyString(), any());
	}

	@Test
	@DisplayName("UNIT-017: 공백 본문은 bean validation으로 400이며 application service를 호출하지 않는다")
	void submitRejectsBlankBody() throws Exception {
		mockMvc.perform(post("/api/v1/direction/inbox/9/answers")
				.header("Idempotency-Key", "key")
				.contentType("application/json")
				.content("{\"bodyText\":\"   \",\"mediaIds\":[]}"))
			.andExpect(status().isBadRequest());

		verify(applicationService, never()).submit(anyLong(), anyString(), anyLong(), anyString(), any());
	}

	@Test
	@DisplayName("UNIT-017: mediaIds가 2개 이상이면 400이며 application service를 호출하지 않는다")
	void submitRejectsTooManyMediaIds() throws Exception {
		mockMvc.perform(post("/api/v1/direction/inbox/9/answers")
				.header("Idempotency-Key", "key")
				.contentType("application/json")
				.content("{\"bodyText\":\"본문\",\"mediaIds\":[1,2]}"))
			.andExpect(status().isBadRequest());

		verify(applicationService, never()).submit(anyLong(), anyString(), anyLong(), anyString(), any());
	}

	@Test
	@DisplayName("UNIT-017: path의 postRecipientId가 숫자가 아니면 400이다")
	void submitRejectsNonNumericPathVariable() throws Exception {
		mockMvc.perform(post("/api/v1/direction/inbox/not-a-number/answers")
				.header("Idempotency-Key", "key")
				.contentType("application/json")
				.content(VALID_BODY))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("UNIT-018: 답변할 수 있는 수신 항목이 없으면 404와 RECIPIENT_NOT_FOUND 코드를 반환한다")
	void submitReturnsNotFoundForIneligibleRecipient() throws Exception {
		when(applicationService.submit(eq(11L), eq("key"), eq(9L), any(), any())).thenThrow(
			new AnswerException(AnswerErrorCode.RECIPIENT_NOT_FOUND, "postRecipientId", "답변할 수 있는 수신 항목을 찾을 수 없습니다"));

		mockMvc.perform(post("/api/v1/direction/inbox/9/answers")
				.header("Idempotency-Key", "key")
				.contentType("application/json")
				.content(VALID_BODY))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorDetail.code").value(AnswerErrorCode.RECIPIENT_NOT_FOUND.code()));
	}

	@Test
	@DisplayName("UNIT-018: 멱등키 재사용은 409와 IDEMPOTENCY_KEY_REUSED 코드를 반환한다")
	void submitReturnsConflictForReusedIdempotencyKey() throws Exception {
		when(applicationService.submit(eq(11L), eq("key"), eq(9L), any(), any())).thenThrow(
			new AnswerException(AnswerErrorCode.IDEMPOTENCY_KEY_REUSED, "idempotencyKey", "같은 멱등키로 다른 요청을 재사용할 수 없습니다"));

		mockMvc.perform(post("/api/v1/direction/inbox/9/answers")
				.header("Idempotency-Key", "key")
				.contentType("application/json")
				.content(VALID_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorDetail.code").value(AnswerErrorCode.IDEMPOTENCY_KEY_REUSED.code()));
	}

	@Test
	@DisplayName("UNIT-018: 이미 활성 답변이 있는 수신 항목의 다른 키 재제출은 409와 DUPLICATE_ACTIVE_ANSWER 코드를 반환한다")
	void submitReturnsConflictForDuplicateActiveAnswer() throws Exception {
		when(applicationService.submit(eq(11L), eq("key"), eq(9L), any(), any())).thenThrow(
			new AnswerException(AnswerErrorCode.DUPLICATE_ACTIVE_ANSWER, "postRecipientId", "이미 이 수신 항목에 답변이 등록되었습니다"));

		mockMvc.perform(post("/api/v1/direction/inbox/9/answers")
				.header("Idempotency-Key", "key")
				.contentType("application/json")
				.content(VALID_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorDetail.code").value(AnswerErrorCode.DUPLICATE_ACTIVE_ANSWER.code()));
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
			return authenticated ? UsernamePasswordAuthenticationToken.authenticated("11", null, List.of()) : null;
		}
	}
}
