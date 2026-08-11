/**
 * Created at: 2026-08-12T01:17:58+09:00
 * Source scenario: TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-002,
 * TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-003,
 * TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-007
 */
package com.dnd.qello.direction;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.direction.repository.jdbc.sql.ActiveUserPresenceSql;

class DirectionPreviewPersistenceBoundaryTest {

	@Test
	@DisplayName("전체 방향 집계 SQL은 ST_DWithin과 ST_Azimuth를 한 질의에서 사용한다")
	void aggregateSqlUsesPostgisDistanceAndBearingInOneQuery() {
		String sql = ActiveUserPresenceSql.FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL;

		assertThat(sql).contains("ST_DWithin")
			.contains("ST_Distance")
			.contains("ST_Azimuth")
			.contains("DEGREES(ST_Azimuth(origin.point, p.position))")
			.contains("JOIN direction_segment ds")
			.contains("LEFT JOIN eligible_candidates ec")
			.contains("COUNT(ec.user_id)")
			.contains("scheme.status = 'ACTIVE'");
	}

	@Test
	@DisplayName("전체 방향 집계 SQL은 시작각 포함 종료각 제외와 0/360도 래핑을 표현한다")
	void aggregateSqlUsesHalfOpenWrappingBoundaries() {
		String sql = ActiveUserPresenceSql.FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL;

		assertThat(sql).contains("ec.bearing_deg >= bounds.start_deg")
			.contains("ec.bearing_deg < bounds.end_deg")
			.contains("bounds.start_deg >= bounds.end_deg")
			.contains("ec.bearing_deg >= bounds.start_deg OR ec.bearing_deg < bounds.end_deg")
			.contains("MOD(ds.center_bearing_deg - ds.angular_width_deg / 2 + 360, 360)")
			.contains("MOD(ds.center_bearing_deg + ds.angular_width_deg / 2 + 360, 360)");
	}

	@Test
	@DisplayName("전체 방향 집계 repository는 segment별 findCandidates 반복 호출 대신 단일 JDBC query를 사용한다")
	void repositoryUsesSingleJdbcQueryForAllSegments() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/com/dnd/qello/direction/repository/jdbc/JdbcActiveUserPresenceRepository.java"));
		int methodStart = source.indexOf("findCandidateCountsBySegment");
		int methodEnd = source.indexOf("\n\tprivate static MapSqlParameterSource", methodStart);
		String method = source.substring(methodStart, methodEnd);

		assertThat(method).contains("jdbc.query(ActiveUserPresenceSql.FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL")
			.doesNotContain("findCandidates(")
			.doesNotContain("for (")
			.doesNotContain("while (");
	}
}
