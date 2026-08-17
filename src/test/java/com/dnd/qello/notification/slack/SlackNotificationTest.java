package com.dnd.qello.notification.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.error.NotificationException;

/**
 * Created at: 2026-08-17T16:30:00+09:00
 * Source scenario: TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-007
 */
class SlackNotificationTest {

	@Test
	@DisplayName("SlackNotification은 caseId와 adminLinkPath 두 필드만 갖는다(INV-SLK-003, INV-SLK-004)")
	void exposesOnlyAllowlistedFields() {
		List<String> fieldNames = Arrays.stream(SlackNotification.class.getDeclaredFields())
			.filter(field -> !field.isSynthetic())
			.map(Field::getName)
			.toList();

		assertThat(fieldNames).containsExactlyInAnyOrder("caseId", "adminLinkPath");
	}

	@Test
	@DisplayName("caseId가 0 이하면 생성을 거절한다")
	void rejectsNonPositiveCaseId() {
		assertThatThrownBy(() -> new SlackNotification(0L, "/admin/filtering/manual-review-cases/7"))
			.isInstanceOf(NotificationException.class);
	}

	@Test
	@DisplayName("adminLinkPath가 공백이면 생성을 거절한다")
	void rejectsBlankAdminLinkPath() {
		assertThatThrownBy(() -> new SlackNotification(7L, " "))
			.isInstanceOf(NotificationException.class);
	}
}
