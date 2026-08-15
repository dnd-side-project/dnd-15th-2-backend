package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// synthetic probe 원시 결과 한 건의 append-only 기록(#109). classification이
// null이면 probe 성공을 뜻한다. SnapshotHealth의 상태 자체와 달리 이 레코드는
// 매 probe 호출마다 하나씩 쌓이는 원장이다.
public record SnapshotHealthProbeResult(
	Long id, String modelSnapshot, ProbeType probeType, ModerationFailureClassification classification,
	Instant probedAt
) {

	public SnapshotHealthProbeResult {
		if (id != null && id <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "id", "id는 양수여야 합니다");
		}
		if (modelSnapshot == null || modelSnapshot.isBlank()) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "modelSnapshot");
		}
		if (probeType == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "probeType");
		}
		if (probedAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "probedAt");
		}
	}

	public static SnapshotHealthProbeResult of(
		String modelSnapshot, ProbeType probeType, ModerationFailureClassification classification, Instant probedAt
	) {
		return new SnapshotHealthProbeResult(null, modelSnapshot, probeType, classification, probedAt);
	}

	public boolean succeeded() {
		return classification == null;
	}
}
