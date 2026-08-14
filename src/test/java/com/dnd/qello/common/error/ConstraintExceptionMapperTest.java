/**
 * Created at: 2026-08-14T17:00:00+09:00
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-014
 */
package com.dnd.qello.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.answer.error.AnswerErrorCode;

class ConstraintExceptionMapperTest {

	@Test
	@DisplayName("미디어 첨부 PK 경합은 미디어 상태 오류로 변환한다")
	void mapsMediaAttachmentPrimaryKeyCollision() {
		ConstraintMapping mapping = new ConstraintExceptionMapper().map("media_attachment_pkey");

		assertThat(mapping.errorCode()).isEqualTo(AnswerErrorCode.INVALID_MEDIA_STATUS);
		assertThat(mapping.field()).isEqualTo("mediaId");
		assertThat(new ConstraintExceptionMapper().knownConstraints()).contains("media_attachment_pkey");
	}
}
