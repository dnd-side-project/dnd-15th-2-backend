package com.dnd.qello.filtering.domain;

import java.time.Duration;
import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// OpenAI moderation snapshot(modelSnapshot 문자열) 단위 health 상태(#109). 개별
// FilterJob 실패가 아니라 synthetic target/control probe 결과로만 전이한다
// (INV-HLT-001) — recordProbe만이 이 상태를 바꾸며, 어떤 pipeline 처리 경로도
// 이 객체를 직접 호출하지 않는다.
//
// target-only 실패(target probe만 실패, control probe는 성공)만 증거로
// 누적한다. NON_TARGET_CLIENT_ERROR·UNKNOWN·RATE_LIMITED 분류는 반복되어도
// 증거로 집계되지 않는다(INV-HLT-002, INV-HLT-003, INV-HLT-004) — SERVER_ERROR와
// TIMEOUT_OR_NETWORK만 target-only일 때 누적된다. PERMANENT_SUSPECTED는
// 이 누적만으로 자동 도달할 수 있지만, PERMANENT_CONFIRMED는 confirmPermanent를
// 통한 운영자 호출로만 도달한다(INV-HLT-005) — recordProbe는 이 상태를
// 만들지 않는다.
public record SnapshotHealth(
	String modelSnapshot,
	SnapshotHealthStatus status,
	int targetOnlyFailureCount,
	Instant firstTargetOnlyFailureAt,
	Instant lastTargetOnlyFailureAt,
	boolean officialAnnouncement,
	Instant confirmedAt,
	Long confirmedByOperatorUserId,
	Instant updatedAt
) {

	private static final int MODEL_SNAPSHOT_MAX_LENGTH = 200;

	public SnapshotHealth {
		if (modelSnapshot == null || modelSnapshot.isBlank() || modelSnapshot.length() > MODEL_SNAPSHOT_MAX_LENGTH) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_TEXT, "modelSnapshot", "modelSnapshot 값이 유효하지 않습니다");
		}
		if (status == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "status");
		}
		if (targetOnlyFailureCount < 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "targetOnlyFailureCount",
				"targetOnlyFailureCount는 음수일 수 없습니다");
		}
		if ((status == SnapshotHealthStatus.PERMANENT_CONFIRMED)
			!= (confirmedAt != null && confirmedByOperatorUserId != null)) {
			throw new FilteringException(FilteringErrorCode.INVALID_SNAPSHOT_HEALTH_STATUS, "confirmedAt",
				"PERMANENT_CONFIRMED 상태와 confirmedAt/confirmedByOperatorUserId는 함께 있어야 합니다");
		}
		if (confirmedByOperatorUserId != null && confirmedByOperatorUserId <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "confirmedByOperatorUserId",
				"confirmedByOperatorUserId는 양수여야 합니다");
		}
		if (updatedAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "updatedAt");
		}
	}

	public static SnapshotHealth healthy(String modelSnapshot, Instant now) {
		return new SnapshotHealth(modelSnapshot, SnapshotHealthStatus.HEALTHY, 0, null, null, false, null, null, now);
	}

	public static SnapshotHealth restore(String modelSnapshot, SnapshotHealthStatus status,
		int targetOnlyFailureCount, Instant firstTargetOnlyFailureAt, Instant lastTargetOnlyFailureAt,
		boolean officialAnnouncement, Instant confirmedAt, Long confirmedByOperatorUserId, Instant updatedAt) {
		return new SnapshotHealth(modelSnapshot, status, targetOnlyFailureCount, firstTargetOnlyFailureAt,
			lastTargetOnlyFailureAt, officialAnnouncement, confirmedAt, confirmedByOperatorUserId, updatedAt);
	}

	// target/control synthetic probe 결과를 반영한다. null은 그 probe가 성공했다는
	// 뜻이다. PERMANENT_CONFIRMED는 운영자 번복(un-confirm) 경로가 이 이슈 범위
	// 밖이라 이후 어떤 probe 결과로도 바뀌지 않는다.
	public SnapshotHealth recordProbe(
		ModerationFailureClassification targetFailure, ModerationFailureClassification controlFailure,
		Instant now, SnapshotHealthPolicy policy
	) {
		requireArgs(now, policy);
		if (status == SnapshotHealthStatus.PERMANENT_CONFIRMED) {
			return this;
		}
		if (targetFailure == null) {
			// target probe 성공 = recovery. 이미 HEALTHY고 누적 증거가 없으면
			// updatedAt만 갱신한다.
			if (status == SnapshotHealthStatus.HEALTHY && targetOnlyFailureCount == 0) {
				return withUpdatedAt(now);
			}
			return new SnapshotHealth(
				modelSnapshot, SnapshotHealthStatus.HEALTHY, 0, null, null, officialAnnouncement, null, null, now);
		}
		if (!isTargetOnlyEvidence(targetFailure) || controlFailure != null) {
			// NON_TARGET_CLIENT_ERROR·UNKNOWN·RATE_LIMITED는 절대 증거로 집계되지
			// 않는다(INV-HLT-003, INV-HLT-004). control도 실패하면 공급자 전역
			// 장애로 간주해 target-only 증거로 집계하지 않는다(INV-HLT-006).
			return withUpdatedAt(now);
		}
		int nextCount = targetOnlyFailureCount + 1;
		Instant firstAt = firstTargetOnlyFailureAt == null ? now : firstTargetOnlyFailureAt;
		SnapshotHealthStatus nextStatus = status;
		if (nextCount >= policy.suspectThresholdCount()
			&& Duration.between(firstAt, now).compareTo(policy.minPersistence()) >= 0) {
			nextStatus = SnapshotHealthStatus.PERMANENT_SUSPECTED;
		}
		return new SnapshotHealth(
			modelSnapshot, nextStatus, nextCount, firstAt, now, officialAnnouncement, null, null, now);
	}

	public SnapshotHealth recordOfficialAnnouncement(boolean announced, Instant now) {
		if (now == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "now");
		}
		return new SnapshotHealth(modelSnapshot, status, targetOnlyFailureCount, firstTargetOnlyFailureAt,
			lastTargetOnlyFailureAt, announced, confirmedAt, confirmedByOperatorUserId, now);
	}

	// PERMANENT_SUSPECTED에서만 허용되는, 운영자만 호출할 수 있는 유일한
	// PERMANENT_CONFIRMED 진입점(INV-HLT-005). recordProbe를 포함해 다른 어떤
	// 메서드도 이 상태로 전이시키지 않는다.
	public SnapshotHealth confirmPermanent(long operatorUserId, Instant now) {
		if (status != SnapshotHealthStatus.PERMANENT_SUSPECTED) {
			throw new FilteringException(FilteringErrorCode.INVALID_SNAPSHOT_HEALTH_STATUS, "status",
				status + " 상태에서는 PERMANENT_CONFIRMED로 전이할 수 없습니다");
		}
		if (operatorUserId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "operatorUserId", "operatorUserId는 양수여야 합니다");
		}
		if (now == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "now");
		}
		return new SnapshotHealth(modelSnapshot, SnapshotHealthStatus.PERMANENT_CONFIRMED, targetOnlyFailureCount,
			firstTargetOnlyFailureAt, lastTargetOnlyFailureAt, officialAnnouncement, now, operatorUserId, now);
	}

	private static boolean isTargetOnlyEvidence(ModerationFailureClassification classification) {
		return classification == ModerationFailureClassification.SERVER_ERROR
			|| classification == ModerationFailureClassification.TIMEOUT_OR_NETWORK;
	}

	private SnapshotHealth withUpdatedAt(Instant now) {
		return new SnapshotHealth(modelSnapshot, status, targetOnlyFailureCount, firstTargetOnlyFailureAt,
			lastTargetOnlyFailureAt, officialAnnouncement, confirmedAt, confirmedByOperatorUserId, now);
	}

	private static void requireArgs(Instant now, SnapshotHealthPolicy policy) {
		if (now == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "now");
		}
		if (policy == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "policy");
		}
	}
}
