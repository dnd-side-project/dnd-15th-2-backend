package com.dnd.qello.safety.repository.jdbc;

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

import com.dnd.qello.safety.domain.ModerationDecision;
import com.dnd.qello.safety.domain.ModerationReview;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportCaseSeverity;
import com.dnd.qello.safety.domain.ReportStatus;
import com.dnd.qello.safety.domain.ReportSubReason;
import com.dnd.qello.safety.domain.UserBlock;
import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;
import com.dnd.qello.safety.repository.SafetyRepository;

@Repository
public class JdbcSafetyRepository implements SafetyRepository {

	// CRITICAL 하위 사유 목록을 ReportCaseSeverity.of(...)에서 파생시켜, 새 CRITICAL
	// 하위 사유가 추가돼도 이 쿼리가 따로 갱신되지 않아 쿼터를 우회하는 회귀를 막는다.
	private static final List<String> CRITICAL_SUB_REASONS = Arrays.stream(ReportSubReason.values())
		.filter(subReason -> ReportCaseSeverity.of(subReason) == ReportCaseSeverity.CRITICAL)
		.map(Enum::name)
		.toList();

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
		if (updated != 1) {
			throw new SafetyException(
				SafetyErrorCode.ACTIVE_BLOCK_NOT_FOUND, null, "활성 차단을 찾을 수 없습니다");
		}
		return findBlock(blockerId, blockedId).orElseThrow();
	}

	@Override
	public Report saveReport(Report report) {
		Long id = jdbc.queryForObject("""
			INSERT INTO report (reporter_id, target_user_id, direction_post_id, answer_id,
				reason_code, sub_reason_code, detail, status, created_at, resolved_at, case_id)
			VALUES (:reporterId, :targetUserId, :directionPostId, :answerId,
				:reasonCode, :subReasonCode, :detail, :status, :createdAt, :resolvedAt, :caseId)
			RETURNING id
			""", reportParams(report), Long.class);
		return new Report(id, report.reporterId(), report.targetUserId(), report.directionPostId(),
			report.answerId(), report.reasonCode(), report.detail(), report.status(),
			report.createdAt(), report.resolvedAt(), report.caseId(), report.subReasonCode());
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
			  AND status IN ('RECEIVED', 'AUTO_HIDDEN', 'UNDER_REVIEW', 'MORE_INFO_REQUIRED')
			ORDER BY id DESC LIMIT 1
			""", reportParams(reporterId, targetUserId, directionPostId, answerId),
			(rs, row) -> mapReport(rs)).stream().findFirst();
	}

	@Override
	public Report updateReport(Report report) {
		if (report.id() == null) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING, "id", "수정에는 report id가 필요합니다");
		}
		jdbc.update("""
			UPDATE report SET status = :status, resolved_at = :resolvedAt, case_id = :caseId WHERE id = :id
			""", new MapSqlParameterSource().addValue("id", report.id())
			.addValue("status", report.status().name()).addValue("resolvedAt", timestamp(report.resolvedAt()))
			.addValue("caseId", report.caseId()));
		return report;
	}

	@Override
	public Optional<Report> findMostRecentClosedReport(
		long reporterId, Long targetUserId, Long directionPostId, Long answerId) {
		return jdbc.query("""
			SELECT * FROM report
			WHERE reporter_id = :reporterId
			  AND target_user_id IS NOT DISTINCT FROM :targetUserId
			  AND direction_post_id IS NOT DISTINCT FROM :directionPostId
			  AND answer_id IS NOT DISTINCT FROM :answerId
			  AND status IN ('ACTIONED', 'NO_VIOLATION')
			ORDER BY resolved_at DESC, id DESC LIMIT 1
			""", reportParams(reporterId, targetUserId, directionPostId, answerId),
			(rs, row) -> mapReport(rs)).stream().findFirst();
	}

	@Override
	public int countReportsByReporterSince(long reporterId, Instant since) {
		Integer count = jdbc.queryForObject("""
			SELECT count(*) FROM report WHERE reporter_id = :reporterId AND created_at >= :since
			""", new MapSqlParameterSource().addValue("reporterId", reporterId)
				.addValue("since", timestamp(since)), Integer.class);
		return count == null ? 0 : count;
	}

	@Override
	public int countCriticalReportsByReporterSince(long reporterId, Instant since) {
		Integer count = jdbc.queryForObject("""
			SELECT count(*) FROM report
			WHERE reporter_id = :reporterId AND created_at >= :since AND sub_reason_code IN (:criticalSubReasons)
			""", new MapSqlParameterSource().addValue("reporterId", reporterId)
				.addValue("since", timestamp(since))
				.addValue("criticalSubReasons", CRITICAL_SUB_REASONS), Integer.class);
		return count == null ? 0 : count;
	}

	@Override
	public void acquireReporterSubmissionLock(long reporterId) {
		jdbc.query("SELECT pg_advisory_xact_lock(:reporterId)",
			new MapSqlParameterSource("reporterId", reporterId), (ResultSet rs) -> null);
	}

	@Override
	public List<Report> findReportsByReporter(
		long reporterId, Instant cursorCreatedAt, Long cursorId, int limit) {
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("reporterId", reporterId)
			.addValue("limit", limit);
		String cursorFilter = "";
		if (cursorCreatedAt != null && cursorId != null) {
			cursorFilter = " AND (created_at, id) < (:cursorCreatedAt, :cursorId)";
			params.addValue("cursorCreatedAt", timestamp(cursorCreatedAt)).addValue("cursorId", cursorId);
		}
		return jdbc.query("""
			SELECT * FROM report
			WHERE reporter_id = :reporterId
			""" + cursorFilter + """

			ORDER BY created_at DESC, id DESC
			LIMIT :limit
			""", params, (rs, row) -> mapReport(rs));
	}

	@Override
	public List<Report> findReportsByCaseId(long caseId) {
		return jdbc.query("SELECT * FROM report WHERE case_id = :caseId ORDER BY id",
			new MapSqlParameterSource("caseId", caseId), (rs, row) -> mapReport(rs));
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
			.addValue("reasonCode", r.reasonCode()).addValue("subReasonCode", r.subReasonCode())
			.addValue("detail", r.detail())
			.addValue("status", r.status().name()).addValue("createdAt", timestamp(r.createdAt()))
			.addValue("resolvedAt", timestamp(r.resolvedAt())).addValue("caseId", r.caseId());
	}

	private static MapSqlParameterSource reportParams(long reporterId, Long targetUserId, Long directionPostId, Long answerId) {
		return new MapSqlParameterSource().addValue("reporterId", reporterId)
			.addValue("targetUserId", targetUserId).addValue("directionPostId", directionPostId)
			.addValue("answerId", answerId);
	}

	private static Report mapReport(ResultSet rs) throws SQLException {
		return new Report(rs.getLong("id"), rs.getLong("reporter_id"), nullableLong(rs, "target_user_id"),
			nullableLong(rs, "direction_post_id"), nullableLong(rs, "answer_id"), rs.getString("reason_code"),
			rs.getString("detail"), ReportStatus.valueOf(rs.getString("status")), instant(rs, "created_at"),
			instant(rs, "resolved_at"), nullableLong(rs, "case_id"), rs.getString("sub_reason_code"));
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
