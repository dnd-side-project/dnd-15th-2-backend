package com.dnd.qello.direction.sweep;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

/**
 * sweep batch 처리 결과 카운터. scanned/released/ineligible/failed 네 값만 갖는다 —
 * 이후 Micrometer {@code Counter}로 그대로 승격할 수 있도록 이름을 그 용도에 맞게
 * 정했다. 좌표, 답변 본문, 사용자 식별자는 포함하지 않는다.
 */
public record SweepBatchResult(int scanned, int released, int ineligible, int failed) {

	public SweepBatchResult {
		if (scanned < 0 || released < 0 || ineligible < 0 || failed < 0) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_VALUE_RANGE, "sweepBatchResult", "sweep 결과 카운터는 음수일 수 없습니다");
		}
	}
}
