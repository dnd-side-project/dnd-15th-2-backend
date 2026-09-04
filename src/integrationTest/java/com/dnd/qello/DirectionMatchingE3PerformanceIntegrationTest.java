/**
 * Created at: 2026-09-05T02:07:05+09:00
 * Source scenario: TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-003 through PERF-005
 *
 * GH-214 E3 실험의 결정적 cardinality sweep이다. #163이 고정한 합성 분포·이중
 * 반경 측정 방식을 10K:1K, 50K:5K, 100K:10K 세 규모로 확장해 계획기의 접근 경로가
 * 어느 규모에서 바뀌는지 관측한다. 접근 경로와 지연은 관측값이며 단언 대상이
 * 아니다 — GiST 사용 여부나 지연 임계값을 단언하지 않는다.
 *
 * <p>#163 전용 클래스(DirectionMatchingIndexPlanPerformanceIntegrationTest)는
 * 그대로 두고 이 클래스만 추가한다. 출력은 DirectionMatchingPerformanceProbe의
 * sanitized 한 줄과 비식별 plan 요약뿐이며 사용자 식별자, 닉네임, 좌표, 원문 SQL,
 * EXPLAIN 원문은 어떤 경로로도 출력하지 않는다.</p>
 */
package com.dnd.qello;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.DirectionMatchingPerformanceProbe.Measurement;
import com.dnd.qello.DirectionMatchingPerformanceProbe.PlanNodeObservation;
import com.dnd.qello.DirectionMatchingPerformanceProbe.PlanObservation;
import com.dnd.qello.DirectionMatchingPerformanceProbe.QueryFingerprint;
import com.dnd.qello.direction.config.DirectionPostProperties;
import com.dnd.qello.direction.repository.jdbc.sql.ActiveUserPresenceSql;
import com.dnd.qello.direction.service.DirectionPostPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("performance")
@SpringBootTest
@ActiveProfiles("test")
@Import(DirectionMatchingE3TestClockConfiguration.class)
class DirectionMatchingE3PerformanceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRECTION-PERF-E3";
	private static final String ACCOUNT_PREFIX = "perf-e3-account-";
	private static final String EXCLUDED_NICKNAME = "perf-e3-excluded";
	private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
	private static final double ORIGIN_LATITUDE = 37.5000;
	private static final double ORIGIN_LONGITUDE = 127.0000;
	private static final long SELECTIVITY_PROBE_MAX_DISTANCE_METERS = 5_000L;
	private static final int BASELINE_STATISTICS_TARGET = 100;
	private static final int EXPERIMENT_STATISTICS_TARGET = 1000;

	private static final String PREVIEW = "PREVIEW";
	private static final String MATCHING = "MATCHING";
	private static final String POLICY_BASELINE = "POLICY_BASELINE";
	private static final String SELECTIVITY_PROBE = "SELECTIVITY_PROBE";
	private static final double NORTH_SECTOR_START_DEGREES = 337.5;
	private static final double NORTH_SECTOR_END_DEGREES = 22.5;
	private static final List<String> OCTANT_SEGMENT_KEYS = List.of("N", "NE", "E", "SE", "S", "SW", "W", "NW");
	private static final String ACCOUNT_NICKNAME_PATTERN = ACCOUNT_PREFIX + "%";
	private static final String LOGICAL_SUFFIX_PATTERN = "\\d{6}";

	/**
	 * cardinality sweep은 guardrail 행 없이 시딩한다. PERF-008·PERF-009가 {@code guardrail-}
	 * 접두사로 추가할 정책 adversary 행은 여기서 시딩하지 않아야 {@code ACCOUNT_PREFIX} 기준 정확 카운트가 유지된다.
	 */
	private static final Runnable NO_GUARDRAIL_ROWS = () -> {
	};

	private static final String SEED_ACCOUNTS_SQL = """
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			SELECT 'USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ? || lpad(gs::text, 6, '0')
			FROM generate_series(1, ?) AS gs
			""";
	private static final String SEED_PRESENCE_SQL = """
			INSERT INTO active_user_presence
				(user_id, position, coarse_region_code, accuracy_m, receive_allowed, location_at, expires_at)
			SELECT ua.id,
			       ST_Project(
			           ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
			           sqrt((gs - 0.5) / ?) * 100000,
			           radians(mod((gs - 1) * 137.50776405003785, 360))
			       ),
			       ?, 5, TRUE, ?, ?
			FROM generate_series(1, ?) AS gs
			JOIN user_account ua ON ua.nickname = ? || lpad(gs::text, 6, '0')
			""";
	private static final String SEED_EXCLUDED_SQL = """
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			""";

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NamedParameterJdbcTemplate namedJdbc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private DirectionPostPolicy policy;
	@Autowired
	private DirectionMatchingE3MutableClock clock;

	private DirectionMatchingPerformanceProbe probe;

	@BeforeAll
	static void installPgStatStatements(@Autowired JdbcTemplate jdbc) {
		jdbc.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements");
	}

	@BeforeEach
	void prepareObservation() {
		clock.setInstant(NOW);
		probe = new DirectionMatchingPerformanceProbe(jdbc, namedJdbc, objectMapper);
	}

	@AfterEach
	void cleanupAfterObservation() {
		cleanupFixture();
	}

	/**
	 * 10K:1K에서 gs=3, 50K:5K에서 gs=13은 결정적 반경식이 정확히 5,000.000m를 만드는 경계값이다.
	 * {@code ST_DWithin}의 spheroid 경로는 두 경계를 서로 다르게 판정해 10K:1K에서만 gs=3을 제외한다. 따라서
	 * 5km 진단 후보 수의 실측 결정값은 2, 13, 25다. 이 값은 합성 데이터를 조정해서 얻은 것이 아니라 승인된 반경식이 만든
	 * 관측값이며, 구현 계획이 해석적으로 적어 둔 3과 다르다.
	 */
	private static Stream<FixtureScale> scales() {
		return Stream.of(
				new FixtureScale("10K_1K", 10_000, 1_000, 2),
				new FixtureScale("50K_5K", 50_000, 5_000, 13),
				new FixtureScale("100K_10K", 100_000, 10_000, 25));
	}

	@ParameterizedTest
	@MethodSource("scales")
	@DisplayName("PERF-003·PERF-004·PERF-005: 10K:1K·50K:5K·100K:10K에서 preview·matching의 정책 반경과 5km 진단 반경 네 조합을 측정한다")
	void cardinalitySweepObservesPreviewAndMatchingPlans(FixtureScale scale) {
		seedFixture(scale);
		assertFixtureShape(scale);
		analyzeWithStatisticsTarget(BASELINE_STATISTICS_TARGET);

		long excludedUserId = excludedUserId();
		long schemeId = octantSchemeId();
		long minDistanceMeters = policy.minDistanceMeters();
		long policyMaxDistanceMeters = policy.maxDistanceMeters();

		observePreview(scale, POLICY_BASELINE,
				previewParameters(excludedUserId, schemeId, minDistanceMeters, policyMaxDistanceMeters),
				scale.presenceCount());
		observeMatching(scale, POLICY_BASELINE,
				matchingParameters(excludedUserId, minDistanceMeters, policyMaxDistanceMeters),
				scale.presenceCount());
		observePreview(scale, SELECTIVITY_PROBE,
				previewParameters(excludedUserId, schemeId, minDistanceMeters,
						SELECTIVITY_PROBE_MAX_DISTANCE_METERS),
				scale.expectedProbePresenceCount());
		observeMatching(scale, SELECTIVITY_PROBE,
				matchingParameters(excludedUserId, minDistanceMeters, SELECTIVITY_PROBE_MAX_DISTANCE_METERS),
				scale.expectedProbePresenceCount());
	}

	private void observePreview(FixtureScale scale, String radiusLabel, MapSqlParameterSource parameters,
			long expectedCandidateTotal) {
		Measurement<Map<String, Long>> measurement = probe.measure(scenarioId(scale),
				condition(scale, PREVIEW, radiusLabel), QueryFingerprint.PREVIEW,
				() -> previewCounts(parameters));
		Map<String, Long> counts = assertIdenticalResults(measurement, scale, PREVIEW, radiusLabel);

		assertThat(counts.keySet()).as("%s preview segment key", radiusLabel)
				.containsExactlyInAnyOrderElementsOf(OCTANT_SEGMENT_KEYS);
		assertThat(counts.values().stream().mapToLong(Long::longValue).sum())
				.as("%s preview 후보 합계", radiusLabel)
				.isEqualTo(expectedCandidateTotal);

		System.out.println(measurement.sanitizedLine());
		observePlan(scale, PREVIEW, radiusLabel,
				ActiveUserPresenceSql.FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL, parameters);
	}

	private void observeMatching(FixtureScale scale, String radiusLabel, MapSqlParameterSource parameters,
			long maxCandidateTotal) {
		Measurement<List<Long>> measurement = probe.measure(scenarioId(scale),
				condition(scale, MATCHING, radiusLabel), QueryFingerprint.MATCHING,
				() -> matchingCandidateIds(parameters));
		List<Long> candidateIds = assertIdenticalResults(measurement, scale, MATCHING, radiusLabel);
		List<String> suffixes = logicalAccountSuffixes(candidateIds);

		assertThat(suffixes).as("%s matching 후보 논리 라벨", radiusLabel)
				.isNotEmpty()
				.doesNotHaveDuplicates()
				.hasSizeLessThanOrEqualTo(Math.toIntExact(maxCandidateTotal))
				.allSatisfy(suffix -> {
					assertThat(suffix).matches(LOGICAL_SUFFIX_PATTERN);
					assertThat(Integer.parseInt(suffix)).isBetween(1, scale.presenceCount());
				});

		System.out.println(measurement.sanitizedLine());
		observePlan(scale, MATCHING, radiusLabel, ActiveUserPresenceSql.FIND_CANDIDATES_SQL, parameters);
	}

	private <T> T assertIdenticalResults(Measurement<T> measurement, FixtureScale scale, String queryKind,
			String radiusLabel) {
		String label = condition(scale, queryKind, radiusLabel);
		assertThat(measurement.results()).as("%s 측정 호출 수", label)
				.hasSize(DirectionMatchingPerformanceProbe.MEASURED_CALLS);
		assertThat(measurement.pgStat().calls()).as("%s pg_stat_statements calls", label)
				.isEqualTo(DirectionMatchingPerformanceProbe.MEASURED_CALLS);

		T first = measurement.results().get(0);
		assertThat(measurement.results()).as("%s 20회 결과 동일성", label)
				.allSatisfy(result -> assertThat(result).isEqualTo(first));
		return first;
	}

	private void observePlan(FixtureScale scale, String queryKind, String radiusLabel, String sql,
			MapSqlParameterSource parameters) {
		PlanObservation observation = probe.explain(queryKind, radiusLabel, sql, parameters);
		assertCompletePlan(scale, observation);
		printPlanEvidence(scale, observation);
	}

	/**
	 * 접근 경로 자체는 단언하지 않는다. 대상 relation 두 개가 계획에 존재하고, 수집된 각 노드가 추정 행 수·실제 행
	 * 수·loop·block 합계를 실제로 담고 있는지만 확인해 관측 증거가 비어 있는 채로 통과하지 않게 한다.
	 */
	private void assertCompletePlan(FixtureScale scale, PlanObservation observation) {
		String label = condition(scale, observation.queryKind(), observation.radius());
		assertThat(observation.targetNodes()).as("%s 대상 relation 노드", label)
				.isNotEmpty()
				.extracting(PlanNodeObservation::relation)
				.contains("active_user_presence", "user_account");
		assertThat(observation.targetNodes()).as("%s 대상 relation 노드 측정값", label)
				.allSatisfy(node -> {
					assertThat(node.nodeType()).as("%s node type", node.relation()).isNotBlank();
					assertThat(node.planRows()).as("%s plan rows", node.relation())
							.isNotNaN().isGreaterThanOrEqualTo(1.0);
					assertThat(node.actualRows()).as("%s actual rows", node.relation())
							.isNotNaN().isGreaterThanOrEqualTo(0.0);
					assertThat(node.actualLoops()).as("%s actual loops", node.relation())
							.isNotNaN().isGreaterThanOrEqualTo(1.0);
					assertThat(node.sharedBlocksHit() + node.sharedBlocksRead())
							.as("%s block totals", node.relation()).isGreaterThan(0.0);
				});
		assertThat(observation.planningTimeMs()).as("%s planning time", label).isGreaterThan(0.0);
		assertThat(observation.executionTimeMs()).as("%s execution time", label).isGreaterThan(0.0);
	}

	private void printPlanEvidence(FixtureScale scale, PlanObservation observation) {
		String accessPaths = observation.targetNodes().stream()
				.sorted(Comparator.comparing(PlanNodeObservation::relation)
						.thenComparing(PlanNodeObservation::nodeType))
				.map(DirectionMatchingE3PerformanceIntegrationTest::renderNode)
				.collect(Collectors.joining("; "));
		String sorts = observation.sorts().stream()
				.map(sort -> String.format(Locale.ROOT, "%s/%s space_kb=%.0f rows=%.0f", sort.method(),
						sort.spaceType(), sort.spaceUsedKb(), sort.actualRows()))
				.collect(Collectors.joining("; "));
		System.out.println(String.format(Locale.ROOT,
				"experiment=%s condition=%s partial_gist=%s planning_ms=%.3f execution_ms=%.3f "
						+ "access_paths=[%s] sorts=[%s]",
				scenarioId(scale), condition(scale, observation.queryKind(), observation.radius()),
				observation.usesPartialGist() ? "USED" : "NOT_USED", observation.planningTimeMs(),
				observation.executionTimeMs(), accessPaths, sorts));
	}

	private static String renderNode(PlanNodeObservation node) {
		return String.format(Locale.ROOT,
				"%s:%s(index=%s) plan_rows=%.0f actual_rows=%.0f loops=%.0f removed=%.0f "
						+ "hit=%.0f read=%.0f temp_read=%.0f temp_written=%.0f",
				node.relation(), node.nodeType(), node.indexName(), node.planRows(), node.actualRows(),
				node.actualLoops(), node.rowsRemovedByFilter(), node.sharedBlocksHit(),
				node.sharedBlocksRead(), node.tempBlocksRead(), node.tempBlocksWritten());
	}

	private Map<String, Long> previewCounts(MapSqlParameterSource parameters) {
		List<Map.Entry<String, Long>> rows = namedJdbc.query(
				ActiveUserPresenceSql.FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL, parameters,
				(resultSet, rowNumber) -> Map.entry(resultSet.getString("segment_key"),
						resultSet.getLong("candidate_count")));
		return rows.stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private List<Long> matchingCandidateIds(MapSqlParameterSource parameters) {
		return List.copyOf(namedJdbc.query(ActiveUserPresenceSql.FIND_CANDIDATES_SQL, parameters,
				(resultSet, rowNumber) -> resultSet.getLong("user_id")));
	}

	/**
	 * 측정 구간이 끝난 뒤에만 호출한다. 데이터베이스 ID를 논리 계정 접미사로 바꿔 정합성 단언에만 쓰고, 접미사와 ID 모두 증거 출력에는
	 * 넣지 않는다.
	 */
	private List<String> logicalAccountSuffixes(List<Long> userIds) {
		if (userIds.isEmpty()) {
			return List.of();
		}
		List<Map.Entry<Long, String>> rows = namedJdbc.query(
				"SELECT id, nickname FROM user_account WHERE id IN (:userIds)",
				new MapSqlParameterSource("userIds", userIds),
				(resultSet, rowNumber) -> Map.entry(resultSet.getLong("id"), resultSet.getString("nickname")));
		Map<Long, String> nicknames = rows.stream()
				.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
		return userIds.stream().map(userId -> logicalSuffix(nicknames.get(userId))).toList();
	}

	private static String logicalSuffix(String nickname) {
		assertThat(nickname).as("논리 계정 라벨").isNotNull();
		return nickname.startsWith(ACCOUNT_PREFIX) ? nickname.substring(ACCOUNT_PREFIX.length()) : nickname;
	}

	private void seedFixture(FixtureScale scale) {
		seedFixture(scale, NO_GUARDRAIL_ROWS);
	}

	private void seedFixture(FixtureScale scale, Runnable guardrailSeeder) {
		cleanupFixture();
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level)"
				+ " VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level)"
				+ " VALUES (?, 'KR', 'Direction Perf E3', 'REGION')", REGION);
		jdbc.update(SEED_ACCOUNTS_SQL, REGION, ACCOUNT_PREFIX, scale.accountCount());
		jdbc.update(SEED_PRESENCE_SQL, ORIGIN_LONGITUDE, ORIGIN_LATITUDE, scale.presenceCount(), REGION,
				Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW.plusSeconds(3600)),
				scale.presenceCount(), ACCOUNT_PREFIX);
		jdbc.update(SEED_EXCLUDED_SQL, REGION, EXCLUDED_NICKNAME);
		guardrailSeeder.run();
	}

	private void cleanupFixture() {
		MapSqlParameterSource region = new MapSqlParameterSource("region", REGION);
		cleanupPostGraph(region);
		cleanupAccountGraph(region);
	}

	private void cleanupPostGraph(MapSqlParameterSource region) {
		namedJdbc.update("""
				DELETE FROM outbox_event
				WHERE aggregate_type = 'POST_RECIPIENT'
				  AND aggregate_id IN (
				      SELECT pr.id FROM post_recipient pr
				      WHERE pr.post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = :region)
				         OR pr.recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = :region))
				""", region);
		namedJdbc.update("""
				DELETE FROM post_recipient
				WHERE post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = :region)
				   OR recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = :region)
				""", region);
		namedJdbc.update("""
				DELETE FROM outbox_event
				WHERE aggregate_type = 'DIRECTION_POST'
				  AND aggregate_id IN (SELECT id FROM direction_post WHERE coarse_region_code = :region)
				""", region);
		namedJdbc.update("""
				DELETE FROM post_audience
				WHERE post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = :region)
				""", region);
		namedJdbc.update("DELETE FROM direction_post WHERE coarse_region_code = :region", region);
	}

	private void cleanupAccountGraph(MapSqlParameterSource region) {
		namedJdbc.update("""
				DELETE FROM recipient_receive_state
				WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = :region)
				""", region);
		namedJdbc.update("""
				DELETE FROM approved_question
				WHERE approved_by IN (SELECT id FROM user_account WHERE coarse_region_code = :region)
				""", region);
		namedJdbc.update("""
				DELETE FROM active_user_presence
				WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = :region)
				""", region);
		namedJdbc.update("DELETE FROM user_account WHERE coarse_region_code = :region", region);
		namedJdbc.update("DELETE FROM region_code WHERE code = :region", region);
	}

	private void analyzeWithStatisticsTarget(int target) {
		assertThat(target).isIn(BASELINE_STATISTICS_TARGET, EXPERIMENT_STATISTICS_TARGET);
		jdbc.execute((ConnectionCallback<Void>) connection -> {
			try (Statement statement = connection.createStatement()) {
				statement.execute("SET default_statistics_target = " + target);
				statement.execute("ANALYZE user_account, active_user_presence");
				statement.execute("RESET default_statistics_target");
			}
			return null;
		});
	}

	private void assertFixtureShape(FixtureScale scale) {
		assertThat(policy.isGlobal()).as("운영 기본 delivery scope").isTrue();
		assertThat(policy.minDistanceMeters()).as("운영 기본 최소 반경").isZero();
		assertThat(policy.maxDistanceMeters()).as("운영 기본 최대 반경")
				.isEqualTo(DirectionPostProperties.GLOBAL_MAX_DISTANCE_METERS);
		assertThat(syntheticAccountCount()).as("%s 합성 계정 수", scale.label())
				.isEqualTo(scale.accountCount());
		assertThat(validPresenceCount()).as("%s 유효 presence 수", scale.label())
				.isEqualTo(scale.presenceCount());
		assertThat(countWithin(policy.maxDistanceMeters())).as("%s 정책 반경 후보 수", scale.label())
				.isEqualTo(scale.presenceCount());
		assertThat(countWithin(SELECTIVITY_PROBE_MAX_DISTANCE_METERS)).as("%s 5km 진단 후보 수", scale.label())
				.isEqualTo(scale.expectedProbePresenceCount());
	}

	private long syntheticAccountCount() {
		return count("""
				SELECT COUNT(*) FROM user_account
				WHERE coarse_region_code = ? AND nickname LIKE ?
				""", REGION, ACCOUNT_NICKNAME_PATTERN);
	}

	private long validPresenceCount() {
		return count("""
				SELECT COUNT(*)
				FROM active_user_presence p
				JOIN user_account ua ON ua.id = p.user_id
				WHERE ua.coarse_region_code = ? AND ua.nickname LIKE ?
				  AND p.receive_allowed = TRUE AND p.position IS NOT NULL
				  AND p.location_at <= ? AND p.expires_at > ?
				""", REGION, ACCOUNT_NICKNAME_PATTERN, Timestamp.from(NOW), Timestamp.from(NOW));
	}

	private long countWithin(long maxDistanceMeters) {
		return count("""
				SELECT COUNT(*)
				FROM active_user_presence p
				JOIN user_account ua ON ua.id = p.user_id
				WHERE ua.coarse_region_code = ? AND ua.nickname LIKE ?
				  AND ST_DWithin(p.position, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
				""", REGION, ACCOUNT_NICKNAME_PATTERN, ORIGIN_LONGITUDE, ORIGIN_LATITUDE, maxDistanceMeters);
	}

	private long count(String sql, Object... arguments) {
		Long value = jdbc.queryForObject(sql, Long.class, arguments);
		return value == null ? 0 : value;
	}

	private MapSqlParameterSource previewParameters(long excludedUserId, long schemeId, long minDistanceMeters,
			long maxDistanceMeters) {
		return commonParameters(excludedUserId, minDistanceMeters, maxDistanceMeters)
				.addValue("schemeId", schemeId);
	}

	private MapSqlParameterSource matchingParameters(long excludedUserId, long minDistanceMeters,
			long maxDistanceMeters) {
		return commonParameters(excludedUserId, minDistanceMeters, maxDistanceMeters)
				.addValue("sectorStartDegrees", NORTH_SECTOR_START_DEGREES)
				.addValue("sectorEndDegrees", NORTH_SECTOR_END_DEGREES);
	}

	private MapSqlParameterSource commonParameters(long excludedUserId, long minDistanceMeters,
			long maxDistanceMeters) {
		return new MapSqlParameterSource()
				.addValue("excludedUserId", excludedUserId)
				.addValue("originLatitude", ORIGIN_LATITUDE)
				.addValue("originLongitude", ORIGIN_LONGITUDE)
				.addValue("minDistanceMeters", minDistanceMeters)
				.addValue("maxDistanceMeters", maxDistanceMeters)
				.addValue("at", Timestamp.from(NOW))
				.addValue("regionCode", null);
	}

	private long excludedUserId() {
		Long userId = jdbc.queryForObject(
				"SELECT id FROM user_account WHERE coarse_region_code = ? AND nickname = ?",
				Long.class, REGION, EXCLUDED_NICKNAME);
		assertThat(userId).as("별도 제외 사용자").isNotNull();
		return userId;
	}

	private long octantSchemeId() {
		Long schemeId = jdbc.queryForObject(
				"SELECT id FROM direction_scheme WHERE code = 'OCTANT' AND status = 'ACTIVE'", Long.class);
		assertThat(schemeId).as("활성 OCTANT scheme").isNotNull();
		return schemeId;
	}

	private static String scenarioId(FixtureScale scale) {
		return switch (scale.label()) {
			case "10K_1K" -> "PERF-003";
			case "50K_5K" -> "PERF-004";
			case "100K_10K" -> "PERF-005";
			default -> throw new IllegalArgumentException("승인된 fixture scale label이 아닙니다");
		};
	}

	private static String condition(FixtureScale scale, String queryKind, String radiusLabel) {
		return scale.label() + "|" + queryKind + "|" + radiusLabel;
	}

	private record FixtureScale(
			String label,
			int accountCount,
			int presenceCount,
			int expectedProbePresenceCount) {
	}

}

@TestConfiguration
class DirectionMatchingE3TestClockConfiguration {

	@Bean
	@Primary
	DirectionMatchingE3MutableClock directionMatchingE3MutableClock() {
		return new DirectionMatchingE3MutableClock(
				Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC);
	}
}

final class DirectionMatchingE3MutableClock extends Clock {

	private final AtomicReference<Instant> current;
	private final ZoneId zone;

	DirectionMatchingE3MutableClock(Instant initial, ZoneId zone) {
		this.current = new AtomicReference<>(initial);
		this.zone = zone;
	}

	void setInstant(Instant instant) {
		current.set(instant);
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId requestedZone) {
		return new DirectionMatchingE3MutableClock(current.get(), requestedZone);
	}

	@Override
	public Instant instant() {
		return current.get();
	}
}
