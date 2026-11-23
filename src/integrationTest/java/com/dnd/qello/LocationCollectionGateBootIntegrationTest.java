/**
 * Created at: 2026-11-23T16:43:12+09:00
 * Source scenario: TEST-PLAN-GH-379-LOCATION-GATE-INT-002
 */
package com.dnd.qello;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 공용 DB 초기화 확장이 Spring 테스트 컨텍스트를 요구하므로 캐시된 test 컨텍스트를 함께 쓴다.
// 검증 대상은 그 컨텍스트가 아니라 테스트 안에서 새로 띄우는 애플리케이션이다.
@SpringBootTest
@ActiveProfiles("test")
class LocationCollectionGateBootIntegrationTest extends PostgisContainerIntegrationTestSupport {

	@ParameterizedTest
	@ValueSource(strings = {"", "ok"})
	@DisplayName("test 프로필에서 신고 수리 참조가 비었거나 placeholder인 채 게이트를 켜면 실제 YAML로 기동이 실패한다")
	void failsToStartWithoutTraceableReference(String reference) {
		SpringApplicationBuilder builder = new SpringApplicationBuilder(QelloApplication.class)
				.web(WebApplicationType.SERVLET)
				.profiles("test");
		// builder.properties()는 가장 낮은 우선순위라 application-test.yml의 fixture 참조에 덮인다.
		// 명령행 인자로 넘겨 YAML보다 우선하게 한다.
		String[] args = {
				"--server.port=0",
				"--spring.datasource.url=" + postgres.getJdbcUrl(),
				"--spring.datasource.username=" + postgres.getUsername(),
				"--spring.datasource.password=" + postgres.getPassword(),
				"--qello.location.collection.enabled=true",
				"--qello.location.collection.lbs-registration-reference=" + reference};

		assertThatThrownBy(() -> {
			// 기대와 달리 기동되면 서버를 남기지 않도록 닫은 뒤 실패로 드러낸다.
			try (ConfigurableApplicationContext started = builder.run(args)) {
				assertThat(started.isActive()).as("기동되면 안 된다").isFalse();
			}
		})
				.satisfies(failure -> {
					Throwable root = failure;
					while (root.getCause() != null) {
						root = root.getCause();
					}
					assertThat(root).isInstanceOf(IllegalStateException.class)
							.hasMessageContaining("qello.location.collection.lbs-registration-reference");
				});
	}
}
