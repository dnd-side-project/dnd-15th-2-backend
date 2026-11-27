/**
 * Created at: 2026-08-18T16:40:00+09:00
 * Source scenario: TEST-PLAN-DIRECTION-MATCHING-VERTICAL-FLOW-PERF-001 through PERF-003
 * Source scenario: TEST-PLAN-GH-137-DIRECTION-POST-MODERATION (release fixture only, added 2026-10-07T22:14:59+09:00)
 * Source scenario: TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT-INT-001 (base와 중복된 컨텍스트 폐기 선언 제거, added 2026-10-10T15:19:46+09:00)
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.direction.repository.jdbc.sql.ActiveUserPresenceSql;
import com.dnd.qello.direction.security.CoordinateProtector;
import com.dnd.qello.direction.service.DirectionPostApplicationService;
import com.dnd.qello.direction.service.DirectionPresenceService;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0009의 앱 계산에서는 비공간 snapshot의 접근 경로를 기록한다. 공간 인덱스와 Seq Scan 금지는 더 이상 유효한
 * 계약이 아니다.
 *
 * <p>
 * {@code check}가 이 클래스에 의존하지 않도록 {@code @Tag("performance")}를 붙이고
 * build.gradle에서 {@code integrationTest}는 이 태그를 제외, 전용 {@code performanceTest}
 * 태스크만 포함한다.
 * </p>
 */
