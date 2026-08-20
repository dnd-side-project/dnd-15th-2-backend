package com.dnd.qello.account.service;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;

import lombok.RequiredArgsConstructor;

/**
 * 여러 기능이 공유하는 계정 자격(ACTIVE USER) 게이트다. 예외를 직접 던지지 않고
 * 호출부가 준 supplier로 던진다 — {@code account}가 호출부의 오류 코드 체계를
 * 알 필요가 없고, 모듈마다 계정 없음·자격 없음을 자기 코드로 번역할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class AccountEligibilityGate {

	private final AccountRepository accountRepository;

	public Account requireActiveUser(
		long accountId, Supplier<? extends RuntimeException> notFound, Supplier<? extends RuntimeException> notEligible) {
		Account account = accountRepository.findById(accountId).orElseThrow(notFound);
		if (account.getRole() != AccountRole.USER || account.getStatus() != AccountStatus.ACTIVE) {
			throw notEligible.get();
		}
		return account;
	}
}
