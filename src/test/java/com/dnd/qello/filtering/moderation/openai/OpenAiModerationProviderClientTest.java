/**
 * Created at: 2026-08-14T23:50:00+09:00
 * Source scenario: TEST-PLAN-GH-108-ANSWER-MODERATION-RETRY-UNIT-009, UNIT-010
 */
package com.dnd.qello.filtering.moderation.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.moderation.ModerationRateLimitedException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class OpenAiModerationProviderClientTest {

	private FakeHttpModerationServer fakeServer;
	private OpenAiModerationProviderClient client;

	@BeforeEach
	void startServer() throws IOException {
		fakeServer = new FakeHttpModerationServer();
		fakeServer.start();
		ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk()
			.build(ClientHttpRequestFactorySettings.defaults()
				.withConnectTimeout(Duration.ofSeconds(2))
				.withReadTimeout(Duration.ofSeconds(5)));
		RestClient restClient = RestClient.builder().baseUrl(fakeServer.baseUrl()).requestFactory(requestFactory).build();
		client = new OpenAiModerationProviderClient(restClient);
	}

	@AfterEach
	void stopServer() {
		fakeServer.stop();
	}

	@Test
	@DisplayName("429 응답에 유효한 Retry-After 헤더가 있으면 그 값을 담아 ModerationRateLimitedException을 던진다")
	void capturesValidRetryAfterHeader() {
		fakeServer.respondWith(429, "{}", "30");

		assertThatThrownBy(() -> client.moderate("content", "model-v1"))
			.isInstanceOf(ModerationRateLimitedException.class)
			.satisfies(e -> assertThat(((ModerationRateLimitedException) e).retryAfter())
				.isEqualTo(Duration.ofSeconds(30)));
	}

	@Test
	@DisplayName("429 응답에 Retry-After 헤더가 없으면 힌트 없이 ModerationRateLimitedException을 던진다")
	void handlesMissingRetryAfterHeader() {
		fakeServer.respondWith(429, "{}", null);

		assertThatThrownBy(() -> client.moderate("content", "model-v1"))
			.isInstanceOf(ModerationRateLimitedException.class)
			.satisfies(e -> assertThat(((ModerationRateLimitedException) e).retryAfter()).isNull());
	}

	@Test
	@DisplayName("429 응답의 Retry-After 헤더가 정수가 아니면 힌트 없이 ModerationRateLimitedException을 던진다")
	void handlesMalformedRetryAfterHeader() {
		fakeServer.respondWith(429, "{}", "not-a-number");

		assertThatThrownBy(() -> client.moderate("content", "model-v1"))
			.isInstanceOf(ModerationRateLimitedException.class)
			.satisfies(e -> assertThat(((ModerationRateLimitedException) e).retryAfter()).isNull());
	}

	@Test
	@DisplayName("429가 아닌 실패는 기존과 동일하게 FilteringException(MODERATION_PROVIDER_UNAVAILABLE)로 처리된다")
	void nonRateLimitFailuresKeepExistingBehavior() {
		fakeServer.respondWith(500, "{}", null);

		assertThatThrownBy(() -> client.moderate("content", "model-v1"))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE);
	}

	// 실제 OpenAI 계정·네트워크 없이 HTTP 경계(status·헤더)를 검증하기 위한 로컬 fake
	// 서버. ModerationPipelineIntegrationTest의 FakeHttpModerationServer와 같은
	// 패턴(JDK 내장 HttpServer, 신규 외부 라이브러리 없음)이지만, Retry-After 헤더를
	// 설정할 수 있어야 해서 이 파일 전용으로 별도로 둔다.
	private static final class FakeHttpModerationServer {
		private final HttpServer server;
		private final ExecutorService executor;
		private volatile int statusCode = 200;
		private volatile String responseBody = "{}";
		private volatile String retryAfterHeader;

		FakeHttpModerationServer() throws IOException {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/v1/moderations", this::handle);
			executor = Executors.newSingleThreadExecutor();
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

		void respondWith(int statusCode, String body, String retryAfterHeader) {
			this.statusCode = statusCode;
			this.responseBody = body;
			this.retryAfterHeader = retryAfterHeader;
		}

		private void handle(HttpExchange exchange) throws IOException {
			byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			if (retryAfterHeader != null) {
				exchange.getResponseHeaders().add("Retry-After", retryAfterHeader);
			}
			exchange.sendResponseHeaders(statusCode, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
			exchange.close();
		}
	}
}
