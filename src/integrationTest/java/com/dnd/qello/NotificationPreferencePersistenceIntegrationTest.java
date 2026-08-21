/**
 * Created at: 2026-08-21T18:12:00+09:00
 * Source scenario: TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-004 through
 * INT-007, INT-011, INT-013
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.notification.domain.NotificationPreferenceSnapshot;
import com.dnd.qello.notification.domain.NotificationQuietHours;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.repository.NotificationPreferenceRepository;
import com.dnd.qello.notification.repository.jdbc.sql.NotificationPreferenceSql;
import com.dnd.qello.notification.service.NotificationPreferenceService;
import com.dnd.qello.notification.service.UpdateNotificationPreferences;

@SpringBootTest
@ActiveProfiles("test")
class NotificationPreferencePersistenceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH178-PREF";

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private TransactionTemplate transactions;
	@Autowired
	private NotificationPreferenceRepository preferences;
	@Autowired
	private NotificationPreferenceService preferenceService;
	@MockitoSpyBean
	private NamedParameterJdbcTemplate namedJdbc;

	private long userId;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM notification_user_setting");
		jdbc.update("DELETE FROM notification_preference");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'GH178 Preference', 'REGION')
			""", REGION);
		userId = jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', 'gh178-preference-user')
			RETURNING id
			""", Long.class, REGION);
	}

	@Test
	@DisplayName("설정 행이 없으면 global과 6종은 ON이고 quiet는 없다")
	void readsSparseDefaults() {
		NotificationPreferenceSnapshot snapshot = preferences.findByUserId(userId);

		assertThat(snapshot.userId()).isEqualTo(userId);
		assertThat(snapshot.pushEnabled()).isTrue();
		assertThat(snapshot.quietHours()).isNull();
		assertThat(snapshot.typeEnabled()).hasSize(NotificationType.values().length);
		assertThat(snapshot.typeEnabled().values()).containsOnly(true);
	}

	@Test
	@DisplayName("사용자 설정과 6종 설정을 round-trip하고 effective push gate를 계산한다")
	void savesAndReadsRoundTrip() {
		NotificationQuietHours quietHours = new NotificationQuietHours(
			LocalTime.of(22, 0),
			LocalTime.of(7, 0),
			ZoneId.of("Asia/Seoul"));
		Map<NotificationType, Boolean> typeEnabled = Map.ofEntries(
			Map.entry(NotificationType.ANSWER_RECEIVED, true),
			Map.entry(NotificationType.ANSWER_REACTED, false),
			Map.entry(NotificationType.DIRECTION_POST_RECEIVED, true),
			Map.entry(NotificationType.REPORT_RESOLVED, false),
			Map.entry(NotificationType.QUESTION_PROPOSAL_REVIEWED, true),
			Map.entry(NotificationType.QUESTION_RECOMMENDED, true));

		transactions.executeWithoutResult(status -> {
			preferences.lockUser(userId);
			preferences.saveUserSetting(userId, false, quietHours);
			preferences.replaceTypePreferences(userId, typeEnabled);
		});

		NotificationPreferenceSnapshot saved = preferences.findByUserId(userId);

		assertThat(saved.userId()).isEqualTo(userId);
		assertThat(saved.pushEnabled()).isFalse();
		assertThat(saved.quietHours()).isEqualTo(quietHours);
		assertThat(saved.typeEnabled()).containsExactlyInAnyOrderEntriesOf(typeEnabled);
		assertThat(preferences.isPushEnabled(userId, NotificationType.ANSWER_RECEIVED)).isFalse();

		preferences.saveUserSetting(userId, true, quietHours);

		assertThat(preferences.isPushEnabled(userId, NotificationType.ANSWER_RECEIVED)).isTrue();
		assertThat(preferences.isPushEnabled(userId, NotificationType.ANSWER_REACTED)).isFalse();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_user_setting", Long.class)).isEqualTo(1L);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_preference WHERE user_id = ?",
			Long.class, userId)).isEqualTo((long) NotificationType.values().length);
	}

	@Test
	@DisplayName("마지막 종류 저장이 실패하면 global과 앞선 종류도 요청 전 snapshot으로 롤백한다")
	void rollsBackWholeSnapshotOnTypeWriteFailure() {
		NotificationPreferenceSnapshot original = persist(snapshot(false, mixedTypesA(), overnightQuietHours()));
		doAnswer(invocation -> {
			MapSqlParameterSource parameters = invocation.getArgument(1);
			if (NotificationType.QUESTION_RECOMMENDED.name().equals(parameters.getValue("notificationType"))) {
				throw new DataAccessResourceFailureException("forced last type write failure");
			}
			return invocation.callRealMethod();
		}).when(namedJdbc).update(eq(NotificationPreferenceSql.UPSERT_TYPE_PREFERENCE), any(MapSqlParameterSource.class));

		try {
			assertThatThrownBy(() -> preferenceService.replaceMine(userId, command(true, mixedTypesB(), null)))
				.isInstanceOf(DataAccessException.class);

			assertThat(preferences.findByUserId(userId)).isEqualTo(original);
		} finally {
			reset(namedJdbc);
		}
	}

	@Test
	@DisplayName("동시 PUT 두 개는 snapshot A 또는 B 전체만 남기고 종류별 혼합값을 남기지 않는다")
	void concurrentPutsLeaveOnlyOneCompleteSnapshot() throws Exception {
		NotificationPreferenceSnapshot snapshotA = snapshot(false, mixedTypesA(), overnightQuietHours());
		NotificationPreferenceSnapshot snapshotB = snapshot(true, mixedTypesB(), new NotificationQuietHours(
			LocalTime.of(1, 30), LocalTime.of(8, 45), ZoneId.of("Asia/Tokyo")));
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<NotificationPreferenceSnapshot>> results = List.of(
				executor.submit(() -> replaceAfterSignal(command(
					snapshotA.pushEnabled(), snapshotA.typeEnabled(), snapshotA.quietHours()), ready, start)),
				executor.submit(() -> replaceAfterSignal(command(
					snapshotB.pushEnabled(), snapshotB.typeEnabled(), snapshotB.quietHours()), ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			NotificationPreferenceSnapshot firstResult = results.get(0).get(10, TimeUnit.SECONDS);
			NotificationPreferenceSnapshot secondResult = results.get(1).get(10, TimeUnit.SECONDS);
			NotificationPreferenceSnapshot finalSnapshot = preferences.findByUserId(userId);

			assertThat(firstResult).isEqualTo(snapshotA);
			assertThat(secondResult).isEqualTo(snapshotB);
			assertThat(finalSnapshot).isIn(snapshotA, snapshotB);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("선행 transaction이 user lock을 잡고 있으면 PUT은 release 전 완료되지 않고 release 후 완료된다")
	void replaceWaitsForExistingUserLockUntilReleased() throws Exception {
		NotificationPreferenceSnapshot original = persist(snapshot(false, mixedTypesA(), overnightQuietHours()));
		UpdateNotificationPreferences replacement = command(true, mixedTypesB(), null);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch replaceStarted = new CountDownLatch(1);
		try {
			Future<?> lockHolder = executor.submit(() -> transactions.executeWithoutResult(status -> {
				preferences.lockUser(userId);
				locked.countDown();
				awaitOrFail(release, "release");
			}));
			assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

			Future<NotificationPreferenceSnapshot> blockedReplace = executor.submit(() -> {
				replaceStarted.countDown();
				return preferenceService.replaceMine(userId, replacement);
			});
			assertThat(replaceStarted.await(5, TimeUnit.SECONDS)).isTrue();

			assertThatThrownBy(() -> blockedReplace.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			assertThat(preferences.findByUserId(userId)).isEqualTo(original);

			release.countDown();

			lockHolder.get(5, TimeUnit.SECONDS);
			NotificationPreferenceSnapshot saved = blockedReplace.get(5, TimeUnit.SECONDS);
			assertThat(saved).isEqualTo(snapshot(true, mixedTypesB(), null));
			assertThat(preferences.findByUserId(userId)).isEqualTo(saved);
		} finally {
			executor.shutdownNow();
		}
	}

	private NotificationPreferenceSnapshot replaceAfterSignal(
		UpdateNotificationPreferences command,
		CountDownLatch ready,
		CountDownLatch start) throws Exception {
		ready.countDown();
		assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
		return preferenceService.replaceMine(userId, command);
	}

	private NotificationPreferenceSnapshot persist(NotificationPreferenceSnapshot snapshot) {
		transactions.executeWithoutResult(status -> {
			preferences.lockUser(snapshot.userId());
			preferences.saveUserSetting(snapshot.userId(), snapshot.pushEnabled(), snapshot.quietHours());
			preferences.replaceTypePreferences(snapshot.userId(), snapshot.typeEnabled());
		});
		return preferences.findByUserId(snapshot.userId());
	}

	private void awaitOrFail(CountDownLatch latch, String name) {
		try {
			assertThat(latch.await(5, TimeUnit.SECONDS))
				.as("%s latch timed out", name)
				.isTrue();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private NotificationPreferenceSnapshot snapshot(
		boolean pushEnabled,
		Map<NotificationType, Boolean> typeEnabled,
		NotificationQuietHours quietHours) {
		return new NotificationPreferenceSnapshot(userId, pushEnabled, quietHours, typeEnabled);
	}

	private UpdateNotificationPreferences command(
		boolean pushEnabled,
		Map<NotificationType, Boolean> typeEnabled,
		NotificationQuietHours quietHours) {
		return new UpdateNotificationPreferences(pushEnabled, quietHours, typeEnabled);
	}

	private Map<NotificationType, Boolean> mixedTypesA() {
		return Map.ofEntries(
			Map.entry(NotificationType.ANSWER_RECEIVED, true),
			Map.entry(NotificationType.ANSWER_REACTED, false),
			Map.entry(NotificationType.DIRECTION_POST_RECEIVED, true),
			Map.entry(NotificationType.REPORT_RESOLVED, false),
			Map.entry(NotificationType.QUESTION_PROPOSAL_REVIEWED, true),
			Map.entry(NotificationType.QUESTION_RECOMMENDED, true));
	}

	private Map<NotificationType, Boolean> mixedTypesB() {
		return Map.ofEntries(
			Map.entry(NotificationType.ANSWER_RECEIVED, false),
			Map.entry(NotificationType.ANSWER_REACTED, true),
			Map.entry(NotificationType.DIRECTION_POST_RECEIVED, false),
			Map.entry(NotificationType.REPORT_RESOLVED, true),
			Map.entry(NotificationType.QUESTION_PROPOSAL_REVIEWED, false),
			Map.entry(NotificationType.QUESTION_RECOMMENDED, false));
	}

	private NotificationQuietHours overnightQuietHours() {
		return new NotificationQuietHours(
			LocalTime.of(22, 0),
			LocalTime.of(7, 0),
			ZoneId.of("Asia/Seoul"));
	}
}
