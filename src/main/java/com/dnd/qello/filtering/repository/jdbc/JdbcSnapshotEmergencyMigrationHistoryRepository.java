package com.dnd.qello.filtering.repository.jdbc;

import java.sql.Timestamp;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.dnd.qello.filtering.domain.SnapshotEmergencyMigrationHistoryEntry;
import com.dnd.qello.filtering.repository.SnapshotEmergencyMigrationHistoryRepository;

@Repository
public class JdbcSnapshotEmergencyMigrationHistoryRepository implements SnapshotEmergencyMigrationHistoryRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcSnapshotEmergencyMigrationHistoryRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public SnapshotEmergencyMigrationHistoryEntry save(SnapshotEmergencyMigrationHistoryEntry entry) {
		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update("""
			INSERT INTO snapshot_emergency_migration_history
				(model_snapshot, source_release_id, target_release_id, migrated_job_count, operator_user_id,
				 occurred_at)
			VALUES (:modelSnapshot, :sourceReleaseId, :targetReleaseId, :migratedJobCount, :operatorUserId,
			        :occurredAt)
			""",
			new MapSqlParameterSource()
				.addValue("modelSnapshot", entry.modelSnapshot())
				.addValue("sourceReleaseId", entry.sourceReleaseId())
				.addValue("targetReleaseId", entry.targetReleaseId())
				.addValue("migratedJobCount", entry.migratedJobCount())
				.addValue("operatorUserId", entry.operatorUserId())
				.addValue("occurredAt", Timestamp.from(entry.occurredAt())),
			keyHolder, new String[] {"id"});
		return new SnapshotEmergencyMigrationHistoryEntry(keyHolder.getKey().longValue(), entry.modelSnapshot(),
			entry.sourceReleaseId(), entry.targetReleaseId(), entry.migratedJobCount(), entry.operatorUserId(),
			entry.occurredAt());
	}
}
