/**
 * Created at: 2026-08-14T00:51:11+09:00
 * Source scenario: TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-005, INT-006, INT-007, INT-008
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;

@SpringBootTest
@ActiveProfiles("test")
class ActiveUserPresenceUpdateIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-PRESENCE-121";
	private static final Instant BASE = Instant.parse("2026-08-13T15:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ActiveUserPresenceRepository repository;

	@BeforeEach
	void setUp() {
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
		assertThat(repository.saveIfNewer(equal)).isFalse();
		assertThat(repository.saveIfNewer(older)).isFalse();
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

	@Test
	@DisplayName("동시 갱신의 완료 순서와 무관하게 가장 늦은 관측 시각의 행만 남는다")
	void concurrentUpdatesKeepNewestObservation() throws Exception {
		long userId = createUser("concurrent");
		ActiveUserPresence older = presence(userId, "37.5000", "127.0000", true, BASE);
		ActiveUserPresence newer = presence(userId, "37.9000", "127.4000", false, BASE.plusSeconds(1));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<Boolean> olderResult = executor.submit(() -> saveAfterLatch(older, ready, start));
			Future<Boolean> newerResult = executor.submit(() -> saveAfterLatch(newer, ready, start));
			ready.await();
			start.countDown();
			olderResult.get();
			assertThat(newerResult.get()).isTrue();
		}

		assertThat(repository.findByUserId(userId)).get()
			.satisfies(saved -> {
				assertThat(saved.getLocationAt()).isEqualTo(BASE.plusSeconds(1));
				assertThat(saved.getLatitude()).isEqualByComparingTo("37.9000");
				assertThat(saved.isReceiveAllowed()).isFalse();
			});
	}

	private boolean saveAfterLatch(ActiveUserPresence presence, CountDownLatch ready, CountDownLatch start)
		throws InterruptedException {
		ready.countDown();
		start.await();
		return repository.saveIfNewer(presence);
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
}
