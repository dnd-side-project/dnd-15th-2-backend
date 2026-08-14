/**
 * Created at: 2026-08-12T01:17:58+09:00
 * Source scenario: TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-INT-001 through INT-008,
 * TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-013 (added 2026-08-14T00:51:11+09:00)
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.direction.service.DirectionPreviewResult;

@SpringBootTest
@ActiveProfiles("test")
class DirectionPreviewIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRECTION-PREVIEW-117";
	private static final Instant AT = Instant.parse("2026-08-12T00:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ActiveUserPresenceRepository presenceRepository;
	@Autowired
	private DirectionSchemeRepository schemeRepository;
	@Autowired
	private DirectionPostService previewService;

	@BeforeEach
	void reset() {
		jdbc.update("DELETE FROM user_block");
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM direction_segment");
		jdbc.update("DELETE FROM direction_scheme");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Direction Preview Test Region', 'REGION')", REGION);
	}

	@Test
	@DisplayName("PostGIS 한 질의가 모든 활성 방향 segment count와 빈 segment 0을 반환한다")
	void aggregatesAllActiveSegmentsAndZeroFillsEmptySegments() {
		long senderId = account("preview-sender", "ACTIVE");
		presence(senderId, 37.5, 127.0, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		long schemeId = scheme("PREVIEW-ALL", 1);

		long north = account("preview-north", "ACTIVE");
		long northEast = account("preview-north-east", "ACTIVE");
		long east = account("preview-east", "ACTIVE");
		presenceAtBearing(north, 37.5, 127.0, 100, 0, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		presenceAtBearing(northEast, 37.5, 127.0, 100, 10, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		presenceAtBearing(east, 37.5, 127.0, 100, 90, true, AT.minusSeconds(60), AT.plusSeconds(3600));

		DirectionPreviewResult result = preview(senderId, schemeId, 0, 1_000);

		assertThat(result.segments()).hasSize(8)
			.extracting(DirectionPreviewResult.SegmentCount::segmentKey)
			.containsExactly("S0", "S1", "S2", "S3", "S4", "S5", "S6", "S7");
		assertThat(result.segments()).extracting(DirectionPreviewResult.SegmentCount::count)
			.containsExactly(2L, 0L, 1L, 0L, 0L, 0L, 0L, 0L);
	}

	@Test
	@DisplayName("실제 PostGIS 방위 집계는 시작각 포함 종료각 제외로 인접 segment를 중복 계상하지 않는다")
	void appliesHalfOpenBoundariesWithoutDuplicateCounts() {
		long senderId = account("boundary-sender", "ACTIVE");
		presence(senderId, 37.5, 127.0, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		long schemeId = scheme("PREVIEW-BOUNDARY", 2);

		// 이 fixture의 segment 시작각은 0°, 45°, 90° ... 이다. ST_Project → ST_Azimuth의
		// 부동소수점 오차로 정확한 시작각을 안정적으로 재현하기
		// 어렵기 때문에, 실제 PostGIS에서는 경계 직전·직후를 검증하고 정확한 포함 규칙은
		// DirectionSegment/SQL boundary test에서 고정한다.
		presenceAtBearing(account("after-45", "ACTIVE"), 37.5, 127.0, 100, 45.1, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		presenceAtBearing(account("before-45", "ACTIVE"), 37.5, 127.0, 100, 44.9, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		presenceAtBearing(account("after-315", "ACTIVE"), 37.5, 127.0, 100, 315.1, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		presenceAtBearing(account("before-315", "ACTIVE"), 37.5, 127.0, 100, 314.9, true, AT.minusSeconds(60), AT.plusSeconds(3600));

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
	@DisplayName("실제 PostGIS 거리 집계는 최소·최대 거리 경계를 포함하고 범위 밖 후보를 제외한다")
	void appliesInclusiveDistanceBoundaries() {
		long senderId = account("distance-sender", "ACTIVE");
		presence(senderId, 37.5, 127.0, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		long schemeId = scheme("PREVIEW-DISTANCE", 4);
		presenceAtBearing(account("min-boundary", "ACTIVE"), 37.5, 127.0, 1_000, 90, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		presenceAtBearing(account("max-boundary", "ACTIVE"), 37.5, 127.0, 2_000, 90, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		presenceAtBearing(account("outside-max", "ACTIVE"), 37.5, 127.0, 2_500, 90, true, AT.minusSeconds(60), AT.plusSeconds(3600));
		presenceAtBearing(account("expired", "ACTIVE"), 37.5, 127.0, 1_500, 90, true, AT.minusSeconds(3600), AT);
		presenceAtBearing(account("receive-disabled", "ACTIVE"), 37.5, 127.0, 1_500, 90, false, AT.minusSeconds(60), AT.plusSeconds(3600));
		presenceAtBearing(account("blocked-account", "BLOCKED"), 37.5, 127.0, 1_500, 90, true, AT.minusSeconds(60), AT.plusSeconds(3600));

		DirectionPreviewResult result = preview(senderId, schemeId, 1_000, 2_000);

		assertThat(count(result, "S2")).isEqualTo(2);
		assertThat(result.segments().stream().mapToLong(DirectionPreviewResult.SegmentCount::count).sum()).isEqualTo(2);
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
			"S" + index, "segment-" + index, BigDecimal.valueOf(index * 45L + 22.5), BigDecimal.valueOf(45), index)));
		return scheme.getId();
	}

	private void presenceAtBearing(long userId, double latitude, double longitude, double distanceMeters,
		double bearingDegrees, boolean receiveAllowed, Instant locationAt, Instant expiresAt) {
		jdbc.update("""
			INSERT INTO active_user_presence
				(user_id, position, coarse_cell_id, coarse_region_code, accuracy_m, receive_allowed, location_at, expires_at)
			VALUES (?, ST_Project(ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, radians(?)), NULL, ?, 1, ?, ?, ?)
			""", userId, longitude, latitude, distanceMeters, bearingDegrees, REGION, receiveAllowed,
			Timestamp.from(locationAt), Timestamp.from(expiresAt));
	}

	private void presence(long userId, double latitude, double longitude, boolean receiveAllowed,
		Instant locationAt, Instant expiresAt) {
		presenceRepository.save(ActiveUserPresence.create(userId, BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude),
			null, REGION, BigDecimal.ONE, receiveAllowed, locationAt, expiresAt));
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
