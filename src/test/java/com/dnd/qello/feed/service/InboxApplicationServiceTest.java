/**
 * Created at: 2026-08-16T14:36:00+09:00
 * Source scenario: TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-003 through UNIT-011,
 * TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-UNIT-006
 */
package com.dnd.qello.feed.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dnd.qello.direction.config.SkipConfirmationProperties;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.service.PostRecipientService;
import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.view.InboxDetail;
import com.dnd.qello.feed.view.InboxListing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboxApplicationServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-16T05:00:00Z");
	private static final long RECIPIENT_ID = 11L;
	private static final long POST_RECIPIENT_ID = 101L;

	@Mock
	private AccountEligibilityGate accountEligibilityGate;
	@Mock
	private InboxQueryService queryService;
	@Mock
	private PostRecipientService postRecipientService;

	private InboxApplicationService service;

	@BeforeEach
	void setUp() {
		service = new InboxApplicationService(accountEligibilityGate, queryService, postRecipientService,
				Clock.fixed(NOW, ZoneOffset.UTC), new SkipConfirmationProperties(5));
	}

	@Test
	@DisplayName("존재하지 않는 수신자 계정은 INBOX_ACCOUNT_NOT_FOUND로 거부한다")
	void rejectsUnknownAccount() {
		doThrow(new FeedException(FeedErrorCode.INBOX_ACCOUNT_NOT_FOUND))
				.when(accountEligibilityGate).require(RECIPIENT_ID);

		assertThatThrownBy(() -> service.list(RECIPIENT_ID, InboxCategory.UNANSWERED, null))
				.isInstanceOf(FeedException.class)
				.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INBOX_ACCOUNT_NOT_FOUND);
		verify(queryService, never()).list(any(Long.TYPE), any(), any(), any());
	}

	@Test
	@DisplayName("ACTIVE USER가 아닌 계정은 INBOX_ACCOUNT_NOT_ELIGIBLE로 거부한다")
	void rejectsIneligibleAccount() {
		doThrow(new FeedException(FeedErrorCode.INBOX_ACCOUNT_NOT_ELIGIBLE))
				.when(accountEligibilityGate).require(RECIPIENT_ID);

		assertThatThrownBy(() -> service.list(RECIPIENT_ID, InboxCategory.UNANSWERED, "N"))
				.isInstanceOf(FeedException.class)
				.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INBOX_ACCOUNT_NOT_ELIGIBLE);
	}

	@Test
	@DisplayName("목록 조회는 서버 Clock의 한 시각과 인증 사용자만 query service에 전달한다")
	void listUsesSingleServerInstant() {
		InboxListing listing = new InboxListing(java.util.List.of(), java.util.List.of());
		when(queryService.list(RECIPIENT_ID, InboxCategory.ANSWERED, "N", NOW)).thenReturn(listing);

		assertThat(service.list(RECIPIENT_ID, InboxCategory.ANSWERED, "N")).isSameAs(listing);
		verify(queryService).list(RECIPIENT_ID, InboxCategory.ANSWERED, "N", NOW);
	}

	@Test
	@DisplayName("상세 조회는 소유자 계정 검증 후 OPENED 전이와 상세 조회를 같은 시각으로 위임한다")
	void detailOpensAndQueriesAtSameInstant() {
		InboxDetail detail = new InboxDetail(null, NOW, null);
		when(queryService.detail(RECIPIENT_ID, POST_RECIPIENT_ID, NOW)).thenReturn(Optional.of(detail));

		assertThat(service.detail(RECIPIENT_ID, POST_RECIPIENT_ID)).isSameAs(detail);
		verify(postRecipientService).open(RECIPIENT_ID, POST_RECIPIENT_ID, NOW);
		verify(queryService).detail(RECIPIENT_ID, POST_RECIPIENT_ID, NOW);
	}

	@Test
	@DisplayName("UNIT-006 상세 projection이 없으면 OPENED 위임 뒤 INBOX_ITEM_NOT_FOUND를 던진다")
	void detailThrowsWhenProjectionMissingAfterOpen() {
		when(queryService.detail(RECIPIENT_ID, POST_RECIPIENT_ID, NOW)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.detail(RECIPIENT_ID, POST_RECIPIENT_ID))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INBOX_ITEM_NOT_FOUND);
		verify(postRecipientService).open(RECIPIENT_ID, POST_RECIPIENT_ID, NOW);
	}

	@Test
	@DisplayName("넘김 요청은 수신자 소유권 검증 뒤 기존 command service에 서버 시각으로 위임한다")
	void skipDelegatesWithServerInstant() {
		PostRecipient pending = org.mockito.Mockito.mock(PostRecipient.class);
		when(postRecipientService.requestSkip(RECIPIENT_ID, POST_RECIPIENT_ID, NOW)).thenReturn(pending);

		assertThat(service.skip(RECIPIENT_ID, POST_RECIPIENT_ID)).isSameAs(pending);
		verify(postRecipientService).requestSkip(RECIPIENT_ID, POST_RECIPIENT_ID, NOW);
	}

	@Test
	@DisplayName("넘김 되돌리기는 정확한 유예 마감 시각에 conflict를 반환하고 command를 호출하지 않는다")
	void revertSkipRejectsAtGraceDeadline() {
		PostRecipient pending = PostRecipient.available(201L, RECIPIENT_ID, "NEAR",
				BigDecimal.valueOf(45), "KR-11", NOW.minusSeconds(10), BigDecimal.valueOf(225), 1000L)
				.requestSkip(NOW.minusSeconds(5));
		when(postRecipientService.findRevertCandidate(RECIPIENT_ID, POST_RECIPIENT_ID, NOW))
				.thenReturn(pending);

		assertThatThrownBy(() -> service.revertSkip(RECIPIENT_ID, POST_RECIPIENT_ID))
				.isInstanceOf(FeedException.class)
				.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INBOX_TRANSITION_CONFLICT);
		verify(postRecipientService, never()).revertSkip(RECIPIENT_ID, POST_RECIPIENT_ID, NOW);
	}

}
