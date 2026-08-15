package com.dnd.qello.question.repository.jpa;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dnd.qello.question.domain.ApprovedQuestionStatus;

interface SpringDataQuestionProposalRepository extends JpaRepository<QuestionProposalJpaEntity, Long> {

	List<QuestionProposalJpaEntity> findAllByProposerIdOrderByCreatedAtDesc(long proposerId);
}

interface SpringDataQuestionProposalReviewRepository extends JpaRepository<QuestionProposalReviewJpaEntity, Long> {

	List<QuestionProposalReviewJpaEntity> findAllByProposalIdOrderByReviewedAtAsc(long proposalId);
}

interface SpringDataApprovedQuestionRepository extends JpaRepository<ApprovedQuestionJpaEntity, Long> {

	@Query("""
		select question
		from ApprovedQuestionJpaEntity question
		where question.status = :status
		  and question.activeFrom <= :at
		  and (question.activeUntil is null or question.activeUntil > :at)
		order by question.id
		""")
	List<ApprovedQuestionJpaEntity> findAssignableAt(
		@Param("status") ApprovedQuestionStatus status, @Param("at") Instant at);
}

interface SpringDataQuestionAssignmentCycleRepository extends JpaRepository<QuestionAssignmentCycleJpaEntity, Long> {
}

interface SpringDataQuestionAssignmentRepository extends JpaRepository<QuestionAssignmentJpaEntity, Long> {

	List<QuestionAssignmentJpaEntity> findAllByCycleIdOrderByDisplayOrderAsc(long cycleId);
}
