/**
 * Created at: 2026-08-14T17:00:00+09:00
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-014
 */
package com.dnd.qello.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.answer.error.AnswerErrorCode;

/**
 * Created at: 2026-08-19T02:00:00+09:00
 * Source scenario: TEST-PLAN-GH-168-NICKNAME-DUPLICATE-MODERATION-UNIT-001, UNIT-002
 */
class ConstraintExceptionMapperTest {

	@Test
	@DisplayName("미디어 첨부 PK 경합은 미디어 상태 오류로 변환한다")
	void mapsMediaAttachmentPrimaryKeyCollision() {
		ConstraintMapping mapping = new ConstraintExceptionMapper().map("media_attachment_pkey");

		assertThat(mapping.errorCode()).isEqualTo(AnswerErrorCode.INVALID_MEDIA_STATUS);
		assertThat(mapping.field()).isEqualTo("mediaId");
		assertThat(new ConstraintExceptionMapper().knownConstraints()).contains("media_attachment_pkey");
	}

	@Test
	@DisplayName("UNIT-001: 닉네임 대소문자 무시 유일성 경합은 중복 닉네임 오류로 변환한다")
	void mapsNicknameUniquenessCollision() {
		ConstraintMapping mapping = new ConstraintExceptionMapper().map("uq_user_account_nickname_ci");

		assertThat(mapping.errorCode()).isEqualTo(AccountErrorCode.DUPLICATED_NICKNAME);
		assertThat(mapping.field()).isEqualTo("nickname");
	}

	@Test
	@DisplayName("UNIT-002: knownConstraints()가 닉네임 유일성 제약 이름을 포함한다")
	void knownConstraintsIncludesNicknameUniqueness() {
		assertThat(new ConstraintExceptionMapper().knownConstraints()).contains("uq_user_account_nickname_ci");
	}
}
