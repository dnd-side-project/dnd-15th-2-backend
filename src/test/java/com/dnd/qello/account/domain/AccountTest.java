package com.dnd.qello.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Created at: 2026-08-04T12:00:00+09:00
 * Source scenario: TEST-PLAN-GH-48-ACCOUNT-PASSWORD-UNIT-001
 */
class AccountTest {

	private static final PasswordHash OPERATOR_PASSWORD_HASH = new PasswordHash("$2a$10$hashed-value");

	@Test
	@DisplayName("createUser는 항상 USER 역할과 ACTIVE 상태로 생성되며 비밀번호를 가지지 않는다")
	void createsActiveUserAccountWithoutPassword() {
		Account account = Account.createUser("KR-TEST", "ko-KR", "Asia/Seoul", "qello-user");

		assertThat(account.getId()).isNull();
		assertThat(account.getRole()).isEqualTo(AccountRole.USER);
		assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(account.getPasswordHash()).isNull();
		assertThat(account.getDeletedAt()).isNull();
	}

	@Test
	@DisplayName("createOperator는 항상 OPERATOR 역할로 생성되며 유효한 passwordHash를 요구한다")
	void createsOperatorAccountWithPasswordHash() {
		Account account = Account.createOperator(
			"KR-TEST", "ko-KR", "Asia/Seoul", "qello-admin", OPERATOR_PASSWORD_HASH);

		assertThat(account.getRole()).isEqualTo(AccountRole.OPERATOR);
		assertThat(account.getPasswordHash()).isEqualTo(OPERATOR_PASSWORD_HASH);

		assertThatThrownBy(() -> Account.createOperator(
			"KR-TEST", "ko-KR", "Asia/Seoul", "qello-admin", null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Account는 필수 문자열과 schema 길이 및 IANA timezone을 검증한다")
	void rejectsInvalidProfileValues() {
		assertThatThrownBy(() -> Account.createUser(" ", "ko-KR", "Asia/Seoul", null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Account.createUser("KR-TEST", " ", "Asia/Seoul", null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Account.createUser("KR-TEST", "ko-KR", "invalid/timezone", null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Account.createUser("KR-TEST", "ko-KR", "Asia/Seoul", "   "))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Account.createUser("R".repeat(101), "ko-KR", "Asia/Seoul", null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Account.createUser("KR-TEST", "ko-KR", "Asia/Seoul", "N".repeat(51)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("USER는 passwordHash를 가질 수 없고 OPERATOR는 passwordHash가 필수다")
	void enforcesPasswordHashInvariantPerRole() {
		assertThatThrownBy(() -> Account.restore(
			1L, AccountRole.USER, AccountStatus.ACTIVE, "KR-TEST", "ko-KR", "Asia/Seoul",
			null, OPERATOR_PASSWORD_HASH, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Account.restore(
			1L, AccountRole.OPERATOR, AccountStatus.ACTIVE, "KR-TEST", "ko-KR", "Asia/Seoul",
			null, null, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("restore는 유효한 기존 id를 반드시 요구한다")
	void restoreRequiresExistingId() {
		assertThatThrownBy(() -> Account.restore(
			null, AccountRole.USER, AccountStatus.ACTIVE, "KR-TEST", "ko-KR", "Asia/Seoul",
			null, null, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Account.restore(
			0L, AccountRole.USER, AccountStatus.ACTIVE, "KR-TEST", "ko-KR", "Asia/Seoul",
			null, null, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("DELETED 상태와 deletedAt은 항상 함께 존재한다")
	void requiresDeletedAtForDeletedStatus() {
		Instant deletedAt = Instant.parse("2026-08-03T09:00:00Z");

		assertThatThrownBy(() -> Account.restore(
			1L, AccountRole.USER, AccountStatus.DELETED, "KR-TEST", "ko-KR", "Asia/Seoul",
			null, null, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Account.restore(
			1L, AccountRole.USER, AccountStatus.ACTIVE, "KR-TEST", "ko-KR", "Asia/Seoul",
			null, null, deletedAt))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("프로필과 상태 변경은 Account 식별자를 보존한다")
	void updatesProfileAndStatusWithoutChangingIdentity() {
		Account restored = Account.restore(
			1L, AccountRole.OPERATOR, AccountStatus.ACTIVE, "KR-TEST", "ko-KR", "Asia/Seoul",
			"before", OPERATOR_PASSWORD_HASH, null);

		Account updated = restored
			.updateProfile("KR-UPDATED", "en-US", "UTC", "after")
			.block();

		assertThat(updated.getId()).isEqualTo(1L);
		assertThat(updated.getRole()).isEqualTo(AccountRole.OPERATOR);
		assertThat(updated.getStatus()).isEqualTo(AccountStatus.BLOCKED);
		assertThat(updated.getCoarseRegionCode()).isEqualTo("KR-UPDATED");
		assertThat(updated.getLocale()).isEqualTo("en-US");
		assertThat(updated.getTimezone()).isEqualTo("UTC");
		assertThat(updated.getNickname()).isEqualTo("after");
	}

	@Test
	@DisplayName("block/unblock/delete는 허용된 상태 전이만 수행한다")
	void enforcesAllowedStatusTransitions() {
		Account active = Account.createUser("KR-TEST", "ko-KR", "Asia/Seoul", "user");
		Account blocked = active.block();
		Account unblocked = blocked.unblock();
		Account deleted = active.delete(Instant.parse("2026-08-03T09:00:00Z"));

		assertThat(blocked.getStatus()).isEqualTo(AccountStatus.BLOCKED);
		assertThat(unblocked.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(deleted.getStatus()).isEqualTo(AccountStatus.DELETED);
		assertThat(deleted.getDeletedAt()).isNotNull();

		assertThatThrownBy(unblocked::unblock).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(deleted::block).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> deleted.delete(Instant.now())).isInstanceOf(IllegalStateException.class);
	}

}
