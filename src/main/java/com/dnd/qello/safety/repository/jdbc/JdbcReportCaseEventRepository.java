package com.dnd.qello.safety.repository.jdbc;

import java.sql.Timestamp;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.dnd.qello.safety.domain.ReportCaseEvent;
import com.dnd.qello.safety.repository.ReportCaseEventRepository;

@Repository
public class JdbcReportCaseEventRepository implements ReportCaseEventRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcReportCaseEventRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public ReportCaseEvent save(ReportCaseEvent event) {
		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbc.update("""
			INSERT INTO report_case_event (case_id, event_type, detail, occurred_at)
			VALUES (:caseId, :eventType, :detail, :occurredAt)
			""", new MapSqlParameterSource()
				.addValue("caseId", event.caseId())
				.addValue("eventType", event.eventType().name())
				.addValue("detail", event.detail())
				.addValue("occurredAt", Timestamp.from(event.occurredAt())),
			keyHolder, new String[] {"id"});
		return ReportCaseEvent.restore(keyHolder.getKey().longValue(), event.caseId(), event.eventType(),
			event.detail(), event.occurredAt());
	}
}
