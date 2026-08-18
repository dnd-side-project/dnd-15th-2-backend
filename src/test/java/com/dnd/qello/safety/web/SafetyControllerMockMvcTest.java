/**
 * Created at: 2026-08-18T22:20:00+09:00
 * Source scenario: TEST-PLAN-GH-154-REPORT-INTAKE-API-UNIT-013 through UNIT-020
 */
package com.dnd.qello.safety.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.dnd.qello.common.error.ApiErrorResponseFactory;
import com.dnd.qello.common.error.ConstraintExceptionMapper;
import com.dnd.qello.common.web.GlobalExceptionHandler;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportStatus;
import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;
import com.dnd.qello.safety.service.ReportOutcome;
import com.dnd.qello.safety.service.SafetyReportService;
import com.dnd.qello.safety.service.SafetyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class SafetyControllerMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-08-18T05:00:00Z");
	private static final long REPORTER_ID = 11L;

	@Mock
	private SafetyReportService safetyReportService;
	@Mock
	private SafetyService safetyService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = buildMockMvc(true);
	}

	private MockMvc buildMockMvc(boolean authenticated) {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		SafetyController controller =
			new SafetyController(safetyReportService, safetyService, new ApiResponseFactory(clock), clock);
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
	@DisplayName("UNIT-013: 인증 정보가 없으면 401이며 서비스를 호출하지 않는다")
	void reportAnswerRequiresAuthentication() throws Exception {
		buildMockMvc(false).perform(post("/api/v1/answers/9/reports")
				.contentType("application/json")
				.content("{\"reasonCode\":\"SPAM_OR_ADVERTISING\"}"))
			.andExpect(status().isUnauthorized());

		verify(safetyReportService, never()).submitAnswerReport(anyLong(), anyLong(), any(), anyBoolean(), any());
	}

	@Test
	@DisplayName("UNIT-014: reasonCode가 없으면 400이며 서비스를 호출하지 않는다")
	void reportAnswerRejectsMissingReasonCode() throws Exception {
		mockMvc.perform(post("/api/v1/answers/9/reports")
				.contentType("application/json")
				.content("{}"))
			.andExpect(status().isBadRequest());

		verify(safetyReportService, never()).submitAnswerReport(anyLong(), anyLong(), any(), anyBoolean(), any());
	}

	@Test
	@DisplayName("UNIT-015: 정상 요청은 인증 subject·경로 변수·요청 필드를 서비스로 전달하고 201을 반환한다")
	void reportAnswerReturnsCreatedForNewReport() throws Exception {
		Report saved = new Report(101L, REPORTER_ID, null, null, 9L, "SPAM_OR_ADVERTISING", null,
			ReportStatus.RECEIVED, NOW, null, 55L, null);
		when(safetyReportService.submitAnswerReport(eq(REPORTER_ID), eq(9L), any(), eq(false), eq(NOW)))
			.thenReturn(new ReportOutcome(saved, false));

		mockMvc.perform(post("/api/v1/answers/9/reports")
				.contentType("application/json")
				.content("{\"reasonCode\":\"SPAM_OR_ADVERTISING\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.reportId").value(101))
			.andExpect(jsonPath("$.data.alreadyReceived").value(false))
			.andExpect(jsonPath("$.data.status").value("RECEIVED"));
	}

	@Test
	@DisplayName("UNIT-016: 이미 접수된 신고를 반환하면 200이다(201 아님)")
	void reportAnswerReturnsOkForAlreadyReceived() throws Exception {
		Report existing = new Report(101L, REPORTER_ID, null, null, 9L, "SPAM_OR_ADVERTISING", null,
			ReportStatus.RECEIVED, NOW, null, 55L, null);
		when(safetyReportService.submitAnswerReport(eq(REPORTER_ID), eq(9L), any(), eq(false), eq(NOW)))
			.thenReturn(new ReportOutcome(existing, true));

		mockMvc.perform(post("/api/v1/answers/9/reports")
				.contentType("application/json")
				.content("{\"reasonCode\":\"SPAM_OR_ADVERTISING\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.alreadyReceived").value(true));
	}

	@Test
	@DisplayName("UNIT-017: 신고 사유 목록은 200과 8종 배열을 반환한다")
	void reportReasonsReturnsCatalog() throws Exception {
		mockMvc.perform(get("/api/v1/report-reasons"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(8));
	}

	@Test
	@DisplayName("UNIT-018: 존재하지 않거나 본인 소유가 아닌 신고 조회는 404다")
	void findReportReturnsNotFoundForMissingOrForeignReport() throws Exception {
		when(safetyReportService.requireOwnReport(999L, REPORTER_ID)).thenThrow(
			new SafetyException(SafetyErrorCode.REPORT_NOT_FOUND, null, "신고를 찾을 수 없습니다"));

		mockMvc.perform(get("/api/v1/reports/999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorDetail.code").value(SafetyErrorCode.REPORT_NOT_FOUND.code()));
	}

	@Test
	@DisplayName("UNIT-019: 차단 요청은 인증 subject를 blocker로 전달하고 200을 반환한다")
	void blockPassesAuthenticatedUserAsBlocker() throws Exception {
		mockMvc.perform(post("/api/v1/users/9/blocks"))
			.andExpect(status().isOk());

		verify(safetyService).block(eq(REPORTER_ID), eq(9L), eq(NOW));
	}

	@Test
	@DisplayName("UNIT-019: 차단 해제 요청은 200을 반환한다")
	void releaseBlockReturnsOk() throws Exception {
		mockMvc.perform(delete("/api/v1/users/9/blocks"))
			.andExpect(status().isOk());

		verify(safetyService).releaseBlock(eq(REPORTER_ID), eq(9L), eq(NOW));
	}

	@Test
	@DisplayName("UNIT-020: 자기 자신을 차단하면 기존 SAF-DOM-001로 400이다")
	void blockRejectsSelfBlock() throws Exception {
		org.mockito.Mockito.doThrow(
			new SafetyException(SafetyErrorCode.SELF_BLOCK_NOT_ALLOWED, "blockedId", "자기 자신을 차단할 수 없습니다"))
			.when(safetyService).block(eq(REPORTER_ID), eq(REPORTER_ID), eq(NOW));

		mockMvc.perform(post("/api/v1/users/" + REPORTER_ID + "/blocks"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value(SafetyErrorCode.SELF_BLOCK_NOT_ALLOWED.code()));
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
				? UsernamePasswordAuthenticationToken.authenticated(String.valueOf(REPORTER_ID), null, List.of())
				: null;
		}
	}
}
