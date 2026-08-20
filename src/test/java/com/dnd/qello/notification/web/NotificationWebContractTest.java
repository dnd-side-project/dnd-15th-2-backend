/**
 * Created at: 2026-08-20T17:00:00+09:00
 * Source scenario: TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ-UNIT-016,
 * UNIT-017
 */
package com.dnd.qello.notification.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.notification.service.NotificationInboxService;
import com.dnd.qello.notification.web.response.NotificationCardResponse;
import com.dnd.qello.notification.web.response.NotificationListingResponse;
import com.dnd.qello.notification.web.response.NotificationSeenResponse;
import com.dnd.qello.notification.web.response.NotificationTargetResponse;
import com.dnd.qello.notification.web.response.NotificationTargetSummaryResponse;
import com.dnd.qello.notification.web.response.UnreadSignalResponse;

class NotificationWebContractTest {

	@Test
	@DisplayName("NotificationApiSpec과 NotificationController는 분리되고 승인된 의존성만 생성자로 받는다")
	void keepsApiBoundaryTypesSeparated() throws Exception {
		assertThat(NotificationApiSpec.class.isAssignableFrom(NotificationController.class)).isTrue();
		assertThat(NotificationController.class.isAnnotationPresent(RestController.class)).isTrue();
		assertThat(NotificationController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/api/v1");
		assertThat(NotificationController.class.getConstructor(NotificationInboxService.class, ApiResponseFactory.class))
			.isNotNull();
	}

	@Test
	@DisplayName("경로 5개가 GET 3·PUT 2로 선언되고 limit 기본값은 20이다")
	void declaresFiveEndpointsWithDefaultLimit() throws Exception {
		var list = NotificationApiSpec.class.getMethod(
			"list", Instant.class, Long.class, int.class, Authentication.class);
		assertThat(list.getAnnotation(GetMapping.class).value()).containsExactly("/notifications");
		assertThat(list.getParameters()[0].getAnnotation(org.springframework.web.bind.annotation.RequestParam.class)
			.required()).isFalse();
		assertThat(list.getParameters()[2].getAnnotation(org.springframework.web.bind.annotation.RequestParam.class)
			.defaultValue()).isEqualTo("20");

		assertThat(NotificationApiSpec.class.getMethod("unreadCount", Authentication.class)
			.getAnnotation(GetMapping.class).value()).containsExactly("/notifications/unread-count");
		assertThat(NotificationApiSpec.class.getMethod("markSeen", Authentication.class)
			.getAnnotation(PutMapping.class).value()).containsExactly("/notifications/seen");
		assertThat(NotificationApiSpec.class.getMethod("markRead", long.class, Authentication.class)
			.getAnnotation(PutMapping.class).value()).containsExactly("/notifications/{notificationId}/read");
		assertThat(NotificationApiSpec.class.getMethod("target", long.class, Authentication.class)
			.getAnnotation(GetMapping.class).value()).containsExactly("/notifications/{notificationId}/target");
	}

	@Test
	@DisplayName("응답은 질문·답변 본문, 닉네임, 계정 식별자, 위치를 record component로 노출하지 않는다")
	void responsesContainOnlyPrivacySafeComponents() {
		assertThat(recordComponentNames(NotificationListingResponse.class)).noneMatch(this::isSensitive);
		assertThat(recordComponentNames(NotificationCardResponse.class)).noneMatch(this::isSensitive);
		assertThat(recordComponentNames(NotificationTargetSummaryResponse.class)).noneMatch(this::isSensitive);
		assertThat(recordComponentNames(UnreadSignalResponse.class)).noneMatch(this::isSensitive);
		assertThat(recordComponentNames(NotificationSeenResponse.class)).noneMatch(this::isSensitive);
		assertThat(recordComponentNames(NotificationTargetResponse.class)).noneMatch(this::isSensitive);
	}

	private static List<String> recordComponentNames(Class<?> type) {
		return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
	}

	private boolean isSensitive(String name) {
		String lower = name.toLowerCase();
		return lower.contains("body") || lower.contains("nickname") || lower.contains("recipientid")
			|| lower.contains("senderid") || lower.contains("authorid") || lower.contains("accountid")
			|| lower.contains("latitude") || lower.contains("longitude") || lower.contains("coordinate")
			|| lower.contains("bearing") || lower.contains("distance") || lower.contains("region");
	}
}
