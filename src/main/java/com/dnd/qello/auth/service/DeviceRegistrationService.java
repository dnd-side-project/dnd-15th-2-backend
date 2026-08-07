package com.dnd.qello.auth.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.repository.AccountRepository;
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

	private final AccountRepository accountRepository;
	private final DeviceCredentialRepository credentialRepository;
	private final DeviceSecretGenerator secretGenerator;
	private final DeviceSecretHasher secretHasher;
	private final AccessTokenIssuer accessTokenIssuer;
	private final Clock clock;

	public DeviceRegistrationService(
		AccountRepository accountRepository,
		DeviceCredentialRepository credentialRepository,
		DeviceSecretGenerator secretGenerator,
		DeviceSecretHasher secretHasher,
		AccessTokenIssuer accessTokenIssuer,
		Clock clock
	) {
		this.accountRepository = accountRepository;
		this.credentialRepository = credentialRepository;
		this.secretGenerator = secretGenerator;
		this.secretHasher = secretHasher;
		this.accessTokenIssuer = accessTokenIssuer;
		this.clock = clock;
	}

	@Transactional
	public DeviceRegistrationResult register(
		String installationId,
		DevicePlatform platform,
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

		Instant now = Instant.now(clock);
		Account account = accountRepository.save(
			Account.createUser(coarseRegionCode, locale, timezone, nickname));

		DeviceSecret rawSecret = secretGenerator.generate();
		SecretHash secretHash = secretHasher.hash(rawSecret);
		DeviceCredential credential = credentialRepository.save(
			DeviceCredential.issue(account.getId(), installationId, secretHash, platform, now));

		IssuedAccessToken issuedToken = accessTokenIssuer.issue(
			account.getId(), AccountRole.USER, credential.getId());

		return new DeviceRegistrationResult(account.getId(), rawSecret, issuedToken);
	}

}
