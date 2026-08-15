package com.dnd.qello.filtering.repository;

import com.dnd.qello.filtering.domain.SnapshotEmergencyMigrationHistoryEntry;

public interface SnapshotEmergencyMigrationHistoryRepository {

	SnapshotEmergencyMigrationHistoryEntry save(SnapshotEmergencyMigrationHistoryEntry entry);
}
