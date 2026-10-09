package com.dnd.qello.account.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.repository.AccountRepository;

import lombok.RequiredArgsConstructor;

/**
 * 계정 차단과 해제 진입점(#337). 언제 누구를 차단할지와 감사 이력은 F08(신고·차단)이 정하고 이 서비스를 호출한다.
 *
 * <p>
 * 상태만 바꾸고 기기 자격증명은 폐기하지 않는다. 차단이 풀리면 같은 기기로 다시 쓸 수 있어야 하기 때문이다. 차단은 토큰 재발급에서
 * 반영되므로 이미 발급한 액세스 토큰의 TTL만큼 늦을 수 있다(AUTH_DESIGN 4.6절).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountStatusService {

	private final AccountRepository accountRepository;

	@Transactional
	public AccountStatus block(long userId) {
		Account account = findAccount(userId);
		return accountRepository.updateStatus(account.block()).getStatus();
	}

	@Transactional
	public AccountStatus unblock(long userId) {
		Account account = findAccount(userId);
		return accountRepository.updateStatus(account.unblock()).getStatus();
	}

	private Account findAccount(long userId) {
		return accountRepository.findById(userId)
				.orElseThrow(() -> new AccountException(
						AccountErrorCode.ACCOUNT_NOT_FOUND, "userId", "대상 계정이 존재하지 않습니다"));
	}

}
