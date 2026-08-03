package com.dnd.qello.account.repository.jpa;

import com.dnd.qello.account.domain.Account;

final class AccountJpaMapper {

	private AccountJpaMapper() {
	}

	static AccountJpaEntity toEntity(Account account) {
		return new AccountJpaEntity(
			account.getId(),
			account.getRole(),
			account.getStatus(),
			account.getCoarseRegionCode(),
			account.getLocale(),
			account.getTimezone(),
			account.getNickname(),
			account.getCreatedAt(),
			account.getUpdatedAt(),
			account.getDeletedAt()
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
			entity.getCreatedAt(),
			entity.getUpdatedAt(),
			entity.getDeletedAt()
		);
	}

}
