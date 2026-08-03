package com.dnd.qello.safety.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.safety.domain.ModerationDecision;
import com.dnd.qello.safety.domain.ModerationReview;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportStatus;
import com.dnd.qello.safety.domain.UserBlock;
import com.dnd.qello.safety.repository.SafetyRepository;

@Repository
public class JdbcSafetyRepository implements SafetyRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcSafetyRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override
	public UserBlock block(UserBlock block) {
		jdbc.update("""
			INSERT INTO user_block (blocker_id, blocked_id, created_at, released_at)
			VALUES (:blockerId, :blockedId, :createdAt, :releasedAt)
			""", new MapSqlParameterSource()
			.addValue("blockerId", block.blockerId()).addValue("blockedId", block.blockedId())
			.addValue("createdAt", timestamp(block.createdAt())).addValue("releasedAt", timestamp(block.releasedAt())));
		return block;
	}

	@Override
	public Optional<UserBlock> findBlock(long blockerId, long blockedId) {
		return jdbc.query("SELECT * FROM user_block WHERE blocker_id = :blockerId AND blocked_id = :blockedId",
			new MapSqlParameterSource().addValue("blockerId", blockerId).addValue("blockedId", blockedId),
			(rs, row) -> new UserBlock(rs.getLong("blocker_id"), rs.getLong("blocked_id"),
				instant(rs, "created_at"), instant(rs, "released_at"))).stream().findFirst();
	}

	@Override
	public UserBlock releaseBlock(long blockerId, long blockedId, Instant releasedAt) {
		int updated = jdbc.update("""
			UPDATE user_block SET released_at = :releasedAt
			WHERE blocker_id = :blockerId AND blocked_id = :blockedId AND released_at IS NULL
			""", new MapSqlParameterSource().addValue("blockerId", blockerId)
			.addValue("blockedId", blockedId).addValue("releasedAt", timestamp(releasedAt)));
		if (updated != 1) throw new IllegalStateException("활성 차단을 찾을 수 없습니다");
		return findBlock(blockerId, blockedId).orElseThrow();
	}

	@Override
	public Report saveReport(Report report) {
		Long id = jdbc.queryForObject("""
			INSERT INTO report (reporter_id, target_user_id, direction_post_id, answer_id,
				reason_code, detail, status, created_at, resolved_at)
			VALUES (:reporterId, :targetUserId, :directionPostId, :answerId,
				:reasonCode, :detail, :status, :createdAt, :resolvedAt)
			RETURNING id
			""", reportParams(report), Long.class);
		return new Report(id, report.reporterId(), report.targetUserId(), report.directionPostId(),
			report.answerId(), report.reasonCode(), report.detail(), report.status(),
			report.createdAt(), report.resolvedAt());
	}

	@Override
	public Optional<Report> findReportById(long reportId) {
		return jdbc.query("SELECT * FROM report WHERE id = :id", new MapSqlParameterSource("id", reportId),
			(rs, row) -> mapReport(rs)).stream().findFirst();
	}

	@Override
	public Optional<Report> findOpenReport(long reporterId, Long targetUserId, Long directionPostId, Long answerId) {
		return jdbc.query("""
			SELECT * FROM report
			WHERE reporter_id = :reporterId
			  AND target_user_id IS NOT DISTINCT FROM :targetUserId
			  AND direction_post_id IS NOT DISTINCT FROM :directionPostId
			  AND answer_id IS NOT DISTINCT FROM :answerId
			  AND status IN ('RECEIVED', 'AUTO_HIDDEN', 'UNDER_REVIEW')
			ORDER BY id DESC LIMIT 1
			""", reportParams(reporterId, targetUserId, directionPostId, answerId),
			(rs, row) -> mapReport(rs)).stream().findFirst();
	}

	@Override
	public Report updateReport(Report report) {
		if (report.id() == null) throw new IllegalArgumentException("수정에는 report id가 필요합니다");
		jdbc.update("""
			UPDATE report SET status = :status, resolved_at = :resolvedAt WHERE id = :id
			""", new MapSqlParameterSource().addValue("id", report.id())
			.addValue("status", report.status().name()).addValue("resolvedAt", timestamp(report.resolvedAt())));
		return report;
	}

	@Override
	public ModerationReview saveReview(ModerationReview review) {
		Long id = jdbc.queryForObject("""
			INSERT INTO moderation_review
				(report_id, reviewer_id, decision, action_type, internal_note, reviewed_at)
			VALUES (:reportId, :reviewerId, :decision, :actionType, :internalNote, :reviewedAt)
			RETURNING id
			""", new MapSqlParameterSource().addValue("reportId", review.reportId())
			.addValue("reviewerId", review.reviewerId()).addValue("decision", review.decision().name())
			.addValue("actionType", review.actionType()).addValue("internalNote", review.internalNote())
			.addValue("reviewedAt", timestamp(review.reviewedAt())), Long.class);
		return new ModerationReview(id, review.reportId(), review.reviewerId(), review.decision(),
			review.actionType(), review.internalNote(), review.reviewedAt());
	}

	private static MapSqlParameterSource reportParams(Report r) {
		return reportParams(r.reporterId(), r.targetUserId(), r.directionPostId(), r.answerId())
			.addValue("reasonCode", r.reasonCode()).addValue("detail", r.detail())
			.addValue("status", r.status().name()).addValue("createdAt", timestamp(r.createdAt()))
			.addValue("resolvedAt", timestamp(r.resolvedAt()));
	}

	private static MapSqlParameterSource reportParams(long reporterId, Long targetUserId, Long directionPostId, Long answerId) {
		return new MapSqlParameterSource().addValue("reporterId", reporterId)
			.addValue("targetUserId", targetUserId).addValue("directionPostId", directionPostId)
			.addValue("answerId", answerId);
	}

	private static Report mapReport(ResultSet rs) throws SQLException {
		return new Report(rs.getLong("id"), rs.getLong("reporter_id"), nullableLong(rs, "target_user_id"),
			nullableLong(rs, "direction_post_id"), nullableLong(rs, "answer_id"), rs.getString("reason_code"),
			rs.getString("detail"), ReportStatus.valueOf(rs.getString("status")), instant(rs, "created_at"), instant(rs, "resolved_at"));
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}
	private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}
}
