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
	// 초기 비밀번호 여부(#376). 시드로 발급한 비밀번호는 운영자가 직접 바꾸기 전까지 true다.
	private final boolean passwordChangeRequired;
	private final Instant lastLoginAt;

	private OperatorCredential(
			Long userId,
			LoginId loginId,
			PasswordHash passwordHash,
			int failedAttemptCount,
			Instant lockedUntil,
			Instant passwordUpdatedAt,
			boolean passwordChangeRequired,
			Instant lastLoginAt) {
		this.userId = requireValue(userId, "userId");
		this.loginId = requireValue(loginId, "loginId");
		this.passwordHash = requireValue(passwordHash, "passwordHash");
		this.failedAttemptCount = validateFailedAttemptCount(failedAttemptCount);
		this.lockedUntil = lockedUntil;
		this.passwordUpdatedAt = requireValue(passwordUpdatedAt, "passwordUpdatedAt");
		this.passwordChangeRequired = passwordChangeRequired;
		this.lastLoginAt = lastLoginAt;
	}

	// 발급한 비밀번호는 시드 설정으로 받은 값이라 운영자 본인이 정한 비밀번호가 아니다. 첫 로그인에서
	// 바꾸게 하려고 변경 필요 상태로 시작한다.
	public static OperatorCredential issue(
			Long userId,
			LoginId loginId,
			PasswordHash passwordHash,
			Instant issuedAt) {
		return new OperatorCredential(userId, loginId, passwordHash, 0, null, issuedAt, true, null);
	}

	public static OperatorCredential restore(
			Long userId,
			LoginId loginId,
			PasswordHash passwordHash,
			int failedAttemptCount,
			Instant lockedUntil,
			Instant passwordUpdatedAt,
			boolean passwordChangeRequired,
			Instant lastLoginAt) {
		return new OperatorCredential(userId, loginId, passwordHash, failedAttemptCount, lockedUntil,
				passwordUpdatedAt, passwordChangeRequired, lastLoginAt);
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
		return new OperatorCredential(userId, loginId, passwordHash, nextCount, nextLockedUntil,
				passwordUpdatedAt, passwordChangeRequired, lastLoginAt);
	}

	// 성공하면 실패 기록과 잠금을 모두 지운다.
	public OperatorCredential recordSuccess(Instant succeededAt) {
		requireValue(succeededAt, "succeededAt");
		return new OperatorCredential(
				userId, loginId, passwordHash, 0, null, passwordUpdatedAt, passwordChangeRequired, succeededAt);
	}

	// 운영자가 직접 바꾼 비밀번호다. 변경 필요 상태와 실패 기록, 잠금을 함께 지운다.
	public OperatorCredential changePassword(PasswordHash newPasswordHash, Instant changedAt) {
		requireValue(newPasswordHash, "passwordHash");
		requireValue(changedAt, "changedAt");
		return new OperatorCredential(
				userId, loginId, newPasswordHash, 0, null, changedAt, false, lastLoginAt);
	}

	/**
	 * 비밀번호를 바꿔야 하는지 판정한다(#376). 초기 비밀번호이거나, 마지막 변경 후 maxAge가 지났으면 true다.
	 *
	 * <p>
	 * 마지막 변경 시각 + maxAge가 되는 순간부터 만료다. 세션 절대
	 * 만료(OperatorSessionAbsoluteTimeoutFilter)와 같은 경계 규칙이다.
	 */
	public boolean requiresPasswordChangeAt(Instant now, Duration maxAge) {
		requireValue(now, "now");
		requireValue(maxAge, "maxAge");
		return passwordChangeRequired || !now.isBefore(passwordUpdatedAt.plus(maxAge));
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

	public boolean isPasswordChangeRequired() {
		return passwordChangeRequired;
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
					"failedAttemptCount는 음수일 수 없습니다");
		}
		return failedAttemptCount;
	}

}
