package com.dnd.qello.feed.repository.jdbc;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** JdbcInboxQueryRepository와 JdbcSentPostQueryRepository가 공유하는 ResultSet 변환 헬퍼. */
final class FeedRowMappers {

	private FeedRowMappers() {
	}

	/**
	 * array_agg(media_id) 컬럼("media_ids")을 List<Long>으로 변환한다.
	 * 첨부가 없으면 SQL이 COALESCE로 빈 배열을 주므로 null 배열은 방어적으로만 처리한다.
	 */
	static List<Long> mediaIds(ResultSet rs) throws SQLException {
		Array array = rs.getArray("media_ids");
		if (array == null) return List.of();
		return Arrays.asList((Long[]) array.getArray());
	}

	/** nullable timestamptz 컬럼을 Instant로 옮긴다. opened_at, skip_requested_at처럼 값이 없을 수 있는 컬럼 전용이다. */
	static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}
}
