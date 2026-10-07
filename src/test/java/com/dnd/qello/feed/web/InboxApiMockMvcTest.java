/**
 * Created at: 2026-08-16T15:02:00+09:00
 * Source scenario: TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-010,
 * UNIT-012, TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-UNIT-004 (added 2026-10-02T17:02:54+09:00),
 * TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL-UNIT-008 (added 2026-10-02T17:33:30+09:00),
 * TEST-PLAN-GH-323-INBOX-UNIT-001 through UNIT-002 (added 2026-10-07T19:48:05+09:00)
 */
package com.dnd.qello.feed.web;

import java.math.BigDecimal;
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
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;
import com.dnd.qello.feed.service.InboxApplicationService;
import com.dnd.qello.feed.view.DirectionChip;
import com.dnd.qello.feed.view.InboxCard;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.view.InboxDetail;
import com.dnd.qello.feed.view.InboxListing;
import com.dnd.qello.feed.view.MediaView;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InboxApiMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-08-16T06:00:00Z");
	private static final long RECIPIENT_ID = 11L;
	private static final long POST_RECIPIENT_ID = 101L;

	@Mock
	private InboxApplicationService applicationService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = buildMockMvc(true);
	}

	@Test
	@DisplayName("category 없는 목록은 ALL과 선택 방향을 위임하고 AVAILABLE·ANSWERED 상태를 그대로 반환한다")
	void listDelegatesFiltersAndReturnsPrivacySafeApiResponse() throws Exception {
		when(applicationService.list(RECIPIENT_ID, InboxCategory.ALL, "N"))
			.thenReturn(new InboxListing(List.of(card(PostRecipientStatus.AVAILABLE), card(PostRecipientStatus.ANSWERED)), List.of(new DirectionChip("N", "북", 0, 1))));

		mockMvc.perform(get("/api/v1/direction/inbox").param("directionSegmentKey", "N"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.cards[0].postRecipientId").value(POST_RECIPIENT_ID))
			.andExpect(jsonPath("$.data.cards[0].status").value("AVAILABLE"))
			.andExpect(jsonPath("$.data.cards[1].status").value("ANSWERED"))
			.andExpect(jsonPath("$.data.chips[0].segmentKey").value("N"))
			.andExpect(jsonPath("$.data.cards[0].recipientId").doesNotExist())
			.andExpect(jsonPath("$.data.cards[0].senderId").doesNotExist())
			.andExpect(jsonPath("$.data.cards[0].latitude").doesNotExist())
			.andExpect(jsonPath("$.data.cards[0].longitude").doesNotExist());

		verify(applicationService).list(RECIPIENT_ID, InboxCategory.ALL, "N");
	}

	@Test
	@DisplayName("목록 카드의 첨부 이미지는 media의 mediaId·url·expiresAt으로 나가고 mediaIds와 storage key는 나가지 않는다")
	void listExposesMediaViewUrlsWithoutStorageKey() throws Exception {
		when(applicationService.list(RECIPIENT_ID, InboxCategory.ALL, null))
			.thenReturn(new InboxListing(List.of(card()), List.of()));

		mockMvc.perform(get("/api/v1/direction/inbox"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.cards[0].media[0].mediaId").value(3))
			.andExpect(jsonPath("$.data.cards[0].media[0].url").value("https://media.example.test/view/3?signature=test"))
			.andExpect(jsonPath("$.data.cards[0].media[0].expiresAt").value("2026-08-16T06:05:00Z"))
			.andExpect(jsonPath("$.data.cards[0].media[0].storageKey").doesNotExist())
			.andExpect(jsonPath("$.data.cards[0].mediaIds").doesNotExist());
	}

	@Test
	@DisplayName("category 파라미터는 바인딩하지 않고 방향을 생략하면 ALL 전체 목록을 조회한다")
	void ignoresCategoryParameterAndListsAllDirections() throws Exception {
		when(applicationService.list(RECIPIENT_ID, InboxCategory.ALL, null))
			.thenReturn(new InboxListing(List.of(card(PostRecipientStatus.ANSWERED)), List.of()));

		mockMvc.perform(get("/api/v1/direction/inbox").param("category", "not-a-category"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.cards[0].status").value("ANSWERED"));
		verify(applicationService).list(RECIPIENT_ID, InboxCategory.ALL, null);
	}

	@Test
	@DisplayName("상세 카드의 첨부 이미지도 media의 mediaId·url·expiresAt으로 나가고 mediaIds와 storage key는 나가지 않는다")
	void detailExposesMediaViewUrlsWithoutStorageKey() throws Exception {
		when(applicationService.detail(RECIPIENT_ID, POST_RECIPIENT_ID)).thenReturn(new InboxDetail(card(), NOW, null));

		mockMvc.perform(get("/api/v1/direction/inbox/{postRecipientId}", POST_RECIPIENT_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.card.media[0].mediaId").value(3))
			.andExpect(jsonPath("$.data.card.media[0].url").value("https://media.example.test/view/3?signature=test"))
			.andExpect(jsonPath("$.data.card.media[0].expiresAt").value("2026-08-16T06:05:00Z"))
			.andExpect(jsonPath("$.data.card.media[0].storageKey").doesNotExist())
			.andExpect(jsonPath("$.data.card.mediaIds").doesNotExist());
	}

	@Test
	@DisplayName("상세는 인증 subject로 위임하고 본문만 포함한 ApiResponse를 반환한다")
	void detailDelegatesAndDoesNotLeakExactLocationOrUsers() throws Exception {
		when(applicationService.detail(RECIPIENT_ID, POST_RECIPIENT_ID))
			.thenReturn(new InboxDetail(card(), NOW, null));

		mockMvc.perform(get("/api/v1/direction/inbox/{postRecipientId}", POST_RECIPIENT_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.card.postRecipientId").value(POST_RECIPIENT_ID))
			.andExpect(jsonPath("$.data.openedAt").isString())
			.andExpect(jsonPath("$.data.card.recipientId").doesNotExist())
			.andExpect(jsonPath("$.data.card.senderId").doesNotExist())
			.andExpect(jsonPath("$.data.card.latitude").doesNotExist())
			.andExpect(jsonPath("$.data.card.longitude").doesNotExist());

		verify(applicationService).detail(RECIPIENT_ID, POST_RECIPIENT_ID);
	}

	@Test
	@DisplayName("넘김 요청은 200 ApiResponse로 상태와 서버 계산 마감만 반환한다")
	void skipReturnsPrivacySafeCommandResponse() throws Exception {
		when(applicationService.skip(RECIPIENT_ID, POST_RECIPIENT_ID)).thenReturn(skipPending());
		when(applicationService.revertibleUntil(org.mockito.ArgumentMatchers.any(PostRecipient.class)))
			.thenReturn(NOW.plusSeconds(5));

		mockMvc.perform(put("/api/v1/direction/inbox/{postRecipientId}/skip", POST_RECIPIENT_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.postRecipientId").value(POST_RECIPIENT_ID))
			.andExpect(jsonPath("$.data.status").value("SKIP_PENDING"))
			.andExpect(jsonPath("$.data.revertibleUntil").isString())
			.andExpect(jsonPath("$.data.recipientId").doesNotExist())
			.andExpect(jsonPath("$.data.latitude").doesNotExist());

		verify(applicationService).skip(RECIPIENT_ID, POST_RECIPIENT_ID);
	}

	@Test
	@DisplayName("넘김 되돌리기는 200 ApiResponse로 복원 상태를 반환한다")
	void revertSkipReturnsCommandResponse() throws Exception {
		when(applicationService.revertSkip(RECIPIENT_ID, POST_RECIPIENT_ID)).thenReturn(openedRecipient());

		mockMvc.perform(delete("/api/v1/direction/inbox/{postRecipientId}/skip", POST_RECIPIENT_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.postRecipientId").value(POST_RECIPIENT_ID))
			.andExpect(jsonPath("$.data.status").value("OPENED"))
			.andExpect(jsonPath("$.data.recipientId").doesNotExist())
			.andExpect(jsonPath("$.data.storageUrl").doesNotExist());

		verify(applicationService).revertSkip(RECIPIENT_ID, POST_RECIPIENT_ID);
	}

	@Test
	@DisplayName("인증 정보가 없으면 네 수신함 endpoint는 401이고 application service를 호출하지 않는다")
	void allEndpointsRequireAuthentication() throws Exception {
		MockMvc unauthenticatedMockMvc = buildMockMvc(false);

		unauthenticatedMockMvc.perform(get("/api/v1/direction/inbox")).andExpect(status().isUnauthorized());
		unauthenticatedMockMvc.perform(get("/api/v1/direction/inbox/{postRecipientId}", POST_RECIPIENT_ID))
				.andExpect(status().isUnauthorized());
		unauthenticatedMockMvc.perform(put("/api/v1/direction/inbox/{postRecipientId}/skip", POST_RECIPIENT_ID))
				.andExpect(status().isUnauthorized());
		unauthenticatedMockMvc.perform(delete("/api/v1/direction/inbox/{postRecipientId}/skip", POST_RECIPIENT_ID))
				.andExpect(status().isUnauthorized());

		verify(applicationService, never()).list(anyLong(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
		verify(applicationService, never()).detail(anyLong(), anyLong());
		verify(applicationService, never()).skip(anyLong(), anyLong());
		verify(applicationService, never()).revertSkip(anyLong(), anyLong());
	}

	@Test
	@DisplayName("숨겨야 하는 수신함 항목은 상세와 명령 모두 같은 404 feed 오류 코드로 매핑한다")
	void mapsHiddenItemsToSingleNotFoundError() throws Exception {
		FeedException notFound = new FeedException(FeedErrorCode.INBOX_ITEM_NOT_FOUND);
		when(applicationService.detail(RECIPIENT_ID, POST_RECIPIENT_ID)).thenThrow(notFound);
		when(applicationService.skip(RECIPIENT_ID, POST_RECIPIENT_ID)).thenThrow(notFound);
		when(applicationService.revertSkip(RECIPIENT_ID, POST_RECIPIENT_ID)).thenThrow(notFound);

		mockMvc.perform(get("/api/v1/direction/inbox/{postRecipientId}", POST_RECIPIENT_ID))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorDetail.code").value(FeedErrorCode.INBOX_ITEM_NOT_FOUND.code()));
		mockMvc.perform(put("/api/v1/direction/inbox/{postRecipientId}/skip", POST_RECIPIENT_ID))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorDetail.code").value(FeedErrorCode.INBOX_ITEM_NOT_FOUND.code()));
		mockMvc.perform(delete("/api/v1/direction/inbox/{postRecipientId}/skip", POST_RECIPIENT_ID))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorDetail.code").value(FeedErrorCode.INBOX_ITEM_NOT_FOUND.code()));
	}

	@Test
	@DisplayName("유예 종료 뒤 되돌리기 충돌은 409 feed 오류 코드로 매핑한다")
	void mapsTransitionConflictToConflictError() throws Exception {
		when(applicationService.revertSkip(RECIPIENT_ID, POST_RECIPIENT_ID))
			.thenThrow(new FeedException(FeedErrorCode.INBOX_TRANSITION_CONFLICT));

		mockMvc.perform(delete("/api/v1/direction/inbox/{postRecipientId}/skip", POST_RECIPIENT_ID))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorDetail.code").value(FeedErrorCode.INBOX_TRANSITION_CONFLICT.code()));
	}

	private MockMvc buildMockMvc(boolean authenticated) {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		return MockMvcTestSupport.standalone(
				new InboxController(applicationService, new ApiResponseFactory(clock)), authenticated, RECIPIENT_ID,
				clock);
	}

	private static InboxCard card() {
		return card(PostRecipientStatus.OPENED);
	}

	private static InboxCard card(PostRecipientStatus status) {
		return new InboxCard(POST_RECIPIENT_ID, 71L, status, "질문", "본문", List.of(mediaView()),
				"KR-11", BigDecimal.valueOf(90), null, "NEAR", NOW.minusSeconds(60), NOW.plusSeconds(3600), 0, false, 0,
				0);
	}

	private static MediaView mediaView() {
		try {
			return new MediaView(3L, URI.create("https://media.example.test/view/3?signature=test").toURL(),
					NOW.plusSeconds(300));
		} catch (MalformedURLException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static PostRecipient skipPending() {
		return PostRecipient.restore(POST_RECIPIENT_ID, 71L, RECIPIENT_ID, PostRecipientStatus.SKIP_PENDING,
				"NEAR", BigDecimal.valueOf(270), "KR-11", NOW.minusSeconds(60), NOW.minusSeconds(30),
				NOW.minusSeconds(20),
				NOW, null, null, null, null, BigDecimal.valueOf(90), 100, null);
	}

	private static PostRecipient openedRecipient() {
		return PostRecipient.restore(POST_RECIPIENT_ID, 71L, RECIPIENT_ID, PostRecipientStatus.OPENED,
				"NEAR", BigDecimal.valueOf(270), "KR-11", NOW.minusSeconds(60), NOW.minusSeconds(30),
				NOW.minusSeconds(20),
				null, null, null, null, null, BigDecimal.valueOf(90), 100, null);
	}
}
