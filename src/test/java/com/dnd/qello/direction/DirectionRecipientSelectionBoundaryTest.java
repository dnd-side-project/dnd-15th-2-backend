/**
 * Created at: 2026-08-10T23:14:20+09:00
 * Source scenario: TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-UNIT-002 through UNIT-004
 */
package com.dnd.qello.direction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.direction.repository.jdbc.sql.ActiveUserPresenceSql;
import com.dnd.qello.feed.repository.jdbc.sql.PostAnswerQuerySql;

class DirectionRecipientSelectionBoundaryTest {

	@Test
	@DisplayName("후보 SQL은 ACTIVE 계정과 양방향 활성 차단을 함께 검사한다")
	void candidateSqlKeepsAccountAndBidirectionalBlockFilters() {
		String sql = ActiveUserPresenceSql.FIND_CANDIDATES_SQL;

		assertThat(sql).contains("JOIN user_account ua ON ua.id = p.user_id")
			.contains("ua.status = 'ACTIVE'")
			.contains("FROM user_block")
			.contains("ub.blocker_id = :excludedUserId")
			.contains("ub.blocker_id = p.user_id")
			.contains("ub.released_at IS NULL")
			.contains("recipient_receive_state")
			.contains("ORDER BY recent_received_count, last_received_at NULLS FIRST, distance_m, user_id");
	}

	@Test
	@DisplayName("답변 열람 SQL은 질문자와 viewer 사이의 양방향 활성 차단을 차단한다")
	void answerVisibilitySqlKeepsBidirectionalBlockFilter() {
		assertThat(PostAnswerQuerySql.CAN_VIEW_ANSWERS_SQL)
			.contains("ub.blocker_id = :viewerId AND ub.blocked_id = dp.sender_id")
			.contains("ub.blocker_id = dp.sender_id AND ub.blocked_id = :viewerId")
			.contains("ub.released_at IS NULL");
	}

	@Test
	@DisplayName("수신자 선정 설정은 application properties에서 조정 가능하다")
	void applicationPropertiesExposeAdjustableRecipientSelectionLimit() throws IOException {
		String properties = Files.readString(Path.of("src/main/resources/application.properties"));

		assertThat(properties).contains("qello.direction.max-recipients-per-post=10");
	}
}
