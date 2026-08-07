package com.dnd.qello.account.repository;

import java.util.Optional;

import com.dnd.qello.account.domain.Account;

public interface AccountRepository {

	/**
	 * 신규 Account만 저장한다. account.getId()는 null이어야 한다.
	 */
	Account save(Account account);

	/**
	 * 기존 Account의 프로필(지역/로케일/타임존/닉네임)만 변경한다. account.getId()는 필수다.
	 */
	Account updateProfile(Account account);

	/**
	 * 기존 Account의 status/deletedAt만 변경한다. account.getId()는 필수다.
	 */
	Account updateStatus(Account account);

	Optional<Account> findById(long id);

}
