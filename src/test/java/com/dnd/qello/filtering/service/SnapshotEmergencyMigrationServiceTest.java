/**
 * Created at: 2026-08-16T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-109-SNAPSHOT-HEALTH-MIGRATION-UNIT-021 through UNIT-023
 */
package com.dnd.qello.filtering.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterReleaseStatus;
import com.dnd.qello.filtering.domain.SnapshotHealth;
import com.dnd.qello.filtering.domain.SnapshotHealthStatus;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.SnapshotEmergencyMigrationHistoryRepository;
import com.dnd.qello.filtering.repository.SnapshotHealthRepository;

class SnapshotEmergencyMigrationServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");
	private static final String MODEL_SNAPSHOT = "omni-moderation-2026-08-01";

	private final SnapshotHealthRepository snapshotHealthRepository = mock(SnapshotHealthRepository.class);
	private final FilterReleaseRegistryService filterReleaseRegistryService = mock(FilterReleaseRegistryService.class);
	private final FilterJobRepository filterJobRepository = mock(FilterJobRepository.class);
	private final SnapshotEmergencyMigrationHistoryRepository migrationHistoryRepository =
		mock(SnapshotEmergencyMigrationHistoryRepository.class);
	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	@Test
	@DisplayName("source snapshot이 PERMANENT_CONFIRMED가 아니면 emergency migration이 거절된다")
	void rejectsMigrationWhenSourceNotConfirmed() {
		when(filterReleaseRegistryService.find(1L)).thenReturn(release(1L, FilterReleaseStatus.PROMOTED));
		when(filterReleaseRegistryService.find(2L)).thenReturn(release(2L, FilterReleaseStatus.CANARY));
		when(snapshotHealthRepository.findOrCreateForUpdate(MODEL_SNAPSHOT, NOW))
			.thenReturn(SnapshotHealth.healthy(MODEL_SNAPSHOT, NOW));
		SnapshotEmergencyMigrationService service = service();

		assertThatThrownBy(() -> service.emergencyMigrate(1L, 2L, 9L))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_SNAPSHOT_HEALTH_STATUS);
		verify(filterJobRepository, never()).findAutomatedByFilterReleaseId(anyLong());
		verify(migrationHistoryRepository, never()).save(any());
	}

	@Test
	@DisplayName("대상 release가 CANARY/ROLLED_BACK이 아니면 emergency migration이 거절된다")
	void rejectsMigrationWhenTargetNotPreApproved() {
		when(filterReleaseRegistryService.find(1L)).thenReturn(release(1L, FilterReleaseStatus.PROMOTED));
		when(filterReleaseRegistryService.find(2L)).thenReturn(release(2L, FilterReleaseStatus.CANDIDATE));
		SnapshotEmergencyMigrationService service = service();

		assertThatThrownBy(() -> service.emergencyMigrate(1L, 2L, 9L))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_MIGRATION_TARGET);
		verify(snapshotHealthRepository, never()).findOrCreateForUpdate(any(), any());
	}

	@Test
	@DisplayName("대상 release가 source release와 같으면 emergency migration이 거절된다")
	void rejectsMigrationWhenTargetEqualsSource() {
		when(filterReleaseRegistryService.find(1L)).thenReturn(release(1L, FilterReleaseStatus.PROMOTED));
		SnapshotEmergencyMigrationService service = service();

		assertThatThrownBy(() -> service.emergencyMigrate(1L, 1L, 9L))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_MIGRATION_TARGET);
	}

	@Test
	@DisplayName("source가 PERMANENT_CONFIRMED이고 대상이 CANARY면 emergency migration이 실행된다")
	void executesMigrationWhenPreconditionsMet() {
		FilterRelease source = release(1L, FilterReleaseStatus.PROMOTED);
		FilterRelease target = release(2L, FilterReleaseStatus.CANARY);
		FilterRelease promotedTarget = new FilterRelease(2L, target.normalizationRef(), target.localRulesetRef(),
			target.categoryMappingRef(), target.modelSnapshot(), FilterReleaseStatus.PROMOTED, NOW, target.createdAt());
		when(filterReleaseRegistryService.find(1L)).thenReturn(source);
		when(filterReleaseRegistryService.find(2L)).thenReturn(target);
		when(filterReleaseRegistryService.promote(2L, 9L)).thenReturn(promotedTarget);
		SnapshotHealth confirmed = SnapshotHealth.healthy(MODEL_SNAPSHOT, NOW)
			.recordProbe(com.dnd.qello.filtering.domain.ModerationFailureClassification.SERVER_ERROR, null, NOW,
				new com.dnd.qello.filtering.domain.SnapshotHealthPolicy(1, java.time.Duration.ZERO))
			.confirmPermanent(3L, NOW);
		when(snapshotHealthRepository.findOrCreateForUpdate(MODEL_SNAPSHOT, NOW)).thenReturn(confirmed);
		when(filterJobRepository.findAutomatedByFilterReleaseId(1L)).thenReturn(List.of());
		when(migrationHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		SnapshotEmergencyMigrationService service = service();

		var result = service.emergencyMigrate(1L, 2L, 9L);

		assertThat(result.sourceReleaseId()).isEqualTo(1L);
		assertThat(result.targetReleaseId()).isEqualTo(2L);
		assertThat(result.operatorUserId()).isEqualTo(9L);
		verify(filterReleaseRegistryService).promote(2L, 9L);
	}

	private SnapshotEmergencyMigrationService service() {
		return new SnapshotEmergencyMigrationService(
			snapshotHealthRepository, filterReleaseRegistryService, filterJobRepository, migrationHistoryRepository, clock);
	}

	private static FilterRelease release(long id, FilterReleaseStatus status) {
		Instant promotedAt = status == FilterReleaseStatus.PROMOTED ? NOW : null;
		return FilterRelease.restore(
			id, "norm-ref", "rules-ref", "category-ref", MODEL_SNAPSHOT, status, promotedAt, NOW);
	}
}
