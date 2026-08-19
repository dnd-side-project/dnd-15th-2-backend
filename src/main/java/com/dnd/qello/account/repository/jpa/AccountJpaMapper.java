package com.dnd.qello.account.repository.jpa;

import com.dnd.qello.account.domain.Account;
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

		AccountJpaEntity entity = new AccountJpaEntity(
			account.getRole(),
			account.getStatus(),
			account.getCountryCode(),
			account.getCoarseRegionCode(),
			account.getLocale(),
			account.getTimezone(),
			account.getNickname()
		);
		// 가입과 동시에 프로필 이미지를 지정하는 경로가 있으므로 최초 INSERT에도 실어 보낸다.
		// 계정만 먼저 저장하고 뒤이어 UPDATE 하면 같은 트랜잭션 안이어도 실패 지점이 늘어난다.
		entity.updateProfileImage(account.getProfileImageMediaId());
		return entity;
	}

	static Account toDomain(AccountJpaEntity entity) {
		Account restored = Account.restore(
			entity.getId(),
			entity.getRole(),
			entity.getStatus(),
			entity.getCountryCode(),
			entity.getCoarseRegionCode(),
			entity.getLocale(),
			entity.getTimezone(),
			entity.getNickname(),
			entity.getDeletedAt()
		);
		Long profileImageMediaId = entity.getProfileImageMediaId();
		return profileImageMediaId == null ? restored : restored.withProfileImage(profileImageMediaId);
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

	static void updateProfileImage(AccountJpaEntity entity, Account account) {
		entity.updateProfileImage(account.getProfileImageMediaId());
	}

	static void updateStatus(AccountJpaEntity entity, Account account) {
		entity.updateStatus(account.getStatus(), account.getDeletedAt());
	}

}
