package com.dnd.qello.account.repository.jpa;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.PasswordHash;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;

final class AccountJpaMapper {

	private AccountJpaMapper() {
	}

	/**
	 * 신규 Account만 허용한다. id, createdAt, updatedAt은 DB와 JPA Auditing이 채운다.
	 */
	static AccountJpaEntity toNewEntity(Account account) {
		if (account.getId() != null) {
			throw new AccountException(
				AccountErrorCode.INVALID_ID, "id", "신규 계정에는 id가 없어야 합니다");
		}

		return new AccountJpaEntity(
			account.getRole(),
			account.getStatus(),
			account.getCoarseRegionCode(),
			account.getLocale(),
			account.getTimezone(),
			account.getNickname(),
			toHashValue(account.getPasswordHash())
		);
	}

	static Account toDomain(AccountJpaEntity entity) {
		return Account.restore(
			entity.getId(),
			entity.getRole(),
			entity.getStatus(),
			entity.getCoarseRegionCode(),
			entity.getLocale(),
			entity.getTimezone(),
			entity.getNickname(),
			toPasswordHash(entity.getPasswordHash()),
			entity.getDeletedAt()
		);
	}

	/**
	 * 관리 상태 엔티티를 변경하고 Dirty Checking에 위임한다. 새 엔티티를 만들거나 merge하지 않는다.
	 */
	static void updateProfile(AccountJpaEntity entity, Account account) {
		entity.updateProfile(
			account.getCoarseRegionCode(),
			account.getLocale(),
			account.getTimezone(),
			account.getNickname()
		);
	}

	static void updateStatus(AccountJpaEntity entity, Account account) {
		entity.updateStatus(account.getStatus(), account.getDeletedAt());
	}

	private static String toHashValue(PasswordHash passwordHash) {
		return passwordHash == null ? null : passwordHash.value();
	}

	private static PasswordHash toPasswordHash(String value) {
		return value == null ? null : new PasswordHash(value);
	}

}
