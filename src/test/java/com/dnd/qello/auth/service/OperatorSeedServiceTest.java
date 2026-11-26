/*
 * Created at: 2026-11-24T23:50:07+09:00
 * Source scenario: TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-015
 */
package com.dnd.qello.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
import com.dnd.qello.auth.security.PasswordHasher;
import com.dnd.qello.auth.security.RawPassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorSeedServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-24T14:00:00Z");
	private static final long OPERATOR_ID = 11L;
	private static final LoginId LOGIN_ID = new LoginId("qello-admin");
	private static final String COMPLIANT_PASSWORD = "example-operator-password";
	private static final String NON_COMPLIANT_PASSWORD = "short1";

	private FakeAccountRepository accountRepository;
	private FakeCredentialRepository credentialRepository;
	private CountingPasswordHasher passwordHasher;
	private OperatorSeedService service;

	@BeforeEach
	void setUp() {
		accountRepository = new FakeAccountRepository();
		credentialRepository = new FakeCredentialRepository();
		passwordHasher = new CountingPasswordHasher();
		service = new OperatorSeedService(
				accountRepository, credentialRepository, passwordHasher, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("UNIT-015: 이미 있는 login_id면 비밀번호가 규칙을 어겨도 검사·저장 없이 false다")
	void skipsExistingOperatorWithoutValidation() {
		OperatorCredential existing = OperatorCredential.issue(
				OPERATOR_ID, LOGIN_ID, new PasswordHash("$2a$10$existing-hash"), NOW);
		credentialRepository.saved.add(existing);

		boolean created = seed(NON_COMPLIANT_PASSWORD);

		assertThat(created).isFalse();
		assertThat(accountRepository.saved).isEmpty();
		assertThat(credentialRepository.saved).containsExactly(existing);
		assertThat(passwordHasher.hashCalls).isZero();
	}

	@Test
	@DisplayName("UNIT-015: 새 운영자의 비밀번호가 규칙을 어기면 AUT-VAL-005이고 계정·자격증명을 저장하지 않는다")
	void rejectsNonCompliantPasswordForNewOperator() {
		assertThatThrownBy(() -> seed(NON_COMPLIANT_PASSWORD))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.PASSWORD_POLICY_VIOLATION);

		assertThat(accountRepository.saved).isEmpty();
		assertThat(credentialRepository.saved).isEmpty();
		assertThat(passwordHasher.hashCalls).isZero();
	}

	@Test
	@DisplayName("UNIT-015: 새 운영자의 비밀번호가 규칙을 지키면 true이고 변경이 필요한 자격증명으로 저장한다")
	void seedsCompliantOperatorAsChangeRequired() {
		boolean created = seed(COMPLIANT_PASSWORD);

		assertThat(created).isTrue();
		assertThat(accountRepository.saved).hasSize(1);
		assertThat(credentialRepository.saved).singleElement().satisfies(credential -> {
			assertThat(credential.getUserId()).isEqualTo(OPERATOR_ID);
			assertThat(credential.getLoginId()).isEqualTo(LOGIN_ID);
			assertThat(credential.isPasswordChangeRequired()).isTrue();
			assertThat(credential.getPasswordUpdatedAt()).isEqualTo(NOW);
		});
	}

	private boolean seed(String password) {
		return service.seedIfAbsent(LOGIN_ID, new RawPassword(password), "qello-admin", "KR-TEST", "ko-KR",
				"Asia/Seoul");
	}

	private static final class CountingPasswordHasher implements PasswordHasher {

		private int hashCalls;

		@Override
		public PasswordHash hash(RawPassword rawPassword) {
			hashCalls++;
			return new PasswordHash("$2a$10$seeded-hash");
		}

		@Override
		public boolean matches(RawPassword rawPassword, PasswordHash passwordHash) {
			throw new UnsupportedOperationException();
		}
	}

	private static final class FakeCredentialRepository implements OperatorCredentialRepository {

		private final List<OperatorCredential> saved = new ArrayList<>();

		@Override
		public OperatorCredential save(OperatorCredential credential) {
			saved.add(credential);
			return credential;
		}

		@Override
		public OperatorCredential updateLoginState(OperatorCredential credential) {
			throw new UnsupportedOperationException();
		}

		@Override
		public OperatorCredential updatePassword(OperatorCredential credential) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<OperatorCredential> findByLoginId(LoginId loginId) {
			return saved.stream().filter(credential -> credential.getLoginId().equals(loginId)).findFirst();
		}

		@Override
		public Optional<OperatorCredential> findByUserId(long userId) {
			throw new UnsupportedOperationException();
		}
	}

	private static final class FakeAccountRepository implements AccountRepository {

		private final List<Account> saved = new ArrayList<>();

		@Override
		public Account save(Account account) {
			Account stored = Account.restore(OPERATOR_ID, AccountRole.OPERATOR, AccountStatus.ACTIVE, null, "KR-TEST",
					"ko-KR", "Asia/Seoul", "qello-admin", null);
			saved.add(stored);
			return stored;
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
		public Account updateDeletion(Account account) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<Account> findById(long id) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean existsActiveNickname(String nickname) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<Long> findWithdrawalDueIds(Instant requestedAtOrBefore, int limit) {
			throw new UnsupportedOperationException();
		}
	}
}
