package com.dnd.qello.account.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.config.AccountWithdrawalProperties;
import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.repository.AccountRepository;

import lombok.RequiredArgsConstructor;

/**
 * 앱 사용자의 탈퇴 요청과 철회(#337). 대상 계정은 언제나 인증 주체 자신이다.
 *
 * <p>
 * 같은 계정을 동시에 바꾸는 요청은 Account의 @Version이 한쪽을 409로 거절한다. 요청 트랜잭션 안에서 다른 모듈의 정리까지
 * 끝내므로 정리가 실패하면 상태 변경도 남지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountWithdrawalService {

	private final AccountRepository accountRepository;
	private final List<AccountWithdrawalCleanup> cleanups;
	private final AccountWithdrawalProperties properties;
	private final Clock clock;

	/** 이미 유예 중이면 아무것도 바꾸지 않고 처음 정해진 삭제 예정 시각을 돌려준다. */
	@Transactional
	public AccountWithdrawal request(long userId) {
		Account account = findAccount(userId);
		if (account.getStatus() == AccountStatus.WITHDRAWAL_PENDING) {
			return AccountWithdrawal.of(account, properties.gracePeriod());
		}
		Instant requestedAt = clock.instant();
		Account requested = accountRepository.updateStatus(account.requestWithdrawal(requestedAt));
		cleanups.forEach(cleanup -> cleanup.onWithdrawalRequested(userId, requestedAt));
		return AccountWithdrawal.of(requested, properties.gracePeriod());
	}

	/** ACTIVE 계정은 아무것도 바꾸지 않는다. 푸시 등록과 위치는 앱이 철회 후 다시 보낸다. */
	@Transactional
	public AccountWithdrawal cancel(long userId) {
		Account account = findAccount(userId);
		if (account.getStatus() == AccountStatus.ACTIVE) {
			return AccountWithdrawal.of(account, properties.gracePeriod());
		}
		Account cancelled = account.cancelWithdrawal(clock.instant(), properties.gracePeriod());
		return AccountWithdrawal.of(accountRepository.updateStatus(cancelled), properties.gracePeriod());
	}

	private Account findAccount(long userId) {
		return accountRepository.findById(userId)
				.orElseThrow(() -> new AccountException(
						AccountErrorCode.ACCOUNT_NOT_FOUND, "userId", "대상 계정이 존재하지 않습니다"));
	}

}
