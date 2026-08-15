package com.dnd.qello.filtering.domain;

import java.time.Duration;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// SnapshotHealth가 PERMANENT_SUSPECTED로 전이하는 임계값(#109). 실제 운영 수치
// (이슈 본문에서 "미결정"으로 명시)는 이 record가 아니라 호출자가 주입한다 —
// 여기서는 형태와 정합성만 강제한다. target-only 실패 "지속성"을 횟수와 최소
// 경과 시간 두 축으로 함께 요구한다 — 순간적인 실패 폭주만으로는 전이하지
// 않는다.
public record SnapshotHealthPolicy(int suspectThresholdCount, Duration minPersistence) {

	public SnapshotHealthPolicy {
		if (suspectThresholdCount < 1) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "suspectThresholdCount",
				"suspectThresholdCount는 1 이상이어야 합니다");
		}
		if (minPersistence == null || minPersistence.isNegative()) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "minPersistence", "minPersistence는 0 이상이어야 합니다");
		}
	}
}
