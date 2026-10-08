/**
 * Created at: 2026-10-08T02:53:18+09:00
 * Source scenario: TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-006
 */
package com.dnd.qello.notification.config;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationInboxPropertiesTest {

	@Test
	@DisplayName("UNIT-006 retention을 지정하지 않으면 30일을 쓴다")
	void defaultsRetentionToThirtyDays() {
		assertThat(new NotificationInboxProperties(null).retention()).isEqualTo(Duration.ofDays(30));
	}

	@Test
	@DisplayName("UNIT-006 retention P30D는 그대로 30일이다")
	void keepsConfiguredRetention() {
		assertThat(new NotificationInboxProperties(Duration.parse("P30D")).retention())
				.isEqualTo(Duration.ofDays(30));
	}

	@Test
	@DisplayName("UNIT-006 retention이 0이면 모든 알림이 숨겨지므로 기동 단계에서 거부한다")
	void rejectsZeroRetention() {
		assertThatThrownBy(() -> new NotificationInboxProperties(Duration.parse("PT0S")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("UNIT-006 retention이 음수면 거부한다")
	void rejectsNegativeRetention() {
		assertThatThrownBy(() -> new NotificationInboxProperties(Duration.ofDays(-1)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
