package com.dnd.qello.feed.service;

import org.springframework.stereotype.Component;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;

import lombok.RequiredArgsConstructor;

/**
 * feed 하위 application service가 공유하는 계정 자격 게이트(ACTIVE USER)다.
 * 수신함과 새 읽기·상호작용 경로가 각자 이 검사를 구현하면 기준이 갈라진다 —
 * {@link InboxApplicationService}와 {@link FeedInteractionApplicationService}가
 * 이 한 곳만 호출한다.
 */
@Component
@RequiredArgsConstructor
class AccountEligibilityGate {

	private final AccountRepository accountRepository;

	void require(long accountId) {
		Account account = accountRepository.findById(accountId)
			.orElseThrow(() -> new FeedException(FeedErrorCode.INBOX_ACCOUNT_NOT_FOUND));
		if (account.getRole() != AccountRole.USER || account.getStatus() != AccountStatus.ACTIVE) {
			throw new FeedException(FeedErrorCode.INBOX_ACCOUNT_NOT_ELIGIBLE);
		}
	}
}