@Tag("performance")
@SpringBootTest
@ActiveProfiles("test")
@Import(DirectionFlow127TestClockConfiguration.class)
class DirectionMatchingPerformanceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRECTION-PERF-127";
	private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");
	private static final double SENDER_LAT = 37.5000;
	private static final double SENDER_LON = 127.0000;
	private static final int SYNTHETIC_PRESENCE_COUNT = 10_000;

	@Autowired
	private FilterReleaseRegistryService releaseRegistryService;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NamedParameterJdbcTemplate namedJdbc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private DirectionPresenceService presenceService;
	@Autowired
	private DirectionPostApplicationService postApplicationService;
	@Autowired
	private DirectionMatchingWorker matchingWorker;
	@Autowired
	private DirectionFlow127MutableClock clock;
	@Autowired
	private CoordinateProtector coordinateProtector;

	@BeforeEach
	void resetClock() {
		clock.setInstant(NOW);
		// #137: 본문 있는 질문글 제출은 승격된 release가 없으면 moderation job 접수에서 거절된다.
		AnswerModerationReleaseTestFixture.promotedRelease(releaseRegistryService, 1L);
	}

	@BeforeAll
	static void seedRegionOnce(@Autowired JdbcTemplate jdbc) {
		jdbc.update(
				"INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update(
				"INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Direction Perf 127', 'REGION')",
				REGION);
	}

	@AfterAll
	static void cleanupSyntheticData(@Autowired JdbcTemplate jdbc) {
		// 10,000행을 다음 실행이나 다른 테스트 클래스의 컨테이너 재사용에 남기지 않는다.
		// PERF-003이 실제 매칭을 실행해 post_recipient가 후보 user_account를 참조하므로
		// user_account 삭제보다 먼저 그 참조를 끊어야 FK 위반이 나지 않는다.
		jdbc.update("""
				DELETE FROM post_recipient pr
				WHERE pr.recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
				   OR pr.post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)
				""", REGION, REGION);
		jdbc.update(
				"DELETE FROM outbox_event WHERE aggregate_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)",
				REGION);
		jdbc.update(
				"DELETE FROM post_audience WHERE post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)",
				REGION);
		jdbc.update("DELETE FROM direction_post WHERE coarse_region_code = ?", REGION);
		jdbc.update(
				"DELETE FROM recipient_receive_state WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)",
				REGION);
		jdbc.update(
				"DELETE FROM approved_question WHERE approved_by IN (SELECT id FROM user_account WHERE coarse_region_code = ?)",
				REGION);
		jdbc.update("DELETE FROM active_user_presence WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
	}

	@Test
	@DisplayName("PERF-001: 10,000명 규모에서 미리보기의 비공간 snapshot 접근 경로를 기록한다")
	void previewCandidateCountQueryAvoidsSeqScanAtScale() {
		seedSyntheticPresence(SYNTHETIC_PRESENCE_COUNT);
		long senderId = seedSender();
		long schemeId = octantSchemeId();

		String explainSql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "
				+ ActiveUserPresenceSql.FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL;
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("schemeId", schemeId)
				.addValue("excludedUserId", senderId)
				.addValue("originLatitude", SENDER_LAT)
				.addValue("originLongitude", SENDER_LON)
				.addValue("minDistanceMeters", 0L)
				.addValue("maxDistanceMeters", 20_100_000L)
				.addValue("at", Timestamp.from(NOW))
				.addValue("regionCode", null);

		JsonNode plan = executeExplain(explainSql, params);
		assertSnapshotPlanAndRecordAccessPath(plan, "preview candidate snapshot query");
		assertThat(postApplicationService.preview(senderId).segments().stream()
				.mapToLong(segment -> segment.count()).sum()).isEqualTo(SYNTHETIC_PRESENCE_COUNT);
	}

	@Test
	@DisplayName("PERF-002: 10,000명 규모에서 매칭의 비공간 snapshot 접근 경로를 기록한다")
	void matchingCandidateSelectionQueryAvoidsSeqScanAtScale() {
		seedSyntheticPresence(SYNTHETIC_PRESENCE_COUNT);
		long senderId = seedSender();

		String explainSql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + ActiveUserPresenceSql.FIND_CANDIDATES_SQL;
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("excludedUserId", senderId)
				.addValue("originLatitude", SENDER_LAT)
				.addValue("originLongitude", SENDER_LON)
				.addValue("minDistanceMeters", 0L)
				.addValue("maxDistanceMeters", 20_100_000L)
				.addValue("sectorStartDegrees", 337.5)
				.addValue("sectorEndDegrees", 22.5)
				.addValue("at", Timestamp.from(NOW))
				.addValue("regionCode", null);

		JsonNode plan = executeExplain(explainSql, params);
		assertSnapshotPlanAndRecordAccessPath(plan, "matching candidate snapshot query");
	}

	@Test
	@DisplayName("PERF-003: 10,000명 규모에서 preview·matching 실행 지연을 증거로 기록한다(단언 없음)")
	void recordsPreviewAndMatchingLatencyAsEvidence() {
		seedSyntheticPresence(SYNTHETIC_PRESENCE_COUNT);
		long senderId = seedSender();
		long questionId = activeQuestion(senderId);
		long schemeId = octantSchemeId();

		long previewStartNanos = System.nanoTime();
		var preview = postApplicationService.preview(senderId);
		long previewDurationMs = Duration.ofNanos(System.nanoTime() - previewStartNanos).toMillis();
		assertThat(preview.schemeCode()).isEqualTo("OCTANT");

		var submitResult = postApplicationService.submit(senderId, "perf003-submit",
				new DirectionPostApplicationService.SubmitCommand(questionId, schemeId, "N", "성능 증거 수집 본문", List.of()));
		long postId = submitResult.post().getId();
		jdbc.update("UPDATE direction_post SET moderation_status = 'PASSED' WHERE id = ?", postId);

		OutboxRetryPolicy retryPolicy = new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1));
		long matchingStartNanos = System.nanoTime();
		DirectionMatchingWorker.BatchResult matchResult = matchingWorker.processBatch(
				new DirectionMatchingWorker.BatchCommand(10, "perf003-matching-worker", NOW.plusSeconds(30),
						NOW.plusSeconds(90), retryPolicy));
		long matchingDurationMs = Duration.ofNanos(System.nanoTime() - matchingStartNanos).toMillis();

		assertThat(matchResult.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		// D6의 main 전체 흐름 상대 비교는 별도 PERF-001~003에서 수행한다.
		System.out.println("PERF-003 evidence: presence_rows=" + SYNTHETIC_PRESENCE_COUNT
				+ " preview_ms=" + previewDurationMs + " matching_ms=" + matchingDurationMs);
	}

	private JsonNode executeExplain(String explainSql, MapSqlParameterSource params) {
		String planJson = namedJdbc.queryForObject(explainSql, params, String.class);
		try {
			JsonNode root = objectMapper.readTree(planJson);
			return root.get(0).get("Plan");
		} catch (Exception exception) {
			throw new IllegalStateException("EXPLAIN 출력 파싱에 실패했습니다: " + planJson, exception);
		}
	}

	private void assertSnapshotPlanAndRecordAccessPath(JsonNode plan, String label) {
		List<JsonNode> allNodes = new ArrayList<>();
		collectAllNodes(plan, allNodes);

		List<JsonNode> presenceScanNodes = allNodes.stream()
				.filter(node -> "active_user_presence".equals(text(node, "Relation Name")))
				.toList();
		assertThat(presenceScanNodes)
				.as("%s: active_user_presence를 스캔하는 계획 노드", label)
				.isNotEmpty();

		boolean usesPartialGistIndex = allNodes.stream()
				.anyMatch(node -> "active_user_presence_position_gix".equals(text(node, "Index Name")));
		String presenceAccessPaths = presenceScanNodes.stream()
				.map(node -> text(node, "Node Type") + "(" + text(node, "Index Name") + ")")
				.reduce((a, b) -> a + "; " + b)
				.orElse("none");
		System.out.println(label + " evidence: presence_access_path=[" + presenceAccessPaths
				+ "] uses_partial_gist_index=" + usesPartialGistIndex);
	}

	private void collectAllNodes(JsonNode node, List<JsonNode> out) {
		if (node == null || node.isMissingNode())
			return;
		out.add(node);
		JsonNode plans = node.get("Plans");
		if (plans != null && plans.isArray()) {
			for (JsonNode child : plans) {
				collectAllNodes(child, out);
			}
		}
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asText();
	}

	private void seedSyntheticPresence(int count) {
		jdbc.update("DELETE FROM active_user_presence WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ? AND nickname LIKE 'perf-candidate-%'",
				REGION);
		jdbc.update("""
				INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
				SELECT 'USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', 'perf-candidate-' || gs
				FROM generate_series(1, ?) AS gs
				""", REGION, count);
		DirectionMatchingPerformanceProbe.seedEncryptedPresence(jdbc, coordinateProtector, """
				SELECT ua.id AS user_id, ST_Y(point::geometry) AS latitude, ST_X(point::geometry) AS longitude
				FROM user_account ua CROSS JOIN LATERAL (
					SELECT ST_Project(ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
						50 + mod(substring(ua.nickname from 16)::integer * 31, 4950),
						radians(mod(substring(ua.nickname from 16)::integer * 137.50776405003785, 360))) AS point
				) projected
				WHERE ua.coarse_region_code = ? AND ua.nickname LIKE 'perf-candidate-%'
				""", REGION, NOW.minusSeconds(10), NOW.plusSeconds(3600), SENDER_LON, SENDER_LAT, REGION);
		jdbc.execute("ANALYZE active_user_presence");
	}

	private long seedSender() {
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ? AND nickname = 'perf-sender'", REGION);
		long senderId = jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
				VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', 'perf-sender') RETURNING id
				""", Long.class, REGION);
		presenceService.update(senderId, new DirectionPresenceService.UpdateCommand(
				BigDecimal.valueOf(SENDER_LAT), BigDecimal.valueOf(SENDER_LON), BigDecimal.ONE, true, NOW));
		return senderId;
	}

	private long octantSchemeId() {
		return jdbc.queryForObject("SELECT id FROM direction_scheme WHERE code = 'OCTANT' AND status = 'ACTIVE'",
				Long.class);
	}

	private long activeQuestion(long approverId) {
		return jdbc.queryForObject(
				"""
						INSERT INTO approved_question (source_type, status, question_text, answer_format, active_from, active_until, approved_at, approved_by)
						VALUES ('OPERATOR', 'ACTIVE', 'flow-127-perf question', 'TEXT', ?, ?, ?, ?) RETURNING id
						""",
				Long.class, Timestamp.from(NOW.minusSeconds(120)), Timestamp.from(NOW.plusSeconds(7200)),
				Timestamp.from(NOW.minusSeconds(120)), approverId);
	}
}
