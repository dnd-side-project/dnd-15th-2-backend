/*
 * Created at: 2026-09-05T02:52:50+09:00
 * Source scenario: TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-UNIT-001 through UNIT-009
 */
package com.dnd.qello.common.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.common.error.ApiErrorResponseFactory;
import com.dnd.qello.common.error.ConstraintExceptionMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

class HttpRequestLoggingFilterTest {

	private static final String REQUEST_ID_HEADER = "X-Request-ID";
	private static final String REQUEST_ID_MDC_KEY = "requestId";
	private static final String UNRELATED_MDC_KEY = "unrelated";
	private static final Instant FIXED_NOW = Instant.parse("2026-09-05T00:00:00Z");
	private static final Pattern GENERATED_ID = Pattern.compile(
			"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

	private final Logger requestLogger = (Logger) LoggerFactory.getLogger("HTTP_REQUEST");
	private final Logger errorLogger = (Logger) LoggerFactory.getLogger("APP_ERROR");
	private final CapturingAppender requestLogs = new CapturingAppender();
	private final CapturingAppender errorLogs = new CapturingAppender();

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		requestLogs.events.clear();
		errorLogs.events.clear();
		requestLogs.start();
		errorLogs.start();
		requestLogger.addAppender(requestLogs);
		errorLogger.addAppender(errorLogs);
		mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
				.setControllerAdvice(new GlobalExceptionHandler(
						new ApiErrorResponseFactory(Clock.fixed(FIXED_NOW, ZoneOffset.UTC)),
						new ConstraintExceptionMapper()))
				.addFilters(new HttpRequestLoggingFilter())
				.build();
	}

	@AfterEach
	void tearDown() {
		requestLogger.detachAppender(requestLogs);
		errorLogger.detachAppender(errorLogs);
		requestLogs.stop();
		errorLogs.stop();
		MDC.remove(REQUEST_ID_MDC_KEY);
		MDC.remove(UNRELATED_MDC_KEY);
	}

	@ParameterizedTest
	@MethodSource("allowlistedRequestIds")
	@DisplayName("허용 목록의 1자와 64자 request ID는 응답과 완료 로그에서 보존한다")
	void keepsOneAndSixtyFourCharacterAllowlistedRequestIds(String requestId) throws Exception {
		MvcResult result = mockMvc.perform(get("/probe/items/item-42").header(REQUEST_ID_HEADER, requestId))
				.andExpect(status().isOk())
				.andReturn();

		assertThat(result.getResponse().getHeader(REQUEST_ID_HEADER)).isEqualTo(requestId);
		assertThat(completionEvents()).singleElement()
				.satisfies(event -> assertThat(mdc(event).get(REQUEST_ID_MDC_KEY)).isEqualTo(requestId));
	}

	@ParameterizedTest
	@MethodSource("unsafeRequestIds")
	@DisplayName("누락되었거나 안전하지 않은 request ID는 소문자 UUID로 교체한다")
	void replacesMissingAndUnsafeRequestIdsWithLowercaseUuid(
			Consumer<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> input)
			throws Exception {
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request = get(
				"/probe/items/item-42");
		input.accept(request);
		MvcResult result = mockMvc.perform(request)
				.andExpect(status().isOk())
				.andReturn();

		String requestId = result.getResponse().getHeader(REQUEST_ID_HEADER);
		assertThat(requestId).matches(GENERATED_ID);
		assertThat(completionEvents()).singleElement()
				.satisfies(event -> assertThat(mdc(event).get(REQUEST_ID_MDC_KEY)).isEqualTo(requestId));
	}

	@Test
	@DisplayName("여러 request ID header는 어느 값도 선택하지 않고 UUID로 교체한다")
	void replacesMultipleRequestIdHeadersInsteadOfSelectingOne() throws Exception {
		MvcResult result = mockMvc.perform(get("/probe/items/item-42")
				.header(REQUEST_ID_HEADER, "first")
				.header(REQUEST_ID_HEADER, "second"))
				.andExpect(status().isOk())
				.andReturn();

		String requestId = result.getResponse().getHeader(REQUEST_ID_HEADER);
		assertThat(requestId).matches(GENERATED_ID).isNotIn("first", "second");
		assertThat(completionEvents()).singleElement()
				.satisfies(event -> assertThat(mdc(event).get(REQUEST_ID_MDC_KEY)).isEqualTo(requestId));
	}

	@Test
	@DisplayName("mapped 성공 요청은 route template, method, status, duration을 한 번만 기록한다")
	void logsMappedRouteMethodStatusAndNonNegativeDurationOnce() throws Exception {
		mockMvc.perform(get("/probe/items/private-item-value?query=private-query-value")
				.header(REQUEST_ID_HEADER, "safe-request-id"))
				.andExpect(status().isOk());

		assertThat(completionEvents()).singleElement().satisfies(event -> {
			Map<String, Object> fields = keyValueFields(event);
			assertThat(fields).containsEntry("route", "/probe/items/{itemId}");
			assertThat(fields).containsEntry("method", "GET");
			assertThat(fields).containsEntry("status", 200);
			assertThat((Long) fields.get("durationMs")).isGreaterThanOrEqualTo(0L);
			assertThat(event.getFormattedMessage()).doesNotContain("private-item-value", "private-query-value");
		});
	}

