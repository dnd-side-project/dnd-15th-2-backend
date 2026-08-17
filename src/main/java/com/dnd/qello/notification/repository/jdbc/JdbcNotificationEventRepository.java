package com.dnd.qello.notification.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.notification.domain.NotificationEvent;
import com.dnd.qello.notification.domain.NotificationEventStatus;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.repository.NotificationEventRepository;
import com.dnd.qello.notification.repository.jdbc.sql.NotificationSql;

// notification_event 전용 JDBC 구현이다(#111). OutboxEventRepository와 메서드
// 시그니처(complete/fail)가 같아 JdbcNotificationRepository에 함께 둘 수 없어
// 별도 클래스로 분리했다 — 두 인터페이스는 서로 다른 테이블을 다룬다.
@Repository
public class JdbcNotificationEventRepository implements NotificationEventRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcNotificationEventRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public NotificationEvent save(NotificationEvent event) {
		Long id = jdbc.queryForObject(NotificationSql.INSERT_NOTIFICATION_EVENT, params(event), Long.class);
		return findById(id).orElseThrow();
	}

	@Override
	public Optional<NotificationEvent> findByCaseId(long caseId) {
		return jdbc.query("SELECT * FROM notification_event WHERE case_id = :caseId",
			new MapSqlParameterSource("caseId", caseId), (rs, row) -> map(rs)).stream().findFirst();
	}

	@Override
	public List<NotificationEvent> claimDue(int limit, String leaseOwner, Instant at, Instant leaseExpiresAt) {
		if (limit <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "limit",
				"limit은 양수여야 합니다.");
		}
		validateLeaseRequest(leaseOwner, at, leaseExpiresAt);
		return jdbc.query(NotificationSql.CLAIM_DUE_NOTIFICATION_EVENTS,
			new MapSqlParameterSource().addValue("limit", limit).addValue("at", timestamp(at))
				.addValue("leaseOwner", leaseOwner).addValue("leaseExpiresAt", timestamp(leaseExpiresAt)),
			(rs, row) -> map(rs));
	}

	@Override
	public boolean complete(long id, String leaseOwner, long leaseGeneration, Instant processedAt) {
		validateLeaseIdentity(leaseOwner, leaseGeneration, processedAt);
		return jdbc.update(NotificationSql.COMPLETE_NOTIFICATION_EVENT,
			new MapSqlParameterSource().addValue("id", id)
				.addValue("leaseOwner", leaseOwner).addValue("leaseGeneration", leaseGeneration)
				.addValue("processedAt", timestamp(processedAt))) == 1;
	}

	@Override
	public boolean fail(long id, String leaseOwner, long leaseGeneration, Instant at,
			Instant nextAttemptAt, boolean dead) {
		validateLeaseIdentity(leaseOwner, leaseGeneration, at);
		if (nextAttemptAt == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "nextAttemptAt",
				"nextAttemptAt은 필수입니다.");
		}
		return jdbc.update(NotificationSql.FAIL_NOTIFICATION_EVENT,
			new MapSqlParameterSource().addValue("id", id)
				.addValue("nextStatus", dead ? NotificationEventStatus.DEAD.name() : NotificationEventStatus.FAILED.name())
				.addValue("nextAttemptAt", timestamp(nextAttemptAt)).addValue("leaseOwner", leaseOwner)
				.addValue("leaseGeneration", leaseGeneration).addValue("at", timestamp(at))) == 1;
	}

	private Optional<NotificationEvent> findById(long id) {
		return jdbc.query("SELECT * FROM notification_event WHERE id = :id",
			new MapSqlParameterSource("id", id), (rs, row) -> map(rs)).stream().findFirst();
	}

	private static MapSqlParameterSource params(NotificationEvent e) {
		return new MapSqlParameterSource().addValue("caseId", e.caseId())
			.addValue("adminLinkPath", e.adminLinkPath()).addValue("status", e.status().name())
			.addValue("attemptCount", e.attemptCount()).addValue("nextAttemptAt", timestamp(e.nextAttemptAt()))
			.addValue("createdAt", timestamp(e.createdAt())).addValue("processedAt", timestamp(e.processedAt()))
			.addValue("leaseOwner", e.leaseOwner()).addValue("leaseExpiresAt", timestamp(e.leaseExpiresAt()))
			.addValue("leaseGeneration", e.leaseGeneration());
	}

	private static NotificationEvent map(ResultSet rs) throws SQLException {
		return new NotificationEvent(rs.getLong("id"), rs.getLong("case_id"), rs.getString("admin_link_path"),
			NotificationEventStatus.valueOf(rs.getString("status")), rs.getInt("attempt_count"),
			instant(rs, "next_attempt_at"), instant(rs, "created_at"), instant(rs, "processed_at"),
			rs.getString("lease_owner"), instant(rs, "lease_expires_at"), rs.getLong("lease_generation"));
	}

	private static void validateLeaseRequest(String owner, Instant at, Instant leaseExpiresAt) {
		if (owner == null || owner.isBlank() || owner.length() > 100 || at == null || leaseExpiresAt == null
				|| !leaseExpiresAt.isAfter(at)) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "lease",
				"lease owner와 현재 시각 이후의 만료 시각이 필요합니다.");
		}
	}

	private static void validateLeaseIdentity(String owner, long generation, Instant at) {
		if (owner == null || owner.isBlank() || owner.length() > 100 || generation < 0 || at == null) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "lease",
				"lease owner, 유효한 generation과 현재 시각이 필요합니다.");
		}
	}

	private static Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}
}
