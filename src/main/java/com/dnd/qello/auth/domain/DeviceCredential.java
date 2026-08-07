package com.dnd.qello.auth.domain;

import java.time.Instant;

import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;

// 앱 사용자 기기 자격증명.
//
// 계정(Account)과 분리된 애그리거트다. push_device와 물리적으로 같은 기기를 가리키지만
// 생명주기가 독립적이다. 푸시 토큰은 FCM/APNs가 소유하고 수시로 갱신·무효화되는 반면,
// 이 자격증명은 우리가 발급하고 우리만 해지하는 인증 값이다. 근거는
// docs/product/AUTH_DESIGN.md 3.3절과 docs/adr/0006-split-operator-and-device-authentication.md에 있다.
public final class DeviceCredential {

	private static final int INSTALLATION_ID_MAX_LENGTH = 64;

	private final Long id;
	private final long userId;
	private final String installationId;
	private final SecretHash secretHash;
	private final DevicePlatform platform;
	private final CredentialStatus status;
	private final Instant lastUsedAt;
	private final Instant createdAt;
	private final Instant revokedAt;

	private DeviceCredential(
		Long id,
		long userId,
		String installationId,
		SecretHash secretHash,
		DevicePlatform platform,
		CredentialStatus status,
		Instant lastUsedAt,
		Instant createdAt,
		Instant revokedAt
	) {
		this.id = id;
		this.userId = requireUserId(userId);
		this.installationId = requireInstallationId(installationId);
		this.secretHash = requireValue(secretHash, "secretHash");
		this.platform = requireValue(platform, "platform");
		this.status = requireValue(status, "status");
		this.lastUsedAt = requireValue(lastUsedAt, "lastUsedAt");
		this.createdAt = requireValue(createdAt, "createdAt");
		this.revokedAt = revokedAt;
		validateRevocationState(status, revokedAt);
	}

	/**
	 * 신규 등록. status는 항상 ACTIVE로 시작한다.
	 */
	public static DeviceCredential issue(
		long userId,
		String installationId,
		SecretHash secretHash,
		DevicePlatform platform,
		Instant issuedAt
	) {
		return new DeviceCredential(
			null, userId, installationId, secretHash, platform,
			CredentialStatus.ACTIVE, issuedAt, issuedAt, null);
	}

	public static DeviceCredential restore(
		Long id,
		long userId,
		String installationId,
		SecretHash secretHash,
		DevicePlatform platform,
		CredentialStatus status,
		Instant lastUsedAt,
		Instant createdAt,
		Instant revokedAt
	) {
		if (id == null) {
			throw new AuthException(
				AuthErrorCode.INVALID_CREDENTIAL_STATE, "id", "restore는 유효한 기존 id가 필요합니다");
		}
		return new DeviceCredential(
			id, userId, installationId, secretHash, platform, status, lastUsedAt, createdAt, revokedAt);
	}

	public boolean isActive() {
		return status == CredentialStatus.ACTIVE;
	}

	/**
	 * 토큰 재발급 성공 시 마지막 사용 시각만 갱신한다.
	 */
	public DeviceCredential touch(Instant usedAt) {
		requireValue(usedAt, "usedAt");
		return new DeviceCredential(
			id, userId, installationId, secretHash, platform, status, usedAt, createdAt, revokedAt);
	}

	public Long getId() {
		return id;
	}

	public long getUserId() {
		return userId;
	}

	public String getInstallationId() {
		return installationId;
	}

	public SecretHash getSecretHash() {
		return secretHash;
	}

	public DevicePlatform getPlatform() {
		return platform;
	}

	public CredentialStatus getStatus() {
		return status;
	}

	public Instant getLastUsedAt() {
		return lastUsedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	private static long requireUserId(long userId) {
		if (userId <= 0) {
			throw new AuthException(
				AuthErrorCode.REQUIRED_VALUE_MISSING, "userId", "userId는 양수여야 합니다");
		}
		return userId;
	}

	private static String requireInstallationId(String installationId) {
		if (installationId == null || installationId.isBlank()) {
			throw new AuthException(
				AuthErrorCode.INVALID_INSTALLATION_ID, "installationId", "installationId는 비어 있을 수 없습니다");
		}
		if (installationId.length() > INSTALLATION_ID_MAX_LENGTH) {
			throw new AuthException(
				AuthErrorCode.INVALID_INSTALLATION_ID,
				"installationId",
				"installationId는 " + INSTALLATION_ID_MAX_LENGTH + "자를 초과할 수 없습니다"
			);
		}
		return installationId;
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new AuthException(
				AuthErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}

	private static void validateRevocationState(CredentialStatus status, Instant revokedAt) {
		boolean revoked = status == CredentialStatus.REVOKED;
		if (revoked != (revokedAt != null)) {
			throw new AuthException(
				AuthErrorCode.INVALID_CREDENTIAL_STATE,
				"revokedAt",
				"REVOKED와 revokedAt은 함께 존재해야 합니다"
			);
		}
	}

}
