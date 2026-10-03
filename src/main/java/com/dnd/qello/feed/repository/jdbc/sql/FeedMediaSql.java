package com.dnd.qello.feed.repository.jdbc.sql;

/**
 * feed 카드가 공유하는 첨부 미디어 SELECT 조각. READY가 아닌 자산은 조회 URL을 발급하지 않으므로 SQL에서 뺀다.
 * media_ids와 media_storage_keys는 같은 필터와 정렬을 쓰는 병렬 배열이라 한쪽만 바꾸면 짝이 어긋난다 — 그래서 두
 * 컬럼을 한 메서드에서 만든다.
 */
public final class FeedMediaSql {

	private FeedMediaSql() {
	}

	/**
	 * @param targetPredicate
	 *            media_attachment(ma)를 카드 대상에 묶는 조건. 예: {@code ma.post_id = dp.id}
	 */
	public static String attachedMediaColumns(String targetPredicate) {
		String source = "FROM media_attachment ma JOIN media_asset m ON m.id = ma.media_id"
				+ " WHERE " + targetPredicate + " AND m.status = 'READY'";
		return "       COALESCE((SELECT array_agg(ma.media_id ORDER BY ma.display_order) "
				+ source + "), '{}'::bigint[]) AS media_ids,\n"
				+ "       COALESCE((SELECT array_agg(m.storage_key ORDER BY ma.display_order) "
				+ source + "), '{}'::varchar[]) AS media_storage_keys,\n";
	}
}
