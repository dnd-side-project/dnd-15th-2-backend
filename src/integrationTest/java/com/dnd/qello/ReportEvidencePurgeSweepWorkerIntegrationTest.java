/**
 * Created at: 2026-08-21T20:15:00+09:00
 * Source scenario: TEST-PLAN-GH-157-REPORT-LEGAL-PRODUCTION-GATE-INT-012, INT-013
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.safety.repository.ReportContentSnapshotRepository;
import com.dnd.qello.safety.sweep.ReportEvidencePurgeSweepWorker;
import com.dnd.qello.safety.sweep.ReportEvidencePurgeSweepWorker.BatchCommand;
import com.dnd.qello.safety.sweep.ReportEvidencePurgeSweepWorker.SweepBatchResult;

@SpringBootTest
@ActiveProfiles("test")
class ReportEvidencePurgeSweepWorkerIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH157-PURGE";
	private static final Instant NOW = Instant.parse("2026-08-21T12:00:00Z");
	private static final String FIXED_HASH = "0".repeat(64);

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ReportEvidencePurgeSweepWorker purgeSweepWorker;
	@Autowired
	private ReportContentSnapshotRepository reportContentSnapshotRepository;

	private long reporterId;
	private long targetUserId;

	@BeforeEach
	void resetSchemaFixtures() {
		jdbc.update("TRUNCATE report_content_snapshot");
		jdbc.update("DELETE FROM moderation_review");
		jdbc.update("DELETE FROM report");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) "
			+ "VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) "
			+ "VALUES (?, 'KR', 'GH157 Test', 'REGION')", REGION);

		reporterId = account("reporter");
		// 스냅샷의 target_id·author_id는 report_content_snapshot 쪽에 FK가 없는
		// opaque 값이라(설계 문서 §7) report의 실제 target_user_id와 같을 필요가
		// 없다 — 신고 대상만 report() 호출마다 달리한다(uq_open_report_user 회피).
		targetUserId = account("snapshot-target");
	}

	@Test
	@DisplayName("INT-012: legal_hold가 아니고 purge_after가 지난 스냅샷만 purge된다")
	void purgesOnlyEligibleSnapshots() {
		// uq_open_report_user가 같은 (reporter_id, target_user_id) 조합의 열린
		// 신고 중복을 막으므로, 신고마다 다른 대상 사용자를 쓴다.
		long expiredReportId = report(account("target-expired"));
		insertSnapshot(expiredReportId, false, NOW.minus(Duration.ofDays(1)), List.of("k1"));
		long notYetExpiredReportId = report(account("target-not-expired"));
		insertSnapshot(notYetExpiredReportId, false, NOW.plus(Duration.ofDays(1)), List.of("k2"));
		long legalHoldReportId = report(account("target-legal-hold"));
		insertSnapshot(legalHoldReportId, true, NOW.minus(Duration.ofDays(1)), List.of("k3"));

		SweepBatchResult result = purgeSweepWorker.processBatch(new BatchCommand(10, NOW));

		assertThat(result.scanned()).isEqualTo(1);
		assertThat(result.purged()).isEqualTo(1);
		assertThat(result.failed()).isZero();
		assertThat(reportContentSnapshotRepository.findByReportId(expiredReportId).orElseThrow().mediaObjectKeys())
			.isEmpty();
		assertThat(
			reportContentSnapshotRepository.findByReportId(notYetExpiredReportId).orElseThrow().mediaObjectKeys())
			.containsExactly("k2");
		assertThat(reportContentSnapshotRepository.findByReportId(legalHoldReportId).orElseThrow().mediaObjectKeys())
			.containsExactly("k3");
	}

	@Test
	@DisplayName("INT-013: 이미 media_object_keys가 빈 만료 스냅샷은 후보에서 제외된다(멱등)")
	void alreadyPurgedSnapshotIsExcludedFromCandidates() {
		long reportId = report(account("target-already-purged"));
		insertSnapshot(reportId, false, NOW.minus(Duration.ofDays(1)), List.of());

		SweepBatchResult result = purgeSweepWorker.processBatch(new BatchCommand(10, NOW));

		assertThat(result.scanned()).isZero();
		assertThat(result.purged()).isZero();
	}

	private long report(long reportTargetUserId) {
		return jdbc.queryForObject("""
			INSERT INTO report (reporter_id, target_user_id, reason_code, status, created_at)
			VALUES (?, ?, 'HATE_OR_HARASSMENT', 'RECEIVED', ?)
			RETURNING id
			""", Long.class, reporterId, reportTargetUserId, Timestamp.from(NOW));
	}

	// legal_hold·과거 purge_after 조합은 애플리케이션 경로로 만들 수 없어 raw SQL로 직접
	// 구성한다(ReportContentSnapshotImmutabilityIntegrationTest와 동일한 이유).
	private void insertSnapshot(
		long reportId, boolean legalHold, Instant purgeAfter, List<String> mediaObjectKeys) {
		Array mediaArray = jdbc.execute((ConnectionCallback<Array>) connection ->
			connection.createArrayOf("text", mediaObjectKeys.toArray()));
		jdbc.update("""
			INSERT INTO report_content_snapshot (report_id, captured_at, target_type, target_id, author_id,
				body_text, media_object_keys, edit_count, content_published_at, content_hash,
				legal_hold, purge_after)
			VALUES (?, ?, 'USER', ?, ?, '본문', ?, 0, ?, ?, ?, ?)
			""", reportId, Timestamp.from(NOW), targetUserId, targetUserId, mediaArray,
			Timestamp.from(NOW), FIXED_HASH, legalHold, Timestamp.from(purgeAfter));
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}
}
