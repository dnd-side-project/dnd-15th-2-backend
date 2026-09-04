/**
 * Created at: 2026-09-05T02:07:05+09:00
 * Source scenario: TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-003 through PERF-005
 * Extended scenario: TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-006 through PERF-009
 *
 * GH-214 E3 실험의 결정적 cardinality sweep이다. #163이 고정한 합성 분포·이중
 * 반경 측정 방식을 10K:1K, 50K:5K, 100K:10K 세 규모로 확장해 계획기의 접근 경로가
 * 어느 규모에서 바뀌는지 관측한다. 접근 경로와 지연은 관측값이며 단언 대상이
 * 아니다 — GiST 사용 여부나 지연 임계값을 단언하지 않는다.
 *
 * <p>PERF-006~PERF-009는 같은 클래스에서 고정 100K:10K fixture 하나를 재시딩 없이
 * 통계 target 100과 1000으로 각각 측정한다. 두 조건 사이에서 바뀌는 유일한 변수는
 * {@code default_statistics_target}이며, 판정 기준은 계획 변화가 아니라 preview 집계,
 * matching 후보 order/set, 영속화된 수신자 논리 집합이 두 조건에서 동일한지다.
 * 계획이나 지연이 개선되었는지는 단언하지 않는다.</p>
 *
 * <p>#163 전용 클래스(DirectionMatchingIndexPlanPerformanceIntegrationTest)는
 * 그대로 두고 이 클래스만 추가한다. 출력은 DirectionMatchingPerformanceProbe의
 * sanitized 한 줄과 비식별 plan 요약뿐이며 사용자 식별자, 닉네임, 좌표, 원문 SQL,
 * EXPLAIN 원문은 어떤 경로로도 출력하지 않는다.</p>
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

import com.dnd.qello.DirectionMatchingPerformanceProbe.LatencyObservation;
import com.dnd.qello.DirectionMatchingPerformanceProbe.Measurement;
import com.dnd.qello.DirectionMatchingPerformanceProbe.PgStatObservation;
import com.dnd.qello.DirectionMatchingPerformanceProbe.PlanNodeObservation;
import com.dnd.qello.DirectionMatchingPerformanceProbe.PlanObservation;
import com.dnd.qello.DirectionMatchingPerformanceProbe.QueryFingerprint;
import com.dnd.qello.direction.config.DirectionPostProperties;
import com.dnd.qello.direction.config.DirectionReceiveProperties;
import com.dnd.qello.direction.config.DirectionRecipientSelectionProperties;
import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.direction.repository.jdbc.sql.ActiveUserPresenceSql;
import com.dnd.qello.direction.service.DirectionPostApplicationService;
import com.dnd.qello.direction.service.DirectionPostPolicy;
import com.dnd.qello.direction.service.DirectionPresenceService;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
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

	private static final FixtureScale FIXED_SCALE = new FixtureScale("100K_10K", 100_000, 10_000, 25);
	private static final String STATISTICS_COMPARISON_SCENARIO = "PERF-006";
	private static final String RECIPIENT_GUARDRAIL_SCENARIO = "PERF-008";
	private static final String RECIPIENT = "RECIPIENT";
	private static final String NORTH_SEGMENT_KEY = "N";
	private static final int MATCHING_BATCH_LIMIT = 10;
	private static final Duration MATCHING_LEASE = Duration.ofSeconds(60);
	private static final int OUTBOX_MAX_ATTEMPTS = 3;

	private static final String GUARDRAIL_PREFIX = "guardrail-";
	private static final String GUARDRAIL_NICKNAME_PATTERN = GUARDRAIL_PREFIX + "%";
	private static final String ELIGIBLE_NEAR = GUARDRAIL_PREFIX + "eligible-near";
	private static final String ELIGIBLE_OLD = GUARDRAIL_PREFIX + "eligible-old";
	private static final String ELIGIBLE_RECENT = GUARDRAIL_PREFIX + "eligible-recent";
	private static final String BLOCKED_BY_SENDER = GUARDRAIL_PREFIX + "blocked-by-sender";
	private static final String BLOCKED_SENDER = GUARDRAIL_PREFIX + "blocked-sender";
	private static final String INACTIVE_ACCOUNT = GUARDRAIL_PREFIX + "inactive";
	private static final String EXPIRED_PRESENCE = GUARDRAIL_PREFIX + "expired";
	private static final String OUTSIDE_PROBE_RADIUS = GUARDRAIL_PREFIX + "outside";
	private static final String FULL_SLOT = GUARDRAIL_PREFIX + "full-slot";

	/** 공정성 대비 기준. 세 eligible 행이 이 순서로만 나와야 한다. */
	private static final List<String> ELIGIBLE_FAIRNESS_ORDER = List.of(ELIGIBLE_NEAR, ELIGIBLE_OLD, ELIGIBLE_RECENT);
	/** 어떤 반경·통계 조건에서도 후보와 수신자에 나타나면 안 되는 정책 위반 라벨. */
	private static final List<String> POLICY_EXCLUDED_LABELS = List.of(BLOCKED_BY_SENDER, BLOCKED_SENDER,
			INACTIVE_ACCOUNT, EXPIRED_PRESENCE);

	/**
	 * guardrail 행은 북 sector(방위 0도)에 둔다. 앞 네 행은 100K:10K 합성 fixture의 최근접 반경(약
	 * 707m)보다 가까운 서로 다른 결정적 거리라 matching 후보 스캔 창(최대 수신자 10명 × 3배 = 30명) 안에 들어간다. 다음
	 * 네 행도 같은 창 거리에 두되 양방향 차단, 비활성 계정, 만료 presence 정책으로 후보에서 빠져야 한다 — 스캔 창 밖이라 빠지는
	 * 것이 아님을 보이기 위해서다. 마지막 행만 5km 진단 반경 밖에 두어 거리 필터를 관측한다.
	 */
	private static final List<GuardrailRow> GUARDRAIL_ROWS = List.of(
			new GuardrailRow(ELIGIBLE_NEAR, "ACTIVE", 100.0, false),
			new GuardrailRow(ELIGIBLE_OLD, "ACTIVE", 200.0, false),
			new GuardrailRow(ELIGIBLE_RECENT, "ACTIVE", 300.0, false),
			new GuardrailRow(FULL_SLOT, "ACTIVE", 400.0, false),
			new GuardrailRow(BLOCKED_BY_SENDER, "ACTIVE", 500.0, false),
			new GuardrailRow(BLOCKED_SENDER, "ACTIVE", 600.0, false),
			new GuardrailRow(INACTIVE_ACCOUNT, "BLOCKED", 650.0, false),
			new GuardrailRow(EXPIRED_PRESENCE, "ACTIVE", 680.0, true),
			new GuardrailRow(OUTSIDE_PROBE_RADIUS, "ACTIVE", 50_000.0, false));
	private static final Set<String> GUARDRAIL_LABELS = GUARDRAIL_ROWS.stream()
			.map(GuardrailRow::nickname)
			.collect(Collectors.toUnmodifiableSet());
	private static final double GUARDRAIL_BEARING_DEGREES = 0.0;

	/**
	 * production 후보 SQL에는 닉네임 필터가 없다. 그래서 sweep의 fixture 카운트와 달리 preview 합계와
	 * matching 상한은 {@code ACCOUNT_PREFIX}로 좁힐 수 없고, 반경 안에 들어온 guardrail 행 수만큼 기대값을
	 * 올려야 한다. 정책 반경에서는 근거리 eligible 3행과 full-slot, 그리고 5km 밖 1행까지 5행이 후보가 되고, 5km
	 * 진단 반경에서는 그 중 4행만 후보가 된다. 이 값은 {@link #assertGuardrailShape()}가 production 술어와
	 * 같은 조건의 직접 카운트로 다시 검증한다.
	 */
	private static final long GUARDRAIL_POLICY_CANDIDATES = 5;
	private static final long GUARDRAIL_PROBE_CANDIDATES = 4;
	/**
	 * 기준 receive state 행: 합성 presence 전체 + guardrail 4행(old, recent, full-slot,
	 * outside).
	 */
	private static final long GUARDRAIL_RECEIVE_STATE_ROWS = 4;
	private static final int SYNTHETIC_RECENT_RECEIVED_COUNT = 1;
	private static final long RECEIVE_WINDOW_SECONDS = 3_600;
	private static final long SYNTHETIC_LAST_RECEIVED_SECONDS_AGO = 10;
	private static final long ELIGIBLE_OLD_LAST_RECEIVED_SECONDS_AGO = 600;
	private static final long ELIGIBLE_RECENT_LAST_RECEIVED_SECONDS_AGO = 60;

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
	private static final String SEED_GUARDRAIL_ACCOUNT_SQL = """
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', ?, ?, 'ko-KR', 'Asia/Seoul', ?)
			""";
	private static final String SEED_GUARDRAIL_PRESENCE_SQL = """
			INSERT INTO active_user_presence
				(user_id, position, coarse_region_code, accuracy_m, receive_allowed, location_at, expires_at)
			SELECT ua.id,
			       ST_Project(
			           ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
			           CAST(? AS double precision),
			           radians(CAST(? AS double precision))
			       ),
			       ?, 5, TRUE, ?, ?
			FROM user_account ua
			WHERE ua.coarse_region_code = ? AND ua.nickname = ?
			""";
	private static final String SEED_GUARDRAIL_BLOCK_SQL = """
			INSERT INTO user_block (blocker_id, blocked_id, created_at)
			SELECT blocker.id, blocked.id, ?
			FROM user_account blocker
			JOIN user_account blocked ON blocked.coarse_region_code = blocker.coarse_region_code
			WHERE blocker.coarse_region_code = ? AND blocker.nickname = ? AND blocked.nickname = ?
			""";
	private static final String SEED_RECEIVE_STATE_SQL = """
			INSERT INTO recipient_receive_state
				(user_id, active_unhandled_count, recent_received_count, recent_window_started_at,
				 last_received_at, updated_at)
			SELECT ua.id, :activeCount, :recentCount, :windowStartedAt,
			       CAST(:lastReceivedAt AS TIMESTAMPTZ), :updatedAt
			FROM user_account ua
			JOIN active_user_presence p ON p.user_id = ua.id
			WHERE ua.coarse_region_code = :region AND ua.nickname LIKE :nicknamePattern
			""";
	/** 배치 실행이 실제로 건드린 행만 되돌리기 위해 baseline 재삽입에 사용자 식별자 조건을 더한다. */
	private static final String RESTORE_RECEIVE_STATE_SQL = SEED_RECEIVE_STATE_SQL + "  AND ua.id IN (:userIds)\n";

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NamedParameterJdbcTemplate namedJdbc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private DirectionPostPolicy policy;
	@Autowired
	private DirectionReceiveProperties receiveProperties;
	@Autowired
	private DirectionRecipientSelectionProperties selectionProperties;
	@Autowired
	private DirectionPresenceService presenceService;
	@Autowired
	private DirectionPostApplicationService postApplicationService;
	@Autowired
	private DirectionMatchingWorker matchingWorker;
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

	@Test
	@DisplayName("PERF-006·PERF-007: 고정 100K:10K fixture에서 통계 target 100과 1000의 preview 집계와 matching 후보 order/set이 동일하다")
	void statisticsTargetComparisonKeepsLogicalQueryResults() {
		seedFixture(FIXED_SCALE, this::seedGuardrailRows);
		assertFixtureShape(FIXED_SCALE);
		assertGuardrailShape();

		Map<String, QueryEvidence> evidence = new LinkedHashMap<>();
		analyzeWithStatisticsTarget(BASELINE_STATISTICS_TARGET);
		LogicalQuerySnapshot baselinePolicy = captureQuerySnapshot(BASELINE_STATISTICS_TARGET, POLICY_BASELINE,
				policy.maxDistanceMeters(), evidence);
		LogicalQuerySnapshot baselineProbe = captureQuerySnapshot(BASELINE_STATISTICS_TARGET, SELECTIVITY_PROBE,
				SELECTIVITY_PROBE_MAX_DISTANCE_METERS, evidence);

		// 두 스냅샷 사이에서 재시딩하지 않는다. 바뀌는 변수는 default_statistics_target 하나뿐이며 시각, 데이터, 쿼리,
		// 반경, 반복 횟수는 모두 위 조건과 같다.
		analyzeWithStatisticsTarget(EXPERIMENT_STATISTICS_TARGET);
		LogicalQuerySnapshot experimentPolicy = captureQuerySnapshot(EXPERIMENT_STATISTICS_TARGET, POLICY_BASELINE,
				policy.maxDistanceMeters(), evidence);
		LogicalQuerySnapshot experimentProbe = captureQuerySnapshot(EXPERIMENT_STATISTICS_TARGET, SELECTIVITY_PROBE,
				SELECTIVITY_PROBE_MAX_DISTANCE_METERS, evidence);

		assertThat(experimentPolicy).as("정책 반경 논리 스냅샷").isEqualTo(baselinePolicy);
		assertThat(experimentProbe).as("5km 진단 반경 논리 스냅샷").isEqualTo(baselineProbe);
		assertGuardrailQueryPolicy(BASELINE_STATISTICS_TARGET, baselinePolicy, baselineProbe);
		assertGuardrailQueryPolicy(EXPERIMENT_STATISTICS_TARGET, experimentPolicy, experimentProbe);
		printComparisonEvidence(evidence);
	}

	@Test
	@DisplayName("PERF-008·PERF-009: 통계 target 100과 1000에서 영속화된 수신자 논리 집합과 차단·비활성·만료·공정성·수신 한도 정책이 동일하다")
	void persistedRecipientGuardrailKeepsLogicalRecipients() {
		seedFixture(FIXED_SCALE, this::seedGuardrailRows);
		assertFixtureShape(FIXED_SCALE);
		assertGuardrailShape();

		long senderId = excludedUserId();
		long questionId = seedActiveQuestion(senderId);
		long schemeId = octantSchemeId();

		analyzeWithStatisticsTarget(BASELINE_STATISTICS_TARGET);
		RecipientRun baseline = runMatchingBatch(BASELINE_STATISTICS_TARGET, senderId, questionId, schemeId);
		// 이 run이 만든 행만 지우고 이 run이 건드린 receive state만 되돌린다. 100K:10K fixture는 재시딩하지 않는다.
		resetRun(baseline);
		assertGuardrailShape();

		analyzeWithStatisticsTarget(EXPERIMENT_STATISTICS_TARGET);
		RecipientRun experiment = runMatchingBatch(EXPERIMENT_STATISTICS_TARGET, senderId, questionId, schemeId);

		assertThat(experiment.snapshot()).as("영속화된 수신자 논리 스냅샷").isEqualTo(baseline.snapshot());
		assertThat(List.of(baseline.outcomes(), experiment.outcomes())).as("매칭 배치 처리 결과")
				.allSatisfy(outcomes -> assertThat(outcomes)
						.containsExactly(DirectionMatchingWorker.Outcome.PROCESSED));
		assertRecipientGuardrail(baseline);
		assertRecipientGuardrail(experiment);
	}

	private void observePreview(FixtureScale scale, String radiusLabel, MapSqlParameterSource parameters,
			long expectedCandidateTotal) {
		observePreview(scenarioId(scale), condition(scale, PREVIEW, radiusLabel), radiusLabel, parameters,
				expectedCandidateTotal);
	}

	private PreviewObservation observePreview(String experimentId, String label, String radiusLabel,
			MapSqlParameterSource parameters, long expectedCandidateTotal) {
		Measurement<Map<String, Long>> measurement = probe.measure(experimentId, label, QueryFingerprint.PREVIEW,
				() -> previewCounts(parameters));
		Map<String, Long> counts = assertIdenticalResults(measurement, label);

		assertThat(counts.keySet()).as("%s preview segment key", radiusLabel)
				.containsExactlyInAnyOrderElementsOf(OCTANT_SEGMENT_KEYS);
		assertThat(counts.values().stream().mapToLong(Long::longValue).sum())
				.as("%s preview 후보 합계", radiusLabel)
				.isEqualTo(expectedCandidateTotal);

		System.out.println(measurement.sanitizedLine());
		PlanObservation plan = observePlan(experimentId, label, PREVIEW, radiusLabel,
				ActiveUserPresenceSql.FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL, parameters);
		return new PreviewObservation(counts, new QueryEvidence(measurement.pgStat(), measurement.latency(), plan));
	}

	private void observeMatching(FixtureScale scale, String radiusLabel, MapSqlParameterSource parameters,
			long maxCandidateTotal) {
		observeMatching(scenarioId(scale), condition(scale, MATCHING, radiusLabel), radiusLabel, parameters,
				maxCandidateTotal, scale.presenceCount(), Set.of());
	}

	/**
	 * {@code allowedLabels}가 비어 있으면 sweep과 동일하게 합성 6자리 접미사만 허용한다. guardrail 실행만 승인된
	 * 정책 adversary 라벨을 추가로 허용하며, 그 라벨 집합은 이 클래스가 시딩한 상수 목록뿐이다.
	 */
	private MatchingObservation observeMatching(String experimentId, String label, String radiusLabel,
			MapSqlParameterSource parameters, long maxCandidateTotal, int maxSyntheticOrdinal,
			Set<String> allowedLabels) {
		Measurement<List<Long>> measurement = probe.measure(experimentId, label, QueryFingerprint.MATCHING,
				() -> matchingCandidateIds(parameters));
		List<Long> candidateIds = assertIdenticalResults(measurement, label);
		List<String> suffixes = logicalAccountSuffixes(candidateIds);

		assertThat(suffixes).as("%s matching 후보 논리 라벨", radiusLabel)
				.isNotEmpty()
				.doesNotHaveDuplicates()
				.hasSizeLessThanOrEqualTo(Math.toIntExact(maxCandidateTotal))
				.allSatisfy(suffix -> {
					if (allowedLabels.contains(suffix)) {
						return;
					}
					assertThat(suffix).matches(LOGICAL_SUFFIX_PATTERN);
					assertThat(Integer.parseInt(suffix)).isBetween(1, maxSyntheticOrdinal);
				});

		System.out.println(measurement.sanitizedLine());
		PlanObservation plan = observePlan(experimentId, label, MATCHING, radiusLabel,
				ActiveUserPresenceSql.FIND_CANDIDATES_SQL, parameters);
		return new MatchingObservation(suffixes, new QueryEvidence(measurement.pgStat(), measurement.latency(), plan));
	}

	private <T> T assertIdenticalResults(Measurement<T> measurement, String label) {
		assertThat(measurement.results()).as("%s 측정 호출 수", label)
				.hasSize(DirectionMatchingPerformanceProbe.MEASURED_CALLS);
		assertThat(measurement.pgStat().calls()).as("%s pg_stat_statements calls", label)
				.isEqualTo(DirectionMatchingPerformanceProbe.MEASURED_CALLS);

		T first = measurement.results().get(0);
		assertThat(measurement.results()).as("%s 20회 결과 동일성", label)
				.allSatisfy(result -> assertThat(result).isEqualTo(first));
		return first;
	}

	private PlanObservation observePlan(String experimentId, String label, String queryKind, String radiusLabel,
			String sql, MapSqlParameterSource parameters) {
		PlanObservation observation = probe.explain(queryKind, radiusLabel, sql, parameters);
		assertCompletePlan(label, observation);
		printPlanEvidence(experimentId, label, observation);
		return observation;
	}

	/**
	 * 접근 경로 자체는 단언하지 않는다. 대상 relation 두 개가 계획에 존재하고, 수집된 각 노드가 추정 행 수·실제 행
	 * 수·loop·block 합계를 실제로 담고 있는지만 확인해 관측 증거가 비어 있는 채로 통과하지 않게 한다.
	 *
	 * <p>
	 * {@code DirectionMatchingPerformanceProbe}는 EXPLAIN JSON에 해당 키가 없으면 0.0을 돌려준다.
	 * 그래서 네 지표의 하한은 모두 0.0을 거부해야 키를 실제로 읽었다는 검증이 된다. 실제 행 수의 하한 1.0은 이 fixture에서 참인
	 * 주장이다 — 두 반경 모두 후보 집합이 비어 있지 않고 합성 계정이 전부 ACTIVE라 두 대상 relation이 loop당 최소 한 행을
	 * 낸다. 이 전제가 깨지면 조용히 통과하지 않고 실패해야 한다. JSON 수치는 NaN이 될 수 없으므로 NaN 검사는 두지 않는다.
	 * </p>
	 */
	private void assertCompletePlan(String label, PlanObservation observation) {
		assertThat(observation.targetNodes()).as("%s 대상 relation 노드", label)
				.isNotEmpty()
				.extracting(PlanNodeObservation::relation)
				.contains("active_user_presence", "user_account");
		assertThat(observation.targetNodes()).as("%s 대상 relation 노드 측정값", label)
				.allSatisfy(node -> {
					assertThat(node.nodeType()).as("%s node type", node.relation()).isNotBlank();
					assertThat(node.planRows()).as("%s plan rows", node.relation())
							.isGreaterThanOrEqualTo(1.0);
					assertThat(node.actualRows()).as("%s actual rows", node.relation())
							.isGreaterThanOrEqualTo(1.0);
					assertThat(node.actualLoops()).as("%s actual loops", node.relation())
							.isGreaterThanOrEqualTo(1.0);
					assertThat(node.sharedBlocksHit() + node.sharedBlocksRead())
							.as("%s block totals", node.relation()).isGreaterThan(0.0);
				});
		assertThat(observation.planningTimeMs()).as("%s planning time", label).isGreaterThan(0.0);
		assertThat(observation.executionTimeMs()).as("%s execution time", label).isGreaterThan(0.0);
	}

	private void printPlanEvidence(String experimentId, String label, PlanObservation observation) {
		String accessPaths = observation.targetNodes().stream()
				.sorted(Comparator.comparing(PlanNodeObservation::relation)
						.thenComparing(PlanNodeObservation::nodeType))
				.map(DirectionMatchingE3PerformanceIntegrationTest::renderNode)
				.collect(Collectors.joining("; "));
		System.out.println(String.format(Locale.ROOT,
				"experiment=%s condition=%s partial_gist=%s planning_ms=%.3f execution_ms=%.3f "
						+ "access_paths=[%s] sorts=[%s]",
				experimentId, label, observation.usesPartialGist() ? "USED" : "NOT_USED",
				observation.planningTimeMs(), observation.executionTimeMs(), accessPaths,
				sortSignature(observation)));
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

	// -------------------------------------------------------------------------
	// PERF-006·PERF-007: 통계 target 전후 논리 쿼리 스냅샷
	// -------------------------------------------------------------------------

	private LogicalQuerySnapshot captureQuerySnapshot(int statisticsTarget, String radiusLabel,
			long maxDistanceMeters, Map<String, QueryEvidence> evidence) {
		long excludedUserId = excludedUserId();
		long schemeId = octantSchemeId();
		long minDistanceMeters = policy.minDistanceMeters();
		long expectedCandidateTotal = expectedCandidateTotal(radiusLabel);

		PreviewObservation preview = observePreview(STATISTICS_COMPARISON_SCENARIO,
				statisticsCondition(statisticsTarget, PREVIEW, radiusLabel), radiusLabel,
				previewParameters(excludedUserId, schemeId, minDistanceMeters, maxDistanceMeters),
				expectedCandidateTotal);
		evidence.put(evidenceKey(statisticsTarget, PREVIEW, radiusLabel), preview.evidence());

		MatchingObservation matching = observeMatching(STATISTICS_COMPARISON_SCENARIO,
				statisticsCondition(statisticsTarget, MATCHING, radiusLabel), radiusLabel,
				matchingParameters(excludedUserId, minDistanceMeters, maxDistanceMeters), expectedCandidateTotal,
				FIXED_SCALE.presenceCount(), GUARDRAIL_LABELS);
		evidence.put(evidenceKey(statisticsTarget, MATCHING, radiusLabel), matching.evidence());

		return new LogicalQuerySnapshot(preview.counts(), matching.labels(), Set.copyOf(matching.labels()));
	}

	/**
	 * preview는 segment 집계라 라벨을 볼 수 없으므로 차단·비활성·만료 행의 제외는 정확한 후보 합계로 확인하고, matching은
	 * 논리 라벨로 직접 확인한다. 5km 진단 반경에서만 {@code guardrail-outside}가 빠지는 것이 거리 필터 관측값이다.
	 */
	private void assertGuardrailQueryPolicy(int statisticsTarget, LogicalQuerySnapshot policySnapshot,
			LogicalQuerySnapshot probeSnapshot) {
		String label = "TARGET_" + statisticsTarget;
		assertThat(policySnapshot.matchingSet()).as("%s 정책 반경 후보 라벨", label)
				.doesNotContainAnyElementsOf(POLICY_EXCLUDED_LABELS)
				.contains(ELIGIBLE_NEAR, ELIGIBLE_OLD, ELIGIBLE_RECENT, FULL_SLOT, OUTSIDE_PROBE_RADIUS);
		assertThat(probeSnapshot.matchingSet()).as("%s 5km 진단 반경 후보 라벨", label)
				.doesNotContainAnyElementsOf(POLICY_EXCLUDED_LABELS)
				.contains(ELIGIBLE_NEAR, ELIGIBLE_OLD, ELIGIBLE_RECENT, FULL_SLOT)
				.doesNotContain(OUTSIDE_PROBE_RADIUS);
		assertThat(policySnapshot.matchingOrder()).as("%s 정책 반경 공정성 상대 순서", label)
				.containsSubsequence(ELIGIBLE_FAIRNESS_ORDER.toArray(String[]::new));
		assertThat(probeSnapshot.matchingOrder()).as("%s 5km 진단 반경 공정성 상대 순서", label)
				.containsSubsequence(ELIGIBLE_FAIRNESS_ORDER.toArray(String[]::new));
	}

	/**
	 * 추정치, 접근 경로, 지연, 호출 수, 행 수, block, sort와 spill을 두 통계 target 사이의 관측값으로 나란히 남긴다.
	 * 변화 여부는 기록 대상이고 단언 대상이 아니다 — 변화가 없어도 실패가 아니다.
	 */
	private void printComparisonEvidence(Map<String, QueryEvidence> evidence) {
		for (String queryKind : List.of(PREVIEW, MATCHING)) {
			for (String radiusLabel : List.of(POLICY_BASELINE, SELECTIVITY_PROBE)) {
				QueryEvidence baseline = evidence.get(evidenceKey(BASELINE_STATISTICS_TARGET, queryKind, radiusLabel));
				QueryEvidence experiment = evidence
						.get(evidenceKey(EXPERIMENT_STATISTICS_TARGET, queryKind, radiusLabel));
				assertThat(baseline).as("%s|%s baseline 증거", queryKind, radiusLabel).isNotNull();
				assertThat(experiment).as("%s|%s experiment 증거", queryKind, radiusLabel).isNotNull();
				System.out.println(renderComparison(queryKind, radiusLabel, baseline, experiment));
			}
		}
	}

	private static String renderComparison(String queryKind, String radiusLabel, QueryEvidence baseline,
			QueryEvidence experiment) {
		String baselinePath = accessPathSignature(baseline.plan());
		String experimentPath = accessPathSignature(experiment.plan());
		return String.format(Locale.ROOT,
				"experiment=%s comparison=%s|%s|%s baseline_target=%d experiment_target=%d access_path_changed=%b "
						+ "access_path=[%s]->[%s] partial_gist=%s->%s row_estimates=[%s]->[%s] calls=%d->%d "
						+ "rows=%d->%d mean_exec_ms=%.3f->%.3f client_p50_ms=%.3f->%.3f client_p95_ms=%.3f->%.3f "
						+ "client_p99_ms=%.3f->%.3f shared_hit=%d->%d shared_read=%d->%d temp_read=%d->%d "
						+ "temp_written=%d->%d sorts=[%s]->[%s] spilled=%b->%b",
				STATISTICS_COMPARISON_SCENARIO, FIXED_SCALE.label(), queryKind, radiusLabel,
				BASELINE_STATISTICS_TARGET, EXPERIMENT_STATISTICS_TARGET, !baselinePath.equals(experimentPath),
				baselinePath, experimentPath, baseline.plan().usesPartialGist() ? "USED" : "NOT_USED",
				experiment.plan().usesPartialGist() ? "USED" : "NOT_USED", rowEstimateSignature(baseline.plan()),
				rowEstimateSignature(experiment.plan()), baseline.pgStat().calls(), experiment.pgStat().calls(),
				baseline.pgStat().rows(), experiment.pgStat().rows(), baseline.pgStat().meanExecTimeMs(),
				experiment.pgStat().meanExecTimeMs(), baseline.latency().p50Ms(), experiment.latency().p50Ms(),
				baseline.latency().p95Ms(), experiment.latency().p95Ms(), baseline.latency().p99Ms(),
				experiment.latency().p99Ms(), baseline.pgStat().sharedBlocksHit(),
				experiment.pgStat().sharedBlocksHit(), baseline.pgStat().sharedBlocksRead(),
				experiment.pgStat().sharedBlocksRead(), baseline.pgStat().tempBlocksRead(),
				experiment.pgStat().tempBlocksRead(), baseline.pgStat().tempBlocksWritten(),
				experiment.pgStat().tempBlocksWritten(), sortSignature(baseline.plan()),
				sortSignature(experiment.plan()), spilled(baseline.plan()), spilled(experiment.plan()));
	}

	private static String accessPathSignature(PlanObservation observation) {
		return observation.targetNodes().stream()
				.sorted(Comparator.comparing(PlanNodeObservation::relation)
						.thenComparing(PlanNodeObservation::nodeType))
				.map(node -> String.format(Locale.ROOT, "%s:%s(index=%s)", node.relation(), node.nodeType(),
						node.indexName()))
				.collect(Collectors.joining("; "));
	}

	private static String rowEstimateSignature(PlanObservation observation) {
		return observation.targetNodes().stream()
				.sorted(Comparator.comparing(PlanNodeObservation::relation)
						.thenComparing(PlanNodeObservation::nodeType))
				.map(node -> String.format(Locale.ROOT, "%s:plan_rows=%.0f actual_rows=%.0f loops=%.0f",
						node.relation(), node.planRows(), node.actualRows(), node.actualLoops()))
				.collect(Collectors.joining("; "));
	}

	private static String sortSignature(PlanObservation observation) {
		return observation.sorts().stream()
				.map(sort -> String.format(Locale.ROOT, "%s/%s space_kb=%.0f rows=%.0f", sort.method(),
						sort.spaceType(), sort.spaceUsedKb(), sort.actualRows()))
				.collect(Collectors.joining("; "));
	}

	private static boolean spilled(PlanObservation observation) {
		return observation.sorts().stream().anyMatch(sort -> "Disk".equals(sort.spaceType()));
	}

	private static String evidenceKey(int statisticsTarget, String queryKind, String radiusLabel) {
		return queryKind + "|" + radiusLabel + "|" + statisticsTarget;
	}

	private static String statisticsCondition(int statisticsTarget, String queryKind, String radiusLabel) {
		return FIXED_SCALE.label() + "|" + queryKind + "|" + radiusLabel + "|TARGET_" + statisticsTarget;
	}

	private long expectedCandidateTotal(String radiusLabel) {
		return POLICY_BASELINE.equals(radiusLabel)
				? FIXED_SCALE.presenceCount() + GUARDRAIL_POLICY_CANDIDATES
				: FIXED_SCALE.expectedProbePresenceCount() + GUARDRAIL_PROBE_CANDIDATES;
	}

	// -------------------------------------------------------------------------
	// PERF-009: 정책 adversary guardrail 시딩과 전제 검증
	// -------------------------------------------------------------------------

	/**
	 * presence를 가진 합성 계정에 균일한 기준 수신 이력을 부여한다. 후보 정렬은 거리보다 공정성(최근 수신 횟수, 마지막 수신 시각)이
	 * 우선이므로, 이 기준선이 없으면 수신 이력이 있는
	 * {@code guardrail-eligible-old}·{@code guardrail-eligible-recent}가 이력이 전혀 없는 북
	 * sector 합성 후보 약 1,250개 뒤로 밀려 30명 스캔 창에서 관측되지 않는다. 이 기준선은 두 통계 조건에 동일하게 적용되며 통계
	 * target 외의 변수를 만들지 않는다.
	 */
	private void seedGuardrailRows() {
		for (GuardrailRow row : GUARDRAIL_ROWS) {
			jdbc.update(SEED_GUARDRAIL_ACCOUNT_SQL, row.accountStatus(), REGION, row.nickname());
			jdbc.update(SEED_GUARDRAIL_PRESENCE_SQL, ORIGIN_LONGITUDE, ORIGIN_LATITUDE, row.distanceMeters(),
					GUARDRAIL_BEARING_DEGREES, REGION, Timestamp.from(NOW.minusSeconds(10)),
					Timestamp.from(row.expiresAtNow() ? NOW : NOW.plusSeconds(3600)), REGION, row.nickname());
		}
		jdbc.update(SEED_GUARDRAIL_BLOCK_SQL, Timestamp.from(NOW.minusSeconds(60)), REGION, EXCLUDED_NICKNAME,
				BLOCKED_BY_SENDER);
		jdbc.update(SEED_GUARDRAIL_BLOCK_SQL, Timestamp.from(NOW.minusSeconds(60)), REGION, BLOCKED_SENDER,
				EXCLUDED_NICKNAME);
		applyReceiveStateBaseline(SEED_RECEIVE_STATE_SQL, new MapSqlParameterSource("region", REGION));
		// 제외 사용자는 후보 SQL에서 식별자로 빠지지만 방향 글을 제출하려면 현재 presence가 필요하다.
		presenceService.update(excludedUserId(), new DirectionPresenceService.UpdateCommand(
				BigDecimal.valueOf(ORIGIN_LATITUDE), BigDecimal.valueOf(ORIGIN_LONGITUDE), BigDecimal.ONE, true,
				NOW));
	}

	private void applyReceiveStateBaseline(String sql, MapSqlParameterSource scope) {
		for (ReceiveStateBaseline baseline : receiveStateBaselines()) {
			namedJdbc.update(sql, new MapSqlParameterSource(scope.getValues())
					.addValue("activeCount", baseline.activeUnhandledCount())
					.addValue("recentCount", baseline.recentReceivedCount())
					.addValue("windowStartedAt", Timestamp.from(NOW.minusSeconds(RECEIVE_WINDOW_SECONDS)))
					.addValue("lastReceivedAt", baseline.lastReceivedSecondsAgo() == null
							? null
							: Timestamp.from(NOW.minusSeconds(baseline.lastReceivedSecondsAgo())))
					.addValue("updatedAt", Timestamp.from(NOW))
					.addValue("nicknamePattern", baseline.nicknamePattern()));
		}
	}

	private List<ReceiveStateBaseline> receiveStateBaselines() {
		return List.of(
				new ReceiveStateBaseline(ACCOUNT_NICKNAME_PATTERN, 0, SYNTHETIC_RECENT_RECEIVED_COUNT,
						SYNTHETIC_LAST_RECEIVED_SECONDS_AGO),
				new ReceiveStateBaseline(ELIGIBLE_OLD, 0, 0, ELIGIBLE_OLD_LAST_RECEIVED_SECONDS_AGO),
				new ReceiveStateBaseline(ELIGIBLE_RECENT, 0, 0, ELIGIBLE_RECENT_LAST_RECEIVED_SECONDS_AGO),
				new ReceiveStateBaseline(FULL_SLOT, receiveProperties.receiveCapacity(), 0, null),
				// 5km 밖 행은 거리 필터만 관측한다. 합성 기준선보다 낮은 공정성 순위를 주어 30명 스캔 창에 들어오지 않게 한다.
				new ReceiveStateBaseline(OUTSIDE_PROBE_RADIUS, 0, SYNTHETIC_RECENT_RECEIVED_COUNT + 1,
						SYNTHETIC_LAST_RECEIVED_SECONDS_AGO));
	}

	private void assertGuardrailShape() {
		long excludedUserId = excludedUserId();
		assertThat(guardrailAccountCount()).as("guardrail 계정 수").isEqualTo(GUARDRAIL_ROWS.size());
		assertThat(senderPresenceCount()).as("제외 사용자 presence 수").isEqualTo(1);
		assertThat(guardrailCandidateCount(policy.maxDistanceMeters(), excludedUserId))
				.as("정책 반경 guardrail 후보 수").isEqualTo(GUARDRAIL_POLICY_CANDIDATES);
		assertThat(guardrailCandidateCount(SELECTIVITY_PROBE_MAX_DISTANCE_METERS, excludedUserId))
				.as("5km 진단 반경 guardrail 후보 수").isEqualTo(GUARDRAIL_PROBE_CANDIDATES);
		assertThat(receiveStateRowCount()).as("기준 receive state 행 수")
				.isEqualTo(FIXED_SCALE.presenceCount() + GUARDRAIL_RECEIVE_STATE_ROWS);
	}

	private long guardrailAccountCount() {
		return count("""
				SELECT COUNT(*) FROM user_account
				WHERE coarse_region_code = ? AND nickname LIKE ?
				""", REGION, GUARDRAIL_NICKNAME_PATTERN);
	}

	private long senderPresenceCount() {
		return count("""
				SELECT COUNT(*)
				FROM active_user_presence p
				JOIN user_account ua ON ua.id = p.user_id
				WHERE ua.coarse_region_code = ? AND ua.nickname = ? AND p.position IS NOT NULL
				""", REGION, EXCLUDED_NICKNAME);
	}

	/**
	 * production 후보 술어와 같은 조건으로 guardrail 행만 직접 센다. preview 합계와 matching 상한의 조정치를
	 * 검증한다.
	 */
	private long guardrailCandidateCount(long maxDistanceMeters, long excludedUserId) {
		return count("""
				SELECT COUNT(*)
				FROM active_user_presence p
				JOIN user_account ua ON ua.id = p.user_id
				WHERE ua.coarse_region_code = ? AND ua.nickname LIKE ?
				  AND p.user_id <> ?
				  AND ua.status = 'ACTIVE'
				  AND p.position IS NOT NULL
				  AND p.receive_allowed = TRUE
				  AND p.location_at <= ? AND p.expires_at > ?
				  AND ST_DWithin(p.position, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
				  AND NOT EXISTS (SELECT 1 FROM user_block ub
				                  WHERE ub.blocker_id = ? AND ub.blocked_id = p.user_id
				                    AND ub.released_at IS NULL)
				  AND NOT EXISTS (SELECT 1 FROM user_block ub
				                  WHERE ub.blocker_id = p.user_id AND ub.blocked_id = ?
				                    AND ub.released_at IS NULL)
				""", REGION, GUARDRAIL_NICKNAME_PATTERN, excludedUserId, Timestamp.from(NOW), Timestamp.from(NOW),
				ORIGIN_LONGITUDE, ORIGIN_LATITUDE, maxDistanceMeters, excludedUserId, excludedUserId);
	}

	private long receiveStateRowCount() {
		return count("""
				SELECT COUNT(*)
				FROM recipient_receive_state r
				JOIN user_account ua ON ua.id = r.user_id
				WHERE ua.coarse_region_code = ?
				""", REGION);
	}

	// -------------------------------------------------------------------------
	// PERF-008: 영속화된 수신자 before/after
	// -------------------------------------------------------------------------

	private RecipientRun runMatchingBatch(int statisticsTarget, long senderId, long questionId, long schemeId) {
		assertNoOtherDueMatchingEvents();
		String condition = FIXED_SCALE.label() + "|" + RECIPIENT + "|TARGET_" + statisticsTarget;
		var submitted = postApplicationService.submit(senderId, "e3-guardrail-" + statisticsTarget,
				new DirectionPostApplicationService.SubmitCommand(questionId, schemeId, NORTH_SEGMENT_KEY,
						"GH-214 E3 정합성 가드레일 본문", List.of()));
		long postId = submitted.post().getId();
		// #127 성능 테스트와 같은 모더레이션 통과 seam이다. 모더레이션 파이프라인 자체는 이 실험의 대상이 아니므로 합성 글 한 건의
		// 상태만 옮긴다.
		jdbc.update("UPDATE direction_post SET moderation_status = 'PASSED' WHERE id = ?", postId);

		DirectionMatchingWorker.BatchResult result = matchingWorker.processBatch(
				new DirectionMatchingWorker.BatchCommand(MATCHING_BATCH_LIMIT,
						"e3-guardrail-worker-" + statisticsTarget, NOW, NOW.plus(MATCHING_LEASE),
						new OutboxRetryPolicy(OUTBOX_MAX_ATTEMPTS, attempt -> Duration.ofSeconds(1))));

		LogicalRecipientSnapshot snapshot = snapshotRecipients(persistedRecipientLabels(postId));
		System.out.println(String.format(Locale.ROOT,
				"experiment=%s condition=%s claimed=%d outcomes=%d processed=%d recipients=%d duplicate=%d "
						+ "missing=%d blocked=%d full_slot=%d",
				RECIPIENT_GUARDRAIL_SCENARIO, condition, result.claimed(), result.outcomes().size(),
				result.outcomes().stream().filter(DirectionMatchingWorker.Outcome.PROCESSED::equals).count(),
				snapshot.orderedRecipients().size(), snapshot.duplicateCount(), snapshot.missingExpectedCount(),
				snapshot.blockedRecipientCount(), snapshot.fullSlotRecipientCount()));
		return new RecipientRun(condition, postId, persistedRecipientUserIds(postId), result.outcomes(), snapshot);
	}

	/**
	 * 다른 테스트가 남긴 due 매칭 이벤트가 있으면 이 배치가 그것까지 점유해 결과 해석이 무효가 된다. 정리하지 않고 전제 위반으로
	 * 실패시킨다.
	 */
	private void assertNoOtherDueMatchingEvents() {
		assertThat(count("""
				SELECT COUNT(*) FROM outbox_event
				WHERE event_type = 'RECIPIENT_MATCH_REQUESTED'
				  AND ((status IN ('PENDING', 'FAILED') AND next_attempt_at <= ?)
				    OR (status = 'PROCESSING' AND lease_expires_at <= ?))
				""", Timestamp.from(NOW), Timestamp.from(NOW))).as("배치 실행 전 다른 due 매칭 이벤트").isZero();
	}

	private List<String> persistedRecipientLabels(long postId) {
		return List.copyOf(namedJdbc.query("""
				SELECT ua.nickname AS nickname
				FROM post_recipient pr
				JOIN user_account ua ON ua.id = pr.recipient_id
				WHERE pr.post_id = :postId
				ORDER BY pr.id
				""", new MapSqlParameterSource("postId", postId),
				(resultSet, rowNumber) -> logicalSuffix(resultSet.getString("nickname"))));
	}

	/** 되돌릴 receive state 행을 고르는 데만 쓰고 증거 출력에는 넣지 않는다. */
	private List<Long> persistedRecipientUserIds(long postId) {
		return List.copyOf(namedJdbc.query(
				"SELECT recipient_id FROM post_recipient WHERE post_id = :postId ORDER BY id",
				new MapSqlParameterSource("postId", postId),
				(resultSet, rowNumber) -> resultSet.getLong("recipient_id")));
	}

	private LogicalRecipientSnapshot snapshotRecipients(List<String> orderedRecipients) {
		Set<String> recipientSet = Set.copyOf(orderedRecipients);
		return new LogicalRecipientSnapshot(orderedRecipients, recipientSet,
				orderedRecipients.size() - recipientSet.size(),
				ELIGIBLE_FAIRNESS_ORDER.stream().filter(label -> !recipientSet.contains(label)).count(),
				POLICY_EXCLUDED_LABELS.stream().filter(recipientSet::contains).count(),
				recipientSet.contains(FULL_SLOT) ? 1 : 0);
	}

	private void assertRecipientGuardrail(RecipientRun run) {
		LogicalRecipientSnapshot snapshot = run.snapshot();
		assertThat(snapshot.duplicateCount()).as("%s 중복 수신자", run.condition()).isZero();
		assertThat(snapshot.missingExpectedCount()).as("%s 누락 수신자", run.condition()).isZero();
		assertThat(snapshot.blockedRecipientCount()).as("%s 차단·비활성·만료 수신자", run.condition()).isZero();
		assertThat(snapshot.fullSlotRecipientCount()).as("%s 수신 한도 초과 수신자", run.condition()).isZero();
		assertThat(snapshot.orderedRecipients()).as("%s 수신자 공정성 상대 순서", run.condition())
				.hasSize(selectionProperties.maxRecipientsPerPost())
				.containsSubsequence(ELIGIBLE_FAIRNESS_ORDER.toArray(String[]::new));
	}

	private void resetRun(RecipientRun run) {
		MapSqlParameterSource post = new MapSqlParameterSource("postId", run.postId());
		namedJdbc.update("""
				DELETE FROM outbox_event
				WHERE aggregate_type = 'POST_RECIPIENT'
				  AND aggregate_id IN (SELECT id FROM post_recipient WHERE post_id = :postId)
				""", post);
		namedJdbc.update("DELETE FROM post_recipient WHERE post_id = :postId", post);
		namedJdbc.update("""
				DELETE FROM outbox_event
				WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id = :postId
				""", post);
		namedJdbc.update("DELETE FROM post_audience WHERE post_id = :postId", post);
		namedJdbc.update("DELETE FROM direction_post WHERE id = :postId", post);
		restoreReceiveStateBaseline(run.recipientUserIds());
	}

	/**
	 * 이 run이 실제로 건드린 receive state 행만 되돌린다. {@code ensureForUsers}가 새로 만든 행(기준선이 없는
	 * {@code guardrail-eligible-near})은 삭제로, {@code reserve}가 증가시킨 행은 기준선 재삽입으로
	 * 복원한다.
	 */
	private void restoreReceiveStateBaseline(List<Long> userIds) {
		assertThat(userIds).as("되돌릴 receive state 대상").isNotEmpty();
		MapSqlParameterSource scope = new MapSqlParameterSource("region", REGION).addValue("userIds", userIds);
		namedJdbc.update("DELETE FROM recipient_receive_state WHERE user_id IN (:userIds)", scope);
		applyReceiveStateBaseline(RESTORE_RECEIVE_STATE_SQL, scope);
	}

	private long seedActiveQuestion(long approverId) {
		Long questionId = jdbc.queryForObject("""
				INSERT INTO approved_question
					(source_type, status, question_text, answer_format, active_from, active_until,
					 approved_at, approved_by)
				VALUES ('OPERATOR', 'ACTIVE', 'GH-214 E3 guardrail question', 'TEXT', ?, ?, ?, ?)
				RETURNING id
				""", Long.class, Timestamp.from(NOW.minusSeconds(120)), Timestamp.from(NOW.plusSeconds(7200)),
				Timestamp.from(NOW.minusSeconds(120)), approverId);
		assertThat(questionId).as("활성 승인 질문").isNotNull();
		return questionId;
	}

	private record FixtureScale(
			String label,
			int accountCount,
			int presenceCount,
			int expectedProbePresenceCount) {
	}

	private record GuardrailRow(
			String nickname,
			String accountStatus,
			double distanceMeters,
			boolean expiresAtNow) {
	}

	private record ReceiveStateBaseline(
			String nicknamePattern,
			int activeUnhandledCount,
			int recentReceivedCount,
			Long lastReceivedSecondsAgo) {
	}

	private record QueryEvidence(
			PgStatObservation pgStat,
			LatencyObservation latency,
			PlanObservation plan) {
	}

	private record PreviewObservation(Map<String, Long> counts, QueryEvidence evidence) {
	}

	private record MatchingObservation(List<String> labels, QueryEvidence evidence) {
	}

	private record LogicalQuerySnapshot(
			Map<String, Long> previewCounts,
			List<String> matchingOrder,
			Set<String> matchingSet) {
	}

	private record LogicalRecipientSnapshot(
			List<String> orderedRecipients,
			Set<String> recipientSet,
			long duplicateCount,
			long missingExpectedCount,
			long blockedRecipientCount,
			long fullSlotRecipientCount) {
	}

	private record RecipientRun(
			String condition,
			long postId,
			List<Long> recipientUserIds,
			List<DirectionMatchingWorker.Outcome> outcomes,
			LogicalRecipientSnapshot snapshot) {
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
