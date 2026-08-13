package com.dnd.qello.direction.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionPostModerationStatus;
import com.dnd.qello.direction.domain.DirectionPostStatus;
import com.dnd.qello.direction.domain.DirectionRequestFingerprint;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.jdbc.sql.DirectionPostSql;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JdbcDirectionPostRepository implements DirectionPostRepository {

	private final NamedParameterJdbcTemplate jdbc;

	@Override
	public DirectionPost save(DirectionPost post) {
		String sql = post.getId() == null ? DirectionPostSql.INSERT : DirectionPostSql.UPDATE;
		Long id = jdbc.queryForObject(sql, params(post), Long.class);
		return DirectionPost.restore(id, post.getSenderId(), post.getApprovedQuestionId(), post.getRequestFingerprint(),
			post.getStatus(), post.getIdempotencyKey(), post.getBodyText(), post.getCoarseRegionCode(), post.getModerationStatus(),
			post.getSubmittedAt(), post.getPublishedAt(), post.getExpiresAt(), post.getAnswersReadAt(),
			post.getDeletedAt());
	}

	@Override
	public DirectionPost advanceAnswersReadAt(long id, Instant at) {
		return jdbc.query("""
			UPDATE direction_post
			SET answers_read_at = GREATEST(answers_read_at, :at)
			WHERE id = :id
			RETURNING *
			""", new MapSqlParameterSource().addValue("id", id).addValue("at", timestamp(at)),
			rs -> { rs.next(); return map(rs); });
	}

	@Override
	public Optional<DirectionPost> findById(long id) { return one("SELECT * FROM direction_post WHERE id = :id", new MapSqlParameterSource("id", id)); }

	@Override
	public Optional<DirectionPost> findByIdForUpdate(long id) {
		return one("SELECT * FROM direction_post WHERE id = :id FOR UPDATE", new MapSqlParameterSource("id", id));
	}

	@Override
	public Optional<DirectionPost> findBySenderAndIdempotencyKey(long senderId, String idempotencyKey) {
		return one("SELECT * FROM direction_post WHERE sender_id = :senderId AND idempotency_key = :idempotencyKey",
			new MapSqlParameterSource().addValue("senderId", senderId).addValue("idempotencyKey", idempotencyKey));
	}

	@Override
	public Optional<DirectionPost> updateRequestFingerprintIfNull(long id,
		DirectionRequestFingerprint requestFingerprint) {
		return jdbc.query(DirectionPostSql.UPDATE_FINGERPRINT_IF_NULL,
			new MapSqlParameterSource().addValue("id", id).addValue("requestFingerprint", requestFingerprint.value()),
			rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty());
	}

	@Override
	public Optional<DirectionPost> findByIdAndSenderId(long id, long senderId) {
		return one("SELECT * FROM direction_post WHERE id = :id AND sender_id = :senderId",
			new MapSqlParameterSource().addValue("id", id).addValue("senderId", senderId));
	}

	private Optional<DirectionPost> one(String sql, MapSqlParameterSource p) {
		return jdbc.query(sql, p, rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty());
	}

	private static MapSqlParameterSource params(DirectionPost p) {
		return new MapSqlParameterSource().addValue("id", p.getId()).addValue("senderId", p.getSenderId())
			.addValue("questionId", p.getApprovedQuestionId()).addValue("status", p.getStatus().name())
			.addValue("idempotencyKey", p.getIdempotencyKey())
			.addValue("requestFingerprint", p.getRequestFingerprint() == null ? null : p.getRequestFingerprint().value())
			.addValue("bodyText", p.getBodyText())
			.addValue("regionCode", p.getCoarseRegionCode()).addValue("moderationStatus", p.getModerationStatus().name())
			.addValue("submittedAt", timestamp(p.getSubmittedAt())).addValue("publishedAt", timestamp(p.getPublishedAt()))
			.addValue("expiresAt", timestamp(p.getExpiresAt()))
			.addValue("answersReadAt", timestamp(p.getAnswersReadAt()))
			.addValue("deletedAt", timestamp(p.getDeletedAt()));
	}

	private static Timestamp timestamp(java.time.Instant value) { return value == null ? null : Timestamp.from(value); }

	private static DirectionPost map(ResultSet rs) throws SQLException {
		return DirectionPost.restore(rs.getLong("id"), rs.getLong("sender_id"), rs.getLong("approved_question_id"),
			DirectionRequestFingerprint.restore(rs.getString("request_fingerprint")),
			DirectionPostStatus.valueOf(rs.getString("status")), rs.getString("idempotency_key"), rs.getString("body_text"),
			rs.getString("coarse_region_code"), DirectionPostModerationStatus.valueOf(rs.getString("moderation_status")),
			rs.getTimestamp("submitted_at").toInstant(), rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
			rs.getTimestamp("expires_at").toInstant(),
			rs.getTimestamp("answers_read_at") == null ? null : rs.getTimestamp("answers_read_at").toInstant(),
			rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant());
	}
}
