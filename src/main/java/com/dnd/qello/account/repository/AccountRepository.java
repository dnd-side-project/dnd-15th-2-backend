package com.dnd.qello.account.repository;

import java.util.Optional;

import com.dnd.qello.account.domain.Account;

public interface AccountRepository {

	Account save(Account account);

	Optional<Account> findById(long id);

}
