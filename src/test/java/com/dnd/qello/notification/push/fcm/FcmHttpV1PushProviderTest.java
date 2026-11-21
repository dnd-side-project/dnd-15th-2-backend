/**
 * Created at: 2026-08-24T21:15:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-013 through UNIT-014,
 * TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-018
 * Extended at: 2026-11-21T08:45:04Z
 * Source scenario: TEST-PLAN-GH-365-PUSH-FID-PROVIDER-UNIT-003 through UNIT-005
 */
package com.dnd.qello.notification.push.fcm;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.dnd.qello.notification.push.PushPayload;
import com.dnd.qello.notification.push.PushProviderResult;
import com.dnd.qello.notification.push.PushSendCommand;
import com.dnd.qello.notification.push.security.PushToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

class FcmHttpV1PushProviderTest {

	private static final String BEARER_VALUE = "test-credential";
	private static final String DEVICE_TOKEN = "test-device-token";

	private final ObjectMapper objectMapper = new ObjectMapper();
	private FakeFcmServer fakeServer;
	private FcmHttpV1PushProvider provider;

	@BeforeEach
	void startServer() throws IOException {
		fakeServer = new FakeFcmServer();
		fakeServer.start();
		provider = providerWith(Duration.ofSeconds(1));
	}

	@AfterEach
	void stopServer() {
		fakeServer.stop();
	}

	@Test
	@DisplayName("UNIT-003 / UNIT-014: FCM 요청은 bearer, projects path와 FID, HIGH, 허용된 data만 전송한다")
	void sendsBearerPathAndAllowlistedPayload() throws IOException {
		fakeServer.respondWith(200,
				"{\"name\":\"projects/test-project/messages/test-message-unit-014\"}", null, Duration.ZERO);

		PushProviderResult result = provider.send(command());

		assertThat(result).isEqualTo(
				new PushProviderResult.Accepted("projects/test-project/messages/test-message-unit-014"));
		assertThat(fakeServer.requestPath()).isEqualTo("/v1/projects/test-project/messages:send");
		assertThat(fakeServer.requestHeader("Authorization")).isEqualTo("Bearer " + BEARER_VALUE);
		JsonNode message = objectMapper.readTree(fakeServer.requestBody()).path("message");
		assertThat(message.fieldNames()).toIterable().containsExactlyInAnyOrder("fid", "data", "android");
		assertThat(message.path("fid").asText()).isEqualTo(DEVICE_TOKEN);
		assertThat(message.has("token")).isFalse();
		assertThat(message.path("android").fieldNames()).toIterable().containsExactly("priority");
		assertThat(message.path("android").path("priority").asText()).isEqualTo("HIGH");
		assertThat(message.path("data").fieldNames()).toIterable()
				.containsExactlyInAnyOrder("type", "count", "hasRemainingTime");
		assertThat(message.path("data").path("type").asText()).isEqualTo("ANSWER_RECEIVED");
		assertThat(message.path("data").path("count").asText()).isEqualTo("1");
		assertThat(message.path("data").path("hasRemainingTime").asText()).isEqualTo("true");
	}

	@Test
	@DisplayName("UNIT-013: FCM 2xx success body의 name을 Accepted providerMessageId로 보존한다")
	void mapsSuccessNameToAcceptedProviderMessageId() {
		fakeServer.respondWith(200,
				"{\"name\":\"projects/test-project/messages/test-message-success\"}", null, Duration.ZERO);

		assertThat(provider.send(command())).isEqualTo(
				new PushProviderResult.Accepted("projects/test-project/messages/test-message-success"));
	}

	@Test
	@DisplayName("UNIT-013: FCM 2xx body의 name이 없거나 malformed이면 safe PermanentFailure로 변환한다")
	void rejectsMissingMalformedOrUnsafeSuccessName() {
		fakeServer.respondWith(200, "{}", null, Duration.ZERO);
		assertThat(provider.send(command()))
				.isEqualTo(new PushProviderResult.PermanentFailure("INVALID_SUCCESS_RESPONSE"));

		fakeServer.respondWith(200, "{\"name\":", null, Duration.ZERO);
		assertThat(provider.send(command()))
				.isEqualTo(new PushProviderResult.PermanentFailure("INVALID_SUCCESS_RESPONSE"));

		fakeServer.respondWith(200, "{\"name\":\"contains whitespace\"}", null, Duration.ZERO);
		assertThat(provider.send(command()))
				.isEqualTo(new PushProviderResult.PermanentFailure("INVALID_SUCCESS_RESPONSE"));

		fakeServer.respondWith(200, "{\"name\":179}", null, Duration.ZERO);
		assertThat(provider.send(command()))
				.isEqualTo(new PushProviderResult.PermanentFailure("INVALID_SUCCESS_RESPONSE"));
	}

