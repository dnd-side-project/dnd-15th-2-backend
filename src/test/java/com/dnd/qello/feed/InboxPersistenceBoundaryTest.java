/**
 * Created at: 2026-08-16T14:09:58+09:00
 * Source scenario: TEST-PLAN-GH-124-INBOX-READ-SKIP-API-UNIT-002
 */
package com.dnd.qello.feed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.direction.repository.jdbc.sql.PostRecipientSql;
import com.dnd.qello.feed.repository.jdbc.sql.FeedScopeSql;

class InboxPersistenceBoundaryTest {

	@Test
	@DisplayName("수신함 공통 스코프는 양방향 활성 차단을 같은 released 조건으로 제외한다")
	void inboxScopeExcludesBothDirectionsOfActiveBlock() {
		assertThat(FeedScopeSql.ACTIVE_POST_VISIBILITY)
			.contains("ub.blocker_id = :recipientId")
			.contains("ub.blocked_id = dp.sender_id")
			.contains("ub.blocker_id = dp.sender_id")
			.contains("ub.blocked_id = :recipientId")
			.contains("ub.released_at IS NULL");
	}

	@Test
	@DisplayName("수신함 상세와 명령 scope는 소유권·visibility·만료 조건으로 수신 행을 잠근다")
	void scopedCommandSqlLocksEligibleOwnerRow() {
		assertThat(PostRecipientSql.FIND_INBOX_ITEM_FOR_UPDATE)
			.contains("pr.id = :id")
			.contains("pr.recipient_id = :recipientId")
			.contains(FeedScopeSql.ACTIVE_POST_VISIBILITY.trim())
			.contains("dp.expires_at > :at")
			.contains("FOR UPDATE OF pr");
		assertThat(PostRecipientSql.FIND_INBOX_COMMAND_ITEM_FOR_UPDATE)
			.contains("pr.id = :id")
			.contains("pr.recipient_id = :recipientId")
			.contains(FeedScopeSql.ACTIVE_POST_VISIBILITY.trim())
			.contains("dp.expires_at > :at")
			.contains("pr.capacity_released_at IS NULL")
			.contains("FOR UPDATE OF pr");
	}

	@Test
	@DisplayName("열람·넘김·되돌리기는 이전 상태와 미해제 슬롯 조건을 만족할 때만 전이한다")
	void userTransitionsAreConditional() {
		assertThat(PostRecipientSql.TRANSITION_TO_OPENED)
			.contains("status = :previousStatus")
			.contains("capacity_released_at IS NULL")
			.contains("RETURNING *");
		assertThat(PostRecipientSql.TRANSITION_TO_SKIP_PENDING)
			.contains("status = :previousStatus")
			.contains("capacity_released_at IS NULL")
			.contains("RETURNING *");
		assertThat(PostRecipientSql.TRANSITION_FROM_SKIP_PENDING)
			.contains("status = 'SKIP_PENDING'")
			.contains("skip_requested_at = :expectedSkipRequestedAt")
			.contains("capacity_released_at IS NULL")
			.contains("RETURNING *");
	}

	@Test
	@DisplayName("수신함 scope SQL은 데이터베이스 현재 시각 대신 호출자가 전달한 시각을 사용한다")
	void scopeSqlRemainsDeterministic() {
		assertThat(FeedScopeSql.ACTIVE_POST_VISIBILITY).doesNotContain("CURRENT_TIMESTAMP", "clock_timestamp()");
	}
}
