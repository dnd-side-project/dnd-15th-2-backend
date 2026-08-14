/**
 * Created at: 2026-08-14T14:25:00+09:00
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-001,
 * INT-004, INT-007, INT-011
 */
package com.dnd.qello.direction.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.core.MethodParameter;

import com.dnd.qello.common.error.ApiErrorResponseFactory;
import com.dnd.qello.common.error.ConstraintExceptionMapper;
import com.dnd.qello.common.web.GlobalExceptionHandler;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionPostModerationStatus;
import com.dnd.qello.direction.domain.DirectionPostStatus;
import com.dnd.qello.direction.domain.DirectionRequestFingerprint;
import com.dnd.qello.direction.service.DirectionPostApplicationService;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.direction.service.DirectionPreviewResult;
import com.dnd.qello.direction.web.request.SubmitDirectionPostRequest;

@ExtendWith(MockitoExtension.class)
class DirectionPostApiMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-08-14T05:00:00Z");

	@Mock
	private DirectionPostApplicationService applicationService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		DirectionPostController controller = new DirectionPostController(applicationService,
			new ApiResponseFactory(clock));
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
			.setCustomArgumentResolvers(new AuthenticationResolver())
			.setControllerAdvice(new GlobalExceptionHandler(
				new ApiErrorResponseFactory(clock), new ConstraintExceptionMapper()))
			.build();
	}

	@Test
	@DisplayName("preview는 200과 방향별 count만 반환하고 사용자·좌표를 반환하지 않는다")
	void previewReturnsPrivacySafeCounts() throws Exception {
		when(applicationService.preview(anyLong())).thenReturn(new DirectionPreviewResult(7L, "OCTANT", 1,
			List.of(new DirectionPreviewResult.SegmentCount("N", "북", 0, 3L))));

		mockMvc.perform(get("/api/v1/direction/preview"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.schemeId").value(7))
			.andExpect(jsonPath("$.data.segments[0].count").value(3))
			.andExpect(jsonPath("$.data.userId").doesNotExist())
			.andExpect(jsonPath("$.data.latitude").doesNotExist())
			.andExpect(jsonPath("$.data.longitude").doesNotExist());
	}

	@Test
	@DisplayName("멱등키가 없으면 제출은 400이고 application service를 호출하지 않는다")
	void submitRequiresIdempotencyKey() throws Exception {
		mockMvc.perform(post("/api/v1/direction/posts")
			.contentType("application/json")
			.content("{\"approvedQuestionId\":1,\"schemeId\":7,\"segmentKey\":\"N\",\"bodyText\":\"본문\",\"mediaIds\":[]}"))
			.andExpect(status().isBadRequest());

		verify(applicationService, never()).submit(anyLong(), any(), any());
	}

	@Test
	@DisplayName("정상 제출은 202와 SUBMITTED 및 최초 만료 시각만 반환한다")
	void submitReturnsAcceptedSnapshot() throws Exception {
		DirectionRequestFingerprint fingerprint = DirectionRequestFingerprint.create(
			1L, 7L, "N", 0L, 20_100_000L, "본문", List.of());
		DirectionPost post = DirectionPost.restore(101L, 11L, 1L, fingerprint, DirectionPostStatus.MATCHING,
			"key", "본문", "TEST", DirectionPostModerationStatus.PENDING, NOW, null,
			NOW.plusSeconds(12 * 60 * 60L), null, null);
		when(applicationService.submit(anyLong(), any(), any())).thenReturn(
			new DirectionPostService.SendResult(post, null, List.of()));

		mockMvc.perform(post("/api/v1/direction/posts")
			.header("Idempotency-Key", "key")
			.contentType("application/json")
			.content("{\"approvedQuestionId\":1,\"schemeId\":7,\"segmentKey\":\"N\",\"bodyText\":\"본문\",\"mediaIds\":[]}"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.postId").value(101))
			.andExpect(jsonPath("$.data.submissionStatus").value("SUBMITTED"))
			.andExpect(jsonPath("$.data.expiresAt").isNumber())
			.andExpect(jsonPath("$.data.recipientIds").doesNotExist())
			.andExpect(jsonPath("$.data.latitude").doesNotExist());
	}

	private static final class AuthenticationResolver implements HandlerMethodArgumentResolver {
		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return Authentication.class.isAssignableFrom(parameter.getParameterType());
		}

		@Override
		public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
			return UsernamePasswordAuthenticationToken.authenticated("11", null, List.of());
		}
	}
}
