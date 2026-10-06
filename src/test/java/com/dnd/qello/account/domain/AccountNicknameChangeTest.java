/*
 * Created at: 2026-10-06T14:26:52+09:00
 * Source scenario: TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-008, UNIT-011, UNIT-013
 */
package com.dnd.qello.account.domain;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountNicknameChangeTest {

	private static final Duration COOLDOWN = Duration.ofDays(30);
	private static final Instant NOW = Instant.parse("2026-10-06T05:00:00Z");

	@Test
	@DisplayName("UNIT-008: 닉네임을 바꾼 적이 없으면 언제든 바꿀 수 있다")
	void allowsFirstChange() {
		assertThat(account().canChangeNicknameAt(NOW, COOLDOWN)).isTrue();
	}

	@Test
	@DisplayName("UNIT-008: 마지막 변경 후 주기가 1초라도 남았으면 바꿀 수 없다")
	void rejectsChangeInsideCooldown() {
		Account changed = account().withNicknameChangedAt(NOW.minus(COOLDOWN).plusSeconds(1));

		assertThat(changed.canChangeNicknameAt(NOW, COOLDOWN)).isFalse();
	}

	@Test
	@DisplayName("UNIT-008: 마지막 변경 시각 + 주기 시점부터 다시 바꿀 수 있다")
	void allowsChangeExactlyAtCooldownBoundary() {
		Account changed = account().withNicknameChangedAt(NOW.minus(COOLDOWN));

		assertThat(changed.canChangeNicknameAt(NOW, COOLDOWN)).isTrue();
	}

	@Test
	@DisplayName("UNIT-011: changeNickname은 새 닉네임과 변경 시각을 함께 기록하고 다른 프로필 값은 유지한다")
	void changeNicknameRecordsChangedAt() {
		Account before = account().withProfileImage(7L);

		Account after = before.changeNickname("  새닉네임  ", NOW);

		assertThat(after.getNickname()).isEqualTo("새닉네임");
		assertThat(after.getNicknameChangedAt()).isEqualTo(NOW);
		assertThat(after.getProfileImageMediaId()).isEqualTo(7L);
		assertThat(after.getCoarseRegionCode()).isEqualTo(before.getCoarseRegionCode());
		assertThat(after.canChangeNicknameAt(NOW.plus(COOLDOWN).minusSeconds(1), COOLDOWN)).isFalse();
	}

	@Test
	@DisplayName("UNIT-011: 변경 시각 없이 닉네임을 바꿀 수 없다")
	void changeNicknameRequiresChangedAt() {
		assertThatThrownBy(() -> account().changeNickname("새닉네임", null))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.REQUIRED_VALUE_MISSING);
	}

	@Test
	@DisplayName("UNIT-013: 가입할 때 정한 닉네임은 변경 이력으로 남지 않는다")
	void createdUserHasNoNicknameChangeHistory() {
		Account created = Account.createUser("KR", "KR", "ko-KR", "Asia/Seoul", "가입닉네임");

		assertThat(created.getNicknameChangedAt()).isNull();
		assertThat(created.canChangeNicknameAt(NOW, COOLDOWN)).isTrue();
	}

	@Test
	@DisplayName("UNIT-013: 상태 전이와 프로필 이미지 변경은 마지막 닉네임 변경 시각을 유지한다")
	void otherTransitionsKeepNicknameChangedAt() {
		Account changed = account().withNicknameChangedAt(NOW);

		assertThat(changed.block().getNicknameChangedAt()).isEqualTo(NOW);
		assertThat(changed.withProfileImage(3L).getNicknameChangedAt()).isEqualTo(NOW);
		assertThat(changed.updateProfile("KR", "en-US", "UTC", "다른닉네임").getNicknameChangedAt()).isEqualTo(NOW);
	}

	private static Account account() {
		return Account.restore(1L, AccountRole.USER, AccountStatus.ACTIVE, "KR", "KR", "ko-KR", "Asia/Seoul",
				"기존닉네임", null);
	}
}
