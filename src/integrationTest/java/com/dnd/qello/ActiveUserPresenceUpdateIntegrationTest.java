/**
 * Created at: 2026-08-14T00:51:11+09:00
 * Source scenario: TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-005, INT-006, INT-007, INT-008
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-INT-022
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.security.CoordinateProtector;
import com.dnd.qello.direction.security.CoordinatePurpose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(ActiveUserPresenceUpdateIntegrationTest.SnapshotConfiguration.class)
class ActiveUserPresenceUpdateIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-PRESENCE-121";
	private static final Instant BASE = Instant.parse("2026-08-13T15:00:00Z");
	private static final AtomicReference<SnapshotSignal> SNAPSHOT = new AtomicReference<>();

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ActiveUserPresenceRepository repository;
	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void setUp() {
		SNAPSHOT.set(null);
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING
				""");
		jdbc.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES (?, 'KR', 'Presence Region', 'REGION')
				""", REGION);
	}

	@Test
	@DisplayName("같거나 오래된 관측은 no-op이고 더 최신 관측만 행 전체를 교체한다")
	void onlyAppliesNewerObservation() {
		long userId = createUser("newer-wins");
		ActiveUserPresence first = presence(userId, "37.5000", "127.0000", true, BASE);
		ActiveUserPresence equal = presence(userId, "37.6000", "127.1000", false, BASE);
		ActiveUserPresence older = presence(userId, "37.7000", "127.2000", false, BASE.minusSeconds(1));
		ActiveUserPresence newer = presence(userId, "37.8000", "127.3000", false, BASE.plusSeconds(1));

		assertThat(repository.saveIfNewer(first)).isTrue();
		byte[] firstCiphertext = jdbc.queryForObject(
				"SELECT position_ciphertext FROM active_user_presence WHERE user_id = ?", byte[].class, userId);
		assertThat(repository.saveIfNewer(equal)).isFalse();
		assertThat(repository.saveIfNewer(older)).isFalse();
		assertThat(jdbc.queryForObject("SELECT position_ciphertext FROM active_user_presence WHERE user_id = ?",
				byte[].class, userId)).isEqualTo(firstCiphertext);
		assertThat(repository.findByUserId(userId)).get()
				.satisfies(saved -> {
					assertThat(saved.getLatitude()).isEqualByComparingTo("37.5000");
					assertThat(saved.isReceiveAllowed()).isTrue();
					assertThat(saved.getLocationAt()).isEqualTo(BASE);
				});

		assertThat(repository.saveIfNewer(newer)).isTrue();
		assertThat(repository.findByUserId(userId)).get()
				.satisfies(saved -> {
					assertThat(saved.getLatitude()).isEqualByComparingTo("37.8000");
					assertThat(saved.isReceiveAllowed()).isFalse();
					assertThat(saved.getLocationAt()).isEqualTo(BASE.plusSeconds(1));
				});
	}

	@Test
	@DisplayName("조건부 UPSERT의 DB 제약 실패는 기존 presence를 부분 갱신하지 않는다")
	void databaseFailureDoesNotPartiallyUpdateExistingPresence() {
		long userId = createUser("constraint-failure");
		ActiveUserPresence original = presence(userId, "37.5000", "127.0000", true, BASE);
		repository.saveIfNewer(original);
		ActiveUserPresence invalidRegion = ActiveUserPresence.create(userId,
				new BigDecimal("37.9000"), new BigDecimal("127.4000"), null,
				"TEST-PRESENCE-MISSING-121", BigDecimal.ONE, false, BASE.plusSeconds(1),
				BASE.plusSeconds(86_401));

		assertThatThrownBy(() -> repository.saveIfNewer(invalidRegion))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(repository.findByUserId(userId)).get()
				.satisfies(saved -> {
					assertThat(saved.getLatitude()).isEqualByComparingTo("37.5000");
					assertThat(saved.getLongitude()).isEqualByComparingTo("127.0000");
					assertThat(saved.isReceiveAllowed()).isTrue();
					assertThat(saved.getLocationAt()).isEqualTo(BASE);
				});
	}

	@Test
	@DisplayName("수신 거부와 만료 경계는 두 후보 조회 경로에서 제외된다")
	void candidateQueriesExcludeReceiveDeniedAndExpiredPresence() {
		long senderId = createUser("sender");
		long deniedId = createUser("denied");
		long expiredId = createUser("expired");
		repository.saveIfNewer(presence(senderId, "37.5000", "127.0000", true, BASE));
		repository.saveIfNewer(presence(deniedId, "37.5010", "127.0010", false, BASE));
		repository.saveIfNewer(ActiveUserPresence.create(expiredId, new BigDecimal("37.5010"),
				new BigDecimal("127.0010"), null, REGION, BigDecimal.ONE, true,
				BASE.minusSeconds(60), BASE));

		assertThat(repository.findCandidates(senderId, 37.5, 127.0, 0, 1_000, 0, 360, BASE, REGION))
				.extracting(candidate -> candidate.userId())
				.doesNotContain(deniedId, expiredId);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("두 암호문 갱신의 DB 쓰기 순서를 양방향으로 강제해도 최신 관측과 전체 메타데이터만 남는다")
	void concurrentUpdatesKeepNewestObservation(boolean newerFirst) throws Exception {
		long userId = createUser("concurrent");
		ActiveUserPresence older = presence(userId, "37.5000", "127.0000", true, BASE);
		ActiveUserPresence newer = presence(userId, "37.9000", "127.4000", false, BASE.plusSeconds(1));
		CountDownLatch firstWritten = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch secondStarted = new CountDownLatch(1);

		try (var executor = Executors.newFixedThreadPool(2)) {
			try {
				Future<Boolean> first = executor.submit(() -> new TransactionTemplate(transactionManager)
						.execute(status -> {
							boolean saved = repository.saveIfNewer(newerFirst ? newer : older);
							firstWritten.countDown();
							await(releaseFirst);
							return saved;
						}));
				assertThat(firstWritten.await(5, TimeUnit.SECONDS)).isTrue();
				Future<Boolean> second = executor.submit(() -> {
					secondStarted.countDown();
					return repository.saveIfNewer(newerFirst ? older : newer);
				});
				assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
				releaseFirst.countDown();
				assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
				assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(!newerFirst);
			} finally {
				releaseFirst.countDown();
				executor.shutdownNow();
			}
		}

		assertThat(repository.findByUserId(userId)).get()
				.satisfies(saved -> {
					assertThat(saved.getLocationAt()).isEqualTo(BASE.plusSeconds(1));
					assertThat(saved.getLatitude()).isEqualByComparingTo("37.9000");
					assertThat(saved.getLongitude()).isEqualByComparingTo("127.4000");
					assertThat(saved.isReceiveAllowed()).isFalse();
					assertThat(saved.getExpiresAt()).isEqualTo(BASE.plusSeconds(86_401));
				});
	}

	@Test
	@DisplayName("후보 SQL snapshot을 읽은 뒤 위치·수신허용을 바꿔도 읽던 후보의 암호문과 자격을 혼합하지 않는다")
	void candidateReadKeepsSingleStatementSnapshotDuringUpdate() throws Exception {
		long senderId = createUser("snapshot-sender");
		long candidateId = createUser("snapshot-candidate");
		repository.save(presence(candidateId, "37.5010", "127.0000", true, BASE));
		CountDownLatch rowRead = new CountDownLatch(1);
		CountDownLatch resumeRead = new CountDownLatch(1);
		SNAPSHOT.set(new SnapshotSignal(candidateId, rowRead, resumeRead));
		try (var executor = Executors.newSingleThreadExecutor()) {
			try {
				var reading = executor.submit(() -> repository.findCandidates(senderId, 37.5, 127, 0, 1000,
						0, 360, BASE.plusSeconds(2), REGION));
				assertThat(rowRead.await(5, TimeUnit.SECONDS)).isTrue();
				assertThat(repository.saveIfNewer(presence(candidateId, "38.5000", "128.0000", false,
						BASE.plusSeconds(1)))).isTrue();
				resumeRead.countDown();

				assertThat(reading.get(10, TimeUnit.SECONDS)).singleElement().satisfies(candidate -> {
					assertThat(candidate.userId()).isEqualTo(candidateId);
					assertThat(candidate.distanceMeters()).isBetween(BigDecimal.valueOf(100), BigDecimal.valueOf(120));
				});
			} finally {
				resumeRead.countDown();
				SNAPSHOT.set(null);
				executor.shutdownNow();
			}
		}
		assertThat(repository.findCandidates(senderId, 37.5, 127, 0, 1000, 0, 360,
				BASE.plusSeconds(2), REGION)).isEmpty();
	}

	private static void await(CountDownLatch signal) {
		try {
			assertThat(signal.await(5, TimeUnit.SECONDS)).as("registered database boundary signal").isTrue();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
		}
	}

	private ActiveUserPresence presence(long userId, String latitude, String longitude,
			boolean receiveAllowed, Instant observedAt) {
		return ActiveUserPresence.create(userId, new BigDecimal(latitude), new BigDecimal(longitude), null,
				REGION, BigDecimal.ONE, receiveAllowed, observedAt, observedAt.plusSeconds(86_400));
	}

	private long createUser(String nickname) {
		return jdbc.queryForObject("""
				INSERT INTO user_account (country_code, coarse_region_code, locale, timezone, nickname)
				VALUES ('KR', ?, 'ko-KR', 'Asia/Seoul', ?) RETURNING id
				""", Long.class, REGION, nickname);
	}

	private record SnapshotSignal(long userId, CountDownLatch rowRead, CountDownLatch resumeRead) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SnapshotConfiguration {
		@Bean
		static BeanPostProcessor coordinateReadSignal() {
			return new BeanPostProcessor() {
				@Override
				public Object postProcessAfterInitialization(Object bean, String beanName) {
					if (!(bean instanceof CoordinateProtector)) {
						return bean;
					}
					ProxyFactory proxy = new ProxyFactory(bean);
					proxy.setInterfaces(CoordinateProtector.class);
					proxy.addAdvice((MethodInterceptor) invocation -> {
						SnapshotSignal signal = SNAPSHOT.get();
						if (signal != null && invocation.getMethod().getName().equals("decrypt")
								&& invocation.getArguments()[1] == CoordinatePurpose.ACTIVE_USER_PRESENCE
								&& ((Long) invocation.getArguments()[2]).longValue() == signal.userId()
								&& SNAPSHOT.compareAndSet(signal, null)) {
							signal.rowRead().countDown();
							await(signal.resumeRead());
						}
						return invocation.proceed();
					});
					return proxy.getProxy();
				}
			};
		}
	}
}
