package com.dnd.qello.direction.repository.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSchemeStatus;
import com.dnd.qello.direction.domain.DirectionSchemeType;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.repository.jdbc.sql.DirectionSchemeSql;

@Repository
public class JdbcDirectionSchemeRepository implements DirectionSchemeRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcDirectionSchemeRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override
	public DirectionScheme save(DirectionScheme scheme) {
		String sql = scheme.getId() == null ? DirectionSchemeSql.SCHEME_INSERT : DirectionSchemeSql.SCHEME_UPDATE;
		MapSqlParameterSource p = parameters(scheme);
		Long id = jdbc.queryForObject(sql, p, Long.class);
		return DirectionScheme.restore(id, scheme.getCode(), scheme.getVersion(), scheme.getType(),
			scheme.getSegmentCount(), scheme.getStartOffsetDegrees(), scheme.getStatus());
	}

	@Override
	public Optional<DirectionScheme> findById(long id) {
		return queryOne("SELECT * FROM direction_scheme WHERE id = :id", new MapSqlParameterSource("id", id));
	}

	@Override
	public Optional<DirectionScheme> findByCodeAndVersion(String code, int version) {
		return queryOne("SELECT * FROM direction_scheme WHERE code = :code AND version = :version",
			new MapSqlParameterSource().addValue("code", code).addValue("version", version));
	}

	@Override
	public Optional<DirectionScheme> findActiveByCode(String code) {
		return queryOne("SELECT * FROM direction_scheme WHERE code = :code AND status = 'ACTIVE' "
			+ "ORDER BY version DESC LIMIT 1", new MapSqlParameterSource("code", code));
	}

	@Override
	public List<DirectionSegment> findSegments(long schemeId) {
		return jdbc.query("SELECT * FROM direction_segment WHERE scheme_id = :schemeId ORDER BY sort_order",
			new MapSqlParameterSource("schemeId", schemeId),
			(org.springframework.jdbc.core.RowMapper<DirectionSegment>) JdbcDirectionSchemeRepository::mapSegment);
	}

	@Override
	public DirectionSegment saveSegment(DirectionSegment segment) {
		String sql = segment.getId() == null ? DirectionSchemeSql.SEGMENT_INSERT : DirectionSchemeSql.SEGMENT_UPDATE;
		MapSqlParameterSource p = new MapSqlParameterSource()
			.addValue("id", segment.getId()).addValue("schemeId", segment.getSchemeId())
			.addValue("segmentKey", segment.getSegmentKey()).addValue("displayName", segment.getDisplayName())
			.addValue("center", segment.getCenterBearingDegrees()).addValue("width", segment.getAngularWidthDegrees())
			.addValue("sortOrder", segment.getSortOrder());
		Long id = jdbc.queryForObject(sql, p, Long.class);
		return DirectionSegment.restore(id, segment.getSchemeId(), segment.getSegmentKey(), segment.getDisplayName(),
			segment.getCenterBearingDegrees(), segment.getAngularWidthDegrees(), segment.getSortOrder());
	}

	private Optional<DirectionScheme> queryOne(String sql, MapSqlParameterSource params) {
		return jdbc.query(sql, params, rs -> rs.next() ? Optional.of(mapScheme(rs)) : Optional.empty());
	}

	private static MapSqlParameterSource parameters(DirectionScheme scheme) {
		return new MapSqlParameterSource().addValue("id", scheme.getId()).addValue("code", scheme.getCode())
			.addValue("version", scheme.getVersion()).addValue("type", scheme.getType().name())
			.addValue("segmentCount", scheme.getSegmentCount()).addValue("startOffset", scheme.getStartOffsetDegrees())
			.addValue("status", scheme.getStatus().name());
	}

	private static DirectionScheme mapScheme(ResultSet rs) throws SQLException {
		return DirectionScheme.restore(rs.getLong("id"), rs.getString("code"), rs.getInt("version"),
			DirectionSchemeType.valueOf(rs.getString("type")), (Integer) rs.getObject("segment_count"),
			rs.getBigDecimal("start_offset_deg"), DirectionSchemeStatus.valueOf(rs.getString("status")));
	}

	private static DirectionSegment mapSegment(ResultSet rs, int rowNum) throws SQLException {
		return mapSegment(rs);
	}

	private static DirectionSegment mapSegment(ResultSet rs) throws SQLException {
		return DirectionSegment.restore(rs.getLong("id"), rs.getLong("scheme_id"), rs.getString("segment_key"),
			rs.getString("display_name"), rs.getBigDecimal("center_bearing_deg"),
			rs.getBigDecimal("angular_width_deg"), rs.getInt("sort_order"));
	}
}
