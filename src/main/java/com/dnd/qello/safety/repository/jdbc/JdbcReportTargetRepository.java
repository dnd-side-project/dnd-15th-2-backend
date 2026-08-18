package com.dnd.qello.safety.repository.jdbc;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.safety.domain.ReportTargetSnapshot;
import com.dnd.qello.safety.repository.ReportTargetRepository;
import com.dnd.qello.safety.repository.jdbc.sql.ReportTargetSql;

@Repository
public class JdbcReportTargetRepository implements ReportTargetRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcReportTargetRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<ReportTargetSnapshot> findViewableAnswer(long answerId, long viewerId, Instant at) {
		return jdbc.query(ReportTargetSql.SELECT_VIEWABLE_ANSWER, new MapSqlParameterSource()
				.addValue("answerId", answerId).addValue("viewerId", viewerId).addValue("at", Timestamp.from(at)),
			(rs, row) -> mapSnapshot(rs)).stream().findFirst();
	}

	@Override
	public Optional<ReportTargetSnapshot> findViewablePost(long postId, long viewerId, Instant at) {
		return jdbc.query(ReportTargetSql.SELECT_VIEWABLE_POST, new MapSqlParameterSource()
				.addValue("postId", postId).addValue("viewerId", viewerId).addValue("at", Timestamp.from(at)),
			(rs, row) -> mapSnapshot(rs)).stream().findFirst();
	}

	@Override
	public Optional<ReportTargetSnapshot> findViewableUser(long targetUserId, long viewerId) {
		return jdbc.query(ReportTargetSql.SELECT_VIEWABLE_USER, new MapSqlParameterSource()
				.addValue("targetUserId", targetUserId).addValue("viewerId", viewerId),
			(rs, row) -> mapSnapshot(rs)).stream().findFirst();
	}

	private static ReportTargetSnapshot mapSnapshot(ResultSet rs) throws SQLException {
		return new ReportTargetSnapshot(rs.getLong("author_id"), rs.getString("body_text"),
			mediaKeys(rs), rs.getInt("edit_count"), instant(rs, "content_published_at"));
	}

	private static List<String> mediaKeys(ResultSet rs) throws SQLException {
		Array array = rs.getArray("media_object_keys");
		if (array == null) {
			return List.of();
		}
		return Arrays.asList((String[]) array.getArray());
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}
}
