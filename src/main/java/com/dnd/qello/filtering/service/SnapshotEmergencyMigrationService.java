package com.dnd.qello.filtering.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterReleaseStatus;
import com.dnd.qello.filtering.domain.SnapshotEmergencyMigrationHistoryEntry;
import com.dnd.qello.filtering.domain.SnapshotHealth;
import com.dnd.qello.filtering.domain.SnapshotHealthStatus;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.SnapshotEmergencyMigrationHistoryRepository;
import com.dnd.qello.filtering.repository.SnapshotHealthRepository;

// emergency migration의 유일한 쓰기 진입점(#109). PERMANENT_CONFIRMED snapshot과
// pre-approved 대상 release(CANARY 또는 ROLLED_BACK — rollback()과 동일한 "이미
// 한 번 완전히 검증된 release만 재사용" 불변식을 상속)가 모두 확인될 때만
// 실행한다(INV-HLT-005).
@Service
@Transactional(readOnly = true)
public class SnapshotEmergencyMigrationService {

	private final SnapshotHealthRepository snapshotHealthRepository;
	private final FilterReleaseRegistryService filterReleaseRegistryService;
	private final FilterJobRepository filterJobRepository;
	private final SnapshotEmergencyMigrationHistoryRepository migrationHistoryRepository;
	private final Clock clock;

	public SnapshotEmergencyMigrationService(
		SnapshotHealthRepository snapshotHealthRepository,
		FilterReleaseRegistryService filterReleaseRegistryService,
		FilterJobRepository filterJobRepository,
		SnapshotEmergencyMigrationHistoryRepository migrationHistoryRepository,
		Clock clock
	) {
		this.snapshotHealthRepository = snapshotHealthRepository;
		this.filterReleaseRegistryService = filterReleaseRegistryService;
		this.filterJobRepository = filterJobRepository;
		this.migrationHistoryRepository = migrationHistoryRepository;
		this.clock = clock;
	}

	// source release에 묶인 AUTOMATED job 전체를 대상 release로 원자적으로
	// 이관한다. source release가 가리키는 modelSnapshot의 SnapshotHealth가
	// PERMANENT_CONFIRMED가 아니면 거절한다(INV-HLT-005) — 이 서비스가 유일한
	// emergency migration 진입점이므로, 이 검증이 곧 "운영자 확인 없는 emergency
	// migration이 불가능하다"의 구현이다.
	@Transactional
	public SnapshotEmergencyMigrationHistoryEntry emergencyMigrate(
		long sourceReleaseId, long targetReleaseId, long operatorUserId
	) {
		FilterRelease source = filterReleaseRegistryService.find(sourceReleaseId);
		FilterRelease target = filterReleaseRegistryService.find(targetReleaseId);
		if (sourceReleaseId == targetReleaseId) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_MIGRATION_TARGET, "targetReleaseId", "대상 release는 source release와 달라야 합니다");
		}
		if (target.status() != FilterReleaseStatus.CANARY && target.status() != FilterReleaseStatus.ROLLED_BACK) {
			throw new FilteringException(FilteringErrorCode.INVALID_MIGRATION_TARGET, "targetReleaseId",
				"대상 release는 CANARY 또는 ROLLED_BACK 상태여야 합니다");
		}

		Instant now = Instant.now(clock);
		SnapshotHealth health = snapshotHealthRepository.findOrCreateForUpdate(source.modelSnapshot(), now);
		if (health.status() != SnapshotHealthStatus.PERMANENT_CONFIRMED) {
			throw new FilteringException(FilteringErrorCode.INVALID_SNAPSHOT_HEALTH_STATUS, "status",
				health.status() + " 상태에서는 emergency migration을 실행할 수 없습니다");
		}

		FilterRelease promotedTarget = target.status() == FilterReleaseStatus.CANARY
			? filterReleaseRegistryService.promote(targetReleaseId, operatorUserId)
			: filterReleaseRegistryService.rollback(targetReleaseId, operatorUserId);

		List<FilterJob> affected = filterJobRepository.findAutomatedByFilterReleaseId(sourceReleaseId);
		for (FilterJob job : affected) {
			filterJobRepository.save(job.migrateToRelease(promotedTarget.id(), now));
		}

		return migrationHistoryRepository.save(SnapshotEmergencyMigrationHistoryEntry.of(
			source.modelSnapshot(), sourceReleaseId, targetReleaseId, affected.size(), operatorUserId, now));
	}
}
