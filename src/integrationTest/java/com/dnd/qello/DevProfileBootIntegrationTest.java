/**
 * Created at: 2026-09-17T00:58:11+09:00
 * Source scenario: TEST-PLAN-GH-230-CORS-ACTUATOR-DEV-PROFILE-INT-005..008
 * Extended at: 2026-11-21T08:52:10Z
 * Source scenario: TEST-PLAN-GH-365-PUSH-FID-PROVIDER-INT-006
 * Extended at: 2026-11-21T12:05:43.793Z
 * Source scenario: TEST-PLAN-GH-366-DEV-WORKER-FCM-INT-001 through INT-003
 * Extended at: 2026-11-23T16:43:12+09:00
 * Source scenario: TEST-PLAN-GH-379-LOCATION-GATE-INT-004
 */
package com.dnd.qello;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import com.dnd.qello.direction.gate.LocationCollectionGate;
import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.notification.config.PushProperties;
import com.dnd.qello.notification.fanout.NotificationFanOutWorker;
import com.dnd.qello.notification.fanout.RecipientNotificationFanOutWorker;
import com.dnd.qello.notification.fanout.ReportResolutionFanOutWorker;
import com.dnd.qello.notification.push.PushProvider;
import com.dnd.qello.notification.push.fcm.FcmAccessTokenProvider;
import com.dnd.qello.notification.push.fcm.FcmHttpV1PushProvider;
import com.dnd.qello.notification.push.fcm.GoogleCredentialsFcmAccessTokenProvider;
import com.dnd.qello.notification.push.security.AesGcmPushTokenProtector;
import com.dnd.qello.notification.push.security.PushToken;
import com.dnd.qello.notification.push.security.PushTokenProtector;
import com.dnd.qello.notification.service.PushDeliveryDispatchWorker;
import com.dnd.qello.scheduling.WorkerInstanceIdentity;
import com.dnd.qello.scheduling.adapter.AccountWithdrawalSweepScheduledAdapter;
import com.dnd.qello.scheduling.adapter.AnswerModerationDeadlineScheduledAdapter;
import com.dnd.qello.scheduling.adapter.AnswerModerationExecutionScheduledAdapter;
import com.dnd.qello.scheduling.adapter.AnswerModerationVerdictScheduledAdapter;
import com.dnd.qello.scheduling.adapter.DirectionMatchingScheduledAdapter;
import com.dnd.qello.scheduling.adapter.NotificationFanOutScheduledAdapter;
import com.dnd.qello.scheduling.adapter.PushDeliveryDispatchScheduledAdapter;
import com.dnd.qello.scheduling.adapter.RecipientExpirationSweepScheduledAdapter;
import com.dnd.qello.scheduling.adapter.RecipientNotificationFanOutScheduledAdapter;
import com.dnd.qello.scheduling.adapter.ReportResolutionFanOutScheduledAdapter;
import com.dnd.qello.scheduling.adapter.SkipConfirmationSweepScheduledAdapter;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties;
import com.dnd.qello.scheduling.observability.WorkerMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@ContextConfiguration(initializers = DevProfileBootIntegrationTest.IsolatedEnvironment.class)
@Import(DevProfileBootIntegrationTest.BoundaryConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DevProfileBootIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String FID = "test-only-gh365-dev-fid-sentinel";
	private static final TestBoundaries BOUNDARIES = new TestBoundaries();

	@DynamicPropertySource
	static void devProfileRequiredProperties(DynamicPropertyRegistry registry) {
		registry.add("qello.media.bucket", () -> "qello-dev-boot-test-placeholder");
		registry.add("qello.auth.access-token.secret", () -> "dev-profile-boot-test-signing-key-32-bytes-min");
		registry.add("qello.notification.push.token-protection.current-key-id", () -> "test-only-gh365-current");
		registry.add("qello.notification.push.token-protection.current-encryption-key-base64",
				() -> fakeKey((byte) 0x11));
		registry.add("qello.notification.push.token-protection.fingerprint-key-base64", () -> fakeKey((byte) 0x33));
		registry.add("qello.notification.push.fcm.project-id", () -> "test-only-gh366-project");
		registry.add("qello.notification.push.fcm.credential-json", BOUNDARIES::credentialJson);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ApplicationContext context;

	@Autowired
	private PushTokenProtector protector;

	@Autowired
	private PushProvider provider;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("INT-001: dev 설정은 다섯 worker와 실제 FCM 및 AES를 등록하고 제외 worker를 켜지 않는다")
	void devProfileContextLoads() {
		assertThat(context.getBeansOfType(PushTokenProtector.class).values()).containsExactly(protector);
		assertThat(protector).isInstanceOf(AesGcmPushTokenProtector.class);
		assertThat(context.getBeansOfType(PushProvider.class).values()).containsExactly(provider);
		assertThat(provider).isInstanceOf(FcmHttpV1PushProvider.class);
		assertThat(context.getBeansOfType(PushProperties.class)).hasSize(1);
		assertThat(context.getBeansOfType(FcmAccessTokenProvider.class)).hasSize(1);
		assertThat(context.getBean(FcmAccessTokenProvider.class))
				.isInstanceOf(GoogleCredentialsFcmAccessTokenProvider.class);
		assertThat(context.containsBean("noOpPushProvider")).isFalse();
		assertThat(context.containsBean("fcmRestClient")).isTrue();
		WorkerSchedulingProperties properties = context.getBean(WorkerSchedulingProperties.class);
		assertThat(properties.enabled()).isTrue();
		assertThat(properties.poolSize()).isEqualTo(5);
		ThreadPoolTaskScheduler scheduler = context.getBean(ThreadPoolTaskScheduler.class);
		assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(properties.poolSize());
		assertThat(context.getBeansOfType(WorkerInstanceIdentity.class)).hasSize(1);
		assertThat(context.getBeansOfType(WorkerMetrics.class)).hasSize(1);
		assertWorkerBeans();
		assertScheduledTasks();
	}

	private void assertWorkerBeans() {
		for (Class<?> type : List.of(DirectionMatchingWorker.class, RecipientNotificationFanOutWorker.class,
				NotificationFanOutWorker.class, ReportResolutionFanOutWorker.class, PushDeliveryDispatchWorker.class,
				DirectionMatchingScheduledAdapter.class, RecipientNotificationFanOutScheduledAdapter.class,
				NotificationFanOutScheduledAdapter.class, ReportResolutionFanOutScheduledAdapter.class,
				PushDeliveryDispatchScheduledAdapter.class)) {
			assertThat(context.getBeansOfType(type)).as(type.getSimpleName()).hasSize(1);
		}
		for (Class<?> type : List.of(RecipientExpirationSweepScheduledAdapter.class,
				SkipConfirmationSweepScheduledAdapter.class, AccountWithdrawalSweepScheduledAdapter.class,
				AnswerModerationExecutionScheduledAdapter.class, AnswerModerationDeadlineScheduledAdapter.class,
				AnswerModerationVerdictScheduledAdapter.class)) {
			assertThat(context.getBeansOfType(type)).as(type.getSimpleName()).isEmpty();
		}
	}

	private void assertScheduledTasks() {
		assertThat(BOUNDARIES.tasks).hasSize(5);
		assertThat(BOUNDARIES.tasks).extracting(task -> task.runnable().getTarget())
				.containsExactlyInAnyOrder(context.getBean(DirectionMatchingScheduledAdapter.class),
						context.getBean(RecipientNotificationFanOutScheduledAdapter.class),
						context.getBean(NotificationFanOutScheduledAdapter.class),
						context.getBean(ReportResolutionFanOutScheduledAdapter.class),
						context.getBean(PushDeliveryDispatchScheduledAdapter.class));
		assertThat(BOUNDARIES.tasks).allSatisfy(task -> {
			assertThat(task.delay()).isEqualTo(Duration.ofSeconds(5));
			assertThat(task.runnable().getMethod().getName()).isEqualTo("runOnce");
		});
	}

	@AfterEach
	void verifiesNoExternalRequests() {
		BOUNDARIES.assertNoExternalRequests();
	}

	@AfterAll
	static void closesTokenEndpoint() {
		try {
			BOUNDARIES.assertNoExternalRequests();
		} finally {
			BOUNDARIES.tokenEndpoint.stop(0);
		}
	}

	@Test
	@DisplayName("INT-002: dev의 실제 자동 AES 보호기로 FID를 등록하고 같은 FID를 해지한다")
	void registersAndRevokesFidWithAutoConfiguredProtector() throws Exception {
		long userId = jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, coarse_region_code, status, locale, timezone, nickname)
				VALUES ('USER', 'KR', 'KR', 'ACTIVE', 'ko-KR', 'Asia/Seoul', 'gh365-dev-boot')
				RETURNING id
				""", Long.class);
		String body = "{\"platform\":\"ANDROID\",\"fid\":\"" + FID + "\"}";
		try {
			mockMvc.perform(post("/api/v1/notifications/devices")
					.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
							.jwt(token -> token.subject(Long.toString(userId))))
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isNoContent()).andExpect(content().string(""));

			byte[] ciphertext = jdbc.queryForObject(
					"SELECT token_ciphertext FROM push_device WHERE user_id = ?", byte[].class, userId);
			assertThat(ciphertext).isNotEqualTo(FID.getBytes(StandardCharsets.UTF_8));
			assertThat(protector.decrypt(ciphertext)).isEqualTo(PushToken.of(FID));
			assertThat(jdbc.queryForObject("SELECT token_fingerprint FROM push_device WHERE user_id = ?",
					String.class, userId)).isEqualTo(protector.fingerprint(PushToken.of(FID)));
			assertThat(jdbc.queryForObject("SELECT device_status FROM push_device WHERE user_id = ?",
					String.class, userId)).isEqualTo("ACTIVE");

			mockMvc.perform(post("/api/v1/notifications/devices/revoke")
					.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
							.jwt(token -> token.subject(Long.toString(userId))))
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isNoContent()).andExpect(content().string(""));

			assertThat(jdbc.queryForObject("SELECT device_status FROM push_device WHERE user_id = ?",
					String.class, userId)).isEqualTo("REVOKED");
		} finally {
			jdbc.update("DELETE FROM push_device WHERE user_id = ?", userId);
			jdbc.update("DELETE FROM user_account WHERE id = ?", userId);
		}
	}

	@Test
	@DisplayName("GH-379 INT-004: dev는 신고 수리 참조 없이 위치 수집 게이트를 켜고 위치 갱신을 저장한다")
	void devProfileOpensLocationCollectionGate() throws Exception {
		assertThat(context.getBean(LocationCollectionGate.class).isEnabled()).isTrue();
		long userId = jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, coarse_region_code, status, locale, timezone, nickname)
				VALUES ('USER', 'KR', 'KR', 'ACTIVE', 'ko-KR', 'Asia/Seoul', 'gh379-dev-gate')
				RETURNING id
				""", Long.class);
		String body = """
				{"latitude":37.5,"longitude":127.0,"accuracyMeters":10,"receiveAllowed":false,"observedAt":"%s"}
				""".formatted(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
		try {
			mockMvc.perform(put("/api/v1/direction/presence")
					.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
							.jwt(token -> token.subject(Long.toString(userId))))
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.applied").value(true));

			assertThat(jdbc.queryForObject("SELECT count(*) FROM active_user_presence WHERE user_id = ?",
					Integer.class, userId)).isEqualTo(1);
		} finally {
			jdbc.update("DELETE FROM active_user_presence WHERE user_id = ?", userId);
			jdbc.update("DELETE FROM user_account WHERE id = ?", userId);
		}
	}

	@Test
	@DisplayName("INT-003: GET /actuator/health는 인증 없이 200과 UP 상태를 반환한다")
	void actuatorHealthIsOpenAndUp() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	@DisplayName("INT-003: health 외 actuator endpoint는 dev 프로파일에서도 노출되지 않는다")
	void onlyHealthIsExposed() throws Exception {
		for (String path : new String[]{"/actuator", "/actuator/metrics", "/actuator/env"}) {
			mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
		}
	}

	private static String fakeKey(byte value) {
		byte[] key = new byte[32];
		Arrays.fill(key, value);
		return Base64.getEncoder().encodeToString(key);
	}

	static class IsolatedEnvironment implements ApplicationContextInitializer<ConfigurableApplicationContext> {
		@Override
		public void initialize(ConfigurableApplicationContext applicationContext) {
			var sources = applicationContext.getEnvironment().getPropertySources();
			sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
			sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class BoundaryConfiguration {
		@Bean
		static TestBoundaries testBoundaries() {
			return BOUNDARIES;
		}
	}

	record RegisteredTask(ScheduledMethodRunnable runnable, Duration delay) {
	}

	static class TestBoundaries implements BeanPostProcessor {
		private final List<RegisteredTask> tasks = new CopyOnWriteArrayList<>();
		private final AtomicInteger tokenRequests = new AtomicInteger();
		private final AtomicInteger oauthCalls = new AtomicInteger();
		private final AtomicInteger fcmCalls = new AtomicInteger();
		private final HttpServer tokenEndpoint;

		TestBoundaries() {
			try {
				tokenEndpoint = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			} catch (IOException exception) {
				throw new IllegalStateException("test-only token endpoint 초기화 실패", exception);
			}
			tokenEndpoint.createContext("/", exchange -> {
				tokenRequests.incrementAndGet();
				exchange.sendResponseHeaders(503, -1);
				exchange.close();
			});
			tokenEndpoint.start();
		}

		String credentialJson() {
			try {
				KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
				generator.initialize(2048);
				String key = Base64.getMimeEncoder(64, new byte[]{'\n'})
						.encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
				return new ObjectMapper().writeValueAsString(Map.of(
						"type", "service_account", "project_id", "test-only-gh366-project",
						"private_key_id", "test-only-key", "client_id", "test-only-client",
						"client_email", "test-only@example.invalid",
						"private_key", "-----BEGIN %1$s-----\n%2$s\n-----END %1$s-----\n".formatted("PRIVATE KEY", key),
						"token_uri", "http://localhost:" + tokenEndpoint.getAddress().getPort() + "/token"));
			} catch (GeneralSecurityException | IOException exception) {
				throw new IllegalStateException("test-only credential 생성 실패", exception);
			}
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (bean instanceof ThreadPoolTaskScheduler scheduler && "workerTaskScheduler".equals(beanName)) {
				return captureScheduler(scheduler);
			}
			if (bean instanceof FcmAccessTokenProvider accessTokenProvider) {
				FcmAccessTokenProvider guarded = spy(accessTokenProvider);
				doAnswer(invocation -> {
					oauthCalls.incrementAndGet();
					throw new AssertionError("dev boot 중 OAuth 호출 금지");
				}).when(guarded).accessToken();
				return guarded;
			}
			if (bean instanceof RestClient client && "fcmRestClient".equals(beanName)) {
				RestClient guarded = spy(client);
				doAnswer(invocation -> {
					fcmCalls.incrementAndGet();
					throw new AssertionError("dev boot 중 FCM 호출 금지");
				}).when(guarded).post();
				return guarded;
			}
			return bean;
		}

		private ThreadPoolTaskScheduler captureScheduler(ThreadPoolTaskScheduler scheduler) {
			ThreadPoolTaskScheduler captured = spy(scheduler);
			doAnswer(invocation -> registerTask(invocation.getArgument(0), invocation.getArgument(1)))
					.when(captured).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
			doAnswer(invocation -> registerTask(invocation.getArgument(0), invocation.getArgument(2)))
					.when(captured)
					.scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), any(Duration.class));
			return captured;
		}

		private ScheduledFuture<?> registerTask(Runnable runnable, Duration delay) {
			// Spring 6.2의 Task는 실행 결과 추적용 wrapper를 scheduler에 전달한다.
			Object scheduledMethod = ReflectionTestUtils.getField(runnable, "runnable");
			assertThat(scheduledMethod).isInstanceOf(ScheduledMethodRunnable.class);
			tasks.add(new RegisteredTask((ScheduledMethodRunnable) scheduledMethod, delay));
			return mock(ScheduledFuture.class);
		}

		void assertNoExternalRequests() {
			assertThat(tokenRequests).as("credential 생성 중 token endpoint 요청 수").hasValue(0);
			assertThat(oauthCalls).as("OAuth 진입 차단 횟수").hasValue(0);
			assertThat(fcmCalls).as("FCM 진입 차단 횟수").hasValue(0);
		}
	}

}
