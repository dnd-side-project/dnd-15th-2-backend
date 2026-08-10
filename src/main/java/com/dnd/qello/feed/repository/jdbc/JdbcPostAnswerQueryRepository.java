package com.dnd.qello.feed.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.feed.repository.PostAnswerQueryRepository;
import com.dnd.qello.feed.config.FeedDistanceProperties;
import com.dnd.qello.feed.repository.jdbc.sql.PostAnswerQuerySql;
import com.dnd.qello.feed.view.AnswerCard;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JdbcPostAnswerQueryRepository implements PostAnswerQueryRepository {

	private final NamedParameterJdbcTemplate jdbc;
	private final FeedDistanceProperties feedDistanceProperties;

	@Override
	public boolean canViewAnswers(long viewerId, long postId, Instant at) {
		Boolean result = jdbc.queryForObject(PostAnswerQuerySql.CAN_VIEW_ANSWERS_SQL,
			new MapSqlParameterSource().addValue("viewerId", viewerId).addValue("postId", postId)
				.addValue("at", Timestamp.from(at)),
			Boolean.class);
		return Boolean.TRUE.equals(result);
	}

	@Override
	public List<AnswerCard> findAnswers(long viewerId, long postId, AnswerCursor cursor, int limit, Instant at) {
		if (!canViewAnswers(viewerId, postId, at)) {
			return List.of();
		}
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("viewerId", viewerId).addValue("postId", postId).addValue("limit", limit)
			.addValue("nearFloor", feedDistanceProperties.nearDistanceFloorM())
			.addValue("nearDistanceLabel", feedDistanceProperties.nearDistanceLabel());
		StringBuilder sql = new StringBuilder(PostAnswerQuerySql.SELECT_ANSWERS);
		if (cursor != null) {
			sql.append(PostAnswerQuerySql.CURSOR_FILTER);
			params.addValue("cursorPublishedAt", Timestamp.from(cursor.publishedAt()))
				.addValue("cursorId", cursor.answerId());
		}
		sql.append(PostAnswerQuerySql.ANSWER_ORDER).append(" LIMIT :limit");
		return jdbc.query(sql.toString(), params, (rs, rowNum) -> answerCard(rs));
	}

	private static AnswerCard answerCard(ResultSet rs) throws SQLException {
		return new AnswerCard(
			rs.getLong("answer_id"),
			rs.getString("author_nickname"),
			rs.getString("author_region_code"),
			rs.getString("body_text"),
			FeedRowMappers.mediaIds(rs),
			rs.getBigDecimal("bearing_from_sender_deg"),
			rs.getObject("distance_m", Long.class),
			rs.getString("distance_band"),
			rs.getTimestamp("published_at").toInstant(),
			FeedRowMappers.instant(rs, "edited_at"),
			rs.getBoolean("reacted_by_me"),
			rs.getLong("reaction_count"));
	}
}
