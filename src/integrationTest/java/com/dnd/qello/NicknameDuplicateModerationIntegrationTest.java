/**
 * Created at: 2026-08-19T04:00:00+09:00
 * Source scenario: TEST-PLAN-GH-168-NICKNAME-DUPLICATE-MODERATION-INT-001 through INT-007
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.account.service.NicknameRegistrationService;
import com.dnd.qello.auth.domain.DevicePlatform;
import com.dnd.qello.auth.service.DeviceRegistrationService;
import com.dnd.qello.filtering.moderation.NicknameModerationChecker;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome.Reason;

@SpringBootTest
@ActiveProfiles({"test", "account-persistence"})
@Import(NicknameDuplicateModerationIntegrationTest.TestModerationConfiguration.class)
class NicknameDuplicateModerationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_CODE = "TEST-NICKNAME-COUNTRY";

	@Autowired
	private DeviceRegistrationService registrationService;

	@Autowired
	private NicknameRegistrationService nicknameRegistrationService;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private NicknameModerationChecker nicknameModerationChecker;

	@BeforeEach
	void resetFixtures() {
		jdbcTemplate.update("DELETE FROM user_account");
		jdbcTemplate.update("DELETE FROM region_code WHERE code = ?", REGION_CODE);
		jdbcTemplate.update("DELETE FROM region_code WHERE code = 'KR'");
		jdbcTemplate.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY'), (?, 'KR', 'Test Region', 'REGION')
			""", REGION_CODE);
		reset(nicknameModerationChecker);
		when(nicknameModerationChecker.check(anyString(), any())).thenReturn(NicknameModerationOutcome.allowed());
	}

	@Test
	@DisplayName("INT-001: 대소문자만 다른 닉네임 직접 insert는 유일성 제약 위반으로 실패한다")
	void databaseRejectsCaseInsensitiveDuplicateInsert() {
		insertAccount("여름");

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertAccount("여름"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasStackTraceContaining("uq_user_account_nickname_ci");
	}

	@Test
	@DisplayName("INT-002: 이미 존재하는 닉네임으로 두 번째 계정을 등록하면 계정 행 수가 늘지 않는다")
	void secondRegistrationWithDuplicateNicknameDoesNotCreateAccount() {
		registrationService.register("install-int-a", DevicePlatform.IOS, "KR", REGION_CODE, "ko-KR", "Asia/Seoul", "여름");

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> registrationService.register(
				"install-int-b", DevicePlatform.IOS, "KR", REGION_CODE, "ko-KR", "Asia/Seoul", "여름"))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.DUPLICATED_NICKNAME);

		Integer accountCount = jdbcTemplate.queryForObject("SELECT count(*) FROM user_account", Integer.class);
		assertThat(accountCount).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-003: 서로 다른 두 설치가 대소문자만 다른 같은 닉네임으로 동시에 등록하면 하나만 성공한다")
	void concurrentRegistrationsWithCaseVariantNicknameYieldExactlyOneWinner() throws Exception {
		RacePair<Long, Long> race = race(
			() -> registrationService.register(
				"install-int-race-a", DevicePlatform.IOS, "KR", REGION_CODE, "ko-KR", "Asia/Seoul", "여름").userId(),
			() -> registrationService.register(
				"install-int-race-b", DevicePlatform.IOS, "KR", REGION_CODE, "ko-KR", "Asia/Seoul", "여름").userId());

		boolean firstSucceeded = race.first().failure() == null;
		boolean secondSucceeded = race.second().failure() == null;
		assertThat(firstSucceeded ^ secondSucceeded).as("정확히 하나만 성공해야 한다").isTrue();

		Integer accountCount = jdbcTemplate.queryForObject("SELECT count(*) FROM user_account", Integer.class);
		assertThat(accountCount).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-004: 닉네임 변경에 성공하면 이전 닉네임을 다른 계정이 재사용할 수 있다")
	void changingNicknameFreesThePreviousValueForReuse() {
		var first = registrationService.register(
			"install-int-c", DevicePlatform.IOS, "KR", REGION_CODE, "ko-KR", "Asia/Seoul", "봄");
		registrationService.register(
			"install-int-d", DevicePlatform.IOS, "KR", REGION_CODE, "ko-KR", "Asia/Seoul", "겨울");

		Account updated = nicknameRegistrationService.changeNickname(first.userId(), "가을");
		assertThat(updated.getNickname()).isEqualTo("가을");

		// "봄"은 이제 아무도 쓰지 않으므로 다른 계정이 새로 등록하며 그 값을 쓸 수 있어야 한다.
		var third = registrationService.register(
			"install-int-e", DevicePlatform.IOS, "KR", REGION_CODE, "ko-KR", "Asia/Seoul", "봄");
		assertThat(accountRepository.findById(third.userId()).orElseThrow().getNickname()).isEqualTo("봄");
	}

	@Test
	@DisplayName("INT-005: moderation이 Allowed를 반환하면 닉네임 변경이 반영된다")
	void changeNicknameSucceedsWhenModerationAllows() {
		var account = registrationService.register(
			"install-int-f", DevicePlatform.IOS, "KR", REGION_CODE, "ko-KR", "Asia/Seoul", "원래닉네임");
		when(nicknameModerationChecker.check(anyString(), any())).thenReturn(NicknameModerationOutcome.allowed());

		Account updated = nicknameRegistrationService.changeNickname(account.userId(), "새닉네임");

		assertThat(updated.getNickname()).isEqualTo("새닉네임");
		assertThat(rawNickname(account.userId())).isEqualTo("새닉네임");
	}

	@Test
	@DisplayName("INT-006: moderation이 BLOCK을 반환하면 닉네임 변경이 반영되지 않는다")
	void changeNicknameFailsWhenModerationBlocks() {
		var account = registrationService.register(
			"install-int-g", DevicePlatform.IOS, "KR", REGION_CODE, "ko-KR", "Asia/Seoul", "원래닉네임2");
		when(nicknameModerationChecker.check(anyString(), any()))
			.thenReturn(NicknameModerationOutcome.rejected(Reason.BLOCKED_BY_PRIMARY));

		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> nicknameRegistrationService.changeNickname(account.userId(), "부적절한닉네임"))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.NICKNAME_REJECTED_BY_MODERATION);

		assertThat(rawNickname(account.userId())).isEqualTo("원래닉네임2");
	}

	@Test
	@DisplayName("INT-007: 주·보조 판정기가 모두 실패(UNAVAILABLE)하면 닉네임 변경이 반영되지 않는다")
	void changeNicknameFailsWhenModerationIsUnavailable() {
		var account = registrationService.register(
			"install-int-h", DevicePlatform.IOS, "KR", REGION_CODE, "ko-KR", "Asia/Seoul", "원래닉네임3");
		when(nicknameModerationChecker.check(anyString(), any()))
			.thenReturn(NicknameModerationOutcome.rejected(Reason.UNAVAILABLE));

		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> nicknameRegistrationService.changeNickname(account.userId(), "새닉네임4"))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.NICKNAME_MODERATION_UNAVAILABLE);

		assertThat(rawNickname(account.userId())).isEqualTo("원래닉네임3");
	}

	private void insertAccount(String nickname) {
		jdbcTemplate.update("""
			INSERT INTO user_account (role, status, country_code, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'ACTIVE', 'KR', ?, 'ko-KR', 'Asia/Seoul', ?)
			""", REGION_CODE, nickname);
	}

	private String rawNickname(long accountId) {
		return jdbcTemplate.queryForObject(
			"SELECT nickname FROM user_account WHERE id = ?", String.class, accountId);
	}

	private static <A, B> RacePair<A, B> race(Callable<A> first, Callable<B> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Attempt<A>> firstFuture = executor.submit(() -> attempt(first, ready, start));
			Future<Attempt<B>> secondFuture = executor.submit(() -> attempt(second, ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).as("both threads reached the start latch").isTrue();
			start.countDown();
			Attempt<A> firstResult = firstFuture.get(15, TimeUnit.SECONDS);
			Attempt<B> secondResult = secondFuture.get(15, TimeUnit.SECONDS);
			return new RacePair<>(firstResult, secondResult);
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).as("executor terminated").isTrue();
		}
	}

	private static <T> Attempt<T> attempt(Callable<T> action, CountDownLatch ready, CountDownLatch start) {
		ready.countDown();
		try {
			if (!start.await(5, TimeUnit.SECONDS)) {
				return new Attempt<>(null, new IllegalStateException("race start latch timed out"));
			}
			return new Attempt<>(action.call(), null);
		} catch (Throwable failure) {
			return new Attempt<>(null, failure);
		}
	}

	private record Attempt<T>(T value, Throwable failure) {
	}

	private record RacePair<A, B>(Attempt<A> first, Attempt<B> second) {
	}

	@TestConfiguration
	static class TestModerationConfiguration {

		@Bean
		@Primary
		NicknameModerationChecker nicknameModerationChecker() {
			NicknameModerationChecker checker = mock(NicknameModerationChecker.class);
			when(checker.check(anyString(), any())).thenReturn(NicknameModerationOutcome.allowed());
			return checker;
		}
	}
}
