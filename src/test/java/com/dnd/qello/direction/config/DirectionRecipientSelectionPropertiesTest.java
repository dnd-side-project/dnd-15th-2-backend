/**
 * Created at: 2026-08-10T23:14:20+09:00
 * Source scenario: TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-UNIT-001, UNIT-005
 */
package com.dnd.qello.direction.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DirectionRecipientSelectionPropertiesTest {

	@Test
	@DisplayName("발송별 최대 수신자 기본값 10은 사용자별 미처리 수신 상한과 별도다")
	void acceptsMvpRecipientSelectionLimit() {
		var properties = new DirectionRecipientSelectionProperties(10);

		assertThat(properties.maxRecipientsPerPost()).isEqualTo(10);
	}

	@Test
	@DisplayName("발송별 최대 수신자는 1 미만으로 설정할 수 없다")
	void rejectsNonPositiveRecipientSelectionLimit() {
		assertThatThrownBy(() -> new DirectionRecipientSelectionProperties(0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("max-recipients-per-post");
	}
}
