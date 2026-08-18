package com.dnd.qello.direction.sweep;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.service.ReceiveSlotReleaseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link PostRecipient#confirmSkip}를 batch로 구동하는 실행기. 되돌리기 유예 계산과
 * 도메인 전이, 슬롯 해제는 {@link ReceiveSlotReleaseService#confirmSkip}이 소유하며
 * 이 클래스는 batch 시각과 limit만 전달하고 후보를 행별로 반복한다.
 *
 * <p>RecipientExpirationSweepWorker와 같은 이유로 자체 트랜잭션을 열지 않고 trigger도
 * 갖지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkipConfirmationSweepWorker {

	private final ReceiveSlotReleaseService receiveSlotReleaseService;
	private final Clock clock;

	public SweepBatchResult processBatch(BatchCommand command) {
		requireCommand(command);
		Instant at = resolveAt(command);
		List<PostRecipient> candidates = receiveSlotReleaseService.findConfirmableSkips(at, command.limit());

		int released = 0;
		int ineligible = 0;
		int failed = 0;
		Long firstFailedId = null;
		RuntimeException firstFailure = null;
		for (PostRecipient candidate : candidates) {
			try {
				if (receiveSlotReleaseService.confirmSkip(candidate.getId(), at).isPresent()) {
					released++;
				} else {
					ineligible++;
				}
			} catch (RuntimeException failure) {
				failed++;
				// 행마다 stack trace를 남기면 광범위한 장애에서 batch 크기만큼 같은 trace가
				// 반복 수집된다. 실패는 카운터로 집계하고 원인 파악용 sample은 첫 실패 하나로
				// 제한해 batch당 stack trace를 최대 한 개로 유지한다.
				if (firstFailure == null) {
					firstFailedId = candidate.getId();
					firstFailure = failure;
				}
			}
		}

		SweepBatchResult result = new SweepBatchResult(candidates.size(), released, ineligible, failed);
		logSummary(result, firstFailedId, firstFailure);
		return result;
	}

	// 실패한 행은 다음 sweep에서 다시 후보가 되므로 행 단위 알림 대신 batch 요약 한 줄로만
	// 남긴다. 실패가 있으면 같은 줄을 WARN으로 올리고 첫 실패의 예외만 첨부한다.
	private void logSummary(SweepBatchResult result, Long firstFailedId, RuntimeException firstFailure) {
		if (result.failed() == 0) {
			log.info("넘김확정 sweep 완료: scanned={} released={} ineligible={} failed=0",
				result.scanned(), result.released(), result.ineligible());
			return;
		}
		log.warn("넘김확정 sweep 완료: scanned={} released={} ineligible={} failed={} firstFailedPostRecipientId={}",
			result.scanned(), result.released(), result.ineligible(), result.failed(), firstFailedId,
			firstFailure);
	}

	private Instant resolveAt(BatchCommand command) {
		return command.at() == null ? clock.instant() : command.at();
	}

	private void requireCommand(BatchCommand command) {
		if (command == null) {
			throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, "command", "command는 필수입니다");
		}
	}

	/** at이 null이면 Clock의 현재 시각을 쓴다. 유예 차감은 ReceiveSlotReleaseService가 소유한다. */
	public record BatchCommand(int limit, Instant at) {
		public BatchCommand {
			if (limit <= 0) {
				throw new DirectionException(DirectionErrorCode.INVALID_VALUE_RANGE, "limit", "limit은 양수여야 합니다");
			}
		}
	}
}
