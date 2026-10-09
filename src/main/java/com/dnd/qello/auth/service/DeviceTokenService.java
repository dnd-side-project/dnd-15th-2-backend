package com.dnd.qello.auth.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.auth.domain.DeviceCredential;
import com.dnd.qello.auth.domain.SecretHash;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;
import com.dnd.qello.auth.repository.DeviceCredentialRepository;
import com.dnd.qello.auth.security.DeviceSecret;
import com.dnd.qello.auth.security.DeviceSecretHasher;
import com.dnd.qello.auth.token.AccessTokenIssuer;
import com.dnd.qello.auth.token.IssuedAccessToken;

import lombok.RequiredArgsConstructor;

// 토큰 재발급. 계정 차단이 세션이 아니라 이 경로에서 반영되는 지점이다.
//
// JWT는 stateless라 즉시 차단할 수 없다. 재발급마다 계정 상태를 확인해 반영 지연을
// access token TTL 이내로 묶는다. 근거는 docs/product/AUTH_DESIGN.md 4.4·4.6절에 있다.
//
// 탈퇴 유예 중인 계정도 재발급한다(#337). 같은 기기로 철회 API를 부를 수 있어야 하기 때문이다. ACTIVE가 필요한
// 다른 API는 각 서비스가 계속 거절한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceTokenService {

	private final AccountRepository accountRepository;
	private final DeviceCredentialRepository credentialRepository;
	private final DeviceSecretHasher secretHasher;
	private final AccessTokenIssuer accessTokenIssuer;
	private final Clock clock;

	/**
	 * installationId는 secret_hash 조회 결과의 교차 검증용이다.
	 */
	@Transactional
	public DeviceTokenReissue reissue(String installationId, DeviceSecret rawSecret) {
		SecretHash secretHash = secretHasher.hash(rawSecret);
		DeviceCredential credential = credentialRepository.findBySecretHash(secretHash)
				.filter(found -> found.getInstallationId().equals(installationId))
				.filter(DeviceCredential::isActive)
				.orElseThrow(this::credentialInvalid);

		Account account = accountRepository.findById(credential.getUserId())
				.orElseThrow(this::credentialInvalid);
		if (account.getStatus() != AccountStatus.ACTIVE && account.getStatus() != AccountStatus.WITHDRAWAL_PENDING) {
			throw new AuthException(AuthErrorCode.ACCOUNT_NOT_ACTIVE, null, "사용할 수 없는 계정입니다");
		}

		credentialRepository.updateLastUsedAt(credential.touch(Instant.now(clock)));
		IssuedAccessToken token = accessTokenIssuer.issue(account.getId(), account.getRole(), credential.getId());
		return new DeviceTokenReissue(token, account.getStatus());
	}

	private AuthException credentialInvalid() {
		return new AuthException(
				AuthErrorCode.DEVICE_CREDENTIAL_INVALID, null, "기기 자격증명이 유효하지 않습니다");
	}

}
