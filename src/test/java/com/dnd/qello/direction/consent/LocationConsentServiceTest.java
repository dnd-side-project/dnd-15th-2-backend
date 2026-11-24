/**
 * Created at: 2026-11-23T13:41:12Z
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-UNIT-001
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-UNIT-002
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-UNIT-003
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-UNIT-004
 */
package com.dnd.qello.direction.consent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.env.StandardEnvironment;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.AccountLockRepository;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.gate.LocationCollectionGate;
import com.dnd.qello.direction.gate.LocationCollectionGateProperties;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LocationConsentServiceTest {

	private static final long USER_ID = 7L;
	private static final Instant NOW = Instant.parse("2026-11-23T13:41:12Z");
	private final AccountRepository accounts = mock(AccountRepository.class);
	private final AccountLockRepository locks = mock(AccountLockRepository.class);
	private final ActiveUserPresenceRepository presences = mock(ActiveUserPresenceRepository.class);
	private History history;
	private LocationConsentService service;

	@BeforeEach
	void setUp() {
		history = new History();
		service = new LocationConsentService(history, accounts, locks, presences,
				new LocationConsentProperties("test-v2"),
				new LocationCollectionGate(new LocationCollectionGateProperties(true, null), new StandardEnvironment()),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@ParameterizedTest
	@MethodSource("latestStates")
	@DisplayName("무기록·철회·이전 버전은 거부하고 현재 동의만 현재 상태와 접근을 허용한다")
	void resolvesLatestStateAndEnforcesConsent(LocationConsent latest, boolean expected) {
		when(accounts.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
		if (latest != null) {
			history.events.add(latest);
		}

		LocationConsentService.CurrentConsent state = service.current(USER_ID);

		assertThat(state.consented()).isEqualTo(expected);
		assertThat(state.currentTermsVersion()).isEqualTo("test-v2");
		assertThat(state.lastAction()).isEqualTo(latest == null ? null : latest.action());
		assertThat(state.lastTermsVersion()).isEqualTo(latest == null ? null : latest.termsVersion());
		assertThat(state.recordedAt()).isEqualTo(latest == null ? null : latest.recordedAt());
		if (expected) {
			assertThatCode(() -> service.ensureConsented(USER_ID)).doesNotThrowAnyException();
		} else {
			assertThatThrownBy(() -> service.ensureConsented(USER_ID))
					.isInstanceOf(DirectionException.class)
					.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.LOCATION_CONSENT_REQUIRED);
		}
		assertThat(history.events).hasSize(latest == null ? 0 : 1);
		verifyNoInteractions(presences, locks);
	}

	static Stream<Arguments> latestStates() {
		return Stream.of(
				Arguments.of(null, false),
				Arguments.of(event(1, "test-v2", LocationConsentAction.WITHDRAW, NOW), false),
				Arguments.of(event(1, "test-v1", LocationConsentAction.CONSENT, NOW), false),
				Arguments.of(event(1, "test-v2", LocationConsentAction.CONSENT, NOW), true));
	}

	@ParameterizedTest
	@MethodSource("withdrawalTimes")
	@DisplayName("최신 이력이 동률·역행 시각의 구버전 철회여도 과거 현재 버전 동의를 부활시키지 않는다")
	void rejectsLatestWithdrawalDespiteOlderCurrentConsent(Instant withdrawalAt) {
		when(accounts.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
		history.events.add(event(1, "test-v2", LocationConsentAction.CONSENT, NOW));
		history.events.add(event(2, "test-v1", LocationConsentAction.WITHDRAW, withdrawalAt));

		assertThat(service.current(USER_ID).consented()).isFalse();
		assertThatThrownBy(() -> service.ensureConsented(USER_ID))
				.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.LOCATION_CONSENT_REQUIRED);
		assertThat(history.events).hasSize(2);
	}

	static Stream<Instant> withdrawalTimes() {
		return Stream.of(NOW, NOW.minusSeconds(60));
	}

	@ParameterizedTest
	@MethodSource("rejectedVersions")
	@DisplayName("현재 버전 불일치와 잘못된 문자열은 성공 이력을 추가하지 않는다")
	void rejectsVersionWithoutAppending(String version, DirectionErrorCode error) {
		when(locks.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(activeUser()));
		LocationConsent previous = event(1, "test-v2", LocationConsentAction.CONSENT, NOW.minusSeconds(60));
		history.events.add(previous);

		assertThatThrownBy(() -> service.consent(USER_ID, version))
				.isInstanceOf(DirectionException.class)
				.hasFieldOrPropertyWithValue("errorCode", error);
		assertThat(history.events).containsExactly(previous);
		verifyNoInteractions(presences);
	}

	static Stream<Arguments> rejectedVersions() {
		return Stream.of(
				Arguments.of("test-v1", DirectionErrorCode.LOCATION_CONSENT_VERSION_MISMATCH),
				Arguments.of(null, DirectionErrorCode.INVALID_TEXT),
				Arguments.of("", DirectionErrorCode.INVALID_TEXT),
				Arguments.of(" \t", DirectionErrorCode.INVALID_TEXT),
				Arguments.of(" test-v2", DirectionErrorCode.INVALID_TEXT),
				Arguments.of("test-v2 ", DirectionErrorCode.INVALID_TEXT),
				Arguments.of("x".repeat(101), DirectionErrorCode.INVALID_TEXT));
	}

	@Test
	@DisplayName("재동의는 서버 Clock으로 한 행을 추가하며 기존 이벤트의 모든 값을 보존한다")
	void repeatedConsentAppendsWithServerClockAndPreservesPreviousEvent() {
		when(locks.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(activeUser()));
		LocationConsent previous = event(1, "test-v2", LocationConsentAction.CONSENT, NOW.plusSeconds(60));
		history.events.add(previous);

		LocationConsentService.CurrentConsent result = service.consent(USER_ID, "test-v2");

		assertThat(history.events).containsExactly(previous,
				event(2, "test-v2", LocationConsentAction.CONSENT, NOW));
		assertThat(history.events.getFirst()).isSameAs(previous)
				.isEqualTo(event(1, "test-v2", LocationConsentAction.CONSENT, NOW.plusSeconds(60)));
		assertThat(result).isEqualTo(new LocationConsentService.CurrentConsent(
				"test-v2", true, LocationConsentAction.CONSENT, "test-v2", NOW));
		verify(locks).findByIdForUpdate(USER_ID);
		verifyNoInteractions(presences, accounts);
	}

	@ParameterizedTest
	@MethodSource("withdrawalStates")
	@DisplayName("철회는 최신 이벤트 버전 또는 무기록의 현재 버전으로 추가하고 본인 위치만 삭제한다")
	void withdrawalAppendsSelectedVersionAndDeletesOwnPresence(LocationConsent previous, String expectedVersion) {
		when(locks.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(activeUser()));
		if (previous != null) {
			history.events.add(previous);
		}

		LocationConsentService.CurrentConsent result = service.withdraw(USER_ID);

		assertThat(history.events).hasSize(previous == null ? 1 : 2);
		assertThat(history.events.getLast()).isEqualTo(
				event(previous == null ? 1 : 2, expectedVersion, LocationConsentAction.WITHDRAW, NOW));
		if (previous != null) {
			assertThat(history.events.getFirst()).isSameAs(previous);
		}
		assertThat(result).isEqualTo(new LocationConsentService.CurrentConsent(
				"test-v2", false, LocationConsentAction.WITHDRAW, expectedVersion, NOW));
		verify(presences).deleteByUserId(USER_ID);
		org.mockito.Mockito.verifyNoMoreInteractions(presences);
		verifyNoInteractions(accounts);
	}

	static Stream<Arguments> withdrawalStates() {
		return Stream.of(
				Arguments.of(null, "test-v2"),
				Arguments.of(event(1, "test-v2", LocationConsentAction.CONSENT, NOW.minusSeconds(60)), "test-v2"),
				Arguments.of(event(1, "test-v1", LocationConsentAction.CONSENT, NOW.minusSeconds(60)), "test-v1"),
				Arguments.of(event(1, "test-v1", LocationConsentAction.WITHDRAW, NOW.minusSeconds(60)), "test-v1"));
	}

	private static Account activeUser() {
		return Account.createUser("KR", "TEST-REGION", "ko-KR", "Asia/Seoul", "test");
	}

	private static LocationConsent event(long id, String version, LocationConsentAction action, Instant at) {
		return new LocationConsent(id, USER_ID, version, action, at);
	}

	private static final class History implements LocationConsentRepository {
		private final List<LocationConsent> events = new ArrayList<>();

		@Override
		public LocationConsent append(LocationConsent consent) {
			assertThat(consent.id()).isNull();
			LocationConsent saved = new LocationConsent(events.size() + 1L, consent.userId(), consent.termsVersion(),
					consent.action(), consent.recordedAt());
			events.add(saved);
			return saved;
		}

		@Override
		public Optional<LocationConsent> findLatestByUserId(long userId) {
			return events.stream().filter(event -> event.userId() == userId)
					.max(Comparator.comparing(LocationConsent::id));
		}
	}
}
