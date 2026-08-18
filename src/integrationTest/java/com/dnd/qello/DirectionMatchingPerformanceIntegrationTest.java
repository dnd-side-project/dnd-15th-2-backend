/**
 * Created at: 2026-08-18T16:40:00+09:00
 * Source scenario: TEST-PLAN-DIRECTION-MATCHING-VERTICAL-FLOW-PERF-001 through PERF-003
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.direction.repository.jdbc.sql.ActiveUserPresenceSql;
import com.dnd.qello.direction.service.DirectionPostApplicationService;
import com.dnd.qello.direction.service.DirectionPresenceService;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * GH-127 Gap B. 저장소 전체에 {@code EXPLAIN} 사용이 0건이었다 — 10,000명 규모에서
 * {@code active_user_presence}의 부분 GIST 인덱스
 * ({@code active_user_presence_position_gix})를 후보 조회가 실제로 타는지 확인된 적이
 * 없었다. 판정 기준은 실행계획이고 지연은 증거일 뿐이다(TASK.md 결정 2) —
 * Testcontainers 머신 편차로 지연 임계값을 단언하면 회귀 탐지력보다 잡음이 커진다.
 *
 * <p>{@code check}가 이 클래스에 의존하지 않도록 {@code @Tag("performance")}를 붙이고
 * build.gradle에서 {@code integrationTest}는 이 태그를 제외, 전용
 * {@code performanceTest} 태스크만 포함한다.</p>
 */
@Tag("performance")
@SpringBootTest
@ActiveProfiles("test")
@Import(DirectionFlow127TestClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DirectionMatchingPerformanceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRECTION-PERF-127";
	private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");
	private static final double SENDER_LAT = 37.5000;
	private static final double SENDER_LON = 127.0000;
	private static final int SYNTHETIC_PRESENCE_COUNT = 10_000;

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

	@BeforeEach
	void resetClock() {
		clock.setInstant(NOW);
	}

	@BeforeAll
	static void seedRegionOnce(@Autowired JdbcTemplate jdbc) {
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Direction Perf 127', 'REGION')", REGION);
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
		jdbc.update("DELETE FROM outbox_event WHERE aggregate_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM post_audience WHERE post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM direction_post WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM recipient_receive_state WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM approved_question WHERE approved_by IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM active_user_presence WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
	}

	@Test
	@DisplayName("PERF-001: 10,000명 규모·운영 기본값에서 미리보기 집계 쿼리는 active_user_presence에 Seq Scan을 쓰지 않는다")
	void previewCandidateCountQueryAvoidsSeqScanAtScale() {
		seedSyntheticPresence(SYNTHETIC_PRESENCE_COUNT);
		long senderId = seedSender();
		long schemeId = octantSchemeId();

		String explainSql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + ActiveUserPresenceSql.FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL;
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
		assertNoSeqScanAndRecordAccessPath(plan, "preview candidate count query");
	}

	@Test
	@DisplayName("PERF-002: 10,000명 규모·운영 기본값에서 매칭 후보 조회 쿼리는 active_user_presence에 Seq Scan을 쓰지 않는다")
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
		assertNoSeqScanAndRecordAccessPath(plan, "matching candidate selection query");
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
		// PASS/FAIL 판정에는 쓰지 않는다 — 실행계획(PERF-001/002)이 판정 기준이고 이 수치는
		// 보고서에 옮겨 적을 증거일 뿐이다. Testcontainers 머신 편차로 임계값을 단언하면
		// 회귀 탐지력보다 잡음이 커진다(TASK.md 결정 2).
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

	/**
	 * PASS/FAIL 기준은 Seq Scan 부재뿐이다. 실측 결과 10,000행 규모에서는 계획기가
	 * {@code user_account}(작은 driving 테이블)를 Seq Scan한 뒤 {@code active_user_presence}를
	 * {@code active_user_presence_pkey}(user_id 기준)로 nested-loop join하고, 부분 GIST
	 * 인덱스({@code active_user_presence_position_gix})는 이 두 쿼리 형태에서 전혀 선택되지
	 * 않았다 — PK join이 이미 1:1 관계라 공간 인덱스보다 저렴하기 때문이다. 이는 인덱스나
	 * 코드 결함이 아니라 이 규모·쿼리 형태의 계획기 판단이므로 GIST 인덱스 사용 자체를
	 * 단언하지 않는다. 실제 사용된 접근 경로는 증거로만 기록해 보고서와 후속 Issue 판단에
	 * 맡긴다(TASK.md 결정 4 — 구현 결함을 발견해도 이 브랜치에서 고치지 않는다).
	 */
	private void assertNoSeqScanAndRecordAccessPath(JsonNode plan, String label) {
		List<JsonNode> allNodes = new ArrayList<>();
		collectAllNodes(plan, allNodes);

		List<JsonNode> presenceScanNodes = allNodes.stream()
			.filter(node -> "active_user_presence".equals(text(node, "Relation Name")))
			.toList();
		assertThat(presenceScanNodes)
			.as("%s: active_user_presence를 스캔하는 계획 노드", label)
			.isNotEmpty();

		boolean usesSeqScan = presenceScanNodes.stream()
			.anyMatch(node -> "Seq Scan".equals(text(node, "Node Type")));
		assertThat(usesSeqScan).as("%s: active_user_presence에 Seq Scan이 없어야 한다", label).isFalse();

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
		if (node == null || node.isMissingNode()) return;
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
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ? AND nickname LIKE 'perf-candidate-%'", REGION);
		jdbc.update("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			SELECT 'USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', 'perf-candidate-' || gs
			FROM generate_series(1, ?) AS gs
			""", REGION, count);
		jdbc.update("""
			INSERT INTO active_user_presence
				(user_id, position, coarse_region_code, accuracy_m, receive_allowed, location_at, expires_at)
			SELECT ua.id,
			       ST_Project(ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, 50 + random() * 4950, radians(random() * 360)),
			       ?, 5, TRUE, ?, ?
			FROM user_account ua
			WHERE ua.coarse_region_code = ? AND ua.nickname LIKE 'perf-candidate-%'
			""", SENDER_LON, SENDER_LAT, REGION, Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW.plusSeconds(3600)), REGION);
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
		return jdbc.queryForObject("SELECT id FROM direction_scheme WHERE code = 'OCTANT' AND status = 'ACTIVE'", Long.class);
	}

	private long activeQuestion(long approverId) {
		return jdbc.queryForObject("""
			INSERT INTO approved_question (source_type, status, question_text, answer_format, active_from, active_until, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', 'flow-127-perf question', 'TEXT', ?, ?, ?, ?) RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(120)), Timestamp.from(NOW.plusSeconds(7200)),
			Timestamp.from(NOW.minusSeconds(120)), approverId);
	}
}
