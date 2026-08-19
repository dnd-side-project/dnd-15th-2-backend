package com.dnd.qello.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;

/**
 * Created at: 2026-08-04T12:00:00+09:00
 * Source scenario: TEST-PLAN-GH-48-ACCOUNT-PASSWORD-UNIT-001, TEST-PLAN-GH-88-COUNTRY-ONBOARDING-UNIT-001
 */
class AccountTest {

	@Test
	@DisplayName("createUser는 항상 USER 역할과 ACTIVE 상태로 생성되며 비밀번호를 가지지 않는다")
	void createsActiveUserAccountWithoutPassword() {
		Account account = Account.createUser("KR", "KR-TEST", "ko-KR", "Asia/Seoul", "qello-user");

		assertThat(account.getId()).isNull();
		assertThat(account.getRole()).isEqualTo(AccountRole.USER);
		assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(account.getCountryCode()).isEqualTo("KR");
		assertThat(account.getDeletedAt()).isNull();
	}

	@Test
	@DisplayName("닉네임 앞뒤 공백은 저장 전에 제거된다")
	void trimsNicknameWhitespace() {
		Account account = Account.createUser("KR", "KR-TEST", "ko-KR", "Asia/Seoul", "  여름  ");

		assertThat(account.getNickname()).isEqualTo("여름");
	}

	@Test
	@DisplayName("createOperator는 항상 OPERATOR 역할로 생성되며 자격증명을 갖지 않는다")
	void createsOperatorAccountWithoutCredential() {
		Account account = Account.createOperator("KR-TEST", "ko-KR", "Asia/Seoul", "qello-admin");

		assertThat(account.getId()).isNull();
		assertThat(account.getRole()).isEqualTo(AccountRole.OPERATOR);
		assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(account.getCountryCode()).isNull();
	}

	@Test
	@DisplayName("Account는 필수 문자열과 schema 길이 및 IANA timezone을 검증한다")
	void rejectsInvalidProfileValues() {
		assertThatThrownBy(() -> Account.createUser("KR", " ", "ko-KR", "Asia/Seoul", null))
			.isInstanceOf(AccountException.class);
		assertThatThrownBy(() -> Account.createUser("KR", "KR-TEST", " ", "Asia/Seoul", null))
			.isInstanceOf(AccountException.class);
		assertThatThrownBy(() -> Account.createUser("KR", "KR-TEST", "ko-KR", "invalid/timezone", null))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_TIMEZONE)
			.hasFieldOrPropertyWithValue("field", "timezone");
		assertThatThrownBy(() -> Account.createUser("KR", "KR-TEST", "ko-KR", "Asia/Seoul", "   "))
			.isInstanceOf(AccountException.class);
		assertThatThrownBy(() -> Account.createUser("KR", "R".repeat(101), "ko-KR", "Asia/Seoul", null))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.TEXT_TOO_LONG);
		assertThatThrownBy(() -> Account.createUser("KR", "KR-TEST", "ko-KR", "Asia/Seoul", "N".repeat(51)))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.TEXT_TOO_LONG);
		assertThatThrownBy(() -> Account.createUser(null, "KR-TEST", "ko-KR", "Asia/Seoul", null))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.REQUIRED_VALUE_MISSING);
		assertThatThrownBy(() -> Account.createUser("KOR", "KR-TEST", "ko-KR", "Asia/Seoul", null))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_COUNTRY_CODE);
	}

	@Test
	@DisplayName("Account는 role과 자격증명의 조합을 검증하지 않는다")
	void doesNotKnowAboutCredentials() {
		// 자격증명은 operator_credential이 소유하고, role과의 조합은 (user_id, role)
		// 복합 FK가 DB에서 거절한다(V5). 도메인이 같은 규칙을 중복 검사하지 않는다.
		Account operator = Account.restore(
			1L, AccountRole.OPERATOR, AccountStatus.ACTIVE, "KR", "KR-TEST", "ko-KR", "Asia/Seoul", null, null);
		Account user = Account.restore(
			2L, AccountRole.USER, AccountStatus.ACTIVE, "KR", "KR-TEST", "ko-KR", "Asia/Seoul", null, null);

		assertThat(operator.getRole()).isEqualTo(AccountRole.OPERATOR);
		assertThat(user.getRole()).isEqualTo(AccountRole.USER);
	}

	@Test
	@DisplayName("restore는 유효한 기존 id를 반드시 요구한다")
	void restoreRequiresExistingId() {
		assertThatThrownBy(() -> Account.restore(
			null, AccountRole.USER, AccountStatus.ACTIVE, "KR", "KR-TEST", "ko-KR", "Asia/Seoul",
			null, null))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_ID);
		assertThatThrownBy(() -> Account.restore(
			0L, AccountRole.USER, AccountStatus.ACTIVE, "KR", "KR-TEST", "ko-KR", "Asia/Seoul",
			null, null))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_ID);
	}

	@Test
	@DisplayName("DELETED 상태와 deletedAt은 항상 함께 존재한다")
	void requiresDeletedAtForDeletedStatus() {
		Instant deletedAt = Instant.parse("2026-08-03T09:00:00Z");

		assertThatThrownBy(() -> Account.restore(
			1L, AccountRole.USER, AccountStatus.DELETED, "KR", "KR-TEST", "ko-KR", "Asia/Seoul",
			null, null))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_DELETION_STATE);
		assertThatThrownBy(() -> Account.restore(
			1L, AccountRole.USER, AccountStatus.ACTIVE, "KR", "KR-TEST", "ko-KR", "Asia/Seoul",
			null, deletedAt))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_DELETION_STATE);
	}

	@Test
	@DisplayName("프로필과 상태 변경은 Account 식별자를 보존한다")
	void updatesProfileAndStatusWithoutChangingIdentity() {
		Account restored = Account.restore(
			1L, AccountRole.OPERATOR, AccountStatus.ACTIVE, "KR", "KR-TEST", "ko-KR", "Asia/Seoul",
			"before", null);

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
		Account active = Account.createUser("KR", "KR-TEST", "ko-KR", "Asia/Seoul", "user");
		Account blocked = active.block();
		Account unblocked = blocked.unblock();
		Account deleted = active.delete(Instant.parse("2026-08-03T09:00:00Z"));

		assertThat(blocked.getStatus()).isEqualTo(AccountStatus.BLOCKED);
		assertThat(unblocked.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(deleted.getStatus()).isEqualTo(AccountStatus.DELETED);
		assertThat(deleted.getDeletedAt()).isNotNull();

		assertThatThrownBy(unblocked::unblock)
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_STATUS_TRANSITION);
		assertThatThrownBy(deleted::block)
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_STATUS_TRANSITION);
		assertThatThrownBy(() -> deleted.delete(Instant.now()))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_STATUS_TRANSITION);
	}

}
