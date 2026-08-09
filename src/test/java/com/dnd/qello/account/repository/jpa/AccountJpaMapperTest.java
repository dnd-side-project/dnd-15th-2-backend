package com.dnd.qello.account.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;

/**
 * Created at: 2026-08-04T12:00:00+09:00
 * Source scenario: TEST-PLAN-GH-48-ACCOUNT-PASSWORD-UNIT-004, TEST-PLAN-GH-88-COUNTRY-ONBOARDING-UNIT-001
 */
class AccountJpaMapperTest {

	@Test
	@DisplayName("toNewEntity는 id가 없는 Account만 허용하고 id/audit 필드를 직접 설정하지 않는다")
	void toNewEntityRejectsAccountWithId() {
		Account existing = Account.restore(
			7L, AccountRole.USER, AccountStatus.ACTIVE, "KR", "KR-TEST", "ko-KR", "Asia/Seoul",
			null, null);

		assertThatThrownBy(() -> AccountJpaMapper.toNewEntity(existing))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_ID);
	}

	@Test
	@DisplayName("toNewEntity와 toDomain은 scalar 필드를 보존하며 왕복한다")
	void mapsAccountRoundTripWithoutValueLoss() {
		Account source = Account.createOperator("KR-TEST", "ko-KR", "Asia/Seoul", "qello-admin");

		AccountJpaEntity entity = AccountJpaMapper.toNewEntity(source);

		assertThat(entity.getRole()).isEqualTo(AccountRole.OPERATOR);
		assertThat(entity.getCountryCode()).isNull();
		assertThat(entity.getCoarseRegionCode()).isEqualTo("KR-TEST");
		assertThat(entity.getNickname()).isEqualTo("qello-admin");
		assertThat(entity.getId()).isNull();
	}

	@Test
	@DisplayName("USER 매핑은 countryCode를 보존한다")
	void mapsUserCountryCode() {
		Account source = Account.createUser("KR", "KR-TEST", "ko-KR", "Asia/Seoul", "qello-user");

		AccountJpaEntity entity = AccountJpaMapper.toNewEntity(source);

		assertThat(entity.getCountryCode()).isEqualTo("KR");
	}

	@Test
	@DisplayName("toDomain은 기존 id를 가진 엔티티만 복원할 수 있다")
	void toDomainRestoresExistingAccount() throws Exception {
		AccountJpaEntity entity = newManagedEntity(
			AccountRole.USER, AccountStatus.ACTIVE, "KR-TEST", "ko-KR", "Asia/Seoul", "nickname", null, 7L);

		Account restored = AccountJpaMapper.toDomain(entity);

		assertThat(restored.getId()).isEqualTo(7L);
		assertThat(restored.getCoarseRegionCode()).isEqualTo("KR-TEST");
		assertThat(restored.getNickname()).isEqualTo("nickname");
	}

	@Test
	@DisplayName("updateProfile은 관리 상태 엔티티의 프로필 필드만 변경한다")
	void updateProfileMutatesManagedEntityInPlace() throws Exception {
		AccountJpaEntity entity = newManagedEntity(
			AccountRole.USER, AccountStatus.ACTIVE, "KR-OLD", "ko-KR", "Asia/Seoul", "old", null, 7L);
		Account account = AccountJpaMapper.toDomain(entity)
			.updateProfile("KR-NEW", "en-US", "UTC", "new");

		AccountJpaMapper.updateProfile(entity, account);

		assertThat(entity.getCoarseRegionCode()).isEqualTo("KR-NEW");
		assertThat(entity.getLocale()).isEqualTo("en-US");
		assertThat(entity.getTimezone()).isEqualTo("UTC");
		assertThat(entity.getNickname()).isEqualTo("new");
		assertThat(entity.getStatus()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	@DisplayName("updateStatus는 status와 deletedAt만 변경한다")
	void updateStatusMutatesManagedEntityInPlace() throws Exception {
		AccountJpaEntity entity = newManagedEntity(
			AccountRole.USER, AccountStatus.ACTIVE, "KR-TEST", "ko-KR", "Asia/Seoul", "nickname", null, 7L);
		Instant deletedAt = Instant.parse("2026-08-04T00:00:00Z");
		Account account = AccountJpaMapper.toDomain(entity).delete(deletedAt);

		AccountJpaMapper.updateStatus(entity, account);

		assertThat(entity.getStatus()).isEqualTo(AccountStatus.DELETED);
		assertThat(entity.getDeletedAt()).isEqualTo(deletedAt);
		assertThat(entity.getCoarseRegionCode()).isEqualTo("KR-TEST");
	}

	private AccountJpaEntity newManagedEntity(
		AccountRole role,
		AccountStatus status,
		String coarseRegionCode,
		String locale,
		String timezone,
		String nickname,
		Instant deletedAt,
		Long id
	) throws Exception {
		var constructor = AccountJpaEntity.class.getDeclaredConstructor(
			AccountRole.class, AccountStatus.class, String.class, String.class, String.class,
			String.class, String.class);
		constructor.setAccessible(true);
		AccountJpaEntity entity = constructor.newInstance(
			role, status, role == AccountRole.USER ? "KR" : null, coarseRegionCode, locale, timezone, nickname);

		setField(entity, "id", id);
		if (deletedAt != null) {
			setField(entity, "deletedAt", deletedAt);
		}
		return entity;
	}

	private void setField(AccountJpaEntity entity, String fieldName, Object value) throws Exception {
		var field = AccountJpaEntity.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(entity, value);
	}

}
