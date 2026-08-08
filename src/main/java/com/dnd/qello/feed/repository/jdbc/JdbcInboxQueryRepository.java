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

import com.dnd.qello.direction.config.DirectionSchemeProperties;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.feed.config.FeedDistanceProperties;
import com.dnd.qello.feed.repository.InboxQueryRepository;
import com.dnd.qello.feed.repository.jdbc.sql.InboxQuerySql;
import com.dnd.qello.feed.view.DirectionChip;
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
	private final DirectionSchemeProperties directionSchemeProperties;

	@Override
	public List<InboxCard> findInbox(long recipientId, InboxCategory category, String directionSegmentKey, Instant at) {
		boolean filterByDirection = directionSegmentKey != null;
		// statusFilter와 방향 필터 조각은 텍스트 블록이 아니라 일반 String 연결이다 —
		// 텍스트 블록은 줄 끝 공백을 잘라내므로 "AND" 뒤에 이어붙이면
		// "ANDpr.status..."처럼 토큰이 붙는다.
		String sql = InboxQuerySql.SELECT_CARD
			+ (filterByDirection ? InboxQuerySql.SEGMENT_JOIN : "")
			+ "WHERE pr.recipient_id = :recipientId AND " + statusFilter(category)
			+ (filterByDirection ? " AND seg.segment_key = :directionSegmentKey" : "")
			+ InboxQuerySql.SCOPE_FILTER
			+ "ORDER BY pr.matched_at DESC, pr.id DESC\n";
		MapSqlParameterSource params = params(recipientId).addValue("at", Timestamp.from(at));
		if (filterByDirection) {
			params.addValue("schemeCode", directionSchemeProperties.schemeCode())
				.addValue("directionSegmentKey", directionSegmentKey);
		}
		return jdbc.query(sql, params, (rs, rowNum) -> card(rs));
	}

	@Override
	public List<DirectionChip> countByDirection(long recipientId, InboxCategory category, Instant at) {
		String sql = InboxQuerySql.SELECT_CHIP_AGGREGATE
			+ "WHERE pr.recipient_id = :recipientId AND " + statusFilter(category)
			+ InboxQuerySql.SCOPE_FILTER
			+ """
				GROUP BY seg.segment_key, seg.display_name, seg.sort_order
				ORDER BY seg.sort_order
				""";
		MapSqlParameterSource params = params(recipientId).addValue("at", Timestamp.from(at))
			.addValue("schemeCode", directionSchemeProperties.schemeCode());
		return jdbc.query(sql, params, (rs, rowNum) -> new DirectionChip(
			rs.getString("segment_key"), rs.getString("display_name"),
			rs.getInt("sort_order"), rs.getLong("chip_count")));
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

	private static String statusFilter(InboxCategory category) {
		return switch (category) {
			case UNANSWERED -> "pr.status IN " + UNANSWERED_STATUSES;
			case ANSWERED -> "pr.status = 'ANSWERED'";
		};
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
