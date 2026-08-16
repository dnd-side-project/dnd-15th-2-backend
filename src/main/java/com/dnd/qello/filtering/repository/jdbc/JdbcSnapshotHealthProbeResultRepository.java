package com.dnd.qello.filtering.repository.jdbc;

import java.sql.Timestamp;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.dnd.qello.filtering.domain.SnapshotHealthProbeResult;
import com.dnd.qello.filtering.repository.SnapshotHealthProbeResultRepository;

@Repository
public class JdbcSnapshotHealthProbeResultRepository implements SnapshotHealthProbeResultRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcSnapshotHealthProbeResultRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public SnapshotHealthProbeResult save(SnapshotHealthProbeResult result) {
		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update("""
			INSERT INTO snapshot_health_probe_result (model_snapshot, probe_type, classification, probed_at)
			VALUES (:modelSnapshot, :probeType, :classification, :probedAt)
			""",
			new MapSqlParameterSource()
				.addValue("modelSnapshot", result.modelSnapshot())
				.addValue("probeType", result.probeType().name())
				.addValue("classification", result.classification() == null ? null : result.classification().name())
				.addValue("probedAt", Timestamp.from(result.probedAt())),
			keyHolder, new String[] {"id"});
		return new SnapshotHealthProbeResult(keyHolder.getKey().longValue(), result.modelSnapshot(),
			result.probeType(), result.classification(), result.probedAt());
	}
}
