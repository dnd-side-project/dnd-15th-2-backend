package com.dnd.qello.account.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.config.AccountWithdrawalProperties;
import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.AccountRepository;

import lombok.RequiredArgsConstructor;

/**
 * 유예가 끝난 탈퇴를 계정 하나씩 완료한다(#337). sweep 워커가 계정마다 이 메서드를 따로 호출해 트랜잭션을 나눈다.
 *
 * <p>
 * 계정 상태 변경과 다른 모듈의 정리(자격증명·푸시 기기 폐기)가 한 트랜잭션이다. 같은 계정의 철회가 먼저 커밋하면 Account의
 * {@code @Version}이 이 트랜잭션을 거절하고 정리도 함께 rollback된다. 그래서 ACTIVE인데 자격증명만 폐기된 계정이
 * 남지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountWithdrawalCompletionService {

	private final AccountRepository accountRepository;
	private final List<AccountWithdrawalCleanup> cleanups;
	private final AccountWithdrawalProperties properties;

	/** 후보 조회 뒤 철회됐거나 이미 완료된 계정은 아무것도 바꾸지 않고 false를 돌려준다. */
	@Transactional
	public boolean complete(long userId, Instant at) {
		Optional<Account> found = accountRepository.findById(userId);
		if (found.isEmpty() || !found.get().isWithdrawalDue(at, properties.gracePeriod())) {
			return false;
		}
		accountRepository.updateDeletion(found.get().completeWithdrawal(at, properties.gracePeriod()));
		cleanups.forEach(cleanup -> cleanup.onWithdrawalCompleted(userId, at));
		return true;
	}

	/** 요청 시각이 이 값 이전(같은 시각 포함)인 유예 계정이 at 시점에 완료 대상이다. */
	public List<Long> findDueUserIds(Instant at, int limit) {
		return accountRepository.findWithdrawalDueIds(at.minus(properties.gracePeriod()), limit);
	}

}
