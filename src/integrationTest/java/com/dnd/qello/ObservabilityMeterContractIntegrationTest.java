/**
 * Created at: 2026-09-05T18:32:31+09:00
 * Source scenario: TEST-PLAN-GH-218-OBSERVABILITY-METRICS-EXPOSURE-INT-007 through INT-008
 */
package com.dnd.qello;

import java.sql.Connection;
import java.time.Duration;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.filtering.observability.FilteringMetrics;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
// Spring Boot 테스트는 외부 MeterRegistry를 기본 비활성화하므로 실제 Prometheus
// scrape와 histogram을 검증하려면 observability auto-configuration을 복원해야 한다.
@AutoConfigureObservability
@ActiveProfiles({"test", "observability"})
class ObservabilityMeterContractIntegrationTest extends PostgisContainerIntegrationTestSupport {

	// Prometheus는 Timer 이름의 점을 밑줄로 바꾸고 단위 suffix를 붙인다.
	private static final List<String> HISTOGRAM_PREFIXES = List.of(
			"http_server_requests_seconds",
			"qello_filtering_pipeline_latency_seconds",
			"hikaricp_connections_acquire_seconds",
			"hikaricp_connections_usage_seconds");

	// tag로 새어 나오면 안 되는 값의 키. 지표는 "지금 밀리는가"에 답하고,
	// "누가 무엇에서 실패했는가"는 로그가 답한다.
	private static final List<String> FORBIDDEN_TAG_KEYS = List.of(
			"userId", "user_id", "requestId", "request_id",
			"correlationId", "correlation_id", "eventId", "event_id",
			"postId", "post_id", "nickname", "token", "exception_message");

	@LocalServerPort
	private int appPort;

	// MockMvc는 실제 listener를 열지 않아 app port와 management child context의 경계를
	// 거친 scrape를 검증할 수 없다.
	@Value("${local.management.port}")
	private int managementPort;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private FilteringMetrics filteringMetrics;

	@Autowired
	private DataSource dataSource;

	@BeforeEach
	void recordMeterSamples() throws Exception {
		// http.server.requests를 등록시킨다. 인증 실패든 성공이든 Timer는 기록된다.
		restTemplate.getForEntity("http://localhost:" + appPort + "/api/v1/direction/inbox", String.class);

		// qello.filtering.pipeline.latency Timer를 등록시킨다.
		// tag 값은 FilteringMetricTags의 ENUM_TOKEN 형태를 따른다.
		filteringMetrics.recordPipeline("ANSWER", "KOREAN", "SUCCESS", Duration.ofMillis(5));

		// hikaricp.connections.* Timer는 connection을 최소 한 번 획득해야 등록된다.
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.isValid(2)).isTrue();
		}
	}

	@Test
	@DisplayName("INT-007: scrape 본문에 네 Timer의 bucket, count, sum 시계열이 존재한다")
	void exposesHistogramSeriesForConfiguredTimers() {
		String scrape = scrape();

		for (String prefix : HISTOGRAM_PREFIXES) {
			assertThat(scrape)
					.as("%s_bucket", prefix)
					.contains(prefix + "_bucket");
			assertThat(scrape)
					.as("%s_count", prefix)
					.contains(prefix + "_count");
			assertThat(scrape)
					.as("%s_sum", prefix)
					.contains(prefix + "_sum");
		}
	}

	@Test
	@DisplayName("INT-008: 노출된 지표의 tag에 사용자·요청 식별자가 없다")
	void keepsExposedTagsBounded() {
		String scrape = scrape();

		for (String forbidden : FORBIDDEN_TAG_KEYS) {
			assertThat(scrape)
					.as("금지 tag 키 %s", forbidden)
					.doesNotContain(forbidden + "=");
		}
	}

	private String scrape() {
		return restTemplate
				.getForEntity("http://localhost:" + managementPort + "/actuator/prometheus", String.class)
				.getBody();
	}
}
