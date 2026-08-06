package com.dnd.qello.feed.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.feed.repository.SentPostQueryRepository;
import com.dnd.qello.feed.view.SentPostCard;
import com.dnd.qello.feed.view.SentPostDetail;
import com.dnd.qello.feed.view.SentPostFilter;

/**
 * mediaIds/instant 변환은 JdbcInboxQueryRepository(같은 패키지)의 package-private static
 * 메서드를 그대로 재사용한다. static import 대신 클래스명으로 한정해 호출하는 이유는
 * FeedPersistenceBoundaryTest가 "다른 feature의 JDBC 구현 참조"를 소스 문자열에 특정
 * 패키지 경로 조각이 포함되는지로 판정하기 때문이다 — static import 구문은 그 전체
 * 패키지 경로를 그대로 담아 이 검사에 걸린다.
 */
@Repository
public class JdbcSentPostQueryRepository implements SentPostQueryRepository {

	private static final String SELECT_CARD = """
		SELECT dp.id AS post_id,
		       aq.question_text,
		       dp.body_text,
		       dp.coarse_region_code,
		       dp.submitted_at,
		       dp.expires_at,
		       dp.answers_read_at,
		       COALESCE((SELECT array_agg(ma.media_id ORDER BY ma.display_order)
		                 FROM media_attachment ma WHERE ma.post_id = dp.id), '{}'::bigint[]) AS media_ids,
		       (SELECT count(*) FROM answer a
		          JOIN post_recipient pra ON pra.id = a.post_recipient_id
		         WHERE pra.post_id = dp.id AND a.status = 'PUBLISHED' AND a.deleted_at IS NULL) AS answer_count,
		       (SELECT count(*) FROM post_reaction prx WHERE prx.post_id = dp.id) AS reaction_count,
		       (SELECT count(*) FROM answer a
		          JOIN post_recipient pru ON pru.id = a.post_recipient_id
		         WHERE pru.post_id = dp.id AND a.status = 'PUBLISHED' AND a.deleted_at IS NULL
		           AND a.published_at > COALESCE(dp.answers_read_at, '-infinity'::timestamptz)) AS unread_answer_count
		FROM direction_post dp
		JOIN approved_question aq ON aq.id = dp.approved_question_id
		""";

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcSentPostQueryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override
	public List<SentPostCard> findSentPosts(long senderId, SentPostFilter filter, SentPostCursor cursor,
		int limit, Instant at) {
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("senderId", senderId).addValue("at", Timestamp.from(at)).addValue("limit", limit);
		StringBuilder sql = new StringBuilder(SELECT_CARD)
			.append(" WHERE dp.sender_id = :senderId AND dp.deleted_at IS NULL");
		sql.append(switch (filter) {
			case ALL -> "";
			case IN_PROGRESS -> " AND dp.expires_at > :at";
			case EXPIRED -> " AND dp.expires_at <= :at";
		});
		if (cursor != null) {
			// row-value 비교는 ORDER BY submitted_at DESC, id DESC와 정확히 같은 순서를 따른다.
			sql.append(" AND (dp.submitted_at, dp.id) < (:cursorSubmittedAt, :cursorId)");
			params.addValue("cursorSubmittedAt", Timestamp.from(cursor.submittedAt()))
				.addValue("cursorId", cursor.postId());
		}
		sql.append(" ORDER BY dp.submitted_at DESC, dp.id DESC LIMIT :limit");
		return jdbc.query(sql.toString(), params, (rs, rowNum) -> card(rs));
	}

	@Override
	public Optional<SentPostDetail> findSentPostDetail(long senderId, long postId) {
		return jdbc.query(SELECT_CARD + """
			WHERE dp.id = :postId AND dp.sender_id = :senderId AND dp.deleted_at IS NULL
			""", new MapSqlParameterSource().addValue("postId", postId).addValue("senderId", senderId),
			rs -> rs.next()
				? Optional.of(new SentPostDetail(card(rs), JdbcInboxQueryRepository.instant(rs, "answers_read_at")))
				: Optional.empty());
	}

	private static SentPostCard card(ResultSet rs) throws SQLException {
		return new SentPostCard(
			rs.getLong("post_id"),
			rs.getString("question_text"),
			rs.getString("body_text"),
			JdbcInboxQueryRepository.mediaIds(rs),
			rs.getString("coarse_region_code"),
			rs.getTimestamp("submitted_at").toInstant(),
			rs.getTimestamp("expires_at").toInstant(),
			rs.getLong("answer_count"),
			rs.getLong("reaction_count"),
			rs.getLong("unread_answer_count"));
	}
}
