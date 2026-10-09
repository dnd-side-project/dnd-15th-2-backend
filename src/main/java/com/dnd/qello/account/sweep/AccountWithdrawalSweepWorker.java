package com.dnd.qello.account.sweep;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.service.AccountWithdrawalCompletionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 유예가 끝난 탈퇴를 batch로 완료하는 실행기(#337). 상태 전이와 정리는
 * {@link AccountWithdrawalCompletionService#complete}가 계정마다 자기 트랜잭션에서 소유하고, 이
 * 클래스는 후보 조회와 행별 반복, 실패 격리만 담당한다. 한 계정의 실패가 이미 커밋된 다른 계정을 되돌리지 않는다.
 *
 * <p>
 * 스스로 trigger를 갖지 않는다. 주기 실행은 {@code AccountWithdrawalSweepScheduledAdapter}가
 * {@code qello.worker.scheduling} 설정으로 구동하며, 그 gate가 꺼져 있으면 adapter 자체가 등록되지
 * 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AccountWithdrawalSweepWorker {

	private final AccountWithdrawalCompletionService completionService;
	private final Clock clock;

	// 계정별 쓰기 트랜잭션은 completionService가 연다. 클래스 read-only 트랜잭션에 합류하지 않도록 진입 메서드는 트랜잭션
	// 없이 실행한다.
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public BatchResult processBatch(BatchCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("command는 필수입니다");
		}
		Instant at = command.at() == null ? clock.instant() : command.at();
		List<Long> candidates = completionService.findDueUserIds(at, command.limit());

		int completed = 0;
		int ineligible = 0;
		int failed = 0;
		Long firstFailedId = null;
		RuntimeException firstFailure = null;
		for (Long userId : candidates) {
			try {
				if (completionService.complete(userId, at)) {
					completed++;
				} else {
					ineligible++;
				}
			} catch (RuntimeException failure) {
				failed++;
				// 광범위한 장애에서 같은 stack trace가 batch 크기만큼 쌓이지 않게 첫 실패 하나만 남긴다.
				if (firstFailure == null) {
					firstFailedId = userId;
					firstFailure = failure;
				}
			}
		}

		BatchResult result = new BatchResult(candidates.size(), completed, ineligible, failed);
		logSummary(result, firstFailedId, firstFailure);
		return result;
	}

	// 실패한 계정은 유예 중으로 남아 다음 sweep에서 다시 후보가 되므로 batch 요약 한 줄만 남긴다.
	private void logSummary(BatchResult result, Long firstFailedId, RuntimeException firstFailure) {
		if (result.failed() == 0) {
			log.info("탈퇴 완료 sweep: scanned={} completed={} ineligible={} failed=0",
					result.scanned(), result.completed(), result.ineligible());
			return;
		}
		log.warn("탈퇴 완료 sweep: scanned={} completed={} ineligible={} failed={} firstFailedUserId={}",
				result.scanned(), result.completed(), result.ineligible(), result.failed(), firstFailedId,
				firstFailure);
	}

	/** at이 null이면 후보 조회와 각 계정 처리 모두 Clock의 현재 시각을 쓴다. */
	public record BatchCommand(int limit, Instant at) {
		public BatchCommand {
			if (limit <= 0) {
				throw new IllegalArgumentException("limit은 양수여야 합니다");
			}
		}
	}

	/** scanned는 completed, ineligible, failed의 합이다. */
	public record BatchResult(int scanned, int completed, int ineligible, int failed) {
	}
}
