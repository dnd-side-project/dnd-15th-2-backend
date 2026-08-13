package com.dnd.qello.direction.repository.jdbc.sql;

/** JdbcRecipientReceiveStateRepository가 쓰는 SQL 상수. */
public final class RecipientReceiveStateSql {

	private RecipientReceiveStateSql() {
	}

	/** 시더 전용 덮어쓰기 UPSERT. 충돌 시 제안값(EXCLUDED)으로 모든 컬럼을 대체한다. */
	public static final String SAVE = """
		INSERT INTO recipient_receive_state
			(user_id, active_unhandled_count, recent_received_count, recent_window_started_at, last_received_at, updated_at)
		VALUES (:userId, :activeCount, :recentCount, :windowStartedAt, :lastReceivedAt, COALESCE(:updatedAt, clock_timestamp()))
		ON CONFLICT (user_id) DO UPDATE SET
			active_unhandled_count = EXCLUDED.active_unhandled_count,
			recent_received_count = EXCLUDED.recent_received_count,
			recent_window_started_at = EXCLUDED.recent_window_started_at,
			last_received_at = EXCLUDED.last_received_at,
			updated_at = EXCLUDED.updated_at
		""";

	/** 매칭 후보에 처음 등장한 legacy 사용자의 상태 행을 예약 전에 만든다. */
	public static final String ENSURE = """
		INSERT INTO recipient_receive_state
			(user_id, active_unhandled_count, recent_received_count, recent_window_started_at, last_received_at, updated_at)
		VALUES (:userId, 0, 0, :at, NULL, :at)
		ON CONFLICT (user_id) DO NOTHING
		""";

	/** 잠금된 후보만 반환해 다른 matching transaction이 같은 슬롯을 기다리지 않게 한다. */
	public static final String LOCK_AVAILABLE = """
		SELECT user_id
		FROM recipient_receive_state
		WHERE user_id IN (:userIds)
		  AND active_unhandled_count < :activeLimit
		ORDER BY active_unhandled_count, recent_received_count, last_received_at NULLS FIRST, user_id
		LIMIT :lockLimit
		FOR UPDATE SKIP LOCKED
		""";

	/**
	 * 슬롯 하나를 예약한다. 행이 없으면 만들고 있으면 늘리는 것을 한 문장으로 수행한다 —
	 * 조회 후 초기 행을 만들고 다시 갱신하는 3단계는 원자적이지 않아, 같은 신규 사용자를
	 * 동시에 노린 두 발송이 서로의 예약을 덮어썼다(#94).
	 *
	 * CONFLICT 경로는 기존 행의 값을 읽어 증가시키고 제안값(EXCLUDED)을 쓰지 않는다.
	 * 제안값을 쓰면 나중에 도착한 트랜잭션이 먼저 예약된 슬롯을 지운다. 이 점이 SAVE와
	 * 다르다 — SAVE는 시더로서 의도적으로 덮어쓴다.
	 * {@code recent_window_started_at}은 CONFLICT 경로에서 건드리지 않는다 —
	 * 매 예약마다 갱신되면 recent_received_count의 기준 구간이 사라진다.
	 *
	 * 상한 검사는 CONFLICT 경로에만 있다. INSERT 경로는 항상 1을 넣는데, activeLimit은
	 * 호출 전에 1 이상으로 검증되므로(DirectionReceiveProperties, RecipientReceiveState.canReserve)
	 * 첫 예약이 상한을 넘길 수 없다.
	 */
	public static final String RESERVE = """
		INSERT INTO recipient_receive_state
			(user_id, active_unhandled_count, recent_received_count, recent_window_started_at, last_received_at, updated_at)
		VALUES (:userId, 1, 1, :receivedAt, :receivedAt, clock_timestamp())
		ON CONFLICT (user_id) DO UPDATE SET
			active_unhandled_count = recipient_receive_state.active_unhandled_count + 1,
			recent_received_count = recipient_receive_state.recent_received_count + 1,
			last_received_at = :receivedAt,
			updated_at = clock_timestamp()
		WHERE recipient_receive_state.active_unhandled_count < :activeLimit
		""";

	/**
	 * 활성 미처리 카운터를 1 줄인다. 이미 0이면 아무것도 하지 않고 false를 반환한다 —
	 * 답변 발행이 재시도되거나 두 경로가 동시에 슬롯을 반환하려 할 때 카운터가
	 * 음수로 내려가지 않도록 막는 안전장치다.
	 */
	public static final String RELEASE = """
		UPDATE recipient_receive_state
		SET active_unhandled_count = active_unhandled_count - 1,
		    updated_at = :releasedAt
		WHERE user_id = :userId AND active_unhandled_count > 0
		""";
}
