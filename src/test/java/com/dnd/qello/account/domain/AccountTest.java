package com.dnd.qello.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Created at: 2026-08-03T18:13:05+09:00
 * Source scenario: TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-UNIT-001
 */
class AccountTest {

	@Test
	@DisplayName("새 Account는 USER 또는 OPERATOR 역할과 ACTIVE 상태로 생성된다")
	void createsActiveAccount() {
		Account account = Account.create(
			AccountRole.USER,
			"KR-TEST",
			"ko-KR",
			"Asia/Seoul",
			"qello-user"
		);

		assertThat(account.getId()).isNull();
		assertThat(account.getRole()).isEqualTo(AccountRole.USER);
		assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(account.getCreatedAt()).isNull();
		assertThat(account.getUpdatedAt()).isNull();
		assertThat(account.getDeletedAt()).isNull();
	}

	@Test
	@DisplayName("Account는 필수 문자열과 schema 길이 및 IANA timezone을 검증한다")
	void rejectsInvalidProfileValues() {
		assertThatThrownBy(() -> Account.create(
			AccountRole.USER, " ", "ko-KR", "Asia/Seoul", null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Account.create(
			AccountRole.USER, "KR-TEST", " ", "Asia/Seoul", null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Account.create(
			AccountRole.USER, "KR-TEST", "ko-KR", "invalid/timezone", null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Account.create(
			AccountRole.USER, "KR-TEST", "ko-KR", "Asia/Seoul", "   "))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Account.create(
			AccountRole.USER, "R".repeat(101), "ko-KR", "Asia/Seoul", null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Account.create(
			AccountRole.USER, "KR-TEST", "ko-KR", "Asia/Seoul", "N".repeat(51)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("DELETED 상태와 deletedAt은 항상 함께 존재한다")
	void requiresDeletedAtForDeletedStatus() {
		Instant createdAt = Instant.parse("2026-08-03T09:00:00Z");

		assertThatThrownBy(() -> Account.restore(
			1L,
			AccountRole.USER,
			AccountStatus.DELETED,
			"KR-TEST",
			"ko-KR",
			"Asia/Seoul",
			null,
			createdAt,
			createdAt,
			null
		)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Account.restore(
			1L,
			AccountRole.USER,
			AccountStatus.ACTIVE,
			"KR-TEST",
			"ko-KR",
			"Asia/Seoul",
			null,
			createdAt,
			createdAt,
			createdAt
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("프로필과 상태 변경은 Account 식별자와 감사 시각을 보존한다")
	void updatesProfileAndStatusWithoutChangingIdentity() {
		Instant createdAt = Instant.parse("2026-08-03T09:00:00Z");
		Account restored = Account.restore(
			1L,
			AccountRole.OPERATOR,
			AccountStatus.ACTIVE,
			"KR-TEST",
			"ko-KR",
			"Asia/Seoul",
			"before",
			createdAt,
			createdAt,
			null
		);

		Account updated = restored
			.updateProfile("KR-UPDATED", "en-US", "UTC", "after")
			.changeStatus(AccountStatus.BLOCKED, null);

		assertThat(updated.getId()).isEqualTo(1L);
		assertThat(updated.getRole()).isEqualTo(AccountRole.OPERATOR);
		assertThat(updated.getStatus()).isEqualTo(AccountStatus.BLOCKED);
		assertThat(updated.getCoarseRegionCode()).isEqualTo("KR-UPDATED");
		assertThat(updated.getLocale()).isEqualTo("en-US");
		assertThat(updated.getTimezone()).isEqualTo("UTC");
		assertThat(updated.getNickname()).isEqualTo("after");
		assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
		assertThat(updated.getUpdatedAt()).isEqualTo(createdAt);
	}

}
