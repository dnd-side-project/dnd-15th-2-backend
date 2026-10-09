/*
 * Created at: 2026-10-09T18:14:46+09:00
 * Source scenario: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-001 through UNIT-007
 */
package com.dnd.qello.account.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountWithdrawalTest {

	private static final long ACCOUNT_ID = 7L;
	private static final String NICKNAME = "바람";
	private static final Duration GRACE = Duration.ofDays(30);
	private static final Instant REQUESTED_AT = Instant.parse("2026-10-01T00:00:00Z");
	private static final Instant DEADLINE = REQUESTED_AT.plus(GRACE);
	private static final Instant DELETED_AT = Instant.parse("2026-09-01T00:00:00Z");

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-001: ACTIVE 계정의 탈퇴 요청은 유예 상태와 요청 시각을 남기고 닉네임을 유지한다")
	void requestWithdrawalMovesActiveAccountToPending() {
		Account active = account(AccountStatus.ACTIVE);

		Account requested = active.requestWithdrawal(REQUESTED_AT);

		assertThat(requested.getStatus()).isEqualTo(AccountStatus.WITHDRAWAL_PENDING);
		assertThat(requested.getWithdrawalRequestedAt()).isEqualTo(REQUESTED_AT);
		assertThat(requested.getNickname()).isEqualTo(NICKNAME);
		assertThat(requested.getDeletedAt()).isNull();
		assertThat(requested.getId()).isEqualTo(ACCOUNT_ID);
		assertThat(requested.withdrawalDeadline(GRACE)).isEqualTo(DEADLINE);
		assertThat(requested.isWithdrawalDue(DEADLINE.minusSeconds(1), GRACE)).isFalse();
		assertThat(active.withdrawalDeadline(GRACE)).isNull();
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-002: 유예 중 계정의 재요청은 처음 요청 시각과 삭제 예정 시각을 바꾸지 않는다(A1)")
	void repeatedRequestKeepsFirstRequestedAt() {
		Account pending = account(AccountStatus.WITHDRAWAL_PENDING);

		Account again = pending.requestWithdrawal(REQUESTED_AT.plus(Duration.ofDays(1)));

		assertThat(again.getStatus()).isEqualTo(AccountStatus.WITHDRAWAL_PENDING);
		assertThat(again.getWithdrawalRequestedAt()).isEqualTo(REQUESTED_AT);
		assertThat(again.withdrawalDeadline(GRACE)).isEqualTo(DEADLINE);
		assertThat(again.getNickname()).isEqualTo(NICKNAME);
	}

	@ParameterizedTest
	@EnumSource(value = AccountStatus.class, names = {"BLOCKED", "DELETED"})
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-002: 차단·삭제 계정의 탈퇴 요청은 INVALID_STATUS_TRANSITION이다(A3)")
	void requestWithdrawalRejectsBlockedAndDeletedAccounts(AccountStatus status) {
		Account account = account(status);

		assertThatThrownBy(() -> account.requestWithdrawal(REQUESTED_AT))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_STATUS_TRANSITION);
	}

	@ParameterizedTest(name = "now = deadline {0}s -> cancelled={1}")
	@MethodSource("cancelBoundaries")
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-003: 철회는 삭제 예정 시각 전에만 ACTIVE로 되돌리고 같거나 지난 시각은 거절한다(A2)")
	void cancelWithdrawalOnlyBeforeDeadline(long offsetSeconds, boolean cancelled) {
		Account pending = account(AccountStatus.WITHDRAWAL_PENDING);
		Instant now = DEADLINE.plusSeconds(offsetSeconds);

		if (!cancelled) {
			assertThatThrownBy(() -> pending.cancelWithdrawal(now, GRACE))
					.isInstanceOf(AccountException.class)
					.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_STATUS_TRANSITION);
			return;
		}
		Account active = pending.cancelWithdrawal(now, GRACE);

		assertThat(active.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(active.getWithdrawalRequestedAt()).isNull();
		assertThat(active.getDeletedAt()).isNull();
		assertThat(active.getNickname()).isEqualTo(NICKNAME);
		assertThat(active.withdrawalDeadline(GRACE)).isNull();
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-004: ACTIVE 계정의 철회는 값을 바꾸지 않는다(A2)")
	void cancelWithdrawalIsNoOpForActiveAccount() {
		Account active = account(AccountStatus.ACTIVE);

		Account result = active.cancelWithdrawal(DEADLINE.plusSeconds(1), GRACE);

		assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(result.getWithdrawalRequestedAt()).isNull();
		assertThat(result.getDeletedAt()).isNull();
		assertThat(result.getNickname()).isEqualTo(NICKNAME);
	}

	@ParameterizedTest
	@EnumSource(value = AccountStatus.class, names = {"BLOCKED", "DELETED"})
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-004: 차단·삭제 계정의 철회는 INVALID_STATUS_TRANSITION이다")
	void cancelWithdrawalRejectsBlockedAndDeletedAccounts(AccountStatus status) {
		Account account = account(status);

		assertThatThrownBy(() -> account.cancelWithdrawal(REQUESTED_AT, GRACE))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_STATUS_TRANSITION);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-005: 유예가 끝난 계정의 완료는 DELETED, deletedAt, 닉네임 null, 요청 시각 null이다")
	void completeWithdrawalDeletesDueAccount() {
		Account pending = account(AccountStatus.WITHDRAWAL_PENDING);

		Account deleted = pending.completeWithdrawal(DEADLINE, GRACE);

		assertThat(deleted.getStatus()).isEqualTo(AccountStatus.DELETED);
		assertThat(deleted.getDeletedAt()).isEqualTo(DEADLINE);
		assertThat(deleted.getNickname()).isNull();
		assertThat(deleted.getWithdrawalRequestedAt()).isNull();
		assertThat(deleted.getId()).isEqualTo(ACCOUNT_ID);
		assertThat(deleted.withdrawalDeadline(GRACE)).isNull();
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-005: 유예가 남은 계정의 완료는 INVALID_STATUS_TRANSITION이다")
	void completeWithdrawalRejectsPendingAccountBeforeDeadline() {
		Account pending = account(AccountStatus.WITHDRAWAL_PENDING);

		assertThatThrownBy(() -> pending.completeWithdrawal(DEADLINE.minusSeconds(1), GRACE))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_STATUS_TRANSITION);
	}

	@ParameterizedTest
	@EnumSource(value = AccountStatus.class, names = {"ACTIVE", "BLOCKED", "DELETED"})
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-005: 유예 중이 아닌 계정의 완료는 INVALID_STATUS_TRANSITION이다")
	void completeWithdrawalRejectsAccountsNotPending(AccountStatus status) {
		Account account = account(status);

		assertThatThrownBy(() -> account.completeWithdrawal(DEADLINE.plusSeconds(1), GRACE))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_STATUS_TRANSITION);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-006: 유예 상태와 요청 시각이 어긋난 restore는 상태 불변식 오류다")
	void restoreRejectsMismatchedWithdrawalState() {
		assertThatThrownBy(() -> Account.restore(ACCOUNT_ID, AccountRole.USER, AccountStatus.WITHDRAWAL_PENDING, "KR",
				"KR-11", "ko-KR", "Asia/Seoul", NICKNAME, null, null))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_DELETION_STATE);
		assertThatThrownBy(() -> Account.restore(ACCOUNT_ID, AccountRole.USER, AccountStatus.ACTIVE, "KR",
				"KR-11", "ko-KR", "Asia/Seoul", NICKNAME, null, REQUESTED_AT))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_DELETION_STATE);
		// 기존 9인자 restore는 요청 시각을 null로 넘기므로 유예 중 계정을 조용히 복원하지 않는다.
		assertThatThrownBy(() -> Account.restore(ACCOUNT_ID, AccountRole.USER, AccountStatus.WITHDRAWAL_PENDING, "KR",
				"KR-11", "ko-KR", "Asia/Seoul", NICKNAME, null))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_DELETION_STATE);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-007: 유예 중 계정의 차단은 INVALID_STATUS_TRANSITION이다")
	void blockRejectsWithdrawalPendingAccount() {
		Account pending = account(AccountStatus.WITHDRAWAL_PENDING);

		assertThatThrownBy(pending::block)
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_STATUS_TRANSITION);
	}

	private static Stream<Arguments> cancelBoundaries() {
		return Stream.of(
				Arguments.of(-1L, true),
				Arguments.of(0L, false),
				Arguments.of(1L, false));
	}

	private static Account account(AccountStatus status) {
		Instant deletedAt = status == AccountStatus.DELETED ? DELETED_AT : null;
		Instant withdrawalRequestedAt = status == AccountStatus.WITHDRAWAL_PENDING ? REQUESTED_AT : null;
		return Account.restore(ACCOUNT_ID, AccountRole.USER, status, "KR", "KR-11", "ko-KR", "Asia/Seoul", NICKNAME,
				deletedAt, withdrawalRequestedAt);
	}

}
