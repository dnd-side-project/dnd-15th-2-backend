package com.dnd.qello.direction.repository.jdbc.sql;

/**
 * JdbcDirectionPostRepository가 쓰는 SQL 상수.
 * INSERT/UPDATE는 id 유무에 따른 upsert 분기의 각 절반이다 — 분기 자체는
 * 리포지토리에 남아 있다.
 */
public final class DirectionPostSql {

	private DirectionPostSql() {
	}

	public static final String INSERT = """
		INSERT INTO direction_post
			(sender_id, approved_question_id, status, idempotency_key, body_text, coarse_region_code,
			 moderation_status, submitted_at, published_at, expires_at, answers_read_at, deleted_at)
		VALUES (:senderId, :questionId, :status, :idempotencyKey, :bodyText, :regionCode,
			:moderationStatus, :submittedAt, :publishedAt, :expiresAt, :answersReadAt, :deletedAt)
		RETURNING id
		""";

	public static final String UPDATE = """
		UPDATE direction_post
		SET sender_id = :senderId, approved_question_id = :questionId, status = :status,
		    idempotency_key = :idempotencyKey, body_text = :bodyText, coarse_region_code = :regionCode,
		    moderation_status = :moderationStatus, submitted_at = :submittedAt,
		    published_at = :publishedAt, expires_at = :expiresAt, answers_read_at = :answersReadAt,
		    deleted_at = :deletedAt
		WHERE id = :id
		RETURNING id
		""";
}
