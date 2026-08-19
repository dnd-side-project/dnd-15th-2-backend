/**
 * Created at: 2026-08-19T15:29:03+09:00
 * Source scenario: TEST-PLAN-GH-170-FEED-READ-INTERACTION-API-UNIT-013,
 * UNIT-015
 */
package com.dnd.qello.direction.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import com.dnd.qello.common.web.MockMvcTestSupport;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.feed.service.FeedInteractionApplicationService;

@ExtendWith(MockitoExtension.class)
class PostReactionApiMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-08-19T06:00:00Z");
	private static final long REACTOR_ID = 11L;
	private static final long POST_ID = 41L;

	@Mock
	private FeedInteractionApplicationService applicationService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = buildMockMvc(true);
	}

	@Test
	@DisplayName("공감 누르기는 200과 반영된 공감 수를 반환한다")
	void reactReturnsUpdatedCount() throws Exception {
		when(applicationService.reactToPost(POST_ID, REACTOR_ID)).thenReturn(3L);

		mockMvc.perform(put("/api/v1/direction/posts/{postId}/reaction", POST_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.reacted").value(true))
			.andExpect(jsonPath("$.data.reactionCount").value(3));

		verify(applicationService).reactToPost(POST_ID, REACTOR_ID);
	}

	@Test
	@DisplayName("공감 취소는 200과 반영된 공감 수를 반환한다")
	void cancelReturnsUpdatedCount() throws Exception {
		when(applicationService.cancelPostReaction(POST_ID, REACTOR_ID)).thenReturn(0L);

		mockMvc.perform(delete("/api/v1/direction/posts/{postId}/reaction", POST_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.reacted").value(false))
			.andExpect(jsonPath("$.data.reactionCount").value(0));

		verify(applicationService).cancelPostReaction(POST_ID, REACTOR_ID);
	}

	@Test
	@DisplayName("수신 자격이 없는 공감 누르기는 403 DIR-DOM-007로 매핑한다")
	void mapsIneligibleReactorTo403() throws Exception {
		when(applicationService.reactToPost(POST_ID, REACTOR_ID)).thenThrow(
			new DirectionException(DirectionErrorCode.INELIGIBLE_REACTOR, "reactorId", "수신 자격이 없는 사용자는 공감할 수 없습니다"));

		mockMvc.perform(put("/api/v1/direction/posts/{postId}/reaction", POST_ID))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.errorDetail.code").value(DirectionErrorCode.INELIGIBLE_REACTOR.code()));
	}

	@Test
	@DisplayName("인증 정보가 없으면 공감 PUT·DELETE 모두 401이고 application service를 호출하지 않는다")
	void requiresAuthentication() throws Exception {
		MockMvc unauthenticatedMockMvc = buildMockMvc(false);

		unauthenticatedMockMvc.perform(put("/api/v1/direction/posts/{postId}/reaction", POST_ID))
			.andExpect(status().isUnauthorized());
		unauthenticatedMockMvc.perform(delete("/api/v1/direction/posts/{postId}/reaction", POST_ID))
			.andExpect(status().isUnauthorized());

		verify(applicationService, never()).reactToPost(anyLong(), anyLong());
		verify(applicationService, never()).cancelPostReaction(anyLong(), anyLong());
	}

	private MockMvc buildMockMvc(boolean authenticated) {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		return MockMvcTestSupport.standalone(
			new PostReactionController(applicationService, new ApiResponseFactory(clock)), authenticated, REACTOR_ID, clock);
	}
}
