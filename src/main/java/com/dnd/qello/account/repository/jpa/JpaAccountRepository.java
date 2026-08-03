package com.dnd.qello.account.repository.jpa;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.AccountRepository;

@Repository
@Transactional(readOnly = true)
public class JpaAccountRepository implements AccountRepository {

	private final SpringDataAccountRepository repository;

	public JpaAccountRepository(SpringDataAccountRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public Account save(Account account) {
		AccountJpaEntity saved = repository.saveAndFlush(
			AccountJpaMapper.toEntity(account));
		return AccountJpaMapper.toDomain(saved);
	}

	@Override
	public Optional<Account> findById(long id) {
		return repository.findById(id).map(AccountJpaMapper::toDomain);
	}

}
