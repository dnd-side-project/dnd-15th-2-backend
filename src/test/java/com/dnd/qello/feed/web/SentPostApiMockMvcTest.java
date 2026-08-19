/**
 * Created at: 2026-08-19T15:29:03+09:00
 * Source scenario: TEST-PLAN-GH-170-FEED-READ-INTERACTION-API-UNIT-013,
 * UNIT-014
 */
package com.dnd.qello.feed.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

import com.dnd.qello.common.web.MockMvcTestSupport;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;
import com.dnd.qello.feed.service.FeedInteractionApplicationService;
import com.dnd.qello.feed.view.AnswerCard;
import com.dnd.qello.feed.view.SentPostCard;
import com.dnd.qello.feed.view.SentPostDetail;
import com.dnd.qello.feed.view.SentPostFilter;

@ExtendWith(MockitoExtension.class)
class SentPostApiMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-08-19T06:00:00Z");
	private static final long SENDER_ID = 11L;
	private static final long POST_ID = 41L;

	@Mock
	private FeedInteractionApplicationService applicationService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = buildMockMvc(true);
	}

	@Test
	@DisplayName("목록은 기본 필터·limit을 인증 subject로 위임하고 privacy-safe ApiResponse를 반환한다")
	void listDelegatesDefaultsAndReturnsPrivacySafeApiResponse() throws Exception {
		when(applicationService.listSentPosts(SENDER_ID, SentPostFilter.ALL, null, null, 20))
			.thenReturn(List.of(sentPostCard()));

		mockMvc.perform(get("/api/v1/direction/posts"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.cards[0].postId").value(POST_ID))
			.andExpect(jsonPath("$.data.cards[0].senderId").doesNotExist())
			.andExpect(jsonPath("$.data.cards[0].latitude").doesNotExist());

		verify(applicationService).listSentPosts(SENDER_ID, SentPostFilter.ALL, null, null, 20);
	}

	@Test
	@DisplayName("반환 건수가 limit과 같으면 nextCursor를 채우고 적으면 null이다")
	void fillsNextCursorOnlyWhenPageIsFull() throws Exception {
		when(applicationService.listSentPosts(SENDER_ID, SentPostFilter.ALL, null, null, 1))
			.thenReturn(List.of(sentPostCard()));

		mockMvc.perform(get("/api/v1/direction/posts").param("limit", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.nextCursor.postId").value(POST_ID));

		when(applicationService.listSentPosts(SENDER_ID, SentPostFilter.ALL, null, null, 20))
			.thenReturn(List.of(sentPostCard()));

		mockMvc.perform(get("/api/v1/direction/posts"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.nextCursor").doesNotExist());
	}

	@Test
	@DisplayName("상세는 인증 subject로 위임하고 남의 질문글이면 SENT_POST_NOT_FOUND 404를 반환한다")
	void detailDelegatesAndMapsMissingPostTo404() throws Exception {
		when(applicationService.sentPostDetail(SENDER_ID, POST_ID))
			.thenReturn(new SentPostDetail(sentPostCard(), NOW));

		mockMvc.perform(get("/api/v1/direction/posts/{postId}", POST_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.card.postId").value(POST_ID))
			.andExpect(jsonPath("$.data.answersReadAt").isString());

		when(applicationService.sentPostDetail(SENDER_ID, 999L))
			.thenThrow(new FeedException(FeedErrorCode.SENT_POST_NOT_FOUND));

		mockMvc.perform(get("/api/v1/direction/posts/{postId}", 999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorDetail.code").value(FeedErrorCode.SENT_POST_NOT_FOUND.code()));
	}

	@Test
	@DisplayName("답변 목록은 200과 뷰어 기준 reactedByMe·reactionCount를 함께 반환하고 자격 없는 뷰어도 빈 목록으로 200이다")
	void answersReturnsCardsAndEmptyListForIneligibleViewer() throws Exception {
		when(applicationService.answers(SENDER_ID, POST_ID, null, null, 20)).thenReturn(List.of(answerCard()));

		mockMvc.perform(get("/api/v1/direction/posts/{postId}/answers", POST_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.answers[0].answerId").value(101))
			.andExpect(jsonPath("$.data.answers[0].reactedByMe").value(true))
			.andExpect(jsonPath("$.data.answers[0].reactionCount").value(2))
			.andExpect(jsonPath("$.data.answers[0].authorNickname").value("닉네임"));
	}

	@Test
	@DisplayName("limit·cursor 검증 실패는 애플리케이션 계층 오류 코드로 400을 반환한다")
	void mapsValidationFailuresTo400() throws Exception {
		when(applicationService.listSentPosts(SENDER_ID, SentPostFilter.ALL, null, null, 51))
			.thenThrow(new FeedException(FeedErrorCode.LIMIT_OUT_OF_RANGE));

		mockMvc.perform(get("/api/v1/direction/posts").param("limit", "51"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value(FeedErrorCode.LIMIT_OUT_OF_RANGE.code()));

		when(applicationService.listSentPosts(eq(SENDER_ID), eq(SentPostFilter.ALL), any(Instant.class), isNull(), anyInt()))
			.thenThrow(new FeedException(FeedErrorCode.CURSOR_INCOMPLETE));

		mockMvc.perform(get("/api/v1/direction/posts").param("cursorSubmittedAt", NOW.toString()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value(FeedErrorCode.CURSOR_INCOMPLETE.code()));
	}

	@Test
	@DisplayName("인증 정보가 없으면 세 GET 경로 모두 401이고 application service를 호출하지 않는다")
	void allEndpointsRequireAuthentication() throws Exception {
		MockMvc unauthenticatedMockMvc = buildMockMvc(false);

		unauthenticatedMockMvc.perform(get("/api/v1/direction/posts")).andExpect(status().isUnauthorized());
		unauthenticatedMockMvc.perform(get("/api/v1/direction/posts/{postId}", POST_ID))
			.andExpect(status().isUnauthorized());
		unauthenticatedMockMvc.perform(get("/api/v1/direction/posts/{postId}/answers", POST_ID))
			.andExpect(status().isUnauthorized());

		verify(applicationService, never()).listSentPosts(anyLong(), any(), any(), any(), anyInt());
		verify(applicationService, never()).sentPostDetail(anyLong(), anyLong());
		verify(applicationService, never()).answers(anyLong(), anyLong(), any(), any(), anyInt());
	}

	private MockMvc buildMockMvc(boolean authenticated) {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		return MockMvcTestSupport.standalone(
			new SentPostController(applicationService, new ApiResponseFactory(clock)), authenticated, SENDER_ID, clock);
	}

	private static SentPostCard sentPostCard() {
		return new SentPostCard(POST_ID, "질문", "본문", List.of(3L), "KR-11", NOW.minusSeconds(60),
			NOW.plusSeconds(3600), 2, 1, 0);
	}

	private static AnswerCard answerCard() {
		return new AnswerCard(101L, "닉네임", "KR-11", "답변 본문", List.of(), null, null, "NEAR",
			NOW.minusSeconds(10), null, true, 2);
	}
}
