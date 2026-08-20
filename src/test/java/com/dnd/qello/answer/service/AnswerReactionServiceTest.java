/**
 * Created at: 2026-08-19T15:16:05+09:00
 * Source scenario: TEST-PLAN-GH-170-FEED-READ-INTERACTION-API-UNIT-017,
 * UNIT-018, UNIT-019, TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-016,
 * UNIT-017, UNIT-018, UNIT-019
 */
package com.dnd.qello.answer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerReaction;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.AnswerReactionRepository;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.feed.service.PostAnswerQueryService;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class AnswerReactionServiceTest {

	private static final long ANSWER_ID = 91L;
	private static final long POST_ID = 41L;
	private static final long POST_RECIPIENT_ID = 55L;
	private static final long AUTHOR_ID = 3L;
	private static final long REACTOR_ID = 7L;
	private static final Instant AT = Instant.parse("2026-08-19T06:00:00Z");

	@Mock private AnswerReactionRepository reactionRepository;
	@Mock private AnswerRepository answerRepository;
	@Mock private com.dnd.qello.direction.repository.PostRecipientRepository recipientRepository;
	@Mock private PostAnswerQueryService postAnswerQueryService;
	@Mock private OutboxEventRepository outboxEventRepository;
	@Mock private PlatformTransactionManager transactionManager;

	@InjectMocks private AnswerReactionService service;

	@Test
	@DisplayName("이미 공감한 사용자가 다시 공감해도 새 행을 저장하지 않고 같은 공감 수를 돌려준다")
	void reactIsIdempotentWhenAlreadyReacted() {
		givenEligibleReactor();
		givenReactionTransactionRunsInline();
		when(reactionRepository.findByAnswerIdAndReactorId(ANSWER_ID, REACTOR_ID))
			.thenReturn(Optional.of(AnswerReaction.create(ANSWER_ID, REACTOR_ID, AT)));
		when(reactionRepository.countByAnswerId(ANSWER_ID)).thenReturn(2L);

		assertThat(service.react(ANSWER_ID, REACTOR_ID, AT)).isEqualTo(2L);

		verify(reactionRepository, never()).react(any());
	}

	@Test
	@DisplayName("공감이 없던 사용자의 답변 공감은 한 번만 저장되고 반영된 공감 수를 돌려준다")
	void reactSavesOnceWhenNotReactedYet() {
		givenEligibleReactor();
		givenReactionTransactionRunsInline();
		when(reactionRepository.findByAnswerIdAndReactorId(ANSWER_ID, REACTOR_ID)).thenReturn(Optional.empty());
		when(reactionRepository.countByAnswerId(ANSWER_ID)).thenReturn(1L);

		assertThat(service.react(ANSWER_ID, REACTOR_ID, AT)).isEqualTo(1L);

		verify(reactionRepository).react(any(AnswerReaction.class));
	}

	@Test
	@DisplayName("새 답변 공감은 ANSWER_REACTED outbox event를 같은 저장 경계에서 발행한다")
	void reactPublishesAnswerReactedEventWhenReactionIsCreated() {
		givenEligibleReactor();
		givenReactionTransactionRunsInline();
		when(reactionRepository.findByAnswerIdAndReactorId(ANSWER_ID, REACTOR_ID)).thenReturn(Optional.empty());
		when(reactionRepository.countByAnswerId(ANSWER_ID)).thenReturn(1L);
		when(outboxEventRepository.findByDedupKey(any())).thenReturn(Optional.empty());

		service.react(ANSWER_ID, REACTOR_ID, AT);

		ArgumentCaptor<OutboxEvent> events = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository).save(events.capture());
		assertThat(events.getValue().aggregateType()).isEqualTo(OutboxAggregateType.ANSWER);
		assertThat(events.getValue().aggregateId()).isEqualTo(ANSWER_ID);
		assertThat(events.getValue().eventType()).isEqualTo(OutboxEventType.ANSWER_REACTED);
		assertThat(events.getValue().payload()).contains("\"answerId\":91").contains("\"reactorId\":7");
	}

	@Test
	@DisplayName("자기 답변에는 공감할 수 없다")
	void reactRejectsOwnAnswer() {
		when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer()));

		assertThatThrownBy(() -> service.react(ANSWER_ID, AUTHOR_ID, AT))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INELIGIBLE_REACTOR);

		verify(reactionRepository, never()).react(any());
	}

	@Test
	@DisplayName("그 질문글을 볼 자격이 없으면 답변에 공감할 수 없다")
	void reactRejectsViewerWithoutEligibility() {
		when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer()));
		when(recipientRepository.findById(POST_RECIPIENT_ID)).thenReturn(Optional.of(recipient()));
		when(postAnswerQueryService.canView(REACTOR_ID, POST_ID, AT)).thenReturn(false);

		assertThatThrownBy(() -> service.react(ANSWER_ID, REACTOR_ID, AT))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INELIGIBLE_REACTOR);

		verify(reactionRepository, never()).react(any());
	}

	@Test
	@DisplayName("답변 공감 취소는 답변 조회와 열람 자격 판정을 하지 않는다")
	void cancelDoesNotCheckEligibility() {
		when(reactionRepository.countByAnswerId(ANSWER_ID)).thenReturn(0L);

		assertThat(service.cancel(ANSWER_ID, REACTOR_ID)).isZero();

		verify(reactionRepository).cancel(ANSWER_ID, REACTOR_ID);
		verify(answerRepository, never()).findById(anyLong());
		verify(postAnswerQueryService, never()).canView(anyLong(), anyLong(), any());
	}

	@Test
	@DisplayName("공감이 없는 상태에서 취소를 반복해도 실패하지 않고 같은 공감 수를 돌려준다")
	void cancelIsIdempotentWhenNothingToCancel() {
		when(reactionRepository.countByAnswerId(ANSWER_ID)).thenReturn(0L);

		assertThat(service.cancel(ANSWER_ID, REACTOR_ID)).isZero();
		assertThat(service.cancel(ANSWER_ID, REACTOR_ID)).isZero();
	}

	@Test
	@DisplayName("toggle은 공감이 없으면 남기고 있으면 취소하는 기존 동작을 유지한다")
	void toggleKeepsPreviousRoundTripBehaviour() {
		givenEligibleReactor();
		when(reactionRepository.findByAnswerIdAndReactorId(ANSWER_ID, REACTOR_ID))
			.thenReturn(Optional.empty(), Optional.of(AnswerReaction.create(ANSWER_ID, REACTOR_ID, AT)));

		assertThat(service.toggle(ANSWER_ID, REACTOR_ID, AT)).isTrue();
		assertThat(service.toggle(ANSWER_ID, REACTOR_ID, AT)).isFalse();

		verify(reactionRepository).react(any(AnswerReaction.class));
		verify(reactionRepository).cancel(ANSWER_ID, REACTOR_ID);
	}

	private void givenEligibleReactor() {
		when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer()));
		when(recipientRepository.findById(POST_RECIPIENT_ID)).thenReturn(Optional.of(recipient()));
		when(postAnswerQueryService.canView(REACTOR_ID, POST_ID, AT)).thenReturn(true);
	}

	/** react()가 삽입 시도를 감싸는 REQUIRES_NEW TransactionTemplate이 실제 트랜잭션 없이도 콜백을 실행하게 한다. */
	private void givenReactionTransactionRunsInline() {
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
	}

	private static Answer answer() {
		return Answer.submit(POST_RECIPIENT_ID, AUTHOR_ID, "gh170-answer", "본문", "KR-TEST",
			BigDecimal.valueOf(45), "NEAR", AT, 4000L);
	}

	private static PostRecipient recipient() {
		return PostRecipient.available(POST_ID, AUTHOR_ID, "NEAR",
			BigDecimal.valueOf(90), "KR-TEST", AT, BigDecimal.valueOf(270), 4000L);
	}
}
