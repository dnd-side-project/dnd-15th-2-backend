/**
 * Created at: 2026-08-20T15:05:00+09:00
 * Source scenario: TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ-UNIT-014
 */
package com.dnd.qello.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;

@ExtendWith(MockitoExtension.class)
class AccountEligibilityGateTest {

	private static final long ACCOUNT_ID = 42L;

	@Mock private AccountRepository accountRepository;

	private AccountEligibilityGate gate;

	@BeforeEach
	void setUp() {
		gate = new AccountEligibilityGate(accountRepository);
	}

	@Test
	@DisplayName("ACTIVE USER 계정은 그대로 반환한다")
	void returnsActiveUserAccount() {
		Account account = userAccount(AccountStatus.ACTIVE);
		when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

		Account result = gate.requireActiveUser(
			ACCOUNT_ID, RuntimeException::new, RuntimeException::new);

		assertThat(result).isSameAs(account);
	}

	@Test
	@DisplayName("계정이 없으면 호출부가 준 notFound supplier의 예외를 그대로 던진다")
	void throwsCallerSuppliedNotFoundException() {
		when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());
		IllegalStateException notFound = new IllegalStateException("not-found");
		Supplier<RuntimeException> notEligible = neverCalled();

		assertThatThrownBy(() -> gate.requireActiveUser(ACCOUNT_ID, () -> notFound, notEligible))
			.isSameAs(notFound);
	}

	@Test
	@DisplayName("OPERATOR 역할은 호출부가 준 notEligible supplier의 예외를 그대로 던진다")
	void throwsCallerSuppliedNotEligibleExceptionForOperator() {
		Account operator = Account.restore(
			ACCOUNT_ID, AccountRole.OPERATOR, AccountStatus.ACTIVE, null, "KR-11", "ko-KR",
			"Asia/Seoul", "operator-nick", null);
		when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(operator));
		IllegalStateException notEligible = new IllegalStateException("not-eligible");
		Supplier<RuntimeException> notFound = neverCalled();

		assertThatThrownBy(() -> gate.requireActiveUser(ACCOUNT_ID, notFound, () -> notEligible))
			.isSameAs(notEligible);
	}

	@Test
	@DisplayName("BLOCKED 상태인 USER는 notEligible supplier의 예외를 그대로 던진다")
	void throwsCallerSuppliedNotEligibleExceptionForBlockedUser() {
		Account blocked = userAccount(AccountStatus.BLOCKED);
		when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(blocked));
		IllegalStateException notEligible = new IllegalStateException("not-eligible");

		assertThatThrownBy(() -> gate.requireActiveUser(ACCOUNT_ID, RuntimeException::new, () -> notEligible))
			.isSameAs(notEligible);
	}

	@Test
	@DisplayName("게이트는 account.service 밖의 오류 코드 타입을 참조하지 않는다")
	void doesNotReferenceCallerErrorCodes() {
		when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());
		Supplier<RuntimeException> notFound = () -> new RuntimeException("caller-defined");

		assertThatThrownBy(() -> gate.requireActiveUser(ACCOUNT_ID, notFound, RuntimeException::new))
			.hasMessage("caller-defined");
		verify(accountRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	private static Supplier<RuntimeException> neverCalled() {
		return () -> {
			throw new AssertionError("이 supplier는 호출되지 않아야 한다");
		};
	}

	private static Account userAccount(AccountStatus status) {
		return Account.restore(
			ACCOUNT_ID, AccountRole.USER, status, "KR", "KR-11", "ko-KR", "Asia/Seoul",
			"gate-test-nick", status == AccountStatus.DELETED ? Instant.parse("2026-08-01T00:00:00Z") : null);
	}
}
