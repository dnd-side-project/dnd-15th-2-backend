package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// release(snapshot) 단위 재시도 폭주 완화 상태(INV-RTY-007). release당 1행이며 여러
// worker가 동시에 실패/성공을 보고할 수 있어, 이 객체를 다루는 repository는 행을
// SELECT ... FOR UPDATE로 잠가 읽기-수정-쓰기를 직렬화한다(ADR-0002).
//
// currentLimit은 DEGRADED 상태에서만 의미가 있다 — 그 배치에서 이 release에 속한
// 재시도를 pipeline까지 admit할 수 있는 상한이다. HEALTHY는 무제한을 뜻하며
// currentLimit은 항상 null이다.
public record FilterReleaseRetryGate(long filterReleaseId, FilterReleaseGateState state, Integer currentLimit,
	int consecutiveFailures, int consecutiveSuccesses, Instant updatedAt) {

	public FilterReleaseRetryGate {
		if (filterReleaseId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "filterReleaseId", "filterReleaseId는 양수여야 합니다");
		}
		if (state == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "state");
		}
		if ((state == FilterReleaseGateState.DEGRADED) != (currentLimit != null)) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "currentLimit",
				"DEGRADED 상태와 currentLimit은 함께 있어야 합니다");
		}
		if (currentLimit != null && currentLimit <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "currentLimit", "currentLimit은 양수여야 합니다");
		}
		if (consecutiveFailures < 0 || consecutiveSuccesses < 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "consecutive",
				"consecutiveFailures/consecutiveSuccesses는 음수일 수 없습니다");
		}
		if (updatedAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "updatedAt");
		}
	}

	public static FilterReleaseRetryGate healthy(long filterReleaseId, Instant now) {
		return new FilterReleaseRetryGate(filterReleaseId, FilterReleaseGateState.HEALTHY, null, 0, 0, now);
	}

	public static FilterReleaseRetryGate restore(long filterReleaseId, FilterReleaseGateState state,
		Integer currentLimit, int consecutiveFailures, int consecutiveSuccesses, Instant updatedAt) {
		return new FilterReleaseRetryGate(
			filterReleaseId, state, currentLimit, consecutiveFailures, consecutiveSuccesses, updatedAt);
	}

	// 연속 실패 누적. HEALTHY에서 임계값에 도달하면 DEGRADED로 저하돼 최소 한도로 시작한다.
	// 이미 DEGRADED라면 회복 진행(consecutiveSuccesses)을 버리고 최소 한도로 되돌린다 —
	// 복구 도중 재발한 실패를 성공 streak가 가려서는 안 되기 때문이다.
	public FilterReleaseRetryGate onFailure(Instant now, RetryGateConfig config) {
		requireArgs(now, config);
		int failures = consecutiveFailures + 1;
		if (state == FilterReleaseGateState.HEALTHY) {
			if (failures >= config.degradeThreshold()) {
				return new FilterReleaseRetryGate(
					filterReleaseId, FilterReleaseGateState.DEGRADED, config.minLimit(), 0, 0, now);
			}
			return new FilterReleaseRetryGate(filterReleaseId, FilterReleaseGateState.HEALTHY, null, failures, 0, now);
		}
		return new FilterReleaseRetryGate(
			filterReleaseId, FilterReleaseGateState.DEGRADED, config.minLimit(), failures, 0, now);
	}

	// 연속 성공 누적. DEGRADED에서 회복 streak에 도달하면 한도를 rampStep만큼 늘리고,
	// 그 한도가 healthyLimit 이상이 되면 HEALTHY로 완전히 복귀한다.
	public FilterReleaseRetryGate onSuccess(Instant now, RetryGateConfig config) {
		requireArgs(now, config);
		if (state == FilterReleaseGateState.HEALTHY) {
			return new FilterReleaseRetryGate(
				filterReleaseId, FilterReleaseGateState.HEALTHY, null, 0, consecutiveSuccesses + 1, now);
		}
		int successes = consecutiveSuccesses + 1;
		if (successes < config.recoveryStreak()) {
			return new FilterReleaseRetryGate(
				filterReleaseId, FilterReleaseGateState.DEGRADED, currentLimit, 0, successes, now);
		}
		int nextLimit = currentLimit + config.rampStep();
		if (nextLimit >= config.healthyLimit()) {
			return new FilterReleaseRetryGate(filterReleaseId, FilterReleaseGateState.HEALTHY, null, 0, 0, now);
		}
		return new FilterReleaseRetryGate(filterReleaseId, FilterReleaseGateState.DEGRADED, nextLimit, 0, 0, now);
	}

	// 이번 배치에서 이미 admit된 수가 현재 한도 미만인지 확인한다. HEALTHY는 항상 허용한다.
	public boolean allowsClaim(int admittedInBatch) {
		return state == FilterReleaseGateState.HEALTHY || admittedInBatch < currentLimit;
	}

	private static void requireArgs(Instant now, RetryGateConfig config) {
		if (now == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "now");
		}
		if (config == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "config");
		}
	}
}
