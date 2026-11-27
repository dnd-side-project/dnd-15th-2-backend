/**
 * Created at: 2026-08-12T01:17:58+09:00
 * Source scenario: TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-002, UNIT-003, UNIT-007
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-011
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-INT-013 (updated 2026-11-25T01:43:04.889Z)
 */
package com.dnd.qello.direction;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.dnd.qello.direction.repository.ActiveUserPresenceRepository.DirectionSegmentCandidateCount;
import com.dnd.qello.direction.repository.jdbc.JdbcActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.jdbc.sql.ActiveUserPresenceSql;
import com.dnd.qello.direction.security.AesGcmCoordinateProtector;
import com.dnd.qello.direction.security.Coordinate;
import com.dnd.qello.direction.security.CoordinateKeyRing;
import com.dnd.qello.direction.security.CoordinateProtector;
import com.dnd.qello.direction.security.CoordinatePurpose;

import static org.assertj.core.api.Assertions.assertThat;

class DirectionPreviewPersistenceBoundaryTest {

	static final Instant AT = Instant.parse("2026-11-25T00:00:00Z");
	private static final CoordinateProtector PROTECTOR = new AesGcmCoordinateProtector(
			new CoordinateKeyRing("unit-coordinate-fixture", Map.of("unit-coordinate-fixture", new byte[32])));

	@Test
	@DisplayName("한 snapshot의 암호문 후보와 모든 segment를 읽어 빈 방향도 순서대로 반환한다")
	void preservesEmptySegmentsFromOneSnapshot() {
		AtomicInteger reads = new AtomicInteger();
		var repository = repository(List.of(segment("N", 337.5, 22.5), segment("E", 67.5, 112.5),
				candidate(2, 1, 0, 0, null)), reads);

		var counts = repository.findCandidateCountsBySegment(1, 1, 0, 0, 0, 20_100_000, AT, null);

		assertThat(counts).containsExactly(new DirectionSegmentCandidateCount("N", 1),
				new DirectionSegmentCandidateCount("E", 0));
		assertThat(reads.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("GLOBAL nullable region parameter는 PostgreSQL 타입 추론 오류 없이 명시적 타입을 갖는다")
	void aggregateSqlCastsNullableRegionParameter() {
		assertThat(ActiveUserPresenceSql.FIND_CANDIDATES_SQL)
				.contains("CAST(:regionCode AS VARCHAR) IS NULL")
				.contains("p.coarse_region_code = CAST(:regionCode AS VARCHAR)");
		assertThat(ActiveUserPresenceSql.FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL)
				.contains("CAST(:regionCode AS VARCHAR) IS NULL")
				.contains("p.coarse_region_code = CAST(:regionCode AS VARCHAR)");
	}

	@Test
	@DisplayName("복호화한 북쪽 후보는 0도 시작 segment에 포함되고 0도 끝 segment에는 포함되지 않는다")
	void appliesHalfOpenWrappingBoundariesToDecryptedCandidates() {
		var repository = repository(List.of(segment("START", 0, 45), segment("END", 315, 0),
				candidate(2, 1, 0, 0, null)), new AtomicInteger());

		assertThat(repository.findCandidateCountsBySegment(1, 1, 0, 0, 0, 20_100_000, AT, null))
				.containsExactly(new DirectionSegmentCandidateCount("START", 1),
						new DirectionSegmentCandidateCount("END", 0));
	}

	@Test
	@DisplayName("후보가 전혀 없는 snapshot에서도 모든 방향을 0명으로 보존한다")
	void returnsZeroForEverySegmentWithoutCandidates() {
		var repository = repository(List.of(segment("N", 337.5, 22.5), segment("S", 157.5, 202.5)),
				new AtomicInteger());

		assertThat(repository.findCandidateCountsBySegment(1, 1, 0, 0, 0, 20_100_000, AT, null))
				.containsExactly(new DirectionSegmentCandidateCount("N", 0),
						new DirectionSegmentCandidateCount("S", 0));
	}

	static Object[] candidate(long id, double latitude, double longitude, long recent, Instant last) {
		byte[] envelope = PROTECTOR.protect(new Coordinate(BigDecimal.valueOf(latitude),
				BigDecimal.valueOf(longitude)), CoordinatePurpose.ACTIVE_USER_PRESENCE, id);
		return new Object[]{id, envelope, "TEST", recent, last == null ? null : Timestamp.from(last),
				null, null, null};
	}

	private static Object[] segment(String key, double start, double end) {
		return new Object[]{null, null, null, null, null, key, start, end};
	}

	static JdbcActiveUserPresenceRepository repository(List<Object[]> data, AtomicInteger reads) {
		var jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource()) {
			@Override
			public <T> T query(String sql, SqlParameterSource parameters, ResultSetExtractor<T> extractor) {
				reads.incrementAndGet();
				try (CachedRowSet rows = snapshot(data)) {
					return extractor.extractData(rows);
				} catch (SQLException exception) {
					throw new IllegalStateException(exception);
				}
			}
		};
		return new JdbcActiveUserPresenceRepository(jdbc, PROTECTOR);
	}

	private static CachedRowSet snapshot(List<Object[]> data) throws SQLException {
		List<String> names = List.of("user_id", "position_ciphertext", "coarse_region_code",
				"recent_received_count", "last_received_at", "segment_key", "start_deg", "end_deg");
		List<Integer> types = List.of(Types.BIGINT, Types.VARBINARY, Types.VARCHAR, Types.BIGINT,
				Types.TIMESTAMP, Types.VARCHAR, Types.DOUBLE, Types.DOUBLE);
		RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
		metadata.setColumnCount(names.size());
		for (int i = 0; i < names.size(); i++) {
			metadata.setColumnName(i + 1, names.get(i));
			metadata.setColumnType(i + 1, types.get(i));
		}
		CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
		rows.setMetaData(metadata);
		for (Object[] values : data.reversed()) {
			rows.moveToInsertRow();
			for (int i = 0; i < values.length; i++) {
				rows.updateObject(i + 1, values[i]);
			}
			rows.insertRow();
			rows.moveToCurrentRow();
			rows.beforeFirst();
		}
		rows.beforeFirst();
		return rows;
	}
}
