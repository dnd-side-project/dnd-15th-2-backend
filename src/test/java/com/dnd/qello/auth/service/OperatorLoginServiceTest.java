/*
 * Created at: 2026-08-07T15:40:00+09:00
 * Source scenario: TEST-PLAN-GH-72-OPERATOR-LOGIN-UNIT-001 through UNIT-006
 * Source scenario: TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-001 through UNIT-006 (added 2026-11-22T22:09:04+09:00)
 * Source scenario: TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-009 (added 2026-11-24T23:50:07+09:00)
 *
 * import 수가 많아 클래스 선언 위에 두면 정책 검사 범위(첫 30줄)를 벗어나므로 여기에 배치.
 */
package com.dnd.qello.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.auth.config.OperatorPasswordProperties;
import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.domain.OperatorAccessEvent;
import com.dnd.qello.auth.domain.OperatorAccessLogEntry;
import com.dnd.qello.auth.domain.OperatorAccessRequest;
import com.dnd.qello.auth.domain.OperatorCredential;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;
import com.dnd.qello.auth.repository.OperatorAccessLogRepository;
import com.dnd.qello.auth.repository.OperatorCredentialRepository;
import com.dnd.qello.auth.security.PasswordHash;
import com.dnd.qello.auth.security.PasswordHasher;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.security.bcrypt.BCryptPasswordHasher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class OperatorLoginServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-07T09:00:00Z");
	private static final long OPERATOR_ID = 1L;
	private static final String CORRECT_PASSWORD = "correct-horse-battery";
	private static final OperatorAccessRequest REQUEST = new OperatorAccessRequest("192.0.2.10", "POST",
			"/admin/login");
	private static final Duration PASSWORD_MAX_AGE = Duration.ofDays(90);

	private final CountingPasswordHasher passwordHasher = new CountingPasswordHasher(new BCryptPasswordHasher());

	private FakeCredentialRepository credentialRepository;
	private FakeAccountRepository accountRepository;
	private FakeAccessLogRepository accessLogRepository;
	private OperatorLoginService service;

	@BeforeEach
	void setUp() {
		credentialRepository = new FakeCredentialRepository();
		accountRepository = new FakeAccountRepository();
		accessLogRepository = new FakeAccessLogRepository();
		service = new OperatorLoginService(credentialRepository, accountRepository, passwordHasher,
				new OperatorAccessLogRecorder(accessLogRepository), new OperatorPasswordProperties(PASSWORD_MAX_AGE),
				Clock.fixed(NOW, ZoneOffset.UTC));
		passwordHasher.matchesCalls = 0;
	}

	@Test
	@DisplayName("UNIT-001: 로그인 성공은 운영자 id·200·요청 정보·판정 시각으로 한 건 기록한다")
	void recordsSuccessfulLogin() {
		givenOperator(AccountStatus.ACTIVE);
		credentialRepository.store(storedCredential());

		service.login(new LoginId("qello-admin"), new RawPassword(CORRECT_PASSWORD), REQUEST);

		assertThat(accessLogRepository.entries).singleElement().satisfies(entry -> {
			assertThat(entry.eventType()).isEqualTo(OperatorAccessEvent.LOGIN_SUCCEEDED);
			assertThat(entry.operatorUserId()).isEqualTo(OPERATOR_ID);
			assertThat(entry.httpStatus()).isEqualTo(200);
			assertThat(entry.resultCode()).isNull();
			assertThat(entry.occurredAt()).isEqualTo(NOW);
			assertThat(entry.request()).isEqualTo(REQUEST);
		});
	}

	@Test
	@DisplayName("UNIT-002: 존재하지 않는 loginId는 운영자 id 없이 401·AUT-APP-001로 기록하고 더미 해시 검증을 한 번 한다")
	void recordsUnknownLoginIdWithoutOperator() {
		assertThatThrownBy(
				() -> service.login(new LoginId("no-such-operator"), new RawPassword(CORRECT_PASSWORD), REQUEST))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.LOGIN_FAILED);

		assertThat(passwordHasher.matchesCalls).isEqualTo(1);
		assertThat(accessLogRepository.entries).singleElement().satisfies(entry -> {
			assertThat(entry.eventType()).isEqualTo(OperatorAccessEvent.LOGIN_FAILED);
			assertThat(entry.operatorUserId()).isNull();
			assertThat(entry.httpStatus()).isEqualTo(401);
			assertThat(entry.resultCode()).isEqualTo("AUT-APP-001");
		});
	}

	@Test
	@DisplayName("UNIT-003: 잘못된 비밀번호는 실패 카운터를 올리고 운영자 id와 함께 401·AUT-APP-001로 기록한다")
	void recordsWrongPasswordWithOperator() {
		givenOperator(AccountStatus.ACTIVE);
		credentialRepository.store(storedCredential());

		assertThatThrownBy(() -> service.login(new LoginId("qello-admin"), new RawPassword("wrong-password"), REQUEST))
				.isInstanceOf(AuthException.class);

		assertThat(credentialRepository.stored.getFailedAttemptCount()).isEqualTo(1);
		assertThat(accessLogRepository.entries).singleElement().satisfies(entry -> {
			assertThat(entry.eventType()).isEqualTo(OperatorAccessEvent.LOGIN_FAILED);
			assertThat(entry.operatorUserId()).isEqualTo(OPERATOR_ID);
			assertThat(entry.httpStatus()).isEqualTo(401);
			assertThat(entry.resultCode()).isEqualTo("AUT-APP-001");
		});
	}

	@Test
	@DisplayName("UNIT-004: 잠긴 자격증명은 비밀번호를 검증하지 않고 423·AUT-APP-002로 기록한다")
	void recordsLockedCredentialWithoutPasswordCheck() {
		givenOperator(AccountStatus.ACTIVE);
		OperatorCredential locked = storedCredential();
		for (int attempt = 0; attempt < OperatorCredential.MAX_FAILED_ATTEMPTS; attempt++) {
			locked = locked.recordFailure(NOW);
		}
		credentialRepository.store(locked);
		passwordHasher.matchesCalls = 0;

		assertThatThrownBy(() -> service.login(new LoginId("qello-admin"), new RawPassword(CORRECT_PASSWORD), REQUEST))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.CREDENTIAL_LOCKED);

		assertThat(passwordHasher.matchesCalls).isZero();
		assertThat(accessLogRepository.entries).singleElement().satisfies(entry -> {
			assertThat(entry.operatorUserId()).isEqualTo(OPERATOR_ID);
			assertThat(entry.httpStatus()).isEqualTo(423);
			assertThat(entry.resultCode()).isEqualTo("AUT-APP-002");
		});
	}

	@Test
	@DisplayName("UNIT-005: 비활성 계정은 성공 처리 없이 운영자 id와 함께 403·AUT-APP-003으로 기록한다")
	void recordsInactiveAccount() {
		givenOperator(AccountStatus.BLOCKED);
		credentialRepository.store(storedCredential());

		assertThatThrownBy(() -> service.login(new LoginId("qello-admin"), new RawPassword(CORRECT_PASSWORD), REQUEST))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ACCOUNT_NOT_ACTIVE);

		assertThat(credentialRepository.stored.getLastLoginAt()).isNull();
		assertThat(accessLogRepository.entries).singleElement().satisfies(entry -> {
			assertThat(entry.eventType()).isEqualTo(OperatorAccessEvent.LOGIN_FAILED);
			assertThat(entry.operatorUserId()).isEqualTo(OPERATOR_ID);
			assertThat(entry.httpStatus()).isEqualTo(403);
			assertThat(entry.resultCode()).isEqualTo("AUT-APP-003");
		});
	}

	@Test
	@DisplayName("UNIT-006: 기록 저장이 실패하면 성공·실패 분기 모두 그 예외를 AuthException으로 바꾸지 않고 던진다")
	void propagatesRecordingFailure() {
		givenOperator(AccountStatus.ACTIVE);
		credentialRepository.store(storedCredential());
		accessLogRepository.failure = new DataAccessResourceFailureException("simulated");

		assertThatThrownBy(() -> service.login(new LoginId("qello-admin"), new RawPassword(CORRECT_PASSWORD), REQUEST))
				.isInstanceOf(DataAccessResourceFailureException.class);
		assertThatThrownBy(() -> service.login(new LoginId("qello-admin"), new RawPassword("wrong-password"), REQUEST))
				.isInstanceOf(DataAccessResourceFailureException.class);
	}

	@Test
	@DisplayName("UNIT-009: 초기 비밀번호로 로그인하면 변경이 필요하다는 결과를 돌려주고 성공 처리는 그대로 한다")
	void reportsChangeRequiredForInitialPassword() {
		givenOperator(AccountStatus.ACTIVE);
		credentialRepository.store(storedCredential().recordFailure(NOW));

		OperatorLoginResult result = service.login(
				new LoginId("qello-admin"), new RawPassword(CORRECT_PASSWORD), REQUEST);

		assertThat(result).isEqualTo(new OperatorLoginResult(OPERATOR_ID, true));
		assertSuccessHandled();
	}

	@Test
	@DisplayName("UNIT-009: 주기 안에 직접 바꾼 비밀번호로 로그인하면 변경이 필요 없다는 결과를 돌려준다")
	void reportsNoChangeForRecentlyChangedPassword() {
		givenOperator(AccountStatus.ACTIVE);
		credentialRepository.store(changedCredential(NOW.minus(PASSWORD_MAX_AGE).plusSeconds(1)).recordFailure(NOW));

		OperatorLoginResult result = service.login(
				new LoginId("qello-admin"), new RawPassword(CORRECT_PASSWORD), REQUEST);

		assertThat(result).isEqualTo(new OperatorLoginResult(OPERATOR_ID, false));
		assertSuccessHandled();
	}

	@Test
	@DisplayName("UNIT-009: 마지막 변경 후 주기가 지난 비밀번호로 로그인하면 변경이 필요하다는 결과를 돌려준다")
	void reportsChangeRequiredForExpiredPassword() {
		givenOperator(AccountStatus.ACTIVE);
		credentialRepository.store(changedCredential(NOW.minus(PASSWORD_MAX_AGE)).recordFailure(NOW));

		OperatorLoginResult result = service.login(
				new LoginId("qello-admin"), new RawPassword(CORRECT_PASSWORD), REQUEST);

		assertThat(result).isEqualTo(new OperatorLoginResult(OPERATOR_ID, true));
		assertSuccessHandled();
	}

	private void assertSuccessHandled() {
		assertThat(credentialRepository.stored.getFailedAttemptCount()).isZero();
		assertThat(credentialRepository.stored.getLastLoginAt()).isEqualTo(NOW);
		assertThat(accessLogRepository.entries).singleElement()
				.extracting(OperatorAccessLogEntry::eventType)
				.isEqualTo(OperatorAccessEvent.LOGIN_SUCCEEDED);
	}

	@Test
	@DisplayName("자격증명이 맞으면 계정 id를 돌려주고 실패 기록을 지운다")
	void returnsAccountIdOnSuccess() {
		givenOperator(AccountStatus.ACTIVE);
		credentialRepository.store(storedCredential().recordFailure(NOW));

		long userId = service.login(new LoginId("qello-admin"), new RawPassword(CORRECT_PASSWORD), REQUEST).userId();

		assertThat(userId).isEqualTo(OPERATOR_ID);
		assertThat(credentialRepository.stored.getFailedAttemptCount()).isZero();
		assertThat(credentialRepository.stored.getLastLoginAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("존재하지 않는 loginId와 잘못된 비밀번호는 같은 오류 코드로 응답한다")
	void doesNotDistinguishMissingAccountFromWrongPassword() {
		givenOperator(AccountStatus.ACTIVE);
		credentialRepository.store(storedCredential());

		Throwable unknownLoginId = catchThrowable(
				() -> service.login(new LoginId("no-such-operator"), new RawPassword(CORRECT_PASSWORD), REQUEST));
		Throwable wrongPassword = catchThrowable(
				() -> service.login(new LoginId("qello-admin"), new RawPassword("wrong-password"), REQUEST));

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

		long unknownElapsed = elapsedNanos(
				() -> service.login(new LoginId("no-such-operator"), new RawPassword(CORRECT_PASSWORD), REQUEST));
		long wrongPasswordElapsed = elapsedNanos(
				() -> service.login(new LoginId("qello-admin"), new RawPassword("wrong-password"), REQUEST));

		// bcrypt 한 번의 비용은 수 ms다. 검증을 건너뛰면 두 경로의 차이가 그만큼 벌어진다.
		assertThat(unknownElapsed).isGreaterThan(wrongPasswordElapsed / 10);
	}

	@Test
	@DisplayName("실패가 5회에 도달하면 잠기고 이후 요청은 잠금 오류를 받는다")
	void locksAfterRepeatedFailures() {
		givenOperator(AccountStatus.ACTIVE);
		credentialRepository.store(storedCredential());

		for (int attempt = 0; attempt < OperatorCredential.MAX_FAILED_ATTEMPTS; attempt++) {
			assertThatThrownBy(
					() -> service.login(new LoginId("qello-admin"), new RawPassword("wrong-password"), REQUEST))
					.isInstanceOf(AuthException.class)
					.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.LOGIN_FAILED);
		}

		assertThatThrownBy(() -> service.login(new LoginId("qello-admin"), new RawPassword(CORRECT_PASSWORD), REQUEST))
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

		assertThatThrownBy(() -> service.login(new LoginId("qello-admin"), new RawPassword(CORRECT_PASSWORD), REQUEST))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ACCOUNT_NOT_ACTIVE);
	}

	@Test
	@DisplayName("실패 예외는 입력한 loginId와 비밀번호를 담지 않는다")
	void failureCarriesNoUserInput() {
		givenOperator(AccountStatus.ACTIVE);
		credentialRepository.store(storedCredential());

		Throwable failure = catchThrowable(
				() -> service.login(new LoginId("qello-admin"), new RawPassword("super-secret-input"), REQUEST));

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

	// 운영자가 changedAt에 직접 바꾼 비밀번호. 해시는 CORRECT_PASSWORD 그대로 둔다.
	private OperatorCredential changedCredential(Instant changedAt) {
		PasswordHash hash = passwordHasher.hash(new RawPassword(CORRECT_PASSWORD));
		return OperatorCredential.restore(OPERATOR_ID, new LoginId("qello-admin"), hash, 0, null, changedAt, false,
				null);
	}

	private static final class CountingPasswordHasher implements PasswordHasher {

		private final PasswordHasher delegate;
		private int matchesCalls;

		CountingPasswordHasher(PasswordHasher delegate) {
			this.delegate = delegate;
		}

		@Override
		public PasswordHash hash(RawPassword rawPassword) {
			return delegate.hash(rawPassword);
		}

		@Override
		public boolean matches(RawPassword rawPassword, PasswordHash passwordHash) {
			matchesCalls++;
			return delegate.matches(rawPassword, passwordHash);
		}
	}

	private static final class FakeAccessLogRepository implements OperatorAccessLogRepository {

		private final List<OperatorAccessLogEntry> entries = new ArrayList<>();
		private RuntimeException failure;

		@Override
		public void append(OperatorAccessLogEntry entry) {
			if (failure != null) {
				throw failure;
			}
			entries.add(entry);
		}
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
		public OperatorCredential updatePassword(OperatorCredential credential) {
			throw new UnsupportedOperationException("로그인은 비밀번호를 바꾸지 않는다");
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
		public Account updateDeletion(Account account) {
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

		@Override
		public List<Long> findWithdrawalDueIds(Instant requestedAtOrBefore, int limit) {
			throw new UnsupportedOperationException();
		}
	}
}
