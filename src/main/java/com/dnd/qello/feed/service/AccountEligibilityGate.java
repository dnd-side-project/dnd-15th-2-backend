package com.dnd.qello.feed.service;

import org.springframework.stereotype.Component;

import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;

import lombok.RequiredArgsConstructor;

/**
 * feed 하위 application service가 공유하는 계정 자격 게이트(ACTIVE USER) 어댑터다.
 * 실제 판정은 {@link com.dnd.qello.account.service.AccountEligibilityGate}에 있다 —
 * 승격된 게이트가 한 곳에만 있어야 기준이 갈라지지 않는다(#170 결정 7). 이 어댑터는
 * feed 오류 코드로 번역만 하고, 기존 {@code require(long)} 시그니처를 유지해
 * {@link InboxApplicationService}와 {@link FeedInteractionApplicationService}의
 * 호출부를 바꾸지 않는다.
 */
// 빈 이름이 account.service.AccountEligibilityGate와 같은 단순 클래스명으로 충돌하므로
// 명시적으로 구분한다.
@Component("feedAccountEligibilityGate")
@RequiredArgsConstructor
class AccountEligibilityGate {

	private final com.dnd.qello.account.service.AccountEligibilityGate delegate;

	void require(long accountId) {
		delegate.requireActiveUser(
			accountId,
			() -> new FeedException(FeedErrorCode.INBOX_ACCOUNT_NOT_FOUND),
			() -> new FeedException(FeedErrorCode.INBOX_ACCOUNT_NOT_ELIGIBLE));
	}
}
