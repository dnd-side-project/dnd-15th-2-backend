package com.dnd.qello.filtering.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.dnd.qello.filtering.domain.AppealCaseStatus;
import com.dnd.qello.filtering.domain.FilterTargetType;

interface SpringDataAppealCaseRepository extends JpaRepository<AppealCaseJpaEntity, Long> {

	Optional<AppealCaseJpaEntity> findByTargetTypeAndTargetIdAndFilterDecisionId(
		FilterTargetType targetType, long targetId, long filterDecisionId);

	List<AppealCaseJpaEntity> findByAppellantUserIdOrderByCreatedAtDescIdDesc(long appellantUserId);

	List<AppealCaseJpaEntity> findByStatusOrderByCreatedAtAscIdAsc(AppealCaseStatus status, Pageable pageable);

	// 검토자 결정의 경합을 직렬화한다. 잠금 없이는 두 트랜잭션이 같은 OPEN case를
	// 읽어 각각 종결 처리하고 복원 콜백을 두 번 발행할 수 있다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT a FROM AppealCaseJpaEntity a WHERE a.id = :id")
	Optional<AppealCaseJpaEntity> findByIdForUpdate(@Param("id") long id);
}
