package com.dnd.qello.answer.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.answer.domain.MediaAttachment;
import com.dnd.qello.answer.repository.MediaAttachmentRepository;

@Repository
public class JdbcMediaAttachmentRepository implements MediaAttachmentRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcMediaAttachmentRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override
	public MediaAttachment save(MediaAttachment attachment) {
		jdbc.update("""
			INSERT INTO media_attachment (media_id, owner_id, post_id, answer_id, display_order)
			VALUES (:mediaId, :ownerId, :postId, :answerId, :displayOrder)
			""", new MapSqlParameterSource().addValue("mediaId", attachment.mediaId())
			.addValue("ownerId", attachment.ownerId()).addValue("postId", attachment.postId())
			.addValue("answerId", attachment.answerId()).addValue("displayOrder", attachment.displayOrder()));
		return attachment;
	}

	@Override
	public Optional<MediaAttachment> findByMediaId(long mediaId) {
		return one("SELECT * FROM media_attachment WHERE media_id = :mediaId",
			new MapSqlParameterSource("mediaId", mediaId));
	}

	@Override
	public Optional<MediaAttachment> findByMediaIdAndOwnerId(long mediaId, long ownerId) {
		return one("SELECT * FROM media_attachment WHERE media_id = :mediaId AND owner_id = :ownerId",
			new MapSqlParameterSource().addValue("mediaId", mediaId).addValue("ownerId", ownerId));
	}

	@Override
	public List<Long> findMediaIdsByPostId(long postId) {
		return jdbc.queryForList("""
			SELECT media_id
			FROM media_attachment
			WHERE post_id = :postId
			ORDER BY display_order, media_id
			""", new MapSqlParameterSource("postId", postId), Long.class);
	}

	@Override
	public List<Long> findMediaIdsByAnswerId(long answerId) {
		return jdbc.queryForList("""
			SELECT media_id
			FROM media_attachment
			WHERE answer_id = :answerId
			ORDER BY display_order, media_id
			""", new MapSqlParameterSource("answerId", answerId), Long.class);
	}

	@Override
	public void deleteByMediaId(long mediaId) {
		jdbc.update("DELETE FROM media_attachment WHERE media_id = :mediaId",
			new MapSqlParameterSource("mediaId", mediaId));
	}

	@Override
	public boolean existsOtherReadyMediaForPost(long postId, long excludingMediaId) {
		return existsOtherReadyMedia("post_id", postId, excludingMediaId);
	}

	@Override
	public boolean existsOtherReadyMediaForAnswer(long answerId, long excludingMediaId) {
		return existsOtherReadyMedia("answer_id", answerId, excludingMediaId);
	}

	private boolean existsOtherReadyMedia(String targetColumn, long targetId, long excludingMediaId) {
		Boolean exists = jdbc.queryForObject("""
			SELECT EXISTS (
				SELECT 1 FROM media_attachment ma
				JOIN media_asset m ON m.id = ma.media_id
				WHERE ma.%s = :targetId AND m.status = 'READY' AND ma.media_id <> :excludingMediaId
			)
			""".formatted(targetColumn),
			new MapSqlParameterSource().addValue("targetId", targetId).addValue("excludingMediaId", excludingMediaId),
			Boolean.class);
		return Boolean.TRUE.equals(exists);
	}

	private Optional<MediaAttachment> one(String sql, MapSqlParameterSource params) {
		return jdbc.query(sql, params, rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty());
	}

	private static MediaAttachment map(ResultSet rs) throws SQLException {
		long postId = rs.getLong("post_id");
		Long postIdValue = rs.wasNull() ? null : postId;
		long answerId = rs.getLong("answer_id");
		Long answerIdValue = rs.wasNull() ? null : answerId;
		return new MediaAttachment(rs.getLong("media_id"), rs.getLong("owner_id"), postIdValue, answerIdValue,
			rs.getInt("display_order"));
	}
}
