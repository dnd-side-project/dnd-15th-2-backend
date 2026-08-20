/**
 * Created at: 2026-08-20T19:16:00+09:00
 * Source scenario: TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-012,
 * UNIT-013, UNIT-020, UNIT-021, UNIT-023, UNIT-024, UNIT-026
 */
package com.dnd.qello.notification.fanout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerModerationStatus;
import com.dnd.qello.answer.domain.AnswerReaction;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.repository.AnswerReactionRepository;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionPostModerationStatus;
import com.dnd.qello.direction.domain.DirectionPostStatus;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.ApprovedQuestionSourceType;
import com.dnd.qello.question.domain.ApprovedQuestionStatus;
import com.dnd.qello.question.domain.QuestionAssignment;
import com.dnd.qello.question.domain.QuestionAssignmentCycle;
import com.dnd.qello.question.domain.QuestionAssignmentCycleStatus;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.domain.QuestionProposalStatus;
import com.dnd.qello.question.repository.QuestionAssignmentCycleRepository;
import com.dnd.qello.question.repository.QuestionAssignmentRepository;
import com.dnd.qello.question.repository.QuestionProposalRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class NotificationFanOutResolverTest {

	private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
	private static final long ANSWER_ID = 91L;
	private static final long ANSWER_AUTHOR_ID = 3L;
	private static final long QUESTION_AUTHOR_ID = 7L;
	private static final long REACTOR_ID = 9L;
	private static final long POST_ID = 41L;
	private static final long POST_RECIPIENT_ID = 55L;

	@Mock private AnswerRepository answerRepository;
	@Mock private AnswerReactionRepository reactionRepository;
	@Mock private PostRecipientRepository recipientRepository;
	@Mock private DirectionPostRepository postRepository;
	@Mock private QuestionProposalRepository proposalRepository;
	@Mock private QuestionAssignmentRepository assignmentRepository;
	@Mock private QuestionAssignmentCycleRepository cycleRepository;

	@Test
	@DisplayName("ANSWER_PUBLISHED는 질문글 작성자 한 명에게 ANSWER_RECEIVED와 answer target을 만든다")
	void answerPublishedTargetsQuestionAuthor() {
		Answer answer = answer(ANSWER_ID, ANSWER_AUTHOR_ID);
		when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer));
		when(recipientRepository.findById(POST_RECIPIENT_ID)).thenReturn(Optional.of(recipient()));
		when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post()));

		FanOutInstruction instruction = new AnswerPublishedNotificationResolver(
			answerRepository, recipientRepository, postRepository, new ObjectMapper())
			.resolve(event(OutboxAggregateType.ANSWER, ANSWER_ID, OutboxEventType.ANSWER_PUBLISHED,
				"{\"answerId\":91}"));

		assertThat(instruction.notificationType()).isEqualTo(NotificationType.ANSWER_RECEIVED);
		assertThat(instruction.recipientId()).isEqualTo(QUESTION_AUTHOR_ID);
		assertThat(instruction.actorId()).isEqualTo(ANSWER_AUTHOR_ID);
		assertThat(instruction.answerId()).isEqualTo(ANSWER_ID);
		assertThat(instruction.dedupKey()).isEqualTo("answer-received:91");
	}

	@Test
	@DisplayName("ANSWER_REACTED는 현재 reaction이 없으면 stale 알림을 만들지 않는다")
	void answerReactedSuppressesCancelledReaction() {
		when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer(ANSWER_ID, ANSWER_AUTHOR_ID)));
		when(reactionRepository.findByAnswerIdAndReactorId(ANSWER_ID, REACTOR_ID)).thenReturn(Optional.empty());

		FanOutInstruction instruction = new AnswerReactedNotificationResolver(
			answerRepository, reactionRepository, new ObjectMapper())
			.resolve(event(OutboxAggregateType.ANSWER, ANSWER_ID, OutboxEventType.ANSWER_REACTED,
				"{\"answerId\":91,\"reactorId\":9}"));

		assertThat(instruction.suppressed()).isTrue();
	}

	@Test
	@DisplayName("ANSWER_REACTED는 답변 작성자에게 answer target을 만든다")
	void answerReactedTargetsAnswerAuthor() {
		when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer(ANSWER_ID, ANSWER_AUTHOR_ID)));
		when(reactionRepository.findByAnswerIdAndReactorId(ANSWER_ID, REACTOR_ID))
			.thenReturn(Optional.of(AnswerReaction.create(ANSWER_ID, REACTOR_ID, NOW)));

		FanOutInstruction instruction = new AnswerReactedNotificationResolver(
			answerRepository, reactionRepository, new ObjectMapper())
			.resolve(event(OutboxAggregateType.ANSWER, ANSWER_ID, OutboxEventType.ANSWER_REACTED,
				"{\"answerId\":91,\"reactorId\":9}"));

		assertThat(instruction.notificationType()).isEqualTo(NotificationType.ANSWER_REACTED);
		assertThat(instruction.recipientId()).isEqualTo(ANSWER_AUTHOR_ID);
		assertThat(instruction.actorId()).isEqualTo(REACTOR_ID);
		assertThat(instruction.answerId()).isEqualTo(ANSWER_ID);
	}

	@Test
	@DisplayName("QUESTION_PROPOSAL_REVIEWED는 payload가 달라도 저장된 proposer에게 대상 없이 만든다")
	void proposalReviewUsesPersistedProposer() {
		QuestionProposal proposal = QuestionProposal.restore(12L, QUESTION_AUTHOR_ID,
			QuestionProposalStatus.APPROVED, "제안", "승인", NOW, NOW, NOW);
		when(proposalRepository.findById(12L)).thenReturn(Optional.of(proposal));

		FanOutInstruction instruction = new QuestionProposalReviewedNotificationResolver(
			proposalRepository, new ObjectMapper())
			.resolve(event(OutboxAggregateType.QUESTION_PROPOSAL, 12L,
				OutboxEventType.QUESTION_PROPOSAL_REVIEWED,
				"{\"proposalId\":12,\"proposerId\":999,\"decision\":\"APPROVED\"}"));

		assertThat(instruction.notificationType()).isEqualTo(NotificationType.QUESTION_PROPOSAL_REVIEWED);
		assertThat(instruction.recipientId()).isEqualTo(QUESTION_AUTHOR_ID);
		assertThat(instruction.answerId()).isNull();
		assertThat(instruction.actorId()).isNull();
	}

	@Test
	@DisplayName("QUESTION_RECOMMENDED는 assignment cycle owner에게 대상 없이 만든다")
	void questionRecommendedTargetsCycleOwner() {
		QuestionAssignment assignment = QuestionAssignment.restore(30L, 20L, 11L, 1, NOW, null, null);
		QuestionAssignmentCycle cycle = QuestionAssignmentCycle.restore(20L, QUESTION_AUTHOR_ID,
			"cycle", "pool", QuestionAssignmentCycleStatus.ACTIVE, NOW, NOW.plusSeconds(3600), NOW);
		when(assignmentRepository.findById(30L)).thenReturn(Optional.of(assignment));
		when(cycleRepository.findById(20L)).thenReturn(Optional.of(cycle));

		FanOutInstruction instruction = new QuestionRecommendedNotificationResolver(
			assignmentRepository, cycleRepository, new ObjectMapper())
			.resolve(event(OutboxAggregateType.QUESTION_ASSIGNMENT, 30L,
				OutboxEventType.QUESTION_RECOMMENDED, "{\"assignmentId\":30}"));

		assertThat(instruction.notificationType()).isEqualTo(NotificationType.QUESTION_RECOMMENDED);
		assertThat(instruction.recipientId()).isEqualTo(QUESTION_AUTHOR_ID);
		assertThat(instruction.actorId()).isNull();
		assertThat(instruction.answerId()).isNull();
	}

	private static OutboxEvent event(OutboxAggregateType aggregateType, long aggregateId,
		OutboxEventType eventType, String payload) {
		return OutboxEvent.pending(aggregateType, aggregateId, eventType,
			"event:" + aggregateId + ":" + eventType, payload, NOW).claimed("worker", NOW, NOW.plusSeconds(30));
	}

	private static Answer answer(long id, long authorId) {
		return Answer.restore(id, POST_RECIPIENT_ID, authorId, AnswerStatus.PUBLISHED, "answer-" + id,
			"본문", "KR", BigDecimal.TEN, "NEAR", AnswerModerationStatus.PASSED,
			NOW.minusSeconds(120), NOW.minusSeconds(60), null, 1000L, null, 0);
	}

	private static PostRecipient recipient() {
		return PostRecipient.available(POST_ID, QUESTION_AUTHOR_ID, "NEAR", BigDecimal.TEN,
			"KR", NOW.minusSeconds(180), BigDecimal.ONE, 1000L);
	}

	private static DirectionPost post() {
		return DirectionPost.restore(POST_ID, QUESTION_AUTHOR_ID, 1L, DirectionPostStatus.ACTIVE,
			"post-41", "질문", "KR", DirectionPostModerationStatus.PASSED,
			NOW.minusSeconds(300), NOW.minusSeconds(240), NOW.plusSeconds(3600), null, null);
	}
}
