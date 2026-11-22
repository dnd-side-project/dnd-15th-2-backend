package com.dnd.qello.auth.repository.jdbc;

import java.sql.Timestamp;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.auth.domain.OperatorAccessLogEntry;
import com.dnd.qello.auth.repository.OperatorAccessLogRepository;

import lombok.RequiredArgsConstructor;

// ADR-0002: 순수 append-only 삽입뿐이라 JPA entity를 두지 않고 JDBC로 구현한다
// (JdbcOperatorActionAuditRepository와 같은 판단).
@Repository
@RequiredArgsConstructor
public class JdbcOperatorAccessLogRepository implements OperatorAccessLogRepository {

	private final NamedParameterJdbcTemplate jdbc;

	@Override
	public void append(OperatorAccessLogEntry entry) {
		jdbc.update("""
				INSERT INTO operator_access_log
					(event_type, operator_user_id, client_address, http_method, request_path, http_status,
					 result_code, occurred_at)
				VALUES (:eventType, :operatorUserId, :clientAddress, :httpMethod, :requestPath, :httpStatus,
				        :resultCode, :occurredAt)
				""",
				new MapSqlParameterSource()
						.addValue("eventType", entry.eventType().name())
						.addValue("operatorUserId", entry.operatorUserId())
						.addValue("clientAddress", entry.request().clientAddress())
						.addValue("httpMethod", entry.request().httpMethod())
						.addValue("requestPath", entry.request().requestPath())
						.addValue("httpStatus", entry.httpStatus())
						.addValue("resultCode", entry.resultCode())
						.addValue("occurredAt", Timestamp.from(entry.occurredAt())));
	}
}
