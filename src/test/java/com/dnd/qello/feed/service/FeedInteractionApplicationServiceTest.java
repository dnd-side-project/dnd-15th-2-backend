/**
 * Created at: 2026-08-19T15:29:03+09:00
 * Source scenario: TEST-PLAN-GH-170-FEED-READ-INTERACTION-API-UNIT-001
 * through UNIT-009
 */
package com.dnd.qello.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dnd.qello.answer.service.AnswerReactionService;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.direction.service.PostReactionService;
import com.dnd.qello.direction.service.PostRecipientService;
import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;
import com.dnd.qello.feed.repository.PostAnswerQueryRepository.AnswerCursor;
import com.dnd.qello.feed.repository.SentPostQueryRepository.SentPostCursor;
import com.dnd.qello.feed.view.SentPostCard;
import com.dnd.qello.feed.view.SentPostDetail;
import com.dnd.qello.feed.view.SentPostFilter;

@ExtendWith(MockitoExtension.class)
class FeedInteractionApplicationServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-19T06:00:00Z");
	private static final long ACCOUNT_ID = 11L;
	private static final long POST_ID = 41L;
	private static final long POST_RECIPIENT_ID = 55L;
	private static final long ANSWER_ID = 91L;

	@Mock private AccountEligibilityGate accountEligibilityGate;
	@Mock private SentPostQueryService sentPostQueryService;
	@Mock private PostAnswerQueryService postAnswerQueryService;
	@Mock private DirectionPostService directionPostService;
	@Mock private PostRecipientService postRecipientService;
	@Mock private PostReactionService postReactionService;
	@Mock private AnswerReactionService answerReactionService;

	private FeedInteractionApplicationService service;

	@BeforeEach
	void setUp() {
		service = new FeedInteractionApplicationService(accountEligibilityGate, sentPostQueryService,
			postAnswerQueryService, directionPostService, postRecipientService, postReactionService,
			answerReactionService, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("존재하지 않는 계정은 모든 진입점을 INBOX_ACCOUNT_NOT_FOUND로 거부하고 하위 service를 호출하지 않는다")
	void rejectsUnknownAccountOnEveryEntryPoint() {
		doThrow(new FeedException(FeedErrorCode.INBOX_ACCOUNT_NOT_FOUND)).when(accountEligibilityGate).require(ACCOUNT_ID);

		assertRejectsWithoutDelegation(FeedErrorCode.INBOX_ACCOUNT_NOT_FOUND);
	}

	@Test
	@DisplayName("ACTIVE USER가 아닌 계정은 모든 진입점을 INBOX_ACCOUNT_NOT_ELIGIBLE로 거부하고 하위 service를 호출하지 않는다")
	void rejectsIneligibleAccountOnEveryEntryPoint() {
		doThrow(new FeedException(FeedErrorCode.INBOX_ACCOUNT_NOT_ELIGIBLE)).when(accountEligibilityGate).require(ACCOUNT_ID);

		assertRejectsWithoutDelegation(FeedErrorCode.INBOX_ACCOUNT_NOT_ELIGIBLE);
	}

	private void assertRejectsWithoutDelegation(FeedErrorCode expected) {
		assertThatThrownBy(() -> service.listSentPosts(ACCOUNT_ID, SentPostFilter.ALL, null, null, 20))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", expected);
		assertThatThrownBy(() -> service.sentPostDetail(ACCOUNT_ID, POST_ID))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", expected);
		assertThatThrownBy(() -> service.answers(ACCOUNT_ID, POST_ID, null, null, 20))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", expected);
		assertThatThrownBy(() -> service.markSenderAnswersRead(ACCOUNT_ID, POST_ID))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", expected);
		assertThatThrownBy(() -> service.markRecipientAnswersRead(ACCOUNT_ID, POST_RECIPIENT_ID))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", expected);
		assertThatThrownBy(() -> service.reactToPost(POST_ID, ACCOUNT_ID))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", expected);
		assertThatThrownBy(() -> service.cancelPostReaction(POST_ID, ACCOUNT_ID))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", expected);
		assertThatThrownBy(() -> service.reactToAnswer(ANSWER_ID, ACCOUNT_ID))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", expected);
		assertThatThrownBy(() -> service.cancelAnswerReaction(ANSWER_ID, ACCOUNT_ID))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", expected);

		verifyNoInteractions(sentPostQueryService, postAnswerQueryService, directionPostService,
			postRecipientService, postReactionService, answerReactionService);
	}

	@Test
	@DisplayName("limit이 0이거나 51이면 LIMIT_OUT_OF_RANGE이고, 20과 50은 그대로 위임된다")
	void validatesLimitRange() {
		assertThatThrownBy(() -> service.listSentPosts(ACCOUNT_ID, SentPostFilter.ALL, null, null, 0))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.LIMIT_OUT_OF_RANGE);
		assertThatThrownBy(() -> service.listSentPosts(ACCOUNT_ID, SentPostFilter.ALL, null, null, 51))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.LIMIT_OUT_OF_RANGE);
		verify(sentPostQueryService, never()).list(anyLong(), any(), any(), anyInt(), any());

		service.listSentPosts(ACCOUNT_ID, SentPostFilter.ALL, null, null, 50);
		verify(sentPostQueryService).list(ACCOUNT_ID, SentPostFilter.ALL, null, 50, NOW);
	}

	@Test
	@DisplayName("커서 두 파라미터 중 하나만 오면 CURSOR_INCOMPLETE이고 조회를 시도하지 않는다")
	void rejectsIncompleteCursorParams() {
		assertThatThrownBy(() -> service.listSentPosts(ACCOUNT_ID, SentPostFilter.ALL, NOW, null, 20))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.CURSOR_INCOMPLETE);
		assertThatThrownBy(() -> service.listSentPosts(ACCOUNT_ID, SentPostFilter.ALL, null, 1L, 20))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.CURSOR_INCOMPLETE);
		assertThatThrownBy(() -> service.answers(ACCOUNT_ID, POST_ID, NOW, null, 20))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.CURSOR_INCOMPLETE);
		assertThatThrownBy(() -> service.answers(ACCOUNT_ID, POST_ID, null, 1L, 20))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.CURSOR_INCOMPLETE);

		verifyNoInteractions(sentPostQueryService, postAnswerQueryService);
	}

	@Test
	@DisplayName("커서 두 파라미터가 모두 있으면 정렬 키 그대로 조합해 위임한다")
	void combinesBothCursorParamsWhenBothPresent() {
		service.listSentPosts(ACCOUNT_ID, SentPostFilter.IN_PROGRESS, NOW, 7L, 20);

		verify(sentPostQueryService).list(ACCOUNT_ID, SentPostFilter.IN_PROGRESS, new SentPostCursor(NOW, 7L), 20, NOW);
	}

	@Test
	@DisplayName("남의 질문글이거나 존재하지 않는 상세 조회는 SENT_POST_NOT_FOUND로 거부한다")
	void rejectsMissingSentPostDetail() {
		when(sentPostQueryService.detail(ACCOUNT_ID, POST_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.sentPostDetail(ACCOUNT_ID, POST_ID))
			.isInstanceOf(FeedException.class).hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.SENT_POST_NOT_FOUND);
	}

	@Test
	@DisplayName("답변 목록은 자격이 없어도 예외 없이 하위 service가 돌려준 빈 목록을 그대로 반환한다")
	void answersPassesThroughEmptyListWithoutException() {
		when(postAnswerQueryService.answers(ACCOUNT_ID, POST_ID, null, 20, NOW)).thenReturn(List.of());

		assertThat(service.answers(ACCOUNT_ID, POST_ID, null, null, 20)).isEmpty();
	}

	@Test
	@DisplayName("읽음 처리 2종은 서버 Clock의 한 시각으로 각자 소유 service에 위임한다")
	void marksAnswersReadWithServerInstant() {
		DirectionPost post = mock(DirectionPost.class);
		when(post.getAnswersReadAt()).thenReturn(NOW);
		when(directionPostService.markAnswersRead(ACCOUNT_ID, POST_ID, NOW)).thenReturn(post);
		PostRecipient recipient = mock(PostRecipient.class);
		when(recipient.getAnswersReadAt()).thenReturn(NOW);
		when(postRecipientService.markAnswersRead(ACCOUNT_ID, POST_RECIPIENT_ID, NOW)).thenReturn(recipient);

		assertThat(service.markSenderAnswersRead(ACCOUNT_ID, POST_ID)).isEqualTo(NOW);
		assertThat(service.markRecipientAnswersRead(ACCOUNT_ID, POST_RECIPIENT_ID)).isEqualTo(NOW);
		verify(directionPostService).markAnswersRead(ACCOUNT_ID, POST_ID, NOW);
		verify(postRecipientService).markAnswersRead(ACCOUNT_ID, POST_RECIPIENT_ID, NOW);
	}

	@Test
	@DisplayName("공감 4개 진입점은 계정 게이트를 통과한 뒤 소유 service에 서버 시각과 함께 위임한다")
	void delegatesReactionEntryPointsAfterAccountGate() {
		when(postReactionService.react(POST_ID, ACCOUNT_ID, NOW)).thenReturn(3L);
		when(postReactionService.cancel(POST_ID, ACCOUNT_ID)).thenReturn(2L);
		when(answerReactionService.react(ANSWER_ID, ACCOUNT_ID, NOW)).thenReturn(5L);
		when(answerReactionService.cancel(ANSWER_ID, ACCOUNT_ID)).thenReturn(4L);

		assertThat(service.reactToPost(POST_ID, ACCOUNT_ID)).isEqualTo(3L);
		assertThat(service.cancelPostReaction(POST_ID, ACCOUNT_ID)).isEqualTo(2L);
		assertThat(service.reactToAnswer(ANSWER_ID, ACCOUNT_ID)).isEqualTo(5L);
		assertThat(service.cancelAnswerReaction(ANSWER_ID, ACCOUNT_ID)).isEqualTo(4L);
	}

	@Test
	@DisplayName("커서가 없는 목록·답변 조회는 null 커서로 위임한다")
	void passesNullCursorWhenNoCursorParamsGiven() {
		SentPostCard card = new SentPostCard(POST_ID, "질문", "본문", List.of(), "KR-11", NOW, NOW.plusSeconds(3600), 0, 0, 0);
		when(sentPostQueryService.detail(ACCOUNT_ID, POST_ID)).thenReturn(Optional.of(new SentPostDetail(card, null)));

		SentPostDetail detail = service.sentPostDetail(ACCOUNT_ID, POST_ID);

		assertThat(detail.card().postId()).isEqualTo(POST_ID);
		verify(postAnswerQueryService, never()).answers(anyLong(), anyLong(), any(AnswerCursor.class), anyInt(), any());
	}

}
