package com.dnd.qello.question.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.domain.QuestionAssignment;
import com.dnd.qello.question.domain.QuestionAssignmentCycle;
import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;
import com.dnd.qello.question.repository.ApprovedQuestionRepository;
import com.dnd.qello.question.repository.QuestionAssignmentCycleRepository;
import com.dnd.qello.question.repository.QuestionAssignmentRepository;

/**
 * 서버가 계산한 절대 시각을 보존하면서 cycle과 assignment 묶음을 원자적으로 저장한다.
 */
@Service
public class QuestionAssignmentService {

	private final ApprovedQuestionRepository approvedQuestionRepository;
	private final QuestionAssignmentCycleRepository cycleRepository;
	private final QuestionAssignmentRepository assignmentRepository;
	private final OutboxEventRepository outboxEventRepository;

	public QuestionAssignmentService(
		ApprovedQuestionRepository approvedQuestionRepository,
		QuestionAssignmentCycleRepository cycleRepository,
		QuestionAssignmentRepository assignmentRepository,
		OutboxEventRepository outboxEventRepository
	) {
		this.approvedQuestionRepository = approvedQuestionRepository;
		this.cycleRepository = cycleRepository;
		this.assignmentRepository = assignmentRepository;
		this.outboxEventRepository = outboxEventRepository;
	}

	@Transactional
	public AssignmentBatch assign(CycleCommand command) {
		requireValue(command, "command");
		QuestionAssignmentCycle savedCycle = cycleRepository.save(QuestionAssignmentCycle.create(
			command.userId(), command.cycleKey(), command.poolVersion(),
			command.startsAt(), command.endsAt()));
		if (savedCycle.getId() == null) {
			throw new QuestionException(
				QuestionErrorCode.ASSIGNMENT_CYCLE_NOT_PERSISTED, null, "저장된 cycle에 ID가 없습니다");
		}

		List<QuestionAssignment> assignments = command.assignments().stream()
			.map(item -> createAssignment(savedCycle, item))
			.map(assignmentRepository::save)
			.map(this::publishRecommendationEvent)
			.toList();
		return new AssignmentBatch(savedCycle, assignments);
	}

	private QuestionAssignment publishRecommendationEvent(QuestionAssignment assignment) {
		long assignmentId = requireSavedAssignmentId(assignment);
		String dedupKey = recommendationDedupKey(assignmentId);
		if (outboxEventRepository.findByDedupKey(dedupKey).isEmpty()) {
			outboxEventRepository.save(OutboxEvent.pending(
				OutboxAggregateType.QUESTION_ASSIGNMENT, assignmentId,
				OutboxEventType.QUESTION_RECOMMENDED, dedupKey,
				recommendationPayload(assignmentId), assignment.getAssignedAt()));
		}
		return assignment;
	}

	private long requireSavedAssignmentId(QuestionAssignment assignment) {
		Long assignmentId = assignment.getId();
		if (assignmentId == null) {
			throw new QuestionException(
				QuestionErrorCode.ASSIGNMENT_CYCLE_NOT_PERSISTED, null, "저장된 assignment에 ID가 없습니다");
		}
		return assignmentId;
	}

	private static String recommendationDedupKey(long assignmentId) {
		return "question-recommended:" + assignmentId;
	}

	private static String recommendationPayload(long assignmentId) {
		return "{\"assignmentId\":" + assignmentId + "}";
	}

	private QuestionAssignment createAssignment(
		QuestionAssignmentCycle cycle, AssignmentCommand command
	) {
		ApprovedQuestion question = approvedQuestionRepository.findAssignableAt(command.assignedAt()).stream()
			.filter(candidate -> candidate.getId().equals(command.approvedQuestionId()))
			.findFirst()
			.orElseThrow(() -> new QuestionException(
				QuestionErrorCode.QUESTION_NOT_ASSIGNABLE,
				"approvedQuestionId",
				"배정 시각에 활성인 질문이 아닙니다"
			));
		return QuestionAssignment.create(cycle.getId(), question.getId(),
			command.displayOrder(), command.assignedAt());
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new QuestionException(QuestionErrorCode.REQUIRED_VALUE_MISSING, field, field + "는 필수입니다");
		}
		return value;
	}

	public record CycleCommand(
		Long userId,
		String cycleKey,
		String poolVersion,
		Instant startsAt,
		Instant endsAt,
		List<AssignmentCommand> assignments
	) {
		public CycleCommand {
			assignments = List.copyOf(requireValue(assignments, "assignments"));
		}
	}

	public record AssignmentCommand(Long approvedQuestionId, int displayOrder, Instant assignedAt) {
	}

	public record AssignmentBatch(QuestionAssignmentCycle cycle, List<QuestionAssignment> assignments) {
		public AssignmentBatch {
			assignments = List.copyOf(assignments);
		}
	}
}
