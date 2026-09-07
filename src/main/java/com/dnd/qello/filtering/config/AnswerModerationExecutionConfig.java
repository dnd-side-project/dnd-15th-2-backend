package com.dnd.qello.filtering.config;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

import com.dnd.qello.filtering.domain.ManualReviewPriorityPolicy;
import com.dnd.qello.filtering.domain.RetryGateConfig;
import com.dnd.qello.filtering.moderation.AnswerModerationExecutionWorker;
import com.dnd.qello.filtering.moderation.AnswerModerationRetryPolicy;
import com.dnd.qello.filtering.moderation.FlaggedCategoryPolicyEngine;
import com.dnd.qello.filtering.moderation.LocalRuleEngine;
import com.dnd.qello.filtering.moderation.ModerationPipelineService;
import com.dnd.qello.filtering.moderation.ModerationProviderClient;
import com.dnd.qello.filtering.moderation.NoMatchLocalRuleEngine;
import com.dnd.qello.filtering.moderation.PassthroughTextNormalizer;
import com.dnd.qello.filtering.moderation.PolicyEngine;
import com.dnd.qello.filtering.moderation.TextNormalizer;
import com.dnd.qello.filtering.moderation.openai.OpenAiModerationProviderClient;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterJobStatusHistoryRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRetryGateRepository;
import com.dnd.qello.filtering.repository.ManualReviewCaseRepository;
import com.dnd.qello.filtering.repository.ManualReviewPriorityEvaluationRepository;
import com.dnd.qello.notification.domain.ExponentialJitterBackoffStrategy;
import com.dnd.qello.notification.domain.OutboxBackoffStrategy;
import com.dnd.qello.notification.repository.NotificationEventRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

// 답변 moderation 실행 경로(#107, #108)를 production gate 뒤에서 구성한다(#204).
// NicknameModerationGateConfig(#168)와 대칭이다 — 판정 파이프라인 자체는 같은
// 구현체(PassthroughTextNormalizer 등)를 쓰지만, 답변 부하가 닉네임 판정 지연으로
// 번지지 않도록 RestClient·ExecutorService는 이 설정에서만 별도로 만든다
// (INV-RES-001, INV-RES-002).
//
// AnswerModerationExecutionWorker는 의도적으로 컴포넌트 스캔 대상이 아니다(worker
// 클래스 자체 주석 참고) — 그 하위 구현체가 실제로 갖춰진 지금 이 설정에서만
// 명시적으로 bean으로 등록한다.
//
// retry-gate·retry-policy·manual-review-priority의 운영 수치는 #108/#110에서
// "미결정"으로 명시된 값이다. 기본값을 두지 않고, 값이 없으면 기동을 실패시킨다
// (fail-closed) — 실제 값은 책임자가 확인한 뒤 배포 환경에서만 주입한다.
@Configuration(proxyBeanMethods = false)
public class AnswerModerationExecutionConfig {

	@Bean
	@ConditionalOnProperty(name = "qello.filtering.production.enabled", havingValue = "true")
	public RestClient answerModerationOpenAiRestClient(
			@Value("${qello.filtering.answer-moderation.openai-api-key:}") String apiKey,
			@Value("${qello.filtering.answer-moderation.openai-base-url:https://api.openai.com}") String baseUrl,
			@Value("${qello.filtering.answer-moderation.connect-timeout:PT3S}") Duration connectTimeout,
			@Value("${qello.filtering.answer-moderation.read-timeout:PT5S}") Duration readTimeout) {
		if (apiKey.isBlank()) {
			throw new IllegalStateException(
					"답변 moderation이 활성화됐지만 qello.filtering.answer-moderation.openai-api-key가 "
							+ "비어 있습니다. 환경 변수로 값을 주입해야 합니다.");
		}
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(connectTimeout);
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.build();
	}

	// 답변 판정 경로 전용 pool이다(INV-RES-001, INV-RES-002). 컨텍스트 종료 시
	// destroyMethod로 정리한다.
	@Bean(destroyMethod = "shutdown")
	@ConditionalOnProperty(name = "qello.filtering.production.enabled", havingValue = "true")
	public ExecutorService answerModerationExecutor(
			@Value("${qello.filtering.answer-moderation.executor-pool-size:4}") int executorPoolSize) {
		return Executors.newFixedThreadPool(executorPoolSize);
	}

	@Bean
	@ConditionalOnProperty(name = "qello.filtering.production.enabled", havingValue = "true")
	public ModerationPipelineService answerModerationPipelineService(
			RestClient answerModerationOpenAiRestClient,
			FilterDecisionRepository filterDecisionRepository,
			Clock clock) {
		TextNormalizer textNormalizer = new PassthroughTextNormalizer();
		LocalRuleEngine localRuleEngine = new NoMatchLocalRuleEngine();
		ModerationProviderClient providerClient = new OpenAiModerationProviderClient(answerModerationOpenAiRestClient);
		PolicyEngine policyEngine = new FlaggedCategoryPolicyEngine();
		return new ModerationPipelineService(
				textNormalizer, localRuleEngine, providerClient, policyEngine, filterDecisionRepository, clock);
	}

