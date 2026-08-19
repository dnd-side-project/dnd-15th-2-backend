/**
 * Created at: 2026-08-18T23:33:15+09:00
 * Source scenario: TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-001 through UNIT-004
 */
package com.dnd.qello.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;

class AccountProfileImageTest {

	@Test
	@DisplayName("프로필 이미지를 지정하면 참조만 바뀌고 나머지 프로필 값은 그대로 유지된다")
	void keepsOtherFieldsWhenProfileImageIsSet() {
		Account account = Account.createUser("KR", "KR-TEST", "ko-KR", "Asia/Seoul", "qello-user");

		Account updated = account.withProfileImage(42L);

		assertThat(updated.getProfileImageMediaId()).isEqualTo(42L);
		assertThat(updated.getCountryCode()).isEqualTo("KR");
		assertThat(updated.getCoarseRegionCode()).isEqualTo("KR-TEST");
		assertThat(updated.getLocale()).isEqualTo("ko-KR");
		assertThat(updated.getTimezone()).isEqualTo("Asia/Seoul");
		assertThat(updated.getNickname()).isEqualTo("qello-user");
		assertThat(updated.getStatus()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	@DisplayName("프로필 이미지를 해제하면 참조가 null이 되어 기본 이미지 상태로 돌아간다")
	void clearsReferenceWhenProfileImageIsRemoved() {
		Account account = Account.createUser("KR", "KR-TEST", "ko-KR", "Asia/Seoul", "qello-user")
			.withProfileImage(42L);

		Account updated = account.withoutProfileImage();

		assertThat(updated.getProfileImageMediaId()).isNull();
		assertThat(updated.getNickname()).isEqualTo("qello-user");
	}

	@Test
	@DisplayName("가입으로 만든 계정은 프로필 이미지 참조가 없어 기본 이미지 상태로 시작한다")
	void startsWithoutProfileImageOnCreation() {
		Account account = Account.createUser("KR", "KR-TEST", "ko-KR", "Asia/Seoul", null);

		assertThat(account.getProfileImageMediaId()).isNull();
	}

	@Test
	@DisplayName("0 이하의 미디어 식별자는 프로필 이미지로 지정할 수 없다")
	void rejectsNonPositiveMediaId() {
		Account account = Account.createUser("KR", "KR-TEST", "ko-KR", "Asia/Seoul", null);

		assertThatThrownBy(() -> account.withProfileImage(0L))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_ID)
			.hasFieldOrPropertyWithValue("field", "profileImageMediaId");
		assertThatThrownBy(() -> account.withProfileImage(-1L))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.INVALID_ID);
	}

	@Test
	@DisplayName("상태 전이는 프로필 이미지 참조를 보존한다")
	void preservesProfileImageAcrossStatusTransitions() {
		Account account = Account.restore(7L, AccountRole.USER, AccountStatus.ACTIVE, "KR", "KR-TEST",
			"ko-KR", "Asia/Seoul", "qello-user", null).withProfileImage(42L);

		assertThat(account.block().getProfileImageMediaId()).isEqualTo(42L);
		assertThat(account.block().unblock().getProfileImageMediaId()).isEqualTo(42L);
		assertThat(account.updateProfile("KR-NEW", "en-US", "UTC", "new").getProfileImageMediaId())
			.isEqualTo(42L);
	}
}
