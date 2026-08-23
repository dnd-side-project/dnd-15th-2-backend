/**
 * Created at: 2026-08-21T20:00:00+09:00
 * Source scenario: TEST-PLAN-GH-157-REPORT-LEGAL-PRODUCTION-GATE-INT-006 through INT-011,
 * INT-014, INT-015
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.safety.domain.ReportContentSnapshot;
import com.dnd.qello.safety.repository.ReportContentSnapshotRepository;

/**
 * V26이 {@code report_content_snapshot}의 append-only 트리거에 뚫는
 * "media_object_keys만 비우는" 좁은 예외가 그 외의 모든 변경과 DELETE를
 * 여전히 거부하는지 직접 검증한다(#157, INV-RPT-004 유지). {@code
 * report_case_event}는 이 변경과 무관하게 기존 트리거를 그대로 쓰므로
 * 회귀 방지 차원에서 함께 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportContentSnapshotImmutabilityIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH157-IMMUT";
	private static final Instant NOW = Instant.parse("2026-08-21T12:00:00Z");
	private static final String FIXED_HASH = "0".repeat(64);

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ReportContentSnapshotRepository reportContentSnapshotRepository;

	private long reporterId;
	private long targetUserId;

	@BeforeEach
	void resetSchemaFixtures() {
		jdbc.update("TRUNCATE report_case_event");
		jdbc.update("TRUNCATE report_content_snapshot");
		jdbc.update("DELETE FROM moderation_review");
		jdbc.update("DELETE FROM report_case");
		jdbc.update("DELETE FROM report");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) "
			+ "VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) "
			+ "VALUES (?, 'KR', 'GH157 Test', 'REGION')", REGION);

		reporterId = account("reporter");
		targetUserId = account("target");
	}

	@Test
	@DisplayName("INT-006: purgeMedia는 media_object_keys만 비우고 나머지 컬럼은 그대로 둔다")
	void purgeMediaClearsOnlyMediaObjectKeys() {
		long reportId = report(targetUserId, NOW);
		Instant purgeAfter = NOW.minus(Duration.ofDays(1));
		insertSnapshot(reportId, NOW, false, purgeAfter, List.of("media-key-1"));

		reportContentSnapshotRepository.purgeMedia(reportId);

		ReportContentSnapshot purged = reportContentSnapshotRepository.findByReportId(reportId).orElseThrow();
		assertThat(purged.mediaObjectKeys()).isEmpty();
		assertThat(purged.bodyText()).isEqualTo("본문");
		assertThat(purged.contentHash()).isEqualTo(FIXED_HASH);
		assertThat(purged.legalHold()).isFalse();
		assertThat(purged.purgeAfter()).isEqualTo(purgeAfter);
	}

	@Test
	@DisplayName("INT-007: legal_hold인 스냅샷은 purgeMedia가 거부한다")
	void purgeMediaRejectsLegalHoldSnapshot() {
		long reportId = report(targetUserId, NOW);
		insertSnapshot(reportId, NOW, true, NOW.minus(Duration.ofDays(1)), List.of("media-key-1"));

		assertThatThrownBy(() -> reportContentSnapshotRepository.purgeMedia(reportId))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	@DisplayName("INT-008: body_text를 바꾸는 raw UPDATE는 트리거가 거부한다")
	void rawUpdateChangingBodyTextIsRejected() {
		long reportId = report(targetUserId, NOW);
		insertSnapshot(reportId, NOW, false, NOW.plus(Duration.ofDays(180)), List.of());

		assertThatThrownBy(() -> jdbc.update(
			"UPDATE report_content_snapshot SET body_text = '변조된 본문' WHERE report_id = ?", reportId))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	@DisplayName("INT-009: media_object_keys와 다른 컬럼을 동시에 바꾸는 UPDATE는 거부된다")
	void rawUpdateChangingMediaKeysAndAnotherColumnIsRejected() {
		long reportId = report(targetUserId, NOW);
		insertSnapshot(reportId, NOW, false, NOW.minus(Duration.ofDays(1)), List.of("media-key-1"));

		assertThatThrownBy(() -> jdbc.update("""
			UPDATE report_content_snapshot SET media_object_keys = '{}', edit_count = 1 WHERE report_id = ?
			""", reportId))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	@DisplayName("INT-010: report_content_snapshot는 DELETE를 항상 거부한다(legal_hold 무관)")
	void deleteIsAlwaysRejected() {
		long reportId = report(targetUserId, NOW);
		insertSnapshot(reportId, NOW, false, NOW.plus(Duration.ofDays(180)), List.of());

		assertThatThrownBy(() -> jdbc.update("DELETE FROM report_content_snapshot WHERE report_id = ?", reportId))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	@DisplayName("INT-011: report_case_event 트리거는 이 변경과 무관하게 여전히 UPDATE·DELETE를 거부한다(회귀 방지)")
	void reportCaseEventTriggerStillRejectsUpdateAndDelete() {
		long caseId = reportCase(targetUserId, NOW);
		long eventId = reportCaseEvent(caseId, NOW);

		assertThatThrownBy(() -> jdbc.update(
			"UPDATE report_case_event SET event_type = 'RESOLVED' WHERE id = ?", eventId))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbc.update("DELETE FROM report_case_event WHERE id = ?", eventId))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	@DisplayName("INT-014: ILLEGAL_OR_DANGEROUS + SELF_HARM_RISK 조합은 CHECK를 통과한다")
	void selfHarmRiskCombinationIsAccepted() {
		Long id = jdbc.queryForObject("""
			INSERT INTO report (reporter_id, target_user_id, reason_code, sub_reason_code, status, created_at)
			VALUES (?, ?, 'ILLEGAL_OR_DANGEROUS', 'SELF_HARM_RISK', 'RECEIVED', ?)
			RETURNING id
			""", Long.class, reporterId, targetUserId, Timestamp.from(NOW));

		assertThat(id).isPositive();
	}

	@Test
	@DisplayName("INT-015: SEXUAL_CONTENT + SELF_HARM_RISK 조합은 CHECK 위반으로 거부된다")
	void invalidSelfHarmRiskCombinationIsRejected() {
		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO report (reporter_id, target_user_id, reason_code, sub_reason_code, status, created_at)
			VALUES (?, ?, 'SEXUAL_CONTENT', 'SELF_HARM_RISK', 'RECEIVED', ?)
			""", reporterId, targetUserId, Timestamp.from(NOW)))
			.isInstanceOf(DataAccessException.class);
	}

	private long report(long targetUserIdValue, Instant createdAt) {
		return jdbc.queryForObject("""
			INSERT INTO report (reporter_id, target_user_id, reason_code, status, created_at)
			VALUES (?, ?, 'HATE_OR_HARASSMENT', 'RECEIVED', ?)
			RETURNING id
			""", Long.class, reporterId, targetUserIdValue, Timestamp.from(createdAt));
	}

	private long reportCase(long targetUserIdValue, Instant createdAt) {
		return jdbc.queryForObject("""
			INSERT INTO report_case (target_user_id, status, severity, queue, created_at, sla_due_at)
			VALUES (?, 'OPEN', 'NORMAL', 'STANDARD', ?, ?)
			RETURNING id
			""", Long.class, targetUserIdValue, Timestamp.from(createdAt),
			Timestamp.from(createdAt.plus(Duration.ofDays(3))));
	}

	private long reportCaseEvent(long caseId, Instant occurredAt) {
		return jdbc.queryForObject("""
			INSERT INTO report_case_event (case_id, event_type, occurred_at)
			VALUES (?, 'CASE_OPENED', ?)
			RETURNING id
			""", Long.class, caseId, Timestamp.from(occurredAt));
	}

	// 트리거 테스트는 특정 값 조합(legal_hold, 이미 지난 purge_after)이 필요해 애플리케이션
	// 경로(SafetyReportService)로는 만들 수 없다 — 실제 운영에서도 legal_hold는 애플리케이션이
	// 설정하지 않고(향후 별도 기능), purge_after가 과거인 상태는 시간 경과로만 생긴다. INSERT는
	// 트리거 대상이 아니므로 raw SQL로 직접 구성한다(RecipientSweepIntegrationTest의 만료 데이터
	// 구성 방식과 동일).
	private void insertSnapshot(
		long reportId, Instant capturedAt, boolean legalHold, Instant purgeAfter, List<String> mediaObjectKeys) {
		Array mediaArray = jdbc.execute((ConnectionCallback<Array>) connection ->
			connection.createArrayOf("text", mediaObjectKeys.toArray()));
		jdbc.update("""
			INSERT INTO report_content_snapshot (report_id, captured_at, target_type, target_id, author_id,
				body_text, media_object_keys, edit_count, content_published_at, content_hash,
				legal_hold, purge_after)
			VALUES (?, ?, 'USER', ?, ?, '본문', ?, 0, ?, ?, ?, ?)
			""", reportId, Timestamp.from(capturedAt), targetUserId, targetUserId, mediaArray,
			Timestamp.from(capturedAt), FIXED_HASH, legalHold,
			purgeAfter == null ? null : Timestamp.from(purgeAfter));
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}
}
