package com.dnd.qello.question.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.domain.QuestionAssignment;
import com.dnd.qello.question.domain.QuestionAssignmentCycle;
import com.dnd.qello.question.domain.QuestionAssignmentCycleStatus;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.domain.QuestionProposalReview;

/**
 * Created at: 2026-08-03T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-38-QUESTION-PERSISTENCE-UNIT-004
 */
class QuestionJpaMapperTest {

	private static final Instant FIRST = Instant.parse("2026-08-03T09:00:00Z");
	private static final Instant SECOND = Instant.parse("2026-08-03T10:00:00Z");

	@Test
	@DisplayName("5개 질문 persistence model은 scalar ID, enum, nullable Instant를 왕복 보존한다")
	void mapsAllQuestionModelsWithoutValueLoss() {
		QuestionProposal proposal = QuestionProposal.restore(
			11L, 2L, com.dnd.qello.question.domain.QuestionProposalStatus.UNDER_REVIEW,
			"질문 문구", null, FIRST, FIRST, SECOND);
		QuestionProposalReview review = QuestionProposalReview.approve(11L, 3L, null, SECOND);
		ApprovedQuestion question = ApprovedQuestion.activeUserProposal(
			11L, "질문 문구", AnswerFormat.BOTH, FIRST, SECOND, FIRST, 3L);
		QuestionAssignmentCycle cycle = QuestionAssignmentCycle.restore(
			21L, 2L, "cycle-key", "pool-v1", QuestionAssignmentCycleStatus.ACTIVE, FIRST, SECOND, FIRST);
		QuestionAssignment assignment = QuestionAssignment.restore(
			31L, 21L, 41L, 2, SECOND, SECOND, null);

		assertThat(QuestionJpaMapper.toDomain(QuestionJpaMapper.toEntity(proposal)))
			.usingRecursiveComparison().isEqualTo(proposal);
		assertThat(QuestionJpaMapper.toDomain(QuestionJpaMapper.toEntity(review)))
			.usingRecursiveComparison().isEqualTo(review);
		assertThat(QuestionJpaMapper.toDomain(QuestionJpaMapper.toEntity(question)))
			.usingRecursiveComparison().isEqualTo(question);
		assertThat(QuestionJpaMapper.toDomain(QuestionJpaMapper.toEntity(cycle)))
			.usingRecursiveComparison().isEqualTo(cycle);
		assertThat(QuestionJpaMapper.toDomain(QuestionJpaMapper.toEntity(assignment)))
			.usingRecursiveComparison().isEqualTo(assignment);
	}
}