	@Test
	@DisplayName("UNIT-004 / UNIT-013: UNREGISTERED는 InvalidToken으로 변환한다")
	void mapsUnregisteredToInvalidToken() {
		fakeServer.respondWith(404, "{\"error\":{\"status\":\"UNREGISTERED\"}}", null, Duration.ZERO);

		assertThat(provider.send(command())).isInstanceOf(PushProviderResult.InvalidToken.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"message.token", "message.fid"})
	@DisplayName("UNIT-005 / UNIT-013: token 또는 fid 오류가 명시된 유효 payload만 InvalidToken으로 변환한다")
	void mapsClearlyTokenScopedInvalidArgumentToInvalidToken(String field) {
		fakeServer.respondWith(400, """
				{"error":{"status":"INVALID_ARGUMENT","details":[{"fieldViolations":[{"field":"%s"}]}]}}
				""".formatted(field), null, Duration.ZERO);

		assertThat(provider.send(command())).isInstanceOf(PushProviderResult.InvalidToken.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"message.token", "message.fid"})
	@DisplayName("UNIT-005 / UNIT-018: 양의 count도 token 또는 fid 오류가 명시되면 InvalidToken으로 변환한다")
	void treatsPositiveCountAsAllowlistedForInvalidToken(String field) {
		fakeServer.respondWith(400, """
				{"error":{"status":"INVALID_ARGUMENT","details":[{"fieldViolations":[{"field":"%s"}]}]}}
				""".formatted(field), null, Duration.ZERO);

		assertThat(provider.send(commandWithCount("3"))).isInstanceOf(PushProviderResult.InvalidToken.class);
	}

	@DisplayName("UNIT-005 / UNIT-013: 기기 원인이 명확하지 않은 INVALID_ARGUMENT는 PermanentFailure로 변환한다")
	@ParameterizedTest
	@ValueSource(strings = {
			"{\"error\":{\"status\":\"INVALID_ARGUMENT\"}}",
			"{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"fieldViolations\":[{}]}]}}",
			"{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"fieldViolations\":[{\"field\":\"message.data\"}]}]}}"
	})
	void keepsAmbiguousInvalidArgumentPermanent(String body) {
		fakeServer.respondWith(400, body, null, Duration.ZERO);

		assertThat(provider.send(command()))
				.isEqualTo(new PushProviderResult.PermanentFailure("INVALID_ARGUMENT"));
	}

	@DisplayName("UNIT-005: token 또는 fid 오류라도 HTTP 400과 INVALID_ARGUMENT 조건이 아니면 영구 실패다")
	@ParameterizedTest
	@CsvSource({
			"401, INVALID_ARGUMENT, message.token",
			"401, INVALID_ARGUMENT, message.fid",
			"400, FAILED_PRECONDITION, message.token",
			"400, FAILED_PRECONDITION, message.fid"
	})
	void keepsDeviceFieldViolationPermanentWithoutInvalidArgumentConditions(int status, String reason, String field) {
		fakeServer.respondWith(status, """
				{"error":{"status":"%s","details":[{"fieldViolations":[{"field":"%s"}]}]}}
				""".formatted(reason, field), null, Duration.ZERO);

		assertThat(provider.send(command()))
				.isEqualTo(new PushProviderResult.PermanentFailure(reason));
	}

	@Test
	@DisplayName("UNIT-013: RFC 1123 Retry-After를 retryable backoff hint로 변환한다")
	void mapsHttpDateRetryAfterToRetryableFailure() {
		String retryAfter = DateTimeFormatter.RFC_1123_DATE_TIME.format(
				java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(2));
		fakeServer.respondWith(429, "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}", retryAfter, Duration.ZERO);

		PushProviderResult result = provider.send(command());

		assertThat(result).isInstanceOf(PushProviderResult.RetryableFailure.class);
		assertThat(((PushProviderResult.RetryableFailure) result).retryAfter())
				.isBetween(Duration.ofMinutes(1), Duration.ofMinutes(2));
	}

	@Test
	@DisplayName("UNIT-013: JSON이 아닌 FCM 오류 body는 원문 없이 PermanentFailure로 변환한다")
	void mapsMalformedErrorBodyWithoutSurfacingItsContents() {
		fakeServer.respondWith(400, "upstream-body-sentinel", null, Duration.ZERO);

		PushProviderResult result = provider.send(command());

		assertThat(result).isEqualTo(new PushProviderResult.PermanentFailure("HTTP_ERROR"));
	}

