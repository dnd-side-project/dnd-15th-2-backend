/*
 * Created at: 2026-08-07T15:40:00+09:00
 * Source scenario: TEST-PLAN-GH-72-OPERATOR-LOGIN-UNIT-001 through UNIT-006
 *
 * import 수가 많아 클래스 선언 위에 두면 정책 검사 범위(첫 30줄)를 벗어나므로 여기에 배치.
 */
package com.dnd.qello.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.domain.OperatorCredential;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;
import com.dnd.qello.auth.repository.OperatorCredentialRepository;
import com.dnd.qello.auth.security.PasswordHash;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.security.bcrypt.BCryptPasswordHasher;

class OperatorLoginServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-07T09:00:00Z");
	private static final long OPERATOR_ID = 1L;
	private static final String CORRECT_PASSWORD = "correct-horse-battery";

	private final BCryptPasswordHasher passwordHasher = new BCryptPasswordHasher();

	private FakeCredentialRepository credentialRepository;
	private FakeAccountRepository accountRepository;
	private OperatorLoginService service;

	@BeforeEach
	void setUp() {
		credentialRepository = new FakeCredentialRepository();
		accountRepository = new FakeAccountRepository();
		service = new OperatorLoginService(
			credentialRepository, accountRepository, passwordHasher, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("자격증명이 맞으면 계정 id를 돌려주고 실패 기록을 지운다")
	void returnsAccountIdOnSuccess() {
		givenOperator(AccountStatus.ACTIVE);
		credentialRepository.store(storedCredential().recordFailure(NOW));

		long userId = service.login(new LoginId("qello-admin"), new RawPassword(CORRECT_PASSWORD));

		assertThat(userId).isEqualTo(OPERATOR_ID);
		assertThat(credentialRepository.stored.getFailedAttemptCount()).isZero();
		assertThat(credentialRepository.stored.getLastLoginAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("존재하지 않는 loginId와 잘못된 비밀번호는 같은 오류 코드로 응답한다")
	void doesNotDistinguishMissingAccountFromWrongPassword() {
		givenOperator(AccountStatus.ACTIVE);
		credentialRepository.store(storedCredential());

		Throwable unknownLoginId = catchThrowable(() ->
			service.login(new LoginId("no-such-operator"), new RawPassword(CORRECT_PASSWORD)));
		Throwable wrongPassword = catchThrowable(() ->
			service.login(new LoginId("qello-admin"), new RawPassword("wrong-password")));

		assertThat(unknownLoginId)
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.LOGIN_FAILED);
		assertThat(wrongPassword)
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.LOGIN_FAILED);
		assertThat(((AuthException) unknownLoginId).getReason())
			.isEqualTo(((AuthException) wrongPassword).getReason());
		assertThat(((AuthException) unknownLoginId).getField())
			.isEqualTo(((AuthException) wrongPassword).getField());
	}

	@Test
	@DisplayName("존재하지 않는 loginId에도 비밀번호 검증 비용을 치른다")
	void spendsHashingCostForUnknownLoginId() {
		// 이 호출이 사라지면 응답 시간으로 계정 존재 여부를 알아낼 수 있다.
		givenOperator(AccountStatus.ACTIVE);
		credentialRepository.store(storedCredential());

		long unknownElapsed = elapsedNanos(() ->
			service.login(new LoginId("no-such-operator"), new RawPassword(CORRECT_PASSWORD)));
		long wrongPasswordElapsed = elapsedNanos(() ->
			service.login(new LoginId("qello-admin"), new RawPassword("wrong-password")));

		// bcrypt 한 번의 비용은 수 ms다. 검증을 건너뛰면 두 경로의 차이가 그만큼 벌어진다.
		assertThat(unknownElapsed).isGreaterThan(wrongPasswordElapsed / 10);
	}

	@Test
	@DisplayName("실패가 5회에 도달하면 잠기고 이후 요청은 잠금 오류를 받는다")
	void locksAfterRepeatedFailures() {
		givenOperator(AccountStatus.ACTIVE);
		credentialRepository.store(storedCredential());

		for (int attempt = 0; attempt < OperatorCredential.MAX_FAILED_ATTEMPTS; attempt++) {
			assertThatThrownBy(() ->
				service.login(new LoginId("qello-admin"), new RawPassword("wrong-password")))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.LOGIN_FAILED);
		}

		assertThatThrownBy(() ->
			service.login(new LoginId("qello-admin"), new RawPassword(CORRECT_PASSWORD)))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.CREDENTIAL_LOCKED);
		assertThat(credentialRepository.stored.getLockedUntil())
			.isEqualTo(NOW.plus(Duration.ofMinutes(15)));
	}

	@Test
	@DisplayName("차단된 계정은 자격증명이 맞아도 로그인할 수 없다")
	void rejectsNonActiveAccount() {
		givenOperator(AccountStatus.BLOCKED);
		credentialRepository.store(storedCredential());

		assertThatThrownBy(() ->
			service.login(new LoginId("qello-admin"), new RawPassword(CORRECT_PASSWORD)))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ACCOUNT_NOT_ACTIVE);
	}

	@Test
	@DisplayName("실패 예외는 입력한 loginId와 비밀번호를 담지 않는다")
	void failureCarriesNoUserInput() {
		givenOperator(AccountStatus.ACTIVE);
		credentialRepository.store(storedCredential());

		Throwable failure = catchThrowable(() ->
			service.login(new LoginId("qello-admin"), new RawPassword("super-secret-input")));

		assertThat(failure).isInstanceOf(AuthException.class);
		AuthException exception = (AuthException) failure;
		assertThat(exception.getField()).isNull();
		assertThat(exception.getReason()).doesNotContain("super-secret-input", "qello-admin");
		assertThat(exception.getMessage()).doesNotContain("super-secret-input", "qello-admin");
	}

	private long elapsedNanos(Runnable runnable) {
		long start = System.nanoTime();
		catchThrowable(runnable::run);
		return System.nanoTime() - start;
	}

	private void givenOperator(AccountStatus status) {
		accountRepository.store(Account.restore(
			OPERATOR_ID, AccountRole.OPERATOR, status, null, "KR-TEST", "ko-KR", "Asia/Seoul", "admin", null));
	}

	private OperatorCredential storedCredential() {
		PasswordHash hash = passwordHasher.hash(new RawPassword(CORRECT_PASSWORD));
		return OperatorCredential.issue(OPERATOR_ID, new LoginId("qello-admin"), hash, NOW);
	}

	private static final class FakeCredentialRepository implements OperatorCredentialRepository {

		private OperatorCredential stored;

		void store(OperatorCredential credential) {
			this.stored = credential;
		}

		@Override
		public OperatorCredential save(OperatorCredential credential) {
			this.stored = credential;
			return credential;
		}

		@Override
		public OperatorCredential updateLoginState(OperatorCredential credential) {
			this.stored = credential;
			return credential;
		}

		@Override
		public Optional<OperatorCredential> findByLoginId(LoginId loginId) {
			return stored != null && stored.getLoginId().equals(loginId)
				? Optional.of(stored)
				: Optional.empty();
		}

		@Override
		public Optional<OperatorCredential> findByUserId(long userId) {
			return stored != null && stored.getUserId() == userId
				? Optional.of(stored)
				: Optional.empty();
		}
	}

	private static final class FakeAccountRepository implements AccountRepository {

		private final Map<Long, Account> accounts = new HashMap<>();

		void store(Account account) {
			accounts.put(account.getId(), account);
		}

		@Override
		public Account save(Account account) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Account updateProfile(Account account) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Account updateProfileImage(Account account) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Account updateStatus(Account account) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<Account> findById(long id) {
			return Optional.ofNullable(accounts.get(id));
		}

		@Override
		public boolean existsActiveNickname(String nickname) {
			throw new UnsupportedOperationException();
		}
	}
}
