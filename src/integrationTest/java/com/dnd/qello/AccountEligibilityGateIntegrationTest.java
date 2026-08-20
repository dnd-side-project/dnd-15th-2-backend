/**
 * Created at: 2026-08-20T15:12:00+09:00
 * Source scenario: TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ-INT-030
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;
import com.dnd.qello.feed.service.InboxApplicationService;
import com.dnd.qello.feed.view.InboxCategory;

/**
 * account.service로 승격된 AccountEligibilityGate가 feed의 기존
 * FED-APP-001·FED-APP-002 응답 계약을 바꾸지 않는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountEligibilityGateIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH176-GATE";

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private InboxApplicationService inbox;

	@BeforeEach
	void resetFixtures() {
		jdbc.update("DELETE FROM post_recipient WHERE recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'GH176 Gate Test', 'REGION')
			""", REGION);
	}

	@Test
	@DisplayName("존재하지 않는 계정은 FED-APP-001(404)을 반환한다")
	void unknownAccountReturnsFedApp001() {
		long unknownAccountId = 987_654_321L;

		assertThatThrownBy(() -> inbox.list(unknownAccountId, InboxCategory.UNANSWERED, null))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INBOX_ACCOUNT_NOT_FOUND);
	}

	@Test
	@DisplayName("OPERATOR 역할 계정은 FED-APP-002(403)을 반환한다")
	void operatorAccountReturnsFedApp002() {
		long operatorId = account("gate176-operator", "OPERATOR", "ACTIVE");

		assertThatThrownBy(() -> inbox.list(operatorId, InboxCategory.UNANSWERED, null))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INBOX_ACCOUNT_NOT_ELIGIBLE);
	}

	@Test
	@DisplayName("BLOCKED 상태인 USER 계정은 FED-APP-002(403)을 반환한다")
	void blockedAccountReturnsFedApp002() {
		long blockedId = account("gate176-blocked", "USER", "BLOCKED");

		assertThatThrownBy(() -> inbox.list(blockedId, InboxCategory.UNANSWERED, null))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INBOX_ACCOUNT_NOT_ELIGIBLE);
	}

	private long account(String nickname, String role, String status) {
		return jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES (?, ?, ?, ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, role, role.equals("OPERATOR") ? null : "KR", status, REGION, nickname);
	}
}
