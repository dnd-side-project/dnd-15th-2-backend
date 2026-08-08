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

import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.feed.config.FeedDistanceProperties;
import com.dnd.qello.feed.repository.InboxQueryRepository;
import com.dnd.qello.feed.repository.jdbc.sql.InboxQuerySql;
import com.dnd.qello.feed.view.InboxCard;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.view.InboxDetail;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JdbcInboxQueryRepository implements InboxQueryRepository {

	/** SKIP_PENDING은 되돌릴 수 있으므로 아직 답변 안 한 쪽에 남는다. */
	private static final String UNANSWERED_STATUSES = "('AVAILABLE','DISCOVERED','OPENED','SKIP_PENDING')";

	private final NamedParameterJdbcTemplate jdbc;
	private final FeedDistanceProperties feedDistanceProperties;

	@Override
	public List<InboxCard> findInbox(long recipientId, InboxCategory category, Instant at) {
		String statusFilter = switch (category) {
			case UNANSWERED -> "pr.status IN " + UNANSWERED_STATUSES;
			case ANSWERED -> "pr.status = 'ANSWERED'";
		};
		// statusFilter는 텍스트 블록이 아니라 일반 String 연결이다 — 텍스트 블록은 줄 끝
		// 공백을 잘라내므로 "AND" 뒤에 이어붙이면 "ANDpr.status..."처럼 토큰이 붙는다.
		return jdbc.query(InboxQuerySql.SELECT_CARD + "WHERE pr.recipient_id = :recipientId AND " + statusFilter + """

			  AND dp.status = 'ACTIVE'
			  AND dp.deleted_at IS NULL
			  AND dp.expires_at > :at
			  AND NOT EXISTS (SELECT 1 FROM user_block ub
			                  WHERE ub.blocker_id = :recipientId
			                    AND ub.blocked_id = dp.sender_id
			                    AND ub.released_at IS NULL)
			ORDER BY pr.matched_at DESC, pr.id DESC
			""", params(recipientId).addValue("at", Timestamp.from(at)), (rs, rowNum) -> card(rs));
	}

	@Override
	public Optional<InboxDetail> findDetail(long recipientId, long postRecipientId) {
		return jdbc.query(InboxQuerySql.SELECT_CARD + """
			WHERE pr.id = :postRecipientId
			  AND pr.recipient_id = :recipientId
			  AND dp.deleted_at IS NULL
			""", params(recipientId).addValue("postRecipientId", postRecipientId),
			rs -> rs.next()
				? Optional.of(new InboxDetail(card(rs), FeedRowMappers.instant(rs, "opened_at"),
					FeedRowMappers.instant(rs, "skip_requested_at")))
				: Optional.empty());
	}

	private MapSqlParameterSource params(long recipientId) {
		return new MapSqlParameterSource().addValue("recipientId", recipientId)
			.addValue("nearFloor", feedDistanceProperties.nearDistanceFloorM());
	}

	private static InboxCard card(ResultSet rs) throws SQLException {
		return new InboxCard(
			rs.getLong("post_recipient_id"),
			rs.getLong("post_id"),
			PostRecipientStatus.valueOf(rs.getString("status")),
			rs.getString("question_text"),
			rs.getString("body_text"),
			FeedRowMappers.mediaIds(rs),
			rs.getString("sender_region_code"),
			rs.getBigDecimal("inbound_bearing_deg"),
			rs.getObject("distance_m", Long.class),
			rs.getString("distance_band"),
			rs.getTimestamp("matched_at").toInstant(),
			rs.getTimestamp("expires_at").toInstant(),
			rs.getLong("answer_count"),
			rs.getLong("reaction_count"),
			rs.getLong("unread_answer_count"));
	}
}
