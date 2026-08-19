/*
 * Created at: 2026-08-19T02:20:00+09:00
 * Source scenario: TEST-PLAN-GH-168-NICKNAME-DUPLICATE-MODERATION-UNIT-022
 */
package com.dnd.qello.filtering.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;

import com.dnd.qello.filtering.domain.FilterDecision;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterReleaseStatus;
import com.dnd.qello.filtering.moderation.GatedNicknameModerationChecker;
import com.dnd.qello.filtering.moderation.NicknameModerationChecker;
import com.dnd.qello.filtering.moderation.NicknameSyncModerationGate;
import com.dnd.qello.filtering.moderation.NoOpNicknameModerationChecker;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;

class NicknameModerationGateConfigTest {

	private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

	// ApplicationContextRunner의 기본 ConversionService는 Boot의 "PT3S" 같은 ISO-8601
	// Duration 문자열 변환기를 포함하지 않는다 — 실제 @SpringBootTest/운영 기동에서는
	// SpringApplication이 ApplicationConversionService를 자동 등록하므로 문제가 없지만,
	// 이 경량 러너에서는 명시적으로 등록해야 @Value(Duration) 바인딩이 통과한다.
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withInitializer(context -> ((ConfigurableApplicationContext) context).getBeanFactory()
			.setConversionService(ApplicationConversionService.getSharedInstance()))
		.withUserConfiguration(NicknameModerationGateConfig.class)
		.withBean(FilterDecisionRepository.class, UnusedFilterDecisionRepository::new)
		.withBean(FilterReleaseRepository.class, () -> new FixedFilterReleaseRepository(promotedRelease()))
		.withBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	@DisplayName("UNIT-022: production gate가 꺼져 있으면 게이트 빈 없이 NoOpNicknameModerationChecker만 등록된다")
	void noGateBeanWhenProductionDisabled() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(NicknameSyncModerationGate.class);
			assertThat(context.getBean(NicknameModerationChecker.class))
				.isInstanceOf(NoOpNicknameModerationChecker.class);
		});
	}

	@Test
	@DisplayName("UNIT-022: production gate가 켜져 있으면 실제 게이트와 GatedNicknameModerationChecker가 등록된다")
	void gateBeanPresentWhenProductionEnabled() {
		runner.withPropertyValues(
				"qello.filtering.production.enabled=true",
				"qello.filtering.nickname-moderation.openai-api-key=example-key-for-unit-test")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(NicknameSyncModerationGate.class);
				assertThat(context.getBean(NicknameModerationChecker.class))
					.isInstanceOf(GatedNicknameModerationChecker.class);
			});
	}

	@Test
	@DisplayName("UNIT-022: production gate가 켜져 있는데 API 키가 비어 있으면 기동이 실패한다")
	void contextFailsWhenApiKeyMissing() {
		runner.withPropertyValues("qello.filtering.production.enabled=true")
			.run(context -> assertThat(context).hasFailed());
	}

	private static FilterRelease promotedRelease() {
		return FilterRelease.restore(1L, "norm-v1", "ruleset-v1", "category-map-v1", "model-v1",
			FilterReleaseStatus.PROMOTED, NOW, NOW);
	}

	private static final class UnusedFilterDecisionRepository implements FilterDecisionRepository {
		@Override
		public FilterDecision save(FilterDecision decision) {
			throw new AssertionError("이 테스트에서 호출되지 않아야 합니다");
		}

		@Override
		public Optional<FilterDecision> findById(long id) {
			throw new AssertionError("이 테스트에서 호출되지 않아야 합니다");
		}

		@Override
		public Optional<FilterDecision> findByFilterJobIdAndAttemptGeneration(long filterJobId, int attemptGeneration) {
			throw new AssertionError("이 테스트에서 호출되지 않아야 합니다");
		}
	}

	private static final class FixedFilterReleaseRepository implements FilterReleaseRepository {
		private final FilterRelease release;

		private FixedFilterReleaseRepository(FilterRelease release) {
			this.release = release;
		}

		@Override
		public FilterRelease save(FilterRelease release) {
			throw new AssertionError("이 테스트에서 호출되지 않아야 합니다");
		}

		@Override
		public Optional<FilterRelease> findById(long id) {
			throw new AssertionError("이 테스트에서 호출되지 않아야 합니다");
		}

		@Override
		public Optional<FilterRelease> findCurrentlyPromoted() {
			return Optional.of(release);
		}

		@Override
		public java.util.List<FilterRelease> findAll() {
			throw new AssertionError("이 테스트에서 호출되지 않아야 합니다");
		}
	}
}
