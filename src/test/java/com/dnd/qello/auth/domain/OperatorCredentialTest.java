/*
 * Created at: 2026-08-07T15:40:00+09:00
 * Source scenario: TEST-PLAN-GH-72-OPERATOR-CREDENTIAL-UNIT-001 through UNIT-005
 */
package com.dnd.qello.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;
import com.dnd.qello.auth.security.PasswordHash;

class OperatorCredentialTest {

	private static final Instant NOW = Instant.parse("2026-08-07T09:00:00Z");
	private static final PasswordHash HASH = new PasswordHash("$2a$10$hashed-value");

	@Test
	@DisplayName("발급 직후에는 실패 기록과 잠금이 없다")
	void issuesUnlockedCredential() {
		OperatorCredential credential = issued();

		assertThat(credential.getFailedAttemptCount()).isZero();
		assertThat(credential.getLockedUntil()).isNull();
		assertThat(credential.getLastLoginAt()).isNull();
		assertThat(credential.isLockedAt(NOW)).isFalse();
	}

	@Test
	@DisplayName("실패가 5회에 도달하면 15분 동안 잠긴다")
	void locksAfterFiveConsecutiveFailures() {
		OperatorCredential credential = issued();

		for (int attempt = 1; attempt < OperatorCredential.MAX_FAILED_ATTEMPTS; attempt++) {
			credential = credential.recordFailure(NOW);
			assertThat(credential.isLockedAt(NOW)).isFalse();
		}

		credential = credential.recordFailure(NOW);

		assertThat(credential.getFailedAttemptCount()).isEqualTo(OperatorCredential.MAX_FAILED_ATTEMPTS);
		assertThat(credential.isLockedAt(NOW)).isTrue();
		assertThat(credential.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
	}

	@Test
	@DisplayName("잠금은 해제 시각이 지나면 저절로 풀린다")
	void unlocksWhenLockDurationPasses() {
		OperatorCredential credential = issued();
		for (int attempt = 0; attempt < OperatorCredential.MAX_FAILED_ATTEMPTS; attempt++) {
			credential = credential.recordFailure(NOW);
		}

		assertThat(credential.isLockedAt(NOW.plus(Duration.ofMinutes(14)))).isTrue();
		assertThat(credential.isLockedAt(NOW.plus(Duration.ofMinutes(15)))).isFalse();
		assertThat(credential.isLockedAt(NOW.plus(Duration.ofMinutes(16)))).isFalse();
	}

	@Test
	@DisplayName("로그인에 성공하면 실패 기록과 잠금이 사라지고 마지막 로그인 시각이 남는다")
	void clearsFailureStateOnSuccess() {
		Instant loggedInAt = NOW.plus(Duration.ofMinutes(20));
		OperatorCredential credential = issued()
			.recordFailure(NOW)
			.recordFailure(NOW)
			.recordSuccess(loggedInAt);

		assertThat(credential.getFailedAttemptCount()).isZero();
		assertThat(credential.getLockedUntil()).isNull();
		assertThat(credential.getLastLoginAt()).isEqualTo(loggedInAt);
	}

	@Test
	@DisplayName("비밀번호를 바꾸면 잠금이 풀리고 변경 시각이 갱신된다")
	void changingPasswordResetsLockState() {
		Instant changedAt = NOW.plus(Duration.ofHours(1));
		PasswordHash newHash = new PasswordHash("$2a$10$new-value");
		OperatorCredential credential = issued();
		for (int attempt = 0; attempt < OperatorCredential.MAX_FAILED_ATTEMPTS; attempt++) {
			credential = credential.recordFailure(NOW);
		}

		OperatorCredential changed = credential.changePassword(newHash, changedAt);

		assertThat(changed.getPasswordHash()).isEqualTo(newHash);
		assertThat(changed.getFailedAttemptCount()).isZero();
		assertThat(changed.isLockedAt(NOW)).isFalse();
		assertThat(changed.getPasswordUpdatedAt()).isEqualTo(changedAt);
	}

	@Test
	@DisplayName("음수 실패 횟수는 복원 단계에서 거절한다")
	void rejectsNegativeFailureCount() {
		assertThatThrownBy(() -> OperatorCredential.restore(1L, loginId(), HASH, -1, null, NOW, null))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIAL_STATE);
	}

	private OperatorCredential issued() {
		return OperatorCredential.issue(1L, loginId(), HASH, NOW);
	}

	private LoginId loginId() {
		return new LoginId("qello-admin");
	}
}
