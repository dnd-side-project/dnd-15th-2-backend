/*
 * Created at: 2026-10-09T18:14:46+09:00
 * Source scenario: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-015
 */
package com.dnd.qello.account.service;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.repository.AccountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AccountStatusServiceTest {

	private static final long USER_ID = 42L;
	private static final String NICKNAME = "바람";

	private AccountRepository accountRepository;
	private AccountStatusService service;

	@BeforeEach
	void setUp() {
		accountRepository = mock(AccountRepository.class);
		when(accountRepository.updateStatus(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
		service = new AccountStatusService(accountRepository);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-015: 차단은 BLOCKED 상태만 저장하고 닉네임을 유지한다")
	void blockStoresBlockedStatus() {
		givenAccount(account(AccountStatus.ACTIVE, null));

		AccountStatus result = service.block(USER_ID);

		assertThat(result).isEqualTo(AccountStatus.BLOCKED);
		ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
		verify(accountRepository).findById(USER_ID);
		verify(accountRepository).updateStatus(saved.capture());
		verifyNoMoreInteractions(accountRepository);
		assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.BLOCKED);
		assertThat(saved.getValue().getNickname()).isEqualTo(NICKNAME);
		assertThat(saved.getValue().getDeletedAt()).isNull();
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-015: 해제는 ACTIVE 상태만 저장한다")
	void unblockStoresActiveStatus() {
		givenAccount(account(AccountStatus.BLOCKED, null));

		AccountStatus result = service.unblock(USER_ID);

		assertThat(result).isEqualTo(AccountStatus.ACTIVE);
		ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
		verify(accountRepository).findById(USER_ID);
		verify(accountRepository).updateStatus(saved.capture());
		verifyNoMoreInteractions(accountRepository);
		assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-015: 차단 서비스는 계정 저장소 외 의존이 없어 자격증명 폐기 포트를 호출할 수 없다")
	void dependsOnlyOnAccountRepository() {
		Constructor<?>[] constructors = AccountStatusService.class.getConstructors();

		assertThat(constructors).hasSize(1);
		assertThat(Arrays.asList(constructors[0].getParameterTypes())).containsExactly(AccountRepository.class);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-015: 유예 중 계정 차단은 409이고 저장하지 않는다")
	void blockRejectsWithdrawalPendingAccount() {
		givenAccount(account(AccountStatus.WITHDRAWAL_PENDING, Instant.parse("2026-10-01T00:00:00Z")));

		assertThatThrownBy(() -> service.block(USER_ID))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_STATUS_TRANSITION);
		verify(accountRepository, never()).updateStatus(any(Account.class));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-015: 차단되지 않은 계정 해제는 409이고 저장하지 않는다")
	void unblockRejectsActiveAccount() {
		givenAccount(account(AccountStatus.ACTIVE, null));

		assertThatThrownBy(() -> service.unblock(USER_ID))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_STATUS_TRANSITION);
		verify(accountRepository, never()).updateStatus(any(Account.class));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-015: 계정이 없으면 ACCOUNT_NOT_FOUND다")
	void rejectsMissingAccount() {
		when(accountRepository.findById(USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.block(USER_ID))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.ACCOUNT_NOT_FOUND);
		assertThatThrownBy(() -> service.unblock(USER_ID))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.ACCOUNT_NOT_FOUND);
	}

	private void givenAccount(Account account) {
		when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
	}

	private static Account account(AccountStatus status, Instant withdrawalRequestedAt) {
		return Account.restore(USER_ID, AccountRole.USER, status, "KR", "KR-11", "ko-KR", "Asia/Seoul", NICKNAME,
				null, withdrawalRequestedAt);
	}

}
