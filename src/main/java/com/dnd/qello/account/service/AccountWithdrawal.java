package com.dnd.qello.account.service;

import java.time.Duration;
import java.time.Instant;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountStatus;

/** 탈퇴 요청·철회 결과. scheduledDeletionAt은 유예 중일 때만 값이 있다. */
public record AccountWithdrawal(AccountStatus status, Instant scheduledDeletionAt) {

	static AccountWithdrawal of(Account account, Duration gracePeriod) {
		return new AccountWithdrawal(account.getStatus(), account.withdrawalDeadline(gracePeriod));
	}

}
