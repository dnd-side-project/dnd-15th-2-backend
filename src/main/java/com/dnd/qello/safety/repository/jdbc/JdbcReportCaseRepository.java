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
import com.dnd.qello.safety.domain.ReportCase;
import com.dnd.qello.safety.domain.ReportCaseQueue;
import com.dnd.qello.safety.domain.ReportCaseSeverity;
import com.dnd.qello.safety.domain.ReportCaseStatus;
import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;
import com.dnd.qello.safety.repository.ReportCaseRepository;

@Repository
public class JdbcReportCaseRepository implements ReportCaseRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcReportCaseRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public ReportCase save(ReportCase reportCase) {
		Long id = jdbc.queryForObject("""
			INSERT INTO report_case (target_user_id, direction_post_id, answer_id,
				status, severity, queue, decision, created_at, resolved_at)
			VALUES (:targetUserId, :directionPostId, :answerId,
				:status, :severity, :queue, :decision, :createdAt, :resolvedAt)
			RETURNING id
			""", params(reportCase), Long.class);
		return new ReportCase(id, reportCase.targetUserId(), reportCase.directionPostId(),
			reportCase.answerId(), reportCase.status(), reportCase.severity(), reportCase.queue(),
			reportCase.decision(), reportCase.createdAt(), reportCase.resolvedAt());
	}

	@Override
	public Optional<ReportCase> findById(long id) {
		return jdbc.query("SELECT * FROM report_case WHERE id = :id", new MapSqlParameterSource("id", id),
			(rs, row) -> mapReportCase(rs)).stream().findFirst();
	}

	@Override
	public Optional<ReportCase> findByIdForUpdate(long id) {
		return jdbc.query("SELECT * FROM report_case WHERE id = :id FOR UPDATE",
			new MapSqlParameterSource("id", id), (rs, row) -> mapReportCase(rs)).stream().findFirst();
	}

	@Override
	public ReportCase update(ReportCase reportCase) {
		if (reportCase.id() == null) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING, "id", "수정에는 case id가 필요합니다");
		}
		jdbc.update("""
			UPDATE report_case
			SET status = :status, severity = :severity, queue = :queue,
				decision = :decision, resolved_at = :resolvedAt
			WHERE id = :id
			""", params(reportCase).addValue("id", reportCase.id()));
		return reportCase;
	}

	@Override
	public Optional<ReportCase> tryOpen(ReportCase reportCase) {
		String conflictTarget = conflictTargetFor(reportCase);
		Long id = jdbc.query("""
			INSERT INTO report_case (target_user_id, direction_post_id, answer_id,
				status, severity, queue, decision, created_at, resolved_at)
			VALUES (:targetUserId, :directionPostId, :answerId,
				:status, :severity, :queue, :decision, :createdAt, :resolvedAt)
			ON CONFLICT (%s) WHERE %s IS NOT NULL AND status IN ('OPEN', 'UNDER_REVIEW') DO NOTHING
			RETURNING id
			""".formatted(conflictTarget, conflictTarget), params(reportCase),
			(rs, row) -> rs.getLong("id")).stream().findFirst().orElse(null);
		if (id == null) {
			return Optional.empty();
		}
		return Optional.of(new ReportCase(id, reportCase.targetUserId(), reportCase.directionPostId(),
			reportCase.answerId(), reportCase.status(), reportCase.severity(), reportCase.queue(),
			reportCase.decision(), reportCase.createdAt(), reportCase.resolvedAt()));
	}

	@Override
	public Optional<ReportCase> findOpenByTarget(Long targetUserId, Long directionPostId, Long answerId) {
		return jdbc.query("""
			SELECT * FROM report_case
			WHERE target_user_id IS NOT DISTINCT FROM :targetUserId
			  AND direction_post_id IS NOT DISTINCT FROM :directionPostId
			  AND answer_id IS NOT DISTINCT FROM :answerId
			  AND status IN ('OPEN', 'UNDER_REVIEW')
			ORDER BY id DESC LIMIT 1
			""", new MapSqlParameterSource()
				.addValue("targetUserId", targetUserId)
				.addValue("directionPostId", directionPostId)
				.addValue("answerId", answerId),
			(rs, row) -> mapReportCase(rs)).stream().findFirst();
	}

	private static String conflictTargetFor(ReportCase reportCase) {
		if (reportCase.targetUserId() != null) {
			return "target_user_id";
		}
		if (reportCase.directionPostId() != null) {
			return "direction_post_id";
		}
		return "answer_id";
	}

	private static MapSqlParameterSource params(ReportCase reportCase) {
		return new MapSqlParameterSource()
			.addValue("targetUserId", reportCase.targetUserId())
			.addValue("directionPostId", reportCase.directionPostId())
			.addValue("answerId", reportCase.answerId())
			.addValue("status", reportCase.status().name())
			.addValue("severity", reportCase.severity().name())
			.addValue("queue", reportCase.queue().name())
			.addValue("decision", reportCase.decision() == null ? null : reportCase.decision().name())
			.addValue("createdAt", Timestamp.from(reportCase.createdAt()))
			.addValue("resolvedAt", timestamp(reportCase.resolvedAt()));
	}

	private static ReportCase mapReportCase(ResultSet rs) throws SQLException {
		return ReportCase.restore(rs.getLong("id"), nullableLong(rs, "target_user_id"),
			nullableLong(rs, "direction_post_id"), nullableLong(rs, "answer_id"),
			ReportCaseStatus.valueOf(rs.getString("status")),
			ReportCaseSeverity.valueOf(rs.getString("severity")),
			ReportCaseQueue.valueOf(rs.getString("queue")),
			nullableDecision(rs), instant(rs, "created_at"), instant(rs, "resolved_at"));
	}

	private static ModerationDecision nullableDecision(ResultSet rs) throws SQLException {
		String value = rs.getString("decision");
		return value == null ? null : ModerationDecision.valueOf(value);
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}
}
