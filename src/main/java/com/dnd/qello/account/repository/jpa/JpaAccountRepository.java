package com.dnd.qello.account.repository.jpa;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.repository.AccountRepository;

// 수정 경로는 관리 엔티티를 조회해 Dirty Checking에 맡긴다. 기존 id를 가진 새 엔티티를
// merge하지 않으므로, 오래된 도메인 스냅샷이 다른 속성의 최신 값을 덮어쓰지 않는다.
//
// 같은 속성을 동시에 수정하는 요청은 AccountJpaEntity의 @Version이 거절한다. 충돌은 flush
// 시점에 드러나므로 트랜잭션 경계 밖으로 나가며, GlobalExceptionHandler가 409로 변환한다.
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
			.orElseThrow(() -> new AccountException(
				AccountErrorCode.ACCOUNT_NOT_FOUND, "id", "대상 계정이 존재하지 않습니다"));
	}

}
