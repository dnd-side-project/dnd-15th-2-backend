package com.dnd.qello.filtering.config;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.moderation.FlaggedCategoryPolicyEngine;
import com.dnd.qello.filtering.moderation.GatedNicknameModerationChecker;
import com.dnd.qello.filtering.moderation.LocalRuleEngine;
import com.dnd.qello.filtering.moderation.ModerationPipelineService;
import com.dnd.qello.filtering.moderation.ModerationProviderClient;
import com.dnd.qello.filtering.moderation.NicknameModerationChecker;
import com.dnd.qello.filtering.moderation.NicknameSyncModerationGate;
import com.dnd.qello.filtering.moderation.NoMatchLocalRuleEngine;
import com.dnd.qello.filtering.moderation.NoOpNicknameModerationChecker;
import com.dnd.qello.filtering.moderation.PassthroughTextNormalizer;
import com.dnd.qello.filtering.moderation.PolicyEngine;
import com.dnd.qello.filtering.moderation.SecondaryModerationClient;
import com.dnd.qello.filtering.moderation.TextNormalizer;
import com.dnd.qello.filtering.moderation.UnavailableSecondaryModerationClient;
import com.dnd.qello.filtering.moderation.openai.OpenAiModerationProviderClient;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;

/**
 * 닉네임 동기 moderation 게이트(#106)를 실제로 구성한다(#168). 답변 경로와 실행
 * 자원을 공유하지 않기 위해(INV-RES-001, INV-RES-002) 전용 {@link RestClient}와
 * {@link ExecutorService}를 이 설정에서만 만든다.
 *
 * <p>{@code qello.filtering.production.enabled}(#113 production gate)가
 * {@code true}일 때만 {@link NicknameSyncModerationGate}를 등록한다. 꺼져 있는
 * 로컬·테스트 환경에서는 {@link NoOpNicknameModerationChecker}가 대신 등록되어
 * moderation 호출 없이 통과시킨다(ASSUMED — 테스트 계획 §4).
 */
@Configuration(proxyBeanMethods = false)
public class NicknameModerationGateConfig {

	@Bean
	@ConditionalOnProperty(name = "qello.filtering.production.enabled", havingValue = "true")
	public RestClient nicknameModerationOpenAiRestClient(
		@Value("${qello.filtering.nickname-moderation.openai-api-key:}") String apiKey,
		@Value("${qello.filtering.nickname-moderation.openai-base-url:https://api.openai.com}") String baseUrl,
		@Value("${qello.filtering.nickname-moderation.connect-timeout:PT3S}") Duration connectTimeout,
		@Value("${qello.filtering.nickname-moderation.read-timeout:PT5S}") Duration readTimeout
	) {
		if (apiKey.isBlank()) {
			throw new IllegalStateException(
				"닉네임 moderation이 활성화됐지만 qello.filtering.nickname-moderation.openai-api-key가 "
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

	// 답변 판정 경로와 실행 자원을 공유하지 않기 위한 전용 pool이다(INV-RES-001,
	// INV-RES-002). 컨텍스트 종료 시 destroyMethod로 정리한다.
	@Bean(destroyMethod = "shutdown")
	@ConditionalOnProperty(name = "qello.filtering.production.enabled", havingValue = "true")
	public ExecutorService nicknameModerationExecutor(
		@Value("${qello.filtering.nickname-moderation.executor-pool-size:4}") int executorPoolSize
	) {
		return Executors.newFixedThreadPool(executorPoolSize);
	}

	@Bean
	@ConditionalOnProperty(name = "qello.filtering.production.enabled", havingValue = "true")
	public NicknameSyncModerationGate nicknameSyncModerationGate(
		RestClient nicknameModerationOpenAiRestClient,
		ExecutorService nicknameModerationExecutor,
		FilterDecisionRepository filterDecisionRepository,
		FilterReleaseRepository filterReleaseRepository,
		@Value("${qello.filtering.nickname-moderation.primary-timeout:PT3S}") Duration primaryTimeout,
		@Value("${qello.filtering.nickname-moderation.secondary-timeout:PT1S}") Duration secondaryTimeout,
		Clock clock
	) {
		FilterRelease release = filterReleaseRepository.findCurrentlyPromoted()
			.orElseThrow(() -> new IllegalStateException(
				"닉네임 moderation이 활성화됐지만 PROMOTED 상태인 FilterRelease가 없습니다."));

		TextNormalizer textNormalizer = new PassthroughTextNormalizer();
		LocalRuleEngine localRuleEngine = new NoMatchLocalRuleEngine();
		ModerationProviderClient providerClient = new OpenAiModerationProviderClient(nicknameModerationOpenAiRestClient);
		PolicyEngine policyEngine = new FlaggedCategoryPolicyEngine();
		ModerationPipelineService pipeline = new ModerationPipelineService(
			textNormalizer, localRuleEngine, providerClient, policyEngine, filterDecisionRepository, clock);

		SecondaryModerationClient secondaryClient = new UnavailableSecondaryModerationClient();

		return new NicknameSyncModerationGate(
			pipeline, secondaryClient, nicknameModerationExecutor, primaryTimeout, secondaryTimeout, release);
	}

	@Bean
	@ConditionalOnProperty(name = "qello.filtering.production.enabled", havingValue = "true")
	public NicknameModerationChecker gatedNicknameModerationChecker(NicknameSyncModerationGate nicknameSyncModerationGate) {
		return new GatedNicknameModerationChecker(nicknameSyncModerationGate);
	}

	@Bean
	@ConditionalOnMissingBean(NicknameModerationChecker.class)
	public NicknameModerationChecker noOpNicknameModerationChecker() {
		return new NoOpNicknameModerationChecker();
	}
}
