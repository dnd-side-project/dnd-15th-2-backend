package com.dnd.qello.auth.domain;

import java.time.Duration;
import java.time.Instant;

import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;
import com.dnd.qello.auth.security.PasswordHash;

// 백오피스 운영자의 자격증명.
//
// 계정(Account)과 분리된 애그리거트다. 비밀번호와 잠금 상태가 한곳에 있어야
// "연속 실패 임계치를 넘으면 잠근다"는 규칙을 이 객체 혼자 지킬 수 있다.
// 근거는 docs/adr/0006-split-operator-and-device-authentication.md에 있다.
public final class OperatorCredential {

	public static final int MAX_FAILED_ATTEMPTS = 5;
	public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

	private final Long userId;
	private final LoginId loginId;
	private final PasswordHash passwordHash;
	private final int failedAttemptCount;
	private final Instant lockedUntil;
	private final Instant passwordUpdatedAt;
	private final Instant lastLoginAt;

	private OperatorCredential(
		Long userId,
		LoginId loginId,
		PasswordHash passwordHash,
		int failedAttemptCount,
		Instant lockedUntil,
		Instant passwordUpdatedAt,
		Instant lastLoginAt
	) {
		this.userId = requireValue(userId, "userId");
		this.loginId = requireValue(loginId, "loginId");
		this.passwordHash = requireValue(passwordHash, "passwordHash");
		this.failedAttemptCount = validateFailedAttemptCount(failedAttemptCount);
		this.lockedUntil = lockedUntil;
		this.passwordUpdatedAt = requireValue(passwordUpdatedAt, "passwordUpdatedAt");
		this.lastLoginAt = lastLoginAt;
	}

	public static OperatorCredential issue(
		Long userId,
		LoginId loginId,
		PasswordHash passwordHash,
		Instant issuedAt
	) {
		return new OperatorCredential(userId, loginId, passwordHash, 0, null, issuedAt, null);
	}

	public static OperatorCredential restore(
		Long userId,
		LoginId loginId,
		PasswordHash passwordHash,
		int failedAttemptCount,
		Instant lockedUntil,
		Instant passwordUpdatedAt,
		Instant lastLoginAt
	) {
		return new OperatorCredential(
			userId, loginId, passwordHash, failedAttemptCount, lockedUntil, passwordUpdatedAt, lastLoginAt);
	}

	// 잠금은 시각 비교로만 판단한다. 별도 상태 컬럼을 두면 잠금 해제 시각이 지난 뒤에도
	// 누군가 상태를 되돌려야 풀리는 구조가 된다.
	public boolean isLockedAt(Instant now) {
		requireValue(now, "now");
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

	// 실패를 한 번 기록한다. 임계치에 도달하면 잠근다.
	public OperatorCredential recordFailure(Instant failedAt) {
		requireValue(failedAt, "failedAt");
		int nextCount = failedAttemptCount + 1;
		Instant nextLockedUntil = nextCount >= MAX_FAILED_ATTEMPTS
			? failedAt.plus(LOCK_DURATION)
			: lockedUntil;
		return new OperatorCredential(
			userId, loginId, passwordHash, nextCount, nextLockedUntil, passwordUpdatedAt, lastLoginAt);
	}

	// 성공하면 실패 기록과 잠금을 모두 지운다.
	public OperatorCredential recordSuccess(Instant succeededAt) {
		requireValue(succeededAt, "succeededAt");
		return new OperatorCredential(
			userId, loginId, passwordHash, 0, null, passwordUpdatedAt, succeededAt);
	}

	public OperatorCredential changePassword(PasswordHash newPasswordHash, Instant changedAt) {
		requireValue(newPasswordHash, "passwordHash");
		requireValue(changedAt, "changedAt");
		return new OperatorCredential(
			userId, loginId, newPasswordHash, 0, null, changedAt, lastLoginAt);
	}

	public Long getUserId() {
		return userId;
	}

	public LoginId getLoginId() {
		return loginId;
	}

	public PasswordHash getPasswordHash() {
		return passwordHash;
	}

	public int getFailedAttemptCount() {
		return failedAttemptCount;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

	public Instant getPasswordUpdatedAt() {
		return passwordUpdatedAt;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new AuthException(
				AuthErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}

	private static int validateFailedAttemptCount(int failedAttemptCount) {
		if (failedAttemptCount < 0) {
			throw new AuthException(
				AuthErrorCode.INVALID_CREDENTIAL_STATE,
				"failedAttemptCount",
				"failedAttemptCount는 음수일 수 없습니다"
			);
		}
		return failedAttemptCount;
	}

}
