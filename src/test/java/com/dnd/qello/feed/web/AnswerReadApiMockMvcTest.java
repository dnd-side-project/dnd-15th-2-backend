/**
 * Created at: 2026-08-19T15:29:03+09:00
 * Source scenario: TEST-PLAN-GH-170-FEED-READ-INTERACTION-API-UNIT-013,
 * UNIT-016
 */
package com.dnd.qello.feed.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import com.dnd.qello.feed.service.FeedInteractionApplicationService;

@ExtendWith(MockitoExtension.class)
class AnswerReadApiMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-08-19T06:00:00Z");
	private static final long USER_ID = 11L;
	private static final long POST_ID = 41L;
	private static final long POST_RECIPIENT_ID = 55L;

	@Mock
	private FeedInteractionApplicationService applicationService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = buildMockMvc(true);
	}

	@Test
	@DisplayName("질문자 읽음 처리는 200과 갱신된 읽음 시각을 반환한다")
	void marksSenderAnswersRead() throws Exception {
		when(applicationService.markSenderAnswersRead(USER_ID, POST_ID)).thenReturn(NOW);

		mockMvc.perform(put("/api/v1/direction/posts/{postId}/answers/read", POST_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.answersReadAt").isString());

		verify(applicationService).markSenderAnswersRead(USER_ID, POST_ID);
	}

	@Test
	@DisplayName("수신자 읽음 처리는 200과 갱신된 읽음 시각을 반환한다")
	void marksRecipientAnswersRead() throws Exception {
		when(applicationService.markRecipientAnswersRead(USER_ID, POST_RECIPIENT_ID)).thenReturn(NOW);

		mockMvc.perform(put("/api/v1/direction/inbox/{postRecipientId}/answers/read", POST_RECIPIENT_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.answersReadAt").isString());

		verify(applicationService).markRecipientAnswersRead(USER_ID, POST_RECIPIENT_ID);
	}

	@Test
	@DisplayName("인증 정보가 없으면 두 읽음 경로 모두 401이고 application service를 호출하지 않는다")
	void requiresAuthentication() throws Exception {
		MockMvc unauthenticatedMockMvc = buildMockMvc(false);

		unauthenticatedMockMvc.perform(put("/api/v1/direction/posts/{postId}/answers/read", POST_ID))
			.andExpect(status().isUnauthorized());
		unauthenticatedMockMvc.perform(put("/api/v1/direction/inbox/{postRecipientId}/answers/read", POST_RECIPIENT_ID))
			.andExpect(status().isUnauthorized());

		verify(applicationService, never()).markSenderAnswersRead(anyLong(), anyLong());
		verify(applicationService, never()).markRecipientAnswersRead(anyLong(), anyLong());
	}

	private MockMvc buildMockMvc(boolean authenticated) {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		return MockMvcTestSupport.standalone(
			new AnswerReadController(applicationService, new ApiResponseFactory(clock)), authenticated, USER_ID, clock);
	}
}
