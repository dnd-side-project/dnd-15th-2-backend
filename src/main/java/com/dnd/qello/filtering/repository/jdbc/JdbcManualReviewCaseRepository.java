package com.dnd.qello.filtering.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.domain.ManualReviewBand;
import com.dnd.qello.filtering.domain.ManualReviewCase;
import com.dnd.qello.filtering.domain.ManualReviewCaseStatus;
import com.dnd.qello.filtering.domain.ManualReviewPriorityReasonCode;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.ManualReviewCaseRepository;

// ADR-0002: 큐 정렬(효과적 band + FIFO)이 JPA 파생 쿼리로 표현하기 어렵고,
// SnapshotHealth(#109)와 동일하게 JDBC로 구현한다.
@Repository
public class JdbcManualReviewCaseRepository implements ManualReviewCaseRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcManualReviewCaseRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public ManualReviewCase save(ManualReviewCase reviewCase) {
		if (reviewCase.id() == null) {
			return insert(reviewCase);
		}
		return update(reviewCase);
	}

	private ManualReviewCase insert(ManualReviewCase reviewCase) {
		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update("""
			INSERT INTO manual_review_case
				(target_type, target_id, target_version, filter_release_id, filter_job_id, status, band,
				 validated_report_signal_count, priority_policy_version, priority_reason_code, resolved_at,
				 resolved_by_operator_user_id, resolved_verdict, created_at)
			VALUES (:targetType, :targetId, :targetVersion, :filterReleaseId, :filterJobId, :status, :band,
			        :validatedReportSignalCount, :priorityPolicyVersion, :priorityReasonCode, :resolvedAt,
			        :resolvedByOperatorUserId, :resolvedVerdict, :createdAt)
			""", params(reviewCase), keyHolder, new String[] {"id"});
		return new ManualReviewCase(keyHolder.getKey().longValue(), reviewCase.target(), reviewCase.filterReleaseId(),
			reviewCase.filterJobId(), reviewCase.status(), reviewCase.band(), reviewCase.validatedReportSignalCount(),
			reviewCase.priorityPolicyVersion(), reviewCase.priorityReasonCode(), reviewCase.resolvedAt(),
			reviewCase.resolvedByOperatorUserId(), reviewCase.resolvedVerdict(), reviewCase.createdAt());
	}

	private ManualReviewCase update(ManualReviewCase reviewCase) {
		jdbc.update("""
			UPDATE manual_review_case
			SET status = :status, band = :band, validated_report_signal_count = :validatedReportSignalCount,
			    priority_policy_version = :priorityPolicyVersion, priority_reason_code = :priorityReasonCode,
			    resolved_at = :resolvedAt, resolved_by_operator_user_id = :resolvedByOperatorUserId,
			    resolved_verdict = :resolvedVerdict
			WHERE id = :id
			""", params(reviewCase).addValue("id", reviewCase.id()));
		return reviewCase;
	}

	@Override
	public Optional<ManualReviewCase> findById(long id) {
		return jdbc.query("SELECT * FROM manual_review_case WHERE id = :id",
			new MapSqlParameterSource("id", id), JdbcManualReviewCaseRepository::mapOptional);
	}

	@Override
	public Optional<ManualReviewCase> findByTargetAndFilterReleaseId(FilterTarget target, long filterReleaseId) {
		return jdbc.query("""
			SELECT * FROM manual_review_case
			WHERE target_type = :targetType AND target_id = :targetId AND target_version = :targetVersion
			  AND filter_release_id = :filterReleaseId
			""",
			new MapSqlParameterSource()
				.addValue("targetType", target.targetType().name())
				.addValue("targetId", target.targetId())
				.addValue("targetVersion", target.targetVersion())
				.addValue("filterReleaseId", filterReleaseId),
			JdbcManualReviewCaseRepository::mapOptional);
	}

	@Override
	public Optional<ManualReviewCase> findLatestByTarget(FilterTarget target) {
		return jdbc.query("""
			SELECT * FROM manual_review_case
			WHERE target_type = :targetType AND target_id = :targetId AND target_version = :targetVersion
			ORDER BY created_at DESC, id DESC
			LIMIT 1
			""",
			new MapSqlParameterSource()
				.addValue("targetType", target.targetType().name())
				.addValue("targetId", target.targetId())
				.addValue("targetVersion", target.targetVersion()),
			JdbcManualReviewCaseRepository::mapOptional);
	}

	@Override
	public List<ManualReviewCase> findOpenQueue(Instant agedBeforeThreshold, int limit) {
		if (limit <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "limit", "limit은 양수여야 합니다");
		}
		return jdbc.query("""
			SELECT *,
			       (band = 'HIGH' OR created_at <= :agedBeforeThreshold) AS effective_high
			FROM manual_review_case
			WHERE status = 'OPEN'
			ORDER BY effective_high DESC, created_at ASC
			LIMIT :limit
			""",
			new MapSqlParameterSource()
				.addValue("agedBeforeThreshold", timestamp(agedBeforeThreshold))
				.addValue("limit", limit),
			(rs, rowNum) -> map(rs));
	}

	private static Optional<ManualReviewCase> mapOptional(ResultSet rs) throws SQLException {
		if (!rs.next()) {
			return Optional.empty();
		}
		return Optional.of(map(rs));
	}

	private MapSqlParameterSource params(ManualReviewCase reviewCase) {
		return new MapSqlParameterSource()
			.addValue("targetType", reviewCase.target().targetType().name())
			.addValue("targetId", reviewCase.target().targetId())
			.addValue("targetVersion", reviewCase.target().targetVersion())
			.addValue("filterReleaseId", reviewCase.filterReleaseId())
			.addValue("filterJobId", reviewCase.filterJobId())
			.addValue("status", reviewCase.status().name())
			.addValue("band", reviewCase.band().name())
			.addValue("validatedReportSignalCount", reviewCase.validatedReportSignalCount())
			.addValue("priorityPolicyVersion", reviewCase.priorityPolicyVersion())
			.addValue("priorityReasonCode", reviewCase.priorityReasonCode().name())
			.addValue("resolvedAt", timestamp(reviewCase.resolvedAt()))
			.addValue("resolvedByOperatorUserId", reviewCase.resolvedByOperatorUserId())
			.addValue("resolvedVerdict", reviewCase.resolvedVerdict() == null ? null : reviewCase.resolvedVerdict().name())
			.addValue("createdAt", timestamp(reviewCase.createdAt()));
	}

	private static Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private static ManualReviewCase map(ResultSet rs) throws SQLException {
		FilterTarget target = new FilterTarget(FilterTargetType.valueOf(rs.getString("target_type")),
			rs.getLong("target_id"), rs.getLong("target_version"));
		Timestamp resolvedAt = rs.getTimestamp("resolved_at");
		// wasNull()은 직전 getX 호출을 기준으로 하므로, resolved_by_operator_user_id를
		// 읽은 직후 바로 확인해야 한다.
		long resolvedByOperatorUserId = rs.getLong("resolved_by_operator_user_id");
		Long resolvedByOperatorUserIdOrNull = rs.wasNull() ? null : resolvedByOperatorUserId;
		String resolvedVerdict = rs.getString("resolved_verdict");
		return ManualReviewCase.restore(
			rs.getLong("id"),
			target,
			rs.getLong("filter_release_id"),
			rs.getLong("filter_job_id"),
			ManualReviewCaseStatus.valueOf(rs.getString("status")),
			ManualReviewBand.valueOf(rs.getString("band")),
			rs.getInt("validated_report_signal_count"),
			rs.getString("priority_policy_version"),
			ManualReviewPriorityReasonCode.valueOf(rs.getString("priority_reason_code")),
			resolvedAt == null ? null : resolvedAt.toInstant(),
			resolvedByOperatorUserIdOrNull,
			resolvedVerdict == null ? null : FilterVerdict.valueOf(resolvedVerdict),
			rs.getTimestamp("created_at").toInstant());
	}
}
