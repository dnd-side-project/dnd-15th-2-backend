package com.dnd.qello.account.repository.jpa;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dnd.qello.account.domain.AccountStatus;

interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, Long> {

	@Query("select case when count(a) > 0 then true else false end from AccountJpaEntity a "
			+ "where lower(a.nickname) = lower(:nickname) and a.deletedAt is null")
	boolean existsActiveByNicknameIgnoreCase(@Param("nickname") String nickname);

	// user_account_withdrawal_due_idx와 같은 정렬이다.
	@Query("select a.id from AccountJpaEntity a "
			+ "where a.status = :status and a.withdrawalRequestedAt <= :requestedAtOrBefore "
			+ "order by a.withdrawalRequestedAt, a.id")
	List<Long> findWithdrawalDueIds(
			@Param("status") AccountStatus status,
			@Param("requestedAtOrBefore") Instant requestedAtOrBefore,
			Pageable pageable);
}
