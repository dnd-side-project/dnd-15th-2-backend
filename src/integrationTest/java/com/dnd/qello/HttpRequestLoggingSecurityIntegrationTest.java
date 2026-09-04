/**
 * Created at: 2026-09-05T03:28:27+09:00
 * Source scenario: TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-INT-001 through INT-003
 */
package com.dnd.qello;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(HttpRequestLoggingSecurityIntegrationTest.ProbeController.class)
class HttpRequestLoggingSecurityIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REQUEST_ID_HEADER = "X-Request-ID";
	private static final String REQUEST_ID_MDC_KEY = "requestId";
	private static final Pattern GENERATED_ID = Pattern.compile(
			"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

	private final Logger requestLogger = (Logger) LoggerFactory.getLogger("HTTP_REQUEST");
	private final Logger errorLogger = (Logger) LoggerFactory.getLogger("APP_ERROR");
	private final CapturingAppender requestLogs = new CapturingAppender();
	private final CapturingAppender errorLogs = new CapturingAppender();

	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		requestLogs.events.clear();
		errorLogs.events.clear();
		requestLogs.start();
		errorLogs.start();
		requestLogger.addAppender(requestLogs);
		errorLogger.addAppender(errorLogs);
		MDC.remove(REQUEST_ID_MDC_KEY);
	}

	@AfterEach
	void tearDown() {
		requestLogger.detachAppender(requestLogs);
		errorLogger.detachAppender(errorLogs);
		requestLogs.stop();
		errorLogs.stop();
		MDC.remove(REQUEST_ID_MDC_KEY);
	}

	@Test
	@DisplayName("인증되지 않은 앱 API 401도 같은 request ID와 UNRESOLVED 완료 로그를 남긴다")
	void wrapsUnauthenticatedAppApiResponseOutsideSecurity() throws Exception {
		mockMvc.perform(get("/api/v1/direction/inbox").header(REQUEST_ID_HEADER, "security-401"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(REQUEST_ID_HEADER, "security-401"));

		assertSecurityCompletion("security-401", 401);
		assertThat(MDC.get(REQUEST_ID_MDC_KEY)).isNull();
	}

	@Test
	@DisplayName("잘못된 역할의 운영자 API 403도 같은 request ID와 UNRESOLVED 완료 로그를 남긴다")
	void wrapsWrongRoleOperatorResponseOutsideSecurity() throws Exception {
		mockMvc.perform(get("/api/v1/operator/report-cases")
				.with(user("viewer").roles("VIEWER"))
				.header(REQUEST_ID_HEADER, "security-403"))
				.andExpect(status().isForbidden())
				.andExpect(header().string(REQUEST_ID_HEADER, "security-403"));

		assertSecurityCompletion("security-403", 403);
		assertThat(MDC.get(REQUEST_ID_MDC_KEY)).isNull();
	}

	@Test
	@DisplayName("미매핑 요청의 실제 path와 query는 기록하지 않고 안전하지 않은 request ID를 UUID로 교체한다")
	void replacesInvalidIdWithoutLoggingAnUnmappedActualPath() throws Exception {
		String pathSentinel = "private-path-int002";
		String queryNameSentinel = "private-query-int002";
		String queryValueSentinel = "private-value-int002";

		MvcResult result = mockMvc.perform(get("/" + pathSentinel)
				.queryParam(queryNameSentinel, queryValueSentinel)
				.header(REQUEST_ID_HEADER, "unsafe request id"))
				.andExpect(status().isUnauthorized())
				.andReturn();

		String requestId = result.getResponse().getHeader(REQUEST_ID_HEADER);
		assertThat(requestId).matches(GENERATED_ID).isNotEqualTo("unsafe request id");
		assertThat(completionEvents(requestId)).singleElement().satisfies(event -> {
			assertThat(keyValueFields(event)).containsEntry("route", "UNRESOLVED");
			assertThat(mdc(event)).containsEntry(REQUEST_ID_MDC_KEY, requestId);
		});
		assertNoSentinels(requestLogs.events, List.of(pathSentinel, queryNameSentinel, queryValueSentinel));
		assertThat(MDC.get(REQUEST_ID_MDC_KEY)).isNull();
	}

	@Test
	@DisplayName("도메인 오류와 완료 로그는 같은 request ID로 연결하고 기존 응답 계약을 유지한다")
	void linksGlobalErrorAndCompletionWithoutChangingTheResponseContract() throws Exception {
		mockMvc.perform(get("/api/v1/request-logging-probe/domain")
				.with(jwt())
				.header(REQUEST_ID_HEADER, "domain-error-int003"))
				.andExpect(status().isBadRequest())
				.andExpect(header().string(REQUEST_ID_HEADER, "domain-error-int003"))
				.andExpect(jsonPath("$.errorDetail.code").value("ACC-VAL-004"));

		assertThat(errorEvents("domain-error-int003")).singleElement().satisfies(event -> {
			assertThat(mdc(event)).containsEntry(REQUEST_ID_MDC_KEY, "domain-error-int003");
			assertThat(mdc(event)).containsEntry("errorCode", "ACC-VAL-004");
			assertThat(mdc(event)).containsEntry("errorType", AccountException.class.getName());
		});
		assertThat(completionEvents("domain-error-int003")).singleElement().satisfies(event -> {
			assertThat(mdc(event)).containsEntry(REQUEST_ID_MDC_KEY, "domain-error-int003");
			assertThat(keyValueFields(event)).containsEntry("route", "/api/v1/request-logging-probe/domain");
			assertThat(keyValueFields(event)).containsEntry("status", 400);
		});
		assertThat(MDC.get(REQUEST_ID_MDC_KEY)).isNull();
	}

	private void assertSecurityCompletion(String requestId, int status) {
		assertThat(completionEvents(requestId)).singleElement().satisfies(event -> {
			Map<String, Object> fields = keyValueFields(event);
			assertThat(fields).containsEntry("route", "UNRESOLVED");
			assertThat(fields).containsEntry("method", "GET");
			assertThat(fields).containsEntry("status", status);
			assertThat(mdc(event)).containsEntry(REQUEST_ID_MDC_KEY, requestId);
		});
	}

	private List<ILoggingEvent> completionEvents(String requestId) {
		return requestLogs.events.stream()
				.filter(event -> "http_request_completed".equals(event.getFormattedMessage()))
				.filter(event -> requestId.equals(mdc(event).get(REQUEST_ID_MDC_KEY)))
				.toList();
	}

	private List<ILoggingEvent> errorEvents(String requestId) {
		return errorLogs.events.stream()
				.filter(event -> requestId.equals(mdc(event).get(REQUEST_ID_MDC_KEY)))
				.toList();
	}

	private static Map<String, String> mdc(ILoggingEvent event) {
		return event.getMDCPropertyMap();
	}

	private static Map<String, Object> keyValueFields(ILoggingEvent event) {
		List<org.slf4j.event.KeyValuePair> keyValuePairs = event.getKeyValuePairs();
		if (keyValuePairs == null) {
			return Map.of();
		}
		return keyValuePairs.stream().collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
	}

	private static void assertNoSentinels(List<ILoggingEvent> events, List<String> sentinels) {
		for (ILoggingEvent event : events) {
			for (String sentinel : sentinels) {
				assertThat(event.getFormattedMessage()).doesNotContain(sentinel);
				assertThat(mdc(event).values())
						.allSatisfy(value -> assertThat(String.valueOf(value)).doesNotContain(sentinel));
				assertThat(keyValueFields(event).values())
						.allSatisfy(value -> assertThat(String.valueOf(value)).doesNotContain(sentinel));
			}
		}
	}

	private static final class CapturingAppender extends AppenderBase<ILoggingEvent> {

		private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();

		@Override
		protected void append(ILoggingEvent event) {
			event.prepareForDeferredProcessing();
			events.add(event);
		}
	}

	@RestController
	static class ProbeController {

		@GetMapping("/api/v1/request-logging-probe/domain")
		void domain() {
			throw new AccountException(
					AccountErrorCode.INVALID_TIMEZONE,
					"timezone",
					"timezone은 유효한 IANA ID여야 합니다");
		}
	}
}
