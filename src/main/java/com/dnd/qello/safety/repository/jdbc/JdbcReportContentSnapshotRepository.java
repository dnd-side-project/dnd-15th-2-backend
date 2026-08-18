package com.dnd.qello.safety.repository.jdbc;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.safety.domain.ReportContentSnapshot;
import com.dnd.qello.safety.domain.ReportTargetType;
import com.dnd.qello.safety.repository.ReportContentSnapshotRepository;

@Repository
public class JdbcReportContentSnapshotRepository implements ReportContentSnapshotRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcReportContentSnapshotRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public ReportContentSnapshot save(ReportContentSnapshot snapshot) {
		Array mediaObjectKeys = jdbc.getJdbcTemplate().execute((ConnectionCallback<Array>) connection ->
			connection.createArrayOf("text", snapshot.mediaObjectKeys().toArray()));
		jdbc.update("""
			INSERT INTO report_content_snapshot (report_id, captured_at, target_type, target_id, author_id,
				body_text, media_object_keys, edit_count, content_published_at, content_hash,
				legal_hold, purge_after)
			VALUES (:reportId, :capturedAt, :targetType, :targetId, :authorId,
				:bodyText, :mediaObjectKeys, :editCount, :contentPublishedAt, :contentHash,
				:legalHold, :purgeAfter)
			""", new MapSqlParameterSource()
			.addValue("reportId", snapshot.reportId())
			.addValue("capturedAt", Timestamp.from(snapshot.capturedAt()))
			.addValue("targetType", snapshot.targetType().name())
			.addValue("targetId", snapshot.targetId())
			.addValue("authorId", snapshot.authorId())
			.addValue("bodyText", snapshot.bodyText())
			.addValue("mediaObjectKeys", mediaObjectKeys)
			.addValue("editCount", snapshot.editCount())
			.addValue("contentPublishedAt", timestamp(snapshot.contentPublishedAt()))
			.addValue("contentHash", snapshot.contentHash())
			.addValue("legalHold", snapshot.legalHold())
			.addValue("purgeAfter", timestamp(snapshot.purgeAfter())));
		return snapshot;
	}

	@Override
	public Optional<ReportContentSnapshot> findByReportId(long reportId) {
		return jdbc.query("SELECT * FROM report_content_snapshot WHERE report_id = :reportId",
			new MapSqlParameterSource("reportId", reportId),
			(rs, row) -> mapSnapshot(rs)).stream().findFirst();
	}

	private static ReportContentSnapshot mapSnapshot(ResultSet rs) throws SQLException {
		return ReportContentSnapshot.restore(rs.getLong("report_id"), instant(rs, "captured_at"),
			ReportTargetType.valueOf(rs.getString("target_type")), rs.getLong("target_id"),
			rs.getLong("author_id"), rs.getString("body_text"), mediaKeys(rs),
			rs.getInt("edit_count"), instant(rs, "content_published_at"), rs.getString("content_hash"),
			rs.getBoolean("legal_hold"), instant(rs, "purge_after"));
	}

	private static List<String> mediaKeys(ResultSet rs) throws SQLException {
		Array array = rs.getArray("media_object_keys");
		if (array == null) {
			return List.of();
		}
		return Arrays.asList((String[]) array.getArray());
	}

	private static Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}
}
