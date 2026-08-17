package com.dnd.qello.question.repository.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.dnd.qello.question.domain.ApprovedQuestionStatus;

interface SpringDataQuestionProposalRepository extends JpaRepository<QuestionProposalJpaEntity, Long> {

	List<QuestionProposalJpaEntity> findAllByProposerIdOrderByCreatedAtDesc(long proposerId);

	// 판정 경로 전용 조회. version column이 없어 낙관적 잠금이 없으므로 행 잠금으로
	// 동시 판정을 직렬화한다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select proposal from QuestionProposalJpaEntity proposal where proposal.id = :id")
	Optional<QuestionProposalJpaEntity> findByIdForUpdate(@Param("id") long id);
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
