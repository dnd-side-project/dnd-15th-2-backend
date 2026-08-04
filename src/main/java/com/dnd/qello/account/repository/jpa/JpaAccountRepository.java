package com.dnd.qello.account.repository.jpa;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.AccountRepository;

import jakarta.persistence.EntityNotFoundException;

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
		AccountJpaEntity entity = AccountJpaMapper.toNewEntity(account);
		AccountJpaEntity saved = repository.save(entity);
		return AccountJpaMapper.toDomain(saved);
	}

	@Override
	@Transactional
	public Account updateProfile(Account account) {
		AccountJpaEntity entity = findManaged(account.getId());
		AccountJpaMapper.updateProfile(entity, account);
		return AccountJpaMapper.toDomain(entity);
	}

	@Override
	@Transactional
	public Account updateStatus(Account account) {
		AccountJpaEntity entity = findManaged(account.getId());
		AccountJpaMapper.updateStatus(entity, account);
		return AccountJpaMapper.toDomain(entity);
	}

	@Override
	public Optional<Account> findById(long id) {
		return repository.findById(id).map(AccountJpaMapper::toDomain);
	}

	/**
	 * 현재 트랜잭션의 Persistence Context가 관리하는 엔티티를 조회한다.
	 * 새 엔티티를 만들어 merge하지 않고 Dirty Checking에 위임한다.
	 */
	private AccountJpaEntity findManaged(Long id) {
		return repository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Account를 찾을 수 없습니다. id=" + id));
	}

}
