/**
 * Created at: 2026-08-20T15:05:00+09:00
 * Source scenario: TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ-UNIT-015
 */
package com.dnd.qello.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;

@ExtendWith(MockitoExtension.class)
class AccountEligibilityGateTest {

	private static final long ACCOUNT_ID = 7L;

	@Mock private com.dnd.qello.account.service.AccountEligibilityGate delegate;

	private AccountEligibilityGate adapter;

	@BeforeEach
	void setUp() {
		adapter = new AccountEligibilityGate(delegate);
	}

	@Test
	@DisplayName("계정 없음은 승격된 게이트가 던진 notFound supplier를 통해 FED-APP-001(404)로 번역된다")
	void translatesNotFoundToFedApp001() {
		doAnswer(invocation -> {
			Supplier<RuntimeException> notFound = invocation.getArgument(1);
			throw notFound.get();
		}).when(delegate).requireActiveUser(eq(ACCOUNT_ID), any(), any());

		assertThatThrownBy(() -> adapter.require(ACCOUNT_ID))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INBOX_ACCOUNT_NOT_FOUND);
	}

	@Test
	@DisplayName("자격 없음은 승격된 게이트가 던진 notEligible supplier를 통해 FED-APP-002(403)로 번역된다")
	void translatesNotEligibleToFedApp002() {
		doAnswer(invocation -> {
			Supplier<RuntimeException> notEligible = invocation.getArgument(2);
			throw notEligible.get();
		}).when(delegate).requireActiveUser(eq(ACCOUNT_ID), any(), any());

		assertThatThrownBy(() -> adapter.require(ACCOUNT_ID))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INBOX_ACCOUNT_NOT_ELIGIBLE);
	}

	@Test
	@DisplayName("FED-APP-001과 FED-APP-002의 HTTP 상태와 코드 문자열은 게이트 승격 전과 같다")
	void keepsHttpStatusAndCodeStrings() {
		assertThat(FeedErrorCode.INBOX_ACCOUNT_NOT_FOUND.code()).isEqualTo("FED-APP-001");
		assertThat(FeedErrorCode.INBOX_ACCOUNT_NOT_FOUND.httpStatus().value()).isEqualTo(404);
		assertThat(FeedErrorCode.INBOX_ACCOUNT_NOT_ELIGIBLE.code()).isEqualTo("FED-APP-002");
		assertThat(FeedErrorCode.INBOX_ACCOUNT_NOT_ELIGIBLE.httpStatus().value()).isEqualTo(403);
	}
}
