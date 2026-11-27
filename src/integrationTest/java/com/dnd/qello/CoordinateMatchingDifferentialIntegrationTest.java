/**
 * Created at: 2026-11-24T17:43:18Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-INT-011, INT-012, INT-014 through INT-016
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.DoubleAccumulator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionCandidate;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.geo.CoordinateGeometry;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.security.Coordinate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoordinateMatchingDifferentialIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant AT = Instant.parse("2026-11-24T00:00:00Z");
	private static final Coordinate ORIGIN = coordinate(37.5, 127);
	private static final String REGION = "TEST-COORDINATE-388";
	private static final String OTHER_REGION = "TEST-COORDINATE-388-OTHER";
	private static final DoubleAccumulator MAX_DISTANCE_ERROR = new DoubleAccumulator(Math::max, 0);
	private static final DoubleAccumulator MAX_ANGLE_ERROR = new DoubleAccumulator(Math::max, 0);
	private static final DoubleAccumulator MAX_SUBMETER_ANGLE_ERROR = new DoubleAccumulator(Math::max, 0);
	private final Map<Long, Coordinate> points = new HashMap<>();
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ActiveUserPresenceRepository presence;
	@Autowired
	private DirectionSchemeRepository schemes;
	private CoordinatePostgisOracle oracle;
	private long sender;
	private long scheme;
	private long accountSequence;

	@BeforeEach
	void setUp() {
		oracle = new CoordinatePostgisOracle(jdbc);
		oracle.reset();
		jdbc.update("""
				INSERT INTO region_code (code,parent_code,display_name,level)
				VALUES (?, 'KR', 'Coordinate differential', 'REGION'),
				       (?, 'KR', 'Coordinate differential other', 'REGION')
				""", REGION, OTHER_REGION);
		sender = account("ACTIVE");
		seed(sender, ORIGIN, true, AT.minusSeconds(1), AT.plusSeconds(3600), REGION);
		scheme = schemes.save(DirectionScheme.createEqual("COORDINATE-388", 1, 8, BigDecimal.ZERO)).getId();
		for (int i = 7; i >= 0; i--) {
			schemes.saveSegment(DirectionSegment.create(scheme, "S" + i, "segment-" + i,
					BigDecimal.valueOf(i * 45L + 22.5), BigDecimal.valueOf(45), i));
		}
	}

	@Test
	@DisplayName("INT-011 비공간 조건·시각 경계·양방향 차단·셀 전용·GLOBAL 범위가 원본 SQL과 같다")
	void preservesNonspatialEligibility() {
		long atBoundary = seed(account("ACTIVE"), coordinate(37.51, 127), true, AT, AT.plusSeconds(1), REGION);
		seed(account("ACTIVE"), coordinate(37.52, 127), true, AT.minusSeconds(1), AT, REGION);
		seed(account("ACTIVE"), coordinate(37.53, 127), true, AT.plusSeconds(1), AT.plusSeconds(2), REGION);
		seed(account("ACTIVE"), coordinate(37.54, 127), false, AT.minusSeconds(1), AT.plusSeconds(1), REGION);
		seed(account("BLOCKED"), coordinate(37.55, 127), true, AT.minusSeconds(1), AT.plusSeconds(1), REGION);
		seed(account("ACTIVE"), null, true, AT.minusSeconds(1), AT.plusSeconds(1), REGION);
		long outbound = add(coordinate(37.56, 127));
		long inbound = add(coordinate(37.57, 127));
		long released = add(coordinate(37.58, 127));
		block(sender, outbound, null);
		block(inbound, sender, null);
		block(sender, released, AT.minusSeconds(1));
		long other = seed(account("ACTIVE"), coordinate(37.59, 127), true,
				AT.minusSeconds(1), AT.plusSeconds(1), OTHER_REGION);
		assertThat(presence.findCandidates(sender, 37.5, 127, 0, 20_100_000, 0, 360, AT, null))
				.extracting(DirectionCandidate::userId).containsExactly(atBoundary, released, other);
		compare(ORIGIN, 0, 20_100_000, 0, 360, null);
		compare(ORIGIN, 0, 20_100_000, 0, 360, REGION);
	}

	@Test
	@DisplayName("INT-012 30명 초과 후보의 이력·NULL·거리·ID 공정성 순서와 수치가 원본 SQL과 같다")
	void preservesFullCandidateOrderWithoutLimit() {
		for (int i = 0; i < 48; i++) {
			long id = add(coordinate(37.5 + (i % 12 + 1) * 0.001, 127.01));
			jdbc.update("""
					INSERT INTO recipient_receive_state
						(user_id,recent_received_count,recent_window_started_at,last_received_at)
					VALUES (?,?,?,?)
					""", id, i % 3, Timestamp.from(AT.minusSeconds(3600)),
					i % 4 == 0 ? null : Timestamp.from(AT.minusSeconds(i % 5)));
		}
		assertThat(presence.findCandidates(sender, 37.5, 127, 0, 20_100_000, 0, 360, AT, null)).hasSize(48);
		compare(ORIGIN, 0, 20_100_000, 0, 360, null);
	}

	@ParameterizedTest
	@ValueSource(longs = {0, 10_000, 100_000, 20_000_000})
	@DisplayName("INT-014 거리 경계 ±1m의 후보·집계가 원본 SQL과 같다")
	void preservesOriginalDistancePredicates(long boundary) {
		for (double delta : new double[]{-1, 1}) {
			if (boundary + delta >= 0) {
				add(oracle.project(ORIGIN, boundary + delta, 91.12345));
			}
		}
		compare(ORIGIN, 0, boundary, 0, 360, null);
		compare(ORIGIN, boundary, 20_100_001, 0, 360, null);
	}

	@ParameterizedTest
	@ValueSource(doubles = {0, 45, 90, 135, 180, 225, 270, 315})
	@DisplayName("INT-015 섹터 경계 ±0.0001도의 후보·집계가 원본 SQL과 같다")
	void preservesSectorBoundaries(double boundary) {
		for (double delta : new double[]{-0.0001, 0.0001}) {
			add(oracle.project(ORIGIN, 10_000, boundary + delta));
		}
		double end = (boundary + 45) % 360;
		compare(ORIGIN, 0, 20_100_000, boundary, end, null);
		compare(ORIGIN, 0, 20_100_000, (boundary + 315) % 360, boundary, null);
	}

	@ParameterizedTest
	@ValueSource(doubles = {0, 45, 90, 135, 180, 225, 270, 315})
	@DisplayName("INT-015 경계선 점은 수치 오차와 앱 자체 반열린 구간 규칙을 검증한다")
	void checksExactSectorBoundaryAgainstAppMeasurement(double boundary) {
		long id = add(oracle.project(ORIGIN, 10_000, boundary));
		oracle.snapshot();
		assertMeasurements(ORIGIN);
		var value = CoordinateGeometry.inverse(ORIGIN, points.get(id)).orElseThrow();
		double end = (boundary + 45) % 360;
		double bearing = value.bearingDegrees();
		boolean included = boundary < end
				? bearing >= boundary && bearing < end
				: bearing >= boundary || bearing < end;
		assertThat(presence.findCandidates(sender, 37.5, 127, 0, 20_100_000, boundary, end, AT, null))
				.extracting(DirectionCandidate::userId)
				.containsExactlyElementsOf(included ? List.of(id) : List.of());
	}

	@ParameterizedTest
	@ValueSource(longs = {0, 10_000, 100_000, 20_000_000})
	@DisplayName("INT-014 거리 경계선 점은 수치 오차와 앱 자체 포함 상한 규칙을 검증한다")
	void checksExactDistanceBoundaryAgainstAppMeasurement(long boundary) {
		long id = add(oracle.project(ORIGIN, boundary, 91.12345));
		oracle.snapshot();
		assertMeasurements(ORIGIN);
		var measurement = CoordinateGeometry.inverse(ORIGIN, points.get(id));
		boolean included = measurement.isPresent() && measurement.orElseThrow().distanceMeters() <= boundary;
		assertThat(presence.findCandidates(sender, 37.5, 127, 0, boundary, 0, 360, AT, null))
				.extracting(DirectionCandidate::userId)
				.containsExactlyElementsOf(included ? List.of(id) : List.of());
	}

	@Test
	@DisplayName("INT-016 1m 미만은 방위 수치 비교에서만 제외하고 후보 포함은 비교한다")
	void comparesSubmeterInclusionWithoutIllConditionedAzimuth() {
		add(oracle.project(ORIGIN, 0.001, 91.12345));
		compare(ORIGIN, 0, 10, 0, 360, null);
	}

	@AfterAll
	static void reportMaximumErrors() {
		System.out.printf("COORDINATE_ERROR_MAX distance_m=%.15g angle_deg=%.15g submeter_angle_deg=%.15g%n",
				MAX_DISTANCE_ERROR.get(), MAX_ANGLE_ERROR.get(), MAX_SUBMETER_ANGLE_ERROR.get());
	}

	@ParameterizedTest
	@CsvSource({"0,0", "90,0", "-90,0", "0,179.999", "37.5,127"})
	@DisplayName("INT-016 동일점·극지·날짜변경선·대척점·근대척점·고정 전세계 corpus가 원본과 같다")
	void preservesGlobalGeodesicCorpus(double latitude, double longitude) {
		Coordinate origin = coordinate(latitude, longitude);
		List<Coordinate> corpus = new ArrayList<>(List.of(origin, coordinate(90, 0), coordinate(-90, 0),
				coordinate(0, -179.999), coordinate(-latitude, longitude > 0 ? longitude - 180 : longitude + 180),
				coordinate(-latitude * 0.999999, longitude > 0 ? longitude - 179.999999 : longitude + 179.999999)));
		Random random = new Random(388);
		for (int i = 0; i < 64; i++) {
			corpus.add(coordinate(random.nextDouble(-89.9, 89.9), random.nextDouble(-180, 180)));
		}
		corpus.forEach(this::add);
		compare(origin, 0, 20_100_000, 0, 360, null);
	}

	private void compare(Coordinate origin, long min, long max, double start, double end, String region) {
		oracle.snapshot();
		assertMeasurements(origin);
		MapSqlParameterSource parameters = parameters(origin, min, max, start, end, region);
		List<DirectionCandidate> expected = oracle.candidates(parameters);
		List<DirectionCandidate> actual = presence.findCandidates(sender, origin.latitude().doubleValue(),
				origin.longitude().doubleValue(), min, max, start, end, AT, region);
		var fairness = oracle.fairness();
		assertThat(normalizeTies(actual, fairness)).extracting(DirectionCandidate::userId)
				.containsExactlyElementsOf(normalizeTies(expected, fairness).stream()
						.map(DirectionCandidate::userId).toList());
		for (int i = 0; i < expected.size(); i++) {
			DirectionCandidate wanted = expected.get(i);
			DirectionCandidate found = actual.stream().filter(value -> value.userId().equals(wanted.userId()))
					.findFirst().orElseThrow();
			assertNumeric(found.distanceMeters(), wanted.distanceMeters(), 0.001, false);
			if (wanted.distanceMeters().doubleValue() >= 1) {
				assertNumeric(found.bearingDegrees(), wanted.bearingDegrees(), 0.0000001, true);
				assertNumeric(found.inboundBearingDegrees(), wanted.inboundBearingDegrees(), 0.0000001, true);
			}
		}
		assertThat(presence.findCandidateCountsBySegment(scheme, sender, origin.latitude().doubleValue(),
				origin.longitude().doubleValue(), min, max, AT, region)).isEqualTo(oracle.counts(parameters));
	}

	private static List<DirectionCandidate> normalizeTies(List<DirectionCandidate> values,
			Map<Long, CoordinatePostgisOracle.Fairness> fairness) {
		List<DirectionCandidate> result = new ArrayList<>();
		int start = 0;
		while (start < values.size()) {
			int end = start + 1;
			DirectionCandidate anchor = values.get(start);
			while (end < values.size()
					&& fairness.get(anchor.userId()).equals(fairness.get(values.get(end).userId()))
					&& anchor.distanceMeters().subtract(values.get(end).distanceMeters()).abs()
							.compareTo(new BigDecimal("0.001")) <= 0) {
				end++;
			}
			result.addAll(values.subList(start, end).stream()
					.sorted(Comparator.comparingLong(DirectionCandidate::userId)).toList());
			start = end;
		}
		return result;
	}

	private void assertMeasurements(Coordinate origin) {
		for (var expected : oracle.measurements(origin)) {
			Coordinate point = points.get(expected.userId());
			var actual = CoordinateGeometry.inverse(origin, point);
			if (expected.bearing() == null) {
				assertThat(actual).isEmpty();
			} else {
				assertThat(actual).isPresent();
				var value = actual.orElseThrow();
				assertNumeric(BigDecimal.valueOf(value.distanceMeters()), expected.distance(), 0.001, false);
				if (expected.distance().doubleValue() >= 1) {
					assertNumeric(BigDecimal.valueOf(value.bearingDegrees()), expected.bearing(), 0.0000001, true);
					assertNumeric(BigDecimal.valueOf(value.inboundBearingDegrees()), expected.inbound(), 0.0000001,
							true);
				} else {
					MAX_SUBMETER_ANGLE_ERROR
							.accumulate(angleError(value.bearingDegrees(), expected.bearing().doubleValue()));
					MAX_SUBMETER_ANGLE_ERROR
							.accumulate(angleError(value.inboundBearingDegrees(), expected.inbound().doubleValue()));
				}
			}
		}
	}

	private static void assertNumeric(BigDecimal actual, BigDecimal expected, double tolerance, boolean angle) {
		double difference = actual.subtract(expected).abs().doubleValue();
		double error = angle ? angleError(actual.doubleValue(), expected.doubleValue()) : difference;
		(angle ? MAX_ANGLE_ERROR : MAX_DISTANCE_ERROR).accumulate(error);
		assertThat(error).as("numeric error only; coordinate values redacted").isFinite()
				.isLessThanOrEqualTo(tolerance);
	}

	private static double angleError(double actual, double expected) {
		double difference = Math.abs(actual - expected);
		return Math.min(difference, 360 - difference);
	}

	private MapSqlParameterSource parameters(Coordinate origin, long min, long max, double start, double end,
			String region) {
		return new MapSqlParameterSource("excludedUserId", sender).addValue("schemeId", scheme)
				.addValue("originLatitude", origin.latitude()).addValue("originLongitude", origin.longitude())
				.addValue("minDistanceMeters", min).addValue("maxDistanceMeters", max)
				.addValue("sectorStartDegrees", start).addValue("sectorEndDegrees", end)
				.addValue("at", Timestamp.from(AT)).addValue("regionCode", region);
	}

	private long add(Coordinate position) {
		return seed(account("ACTIVE"), position, true, AT.minusSeconds(1), AT.plusSeconds(3600), REGION);
	}

	private long seed(long id, Coordinate position, boolean allowed, Instant location, Instant expires, String region) {
		ActiveUserPresence value = ActiveUserPresence.create(id, position == null ? null : position.latitude(),
				position == null ? null : position.longitude(), position == null ? "TEST-CELL" : null,
				region, BigDecimal.ONE, allowed, location, expires);
		presence.save(value);
		oracle.seed(value);
		if (position != null) {
			points.put(id, position);
		}
		return id;
	}

	private long account(String status) {
		return jdbc.queryForObject("""
				INSERT INTO user_account (role,country_code,status,coarse_region_code,locale,timezone,nickname)
				VALUES ('USER','KR',?,?,'ko-KR','Asia/Seoul',?) RETURNING id
				""", Long.class, status, REGION, "coordinate-differential-" + ++accountSequence);
	}

	private void block(long blocker, long blocked, Instant released) {
		jdbc.update("INSERT INTO user_block (blocker_id,blocked_id,created_at,released_at) VALUES (?,?,?,?)",
				blocker, blocked, Timestamp.from(AT.minusSeconds(2)),
				released == null ? null : Timestamp.from(released));
	}

	private static Coordinate coordinate(double latitude, double longitude) {
		return new Coordinate(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
	}
}
