package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// emergency migration 감사 이력 한 건(#109). 원본 release·대상 release·snapshot·
// operator를 append-only로 남겨 lineage를 보존한다 — FilterJob.filterReleaseId
// 자체는 이관 후 대상 release를 가리키도록 바뀌므로, "원래 어디서 왔는지"는 이
// 레코드만이 담당한다(ReleasePromotionHistoryEntry와 동일한 감사 패턴, INV-REL-001·
// INV-REL-008이 승인 없는 자동 교체가 없음을 감사하는 것과 같은 근거).
public record SnapshotEmergencyMigrationHistoryEntry(
	Long id, String modelSnapshot, long sourceReleaseId, long targetReleaseId, int migratedJobCount,
	long operatorUserId, Instant occurredAt
) {

	public SnapshotEmergencyMigrationHistoryEntry {
		if (id != null && id <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "id", "id는 양수여야 합니다");
		}
		if (modelSnapshot == null || modelSnapshot.isBlank()) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "modelSnapshot");
		}
		if (sourceReleaseId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "sourceReleaseId", "sourceReleaseId는 양수여야 합니다");
		}
		if (targetReleaseId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "targetReleaseId", "targetReleaseId는 양수여야 합니다");
		}
		if (sourceReleaseId == targetReleaseId) {
			throw new FilteringException(FilteringErrorCode.INVALID_MIGRATION_TARGET, "targetReleaseId",
				"대상 release는 source release와 달라야 합니다");
		}
		if (migratedJobCount < 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "migratedJobCount", "migratedJobCount는 음수일 수 없습니다");
		}
		if (operatorUserId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "operatorUserId", "operatorUserId는 양수여야 합니다");
		}
		if (occurredAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "occurredAt");
		}
	}

	public static SnapshotEmergencyMigrationHistoryEntry of(String modelSnapshot, long sourceReleaseId,
		long targetReleaseId, int migratedJobCount, long operatorUserId, Instant occurredAt) {
		return new SnapshotEmergencyMigrationHistoryEntry(
			null, modelSnapshot, sourceReleaseId, targetReleaseId, migratedJobCount, operatorUserId, occurredAt);
	}
}
