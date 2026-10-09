/*
 * Created at: 2026-08-07T20:52:09+09:00
 * Source scenario: TEST-PLAN-GH-73-DEVICE-REGISTRATION-UNIT-001 through UNIT-004,
 * TEST-PLAN-GH-88-COUNTRY-ONBOARDING-UNIT-001 through UNIT-005,
 * TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-001 through UNIT-003,
 * TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-UNIT-009
 *
 * import 수가 많아 클래스 선언 위에 두면 정책 검사 범위(첫 30줄)를 벗어나므로 여기에 배치.
 */
package com.dnd.qello.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import com.dnd.qello.account.config.NicknameChangeProperties;
import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.account.service.NicknameRegistrationService;
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
import com.dnd.qello.common.ratelimit.RateLimitPolicy;
import com.dnd.qello.filtering.moderation.ModerationLanguage;
import com.dnd.qello.filtering.moderation.NicknameModerationChecker;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome.Reason;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DeviceRegistrationServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-07T09:00:00Z");
	private static final String SECRET = "test-only-access-token-signing-key-32-bytes-min";
	private static final String ZWSP = Character.toString(0x200B);

	private FakeAccountRepository accountRepository;
	private FakeDeviceCredentialRepository credentialRepository;
	private ConfigurableNicknameModerationChecker moderationChecker;
	private DeviceRegistrationService service;

	@BeforeEach
	void setUp() {
		accountRepository = new FakeAccountRepository();
		credentialRepository = new FakeDeviceCredentialRepository();
		moderationChecker = new ConfigurableNicknameModerationChecker(NicknameModerationOutcome.allowed());
		SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		AccessTokenIssuer accessTokenIssuer = new AccessTokenIssuer(
				jwtEncoder,
				new AccessTokenProperties("qello", "qello-app", 1800, SECRET),
				Clock.fixed(NOW, ZoneOffset.UTC));

		service = new DeviceRegistrationService(
				accountRepository,
				new FakeCountryCatalogRepository(),
				credentialRepository,
				new NicknameRegistrationService(
						accountRepository,
						moderationChecker,
						new NicknameChangeProperties(Duration.ofDays(30), new RateLimitPolicy(10, Duration.ofDays(1))),
						mock(PlatformTransactionManager.class),
						Clock.fixed(NOW, ZoneOffset.UTC)),
				new DeviceSecretGenerator(),
				new DeviceSecretHasher(),
				accessTokenIssuer,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("UNIT-013: 닉네임이 이미 존재하면 계정과 자격증명 모두 생성하지 않는다")
	void rejectsRegistrationWhenNicknameAlreadyExists() {
		service.register("install-a", DevicePlatform.IOS, "KR", "ko-KR", "Asia/Seoul", "바람");

		assertThatThrownBy(() -> service.register(
				"install-b", DevicePlatform.IOS, "KR", "ko-KR", "Asia/Seoul", "바람"))
				.isInstanceOf(com.dnd.qello.account.error.AccountException.class);
		assertThat(accountRepository.accounts).hasSize(1);
		assertThat(credentialRepository.findActiveByInstallationId("install-b")).isEmpty();
	}

	@Test
	@DisplayName("UNIT-014: 닉네임이 null이면 중복·moderation 검사를 건너뛰고 정상 등록한다")
	void skipsNicknameChecksWhenNicknameIsNull() {
		DeviceRegistrationResult result = service.register(
				"install-a", DevicePlatform.IOS, "KR", "ko-KR", "Asia/Seoul", null);

		assertThat(result.userId()).isPositive();
		assertThat(moderationChecker.callCount).isZero();
	}

	@Test
	@DisplayName("UNIT-015: moderation이 거부하면 계정과 자격증명 모두 생성하지 않는다")
	void rejectsRegistrationWhenModerationRejectsNickname() {
		moderationChecker.outcome = NicknameModerationOutcome.rejected(Reason.BLOCKED_BY_PRIMARY);

		assertThatThrownBy(() -> service.register(
				"install-a", DevicePlatform.IOS, "KR", "ko-KR", "Asia/Seoul", "부적절한닉네임"))
				.isInstanceOf(com.dnd.qello.account.error.AccountException.class);
		assertThat(accountRepository.accounts).isEmpty();
		assertThat(credentialRepository.byId).isEmpty();
	}

	@Test
	@DisplayName("#317 UNIT-009: 정규화하면 비는 닉네임은 REQUIRED_VALUE_MISSING이고 계정·자격증명을 만들지 않으며 moderation을 부르지 않는다")
	void rejectsRegistrationWhenNicknameBecomesEmpty() {
		assertThatThrownBy(() -> service.register(
				"install-a", DevicePlatform.IOS, "KR", "ko-KR", "Asia/Seoul", ZWSP))
				.isInstanceOf(com.dnd.qello.account.error.AccountException.class)
				.hasFieldOrPropertyWithValue(
						"errorCode", com.dnd.qello.account.error.AccountErrorCode.REQUIRED_VALUE_MISSING);
		assertThat(accountRepository.accounts).isEmpty();
		assertThat(credentialRepository.byId).isEmpty();
		assertThat(moderationChecker.callCount).isZero();
	}

	@Test
	@DisplayName("#317 UNIT-009: 보이지 않는 문자가 섞인 닉네임은 정규화한 값으로 저장한다")
	void storesNormalizedNicknameOnRegistration() {
		DeviceRegistrationResult result = service.register(
				"install-a", DevicePlatform.IOS, "KR", "ko-KR", "Asia/Seoul", "바람" + ZWSP);

		assertThat(accountRepository.accounts.get(result.userId()).getNickname()).isEqualTo("바람");
	}

	@Test
	@DisplayName("등록에 성공하면 계정을 만들고 평문 시크릿과 액세스 토큰을 돌려준다")
	void registersNewDeviceAndAccount() {
		DeviceRegistrationResult result = service.register(
				"install-a", DevicePlatform.IOS, "KR", "ko-KR", "Asia/Seoul", "바람");

		assertThat(result.userId()).isPositive();
		assertThat(result.deviceSecret().value()).isNotBlank();
		assertThat(result.accessToken().value()).isNotBlank();
		assertThat(accountRepository.findById(result.userId())).isPresent();
	}

	@Test
	@DisplayName("저장된 자격증명은 요청한 installationId와 발급 시각을 그대로 갖는다")
	void storesCredentialWithRequestedInstallationId() {
		service.register("install-a", DevicePlatform.ANDROID, "KR", "ko-KR", "Asia/Seoul", "바람");

		DeviceCredential stored = credentialRepository.findActiveByInstallationId("install-a").orElseThrow();
		assertThat(stored.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
		assertThat(stored.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("이미 ACTIVE 자격증명이 있는 installationId는 재등록을 거절한다")
	void rejectsReRegistrationOfActiveInstallation() {
		service.register("install-a", DevicePlatform.IOS, "KR", "ko-KR", "Asia/Seoul", "바람");

		assertThatThrownBy(
				() -> service.register("install-a", DevicePlatform.IOS, "KR", "ko-KR", "Asia/Seoul", "다른닉네임"))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.DEVICE_ALREADY_REGISTERED);
	}

	@Test
	@DisplayName("저장된 자격증명에는 평문 시크릿의 해시만 남는다")
	void storesOnlyHashedSecret() {
		DeviceRegistrationResult result = service.register(
				"install-a", DevicePlatform.IOS, "KR", "ko-KR", "Asia/Seoul", "바람");

		DeviceCredential stored = credentialRepository.findActiveByInstallationId("install-a").orElseThrow();
		assertThat(stored.getSecretHash().value()).doesNotContain(result.deviceSecret().value());
	}

	@Test
	@DisplayName("UNIT-001: 국가 코드는 대문자로 정규화되어 국가와 기준 지역 모두에 저장된다")
	void normalizesCountryCodeBeforeAccountCreation() {
		DeviceRegistrationResult result = service.register(
				"install-a", DevicePlatform.IOS, "kr", "ko-KR", "Asia/Seoul", "바람");

		Account account = accountRepository.findById(result.userId()).orElseThrow();
		assertThat(account.getCountryCode()).isEqualTo("KR");
		assertThat(account.getCoarseRegionCode()).isEqualTo("KR");
	}

	@Test
	@DisplayName("국가가 없으면 계정과 자격증명을 만들지 않는다")
	void rejectsMissingCountryBeforePersistence() {
		assertThatThrownBy(() -> service.register(
				"install-a", DevicePlatform.IOS, null, "ko-KR", "Asia/Seoul", "바람"))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.REQUIRED_VALUE_MISSING);

		assertThat(accountRepository.accounts).isEmpty();
		assertThat(credentialRepository.byId).isEmpty();
	}

	@Test
	@DisplayName("UNIT-002: 지원하지 않는 국가는 계정과 자격증명을 만들지 않고 등록을 거절한다")
	void rejectsUnsupportedCountryBeforePersistence() {
		assertThatThrownBy(() -> service.register(
				"install-a", DevicePlatform.IOS, "US", "ko-KR", "Asia/Seoul", "바람"))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_COUNTRY_CODE);

		assertThat(accountRepository.accounts).isEmpty();
		assertThat(credentialRepository.byId).isEmpty();
	}

	@Test
	@DisplayName("UNIT-003: 형식이 틀리거나 공백뿐인 국가 코드는 계정과 자격증명을 만들지 않고 거절한다")
	void rejectsMalformedOrBlankCountryBeforePersistence() {
		assertThatThrownBy(() -> service.register(
				"install-a", DevicePlatform.IOS, "KOR", "ko-KR", "Asia/Seoul", "바람"))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_COUNTRY_CODE);
		assertThatThrownBy(() -> service.register(
				"install-b", DevicePlatform.IOS, "1A", "ko-KR", "Asia/Seoul", "바람"))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_COUNTRY_CODE);
		assertThatThrownBy(() -> service.register(
				"install-c", DevicePlatform.IOS, "   ", "ko-KR", "Asia/Seoul", "바람"))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.REQUIRED_VALUE_MISSING);

		assertThat(accountRepository.accounts).isEmpty();
		assertThat(credentialRepository.byId).isEmpty();
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
					account.getCountryCode(),
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
			return accounts.values().stream()
					.anyMatch(account -> account.getNickname() != null
							&& account.getNickname().equalsIgnoreCase(nickname)
							&& account.getDeletedAt() == null);
		}

		@Override
		public List<Long> findWithdrawalDueIds(Instant requestedAtOrBefore, int limit) {
			throw new UnsupportedOperationException();
		}

	}

	private static final class ConfigurableNicknameModerationChecker implements NicknameModerationChecker {
		private NicknameModerationOutcome outcome;
		private int callCount;

		private ConfigurableNicknameModerationChecker(NicknameModerationOutcome outcome) {
			this.outcome = outcome;
		}

		@Override
		public NicknameModerationOutcome check(String nickname, ModerationLanguage language) {
			callCount++;
			return outcome;
		}
	}

	private static final class FakeCountryCatalogRepository
			implements
				com.dnd.qello.account.repository.CountryCatalogRepository {

		@Override
		public boolean existsCountry(String countryCode) {
			return "KR".equals(countryCode);
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
					.filter(credential -> credential.getInstallationId().equals(installationId)
							&& credential.isActive())
					.findFirst();
		}

		@Override
		public int revokeAllActiveByUserId(long userId, Instant revokedAt) {
			throw new UnsupportedOperationException();
		}

	}

}
