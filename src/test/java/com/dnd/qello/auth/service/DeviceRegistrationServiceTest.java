/*
 * Created at: 2026-08-07T20:52:09+09:00
 * Source scenario: TEST-PLAN-GH-73-DEVICE-REGISTRATION-UNIT-001 through UNIT-004
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
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.auth.domain.DeviceCredential;
import com.dnd.qello.auth.domain.DevicePlatform;
import com.dnd.qello.auth.domain.SecretHash;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;
import com.dnd.qello.auth.repository.DeviceCredentialRepository;
import com.dnd.qello.auth.security.DeviceSecretGenerator;
import com.dnd.qello.auth.security.DeviceSecretHasher;
import com.dnd.qello.auth.token.AccessTokenIssuer;
import com.dnd.qello.auth.token.AccessTokenProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

class DeviceRegistrationServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-07T09:00:00Z");
	private static final String SECRET = "test-only-access-token-signing-key-32-bytes-min";

	private FakeAccountRepository accountRepository;
	private FakeDeviceCredentialRepository credentialRepository;
	private DeviceRegistrationService service;

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

		service = new DeviceRegistrationService(
			accountRepository,
			credentialRepository,
			new DeviceSecretGenerator(),
			new DeviceSecretHasher(),
			accessTokenIssuer,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("등록에 성공하면 계정을 만들고 평문 시크릿과 액세스 토큰을 돌려준다")
	void registersNewDeviceAndAccount() {
		DeviceRegistrationResult result = service.register(
			"install-a", DevicePlatform.IOS, "KR-11", "ko-KR", "Asia/Seoul", "바람");

		assertThat(result.userId()).isPositive();
		assertThat(result.deviceSecret().value()).isNotBlank();
		assertThat(result.accessToken().value()).isNotBlank();
		assertThat(accountRepository.findById(result.userId())).isPresent();
	}

	@Test
	@DisplayName("저장된 자격증명은 요청한 installationId와 발급 시각을 그대로 갖는다")
	void storesCredentialWithRequestedInstallationId() {
		service.register("install-a", DevicePlatform.ANDROID, "KR-11", "ko-KR", "Asia/Seoul", "바람");

		DeviceCredential stored = credentialRepository.findActiveByInstallationId("install-a").orElseThrow();
		assertThat(stored.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
		assertThat(stored.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("이미 ACTIVE 자격증명이 있는 installationId는 재등록을 거절한다")
	void rejectsReRegistrationOfActiveInstallation() {
		service.register("install-a", DevicePlatform.IOS, "KR-11", "ko-KR", "Asia/Seoul", "바람");

		assertThatThrownBy(() ->
			service.register("install-a", DevicePlatform.IOS, "KR-11", "ko-KR", "Asia/Seoul", "다른닉네임"))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.DEVICE_ALREADY_REGISTERED);
	}

	@Test
	@DisplayName("저장된 자격증명에는 평문 시크릿의 해시만 남는다")
	void storesOnlyHashedSecret() {
		DeviceRegistrationResult result = service.register(
			"install-a", DevicePlatform.IOS, "KR-11", "ko-KR", "Asia/Seoul", "바람");

		DeviceCredential stored = credentialRepository.findActiveByInstallationId("install-a").orElseThrow();
		assertThat(stored.getSecretHash().value()).doesNotContain(result.deviceSecret().value());
	}

	private static final class FakeAccountRepository implements AccountRepository {

		private final Map<Long, Account> accounts = new HashMap<>();
		private long nextId = 1;

		@Override
		public Account save(Account account) {
			Account saved = Account.restore(
				nextId++,
				account.getRole(),
				account.getStatus(),
				account.getCoarseRegionCode(),
				account.getLocale(),
				account.getTimezone(),
				account.getNickname(),
				account.getDeletedAt());
			accounts.put(saved.getId(), saved);
			return saved;
		}

		@Override
		public Account updateProfile(Account account) {
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

	}

	private static final class FakeDeviceCredentialRepository implements DeviceCredentialRepository {

		private final Map<Long, DeviceCredential> byId = new HashMap<>();
		private long nextId = 1;

		@Override
		public DeviceCredential save(DeviceCredential credential) {
			DeviceCredential saved = DeviceCredential.restore(
				nextId++,
				credential.getUserId(),
				credential.getInstallationId(),
				credential.getSecretHash(),
				credential.getPlatform(),
				credential.getStatus(),
				credential.getLastUsedAt(),
				credential.getCreatedAt(),
				credential.getRevokedAt());
			byId.put(saved.getId(), saved);
			return saved;
		}

		@Override
		public DeviceCredential updateLastUsedAt(DeviceCredential credential) {
			byId.put(credential.getId(), credential);
			return credential;
		}

		@Override
		public Optional<DeviceCredential> findBySecretHash(SecretHash secretHash) {
			return byId.values().stream()
				.filter(credential -> credential.getSecretHash().equals(secretHash))
				.findFirst();
		}

		@Override
		public Optional<DeviceCredential> findActiveByInstallationId(String installationId) {
			return byId.values().stream()
				.filter(credential -> credential.getInstallationId().equals(installationId) && credential.isActive())
				.findFirst();
		}

	}

}
