package com.dnd.qello.notification.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import com.dnd.qello.notification.push.PushDeliveryRetryPolicy;
import com.dnd.qello.notification.push.PushDispatchEligibility;
import com.dnd.qello.notification.push.PushPayloadFactory;
import com.dnd.qello.notification.push.PushProvider;
import com.dnd.qello.notification.push.PushProviderResult;
import com.dnd.qello.notification.push.PushSendCommand;
import com.dnd.qello.notification.push.fcm.FcmAccessTokenProvider;
import com.dnd.qello.notification.push.fcm.FcmHttpV1PushProvider;
import com.dnd.qello.notification.push.fcm.GoogleCredentialsFcmAccessTokenProvider;
import com.dnd.qello.notification.push.policy.PushBudgetPolicy;
import com.dnd.qello.notification.push.policy.PushGroupingPolicy;
import com.dnd.qello.notification.push.policy.PushSuppressionPolicy;
import com.dnd.qello.notification.push.security.AesGcmPushTokenProtector;
import com.dnd.qello.notification.push.security.PushTokenProtector;
import com.dnd.qello.notification.repository.PushDispatchGroupRepository;
import com.dnd.qello.notification.service.PushDeliveryDispatchWorker;
import com.dnd.qello.notification.service.PushDispatchGroupClaimService;
import com.dnd.qello.notification.service.PushDispatchGroupPlanner;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@Import({PushConfiguration.ProductionPushConfiguration.class, PushConfiguration.FcmPushConfiguration.class})
@EnableConfigurationProperties(PushPolicyProperties.class)
public class PushConfiguration {

	public PushConfiguration(Environment environment) {
		String provider = environment.getProperty("qello.notification.push.provider");
		if (provider == null) {
			if (environment.acceptsProfiles(Profiles.of("test", "local", "integration", "dev"))) {
				return;
			}
			throw new IllegalArgumentException("qello.notification.push.provider must be configured");
		}
		if (!"noop".equals(provider) && !"fcm".equals(provider)) {
			throw new IllegalArgumentException("qello.notification.push.provider must be noop or fcm");
		}
	}

	@Bean
	@ConditionalOnProperty(prefix = "qello.notification.push", name = "provider", havingValue = "noop", matchIfMissing = true)
	public PushProvider noOpPushProvider() {
		return new NoOpPushProvider();
	}

	@Bean
	PushGroupingPolicy pushGroupingPolicy(PushPolicyProperties properties) {
		return new PushGroupingPolicy(properties);
	}

	@Bean
	PushSuppressionPolicy pushSuppressionPolicy(PushPolicyProperties properties) {
		return new PushSuppressionPolicy(properties, Clock.systemUTC());
	}

	@Bean
	PushBudgetPolicy pushBudgetPolicy(PushPolicyProperties properties) {
		return new PushBudgetPolicy(properties);
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = "qello.worker.scheduling", name = {"enabled",
			"push-delivery-dispatch.enabled"}, havingValue = "true")
	@EnableConfigurationProperties(WorkerSchedulingProperties.class)
	static class PushDispatchWorkerConfiguration {

		@Bean
		PushDispatchGroupPlanner pushDispatchGroupPlanner(
				PushDispatchGroupRepository repository, PushGroupingPolicy policy) {
			return new PushDispatchGroupPlanner(repository, policy);
		}

		@Bean
		PushPayloadFactory pushPayloadFactory() {
			return new PushPayloadFactory();
		}

		@Bean
		PushDeliveryRetryPolicy pushDeliveryRetryPolicy(WorkerSchedulingProperties properties) {
			return properties.pushDeliveryDispatch().retry().toPolicy();
		}

		@Bean
		PushDeliveryDispatchWorker pushDeliveryDispatchWorker(
				PushDispatchGroupPlanner groupPlanner,
				PushDispatchGroupClaimService groupClaims,
				PlatformTransactionManager transactionManager,
				PushDispatchEligibility eligibility,
				PushSuppressionPolicy suppressionPolicy,
				PushBudgetPolicy budgetPolicy,
				PushPolicyProperties policyProperties,
				PushPayloadFactory payloadFactory,
				PushDeliveryRetryPolicy retryPolicy,
				PushTokenProtector tokenProtector,
				PushProvider provider,
				Clock clock) {
			return new PushDeliveryDispatchWorker(
					groupPlanner, groupClaims, new TransactionTemplate(transactionManager),
					eligibility, suppressionPolicy, budgetPolicy, policyProperties,
					payloadFactory, retryPolicy, tokenProtector, provider, clock);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Profile("!test & !local & !integration")
	@EnableConfigurationProperties(PushTokenProperties.class)
	static class ProductionPushConfiguration {

		@Bean
		PushTokenProtector pushTokenProtector(PushTokenProperties properties) {
			return new AesGcmPushTokenProtector(properties.keyRing());
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = "qello.notification.push", name = "provider", havingValue = "fcm")
	@EnableConfigurationProperties(PushProperties.class)
	static class FcmPushConfiguration {

		@Bean
		RestClient fcmRestClient(PushProperties properties) {
			SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
			requestFactory.setConnectTimeout(properties.connectTimeout());
			requestFactory.setReadTimeout(properties.readTimeout());
			return RestClient.builder()
					.baseUrl("https://fcm.googleapis.com")
					.requestFactory(requestFactory)
					.build();
		}

		@Bean
		FcmAccessTokenProvider fcmAccessTokenProvider(PushProperties properties) {
			return new GoogleCredentialsFcmAccessTokenProvider(properties.credentialJson());
		}

		@Bean
		PushProvider pushProvider(RestClient fcmRestClient, FcmAccessTokenProvider fcmAccessTokenProvider,
				PushProperties properties) {
			return new FcmHttpV1PushProvider(
					fcmRestClient,
					fcmAccessTokenProvider,
					new ObjectMapper(),
					properties.projectId());
		}

	}

	private static final class NoOpPushProvider implements PushProvider {

		@Override
		public PushProviderResult send(PushSendCommand command) {
			return new PushProviderResult.Accepted("noop-provider-message");
		}

	}

}
