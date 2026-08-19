package com.dnd.qello.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.repository.CountryCatalogRepository;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.account.service.NicknameRegistrationService;
import com.dnd.qello.auth.domain.DeviceCredential;
import com.dnd.qello.auth.domain.DevicePlatform;
import com.dnd.qello.auth.domain.SecretHash;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;
import com.dnd.qello.auth.repository.DeviceCredentialRepository;
import com.dnd.qello.auth.security.DeviceSecret;
import com.dnd.qello.auth.security.DeviceSecretGenerator;
import com.dnd.qello.auth.security.DeviceSecretHasher;
import com.dnd.qello.auth.token.AccessTokenIssuer;
import com.dnd.qello.auth.token.IssuedAccessToken;

// 기기 최초 등록. 계정 생성과 자격증명 발급을 한 트랜잭션으로 묶는다.
//
// 계정만 만들어지고 자격증명이 없는 상태가 남으면 그 계정은 영원히 로그인할 수 없다.
// 근거는 docs/product/AUTH_DESIGN.md 4.3절에 있다.
@Service
public class DeviceRegistrationService {

	private static final Pattern COUNTRY_CODE_PATTERN = Pattern.compile("^[A-Z]{2}$");

	private final AccountRepository accountRepository;
	private final CountryCatalogRepository countryCatalogRepository;
	private final DeviceCredentialRepository credentialRepository;
	private final NicknameRegistrationService nicknameRegistrationService;
	private final DeviceSecretGenerator secretGenerator;
	private final DeviceSecretHasher secretHasher;
	private final AccessTokenIssuer accessTokenIssuer;
	private final Clock clock;

	public DeviceRegistrationService(
		AccountRepository accountRepository,
		CountryCatalogRepository countryCatalogRepository,
		DeviceCredentialRepository credentialRepository,
		NicknameRegistrationService nicknameRegistrationService,
		DeviceSecretGenerator secretGenerator,
		DeviceSecretHasher secretHasher,
		AccessTokenIssuer accessTokenIssuer,
		Clock clock
	) {
		this.accountRepository = accountRepository;
		this.countryCatalogRepository = countryCatalogRepository;
		this.credentialRepository = credentialRepository;
		this.nicknameRegistrationService = nicknameRegistrationService;
		this.secretGenerator = secretGenerator;
		this.secretHasher = secretHasher;
		this.accessTokenIssuer = accessTokenIssuer;
		this.clock = clock;
	}

	@Transactional
	public DeviceRegistrationResult register(
		String installationId,
		DevicePlatform platform,
		String countryCode,
		String coarseRegionCode,
		String locale,
		String timezone,
		String nickname
	) {
		// 체크-후-삽입이라 경합 창이 남는다. 동시에 같은 installation_id로 등록되는
		// 드문 race는 uq_active_device_installation과 ConstraintExceptionMapper가
		// 같은 오류 코드로 막는다.
		if (credentialRepository.findActiveByInstallationId(installationId).isPresent()) {
			throw new AuthException(
				AuthErrorCode.DEVICE_ALREADY_REGISTERED, "installationId", "이미 등록된 기기입니다");
		}

		String normalizedCountryCode = validateCountry(countryCode, coarseRegionCode);

		// 닉네임은 선택값이다(#48). 주어진 경우에만 중복·moderation을 검사한다 — 이
		// 검사는 계정·자격증명 생성과 같은 트랜잭션 안에서 실행된다(#168, 트레이드오프는
		// docs/test-plans/gh-168-...md §4·§7 참고). 공백만 있는 값은 어차피
		// Account.createUser가 REQUIRED_VALUE_MISSING으로 거부하므로, 그 앞에서
		// DB 조회와 외부 moderation 호출을 낭비하지 않는다.
		if (nickname != null && !nickname.isBlank()) {
			nicknameRegistrationService.ensureAvailable(nickname, locale);
		}

		Instant now = Instant.now(clock);
		Account account = accountRepository.save(
			Account.createUser(normalizedCountryCode, coarseRegionCode, locale, timezone, nickname));

		DeviceSecret rawSecret = secretGenerator.generate();
		SecretHash secretHash = secretHasher.hash(rawSecret);
		DeviceCredential credential = credentialRepository.save(
			DeviceCredential.issue(account.getId(), installationId, secretHash, platform, now));

		IssuedAccessToken issuedToken = accessTokenIssuer.issue(
			account.getId(), AccountRole.USER, credential.getId());

		return new DeviceRegistrationResult(account.getId(), rawSecret, issuedToken);
	}

	private String validateCountry(String countryCode, String coarseRegionCode) {
		if (countryCode == null || countryCode.isBlank()) {
			throw new AuthException(
				AuthErrorCode.REQUIRED_VALUE_MISSING, "countryCode", "countryCode는 필수입니다");
		}
		String normalized = countryCode.trim().toUpperCase(Locale.ROOT);
		if (!COUNTRY_CODE_PATTERN.matcher(normalized).matches()
			|| !countryCatalogRepository.existsCountry(normalized)) {
			throw invalidCountryCode();
		}

		List<String> countryAncestors = countryCatalogRepository.findCountryAncestors(coarseRegionCode);
		if (countryAncestors.size() != 1 || !normalized.equals(countryAncestors.getFirst())) {
			throw invalidCountryCode();
		}
		return normalized;
	}

	private AuthException invalidCountryCode() {
		return new AuthException(
			AuthErrorCode.INVALID_COUNTRY_CODE, "countryCode", "countryCode가 지원 국가와 일치하지 않습니다");
	}

}
