package com.dnd.qello.account.repository.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.repository.AccountLockRepository;
import com.dnd.qello.account.repository.AccountRepository;

import lombok.RequiredArgsConstructor;

// 수정 경로는 관리 엔티티를 조회해 Dirty Checking에 맡긴다. 기존 id를 가진 새 엔티티를
// merge하지 않으므로, 오래된 도메인 스냅샷이 다른 속성의 최신 값을 덮어쓰지 않는다.
//
// 같은 속성을 동시에 수정하는 요청은 AccountJpaEntity의 @Version이 거절한다. 충돌은 flush
// 시점에 드러나므로 트랜잭션 경계 밖으로 나가며, GlobalExceptionHandler가 409로 변환한다.
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaAccountRepository implements AccountRepository, AccountLockRepository {

	private final SpringDataAccountRepository repository;
	private final JdbcTemplate jdbcTemplate;
	private final EntityManager entityManager;

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Account> findByIdForUpdate(long userId) {
		entityManager.flush();
		if (!lockAccount(userId)) {
			return Optional.empty();
		}
		return repository.findById(userId).map(entity -> {
			// 잠금 전 로드된 엔티티도 잠금 획득 이후의 계정 상태로 판정해야 한다.
			entityManager.refresh(entity);
			return AccountJpaMapper.toDomain(entity);
		});
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
	public Account updateProfileImage(Account account) {
		AccountJpaEntity entity = findManaged(account.getId());
		AccountJpaMapper.updateProfileImage(entity, account);
		return AccountJpaMapper.toDomain(entity);
	}

	@Override
	@Transactional
	public Account updateStatus(Account account) {
		lockAccount(account.getId());
		AccountJpaEntity entity = findManaged(account.getId());
		AccountJpaMapper.updateStatus(entity, account);
		// cleanup보다 먼저 상태 UPDATE와 낙관적 충돌을 확정하고 계정 잠금을 commit까지 유지한다.
		entityManager.flush();
		return AccountJpaMapper.toDomain(entity);
	}

	@Override
	@Transactional
	public Account updateDeletion(Account account) {
		AccountJpaEntity entity = findManaged(account.getId());
		AccountJpaMapper.updateDeletion(entity, account);
		return AccountJpaMapper.toDomain(entity);
	}

	@Override
	public Optional<Account> findById(long id) {
		return repository.findById(id).map(AccountJpaMapper::toDomain);
	}

	@Override
	public boolean existsActiveNickname(String nickname) {
		return repository.existsActiveByNicknameIgnoreCase(nickname);
	}

	@Override
	public List<Long> findWithdrawalDueIds(Instant requestedAtOrBefore, int limit) {
		return repository.findWithdrawalDueIds(
				AccountStatus.WITHDRAWAL_PENDING, requestedAtOrBefore, PageRequest.of(0, limit));
	}

	/**
	 * 현재 트랜잭션의 Persistence Context가 관리하는 엔티티를 조회한다. 새 엔티티를 만들어 merge하지 않고 Dirty
	 * Checking에 위임한다.
	 */
	private AccountJpaEntity findManaged(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new AccountException(
						AccountErrorCode.ACCOUNT_NOT_FOUND, "id", "대상 계정이 존재하지 않습니다"));
	}

	private boolean lockAccount(long userId) {
		return !jdbcTemplate.query("SELECT id FROM user_account WHERE id = ? FOR UPDATE",
				(resultSet, rowNum) -> resultSet.getLong("id"), userId).isEmpty();
	}

}
