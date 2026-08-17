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
 * {@link PostRecipient#expire}를 batch로 구동하는 실행기. 도메인 전이와 슬롯 해제는
 * {@link ReceiveSlotReleaseService#expire}가 소유하며 이 클래스는 후보 조회와 행별
 * 반복, 실패 격리만 담당한다.
 *
 * <p>자체 트랜잭션을 열지 않는다 — 각 행의 커밋 경계는
 * {@code ReceiveSlotReleaseService.expire}의 {@code @Transactional}이다. 한 행의
 * 실패가 이미 커밋된 다른 행을 되돌리지 않는다.</p>
 *
 * <p>DirectionMatchingWorker·AnswerModerationVerdictWorker와 같은 이유로 아무
 * trigger(@Scheduled 등)도 갖지 않는다. 운영 주기 실행 활성화는 이 이슈의 범위 밖이다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipientExpirationSweepWorker {

	private final ReceiveSlotReleaseService receiveSlotReleaseService;
	private final Clock clock;

	public SweepBatchResult processBatch(BatchCommand command) {
		requireCommand(command);
		Instant at = resolveAt(command);
		List<PostRecipient> candidates = receiveSlotReleaseService.findExpirable(at, command.limit());

		int released = 0;
		int ineligible = 0;
		int failed = 0;
		for (PostRecipient candidate : candidates) {
			try {
				if (receiveSlotReleaseService.expire(candidate.getId(), at).isPresent()) {
					released++;
				} else {
					ineligible++;
				}
			} catch (RuntimeException failure) {
				failed++;
				log.warn("만료 sweep 행 처리 실패: postRecipientId={}", candidate.getId(), failure);
			}
		}

		SweepBatchResult result = new SweepBatchResult(candidates.size(), released, ineligible, failed);
		log.info("만료 sweep 완료: scanned={} released={} ineligible={} failed={}",
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

	/** at이 null이면 batch 조회와 각 행 처리 모두 Clock의 현재 시각을 쓴다. */
	public record BatchCommand(int limit, Instant at) {
		public BatchCommand {
			if (limit <= 0) {
				throw new DirectionException(DirectionErrorCode.INVALID_VALUE_RANGE, "limit", "limit은 양수여야 합니다");
			}
		}
	}
}
