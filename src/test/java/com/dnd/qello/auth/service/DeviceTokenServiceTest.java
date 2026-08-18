/*
 * Created at: 2026-08-07T20:52:09+09:00
 * Source scenario: TEST-PLAN-GH-73-DEVICE-TOKEN-UNIT-001 through UNIT-006
 *
 * import 수가 많아 클래스 선언 위에 두면 정책 검사 범위(첫 30줄)를 벗어나므로 여기에 배치.
 */
package com.dnd.qello.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.auth.domain.CredentialStatus;
import com.dnd.qello.auth.domain.DeviceCredential;
import com.dnd.qello.auth.domain.DevicePlatform;
import com.dnd.qello.auth.domain.SecretHash;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;
import com.dnd.qello.auth.repository.DeviceCredentialRepository;
import com.dnd.qello.auth.security.DeviceSecret;
import com.dnd.qello.auth.security.DeviceSecretHasher;
import com.dnd.qello.auth.token.AccessTokenIssuer;
import com.dnd.qello.auth.token.AccessTokenProperties;
import com.dnd.qello.auth.token.IssuedAccessToken;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

class DeviceTokenServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-07T09:00:00Z");
	private static final String SECRET = "test-only-access-token-signing-key-32-bytes-min";
	private static final long USER_ID = 1024L;
	private static final long CREDENTIAL_ID = 5521L;
	private static final String INSTALLATION_ID = "install-a";
	private static final DeviceSecret RAW_SECRET = new DeviceSecret("correct-device-secret");

	private final DeviceSecretHasher secretHasher = new DeviceSecretHasher();

	private FakeAccountRepository accountRepository;
	private FakeDeviceCredentialRepository credentialRepository;
	private DeviceTokenService service;

	@BeforeEach
	void setUp() {
		accountRepository = new FakeAccountRepository();
		credentialRepository = new FakeDeviceCredentialRepository();
		SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		AccessTokenIssuer accessTokenIssuer = new AccessTokenIssuer(
			jwtEncoder,
			new AccessTokenProperties("qello", "qello-app", 1800, SECRET),
			Clock.fixed(NOW, ZoneOffset.UTC));

		service = new DeviceTokenService(
			accountRepository, credentialRepository, secretHasher, accessTokenIssuer,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("자격증명이 맞으면 새 액세스 토큰을 돌려주고 last_used_at을 갱신한다")
	void reissuesTokenAndTouchesLastUsedAt() {
		givenAccount(AccountStatus.ACTIVE);
		givenCredential(CredentialStatus.ACTIVE);

		IssuedAccessToken token = service.reissue(INSTALLATION_ID, RAW_SECRET);

		assertThat(token.value()).isNotBlank();
		assertThat(credentialRepository.stored.getLastUsedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("일치하는 secret_hash가 없으면 거절한다")
	void rejectsUnknownSecret() {
		givenAccount(AccountStatus.ACTIVE);
		givenCredential(CredentialStatus.ACTIVE);

		assertThatThrownBy(() -> service.reissue(INSTALLATION_ID, new DeviceSecret("wrong-secret")))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.DEVICE_CREDENTIAL_INVALID);
	}

	@Test
	@DisplayName("secret은 맞지만 installationId가 다르면 거절한다")
	void rejectsInstallationIdMismatch() {
		givenAccount(AccountStatus.ACTIVE);
		givenCredential(CredentialStatus.ACTIVE);

		assertThatThrownBy(() -> service.reissue("install-b", RAW_SECRET))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.DEVICE_CREDENTIAL_INVALID);
	}

	@Test
	@DisplayName("REVOKED 자격증명은 거절한다")
	void rejectsRevokedCredential() {
		givenAccount(AccountStatus.ACTIVE);
		givenCredential(CredentialStatus.REVOKED);

		assertThatThrownBy(() -> service.reissue(INSTALLATION_ID, RAW_SECRET))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.DEVICE_CREDENTIAL_INVALID);
	}

	@Test
	@DisplayName("차단된 계정은 자격증명이 맞아도 재발급을 거절한다")
	void rejectsBlockedAccount() {
		givenAccount(AccountStatus.BLOCKED);
		givenCredential(CredentialStatus.ACTIVE);

		assertThatThrownBy(() -> service.reissue(INSTALLATION_ID, RAW_SECRET))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ACCOUNT_NOT_ACTIVE);
	}

	@Test
	@DisplayName("삭제된 계정은 자격증명이 맞아도 재발급을 거절한다")
	void rejectsDeletedAccount() {
		givenAccount(AccountStatus.DELETED);
		givenCredential(CredentialStatus.ACTIVE);

		assertThatThrownBy(() -> service.reissue(INSTALLATION_ID, RAW_SECRET))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ACCOUNT_NOT_ACTIVE);
	}

	private void givenAccount(AccountStatus status) {
		Instant deletedAt = status == AccountStatus.DELETED ? NOW : null;
		accountRepository.store(Account.restore(
			USER_ID, AccountRole.USER, status, "KR", "KR-TEST", "ko-KR", "Asia/Seoul", "바람", deletedAt));
	}

	private void givenCredential(CredentialStatus status) {
		Instant revokedAt = status == CredentialStatus.REVOKED ? NOW : null;
		credentialRepository.store(DeviceCredential.restore(
			CREDENTIAL_ID,
			USER_ID,
			INSTALLATION_ID,
			secretHasher.hash(RAW_SECRET),
			DevicePlatform.IOS,
			status,
			NOW.minusSeconds(3600),
			NOW.minusSeconds(3600),
			revokedAt));
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

	private static final class FakeDeviceCredentialRepository implements DeviceCredentialRepository {

		private DeviceCredential stored;

		void store(DeviceCredential credential) {
			this.stored = credential;
		}

		@Override
		public DeviceCredential save(DeviceCredential credential) {
			this.stored = credential;
			return credential;
		}

		@Override
		public DeviceCredential updateLastUsedAt(DeviceCredential credential) {
			this.stored = credential;
			return credential;
		}

		@Override
		public Optional<DeviceCredential> findBySecretHash(SecretHash secretHash) {
			return stored != null && stored.getSecretHash().equals(secretHash)
				? Optional.of(stored)
				: Optional.empty();
		}

		@Override
		public Optional<DeviceCredential> findActiveByInstallationId(String installationId) {
			return stored != null && stored.getInstallationId().equals(installationId) && stored.isActive()
				? Optional.of(stored)
				: Optional.empty();
		}

	}

}
