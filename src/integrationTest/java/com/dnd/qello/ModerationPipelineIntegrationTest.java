/*
 * Created at: 2026-08-11T22:10:00+09:00
 * Source scenario: TEST-PLAN-GH-105-MODERATION-PIPELINE-INT-001 through INT-005
 * (INT-006은 Spring 빈 구성이 없어 이 통합 테스트로 다루지 않는다 — 실행 자원
 * 격리는 ModerationPipelineServiceTest#UNIT-013이 순수 자바 동시성 테스트로
 * 이미 검증했다)
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.dnd.qello.filtering.domain.FilterDecision;
import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.moderation.LocalRuleVerdict;
import com.dnd.qello.filtering.moderation.ModerationLanguage;
import com.dnd.qello.filtering.moderation.ModerationPipelineRequest;
import com.dnd.qello.filtering.moderation.ModerationPipelineResult;
import com.dnd.qello.filtering.moderation.ModerationPipelineService;
import com.dnd.qello.filtering.moderation.openai.OpenAiModerationProviderClient;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;

@SpringBootTest
@ActiveProfiles("test")
class ModerationPipelineIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
	private static final String MODEL_SNAPSHOT = "omni-moderation-2024-09-26";

	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private FilterReleaseRepository filterReleaseRepository;
	@Autowired
	private FilterJobRepository filterJobRepository;
	@Autowired
	private FilterDecisionRepository filterDecisionRepository;

	private FakeHttpModerationServer fakeServer;
	private long releaseId;

	@BeforeEach
	void setUp() throws IOException {
		jdbcTemplate.update("DELETE FROM filter_decision");
		jdbcTemplate.update("DELETE FROM filter_job");
		jdbcTemplate.update("DELETE FROM filter_release");
		releaseId = filterReleaseRepository.save(
			FilterRelease.candidate("norm-v1", "ruleset-v1", "category-map-v1", MODEL_SNAPSHOT, NOW)).id();

		fakeServer = new FakeHttpModerationServer();
		fakeServer.start();
	}

	@AfterEach
	void tearDown() {
		fakeServer.stop();
	}

	@Test
	@DisplayName("로컬 규칙 미적중 + 정책 결합 BLOCK이면 filter_decision에 requestedReleaseId·actualModel과 함께 저장된다")
	void persistsBlockDecisionWithReleaseAndActualModel() {
		fakeServer.respondWith(200, moderationResponseJson(true, MODEL_SNAPSHOT));
		FilterJob job = createJob();
		ModerationPipelineService pipeline = pipeline(LocalRuleVerdict.noMatch(), FilterVerdict.BLOCK);

		ModerationPipelineResult result = pipeline.execute(ModerationPipelineRequest.forJob(
			FilterTargetType.ANSWER, ModerationLanguage.KO, "차단 대상 텍스트", releaseFixture(), job.id(), 1));

		assertThat(result.verdict()).isEqualTo(FilterVerdict.BLOCK);
		FilterDecision saved =
			filterDecisionRepository.findByFilterJobIdAndAttemptGeneration(job.id(), 1).orElseThrow();
		assertThat(saved.verdict()).isEqualTo(FilterVerdict.BLOCK);
		assertThat(saved.requestedReleaseId()).isEqualTo(releaseId);
		assertThat(saved.actualModel()).isEqualTo(MODEL_SNAPSHOT);
		assertThat(fakeServer.requestCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("로컬 규칙 BLOCK 적중이면 filter_decision은 저장되지만 공급자 호출은 0회다")
	void persistsRuleBlockedDecisionWithoutCallingProvider() {
		FilterJob job = createJob();
		ModerationPipelineService pipeline = pipeline(LocalRuleVerdict.block("rule-1"), FilterVerdict.ALLOW);

		ModerationPipelineResult result = pipeline.execute(ModerationPipelineRequest.forJob(
			FilterTargetType.ANSWER, ModerationLanguage.KO, "금칙어 포함 텍스트", releaseFixture(), job.id(), 1));

		assertThat(result.verdict()).isEqualTo(FilterVerdict.BLOCK);
		assertThat(filterDecisionRepository.findByFilterJobIdAndAttemptGeneration(job.id(), 1)).isPresent();
		assertThat(fakeServer.requestCount()).isZero();
	}

	@Test
	@DisplayName("공급자 timeout이면 filter_decision이 생성되지 않고 판정 불가 예외가 호출자에게 전달된다")
	void doesNotPersistDecisionOnProviderTimeout() {
		fakeServer.respondAfterDelay(Duration.ofSeconds(3), 200, moderationResponseJson(false, MODEL_SNAPSHOT));
		FilterJob job = createJob();
		ModerationPipelineService pipeline =
			pipeline(LocalRuleVerdict.noMatch(), FilterVerdict.ALLOW, Duration.ofMillis(300));

		assertThatThrownBy(() -> pipeline.execute(ModerationPipelineRequest.forJob(
			FilterTargetType.ANSWER, ModerationLanguage.KO, "일반 텍스트", releaseFixture(), job.id(), 1)))
			.isInstanceOf(FilteringException.class);

		assertThat(filterDecisionRepository.findByFilterJobIdAndAttemptGeneration(job.id(), 1)).isEmpty();
	}

	@Test
	@DisplayName("동일 filterJobId+attemptGeneration으로 두 번째 실행하면 filter_decision 행은 정확히 1개만 남는다")
	void keepsExactlyOneDecisionRowOnDuplicateExecution() {
		fakeServer.respondWith(200, moderationResponseJson(false, MODEL_SNAPSHOT));
		FilterJob job = createJob();
		ModerationPipelineService pipeline = pipeline(LocalRuleVerdict.noMatch(), FilterVerdict.ALLOW);
		ModerationPipelineRequest request = ModerationPipelineRequest.forJob(
			FilterTargetType.ANSWER, ModerationLanguage.KO, "일반 텍스트", releaseFixture(), job.id(), 1);

		pipeline.execute(request);

		assertThatThrownBy(() -> pipeline.execute(request)).isInstanceOf(DataIntegrityViolationException.class);

		Integer rowCount = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM filter_decision WHERE filter_job_id = ? AND attempt_generation = 1",
			Integer.class, job.id());
		assertThat(rowCount).isEqualTo(1);
	}

	@Test
	@DisplayName("filterJobId 없이 호출하면 판정은 반환되지만 filter_decision에는 저장되지 않는다")
	void doesNotPersistWhenFilterJobIdAbsent() {
		fakeServer.respondWith(200, moderationResponseJson(false, MODEL_SNAPSHOT));
		ModerationPipelineService pipeline = pipeline(LocalRuleVerdict.noMatch(), FilterVerdict.ALLOW);

		ModerationPipelineResult result = pipeline.execute(ModerationPipelineRequest.ephemeral(
			FilterTargetType.NICKNAME, ModerationLanguage.KO, "닉네임 후보", releaseFixture()));

		assertThat(result.verdict()).isEqualTo(FilterVerdict.ALLOW);
		Integer rowCount = jdbcTemplate.queryForObject("SELECT count(*) FROM filter_decision", Integer.class);
		assertThat(rowCount).isZero();
	}

	private FilterJob createJob() {
		return filterJobRepository.save(FilterJob.create(
			FilterTarget.of(FilterTargetType.ANSWER, 1L), releaseId, "job-" + UUID.randomUUID(), NOW));
	}

	private FilterRelease releaseFixture() {
		return filterReleaseRepository.findById(releaseId).orElseThrow();
	}

	private ModerationPipelineService pipeline(LocalRuleVerdict ruleVerdict, FilterVerdict policyVerdict) {
		return pipeline(ruleVerdict, policyVerdict, Duration.ofSeconds(5));
	}

	private ModerationPipelineService pipeline(
		LocalRuleVerdict ruleVerdict, FilterVerdict policyVerdict, Duration readTimeout
	) {
		ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk()
			.build(ClientHttpRequestFactorySettings.defaults()
				.withConnectTimeout(Duration.ofSeconds(2))
				.withReadTimeout(readTimeout));
		RestClient restClient = RestClient.builder()
			.baseUrl(fakeServer.baseUrl())
			.requestFactory(requestFactory)
			.build();
		OpenAiModerationProviderClient providerClient = new OpenAiModerationProviderClient(restClient);

		return new ModerationPipelineService(
			(rawContent, normalizationRef) -> rawContent,
			(normalizedContent, localRulesetRef) -> ruleVerdict,
			providerClient,
			(providerResult, contentType, language, categoryMappingRef) -> policyVerdict,
			filterDecisionRepository,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static String moderationResponseJson(boolean flagged, String model) {
		return """
			{"id":"modr-test","model":"%s","results":[{"flagged":%s,\
			"categories":{"harassment":%s},"category_scores":{"harassment":%s}}]}\
			""".formatted(model, flagged, flagged, flagged ? "0.9" : "0.01");
	}

	// 실제 OpenAI 계정·네트워크 없이 HTTP 경계(timeout·지연·응답 형태)를 검증하기
	// 위한 로컬 fake 서버. 신규 외부 라이브러리(WireMock 등) 없이 JDK 내장
	// HttpServer만 사용한다(TEST-PLAN-GH-105-MODERATION-PIPELINE §8).
	private static final class FakeHttpModerationServer {
		private final HttpServer server;
		private final AtomicInteger requestCount = new AtomicInteger();
		private volatile int statusCode = 200;
		private volatile String responseBody = "{}";
		private volatile Duration delay = Duration.ZERO;

		FakeHttpModerationServer() throws IOException {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/v1/moderations", this::handle);
			server.setExecutor(Executors.newSingleThreadExecutor());
		}

		void start() {
			server.start();
		}

		void stop() {
			server.stop(0);
		}

		String baseUrl() {
			return "http://127.0.0.1:" + server.getAddress().getPort();
		}

		void respondWith(int statusCode, String body) {
			this.statusCode = statusCode;
			this.responseBody = body;
			this.delay = Duration.ZERO;
		}

		void respondAfterDelay(Duration delay, int statusCode, String body) {
			this.delay = delay;
			this.statusCode = statusCode;
			this.responseBody = body;
		}

		int requestCount() {
			return requestCount.get();
		}

		private void handle(HttpExchange exchange) throws IOException {
			requestCount.incrementAndGet();
			if (!delay.isZero()) {
				try {
					Thread.sleep(delay.toMillis());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(statusCode, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
			exchange.close();
		}
	}
}
