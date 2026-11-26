/*
 * Created at: 2026-11-24T23:50:07+09:00
 * Source scenario: TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-018
 */
package com.dnd.qello.auth.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorPasswordChangeRequestTest {

	@Test
	@DisplayName("UNIT-018: toString은 현재 비밀번호와 새 비밀번호를 모두 가린다")
	void toStringRedactsBothPasswords() {
		OperatorPasswordChangeRequest request = new OperatorPasswordChangeRequest(
				"current-secret-value", "Changed-Passw0rd-376");

		assertThat(request.toString())
				.doesNotContain("current-secret-value", "Changed-Passw0rd-376")
				.contains("currentPassword=REDACTED", "newPassword=REDACTED");
	}
}
