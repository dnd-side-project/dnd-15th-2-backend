package com.dnd.qello.account.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;

/**
 * Created at: 2026-08-03T18:13:05+09:00
 * Source scenario: TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-UNIT-002
 */
class AccountJpaMapperTest {

	@Test
	@DisplayName("Account와 JPA Entity를 왕복 변환해 scalar FK와 enum 및 Instant를 보존한다")
	void mapsAccountRoundTripWithoutValueLoss() {
		Instant createdAt = Instant.parse("2026-08-03T09:00:00Z");
		Instant updatedAt = Instant.parse("2026-08-03T09:05:00Z");
		Instant deletedAt = Instant.parse("2026-08-03T09:10:00Z");
		Account source = Account.restore(
			7L,
			AccountRole.OPERATOR,
			AccountStatus.DELETED,
			"KR-TEST",
			"ko-KR",
			"Asia/Seoul",
			null,
			createdAt,
			updatedAt,
			deletedAt
		);

		AccountJpaEntity entity = AccountJpaMapper.toEntity(source);
		Account restored = AccountJpaMapper.toDomain(entity);

		assertThat(entity.getCoarseRegionCode()).isEqualTo("KR-TEST");
		assertThat(restored).usingRecursiveComparison().isEqualTo(source);
	}

}
