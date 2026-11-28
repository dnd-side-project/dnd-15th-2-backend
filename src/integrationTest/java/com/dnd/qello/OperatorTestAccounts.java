/*
 * Created at: 2026-11-24T23:50:07+09:00
 * Source scenario: TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-INT-010 (seed helper)
 */
package com.dnd.qello;

import org.springframework.jdbc.core.JdbcTemplate;

import com.dnd.qello.auth.domain.LoginId;

// #376: 시드한 운영자는 비밀번호를 바꿔야 하는 상태(password_change_required = TRUE)로 시작하고, 그 세션은 운영자
// API에서 403 AUT-APP-008을 받는다. 시드 직후 운영자 API를 호출하는 통합 테스트는 이 helper로 변경을 마친 상태로
// 바꾼다. 그 밖의 기대값은 바꾸지 않는다.
final class OperatorTestAccounts {

	private OperatorTestAccounts() {
	}

	static void completePasswordChange(JdbcTemplate jdbcTemplate, String loginId) {
		int updated = jdbcTemplate.update(
				"UPDATE operator_credential SET password_change_required = FALSE WHERE login_id = ?",
				LoginId.of(loginId).value());
		if (updated != 1) {
			throw new IllegalStateException("시드한 운영자 자격증명이 없습니다");
		}
	}
}
