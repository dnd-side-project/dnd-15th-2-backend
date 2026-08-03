package com.dnd.qello.question.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Created at: 2026-08-03T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-38-QUESTION-PERSISTENCE-UNIT-001, TEST-PLAN-GH-38-QUESTION-PERSISTENCE-UNIT-002, TEST-PLAN-GH-38-QUESTION-PERSISTENCE-UNIT-003
 */
class QuestionDomainTest {

	private static final Instant SUBMITTED_AT = Instant.parse("2026-08-03T09:00:00Z");
	private static final Instant ACTIVE_FROM = Instant.parse("2026-08-03T10:00:00Z");
	private static final Instant ACTIVE_UNTIL = Instant.parse("2026-08-03T11:00:00Z");

	@Test
	@DisplayName("QuestionProposal은 DRAFT에서 제출과 검수를 순서대로만 진행한다")
	void enforcesProposalStateTransitions() {
		QuestionProposal proposal = QuestionProposal.create(1L, "오늘 가장 인상 깊었던 장면은 무엇인가요?");

		QuestionProposal underReview = proposal.submit(SUBMITTED_AT).startReview();
		QuestionProposal rejected = underReview.reject("질문 범위가 너무 넓습니다");

		assertThat(underReview.getStatus()).isEqualTo(QuestionProposalStatus.UNDER_REVIEW);
		assertThat(rejected.getStatus()).isEqualTo(QuestionProposalStatus.REJECTED);
		assertThat(rejected.getSubmittedAt()).isEqualTo(SUBMITTED_AT);
		assertThatThrownBy(() -> proposal.startReview()).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> rejected.submit(SUBMITTED_AT)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> underReview.reject(" ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("사용자 제안 승인 질문은 활성 시작 포함 및 종료 시각 미포함 경계를 따른다")
	void appliesAssignableHalfOpenTimeRange() {
		ApprovedQuestion question = ApprovedQuestion.activeUserProposal(
			7L, "지금 보이는 풍경은 어떤가요?", AnswerFormat.BOTH,
			ACTIVE_FROM, ACTIVE_UNTIL, SUBMITTED_AT, 99L);

		assertThat(question.isAssignableAt(ACTIVE_FROM)).isTrue();
		assertThat(question.isAssignableAt(ACTIVE_UNTIL.minusNanos(1))).isTrue();
		assertThat(question.isAssignableAt(ACTIVE_UNTIL)).isFalse();
		assertThat(question.isAssignableAt(ACTIVE_FROM.minusNanos(1))).isFalse();
		assertThatThrownBy(() -> ApprovedQuestion.activeUserProposal(
			7L, "질문", AnswerFormat.TEXT, ACTIVE_UNTIL, ACTIVE_FROM, SUBMITTED_AT, 99L))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("배정 주기와 assignment는 절대 시각과 순서 및 조회 시각을 검증한다")
	void validatesCycleAndAssignmentBoundaries() {
		Instant assignedAt = Instant.parse("2026-08-03T10:30:00Z");
		QuestionAssignmentCycle cycle = QuestionAssignmentCycle.create(
			3L, "2026-08-03", "pool-v1", ACTIVE_FROM, ACTIVE_UNTIL);
		QuestionAssignment assignment = QuestionAssignment.create(cycle.getId() == null ? 1L : cycle.getId(), 5L, 1, assignedAt);

		assertThat(cycle.getStartsAt()).isEqualTo(ACTIVE_FROM);
		assertThat(cycle.getEndsAt()).isEqualTo(ACTIVE_UNTIL);
		assertThat(assignment.getAssignedAt()).isEqualTo(assignedAt);
		assertThatThrownBy(() -> QuestionAssignmentCycle.create(
			3L, "cycle", "pool", ACTIVE_UNTIL, ACTIVE_FROM))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> QuestionAssignment.create(1L, 5L, 0, assignedAt))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> QuestionAssignment.restore(
			1L, 1L, 5L, 1, assignedAt, assignedAt.minusNanos(1), null))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
