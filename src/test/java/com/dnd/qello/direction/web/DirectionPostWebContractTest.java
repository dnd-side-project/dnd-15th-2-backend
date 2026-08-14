/**
 * Created at: 2026-08-14T14:10:00+09:00
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-002,
 * UNIT-010, UNIT-012, UNIT-013
 */
package com.dnd.qello.direction.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.dnd.qello.direction.web.request.SubmitDirectionPostRequest;
import com.dnd.qello.direction.web.response.DirectionPostSubmissionResponse;
import com.dnd.qello.direction.web.response.DirectionPreviewResponse;

class DirectionPostWebContractTest {

	@Test
	@DisplayName("방향 API는 ApiSpec과 Controller를 분리한다")
	void keepsApiBoundaryTypesSeparated() {
		assertThat(DirectionPostApiSpec.class.isAssignableFrom(DirectionPostController.class)).isTrue();
		assertThat(SubmitDirectionPostRequest.class.getPackageName())
			.isEqualTo("com.dnd.qello.direction.web.request");
		assertThat(DirectionPreviewResponse.class.getPackageName())
			.isEqualTo("com.dnd.qello.direction.web.response");
	}

	@Test
	@DisplayName("preview와 제출 응답은 위치·수신자·저장소 내부 값을 노출하지 않는다")
	void responsesContainOnlyPrivacySafeFields() {
		assertThat(recordComponentNames(DirectionPreviewResponse.class))
			.containsExactly("schemeId", "schemeCode", "schemeVersion", "segments");
		assertThat(recordComponentNames(DirectionPostSubmissionResponse.class))
			.containsExactly("postId", "submissionStatus", "submittedAt", "expiresAt");
		assertThat(recordComponentNames(DirectionPreviewResponse.SegmentCount.class))
			.noneMatch(name -> containsSensitiveToken(name));
		assertThat(recordComponentNames(DirectionPostSubmissionResponse.class))
			.noneMatch(name -> containsSensitiveToken(name));
	}

	@Test
	@DisplayName("제출 요청은 client user·좌표·거리·시각을 받지 않고 media를 최대 한 건으로 표현한다")
	void requestContainsOnlyClientIntent() {
		assertThat(recordComponentNames(SubmitDirectionPostRequest.class))
			.containsExactly("approvedQuestionId", "schemeId", "segmentKey", "bodyText", "mediaIds");
		SubmitDirectionPostRequest mediaOnly = new SubmitDirectionPostRequest(1L, 2L, "N", null, List.of(3L));
		assertThat(mediaOnly.mediaIds()).containsExactly(3L);
		assertThat(new SubmitDirectionPostRequest(1L, 2L, "N", "본문", null).mediaIds()).isEmpty();
	}

	@Test
	@DisplayName("ApiSpec에 preview GET과 submit POST 매핑이 선언돼 있다")
	void mappingsLiveOnApiSpec() throws Exception {
		assertThat(DirectionPostApiSpec.class.getMethod("preview", org.springframework.security.core.Authentication.class)
			.isAnnotationPresent(GetMapping.class)).isTrue();
		assertThat(DirectionPostApiSpec.class.getMethod("submit", String.class, SubmitDirectionPostRequest.class,
			org.springframework.security.core.Authentication.class).getAnnotation(PostMapping.class).value())
			.containsExactly("/posts");
	}

	private static List<String> recordComponentNames(Class<?> type) {
		return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
	}

	private static boolean containsSensitiveToken(String name) {
		String lower = name.toLowerCase();
		return lower.contains("user") || lower.contains("recipient") || lower.contains("latitude")
			|| lower.contains("longitude") || lower.contains("storage")
			|| lower.contains("url");
	}
}
