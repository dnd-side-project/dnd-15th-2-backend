package com.dnd.qello.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.dnd.qello.notification.push.PushProvider;
import com.dnd.qello.notification.push.PushProviderResult;
import com.dnd.qello.notification.push.fcm.FcmAccessTokenProvider;
import com.dnd.qello.notification.push.fcm.FcmHttpV1PushProvider;
import com.dnd.qello.notification.push.fcm.GoogleCredentialsFcmAccessTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@Import(PushConfiguration.ProductionPushConfiguration.class)
public class PushConfiguration {

	@Bean
	@Profile({"test", "local", "integration"})
	public PushProvider noOpPushProvider() {
		return new NoOpPushProvider();
	}

	@Configuration(proxyBeanMethods = false)
	@Profile("!test & !local & !integration")
	@EnableConfigurationProperties(PushProperties.class)
	static class ProductionPushConfiguration {

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
		public PushProviderResult send(com.dnd.qello.notification.push.PushSendCommand command) {
			return new PushProviderResult.Accepted("noop-provider-message");
		}

	}

}
