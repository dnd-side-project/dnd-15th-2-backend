/**
 * Created at: 2026-08-14T00:51:11+09:00
 * Source scenario: TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-UNIT-002, UNIT-003, UNIT-006
 */
package com.dnd.qello.direction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.direction.config.DirectionPresenceProperties;
import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;

class DirectionPresenceServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-13T15:00:00Z");
	private static final Duration TTL = Duration.ofHours(24);
	private FakeAccountRepository accountRepository;
	private FakePresenceRepository presenceRepository;
	private DirectionPresenceService service;

	@BeforeEach
	void setUp() {
		accountRepository = new FakeAccountRepository(activeUser(1L, "SERVER-REGION"));
		presenceRepository = new FakePresenceRepository();
		service = new DirectionPresenceService(accountRepository, presenceRepository,
			new DirectionPresenceProperties(TTL, BigDecimal.valueOf(100), Duration.ofSeconds(30), Duration.ofMinutes(5)),
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("ACTIVE USER의 지역과 관측 시각 기준 TTL로 최신 위치를 저장한다")
	void updatesPresenceWithServerOwnedRegionAndExpiration() {
		boolean applied = service.update(1L, command(NOW.minusSeconds(60), BigDecimal.valueOf(100), false));

		assertThat(applied).isTrue();
		assertThat(presenceRepository.saved.getCoarseRegionCode()).isEqualTo("SERVER-REGION");
		assertThat(presenceRepository.saved.getExpiresAt()).isEqualTo(NOW.minusSeconds(60).plus(TTL));
		assertThat(presenceRepository.saved.isReceiveAllowed()).isFalse();
	}

	@Test
	@DisplayName("정확도 상한 초과는 저장 전에 안전한 direction 오류로 거절한다")
	void rejectsAccuracyAboveConfiguredLimit() {
		assertThatThrownBy(() -> service.update(1L, command(NOW, new BigDecimal("100.01"), true)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE)
			.hasFieldOrPropertyWithValue("field", "accuracyMeters");
		assertThat(presenceRepository.saved).isNull();
	}

	@Test
	@DisplayName("승인된 미래와 과거 관측 경계만 포함한다")
	void enforcesConfiguredObservationWindowInclusively() {
		assertThat(service.update(1L, command(NOW.plusSeconds(30), BigDecimal.ONE, true))).isTrue();
		assertThat(service.update(1L, command(NOW.minus(Duration.ofMinutes(5)), BigDecimal.ONE, true))).isTrue();

		assertThatThrownBy(() -> service.update(1L, command(NOW.plusSeconds(31), BigDecimal.ONE, true)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_TIME_ORDER);
		assertThatThrownBy(() -> service.update(1L, command(NOW.minus(Duration.ofMinutes(5)).minusSeconds(1), BigDecimal.ONE, true)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_TIME_ORDER);
	}

	@Test
	@DisplayName("없는 계정과 ACTIVE USER가 아닌 계정은 feature 오류로 변환하고 저장하지 않는다")
	void rejectsMissingOrIneligibleAccountAtFeatureBoundary() {
		accountRepository.account = null;
		assertThatThrownBy(() -> service.update(1L, command(NOW, BigDecimal.ONE, true)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.PRESENCE_ACCOUNT_NOT_FOUND);

		for (Account account : new Account[] {
			account(1L, AccountRole.USER, AccountStatus.BLOCKED),
			account(1L, AccountRole.USER, AccountStatus.DELETED),
			account(1L, AccountRole.OPERATOR, AccountStatus.ACTIVE)
		}) {
			accountRepository.account = account;
			assertThatThrownBy(() -> service.update(1L, command(NOW, BigDecimal.ONE, true)))
				.isInstanceOf(DirectionException.class)
				.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.PRESENCE_ACCOUNT_NOT_ELIGIBLE);
		}
		assertThat(presenceRepository.saved).isNull();
	}

	private DirectionPresenceService.UpdateCommand command(Instant observedAt, BigDecimal accuracy, boolean receiveAllowed) {
		return new DirectionPresenceService.UpdateCommand(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127), accuracy,
			receiveAllowed, observedAt);
	}

	private static Account activeUser(long id, String region) {
		return Account.restore(id, AccountRole.USER, AccountStatus.ACTIVE, "KR", region, "ko-KR", "Asia/Seoul", null, null);
	}

	private static Account account(long id, AccountRole role, AccountStatus status) {
		return Account.restore(id, role, status, role == AccountRole.USER ? "KR" : null, "TEST-REGION",
			"ko-KR", "Asia/Seoul", null, status == AccountStatus.DELETED ? NOW.minusSeconds(1) : null);
	}

	private static final class FakeAccountRepository implements AccountRepository {
		private Account account;

		private FakeAccountRepository(Account account) {
			this.account = account;
		}

		@Override public Account save(Account account) { throw new UnsupportedOperationException(); }
		@Override public Account updateProfile(Account account) { throw new UnsupportedOperationException(); }
		@Override public Account updateProfileImage(Account account) { throw new UnsupportedOperationException(); }
		@Override public Account updateStatus(Account account) { throw new UnsupportedOperationException(); }
		@Override public Optional<Account> findById(long id) { return Optional.ofNullable(account); }
	}

	private static final class FakePresenceRepository implements ActiveUserPresenceRepository {
		private ActiveUserPresence saved;

		@Override public ActiveUserPresence save(ActiveUserPresence presence) { saved = presence; return presence; }
		@Override public boolean saveIfNewer(ActiveUserPresence presence) { saved = presence; return true; }
		@Override public Optional<ActiveUserPresence> findByUserId(long userId) { return Optional.empty(); }
		@Override public java.util.List<com.dnd.qello.direction.domain.DirectionCandidate> findCandidates(long excludedUserId,
			double originLatitude, double originLongitude, long minDistanceMeters, long maxDistanceMeters,
			double sectorStartDegrees, double sectorEndDegrees, Instant at, String regionCode) { return java.util.List.of(); }
		@Override public java.util.List<DirectionSegmentCandidateCount> findCandidateCountsBySegment(long schemeId,
			long excludedUserId, double originLatitude, double originLongitude, long minDistanceMeters,
			long maxDistanceMeters, Instant at, String regionCode) { return java.util.List.of(); }
	}
}
