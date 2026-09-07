/**
 * Created at: 2026-09-07T10:05:00+09:00
 * Source scenario: TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING-UNIT-001 through UNIT-005
 */
package com.dnd.qello.filtering.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterReleaseStatus;
import com.dnd.qello.filtering.domain.ManualReviewPriorityPolicy;
import com.dnd.qello.filtering.domain.RetryGateConfig;
import com.dnd.qello.filtering.moderation.AnswerModerationExecutionWorker;
import com.dnd.qello.filtering.moderation.AnswerModerationRetryPolicy;
import com.dnd.qello.filtering.moderation.ModerationPipelineService;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterJobStatusHistoryRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRetryGateRepository;
import com.dnd.qello.filtering.repository.ManualReviewCaseRepository;
import com.dnd.qello.filtering.repository.ManualReviewPriorityEvaluationRepository;
import com.dnd.qello.notification.repository.NotificationEventRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnswerModerationExecutionConfigTest {

	private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");

	// production.enabled가 켜졌을 때 answer 전용 배선이 요구하는 모든 값. 테스트
	// 전용 값이며 운영 권장값이 아니다.
	private static final String[] ALL_REQUIRED_VALUES = {
			"qello.filtering.production.enabled=true",
			"qello.filtering.answer-moderation.openai-api-key=example-key-for-unit-test",
			"qello.filtering.answer-moderation.retry-gate.degrade-threshold=3",
			"qello.filtering.answer-moderation.retry-gate.min-limit=1",
			"qello.filtering.answer-moderation.retry-gate.ramp-step=1",
			"qello.filtering.answer-moderation.retry-gate.recovery-streak=3",
			"qello.filtering.answer-moderation.retry-gate.healthy-limit=10",
			"qello.filtering.answer-moderation.retry-policy.fast-backoff-base=PT1S",
			"qello.filtering.answer-moderation.retry-policy.fast-backoff-max=PT30S",
			"qello.filtering.answer-moderation.retry-policy.slow-backoff-base=PT30S",
			"qello.filtering.answer-moderation.retry-policy.slow-backoff-max=PT10M",
			"qello.filtering.answer-moderation.retry-policy.max-attempts=5",
			"qello.filtering.answer-moderation.retry-policy.max-retry-lifetime=P1D",
			"qello.filtering.answer-moderation.manual-review-priority.high-band-report-signal-threshold=3",
			"qello.filtering.answer-moderation.manual-review-priority.aging-threshold=PT1H",
			"qello.filtering.answer-moderation.manual-review-priority.policy-version=test-v1"
	};

	// ApplicationContextRunner의 기본 ConversionService는 "PT3S" 같은 ISO-8601
	// Duration 문자열 변환기를 포함하지 않는다(NicknameModerationGateConfigTest와
	// 같은 이유).
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withInitializer(context -> ((ConfigurableApplicationContext) context).getBeanFactory()
					.setConversionService(ApplicationConversionService.getSharedInstance()))
			.withUserConfiguration(AnswerModerationExecutionConfig.class)
			.withBean(FilterDecisionRepository.class, () -> mock(FilterDecisionRepository.class))
			.withBean(FilterJobRepository.class, () -> mock(FilterJobRepository.class))
			.withBean(FilterReleaseRepository.class, AnswerModerationExecutionConfigTest::promotedReleaseRepository)
			.withBean(FilterJobStatusHistoryRepository.class, () -> mock(FilterJobStatusHistoryRepository.class))
			.withBean(OutboxEventRepository.class, () -> mock(OutboxEventRepository.class))
			.withBean(FilterReleaseRetryGateRepository.class, () -> mock(FilterReleaseRetryGateRepository.class))
			.withBean(ManualReviewCaseRepository.class, () -> mock(ManualReviewCaseRepository.class))
			.withBean(ManualReviewPriorityEvaluationRepository.class,
					() -> mock(ManualReviewPriorityEvaluationRepository.class))
			.withBean(NotificationEventRepository.class, () -> mock(NotificationEventRepository.class))
			.withBean(ObjectMapper.class, ObjectMapper::new)
			.withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
			.withBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	@DisplayName("UNIT-001: production gate가 꺼져 있으면 answer 전용 배선 빈이 하나도 등록되지 않는다")
	void noBeansWhenProductionDisabled() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(ModerationPipelineService.class);
			assertThat(context).doesNotHaveBean(AnswerModerationExecutionWorker.class);
			assertThat(context).doesNotHaveBean(RetryGateConfig.class);
			assertThat(context).doesNotHaveBean(AnswerModerationRetryPolicy.class);
			assertThat(context).doesNotHaveBean(ManualReviewPriorityPolicy.class);
			assertThat(context.getBeansOfType(ExecutorService.class)).isEmpty();
			assertThat(context.getBeansOfType(RestClient.class)).isEmpty();
		});
	}

	@Test
	@DisplayName("UNIT-002: production gate가 켜지고 모든 값이 있으면 execution worker까지 전부 등록된다")
	void allBeansPresentWhenProductionEnabledWithAllValues() {
		runner.withPropertyValues(ALL_REQUIRED_VALUES).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(ModerationPipelineService.class);
			assertThat(context).hasSingleBean(AnswerModerationExecutionWorker.class);
			assertThat(context).hasSingleBean(RetryGateConfig.class);
			assertThat(context).hasSingleBean(AnswerModerationRetryPolicy.class);
			assertThat(context).hasSingleBean(ManualReviewPriorityPolicy.class);
		});
	}

	@Test
	@DisplayName("UNIT-003: production gate가 켜졌는데 answer 전용 API 키가 비어 있으면 기동이 실패한다")
	void contextFailsWhenApiKeyMissing() {
		String[] withoutApiKey = withoutProperty(ALL_REQUIRED_VALUES, "openai-api-key");
		runner.withPropertyValues(withoutApiKey).run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("UNIT-004: production gate가 켜졌는데 retry-policy 필수값이 없으면 기동이 실패한다")
	void contextFailsWhenRetryPolicyValueMissing() {
		String[] withoutMaxRetryLifetime = withoutProperty(ALL_REQUIRED_VALUES, "max-retry-lifetime");
		runner.withPropertyValues(withoutMaxRetryLifetime).run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("UNIT-005: answer 전용 실행 자원은 nickname 경로와 서로 다른 인스턴스다")
	void answerModerationResourcesAreIsolatedFromNickname() {
		runner.withUserConfiguration(AnswerModerationExecutionConfig.class, NicknameModerationGateConfig.class)
				.withPropertyValues(ALL_REQUIRED_VALUES)
				.withPropertyValues("qello.filtering.nickname-moderation.openai-api-key=example-key-for-unit-test")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBeansOfType(ExecutorService.class)).hasSize(2);
					assertThat(context.getBeansOfType(RestClient.class)).hasSize(2);
					ExecutorService answerExecutor = context.getBean("answerModerationExecutor", ExecutorService.class);
					ExecutorService nicknameExecutor = context.getBean("nicknameModerationExecutor",
							ExecutorService.class);
					assertThat(answerExecutor).isNotSameAs(nicknameExecutor);
					RestClient answerRestClient = context.getBean("answerModerationOpenAiRestClient", RestClient.class);
					RestClient nicknameRestClient = context.getBean("nicknameModerationOpenAiRestClient",
							RestClient.class);
					assertThat(answerRestClient).isNotSameAs(nicknameRestClient);
				});
	}

	private static String[] withoutProperty(String[] values, String keySuffix) {
		return java.util.Arrays.stream(values).filter(value -> !value.contains(keySuffix)).toArray(String[]::new);
	}

	// NicknameModerationGateConfig도 PROMOTED release를 요구하므로, 두 설정을
	// 함께 로드하는 UNIT-005에서도 실패하지 않도록 기본으로 채워 둔다.
	private static FilterReleaseRepository promotedReleaseRepository() {
		FilterRelease release = FilterRelease.restore(1L, "norm-v1", "ruleset-v1", "category-map-v1", "model-v1",
				FilterReleaseStatus.PROMOTED, NOW, NOW);
		FilterReleaseRepository repository = mock(FilterReleaseRepository.class);
		when(repository.findCurrentlyPromoted()).thenReturn(java.util.Optional.of(release));
		return repository;
	}
}
