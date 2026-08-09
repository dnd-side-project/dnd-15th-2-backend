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
import com.dnd.qello.feed.repository.jdbc.sql.PostAnswerQuerySql;
import com.dnd.qello.feed.view.AnswerCard;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JdbcPostAnswerQueryRepository implements PostAnswerQueryRepository {

	private static final String ANSWER_ORDER = " ORDER BY a.published_at DESC, a.id DESC";

	private final NamedParameterJdbcTemplate jdbc;

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
			.addValue("viewerId", viewerId).addValue("postId", postId).addValue("limit", limit);
		StringBuilder sql = new StringBuilder("""
			SELECT a.id AS answer_id,
			       ua.nickname AS author_nickname,
			       a.coarse_region_code AS author_region_code,
			       a.body_text,
			       a.bearing_from_sender_deg,
			       a.distance_band,
			       a.published_at,
			       a.edited_at,
			       COALESCE((SELECT array_agg(ma.media_id ORDER BY ma.display_order)
			                 FROM media_attachment ma WHERE ma.answer_id = a.id), '{}'::bigint[]) AS media_ids,
			       EXISTS (SELECT 1 FROM answer_reaction ar
			               WHERE ar.answer_id = a.id AND ar.reactor_id = :viewerId) AS reacted_by_me,
			       (SELECT count(*) FROM answer_reaction ar2 WHERE ar2.answer_id = a.id) AS reaction_count
			FROM answer a
			JOIN post_recipient pr ON pr.id = a.post_recipient_id
			JOIN direction_post dp ON dp.id = pr.post_id
			JOIN user_account ua ON ua.id = a.author_id
			WHERE dp.id = :postId
			  AND dp.deleted_at IS NULL
			  AND a.status = 'PUBLISHED'
			  AND a.deleted_at IS NULL
			  AND NOT EXISTS (SELECT 1 FROM user_block ub
			                  WHERE ub.blocker_id = :viewerId
			                    AND ub.blocked_id = a.author_id
			                    AND ub.released_at IS NULL)
			""");
		if (cursor != null) {
			sql.append(" AND (a.published_at, a.id) < (:cursorPublishedAt, :cursorId)");
			params.addValue("cursorPublishedAt", Timestamp.from(cursor.publishedAt()))
				.addValue("cursorId", cursor.answerId());
		}
		sql.append(ANSWER_ORDER).append(" LIMIT :limit");
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
			rs.getString("distance_band"),
			rs.getTimestamp("published_at").toInstant(),
			FeedRowMappers.instant(rs, "edited_at"),
			rs.getBoolean("reacted_by_me"),
			rs.getLong("reaction_count"));
	}
}
