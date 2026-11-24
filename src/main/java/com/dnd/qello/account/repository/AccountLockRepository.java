package com.dnd.qello.account.repository;

import java.util.Optional;

import com.dnd.qello.account.domain.Account;

public interface AccountLockRepository {

	/** 호출자의 쓰기 트랜잭션이 끝날 때까지 계정 행을 잠그고 최신 상태를 반환한다. */
	Optional<Account> findByIdForUpdate(long userId);
}
