/**
 * Created at: 2026-08-20T19:12:00+09:00
 * Source scenario: TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-025
 */
package com.dnd.qello.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.domain.QuestionAssignment;
import com.dnd.qello.question.domain.QuestionAssignmentCycle;
import com.dnd.qello.question.domain.QuestionAssignmentCycleStatus;
import com.dnd.qello.question.repository.ApprovedQuestionRepository;
import com.dnd.qello.question.repository.QuestionAssignmentCycleRepository;
import com.dnd.qello.question.repository.QuestionAssignmentRepository;

@ExtendWith(MockitoExtension.class)
class QuestionAssignmentServiceTest {

	private static final Instant START = Instant.parse("2026-08-20T00:00:00Z");
	private static final Instant END = START.plusSeconds(3600);
	private static final long USER_ID = 42L;
	private static final long QUESTION_ID = 11L;

	@Mock private ApprovedQuestionRepository approvedQuestionRepository;
	@Mock private QuestionAssignmentCycleRepository cycleRepository;
	@Mock private QuestionAssignmentRepository assignmentRepository;
	@Mock private OutboxEventRepository outboxEventRepository;

	@Test
	@DisplayName("assignment마다 QUESTION_RECOMMENDED outbox event를 같은 배정 흐름에서 발행한다")
	void assignPublishesRecommendationEventPerAssignment() {
		QuestionAssignmentCycle cycle = QuestionAssignmentCycle.restore(
			20L, USER_ID, "cycle-177", "pool-v1", QuestionAssignmentCycleStatus.ACTIVE, START, END, START);
		QuestionAssignment assignment = QuestionAssignment.restore(30L, 20L, QUESTION_ID, 1, START, null, null);
		ApprovedQuestion question = ApprovedQuestion.restore(
			QUESTION_ID, null, com.dnd.qello.question.domain.ApprovedQuestionSourceType.OPERATOR,
			com.dnd.qello.question.domain.ApprovedQuestionStatus.ACTIVE, "질문", AnswerFormat.TEXT,
			START.minusSeconds(60), END, START.minusSeconds(60), USER_ID, START.minusSeconds(60));
		when(cycleRepository.save(any(QuestionAssignmentCycle.class))).thenReturn(cycle);
		when(approvedQuestionRepository.findAssignableAt(START)).thenReturn(List.of(question));
		when(assignmentRepository.save(any(QuestionAssignment.class))).thenReturn(assignment);
		when(outboxEventRepository.findByDedupKey("question-recommended:30")).thenReturn(Optional.empty());

		QuestionAssignmentService service = new QuestionAssignmentService(
			approvedQuestionRepository, cycleRepository, assignmentRepository, outboxEventRepository);
		QuestionAssignmentService.AssignmentBatch result = service.assign(
			new QuestionAssignmentService.CycleCommand(USER_ID, "cycle-177", "pool-v1", START, END,
				List.of(new QuestionAssignmentService.AssignmentCommand(QUESTION_ID, 1, START))));

		assertThat(result.assignments()).containsExactly(assignment);
		ArgumentCaptor<OutboxEvent> events = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository).save(events.capture());
		assertThat(events.getValue().aggregateType()).isEqualTo(OutboxAggregateType.QUESTION_ASSIGNMENT);
		assertThat(events.getValue().aggregateId()).isEqualTo(30L);
		assertThat(events.getValue().eventType()).isEqualTo(OutboxEventType.QUESTION_RECOMMENDED);
		assertThat(events.getValue().dedupKey()).isEqualTo("question-recommended:30");
	}
}
