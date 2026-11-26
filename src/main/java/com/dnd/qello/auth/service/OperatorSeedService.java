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
import com.dnd.qello.auth.security.OperatorPasswordPolicy;
import com.dnd.qello.auth.security.PasswordHash;
import com.dnd.qello.auth.security.PasswordHasher;
import com.dnd.qello.auth.security.RawPassword;

import lombok.RequiredArgsConstructor;

// 초기 운영자 계정을 만든다.
//
// 계정과 자격증명이 서로 다른 애그리거트라 두 번 저장하지만, 계정만 남고 자격증명이
// 없는 상태는 로그인 불가라 하나의 트랜잭션으로 묶는다.
//
// 새로 만들 때만 비밀번호 작성 규칙을 검사한다(#376). 이미 있는 운영자에게도 검사하면 규칙 도입 전에 시드한
// 환경이 기동에 실패한다. 이미 있는 운영자의 비밀번호는 다음 로그인에서 변경을 요구받는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperatorSeedService {

	private final AccountRepository accountRepository;
	private final OperatorCredentialRepository credentialRepository;
	private final PasswordHasher passwordHasher;
	private final Clock clock;

	/**
	 * 해당 loginId의 운영자가 없으면 만든다. 이미 있으면 아무것도 하지 않는다.
	 *
	 * @return 새로 만들었으면 true
	 * @throws com.dnd.qello.auth.error.AuthException
	 *             새로 만들 비밀번호가 작성 규칙을 어기면 AUT-VAL-005. 계정과 자격증명을 저장하지 않는다
	 */
	@Transactional
	public boolean seedIfAbsent(
			LoginId loginId,
			RawPassword rawPassword,
			String nickname,
			String coarseRegionCode,
			String locale,
			String timezone) {
		if (credentialRepository.findByLoginId(loginId).isPresent()) {
			return false;
		}
		OperatorPasswordPolicy.validate(loginId, rawPassword);

		Account operator = accountRepository.save(
				Account.createOperator(coarseRegionCode, locale, timezone, nickname));
		PasswordHash passwordHash = passwordHasher.hash(rawPassword);
		credentialRepository.save(OperatorCredential.issue(
				operator.getId(), loginId, passwordHash, Instant.now(clock)));
		return true;
	}

}
