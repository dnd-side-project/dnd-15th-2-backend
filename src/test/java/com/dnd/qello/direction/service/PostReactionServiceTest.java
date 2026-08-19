/**
 * Created at: 2026-08-19T15:16:05+09:00
 * Source scenario: TEST-PLAN-GH-170-FEED-READ-INTERACTION-API-UNIT-017,
 * UNIT-018, UNIT-019
 */
package com.dnd.qello.direction.service;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.dnd.qello.direction.domain.PostReaction;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.PostReactionRepository;
import com.dnd.qello.direction.repository.PostRecipientRepository;

@ExtendWith(MockitoExtension.class)
class PostReactionServiceTest {

	private static final long POST_ID = 41L;
	private static final long REACTOR_ID = 7L;
	private static final Instant AT = Instant.parse("2026-08-19T06:00:00Z");

	@Mock private PostReactionRepository reactionRepository;
	@Mock private PostRecipientRepository recipientRepository;
	@Mock private PlatformTransactionManager transactionManager;

	@InjectMocks private PostReactionService service;

	@Test
	@DisplayName("이미 공감한 사용자가 다시 공감해도 새 행을 저장하지 않고 같은 공감 수를 돌려준다")
	void reactIsIdempotentWhenAlreadyReacted() {
		givenEligibleReactor();
		givenReactionTransactionRunsInline();
		when(reactionRepository.exists(POST_ID, REACTOR_ID)).thenReturn(true);
		when(reactionRepository.countByPostId(POST_ID)).thenReturn(3L);

		assertThat(service.react(POST_ID, REACTOR_ID, AT)).isEqualTo(3L);

		verify(reactionRepository, never()).react(any());
	}

	@Test
	@DisplayName("공감이 없던 사용자의 공감은 한 번만 저장되고 반영된 공감 수를 돌려준다")
	void reactSavesOnceWhenNotReactedYet() {
		givenEligibleReactor();
		givenReactionTransactionRunsInline();
		when(reactionRepository.exists(POST_ID, REACTOR_ID)).thenReturn(false);
		when(reactionRepository.countByPostId(POST_ID)).thenReturn(1L);

		assertThat(service.react(POST_ID, REACTOR_ID, AT)).isEqualTo(1L);

		verify(reactionRepository).react(any(PostReaction.class));
	}

	@Test
	@DisplayName("수신 자격이 없는 사용자의 공감은 INELIGIBLE_REACTOR로 거부하고 저장을 시도하지 않는다")
	void reactRejectsIneligibleReactor() {
		when(recipientRepository.findByPostIdAndRecipientId(POST_ID, REACTOR_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.react(POST_ID, REACTOR_ID, AT))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INELIGIBLE_REACTOR);

		verify(reactionRepository, never()).react(any());
	}

	@Test
	@DisplayName("공감 취소는 수신 자격을 확인하지 않는다 — 자격을 잃은 뒤에도 자기 공감은 거둘 수 있어야 한다")
	void cancelDoesNotCheckEligibility() {
		when(reactionRepository.countByPostId(POST_ID)).thenReturn(0L);

		assertThat(service.cancel(POST_ID, REACTOR_ID)).isZero();

		verify(reactionRepository).cancel(POST_ID, REACTOR_ID);
		verify(recipientRepository, never()).findByPostIdAndRecipientId(anyLong(), anyLong());
	}

	@Test
	@DisplayName("공감이 없는 상태에서 취소를 반복해도 실패하지 않고 같은 공감 수를 돌려준다")
	void cancelIsIdempotentWhenNothingToCancel() {
		when(reactionRepository.countByPostId(POST_ID)).thenReturn(0L);

		assertThat(service.cancel(POST_ID, REACTOR_ID)).isZero();
		assertThat(service.cancel(POST_ID, REACTOR_ID)).isZero();
	}

	@Test
	@DisplayName("toggle은 공감이 없으면 남기고 있으면 취소하는 기존 동작을 유지한다")
	void toggleKeepsPreviousRoundTripBehaviour() {
		givenEligibleReactor();
		when(reactionRepository.exists(POST_ID, REACTOR_ID)).thenReturn(false, true);

		assertThat(service.toggle(POST_ID, REACTOR_ID, AT)).isTrue();
		assertThat(service.toggle(POST_ID, REACTOR_ID, AT)).isFalse();

		verify(reactionRepository).react(any(PostReaction.class));
		verify(reactionRepository).cancel(POST_ID, REACTOR_ID);
	}

	@Test
	@DisplayName("자격이 없는 사용자의 toggle도 INELIGIBLE_REACTOR로 거부한다")
	void toggleRejectsIneligibleReactor() {
		when(recipientRepository.findByPostIdAndRecipientId(POST_ID, REACTOR_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.toggle(POST_ID, REACTOR_ID, AT))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INELIGIBLE_REACTOR);
	}

	private void givenEligibleReactor() {
		when(recipientRepository.findByPostIdAndRecipientId(POST_ID, REACTOR_ID))
			.thenReturn(Optional.of(PostRecipient.available(POST_ID, REACTOR_ID, "NEAR",
				BigDecimal.valueOf(90), "KR-TEST", AT, BigDecimal.valueOf(270), 5000L)));
	}

	/** react()가 삽입 시도를 감싸는 REQUIRES_NEW TransactionTemplate이 실제 트랜잭션 없이도 콜백을 실행하게 한다. */
	private void givenReactionTransactionRunsInline() {
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
	}
}
