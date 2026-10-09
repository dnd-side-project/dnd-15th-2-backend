/*
 * Created at: 2026-08-07T20:52:09+09:00
 * Source scenario: TEST-PLAN-GH-73-DEVICE-CREDENTIAL-UNIT-001 through UNIT-006
 * Source scenario: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-008 (added 2026-10-09T18:14:46+09:00)
 */
package com.dnd.qello.auth.domain;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceCredentialTest {

	private static final Instant NOW = Instant.parse("2026-08-07T09:00:00Z");
	private static final SecretHash HASH = new SecretHash(
			"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

	@Test
	@DisplayName("발급 직후에는 ACTIVE 상태이고 revokedAt이 없다")
	void issuesActiveCredential() {
		DeviceCredential credential = issued();

		assertThat(credential.getStatus()).isEqualTo(CredentialStatus.ACTIVE);
		assertThat(credential.isActive()).isTrue();
		assertThat(credential.getRevokedAt()).isNull();
		assertThat(credential.getCreatedAt()).isEqualTo(NOW);
		assertThat(credential.getLastUsedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("touch는 마지막 사용 시각만 바꾼다")
	void touchUpdatesOnlyLastUsedAt() {
		DeviceCredential credential = issued();
		Instant usedAt = NOW.plusSeconds(3600);

		DeviceCredential touched = credential.touch(usedAt);

		assertThat(touched.getLastUsedAt()).isEqualTo(usedAt);
		assertThat(touched.getCreatedAt()).isEqualTo(credential.getCreatedAt());
		assertThat(touched.getInstallationId()).isEqualTo(credential.getInstallationId());
		assertThat(touched.getSecretHash()).isEqualTo(credential.getSecretHash());
	}

	@Test
	@DisplayName("빈 installationId는 거절한다")
	void rejectsBlankInstallationId() {
		assertThatThrownBy(() -> DeviceCredential.issue(1L, " ", HASH, DevicePlatform.IOS, NOW))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_INSTALLATION_ID);
	}

	@Test
	@DisplayName("64자를 초과하는 installationId는 거절한다")
	void rejectsTooLongInstallationId() {
		String tooLong = "a".repeat(65);

		assertThatThrownBy(() -> DeviceCredential.issue(1L, tooLong, HASH, DevicePlatform.IOS, NOW))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_INSTALLATION_ID);
	}

	@Test
	@DisplayName("REVOKED 상태인데 revokedAt이 없으면 복원을 거절한다")
	void rejectsRevokedStateWithoutRevokedAt() {
		assertThatThrownBy(() -> DeviceCredential.restore(
				1L, 1L, "device-a", HASH, DevicePlatform.IOS, CredentialStatus.REVOKED, NOW, NOW, null))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIAL_STATE);
	}

	@Test
	@DisplayName("id 없는 복원은 거절한다")
	void restoreRequiresId() {
		assertThatThrownBy(() -> DeviceCredential.restore(
				null, 1L, "device-a", HASH, DevicePlatform.IOS, CredentialStatus.ACTIVE, NOW, NOW, null))
				.isInstanceOf(AuthException.class);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-008: ACTIVE 자격증명 폐기는 REVOKED와 폐기 시각만 바꾼다")
	void revokeMovesActiveCredentialToRevoked() {
		DeviceCredential credential = DeviceCredential.restore(
				9L, 1L, "device-a", HASH, DevicePlatform.IOS, CredentialStatus.ACTIVE, NOW.plusSeconds(60), NOW, null);
		Instant revokedAt = NOW.plusSeconds(3600);

		DeviceCredential revoked = credential.revoke(revokedAt);

		assertThat(revoked.getStatus()).isEqualTo(CredentialStatus.REVOKED);
		assertThat(revoked.isActive()).isFalse();
		assertThat(revoked.getRevokedAt()).isEqualTo(revokedAt);
		assertThat(revoked.getId()).isEqualTo(9L);
		assertThat(revoked.getUserId()).isEqualTo(1L);
		assertThat(revoked.getInstallationId()).isEqualTo("device-a");
		assertThat(revoked.getSecretHash()).isEqualTo(HASH);
		assertThat(revoked.getLastUsedAt()).isEqualTo(NOW.plusSeconds(60));
		assertThat(revoked.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-008: 이미 REVOKED인 자격증명을 다시 폐기해도 처음 폐기 시각을 유지한다")
	void revokeKeepsFirstRevokedAtForRevokedCredential() {
		Instant firstRevokedAt = NOW.plusSeconds(60);
		DeviceCredential credential = DeviceCredential.restore(
				9L, 1L, "device-a", HASH, DevicePlatform.IOS, CredentialStatus.REVOKED, NOW, NOW, firstRevokedAt);

		DeviceCredential again = credential.revoke(NOW.plusSeconds(7200));

		assertThat(again.getStatus()).isEqualTo(CredentialStatus.REVOKED);
		assertThat(again.getRevokedAt()).isEqualTo(firstRevokedAt);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-008: 폐기 시각이 없으면 거절한다")
	void revokeRequiresRevokedAt() {
		assertThatThrownBy(() -> issued().revoke(null))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.REQUIRED_VALUE_MISSING);
	}

	private DeviceCredential issued() {
		return DeviceCredential.issue(1L, "device-a", HASH, DevicePlatform.IOS, NOW);
	}

}
