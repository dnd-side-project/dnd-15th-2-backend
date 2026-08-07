package com.dnd.qello.auth.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.domain.OperatorCredential;
import com.dnd.qello.auth.repository.OperatorCredentialRepository;
import com.dnd.qello.auth.security.PasswordHash;
import com.dnd.qello.auth.security.PasswordHasher;
import com.dnd.qello.auth.security.RawPassword;

// 초기 운영자 계정을 만든다.
//
// 계정과 자격증명이 서로 다른 애그리거트라 두 번 저장하지만, 계정만 남고 자격증명이
// 없는 상태는 로그인 불가라 하나의 트랜잭션으로 묶는다.
@Service
public class OperatorSeedService {

	private final AccountRepository accountRepository;
	private final OperatorCredentialRepository credentialRepository;
	private final PasswordHasher passwordHasher;
	private final Clock clock;

	public OperatorSeedService(
		AccountRepository accountRepository,
		OperatorCredentialRepository credentialRepository,
		PasswordHasher passwordHasher,
		Clock clock
	) {
		this.accountRepository = accountRepository;
		this.credentialRepository = credentialRepository;
		this.passwordHasher = passwordHasher;
		this.clock = clock;
	}

	/**
	 * 해당 loginId의 운영자가 없으면 만든다. 이미 있으면 아무것도 하지 않는다.
	 *
	 * @return 새로 만들었으면 true
	 */
	@Transactional
	public boolean seedIfAbsent(
		LoginId loginId,
		RawPassword rawPassword,
		String nickname,
		String coarseRegionCode,
		String locale,
		String timezone
	) {
		if (credentialRepository.findByLoginId(loginId).isPresent()) {
			return false;
		}

		Account operator = accountRepository.save(
			Account.createOperator(coarseRegionCode, locale, timezone, nickname));
		PasswordHash passwordHash = passwordHasher.hash(rawPassword);
		credentialRepository.save(OperatorCredential.issue(
			operator.getId(), loginId, passwordHash, Instant.now(clock)));
		return true;
	}

}
