/**
 * Created at: 2026-08-16T15:02:00+09:00
 * Source scenario: TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-001,
 * UNIT-010, UNIT-012
 */
package com.dnd.qello.feed.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.feed.service.InboxApplicationService;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.web.response.InboxCommandResponse;
import com.dnd.qello.feed.web.response.InboxDetailResponse;
import com.dnd.qello.feed.web.response.InboxListingResponse;

class InboxWebContractTest {

	@Test
	@DisplayName("수신함 API는 ApiSpec과 Controller를 분리하고 승인된 의존성만 생성자로 받는다")
	void keepsApiBoundaryTypesSeparated() throws Exception {
		assertThat(InboxApiSpec.class.isAssignableFrom(InboxController.class)).isTrue();
		assertThat(InboxController.class.isAnnotationPresent(RestController.class)).isTrue();
		assertThat(InboxController.class.getAnnotation(RequestMapping.class).value())
			.containsExactly("/api/v1/direction");
		assertThat(InboxController.class.getConstructor(InboxApplicationService.class, ApiResponseFactory.class)).isNotNull();
		assertThat(InboxListingResponse.class.getPackageName()).isEqualTo("com.dnd.qello.feed.web.response");
		assertThat(InboxDetailResponse.class.getPackageName()).isEqualTo("com.dnd.qello.feed.web.response");
		assertThat(InboxCommandResponse.class.getPackageName()).isEqualTo("com.dnd.qello.feed.web.response");
	}

	@Test
	@DisplayName("수신함 ApiSpec은 목록 상세 넘김과 되돌리기 경로 및 인증 입력을 선언한다")
	void declaresApprovedMappingsAndOnlyQueryFilters() throws Exception {
		assertThat(InboxApiSpec.class.getMethod("list", InboxCategory.class, String.class, Authentication.class)
			.getAnnotation(GetMapping.class).value()).containsExactly("/inbox");
		assertThat(InboxApiSpec.class.getMethod("list", InboxCategory.class, String.class, Authentication.class)
			.getParameters()[0].getAnnotation(RequestParam.class).defaultValue()).isEqualTo("UNANSWERED");
		assertThat(InboxApiSpec.class.getMethod("list", InboxCategory.class, String.class, Authentication.class)
			.getParameters()[1].getAnnotation(RequestParam.class).required()).isFalse();
		assertThat(InboxApiSpec.class.getMethod("detail", long.class, Authentication.class)
			.getAnnotation(GetMapping.class).value()).containsExactly("/inbox/{postRecipientId}");
		assertThat(InboxApiSpec.class.getMethod("skip", long.class, Authentication.class)
			.getAnnotation(PutMapping.class).value()).containsExactly("/inbox/{postRecipientId}/skip");
		assertThat(InboxApiSpec.class.getMethod("revertSkip", long.class, Authentication.class)
			.getAnnotation(DeleteMapping.class).value()).containsExactly("/inbox/{postRecipientId}/skip");
	}

	@Test
	@DisplayName("수신함 응답은 정확 좌표와 내부 사용자 식별자를 record component로 노출하지 않는다")
	void responsesContainOnlyPrivacySafeComponents() {
		assertThat(recordComponentNames(InboxListingResponse.class)).noneMatch(InboxWebContractTest::containsSensitiveToken);
		assertThat(recordComponentNames(InboxDetailResponse.class)).noneMatch(InboxWebContractTest::containsSensitiveToken);
		assertThat(recordComponentNames(InboxCommandResponse.class)).noneMatch(InboxWebContractTest::containsSensitiveToken);
	}

	private static List<String> recordComponentNames(Class<?> type) {
		return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
	}

	private static boolean containsSensitiveToken(String name) {
		if (name.equals("postRecipientId")) {
			return false;
		}
		String lower = name.toLowerCase();
		return lower.contains("userid") || lower.contains("recipientid") || lower.contains("senderid")
			|| lower.contains("latitude") || lower.contains("longitude") || lower.contains("coordinate")
			|| lower.contains("storage") || lower.contains("url") || lower.contains("outbox");
	}
}
