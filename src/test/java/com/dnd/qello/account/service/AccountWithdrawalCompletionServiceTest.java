/*
 * Created at: 2026-10-09T18:14:46+09:00
 * Source scenario: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-014
 */
package com.dnd.qello.account.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import com.dnd.qello.account.config.AccountWithdrawalProperties;
import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.auth.repository.DeviceCredentialRepository;
import com.dnd.qello.auth.service.DeviceCredentialWithdrawalCleanup;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.service.PresenceWithdrawalCleanup;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.service.PushDeviceWithdrawalCleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountWithdrawalCompletionServiceTest {

	private static final long USER_ID = 42L;
	private static final Duration GRACE = Duration.ofDays(30);
	private static final Instant AT = Instant.parse("2026-10-09T09:00:00Z");
	private static final Instant DELETED_AT = Instant.parse("2026-09-01T00:00:00Z");

	private AccountRepository accountRepository;
	private AccountWithdrawalCleanup credentialCleanup;
	private AccountWithdrawalCleanup pushCleanup;
	private AccountWithdrawalCompletionService service;

	@BeforeEach
	void setUp() {
		accountRepository = mock(AccountRepository.class);
		credentialCleanup = mock(AccountWithdrawalCleanup.class);
		pushCleanup = mock(AccountWithdrawalCleanup.class);
		when(accountRepository.updateDeletion(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
		service = new AccountWithdrawalCompletionService(accountRepository, List.of(credentialCleanup, pushCleanup),
				new AccountWithdrawalProperties(GRACE));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-014: 유예가 끝난 계정만 DELETED·닉네임 null로 저장하고 자격증명·푸시 일괄 폐기 포트를 호출한다")
	void completesDueAccountAndRunsCleanups() {
		givenAccount(account(AccountStatus.WITHDRAWAL_PENDING, AT.minus(GRACE)));

		boolean completed = service.complete(USER_ID, AT);

		assertThat(completed).isTrue();
		ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
		verify(accountRepository).updateDeletion(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.DELETED);
		assertThat(saved.getValue().getDeletedAt()).isEqualTo(AT);
		assertThat(saved.getValue().getNickname()).isNull();
		assertThat(saved.getValue().getWithdrawalRequestedAt()).isNull();
		verify(accountRepository, never()).updateStatus(any(Account.class));
		verify(credentialCleanup).onWithdrawalCompleted(USER_ID, AT);
		verify(pushCleanup).onWithdrawalCompleted(USER_ID, AT);
		verify(credentialCleanup, never()).onWithdrawalRequested(anyLong(), any());
		verify(pushCleanup, never()).onWithdrawalRequested(anyLong(), any());
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-014: 완료 정리 구현은 기기 자격증명과 푸시 기기를 모두 폐기하고 위치는 건드리지 않는다(A4)")
	void completionCleanupImplementationsRevokeCredentialsAndPushDevices() {
		NotificationRepository notificationRepository = mock(NotificationRepository.class);
		ActiveUserPresenceRepository presenceRepository = mock(ActiveUserPresenceRepository.class);
		DeviceCredentialRepository credentialRepository = mock(DeviceCredentialRepository.class);
		AccountWithdrawalCompletionService wired = new AccountWithdrawalCompletionService(accountRepository,
				List.of(new DeviceCredentialWithdrawalCleanup(credentialRepository),
						new PushDeviceWithdrawalCleanup(notificationRepository),
						new PresenceWithdrawalCleanup(presenceRepository)),
				new AccountWithdrawalProperties(GRACE));
		givenAccount(account(AccountStatus.WITHDRAWAL_PENDING, AT.minus(GRACE)));

		assertThat(wired.complete(USER_ID, AT)).isTrue();

		verify(credentialRepository).revokeAllActiveByUserId(USER_ID, AT);
		verify(notificationRepository).revokeAllDevicesByUserId(USER_ID, AT);
		verifyNoInteractions(presenceRepository);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-014: 유예가 1초 남은 계정은 ineligible이고 쓰기와 정리가 없다")
	void skipsPendingAccountBeforeDeadline() {
		givenAccount(account(AccountStatus.WITHDRAWAL_PENDING, AT.minus(GRACE).plusSeconds(1)));

		assertThat(service.complete(USER_ID, AT)).isFalse();

		verify(accountRepository, never()).updateDeletion(any(Account.class));
		verify(accountRepository, never()).updateStatus(any(Account.class));
		verifyNoInteractions(credentialCleanup, pushCleanup);
	}

	@ParameterizedTest
	@EnumSource(value = AccountStatus.class, names = {"ACTIVE", "BLOCKED", "DELETED"})
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-014: 후보 조회 뒤 철회됐거나 이미 끝난 계정은 ineligible이고 쓰기와 정리가 없다")
	void skipsAccountsNoLongerPending(AccountStatus status) {
		givenAccount(account(status, null));

		assertThat(service.complete(USER_ID, AT)).isFalse();

		verify(accountRepository, never()).updateDeletion(any(Account.class));
		verify(accountRepository, never()).updateStatus(any(Account.class));
		verifyNoInteractions(credentialCleanup, pushCleanup);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-014: 사라진 계정은 ineligible이고 쓰기와 정리가 없다")
	void skipsMissingAccount() {
		when(accountRepository.findById(USER_ID)).thenReturn(Optional.empty());

		assertThat(service.complete(USER_ID, AT)).isFalse();

		verify(accountRepository, never()).updateDeletion(any(Account.class));
		verifyNoInteractions(credentialCleanup, pushCleanup);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-014: 후보 조회는 at에서 유예 기간을 뺀 요청 시각 기준으로 limit만큼 묻는다")
	void findsDueUserIdsByRequestedAtCutoff() {
		when(accountRepository.findWithdrawalDueIds(AT.minus(GRACE), 25)).thenReturn(List.of(3L, 5L));

		assertThat(service.findDueUserIds(AT, 25)).containsExactly(3L, 5L);

		verify(accountRepository).findWithdrawalDueIds(AT.minus(GRACE), 25);
	}

	private void givenAccount(Account account) {
		when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
	}

	private static Account account(AccountStatus status, Instant withdrawalRequestedAt) {
		Instant deletedAt = status == AccountStatus.DELETED ? DELETED_AT : null;
		return Account.restore(USER_ID, AccountRole.USER, status, "KR", "KR-11", "ko-KR", "Asia/Seoul", "바람",
				deletedAt, withdrawalRequestedAt);
	}

}
