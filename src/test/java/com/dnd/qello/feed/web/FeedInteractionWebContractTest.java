/**
 * Created at: 2026-08-19T15:29:03+09:00
 * Source scenario: TEST-PLAN-GH-170-FEED-READ-INTERACTION-API-UNIT-010,
 * UNIT-011, UNIT-012
 */
package com.dnd.qello.feed.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
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

import com.dnd.qello.answer.web.AnswerReactionApiSpec;
import com.dnd.qello.answer.web.AnswerReactionController;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.direction.web.PostReactionApiSpec;
import com.dnd.qello.direction.web.PostReactionController;
import com.dnd.qello.feed.service.FeedInteractionApplicationService;
import com.dnd.qello.feed.view.SentPostFilter;
import com.dnd.qello.feed.web.response.AnswerListingResponse;
import com.dnd.qello.feed.web.response.AnswersReadResponse;
import com.dnd.qello.feed.web.response.ReactionResponse;
import com.dnd.qello.feed.web.response.SentPostDetailResponse;
import com.dnd.qello.feed.web.response.SentPostListingResponse;

class FeedInteractionWebContractTest {

	@Test
	@DisplayName("새 읽기·상호작용 API는 ApiSpec과 Controller를 분리하고 승인된 의존성만 생성자로 받는다")
	void keepsApiBoundaryTypesSeparated() throws Exception {
		assertBoundary(SentPostApiSpec.class, SentPostController.class);
		assertBoundary(AnswerReadApiSpec.class, AnswerReadController.class);
		assertBoundary(PostReactionApiSpec.class, PostReactionController.class);
		assertBoundary(AnswerReactionApiSpec.class, AnswerReactionController.class);
	}

	private static void assertBoundary(Class<?> spec, Class<?> controller) throws NoSuchMethodException {
		assertThat(spec.isAssignableFrom(controller)).isTrue();
		assertThat(controller.isAnnotationPresent(RestController.class)).isTrue();
		assertThat(controller.getAnnotation(RequestMapping.class).value()).containsExactly("/api/v1/direction");
		assertThat(controller.getConstructor(FeedInteractionApplicationService.class, ApiResponseFactory.class)).isNotNull();
	}

	@Test
	@DisplayName("SentPostApiSpec은 목록·상세·답변 목록 경로와 기본 파라미터를 선언한다")
	void declaresSentPostMappings() throws Exception {
		var list = SentPostApiSpec.class.getMethod(
			"list", SentPostFilter.class, Instant.class, Long.class, int.class, Authentication.class);
		assertThat(list.getAnnotation(GetMapping.class).value()).containsExactly("/posts");
		assertThat(list.getParameters()[0].getAnnotation(RequestParam.class).defaultValue()).isEqualTo("ALL");
		assertThat(list.getParameters()[1].getAnnotation(RequestParam.class).required()).isFalse();
		assertThat(list.getParameters()[3].getAnnotation(RequestParam.class).defaultValue()).isEqualTo("20");

		assertThat(SentPostApiSpec.class.getMethod("detail", long.class, Authentication.class)
			.getAnnotation(GetMapping.class).value()).containsExactly("/posts/{postId}");
		assertThat(SentPostApiSpec.class.getMethod(
				"answers", long.class, Instant.class, Long.class, int.class, Authentication.class)
			.getAnnotation(GetMapping.class).value()).containsExactly("/posts/{postId}/answers");
	}

	@Test
	@DisplayName("AnswerReadApiSpec은 질문자·수신자 읽음 처리 경로를 PUT으로 선언한다")
	void declaresAnswerReadMappings() throws Exception {
		assertThat(AnswerReadApiSpec.class.getMethod("markSenderAnswersRead", long.class, Authentication.class)
			.getAnnotation(PutMapping.class).value()).containsExactly("/posts/{postId}/answers/read");
		assertThat(AnswerReadApiSpec.class.getMethod("markRecipientAnswersRead", long.class, Authentication.class)
			.getAnnotation(PutMapping.class).value()).containsExactly("/inbox/{postRecipientId}/answers/read");
	}

	@Test
	@DisplayName("공감 ApiSpec 두 종류는 같은 경로에 PUT과 DELETE를 함께 선언한다")
	void declaresReactionMappings() throws Exception {
		assertThat(PostReactionApiSpec.class.getMethod("react", long.class, Authentication.class)
			.getAnnotation(PutMapping.class).value()).containsExactly("/posts/{postId}/reaction");
		assertThat(PostReactionApiSpec.class.getMethod("cancel", long.class, Authentication.class)
			.getAnnotation(DeleteMapping.class).value()).containsExactly("/posts/{postId}/reaction");
		assertThat(AnswerReactionApiSpec.class.getMethod("react", long.class, Authentication.class)
			.getAnnotation(PutMapping.class).value()).containsExactly("/answers/{answerId}/reaction");
		assertThat(AnswerReactionApiSpec.class.getMethod("cancel", long.class, Authentication.class)
			.getAnnotation(DeleteMapping.class).value()).containsExactly("/answers/{answerId}/reaction");
	}

	@Test
	@DisplayName("새 응답은 정확 좌표와 내부 사용자 식별자를 record component로 노출하지 않는다")
	void responsesContainOnlyPrivacySafeComponents() {
		assertThat(recordComponentNames(SentPostListingResponse.class)).noneMatch(FeedInteractionWebContractTest::containsSensitiveToken);
		assertThat(recordComponentNames(SentPostListingResponse.Card.class)).noneMatch(FeedInteractionWebContractTest::containsSensitiveToken);
		assertThat(recordComponentNames(SentPostDetailResponse.class)).noneMatch(FeedInteractionWebContractTest::containsSensitiveToken);
		assertThat(recordComponentNames(AnswerListingResponse.class)).noneMatch(FeedInteractionWebContractTest::containsSensitiveToken);
		assertThat(recordComponentNames(AnswerListingResponse.Answer.class)).noneMatch(FeedInteractionWebContractTest::containsSensitiveToken);
		assertThat(recordComponentNames(AnswersReadResponse.class)).noneMatch(FeedInteractionWebContractTest::containsSensitiveToken);
		assertThat(recordComponentNames(ReactionResponse.class)).noneMatch(FeedInteractionWebContractTest::containsSensitiveToken);
	}

	private static List<String> recordComponentNames(Class<?> type) {
		return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
	}

	private static boolean containsSensitiveToken(String name) {
		String lower = name.toLowerCase();
		return lower.contains("userid") || lower.contains("recipientid") || lower.contains("senderid")
			|| lower.contains("reactorid") || lower.contains("authorid")
			|| lower.contains("latitude") || lower.contains("longitude") || lower.contains("coordinate")
			|| lower.contains("storage") || lower.contains("url") || lower.contains("outbox");
	}
}
