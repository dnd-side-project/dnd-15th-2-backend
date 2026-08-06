package com.dnd.qello.feed.repository.jdbc;

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

import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.feed.repository.InboxQueryRepository;
import com.dnd.qello.feed.view.InboxCard;
import com.dnd.qello.feed.view.InboxDetail;

@Repository
public class JdbcInboxQueryRepository implements InboxQueryRepository {

	/** 답변·넘김 확정·만료·차단으로 종료되지 않은 상태. SKIP_PENDING은 되돌릴 수 있으므로 포함한다. */
	private static final String OPEN_STATUSES = "('AVAILABLE','DISCOVERED','OPENED','SKIP_PENDING')";

	private static final String SELECT_CARD = """
		SELECT pr.id AS post_recipient_id,
		       pr.post_id,
		       pr.status,
		       pr.distance_band,
		       pr.matched_bearing_deg,
		       pr.matched_at,
		       pr.opened_at,
		       pr.skip_requested_at,
		       aq.question_text,
		       dp.body_text,
		       dp.coarse_region_code AS sender_region_code,
		       dp.expires_at,
		       COALESCE((SELECT array_agg(ma.media_id ORDER BY ma.display_order)
		                 FROM media_attachment ma WHERE ma.post_id = dp.id), '{}'::bigint[]) AS media_ids
		FROM post_recipient pr
		JOIN direction_post dp ON dp.id = pr.post_id
		JOIN approved_question aq ON aq.id = dp.approved_question_id
		""";

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcInboxQueryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override
	public List<InboxCard> findInbox(long recipientId, Instant at) {
		return jdbc.query(SELECT_CARD + """
			WHERE pr.recipient_id = :recipientId
			  AND pr.status IN """ + OPEN_STATUSES + """
			  AND dp.status = 'ACTIVE'
			  AND dp.deleted_at IS NULL
			  AND dp.expires_at > :at
			  AND NOT EXISTS (SELECT 1 FROM user_block ub
			                  WHERE ub.blocker_id = :recipientId
			                    AND ub.blocked_id = dp.sender_id
			                    AND ub.released_at IS NULL)
			ORDER BY pr.matched_at DESC, pr.id DESC
			""", new MapSqlParameterSource().addValue("recipientId", recipientId)
			.addValue("at", Timestamp.from(at)), (rs, rowNum) -> card(rs));
	}

	@Override
	public Optional<InboxDetail> findDetail(long recipientId, long postRecipientId) {
		return jdbc.query(SELECT_CARD + """
			WHERE pr.id = :postRecipientId
			  AND pr.recipient_id = :recipientId
			  AND dp.deleted_at IS NULL
			""", new MapSqlParameterSource().addValue("recipientId", recipientId)
			.addValue("postRecipientId", postRecipientId),
			rs -> rs.next()
				? Optional.of(new InboxDetail(card(rs), instant(rs, "opened_at"), instant(rs, "skip_requested_at")))
				: Optional.empty());
	}

	private static InboxCard card(ResultSet rs) throws SQLException {
		return new InboxCard(
			rs.getLong("post_recipient_id"),
			rs.getLong("post_id"),
			PostRecipientStatus.valueOf(rs.getString("status")),
			rs.getString("question_text"),
			rs.getString("body_text"),
			mediaIds(rs),
			rs.getString("sender_region_code"),
			rs.getBigDecimal("matched_bearing_deg"),
			rs.getString("distance_band"),
			rs.getTimestamp("matched_at").toInstant(),
			rs.getTimestamp("expires_at").toInstant());
	}

	/**
	 * SELECT_CARD의 array_agg(media_id) 컬럼("media_ids")을 List<Long>으로 변환한다.
	 * 첨부가 없으면 SQL이 COALESCE로 빈 배열을 주므로 null 배열은 방어적으로만 처리한다.
	 * package-private static — JdbcSentPostQueryRepository가 static import로 재사용한다.
	 */
	static List<Long> mediaIds(ResultSet rs) throws SQLException {
		Array array = rs.getArray("media_ids");
		if (array == null) return List.of();
		return Arrays.asList((Long[]) array.getArray());
	}

	/** nullable timestamptz 컬럼을 Instant로 옮긴다. opened_at, skip_requested_at처럼 값이 없을 수 있는 컬럼 전용이다. */
	static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}
}
