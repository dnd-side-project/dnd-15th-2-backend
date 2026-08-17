/**
 * Created at: 2026-08-17T16:20:00+09:00
 * Source scenario: TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-015
 */
package com.dnd.qello.answer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerModerationStatus;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.direction.repository.RecipientReceiveStateRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;

class AnswerNotificationServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
	private static final long ANSWER_ID = 42L;
	private static final long RECIPIENT_ID = 7L;

	private final AnswerRepository answerRepository = mock(AnswerRepository.class);
	private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
	private final PostRecipientRepository recipientRepository = mock(PostRecipientRepository.class);
	private final RecipientReceiveStateRepository receiveStateRepository = mock(RecipientReceiveStateRepository.class);

	@Test
	@DisplayName("UNIT-015: 이미 PUBLISHED인 답변을 다시 publish해도 슬롯·Outbox를 추가로 건드리지 않고 그대로 반환한다")
	void secondPublishCallIsNoOp() {
		Answer published = safetyChecking().markSafetyPassed().publish(NOW);
		when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(published));
		AnswerNotificationService service = service();

		Answer result = service.publish(ANSWER_ID, NOW.plusSeconds(10));

		assertThat(result.getStatus()).isEqualTo(AnswerStatus.PUBLISHED);
		verify(recipientRepository, never()).findById(anyLong());
		verify(answerRepository, never()).save(any());
		verify(outboxEventRepository, never()).save(any());
	}

	@Test
	@DisplayName("UNIT-015: 최초 publish는 슬롯을 확보한 뒤에만 Answer를 저장하고 Outbox를 한 번 남긴다")
	void firstPublishReleasesSlotBeforeSavingAnswer() {
		Answer safetyChecking = safetyChecking();
		when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(safetyChecking));
		PostRecipient open = PostRecipient.restore(RECIPIENT_ID, 1L, 11L, PostRecipientStatus.OPENED, "NEAR",
			BigDecimal.valueOf(45), "TEST", NOW, NOW, NOW, null, null, null, null, null, BigDecimal.valueOf(190), 5000L, null);
		when(recipientRepository.findById(RECIPIENT_ID)).thenReturn(Optional.of(open));
		when(recipientRepository.transitionToAnswered(any(), eq(PostRecipientStatus.OPENED)))
			.thenReturn(Optional.of(open.answered(NOW.plusSeconds(1))));
		when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(outboxEventRepository.findByDedupKey("answer-published:" + ANSWER_ID)).thenReturn(Optional.empty());
		AnswerNotificationService service = service();

		Answer result = service.publish(ANSWER_ID, NOW.plusSeconds(1));

		assertThat(result.getStatus()).isEqualTo(AnswerStatus.PUBLISHED);
		verify(receiveStateRepository).release(11L, NOW.plusSeconds(1));
		verify(outboxEventRepository).save(any());
	}

	@Test
	@DisplayName("UNIT-015: recipient가 이미 만료·차단·넘김확정으로 선점됐으면 공개를 거절하고 Answer를 저장하지 않는다")
	void rejectsPublishWhenRecipientAlreadyClaimedByOtherTerminalTransition() {
		Answer safetyChecking = safetyChecking();
		when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(safetyChecking));
		PostRecipient expired = PostRecipient.restore(RECIPIENT_ID, 1L, 11L, PostRecipientStatus.EXPIRED, "NEAR",
			BigDecimal.valueOf(45), "TEST", NOW, NOW, NOW, null, null, NOW, NOW, null, BigDecimal.valueOf(190), 5000L, null);
		when(recipientRepository.findById(RECIPIENT_ID)).thenReturn(Optional.of(expired));
		AnswerNotificationService service = service();

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.publish(ANSWER_ID, NOW.plusSeconds(1)))
			.isInstanceOf(com.dnd.qello.answer.error.AnswerException.class)
			.hasFieldOrPropertyWithValue(
				"errorCode", com.dnd.qello.answer.error.AnswerErrorCode.INVALID_ANSWER_STATUS);
		verify(answerRepository, never()).save(any());
		verify(outboxEventRepository, never()).save(any());
	}

	@Test
	@DisplayName("이미 REJECTED인 답변을 다시 reject해도 부수효과 없이 그대로 반환한다(멱등)")
	void secondRejectCallIsNoOp() {
		Answer rejected = safetyChecking().rejectSafety();
		when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(rejected));
		AnswerNotificationService service = service();

		Answer result = service.reject(ANSWER_ID, NOW.plusSeconds(10));

		assertThat(result.getStatus()).isEqualTo(AnswerStatus.REJECTED);
		verify(answerRepository, never()).save(any());
	}

	@Test
	@DisplayName("BLOCK 판정은 recipient·슬롯·receive state를 전혀 건드리지 않는다")
	void rejectDoesNotTouchRecipientOrSlot() {
		Answer safetyChecking = safetyChecking();
		when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(safetyChecking));
		when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		AnswerNotificationService service = service();

		Answer result = service.reject(ANSWER_ID, NOW.plusSeconds(1));

		assertThat(result.getStatus()).isEqualTo(AnswerStatus.REJECTED);
		verify(recipientRepository, never()).findById(anyLong());
		verify(receiveStateRepository, never()).release(anyLong(), any());
		verify(outboxEventRepository, never()).save(any());
	}

	private static Answer safetyChecking() {
		Answer submitted = Answer.submit(
			RECIPIENT_ID, 11L, "key", "본문", "TEST", BigDecimal.valueOf(90), "NEAR", NOW, 5000L);
		Answer withId = Answer.restore(ANSWER_ID, submitted.getPostRecipientId(), submitted.getAuthorId(),
			AnswerStatus.SUBMITTED, submitted.getIdempotencyKey(), submitted.getBodyText(),
			submitted.getCoarseRegionCode(), submitted.getBearingFromSenderDegrees(), submitted.getDistanceBand(),
			AnswerModerationStatus.PENDING, submitted.getSubmittedAt(), null, null, submitted.getDistanceM(), null, 0);
		return withId.startSafetyCheck();
	}

	private AnswerNotificationService service() {
		return new AnswerNotificationService(
			answerRepository, outboxEventRepository, recipientRepository, receiveStateRepository);
	}
}