	@Bean
	@ConditionalOnProperty(name = "qello.filtering.production.enabled", havingValue = "true")
	public RetryGateConfig answerModerationRetryGateConfig(
			@Value("${qello.filtering.answer-moderation.retry-gate.degrade-threshold}") int degradeThreshold,
			@Value("${qello.filtering.answer-moderation.retry-gate.min-limit}") int minLimit,
			@Value("${qello.filtering.answer-moderation.retry-gate.ramp-step}") int rampStep,
			@Value("${qello.filtering.answer-moderation.retry-gate.recovery-streak}") int recoveryStreak,
			@Value("${qello.filtering.answer-moderation.retry-gate.healthy-limit}") int healthyLimit) {
		return new RetryGateConfig(degradeThreshold, minLimit, rampStep, recoveryStreak, healthyLimit);
	}

	@Bean
	@ConditionalOnProperty(name = "qello.filtering.production.enabled", havingValue = "true")
	public AnswerModerationRetryPolicy answerModerationRetryPolicy(
			@Value("${qello.filtering.answer-moderation.retry-policy.fast-backoff-base}") Duration fastBackoffBase,
			@Value("${qello.filtering.answer-moderation.retry-policy.fast-backoff-max}") Duration fastBackoffMax,
			@Value("${qello.filtering.answer-moderation.retry-policy.slow-backoff-base}") Duration slowBackoffBase,
			@Value("${qello.filtering.answer-moderation.retry-policy.slow-backoff-max}") Duration slowBackoffMax,
			@Value("${qello.filtering.answer-moderation.retry-policy.max-attempts}") int maxAttempts,
			@Value("${qello.filtering.answer-moderation.retry-policy.max-retry-lifetime}") Duration maxRetryLifetime) {
		OutboxBackoffStrategy fastBackoff = ExponentialJitterBackoffStrategy.withRandomJitter(fastBackoffBase,
				fastBackoffMax);
		OutboxBackoffStrategy slowBackoff = ExponentialJitterBackoffStrategy.withRandomJitter(slowBackoffBase,
				slowBackoffMax);
		return new AnswerModerationRetryPolicy(fastBackoff, slowBackoff, maxAttempts, maxRetryLifetime);
	}

	@Bean
	@ConditionalOnProperty(name = "qello.filtering.production.enabled", havingValue = "true")
	public ManualReviewPriorityPolicy answerModerationManualReviewPriorityPolicy(
			@Value("${qello.filtering.answer-moderation.manual-review-priority.high-band-report-signal-threshold}") int highBandReportSignalThreshold,
			@Value("${qello.filtering.answer-moderation.manual-review-priority.aging-threshold}") Duration agingThreshold,
			@Value("${qello.filtering.answer-moderation.manual-review-priority.policy-version}") String policyVersion) {
		return new ManualReviewPriorityPolicy(highBandReportSignalThreshold, agingThreshold, policyVersion);
	}

	@Bean
	@ConditionalOnProperty(name = "qello.filtering.production.enabled", havingValue = "true")
	public AnswerModerationExecutionWorker answerModerationExecutionWorker(
			ModerationPipelineService answerModerationPipelineService,
			FilterJobRepository filterJobRepository,
			FilterReleaseRepository filterReleaseRepository,
			FilterJobStatusHistoryRepository filterJobStatusHistoryRepository,
			OutboxEventRepository outboxEventRepository,
			AnswerModerationRetryPolicy answerModerationRetryPolicy,
			FilterReleaseRetryGateRepository filterReleaseRetryGateRepository,
			RetryGateConfig answerModerationRetryGateConfig,
			ManualReviewCaseRepository manualReviewCaseRepository,
			ManualReviewPriorityEvaluationRepository manualReviewPriorityEvaluationRepository,
			ManualReviewPriorityPolicy answerModerationManualReviewPriorityPolicy,
			NotificationEventRepository notificationEventRepository,
			@Value("${qello.filtering.answer-moderation.gate-defer-delay:PT2S}") Duration gateDeferDelay,
			ObjectMapper objectMapper,
			ExecutorService answerModerationExecutor,
			@Value("${qello.filtering.answer-moderation.pipeline-timeout:PT5S}") Duration pipelineTimeout,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		return new AnswerModerationExecutionWorker(
				answerModerationPipelineService, filterJobRepository, filterReleaseRepository,
				filterJobStatusHistoryRepository, outboxEventRepository, answerModerationRetryPolicy,
				filterReleaseRetryGateRepository, answerModerationRetryGateConfig, manualReviewCaseRepository,
				manualReviewPriorityEvaluationRepository, answerModerationManualReviewPriorityPolicy,
				notificationEventRepository, gateDeferDelay, objectMapper, answerModerationExecutor, pipelineTimeout,
				transactionManager, clock);
	}
}
