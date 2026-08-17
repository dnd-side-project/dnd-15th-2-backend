/**
 * Created at: 2026-08-17T16:00:00+09:00
 * Source scenario: TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-001, UNIT-002,
 * UNIT-004 through UNIT-009, UNIT-015
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
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.filtering.moderation.AnswerModerationIntake;

class AnswerSubmissionServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
	private static final long AUTHOR_ID = 11L;
	private static final long RECIPIENT_ID = 500L;

	private final AnswerRepository answerRepository = mock(AnswerRepository.class);
	private final PostRecipientRepository postRecipientRepository = mock(PostRecipientRepository.class);
	private final MediaAttachmentService mediaAttachmentService = mock(MediaAttachmentService.class);
	private final AnswerModerationIntake moderationIntake = mock(AnswerModerationIntake.class);

	@Test
	@DisplayName("UNIT-001: command이 null이면 필수값 누락 오류로 거절한다")
	void rejectsNullCommand() {
		AnswerSubmissionService service = service();

		assertThatThrownBy(() -> service.submit(AUTHOR_ID, "key", null))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.REQUIRED_VALUE_MISSING);
	}

	@Test
	@DisplayName("UNIT-001: postRecipientId가 양수가 아니면 거절한다")
	void rejectsNonPositiveRecipientId() {
		assertThatThrownBy(() -> new AnswerSubmissionService.SubmitCommand(0L, "본문", List.of(), NOW))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_ID);
	}

	@Test
	@DisplayName("UNIT-001: submittedAt이 없으면 거절한다")
	void rejectsMissingSubmittedAt() {
		assertThatThrownBy(() -> new AnswerSubmissionService.SubmitCommand(1L, "본문", List.of(), null))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.REQUIRED_VALUE_MISSING);
	}

	@Test
	@DisplayName("UNIT-002: 본문이 없거나 공백이면 거절한다")
	void rejectsBlankBody() {
		assertThatThrownBy(() -> new AnswerSubmissionService.SubmitCommand(1L, "   ", List.of(), NOW))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.REQUIRED_VALUE_MISSING);
		assertThatThrownBy(() -> new AnswerSubmissionService.SubmitCommand(1L, null, List.of(), NOW))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.REQUIRED_VALUE_MISSING);
	}

	@Test
	@DisplayName("UNIT-002: 정상 Unicode 본문은 정규화되어 저장 후보가 된다")
	void normalizesUnicodeBody() {
		AnswerSubmissionService.SubmitCommand command =
			new AnswerSubmissionService.SubmitCommand(1L, "저도 여기 자주 와요! 😊", List.of(), NOW);

		assertThat(command.bodyText()).isEqualTo("저도 여기 자주 와요! 😊");
	}

	@Test
	@DisplayName("UNIT-002: 미디어는 최대 1개까지만 허용하고 초과·비양수 ID는 거절한다")
	void rejectsInvalidMediaIdCount() {
		assertThatThrownBy(() -> new AnswerSubmissionService.SubmitCommand(1L, "본문", List.of(1L, 2L), NOW))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_VALUE_RANGE);
		assertThatThrownBy(() -> new AnswerSubmissionService.SubmitCommand(1L, "본문", List.of(-1L), NOW))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_VALUE_RANGE);

		AnswerSubmissionService.SubmitCommand zeroMedia =
			new AnswerSubmissionService.SubmitCommand(1L, "본문", null, NOW);
		assertThat(zeroMedia.mediaIds()).isEmpty();
		AnswerSubmissionService.SubmitCommand oneMedia =
			new AnswerSubmissionService.SubmitCommand(1L, "본문", List.of(9L), NOW);
		assertThat(oneMedia.mediaIds()).containsExactly(9L);
	}

	@ParameterizedTest(name = "{0} 상태의 본인 recipient는 새 답변 자격을 가진다")
	@DisplayName("UNIT-004: AVAILABLE/DISCOVERED/OPENED만 새 답변 자격을 가진다")
	@EnumSource(value = PostRecipientStatus.class, names = {"AVAILABLE", "DISCOVERED", "OPENED"})
	void acceptsOpenRecipientStatuses(PostRecipientStatus status) {
		PostRecipient recipient = recipient(status);
		when(postRecipientRepository.findInboxCommandItemForUpdate(RECIPIENT_ID, AUTHOR_ID, NOW))
			.thenReturn(Optional.of(recipient));
		when(answerRepository.findByAuthorAndIdempotencyKey(AUTHOR_ID, "key")).thenReturn(Optional.empty());
		when(answerRepository.save(any())).thenAnswer(inv -> withId((Answer) inv.getArgument(0), 900L));
		AnswerSubmissionService service = service();

		Answer saved = service.submit(AUTHOR_ID, "key", command());

		assertThat(saved.getId()).isEqualTo(900L);
		verify(moderationIntake).submit(any(), any(), any(), any());
	}

	@ParameterizedTest(name = "{0}은(는) 저장 전 동일한 not-found 계열로 거절된다")
	@DisplayName("UNIT-005: SKIP_PENDING/SKIPPED/EXPIRED/BLOCKED/ANSWERED는 새 답변 자격이 없다")
	@EnumSource(value = PostRecipientStatus.class,
		names = {"SKIP_PENDING", "SKIPPED", "EXPIRED", "BLOCKED", "ANSWERED"})
	void rejectsTerminalOrPendingStatuses(PostRecipientStatus status) {
		// findInboxCommandItemForUpdate 자체가 소유권·활성 질문글·양방향 차단·만료를 SQL에서 걸러낸다.
		// 이 단위 테스트는 그 필터를 통과해 반환된 값이라도 서비스가 AVAILABLE/DISCOVERED/OPENED만
		// 최종적으로 허용함을 검증한다 — 타인 소유·존재하지 않음·차단된 질문글은 repository가 애초에
		// Optional.empty()를 반환하므로 같은 RECIPIENT_NOT_FOUND로 합류한다(아래 별도 테스트).
		when(postRecipientRepository.findInboxCommandItemForUpdate(RECIPIENT_ID, AUTHOR_ID, NOW))
			.thenReturn(Optional.of(recipient(status)));
		when(answerRepository.findByAuthorAndIdempotencyKey(AUTHOR_ID, "key")).thenReturn(Optional.empty());
		AnswerSubmissionService service = service();

		assertThatThrownBy(() -> service.submit(AUTHOR_ID, "key", command()))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.RECIPIENT_NOT_FOUND);
		verify(answerRepository, never()).save(any());
	}

	@Test
	@DisplayName("UNIT-005: 존재하지 않거나 타인 소유·차단된 질문글도 같은 RECIPIENT_NOT_FOUND 계약을 쓴다")
	void rejectsMissingOrForeignRecipientWithSameContract() {
		when(postRecipientRepository.findInboxCommandItemForUpdate(RECIPIENT_ID, AUTHOR_ID, NOW))
			.thenReturn(Optional.empty());
		when(answerRepository.findByAuthorAndIdempotencyKey(AUTHOR_ID, "key")).thenReturn(Optional.empty());
		AnswerSubmissionService service = service();

		assertThatThrownBy(() -> service.submit(AUTHOR_ID, "key", command()))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.RECIPIENT_NOT_FOUND);
	}

	@Test
	@DisplayName("UNIT-006: author/region/bearing/distance는 잠근 recipient 스냅샷에서만 가져온다")
	void derivesSnapshotFieldsFromLockedRecipientOnly() {
		PostRecipient recipient = recipient(PostRecipientStatus.OPENED);
		when(postRecipientRepository.findInboxCommandItemForUpdate(RECIPIENT_ID, AUTHOR_ID, NOW))
			.thenReturn(Optional.of(recipient));
		when(answerRepository.findByAuthorAndIdempotencyKey(AUTHOR_ID, "key")).thenReturn(Optional.empty());
		when(answerRepository.save(any())).thenAnswer(inv -> withId((Answer) inv.getArgument(0), 901L));
		AnswerSubmissionService service = service();

		service.submit(AUTHOR_ID, "key", command());

		ArgumentCaptor<Answer> captor = ArgumentCaptor.forClass(Answer.class);
		verify(answerRepository).save(captor.capture());
		Answer toSave = captor.getValue();
		assertThat(toSave.getAuthorId()).isEqualTo(AUTHOR_ID);
		assertThat(toSave.getCoarseRegionCode()).isEqualTo(recipient.getMatchedRegionCode());
		assertThat(toSave.getBearingFromSenderDegrees()).isEqualTo(recipient.getMatchedBearingDegrees());
		assertThat(toSave.getDistanceBand()).isEqualTo(recipient.getDistanceBand());
		assertThat(toSave.getDistanceM()).isEqualTo(recipient.getDistanceM());
		assertThat(toSave.getSubmittedAt()).isEqualTo(NOW);
		// SubmitAnswerRequest/SubmitCommand에는 author·time·region·bearing·distance 필드 자체가 없다
		// (answer/web/request/SubmitAnswerRequest.java 참고) — 컴파일 시점에 이미 주입 경로가 없다.
	}

	@Test
	@DisplayName("UNIT-007: 같은 author/key로 동일 fingerprint 재제출은 기존 answer를 반환하고 attach/intake를 호출하지 않는다")
	void replaysIdenticalRequestWithoutSideEffects() {
		Answer existing = Answer.submit(RECIPIENT_ID, AUTHOR_ID, "key", "본문", "TEST", BigDecimal.valueOf(90), "NEAR", NOW, 5000L);
		existing = withId(existing, 950L);
		when(answerRepository.findByAuthorAndIdempotencyKey(AUTHOR_ID, "key")).thenReturn(Optional.of(existing));
		when(mediaAttachmentService.findMediaIdsByAnswerId(950L)).thenReturn(List.of());
		AnswerSubmissionService service = service();

		Answer result = service.submit(AUTHOR_ID, "key", command());

		assertThat(result.getId()).isEqualTo(950L);
		verify(postRecipientRepository, never()).findInboxCommandItemForUpdate(anyLong(), anyLong(), any());
		verify(mediaAttachmentService, never()).attach(any());
		verify(moderationIntake, never()).submit(any(), any(), any(), any());
		verify(answerRepository, never()).save(any());
	}

	@Test
	@DisplayName("UNIT-008: 같은 key로 recipient가 다른 재제출은 IDEMPOTENCY_KEY_REUSED 409로 거절한다")
	void rejectsReplayWithDifferentRecipient() {
		Answer existing = Answer.submit(999L, AUTHOR_ID, "key", "본문", "TEST", BigDecimal.valueOf(90), "NEAR", NOW, 5000L);
		existing = withId(existing, 951L);
		when(answerRepository.findByAuthorAndIdempotencyKey(AUTHOR_ID, "key")).thenReturn(Optional.of(existing));
		AnswerSubmissionService service = service();

		assertThatThrownBy(() -> service.submit(AUTHOR_ID, "key", command()))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.IDEMPOTENCY_KEY_REUSED);
	}

	@Test
	@DisplayName("UNIT-008: 같은 key·recipient지만 본문이 다른 재제출도 IDEMPOTENCY_KEY_REUSED 409로 거절한다")
	void rejectsReplayWithDifferentBody() {
		Answer existing = Answer.submit(RECIPIENT_ID, AUTHOR_ID, "key", "원래 본문", "TEST", BigDecimal.valueOf(90), "NEAR", NOW, 5000L);
		existing = withId(existing, 952L);
		when(answerRepository.findByAuthorAndIdempotencyKey(AUTHOR_ID, "key")).thenReturn(Optional.of(existing));
		AnswerSubmissionService service = service();

		assertThatThrownBy(() -> service.submit(AUTHOR_ID, "key", command()))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.IDEMPOTENCY_KEY_REUSED);
	}

	@Test
	@DisplayName("UNIT-008: 같은 key·recipient·본문이지만 미디어 조합이 다른 재제출도 거절한다")
	void rejectsReplayWithDifferentMedia() {
		Answer existing = Answer.submit(RECIPIENT_ID, AUTHOR_ID, "key", "본문", "TEST", BigDecimal.valueOf(90), "NEAR", NOW, 5000L);
		existing = withId(existing, 953L);
		when(answerRepository.findByAuthorAndIdempotencyKey(AUTHOR_ID, "key")).thenReturn(Optional.of(existing));
		when(mediaAttachmentService.findMediaIdsByAnswerId(953L)).thenReturn(List.of(77L));
		AnswerSubmissionService service = service();

		assertThatThrownBy(() -> service.submit(AUTHOR_ID, "key", command()))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.IDEMPOTENCY_KEY_REUSED);
	}

	@Test
	@DisplayName("UNIT-009: 다른 key로 같은 recipient에 동시 삽입 경쟁이 나면 DB 예외를 그대로 전파한다(raw 노출은 상위 계층 매핑)")
	void propagatesRaceOnDuplicateActiveAnswerAsDataIntegrityViolation() {
		PostRecipient recipient = recipient(PostRecipientStatus.OPENED);
		when(postRecipientRepository.findInboxCommandItemForUpdate(RECIPIENT_ID, AUTHOR_ID, NOW))
			.thenReturn(Optional.of(recipient));
		when(answerRepository.findByAuthorAndIdempotencyKey(AUTHOR_ID, "key")).thenReturn(Optional.empty());
		when(answerRepository.save(any()))
			.thenThrow(new DataIntegrityViolationException("uq_answer_one_per_recipient"));
		AnswerSubmissionService service = service();

		assertThatThrownBy(() -> service.submit(AUTHOR_ID, "key", command()))
			.isInstanceOf(DataIntegrityViolationException.class);
		// ConstraintExceptionMapper가 이 예외를 DUPLICATE_ACTIVE_ANSWER로 매핑한다(HTTP 경계).
		// 실제 unique index 충돌은 INT-006(PostgreSQL)에서 검증한다.
	}

	private static PostRecipient recipient(PostRecipientStatus status) {
		PostRecipient available = PostRecipient.available(
			1L, RECIPIENT_ID, "NEAR", BigDecimal.valueOf(45), "TEST", NOW, BigDecimal.valueOf(190), 5000L);
		return switch (status) {
			case AVAILABLE -> withId(available, 700L);
			case DISCOVERED -> withId(available.discover(NOW), 700L);
			case OPENED -> withId(available.open(NOW), 700L);
			case ANSWERED -> withId(available.open(NOW).answered(NOW), 700L);
			case SKIP_PENDING -> withId(available.requestSkip(NOW), 700L);
			case SKIPPED -> withId(available.requestSkip(NOW).confirmSkip(NOW.plusSeconds(1)), 700L);
			case EXPIRED -> withId(available.expire(NOW), 700L);
			case BLOCKED -> withId(available.block(NOW), 700L);
		};
	}

	private static PostRecipient withId(PostRecipient recipient, long id) {
		return PostRecipient.restore(id, recipient.getPostId(), recipient.getRecipientId(), recipient.getStatus(),
			recipient.getDistanceBand(), recipient.getMatchedBearingDegrees(), recipient.getMatchedRegionCode(),
			recipient.getMatchedAt(), recipient.getDiscoveredAt(), recipient.getOpenedAt(),
			recipient.getSkipRequestedAt(), recipient.getSkippedAt(), recipient.getCapacityReleasedAt(),
			recipient.getExpiredAt(), recipient.getBlockedAt(), recipient.getInboundBearingDegrees(),
			recipient.getDistanceM(), recipient.getAnswersReadAt());
	}

	private static Answer withId(Answer answer, long id) {
		return Answer.restore(id, answer.getPostRecipientId(), answer.getAuthorId(), answer.getStatus(),
			answer.getIdempotencyKey(), answer.getBodyText(), answer.getCoarseRegionCode(),
			answer.getBearingFromSenderDegrees(), answer.getDistanceBand(), answer.getModerationStatus(),
			answer.getSubmittedAt(), answer.getPublishedAt(), answer.getDeletedAt(), answer.getDistanceM(),
			answer.getEditedAt(), answer.getEditCount());
	}

	private AnswerSubmissionService.SubmitCommand command() {
		return new AnswerSubmissionService.SubmitCommand(RECIPIENT_ID, "본문", List.of(), NOW);
	}

	private AnswerSubmissionService service() {
		PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		@SuppressWarnings("unchecked")
		ObjectProvider<AnswerModerationIntake> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(moderationIntake);
		return new AnswerSubmissionService(
			answerRepository, postRecipientRepository, mediaAttachmentService, provider, transactionManager);
	}
}
