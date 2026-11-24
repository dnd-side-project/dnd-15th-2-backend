/**
 * Created at: 2026-11-23T13:52:37Z
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-INT-007
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-INT-008
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-INT-009
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-INT-010
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-INT-014
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.jpa.JpaAccountRepository;
import com.dnd.qello.account.service.AccountWithdrawalCompletionService;
import com.dnd.qello.account.service.AccountWithdrawalService;
import com.dnd.qello.direction.consent.LocationConsentService;
import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.service.DirectionPresenceService;
import com.dnd.qello.filtering.moderation.NicknameModerationChecker;
import com.dnd.qello.notification.push.PushProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("test")
@Import(LocationConsentConcurrencyIntegrationTest.FixedTime.class)
class LocationConsentConcurrencyIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant AT = Instant.parse("2026-11-23T12:00:00Z");
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private LocationConsentService consent;
	@Autowired
	private DirectionPresenceService presence;
	@Autowired
	private AccountWithdrawalService withdrawal;
	@Autowired
	private AccountWithdrawalCompletionService completion;
	@MockitoSpyBean
	private JpaAccountRepository accountRepository;
	@MockitoSpyBean
	private ActiveUserPresenceRepository presenceRepository;
	@MockitoSpyBean
	private PushProvider push;
	@MockitoSpyBean
	private NicknameModerationChecker moderation;

	private final AtomicReference<Pause> pause = new AtomicReference<>();
	private long user;
	private long other;

	@BeforeEach
	void setup() {
		user = account();
		other = account();
		clearInvocations(push, moderation);
		JpaAccountRepository realAccount = AopTestUtils.getUltimateTargetObject(accountRepository);
		ActiveUserPresenceRepository realPresence = AopTestUtils.getUltimateTargetObject(presenceRepository);
		doAnswer(invocation -> {
			observeAttempt();
			Object result = invocation.callRealMethod();
			Pause current = pause.get();
			if (current != null) {
				assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
				current.connections.add(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
			}
			stopLeader("LOCK");
			return result;
		}).when(realAccount).findByIdForUpdate(anyLong());
		doAnswer(invocation -> {
			observeAttempt();
			Object result = invocation.callRealMethod();
			stopLeader("STATUS");
			return result;
		}).when(realAccount).updateStatus(any(Account.class));
		doAnswer(invocation -> {
			stopLeader("WRITE");
			return invocation.callRealMethod();
		}).when(realPresence).saveIfNewer(any(ActiveUserPresence.class));
	}

	@AfterEach
	void cleanup() {
		Pause current = pause.getAndSet(null);
		if (current != null) {
			current.release.countDown();
		}
		dropFailure();
		for (long id : new long[]{user, other}) {
			jdbc.update("DELETE FROM active_user_presence WHERE user_id = ?", id);
			jdbc.update("DELETE FROM location_consent_history WHERE user_id = ?", id);
			jdbc.update("DELETE FROM user_account WHERE id = ?", id);
		}
	}

	@Test
	@DisplayName("INT-007: 철회 성공은 이력과 위치 삭제를 함께 commit하고 타인 행은 보존한다")
	void withdrawalCommitsHistoryAndDeletionTogether() throws Exception {
		seedConsent(user);
		seedPresence(user);
		seedConsent(other);
		seedPresence(other);
		var otherBefore = snapshot(other);
		consent.withdraw(user);
		assertThat(historyActions()).containsExactly("CONSENT", "WITHDRAW");
		assertThat(independentRows("active_user_presence", user)).isEmpty();
		assertThat(snapshot(other)).isEqualTo(otherBefore);
		verifyNoInteractions(push, moderation);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class)).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"INSERT", "DELETE"})
	@DisplayName("INT-007: append 또는 삭제의 실제 DB 실패는 철회 이력·presence를 모두 rollback한다")
	void withdrawalFailureRollsBackBothResources(String operation) throws Exception {
		seedConsent(user);
		seedPresence(user);
		seedConsent(other);
		seedPresence(other);
		var before = snapshot(user);
		var otherBefore = snapshot(other);
		installFailure(operation);
		try {
			assertThatThrownBy(() -> consent.withdraw(user)).isInstanceOf(DataAccessException.class);
		} finally {
			dropFailure();
		}
		assertThat(snapshot(user)).isEqualTo(before);
		assertThat(snapshot(other)).isEqualTo(otherBefore);
		verifyNoInteractions(push, moderation);
	}

	@ParameterizedTest
	@CsvSource({"false,true", "false,false", "true,true", "true,false"})
	@DisplayName("INT-008: 최초·기존 이력에서 동의와 철회는 양쪽 잠금 순서대로 서로 다른 DB transaction을 commit한다")
	void consentAndWithdrawalSerializeFromEitherOrder(boolean existing, boolean consentFirst) throws Exception {
		if (existing) {
			seedConsent(user);
		}
		Callable<?> first = consentFirst ? () -> consent.consent(user, "test-v1") : () -> consent.withdraw(user);
		Callable<?> second = consentFirst ? () -> consent.withdraw(user) : () -> consent.consent(user, "test-v1");
		Race result = race("LOCK", first, second);
		assertThat(result.first.failure).isNull();
		assertThat(result.second.failure).isNull();
		List<String> expected = new java.util.ArrayList<>();
		if (existing) {
			expected.add("CONSENT");
		}
		expected.add(consentFirst ? "CONSENT" : "WITHDRAW");
		expected.add(consentFirst ? "WITHDRAW" : "CONSENT");
		assertThat(historyActions()).containsExactlyElementsOf(expected);
		assertThat(consent.current(user).consented()).isEqualTo(!consentFirst);
		assertThat(result.connections).hasSize(2).doesNotHaveDuplicates();
	}

	@ParameterizedTest
	@CsvSource({"true,true", "true,false", "false,true", "false,false"})
	@DisplayName("INT-009: presence 유무와 무관하게 갱신 후 철회는 삭제하고 철회 후 갱신은 거부한다")
	void updateAndWithdrawalLeaveNoPresence(boolean present, boolean updateFirst) throws Exception {
		seedConsent(user);
		if (present) {
			seedPresence(user);
		}
		Callable<?> update = () -> presence.update(user, command());
		Callable<?> revoke = () -> consent.withdraw(user);
		Race result = race(updateFirst ? "WRITE" : "LOCK", updateFirst ? update : revoke,
				updateFirst ? revoke : update);
		assertThat(result.first.failure).isNull();
		if (updateFirst) {
			assertThat(result.second.failure).isNull();
		} else {
			assertDirectionFailure(result.second.failure, DirectionErrorCode.LOCATION_CONSENT_REQUIRED);
		}
		assertThat(independentRows("active_user_presence", user)).isEmpty();
		assertThat(historyActions()).containsExactly("CONSENT", "WITHDRAW");
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	@DisplayName("INT-010: 계정 탈퇴와 위치 갱신의 양쪽 순서는 위치를 남기지 않고 원장을 보존한다")
	void accountWithdrawalAndUpdateCannotResurrectPresence(boolean updateFirst) throws Exception {
		seedConsent(user);
		var before = independentRows("location_consent_history", user);
		Callable<?> update = () -> presence.update(user, command());
		Callable<?> deactivate = () -> withdrawal.request(user);
		Race result = race(updateFirst ? "WRITE" : "STATUS", updateFirst ? update : deactivate,
				updateFirst ? deactivate : update);
		assertThat(result.first.failure).isNull();
		if (updateFirst) {
			assertThat(result.second.failure).isNull();
		} else {
			assertDirectionFailure(result.second.failure, DirectionErrorCode.PRESENCE_ACCOUNT_NOT_ELIGIBLE);
		}
		assertThat(independentRows("active_user_presence", user)).isEmpty();
		assertThat(independentRows("location_consent_history", user)).isEqualTo(before);
		assertThat(jdbc.queryForObject("SELECT status FROM user_account WHERE id = ?", String.class, user))
				.isEqualTo("WITHDRAWAL_PENDING");
	}

	@Test
	@DisplayName("INT-010: 탈퇴 요청·취소·완료는 동의 원장을 보존하며 취소는 위치를 복원하지 않는다")
	void accountLifecyclePreservesLedgerWithoutRestoringPresence() throws Exception {
		seedConsent(user);
		seedPresence(user);
		var before = independentRows("location_consent_history", user);
		withdrawal.request(user);
		assertThatThrownBy(() -> consent.consent(user, "test-v1"))
				.satisfies(
						failure -> assertDirectionFailure(failure, DirectionErrorCode.PRESENCE_ACCOUNT_NOT_ELIGIBLE));
		assertThatThrownBy(() -> presence.update(user, command()))
				.satisfies(
						failure -> assertDirectionFailure(failure, DirectionErrorCode.PRESENCE_ACCOUNT_NOT_ELIGIBLE));
		withdrawal.cancel(user);
		assertThat(independentRows("active_user_presence", user)).isEmpty();
		withdrawal.request(user);
		assertThat(completion.complete(user, AT.plus(Duration.ofDays(30)).plusSeconds(1))).isTrue();
		assertThatThrownBy(() -> consent.consent(user, "test-v1"))
				.satisfies(
						failure -> assertDirectionFailure(failure, DirectionErrorCode.PRESENCE_ACCOUNT_NOT_ELIGIBLE));
		assertThatThrownBy(() -> presence.update(user, command()))
				.satisfies(
						failure -> assertDirectionFailure(failure, DirectionErrorCode.PRESENCE_ACCOUNT_NOT_ELIGIBLE));
		assertThat(independentRows("location_consent_history", user)).isEqualTo(before);
		assertThat(independentRows("active_user_presence", user)).isEmpty();
	}

	@Test
	@DisplayName("INT-014: commit 전 append 실패 후 재요청은 최종 성공 이력 한 행만 남긴다")
	void retryAfterRollbackLeavesOneCommittedEvent() throws Exception {
		installFailure("INSERT");
		try {
			assertThatThrownBy(() -> consent.consent(user, "test-v1")).isInstanceOf(DataAccessException.class);
		} finally {
			dropFailure();
		}
		assertThat(independentRows("location_consent_history", user)).isEmpty();
		assertThat(consent.consent(user, "test-v1").consented()).isTrue();
		assertThat(historyActions()).containsExactly("CONSENT");
		verifyNoInteractions(push, moderation);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	@DisplayName("INT-014: commit 후 응답 유실을 재요청하면 동의·철회 모두 두 감사 행과 같은 상태를 남긴다")
	void retryAfterLostResponseAppendsAgain(boolean consenting) throws Exception {
		seedPresence(user);
		Callable<?> request = consenting ? () -> consent.consent(user, "test-v1") : () -> consent.withdraw(user);
		assertThatThrownBy(() -> {
			request.call();
			throw new LostResponse();
		}).isInstanceOf(LostResponse.class);
		request.call();
		assertThat(historyActions()).containsExactly(consenting ? "CONSENT" : "WITHDRAW",
				consenting ? "CONSENT" : "WITHDRAW");
		assertThat(consent.current(user).consented()).isEqualTo(consenting);
		if (!consenting) {
			assertThat(independentRows("active_user_presence", user)).isEmpty();
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class)).isZero();
		verifyNoInteractions(push, moderation);
	}

	private Race race(String stage, Callable<?> first, Callable<?> second) throws Exception {
		Pause signals = new Pause(stage);
		pause.set(signals);
		var executor = Executors.newFixedThreadPool(2);
		try {
			Future<Attempt> leader = executor.submit(() -> {
				signals.leader.set(Thread.currentThread());
				return attempt(first);
			});
			await(signals.reached, "leader reached " + stage);
			assertRealAccountLock();
			Future<Attempt> follower = executor.submit(() -> attempt(second));
			await(signals.attempted, "follower entered real account operation");
			signals.release.countDown();
			return new Race(leader.get(10, TimeUnit.SECONDS), follower.get(10, TimeUnit.SECONDS),
					List.copyOf(signals.connections));
		} finally {
			signals.release.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
			pause.set(null);
		}
	}

	private void assertRealAccountLock() throws SQLException {
		try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword())) {
			connection.setAutoCommit(false);
			try (var query = connection
					.prepareStatement("SELECT id FROM user_account WHERE id = ? FOR UPDATE NOWAIT")) {
				query.setLong(1, user);
				assertThatThrownBy(query::executeQuery).isInstanceOf(SQLException.class)
						.satisfies(failure -> assertThat(((SQLException) failure).getSQLState()).isEqualTo("55P03"));
			} finally {
				connection.rollback();
			}
		}
	}

	private void observeAttempt() {
		Pause current = pause.get();
		if (current != null && current.leader.get() != Thread.currentThread()) {
			current.attempted.countDown();
		}
	}

	private void stopLeader(String stage) throws InterruptedException {
		Pause current = pause.get();
		if (current != null && current.stage.equals(stage) && current.leader.get() == Thread.currentThread()) {
			current.reached.countDown();
			await(current.release, "leader release");
		}
	}

	private static void await(CountDownLatch signal, String description) throws InterruptedException {
		assertThat(signal.await(10, TimeUnit.SECONDS)).as(description).isTrue();
	}

	private static Attempt attempt(Callable<?> action) {
		try {
			return new Attempt(action.call(), null);
		} catch (Exception failure) {
			return new Attempt(null, failure);
		}
	}

	private long account() {
		return jdbc.queryForObject("""
				INSERT INTO user_account (country_code, coarse_region_code, locale, timezone, nickname)
				VALUES ('KR', 'KR', 'ko-KR', 'UTC', ?) RETURNING id
				""", Long.class, "consent-race-" + SEQUENCE.incrementAndGet());
	}

	private void seedConsent(long id) {
		jdbc.update("""
				INSERT INTO location_consent_history (user_id, terms_version, action, recorded_at)
				VALUES (?, 'test-v1', 'CONSENT', ?)
				""", id, Timestamp.from(AT.minusSeconds(60)));
	}

	private void seedPresence(long id) {
		jdbc.update("""
				INSERT INTO active_user_presence (user_id, position, coarse_region_code, accuracy_m, receive_allowed,
					location_at, expires_at)
				VALUES (?, ST_SetSRID(ST_MakePoint(127,37.5),4326)::geography, 'KR', 1, true, ?, ?)
				""", id, Timestamp.from(AT.minusSeconds(60)), Timestamp.from(AT.plusSeconds(3600)));
	}

	private DirectionPresenceService.UpdateCommand command() {
		return new DirectionPresenceService.UpdateCommand(new BigDecimal("37.6"), new BigDecimal("127.1"),
				BigDecimal.ONE, true, AT);
	}

	private void installFailure(String operation) {
		String table = operation.equals("INSERT") ? "location_consent_history" : "active_user_presence";
		jdbc.execute("""
				CREATE FUNCTION consent381_fail() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN
					IF %s.user_id = %d THEN
						RAISE EXCEPTION 'CONSENT381_INJECTED' USING ERRCODE = '23514';
					END IF;
					RETURN %s;
				END;
				$$
				""".formatted(operation.equals("INSERT") ? "NEW" : "OLD", user,
				operation.equals("INSERT") ? "NEW" : "OLD"));
		jdbc.execute("CREATE TRIGGER consent381_fail BEFORE " + operation + " ON " + table
				+ " FOR EACH ROW EXECUTE FUNCTION consent381_fail()");
	}

	private void dropFailure() {
		jdbc.execute("DROP TRIGGER IF EXISTS consent381_fail ON location_consent_history");
		jdbc.execute("DROP TRIGGER IF EXISTS consent381_fail ON active_user_presence");
		jdbc.execute("DROP FUNCTION IF EXISTS consent381_fail()");
	}

	private Map<String, List<Map<String, Object>>> snapshot(long id) throws SQLException {
		return Map.of("history", independentRows("location_consent_history", id),
				"presence", independentRows("active_user_presence", id));
	}

	private List<Map<String, Object>> independentRows(String table, long id) throws SQLException {
		try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword())) {
			return new JdbcTemplate(new SingleConnectionDataSource(connection, true))
					.queryForList("SELECT * FROM " + table + " WHERE user_id = ? ORDER BY user_id"
							+ (table.equals("location_consent_history") ? ", id" : ""), id);
		}
	}

	private List<String> historyActions() throws SQLException {
		return independentRows("location_consent_history", user).stream().map(row -> (String) row.get("action"))
				.toList();
	}

	private void assertDirectionFailure(Throwable failure, DirectionErrorCode code) {
		assertThat(failure).isInstanceOf(DirectionException.class);
		assertThat(((DirectionException) failure).getErrorCode()).isEqualTo(code);
	}

	private record Attempt(Object value, Throwable failure) {
	}

	private record Race(Attempt first, Attempt second, List<Integer> connections) {
	}

	private static final class LostResponse extends RuntimeException {
	}

	private static final class Pause {
		final String stage;
		final AtomicReference<Thread> leader = new AtomicReference<>();
		final CountDownLatch reached = new CountDownLatch(1);
		final CountDownLatch attempted = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		final List<Integer> connections = new CopyOnWriteArrayList<>();

		Pause(String stage) {
			this.stage = stage;
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTime {
		@Bean
		@Primary
		Clock consentRaceClock() {
			return Clock.fixed(AT, ZoneOffset.UTC);
		}
	}
}
