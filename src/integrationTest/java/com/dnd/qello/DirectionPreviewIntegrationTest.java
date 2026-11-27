/**
 * Created at: 2026-08-12T01:17:58+09:00
 * Source scenario: TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-INT-001 through INT-008,
 * TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-013 (added 2026-08-14T00:51:11+09:00)
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-001, INT-003
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-INT-013 through INT-015 (added 2026-11-24T17:43:18.421Z)
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.geo.CoordinateGeometry;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.security.Coordinate;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.direction.service.DirectionPreviewResult;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DirectionPreviewIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRECTION-PREVIEW-117";
	private static final String OTHER_REGION = "TEST-DIRECTION-PREVIEW-117-OTHER";
	private static final Instant AT = Instant.parse("2026-08-12T00:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ActiveUserPresenceRepository presenceRepository;
	@Autowired
	private DirectionSchemeRepository schemeRepository;
	@Autowired
	private DirectionPostService previewService;
	private CoordinatePostgisOracle oracle;

	@BeforeEach
	void reset() {
		oracle = new CoordinatePostgisOracle(jdbc);
		oracle.reset();
		jdbc.update("DELETE FROM user_block");
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM direction_segment");
		jdbc.update("DELETE FROM direction_scheme");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", OTHER_REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", OTHER_REGION);
		jdbc.update(
				"INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update(
				"INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Direction Preview Test Region', 'REGION')",
				REGION);
		jdbc.update(
				"INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Direction Preview Other Region', 'REGION')",
				OTHER_REGION);
	}

	@Test
	@DisplayName("INT-013 암호문 previewAll은 원본 PostGIS의 모든 segment 순서·개수와 빈 방향 0을 보존한다")
	void aggregatesAllActiveSegmentsAndZeroFillsEmptySegments() {
		long senderId = account("preview-sender", "ACTIVE");
		presence(senderId, 37.5, 127.0, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		long schemeId = scheme("PREVIEW-ALL", 1);

		long north = account("preview-north", "ACTIVE");
		long northEast = account("preview-north-east", "ACTIVE");
		long east = account("preview-east", "ACTIVE");
		presenceAtBearing(north, 37.5, 127.0, 100, 0.0001, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		presenceAtBearing(northEast, 37.5, 127.0, 100, 10, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		presenceAtBearing(east, 37.5, 127.0, 100, 90.0001, true, AT.minusSeconds(60), AT.plusSeconds(3600));

		DirectionPreviewResult result = preview(senderId, schemeId, 0, 1_000);

		assertThat(result.segments()).hasSize(8)
				.extracting(DirectionPreviewResult.SegmentCount::segmentKey)
				.containsExactly("S0", "S1", "S2", "S3", "S4", "S5", "S6", "S7");
		assertThat(result.segments()).extracting(DirectionPreviewResult.SegmentCount::count)
				.containsExactly(2L, 0L, 1L, 0L, 0L, 0L, 0L, 0L);
		oracle.snapshot();
		var expected = oracle.counts(new MapSqlParameterSource("schemeId", schemeId)
				.addValue("excludedUserId", senderId).addValue("originLatitude", 37.5)
				.addValue("originLongitude", 127.0)
				.addValue("minDistanceMeters", 0).addValue("maxDistanceMeters", 1_000)
				.addValue("at", Timestamp.from(AT)).addValue("regionCode", null));
		assertThat(result.segments()).extracting(DirectionPreviewResult.SegmentCount::segmentKey)
				.containsExactlyElementsOf(expected.stream().map(value -> value.segmentKey()).toList());
		assertThat(result.segments()).extracting(DirectionPreviewResult.SegmentCount::count)
				.containsExactlyElementsOf(expected.stream().map(value -> value.count()).toList());
	}

	@Test
	@DisplayName("GLOBAL preview는 발신자 표시 지역과 다른 후보도 방향·거리 count에 포함한다")
	void includesCandidateOutsideSenderRegionInGlobalScope() {
		long senderId = account("global-preview-sender", "ACTIVE");
		presence(senderId, 37.5, 127.0, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		long schemeId = scheme("PREVIEW-GLOBAL", 6);
		long candidateId = account("global-preview-candidate", "ACTIVE");
		presenceAtBearing(candidateId, 37.5, 127.0, 100, 0, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		jdbc.update("UPDATE user_account SET coarse_region_code = ? WHERE id = ?", OTHER_REGION, candidateId);
		jdbc.update("UPDATE active_user_presence SET coarse_region_code = ? WHERE user_id = ?", OTHER_REGION,
				candidateId);

		DirectionPreviewResult result = preview(senderId, schemeId, 0, 1_000);

		assertThat(count(result, "S0")).isEqualTo(1);
	}

	@Test
	@DisplayName("섹터 경계 ±0.0001도 후보는 인접 segment에 중복 계상되지 않는다")
	void appliesHalfOpenBoundariesWithoutDuplicateCounts() {
		long senderId = account("boundary-sender", "ACTIVE");
		presence(senderId, 37.5, 127.0, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		long schemeId = scheme("PREVIEW-BOUNDARY", 2);

		presenceAtBearing(account("after-45", "ACTIVE"), 37.5, 127.0, 100, 45.0001, true, AT.minusSeconds(60),
				AT.plusSeconds(3600));
		presenceAtBearing(account("before-45", "ACTIVE"), 37.5, 127.0, 100, 44.9999, true, AT.minusSeconds(60),
				AT.plusSeconds(3600));
		presenceAtBearing(account("after-315", "ACTIVE"), 37.5, 127.0, 100, 315.0001, true, AT.minusSeconds(60),
				AT.plusSeconds(3600));
		presenceAtBearing(account("before-315", "ACTIVE"), 37.5, 127.0, 100, 314.9999, true, AT.minusSeconds(60),
				AT.plusSeconds(3600));

		DirectionPreviewResult result = preview(senderId, schemeId, 0, 1_000);

		assertThat(count(result, "S0")).isEqualTo(1);
		assertThat(count(result, "S1")).isEqualTo(1);
		assertThat(count(result, "S6")).isEqualTo(1);
		assertThat(count(result, "S7")).isEqualTo(1);
		assertThat(result.segments().stream().mapToLong(DirectionPreviewResult.SegmentCount::count).sum()).isEqualTo(4);
	}

	@Test
	@DisplayName("실제 PostGIS geography 방위 계산은 날짜 변경선을 통과하는 후보를 누락하지 않는다")
	void handlesInternationalDateLine() {
		long senderId = account("date-line-sender", "ACTIVE");
		presence(senderId, 0, 179.999, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		long schemeId = scheme("PREVIEW-DATELINE", 3);
		long candidateId = account("date-line-candidate", "ACTIVE");
		presence(candidateId, 0, -179.999, true, AT.minusSeconds(60), AT.plusSeconds(3600));

		DirectionPreviewResult result = preview(senderId, schemeId, 0, 1_000);

		assertThat(result.segments().stream().mapToLong(DirectionPreviewResult.SegmentCount::count).sum()).isEqualTo(1);
		assertThat(count(result, "S2")).isEqualTo(1);
	}

	@Test
	@DisplayName("거리 경계 ±1m 후보의 preview 개수는 PostGIS와 같고 범위 밖 후보를 제외한다")
	void appliesInclusiveDistanceBoundaries() {
		long senderId = account("distance-sender", "ACTIVE");
		presence(senderId, 37.5, 127.0, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		long schemeId = scheme("PREVIEW-DISTANCE", 4);
		presenceAtBearing(account("inside-min", "ACTIVE"), 37.5, 127.0, 1_001, 90.0001, true, AT.minusSeconds(60),
				AT.plusSeconds(3600));
		presenceAtBearing(account("inside-max", "ACTIVE"), 37.5, 127.0, 1_999, 90.0001, true, AT.minusSeconds(60),
				AT.plusSeconds(3600));
		presenceAtBearing(account("outside-min", "ACTIVE"), 37.5, 127.0, 999, 90.0001, true, AT.minusSeconds(60),
				AT.plusSeconds(3600));
		presenceAtBearing(account("outside-max", "ACTIVE"), 37.5, 127.0, 2_001, 90.0001, true, AT.minusSeconds(60),
				AT.plusSeconds(3600));
		presenceAtBearing(account("expired", "ACTIVE"), 37.5, 127.0, 1_500, 90.0001, true, AT.minusSeconds(3600), AT);
		presenceAtBearing(account("receive-disabled", "ACTIVE"), 37.5, 127.0, 1_500, 90.0001, false,
				AT.minusSeconds(60), AT.plusSeconds(3600));
		presenceAtBearing(account("blocked-account", "BLOCKED"), 37.5, 127.0, 1_500, 90.0001, true, AT.minusSeconds(60),
				AT.plusSeconds(3600));

		DirectionPreviewResult result = preview(senderId, schemeId, 1_000, 2_000);

		assertThat(count(result, "S2")).isEqualTo(2);
		assertThat(result.segments().stream().mapToLong(DirectionPreviewResult.SegmentCount::count).sum()).isEqualTo(2);
		oracle.snapshot();
		var expected = oracle.counts(new MapSqlParameterSource("schemeId", schemeId)
				.addValue("excludedUserId", senderId).addValue("originLatitude", 37.5)
				.addValue("originLongitude", 127.0)
				.addValue("minDistanceMeters", 1_000).addValue("maxDistanceMeters", 2_000)
				.addValue("at", Timestamp.from(AT)).addValue("regionCode", null));
		assertThat(result.segments()).extracting(DirectionPreviewResult.SegmentCount::segmentKey)
				.containsExactlyElementsOf(expected.stream().map(value -> value.segmentKey()).toList());
		assertThat(result.segments()).extracting(DirectionPreviewResult.SegmentCount::count)
				.containsExactlyElementsOf(expected.stream().map(value -> value.count()).toList());
	}

	@ParameterizedTest
	@CsvSource({"1000,90.0001", "2000,90.0001", "1500,90"})
	@DisplayName("정확 거리·방위 경계 preview는 수치 허용 오차와 앱 자체 포함·반열린 구간 판정을 지킨다")
	void appliesExactBoundariesToAppMeasurements(double distance, double bearing) {
		long senderId = account("exact-boundary-sender", "ACTIVE");
		presence(senderId, 37.5, 127.0, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		long schemeId = scheme("PREVIEW-EXACT-BOUNDARY", 7);
		long candidateId = account("exact-boundary-candidate", "ACTIVE");
		Coordinate point = presenceAtBearing(candidateId, 37.5, 127.0, distance, bearing,
				true, AT.minusSeconds(60), AT.plusSeconds(3600));
		Coordinate origin = new Coordinate(new BigDecimal("37.5"), new BigDecimal("127.0"));
		var measurement = CoordinateGeometry.inverse(origin, point).orElseThrow();
		var expected = oracle.measurements(origin).stream()
				.filter(value -> value.userId() == candidateId).findFirst().orElseThrow();
		double distanceError = Math.abs(measurement.distanceMeters() - expected.distance().doubleValue());
		double bearingDifference = Math.abs(measurement.bearingDegrees() - expected.bearing().doubleValue());
		double inboundDifference = Math.abs(measurement.inboundBearingDegrees() - expected.inbound().doubleValue());
		double angleError = Math.max(Math.min(bearingDifference, 360 - bearingDifference),
				Math.min(inboundDifference, 360 - inboundDifference));
		assertThat(distanceError).as("distance numeric error").isFinite().isLessThanOrEqualTo(0.001);
		assertThat(angleError).as("circular angle numeric error").isFinite().isLessThanOrEqualTo(0.0000001);
		System.out.printf("COORDINATE_PREVIEW_BOUNDARY_ERROR distance_m=%.15g angle_deg=%.15g%n",
				distanceError, angleError);

		DirectionPreviewResult result = preview(senderId, schemeId, 1_000, 2_000);

		boolean inRange = measurement.distanceMeters() >= 1_000 && measurement.distanceMeters() <= 2_000;
		assertThat(result.segments()).extracting(DirectionPreviewResult.SegmentCount::segmentKey)
				.containsExactly("S0", "S1", "S2", "S3", "S4", "S5", "S6", "S7");
		assertThat(result.segments()).extracting(DirectionPreviewResult.SegmentCount::count)
				.containsExactlyElementsOf(
						IntStream.range(0, 8).mapToObj(index -> inRange && measurement.bearingDegrees() >= index * 45
								&& measurement.bearingDegrees() < (index + 1) * 45 ? 1L : 0L).toList());
	}

	@Test
	@DisplayName("수신 거부 중인 발신자도 유효한 위치로 preview할 수 있지만 후보에는 포함되지 않는다")
	void receiveDeniedSenderCanPreviewButIsNotCandidate() {
		long senderId = account("receive-denied-sender", "ACTIVE");
		presence(senderId, 37.5, 127.0, false, AT.minusSeconds(60), AT.plusSeconds(3600));
		long schemeId = scheme("PREVIEW-SENDER-DENIED", 5);
		long candidateId = account("receive-allowed-candidate", "ACTIVE");
		presenceAtBearing(candidateId, 37.5, 127.0, 100, 0, true,
				AT.minusSeconds(60), AT.plusSeconds(3600));

		DirectionPreviewResult senderPreview = preview(senderId, schemeId, 0, 1_000);
		var reverseCandidates = presenceRepository.findCandidates(candidateId, 37.501, 127.0,
				0, 1_000, 0, 360, AT, REGION);

		assertThat(senderPreview.segments().stream().mapToLong(DirectionPreviewResult.SegmentCount::count).sum())
				.isEqualTo(1);
		assertThat(reverseCandidates).extracting(candidate -> candidate.userId()).doesNotContain(senderId);
	}

	private DirectionPreviewResult preview(long senderId, long schemeId, long minDistance, long maxDistance) {
		return previewService.previewAll(new DirectionPostService.PreviewAllCommand(senderId, schemeId,
				minDistance, maxDistance, REGION, AT));
	}

	private long count(DirectionPreviewResult result, String segmentKey) {
		return result.segments().stream().filter(segment -> segment.segmentKey().equals(segmentKey))
				.findFirst().orElseThrow().count();
	}

	private long scheme(String code, int version) {
		DirectionScheme scheme = schemeRepository.save(DirectionScheme.createEqual(code, version, 8, BigDecimal.ZERO));
		IntStream.range(0, 8).forEach(index -> schemeRepository.saveSegment(DirectionSegment.create(scheme.getId(),
				"S" + index, "segment-" + index, BigDecimal.valueOf(index * 45L + 22.5), BigDecimal.valueOf(45),
				index)));
		return scheme.getId();
	}

	private Coordinate presenceAtBearing(long userId, double latitude, double longitude, double distanceMeters,
			double bearingDegrees, boolean receiveAllowed, Instant locationAt, Instant expiresAt) {
		Coordinate point = oracle.project(new Coordinate(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude)),
				distanceMeters, bearingDegrees);
		presence(userId, point.latitude().doubleValue(), point.longitude().doubleValue(),
				receiveAllowed, locationAt, expiresAt);
		return point;
	}

	private void presence(long userId, double latitude, double longitude, boolean receiveAllowed,
			Instant locationAt, Instant expiresAt) {
		ActiveUserPresence value = ActiveUserPresence.create(userId, BigDecimal.valueOf(latitude),
				BigDecimal.valueOf(longitude),
				null, REGION, BigDecimal.ONE, receiveAllowed, locationAt, expiresAt);
		presenceRepository.save(value);
		oracle.seed(value);
	}

	private long account(String nickname, String status) {
		if ("DELETED".equals(status)) {
			return jdbc.queryForObject("""
					INSERT INTO user_account
						(role, country_code, status, coarse_region_code, locale, timezone, nickname, deleted_at)
					VALUES ('USER', 'KR', ?, ?, 'ko-KR', 'Asia/Seoul', ?, ?)
					RETURNING id
					""", Long.class, status, REGION, nickname, Timestamp.from(AT));
		}
		return jdbc.queryForObject("""
				INSERT INTO user_account
					(role, country_code, status, coarse_region_code, locale, timezone, nickname)
				VALUES ('USER', 'KR', ?, ?, 'ko-KR', 'Asia/Seoul', ?)
				RETURNING id
				""", Long.class, status, REGION, nickname);
	}
}
