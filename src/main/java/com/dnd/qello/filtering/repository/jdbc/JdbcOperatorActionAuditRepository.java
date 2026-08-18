package com.dnd.qello.filtering.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.dnd.qello.filtering.domain.OperatorActionAudit;
import com.dnd.qello.filtering.domain.OperatorActionTargetType;
import com.dnd.qello.filtering.domain.OperatorActionType;
import com.dnd.qello.filtering.repository.OperatorActionAuditRepository;

// ADR-0002: 순수 append-only 삽입과 단순 조회뿐이라 JPA entity를 두지 않고
// JDBC로 구현한다(SnapshotHealth·ManualReviewCase와 같은 판단).
@Repository
public class JdbcOperatorActionAuditRepository implements OperatorActionAuditRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcOperatorActionAuditRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public OperatorActionAudit save(OperatorActionAudit audit) {
		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update("""
			INSERT INTO operator_action_audit
				(operator_user_id, action_type, target_type, target_key, reason_code, reason_text,
				 policy_version, occurred_at)
			VALUES (:operatorUserId, :actionType, :targetType, :targetKey, :reasonCode, :reasonText,
			        :policyVersion, :occurredAt)
			""",
			new MapSqlParameterSource()
				.addValue("operatorUserId", audit.operatorUserId())
				.addValue("actionType", audit.actionType().name())
				.addValue("targetType", audit.targetType().name())
				.addValue("targetKey", audit.targetKey())
				.addValue("reasonCode", audit.reasonCode())
				.addValue("reasonText", audit.reasonText())
				.addValue("policyVersion", audit.policyVersion())
				.addValue("occurredAt", Timestamp.from(audit.occurredAt())),
			keyHolder, new String[] {"id"});
		return OperatorActionAudit.restore(keyHolder.getKey().longValue(), audit.operatorUserId(),
			audit.actionType(), audit.targetType(), audit.targetKey(), audit.reasonCode(), audit.reasonText(),
			audit.policyVersion(), audit.occurredAt());
	}

	@Override
	public List<OperatorActionAudit> findByTarget(OperatorActionTargetType targetType, String targetKey) {
		return jdbc.query("""
			SELECT * FROM operator_action_audit
			WHERE target_type = :targetType AND target_key = :targetKey
			ORDER BY occurred_at ASC, id ASC
			""",
			new MapSqlParameterSource()
				.addValue("targetType", targetType.name())
				.addValue("targetKey", targetKey),
			(rs, rowNum) -> map(rs));
	}

	private static OperatorActionAudit map(ResultSet rs) throws SQLException {
		return OperatorActionAudit.restore(
			rs.getLong("id"),
			rs.getLong("operator_user_id"),
			OperatorActionType.valueOf(rs.getString("action_type")),
			OperatorActionTargetType.valueOf(rs.getString("target_type")),
			rs.getString("target_key"),
			rs.getString("reason_code"),
			rs.getString("reason_text"),
			rs.getString("policy_version"),
			rs.getTimestamp("occurred_at").toInstant());
	}
}