	@Test
	@DisplayName("UNIT-013: 잘린 FCM 오류 body의 IOException은 원문 없이 PermanentFailure로 변환한다")
	void mapsTruncatedErrorBodyWithoutSurfacingItsContents() {
		fakeServer.respondWithTruncated(400, "{\"error\":", 64);

		PushProviderResult result = provider.send(command());

		assertThat(result).isEqualTo(new PushProviderResult.PermanentFailure("HTTP_ERROR"));
	}

	@Test
	@DisplayName("UNIT-013: 인증 실패는 safe code만 가진 PermanentFailure로 변환한다")
	void mapsAuthenticationFailureToPermanentFailure() {
		fakeServer.respondWith(401, "{\"error\":{\"status\":\"UNAUTHENTICATED\"}}", null, Duration.ZERO);

		assertThat(provider.send(command()))
				.isEqualTo(new PushProviderResult.PermanentFailure("UNAUTHENTICATED"));
	}

	@Test
	@DisplayName("UNIT-013: 5xx와 read timeout은 RetryableFailure로 변환한다")
	void mapsServerFailuresAndTimeoutsToRetryableFailure() {
		fakeServer.respondWith(503, "{}", null, Duration.ZERO);
		assertThat(provider.send(command())).isEqualTo(new PushProviderResult.RetryableFailure(null));

		fakeServer.respondWith(200, "{}", null, Duration.ofMillis(300));
		FcmHttpV1PushProvider shortTimeoutProvider = providerWith(Duration.ofMillis(50));
		assertThat(shortTimeoutProvider.send(command())).isEqualTo(new PushProviderResult.RetryableFailure(null));
	}

	private FcmHttpV1PushProvider providerWith(Duration readTimeout) {
		ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk()
				.build(ClientHttpRequestFactorySettings.defaults()
						.withConnectTimeout(Duration.ofSeconds(1))
						.withReadTimeout(readTimeout));
		RestClient restClient = RestClient.builder()
				.baseUrl(fakeServer.baseUrl())
				.requestFactory(requestFactory)
				.build();
		return new FcmHttpV1PushProvider(restClient, () -> BEARER_VALUE, objectMapper, "test-project");
	}

	private static PushSendCommand command() {
		return commandWithCount("1");
	}

	private static PushSendCommand commandWithCount(String count) {
		return new PushSendCommand(
				PushToken.of(DEVICE_TOKEN),
				new PushPayload("ANSWER_RECEIVED", count, "true"));
	}

	private static final class FakeFcmServer {

		private final HttpServer server;
		private final ExecutorService executor;
		private volatile int statusCode = 200;
		private volatile String responseBody = "{}";
		private volatile String retryAfterHeader;
		private volatile Duration responseDelay = Duration.ZERO;
		private volatile int declaredResponseLength = -1;
		private volatile String requestPath;
		private volatile String requestBody;
		private volatile com.sun.net.httpserver.Headers requestHeaders;

		FakeFcmServer() throws IOException {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", this::handle);
			executor = Executors.newCachedThreadPool();
			server.setExecutor(executor);
		}

		void start() {
			server.start();
		}

		void stop() {
			server.stop(0);
			executor.shutdownNow();
		}

		String baseUrl() {
			return "http://127.0.0.1:" + server.getAddress().getPort();
		}

		void respondWith(int statusCode, String body, String retryAfterHeader, Duration responseDelay) {
			this.statusCode = statusCode;
			this.responseBody = body;
			this.retryAfterHeader = retryAfterHeader;
			this.responseDelay = responseDelay;
			this.declaredResponseLength = -1;
		}

		void respondWithTruncated(int statusCode, String body, int declaredLength) {
			this.statusCode = statusCode;
			this.responseBody = body;
			this.retryAfterHeader = null;
			this.responseDelay = Duration.ZERO;
			this.declaredResponseLength = declaredLength;
		}

		String requestPath() {
			return requestPath;
		}

		String requestBody() {
			return requestBody;
		}

		String requestHeader(String name) {
			return requestHeaders.getFirst(name);
		}

		private void handle(HttpExchange exchange) throws IOException {
			requestPath = exchange.getRequestURI().getPath();
			requestHeaders = exchange.getRequestHeaders();
			requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			try {
				Thread.sleep(responseDelay.toMillis());
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			}
			byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			if (retryAfterHeader != null) {
				exchange.getResponseHeaders().add("Retry-After", retryAfterHeader);
			}
			exchange.sendResponseHeaders(statusCode,
					declaredResponseLength >= 0 ? declaredResponseLength : bytes.length);
			try (OutputStream output = exchange.getResponseBody()) {
				output.write(bytes);
			}
		}

	}

}
