package com.dnd.qello.account.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, Long> {

	@Query("select case when count(a) > 0 then true else false end from AccountJpaEntity a "
		+ "where lower(a.nickname) = lower(:nickname) and a.deletedAt is null")
	boolean existsActiveByNicknameIgnoreCase(@Param("nickname") String nickname);
}
