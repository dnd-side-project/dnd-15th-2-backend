/*
 * Created at: 2026-11-24T23:50:07+09:00
 * Source scenario: TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-010 through UNIT-014
 */
package com.dnd.qello.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.domain.OperatorCredential;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;
import com.dnd.qello.auth.repository.OperatorCredentialRepository;
import com.dnd.qello.auth.security.PasswordHash;
import com.dnd.qello.auth.security.PasswordHasher;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.security.bcrypt.BCryptPasswordHasher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorPasswordChangeServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-24T14:00:00Z");
	private static final Instant ISSUED_AT = NOW.minus(Duration.ofDays(3));
	private static final long OPERATOR_ID = 7L;
	private static final LoginId LOGIN_ID = new LoginId("qello-admin");
	private static final String CURRENT_PASSWORD = "example-operator-password";
	private static final String NEW_PASSWORD = "Changed-Passw0rd-376";

	private final CountingPasswordHasher passwordHasher = new CountingPasswordHasher(new BCryptPasswordHasher());

	private FakeCredentialRepository credentialRepository;
	private OperatorPasswordChangeService service;

	@BeforeEach
	void setUp() {
		credentialRepository = new FakeCredentialRepository();
		service = new OperatorPasswordChangeService(
				credentialRepository, passwordHasher, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("UNIT-010: 현재 비밀번호가 틀리면 AUT-APP-009이고 실패를 1회 기록하며 새 비밀번호는 검사·해시하지 않는다")
	void rejectsWrongCurrentPassword() {
		credentialRepository.store(issued());
		passwordHasher.reset();

		// 새 비밀번호는 작성 규칙 위반이다. 규칙을 먼저 검사하면 AUT-VAL-005가 나온다.
		assertThatThrownBy(() -> change("wrong-current-password", "short"))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.CURRENT_PASSWORD_MISMATCH)
				.hasFieldOrPropertyWithValue("field", "currentPassword");

		assertThat(credentialRepository.stored.getFailedAttemptCount()).isEqualTo(1);
		assertThat(credentialRepository.loginStateUpdates).isEqualTo(1);
		assertThat(credentialRepository.passwordUpdates).isZero();
		assertThat(passwordHasher.hashCalls).isZero();
	}

	@Test
	@DisplayName("UNIT-010: 현재 비밀번호를 5회 틀리면 잠기고 이후 요청은 AUT-APP-002다")
	void locksAfterFiveWrongCurrentPasswords() {
		credentialRepository.store(issued());

		for (int attempt = 0; attempt < OperatorCredential.MAX_FAILED_ATTEMPTS; attempt++) {
			assertThatThrownBy(() -> change("wrong-current-password", NEW_PASSWORD))
					.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.CURRENT_PASSWORD_MISMATCH);
		}

		assertThat(credentialRepository.stored.isLockedAt(NOW)).isTrue();
		assertThatThrownBy(() -> change(CURRENT_PASSWORD, NEW_PASSWORD))
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.CREDENTIAL_LOCKED);
	}

	@Test
	@DisplayName("UNIT-011: 현재 비밀번호는 맞고 새 비밀번호가 규칙을 어기면 AUT-VAL-005이고 실패 횟수·저장은 그대로다")
	void rejectsPolicyViolationWithoutCountingFailure() {
		credentialRepository.store(issued());

		assertThatThrownBy(() -> change(CURRENT_PASSWORD, "abcdefg12"))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.PASSWORD_POLICY_VIOLATION)
				.hasFieldOrPropertyWithValue("field", "newPassword");

		assertThat(credentialRepository.stored.getFailedAttemptCount()).isZero();
		assertThat(credentialRepository.loginStateUpdates).isZero();
		assertThat(credentialRepository.passwordUpdates).isZero();
	}

	@Test
	@DisplayName("UNIT-012: 새 비밀번호가 현재 비밀번호와 같으면 AUT-APP-010이고 저장하지 않는다")
	void rejectsUnchangedPassword() {
		credentialRepository.store(issued());

		assertThatThrownBy(() -> change(CURRENT_PASSWORD, CURRENT_PASSWORD))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.PASSWORD_NOT_CHANGED)
				.hasFieldOrPropertyWithValue("field", "newPassword");

		assertThat(credentialRepository.loginStateUpdates).isZero();
		assertThat(credentialRepository.passwordUpdates).isZero();
	}

	@Test
	@DisplayName("UNIT-013: 잠긴 자격증명은 비밀번호를 비교하지 않고 AUT-APP-002다")
	void rejectsLockedCredentialWithoutComparing() {
		OperatorCredential locked = issued();
		for (int attempt = 0; attempt < OperatorCredential.MAX_FAILED_ATTEMPTS; attempt++) {
			locked = locked.recordFailure(NOW);
		}
		credentialRepository.store(locked);
		passwordHasher.reset();

		assertThatThrownBy(() -> change(CURRENT_PASSWORD, NEW_PASSWORD))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.CREDENTIAL_LOCKED);

		assertThat(passwordHasher.matchesCalls).isZero();
		assertThat(credentialRepository.stored.getFailedAttemptCount())
				.isEqualTo(OperatorCredential.MAX_FAILED_ATTEMPTS);
	}

	@Test
	@DisplayName("UNIT-014: 성공하면 새 해시·변경 시각을 저장하고 변경 필요 상태와 실패 횟수를 지운다")
	void changesPassword() {
		credentialRepository.store(issued().recordFailure(NOW).recordFailure(NOW));

		change(CURRENT_PASSWORD, NEW_PASSWORD);

		OperatorCredential stored = credentialRepository.stored;
		assertThat(credentialRepository.passwordUpdates).isEqualTo(1);
		assertThat(passwordHasher.matches(new RawPassword(NEW_PASSWORD), stored.getPasswordHash())).isTrue();
		assertThat(passwordHasher.matches(new RawPassword(CURRENT_PASSWORD), stored.getPasswordHash())).isFalse();
		assertThat(stored.getPasswordUpdatedAt()).isEqualTo(NOW);
		assertThat(stored.isPasswordChangeRequired()).isFalse();
		assertThat(stored.getFailedAttemptCount()).isZero();
		assertThat(stored.getLockedUntil()).isNull();
	}

	@Test
	@DisplayName("자격증명이 없으면 AUT-APP-004다")
	void rejectsMissingCredential() {
		assertThatThrownBy(() -> change(CURRENT_PASSWORD, NEW_PASSWORD))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.CREDENTIAL_NOT_FOUND);
	}

	private void change(String currentPassword, String newPassword) {
		service.changePassword(OPERATOR_ID, new RawPassword(currentPassword), new RawPassword(newPassword));
	}

	private OperatorCredential issued() {
		return OperatorCredential.issue(
				OPERATOR_ID, LOGIN_ID, passwordHasher.hash(new RawPassword(CURRENT_PASSWORD)), ISSUED_AT);
	}

	private static final class CountingPasswordHasher implements PasswordHasher {

		private final PasswordHasher delegate;
		private int hashCalls;
		private int matchesCalls;

		CountingPasswordHasher(PasswordHasher delegate) {
			this.delegate = delegate;
		}

		void reset() {
			hashCalls = 0;
			matchesCalls = 0;
		}

		@Override
		public PasswordHash hash(RawPassword rawPassword) {
			hashCalls++;
			return delegate.hash(rawPassword);
		}

		@Override
		public boolean matches(RawPassword rawPassword, PasswordHash passwordHash) {
			matchesCalls++;
			return delegate.matches(rawPassword, passwordHash);
		}
	}

	private static final class FakeCredentialRepository implements OperatorCredentialRepository {

		private OperatorCredential stored;
		private int loginStateUpdates;
		private int passwordUpdates;

		void store(OperatorCredential credential) {
			this.stored = credential;
		}

		@Override
		public OperatorCredential save(OperatorCredential credential) {
			throw new UnsupportedOperationException();
		}

		@Override
		public OperatorCredential updateLoginState(OperatorCredential credential) {
			loginStateUpdates++;
			this.stored = credential;
			return credential;
		}

		@Override
		public OperatorCredential updatePassword(OperatorCredential credential) {
			passwordUpdates++;
			this.stored = credential;
			return credential;
		}

		@Override
		public Optional<OperatorCredential> findByLoginId(LoginId loginId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<OperatorCredential> findByUserId(long userId) {
			return stored != null && stored.getUserId() == userId ? Optional.of(stored) : Optional.empty();
		}
	}
}