	@Test
	@DisplayName("처리된 도메인 오류와 완료 로그는 같은 request ID로 연결한다")
	void linksHandledDomainErrorAndCompletionWithTheSameRequestId() throws Exception {
		MvcResult result = mockMvc.perform(get("/probe/domain").header(REQUEST_ID_HEADER, "domain-request-id"))
				.andExpect(status().isBadRequest())
				.andReturn();

		String requestId = result.getResponse().getHeader(REQUEST_ID_HEADER);
		assertThat(errorLogs.events).singleElement().satisfies(event -> {
			assertThat(mdc(event)).containsEntry(REQUEST_ID_MDC_KEY, requestId);
			assertThat(mdc(event)).containsEntry("errorCode", "ACC-VAL-004");
			assertThat(mdc(event)).containsEntry("errorType", AccountException.class.getName());
		});
		assertThat(completionEvents()).singleElement()
				.satisfies(event -> assertThat(mdc(event).get(REQUEST_ID_MDC_KEY)).isEqualTo(requestId));
	}

	@Test
	@DisplayName("chain이 실패하면 500 완료 로그를 남기고 같은 예외를 다시 던진다")
	void logsFiveHundredAndRethrowsWhenTheChainFails() {
		HttpRequestLoggingFilter filter = new HttpRequestLoggingFilter();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/probe/failure");
		request.addHeader(REQUEST_ID_HEADER, "failure-request-id");
		MockHttpServletResponse response = new MockHttpServletResponse();
		RuntimeException failure = new RuntimeException("chain failure");

		assertThatThrownBy(() -> filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
			throw failure;
		})).isSameAs(failure);

		assertThat(completionEvents()).singleElement().satisfies(event -> {
			assertThat(keyValueFields(event)).containsEntry("status", 500);
			assertThat(mdc(event)).containsEntry(REQUEST_ID_MDC_KEY, "failure-request-id");
		});
		assertThat(MDC.get(REQUEST_ID_MDC_KEY)).isNull();
	}

	@Test
	@DisplayName("같은 thread의 연속 요청은 request ID를 누출하지 않고 unrelated MDC를 보존한다")
	void doesNotLeakRequestIdAcrossSequentialRequestsOnTheSameThread() throws Exception {
		MDC.put(UNRELATED_MDC_KEY, "preserved-value");

		mockMvc.perform(get("/probe/items/first").header(REQUEST_ID_HEADER, "first-request-id"))
				.andExpect(status().isOk());
		assertThat(MDC.get(REQUEST_ID_MDC_KEY)).isNull();
		assertThat(MDC.get(UNRELATED_MDC_KEY)).isEqualTo("preserved-value");

		mockMvc.perform(get("/probe/items/second").header(REQUEST_ID_HEADER, "second-request-id"))
				.andExpect(status().isOk());
		assertThat(MDC.get(REQUEST_ID_MDC_KEY)).isNull();
		assertThat(MDC.get(UNRELATED_MDC_KEY)).isEqualTo("preserved-value");

		assertThat(completionEvents()).extracting(event -> mdc(event).get(REQUEST_ID_MDC_KEY))
				.containsExactly("first-request-id", "second-request-id");
	}

	@Test
	@DisplayName("완료 로그는 실제 URI, query, body와 private sentinel을 기록하지 않는다")
	void doesNotRecordPrivateSentinelsOrActualUriAndQuery() throws Exception {
		String pathSentinel = "path-private-sentinel";
		String querySentinel = "query-private-sentinel";
		String bodySentinel = "body-private-sentinel";
		String tokenSentinel = "token-private-sentinel";
		String locationSentinel = "location-private-sentinel";
		String userSentinel = "user-private-sentinel";

		mockMvc.perform(post("/probe/private/" + pathSentinel + "?q=" + querySentinel)
				.contentType(MediaType.TEXT_PLAIN)
				.content(bodySentinel)
				.header("Authorization", tokenSentinel)
				.header("X-Location", locationSentinel)
				.header("X-User", userSentinel))
				.andExpect(status().isOk());
		mockMvc.perform(get("/probe/domain?q=" + querySentinel)
				.contentType(MediaType.TEXT_PLAIN)
				.content(bodySentinel)
				.header("Authorization", tokenSentinel)
				.header("X-Location", locationSentinel)
				.header("X-User", userSentinel))
				.andExpect(status().isBadRequest());

		List<String> sentinels = List.of(pathSentinel, querySentinel, bodySentinel, tokenSentinel, locationSentinel,
				userSentinel);
		assertThat(completionEvents()).hasSize(2);
		assertThat(errorLogs.events).singleElement();
		assertNoSentinels(requestLogs.events, sentinels);
		assertNoSentinels(errorLogs.events, sentinels);
	}

	@Test
	@DisplayName("겹치는 요청은 worker thread의 request ID를 격리하고 정리한다")
	void isolatesOverlappingRequestIdsAndCleansWorkerThreads() throws Exception {
		HttpRequestLoggingFilter filter = new HttpRequestLoggingFilter();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch enteredChains = new CountDownLatch(2);
		CountDownLatch releaseChains = new CountDownLatch(1);
		try {
			Future<WorkerResult> first = executor
					.submit(worker(filter, "concurrent-first", enteredChains, releaseChains));
			Future<WorkerResult> second = executor
					.submit(worker(filter, "concurrent-second", enteredChains, releaseChains));
			assertThat(enteredChains.await(5, TimeUnit.SECONDS)).isTrue();
			releaseChains.countDown();

			assertWorkerResult(first.get());
			assertWorkerResult(second.get());
		} finally {
			releaseChains.countDown();
			executor.shutdownNow();
		}

		assertThat(completionEvents()).extracting(event -> mdc(event).get(REQUEST_ID_MDC_KEY))
				.containsExactlyInAnyOrder("concurrent-first", "concurrent-second");
	}

	private static Stream<Arguments> allowlistedRequestIds() {
		return Stream.of(
				Arguments.of("A"),
				Arguments.of("safe-Request_1.2"),
				Arguments.of("a".repeat(64)));
	}

	private static Stream<Arguments> unsafeRequestIds() {
		return Stream.of(
				Arguments.of(
						(Consumer<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder>) builder -> {
						}),
				Arguments.of(
						(Consumer<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder>) builder -> builder
								.header(REQUEST_ID_HEADER, "")),
				Arguments.of(
						(Consumer<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder>) builder -> builder
								.header(REQUEST_ID_HEADER, " ")),
				Arguments.of(
						(Consumer<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder>) builder -> builder
								.header(REQUEST_ID_HEADER, "a".repeat(65))),
				Arguments.of(
						(Consumer<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder>) builder -> builder
								.header(REQUEST_ID_HEADER, "요청-식별자")),
				Arguments.of(
						(Consumer<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder>) builder -> builder
								.header(REQUEST_ID_HEADER, "unsafe,value")),
				Arguments.of(
						(Consumer<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder>) builder -> builder
								.header(REQUEST_ID_HEADER, "line\nbreak")));
	}

	private List<ILoggingEvent> completionEvents() {
		return requestLogs.events.stream()
				.filter(event -> "http_request_completed".equals(event.getFormattedMessage()))
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
		return keyValuePairs.stream()
				.collect(java.util.stream.Collectors.toMap(pair -> pair.key, pair -> pair.value));
	}

	private static void assertNoSentinels(List<ILoggingEvent> events, List<String> sentinels) {
		for (ILoggingEvent event : events) {
			for (String sentinel : sentinels) {
				assertThat(event.getFormattedMessage()).doesNotContain(sentinel);
			}
			assertThat(mdc(event).values()).doesNotContainAnyElementsOf(sentinels);
			assertThat(keyValueFields(event).values()).doesNotContainAnyElementsOf(sentinels);
		}
	}

	private static Callable<WorkerResult> worker(
			HttpRequestLoggingFilter filter,
			String requestId,
			CountDownLatch enteredChains,
			CountDownLatch releaseChains) {
		return () -> {
			MockHttpServletRequest request = new MockHttpServletRequest("GET", "/probe/concurrent");
			request.addHeader(REQUEST_ID_HEADER, requestId);
			MockHttpServletResponse response = new MockHttpServletResponse();
			filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
				enteredChains.countDown();
				if (!awaitRelease(releaseChains)) {
					throw new IllegalStateException("concurrent chain was not released");
				}
				assertThat(MDC.get(REQUEST_ID_MDC_KEY)).isEqualTo(requestId);
			});
			return new WorkerResult(requestId, MDC.get(REQUEST_ID_MDC_KEY));
		};
	}

	private static boolean awaitRelease(CountDownLatch releaseChains) {
		try {
			return releaseChains.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("concurrent chain was interrupted", exception);
		}
	}

	private static void assertWorkerResult(WorkerResult result) {
		assertThat(result.requestId()).isIn("concurrent-first", "concurrent-second");
		assertThat(result.requestIdAfterFilter()).isNull();
	}

	private record WorkerResult(String requestId, String requestIdAfterFilter) {
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

		@GetMapping("/probe/items/{itemId}")
		ResponseEntity<Void> item(@PathVariable String itemId) {
			return ResponseEntity.ok().build();
		}

		@GetMapping("/probe/domain")
		void domain() {
			throw new AccountException(
					AccountErrorCode.INVALID_TIMEZONE, "timezone", "timezone은 유효한 IANA ID여야 합니다");
		}

		@PostMapping("/probe/private/{pathSentinel}")
		ResponseEntity<Void> privateProbe(@PathVariable String pathSentinel, @RequestBody String body) {
			return ResponseEntity.ok().build();
		}
	}
}
