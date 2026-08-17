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
		for (PostRecipient candidate : candidates) {
			try {
				if (receiveSlotReleaseService.confirmSkip(candidate.getId(), at).isPresent()) {
					released++;
				} else {
					ineligible++;
				}
			} catch (RuntimeException failure) {
				failed++;
				log.warn("넘김확정 sweep 행 처리 실패: postRecipientId={}", candidate.getId(), failure);
			}
		}

		SweepBatchResult result = new SweepBatchResult(candidates.size(), released, ineligible, failed);
		log.info("넘김확정 sweep 완료: scanned={} released={} ineligible={} failed={}",
			result.scanned(), result.released(), result.ineligible(), result.failed());
		return result;
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
