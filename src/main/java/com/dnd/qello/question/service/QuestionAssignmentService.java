package com.dnd.qello.question.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.domain.QuestionAssignment;
import com.dnd.qello.question.domain.QuestionAssignmentCycle;
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

	public QuestionAssignmentService(
		ApprovedQuestionRepository approvedQuestionRepository,
		QuestionAssignmentCycleRepository cycleRepository,
		QuestionAssignmentRepository assignmentRepository
	) {
		this.approvedQuestionRepository = approvedQuestionRepository;
		this.cycleRepository = cycleRepository;
		this.assignmentRepository = assignmentRepository;
	}

	@Transactional
	public AssignmentBatch assign(CycleCommand command) {
		Objects.requireNonNull(command, "command는 필수입니다");
		QuestionAssignmentCycle savedCycle = cycleRepository.save(QuestionAssignmentCycle.create(
			command.userId(), command.cycleKey(), command.poolVersion(),
			command.startsAt(), command.endsAt()));
		if (savedCycle.getId() == null) {
			throw new IllegalStateException("저장된 cycle에 ID가 없습니다");
		}

		List<QuestionAssignment> assignments = command.assignments().stream()
			.map(item -> createAssignment(savedCycle, item))
			.map(assignmentRepository::save)
			.toList();
		return new AssignmentBatch(savedCycle, assignments);
	}

	private QuestionAssignment createAssignment(
		QuestionAssignmentCycle cycle, AssignmentCommand command
	) {
		ApprovedQuestion question = approvedQuestionRepository.findAssignableAt(command.assignedAt()).stream()
			.filter(candidate -> candidate.getId().equals(command.approvedQuestionId()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(
				"배정 시각에 활성인 질문이 아닙니다: " + command.approvedQuestionId()));
		return QuestionAssignment.create(cycle.getId(), question.getId(),
			command.displayOrder(), command.assignedAt());
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
			assignments = List.copyOf(Objects.requireNonNull(assignments, "assignments는 필수입니다"));
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
