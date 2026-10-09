/*
 * Created at: 2026-10-09T18:14:46+09:00
 * Source scenario: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-010 through UNIT-013
 */
package com.dnd.qello.account.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dnd.qello.account.config.AccountWithdrawalProperties;
import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.auth.repository.DeviceCredentialRepository;
import com.dnd.qello.auth.service.DeviceCredentialWithdrawalCleanup;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.service.PresenceWithdrawalCleanup;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.service.PushDeviceWithdrawalCleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountWithdrawalServiceTest {

	private static final long USER_ID = 42L;
	private static final String NICKNAME = "바람";
	private static final Duration GRACE = Duration.ofDays(30);
	private static final Instant NOW = Instant.parse("2026-10-09T09:00:00Z");

	private AccountRepository accountRepository;
	private AccountWithdrawalCleanup pushCleanup;
	private AccountWithdrawalCleanup presenceCleanup;
	private AccountWithdrawalService service;

	@BeforeEach
	void setUp() {
		accountRepository = mock(AccountRepository.class);
		pushCleanup = mock(AccountWithdrawalCleanup.class);
		presenceCleanup = mock(AccountWithdrawalCleanup.class);
		when(accountRepository.updateStatus(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
		service = new AccountWithdrawalService(accountRepository, List.of(pushCleanup, presenceCleanup),
				new AccountWithdrawalProperties(GRACE), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-010: ACTIVE 계정 탈퇴 요청은 계정을 한 번 저장하고 정리 포트를 각각 한 번 호출한 뒤 삭제 예정 시각을 돌려준다")
	void requestStoresPendingAccountAndRunsCleanups() {
		givenAccount(account(AccountStatus.ACTIVE, null));

		AccountWithdrawal result = service.request(USER_ID);

		ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
		verify(accountRepository).updateStatus(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.WITHDRAWAL_PENDING);
		assertThat(saved.getValue().getWithdrawalRequestedAt()).isEqualTo(NOW);
		assertThat(saved.getValue().getNickname()).isEqualTo(NICKNAME);
		verify(pushCleanup).onWithdrawalRequested(USER_ID, NOW);
		verify(presenceCleanup).onWithdrawalRequested(USER_ID, NOW);
		verify(pushCleanup, never()).onWithdrawalCompleted(anyLong(), any());
		verify(presenceCleanup, never()).onWithdrawalCompleted(anyLong(), any());
		assertThat(result.status()).isEqualTo(AccountStatus.WITHDRAWAL_PENDING);
		assertThat(result.scheduledDeletionAt()).isEqualTo(NOW.plus(GRACE));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-010: 탈퇴 요청 정리 구현은 푸시 기기를 해지하고 위치를 지우며 기기 자격증명은 남긴다")
	void requestCleanupImplementationsRevokePushAndPresenceOnly() {
		NotificationRepository notificationRepository = mock(NotificationRepository.class);
		ActiveUserPresenceRepository presenceRepository = mock(ActiveUserPresenceRepository.class);
		DeviceCredentialRepository credentialRepository = mock(DeviceCredentialRepository.class);
		AccountWithdrawalService wired = new AccountWithdrawalService(accountRepository,
				List.of(new PushDeviceWithdrawalCleanup(notificationRepository),
						new PresenceWithdrawalCleanup(presenceRepository),
						new DeviceCredentialWithdrawalCleanup(credentialRepository)),
				new AccountWithdrawalProperties(GRACE), Clock.fixed(NOW, ZoneOffset.UTC));
		givenAccount(account(AccountStatus.ACTIVE, null));

		wired.request(USER_ID);

		verify(notificationRepository).revokeAllDevicesByUserId(USER_ID, NOW);
		verify(presenceRepository).deleteByUserId(USER_ID);
		verifyNoInteractions(credentialRepository);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-010: 계정이 없으면 ACCOUNT_NOT_FOUND이고 저장과 정리를 하지 않는다")
	void requestRejectsMissingAccount() {
		when(accountRepository.findById(USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.request(USER_ID))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.ACCOUNT_NOT_FOUND);
		verify(accountRepository, never()).updateStatus(any(Account.class));
		verifyNoInteractions(pushCleanup, presenceCleanup);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-011: 유예 중 계정 재요청은 저장·정리 없이 처음 정해진 삭제 예정 시각을 돌려준다(A1)")
	void repeatedRequestDoesNotWriteOrCleanUp() {
		Instant firstRequestedAt = NOW.minus(Duration.ofDays(3));
		givenAccount(account(AccountStatus.WITHDRAWAL_PENDING, firstRequestedAt));

		AccountWithdrawal result = service.request(USER_ID);

		verify(accountRepository, never()).updateStatus(any(Account.class));
		verifyNoInteractions(pushCleanup, presenceCleanup);
		assertThat(result.status()).isEqualTo(AccountStatus.WITHDRAWAL_PENDING);
		assertThat(result.scheduledDeletionAt()).isEqualTo(firstRequestedAt.plus(GRACE));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-012: 정리 포트가 실패하면 예외가 호출자에게 그대로 전파된다")
	void requestPropagatesCleanupFailure() {
		givenAccount(account(AccountStatus.ACTIVE, null));
		IllegalStateException failure = new IllegalStateException("presence cleanup failed");
		doThrow(failure).when(presenceCleanup).onWithdrawalRequested(USER_ID, NOW);

		assertThatThrownBy(() -> service.request(USER_ID)).isSameAs(failure);
		verify(accountRepository).updateStatus(any(Account.class));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-013: 삭제 예정 시각 전 철회는 ACTIVE로 저장하고 정리 포트를 부르지 않는다")
	void cancelBeforeDeadlineStoresActiveAccount() {
		givenAccount(account(AccountStatus.WITHDRAWAL_PENDING, NOW.minus(GRACE).plusSeconds(1)));

		AccountWithdrawal result = service.cancel(USER_ID);

		ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
		verify(accountRepository).updateStatus(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(saved.getValue().getWithdrawalRequestedAt()).isNull();
		assertThat(saved.getValue().getNickname()).isEqualTo(NICKNAME);
		assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(result.scheduledDeletionAt()).isNull();
		verifyNoInteractions(pushCleanup, presenceCleanup);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-013: 삭제 예정 시각이 지난 철회는 409이고 저장하지 않는다(A2)")
	void cancelAfterDeadlineIsRejected() {
		givenAccount(account(AccountStatus.WITHDRAWAL_PENDING, NOW.minus(GRACE)));

		assertThatThrownBy(() -> service.cancel(USER_ID))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_STATUS_TRANSITION);
		verify(accountRepository, never()).updateStatus(any(Account.class));
		verifyNoInteractions(pushCleanup, presenceCleanup);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-013: ACTIVE 계정 철회는 저장 없이 ACTIVE를 돌려준다(A2)")
	void cancelForActiveAccountDoesNotWrite() {
		givenAccount(account(AccountStatus.ACTIVE, null));

		AccountWithdrawal result = service.cancel(USER_ID);

		verify(accountRepository, never()).updateStatus(any(Account.class));
		verifyNoInteractions(pushCleanup, presenceCleanup);
		assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(result.scheduledDeletionAt()).isNull();
	}

	private void givenAccount(Account account) {
		when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
	}

	private static Account account(AccountStatus status, Instant withdrawalRequestedAt) {
		return Account.restore(USER_ID, AccountRole.USER, status, "KR", "KR-11", "ko-KR", "Asia/Seoul", NICKNAME,
				null, withdrawalRequestedAt);
	}

}
