package com.dnd.qello.feed.repository.jdbc;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.dnd.qello.feed.repository.AttachedMedia;

/** feed 조회 JDBC repository들이 공유하는 ResultSet 변환 헬퍼. */
final class FeedRowMappers {

	private FeedRowMappers() {
	}

	/**
	 * FeedMediaSql이 만든 병렬 배열("media_ids", "media_storage_keys")을 첨부 목록으로 묶는다. 첨부가
	 * 없으면 SQL이 COALESCE로 빈 배열을 주므로 null 배열은 방어적으로만 처리한다.
	 */
	static List<AttachedMedia> attachedMedia(ResultSet rs) throws SQLException {
		Array idArray = rs.getArray("media_ids");
		Array keyArray = rs.getArray("media_storage_keys");
		if (idArray == null || keyArray == null) {
			return List.of();
		}
		Long[] ids = (Long[]) idArray.getArray();
		String[] storageKeys = (String[]) keyArray.getArray();
		if (ids.length != storageKeys.length) {
			throw new IllegalStateException("media_ids와 media_storage_keys의 길이가 다릅니다");
		}
		List<AttachedMedia> media = new ArrayList<>(ids.length);
		for (int i = 0; i < ids.length; i++) {
			media.add(new AttachedMedia(ids[i], storageKeys[i]));
		}
		return media;
	}

	/**
	 * nullable timestamptz 컬럼을 Instant로 옮긴다. opened_at, skip_requested_at처럼 값이 없을 수
	 * 있는 컬럼 전용이다.
	 */
	static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}
}
