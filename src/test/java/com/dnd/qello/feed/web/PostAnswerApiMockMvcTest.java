/**
 * Created at: 2026-10-01T14:28:33+09:00
 * Source scenario: TEST-PLAN-GH-170-FEED-READ-INTERACTION-API-UNIT-013,
 * UNIT-014 (답변 목록 단언을 SentPostApiMockMvcTest에서 이전, GH-296),
 * TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-UNIT-006 (added 2026-10-02T17:02:54+09:00)
 */
package com.dnd.qello.feed.web;

import java.net.MalformedURLException;
import java.net.URI;
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
import org.springframework.test.web.servlet.MockMvc;

import com.dnd.qello.common.web.MockMvcTestSupport;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.feed.service.FeedInteractionApplicationService;
import com.dnd.qello.feed.view.AnswerCard;
import com.dnd.qello.feed.view.MediaView;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostAnswerApiMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-08-19T06:00:00Z");
	private static final long VIEWER_ID = 11L;
	private static final long POST_ID = 41L;

	@Mock
	private FeedInteractionApplicationService applicationService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = buildMockMvc(true);
	}

	@Test
	@DisplayName("답변 목록은 200과 뷰어 기준 reactedByMe·reactionCount를 함께 반환하고 자격 없는 뷰어도 빈 목록으로 200이다")
	void answersReturnsCardsAndEmptyListForIneligibleViewer() throws Exception {
		when(applicationService.answers(VIEWER_ID, POST_ID, null, null, 20)).thenReturn(List.of(answerCard()));

		mockMvc.perform(get("/api/v1/direction/posts/{postId}/answers", POST_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.answers[0].answerId").value(101))
			.andExpect(jsonPath("$.data.answers[0].reactedByMe").value(true))
			.andExpect(jsonPath("$.data.answers[0].reactionCount").value(2))
			.andExpect(jsonPath("$.data.answers[0].authorNickname").value("닉네임"));
	}

	@Test
	@DisplayName("답변의 첨부 이미지는 media의 mediaId·url·expiresAt으로 나가고 mediaIds와 storage key는 나가지 않는다")
	void answersExposeMediaViewUrlsWithoutStorageKey() throws Exception {
		when(applicationService.answers(VIEWER_ID, POST_ID, null, null, 20)).thenReturn(List.of(answerCard()));

		mockMvc.perform(get("/api/v1/direction/posts/{postId}/answers", POST_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.answers[0].media[0].mediaId").value(3))
			.andExpect(jsonPath("$.data.answers[0].media[0].url").value("https://media.example.test/view/3?signature=test"))
			.andExpect(jsonPath("$.data.answers[0].media[0].expiresAt").value("2026-08-19T06:05:00Z"))
			.andExpect(jsonPath("$.data.answers[0].media[0].storageKey").doesNotExist())
			.andExpect(jsonPath("$.data.answers[0].mediaIds").doesNotExist());
	}

	@Test
	@DisplayName("인증 정보가 없으면 답변 목록은 401이고 application service를 호출하지 않는다")
	void answersRequiresAuthentication() throws Exception {
		buildMockMvc(false).perform(get("/api/v1/direction/posts/{postId}/answers", POST_ID))
				.andExpect(status().isUnauthorized());

		verify(applicationService, never()).answers(anyLong(), anyLong(), any(), any(), anyInt());
	}

	private MockMvc buildMockMvc(boolean authenticated) {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		return MockMvcTestSupport.standalone(
				new PostAnswerController(applicationService, new ApiResponseFactory(clock)), authenticated, VIEWER_ID,
				clock);
	}

	private static AnswerCard answerCard() {
		return new AnswerCard(101L, "닉네임", "KR-11", "답변 본문", List.of(mediaView()), null, null, "NEAR",
				NOW.minusSeconds(10), null, true, 2);
	}

	private static MediaView mediaView() {
		try {
			return new MediaView(3L, URI.create("https://media.example.test/view/3?signature=test").toURL(),
					NOW.plusSeconds(300));
		} catch (MalformedURLException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
