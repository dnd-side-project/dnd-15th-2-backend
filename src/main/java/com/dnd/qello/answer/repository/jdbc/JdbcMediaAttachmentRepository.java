package com.dnd.qello.answer.repository.jdbc;

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
}
