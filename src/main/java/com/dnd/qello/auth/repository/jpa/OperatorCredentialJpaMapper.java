package com.dnd.qello.auth.repository.jpa;

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.domain.OperatorCredential;
import com.dnd.qello.auth.security.PasswordHash;

final class OperatorCredentialJpaMapper {

	private OperatorCredentialJpaMapper() {
	}

	static OperatorCredentialJpaEntity toNewEntity(OperatorCredential credential) {
		return new OperatorCredentialJpaEntity(
				credential.getUserId(),
				credential.getLoginId().value(),
				credential.getPasswordHash().value(),
				toShort(credential.getFailedAttemptCount()),
				credential.getLockedUntil(),
				credential.getPasswordUpdatedAt(),
				credential.isPasswordChangeRequired(),
				credential.getLastLoginAt());
	}

	static OperatorCredential toDomain(OperatorCredentialJpaEntity entity) {
		return OperatorCredential.restore(
				entity.getUserId(),
				new LoginId(entity.getLoginId()),
				new PasswordHash(entity.getPasswordHash()),
				entity.getFailedAttemptCount(),
				entity.getLockedUntil(),
				entity.getPasswordUpdatedAt(),
				entity.isPasswordChangeRequired(),
				entity.getLastLoginAt());
	}

	/**
	 * 관리 상태 엔티티를 변경하고 Dirty Checking에 위임한다.
	 */
	static void updateLoginState(OperatorCredentialJpaEntity entity, OperatorCredential credential) {
		entity.updateLoginState(
				toShort(credential.getFailedAttemptCount()),
				credential.getLockedUntil(),
				credential.getLastLoginAt());
	}

	static void updatePassword(OperatorCredentialJpaEntity entity, OperatorCredential credential) {
		entity.updatePassword(
				credential.getPasswordHash().value(),
				credential.getPasswordUpdatedAt(),
				credential.isPasswordChangeRequired(),
				toShort(credential.getFailedAttemptCount()),
				credential.getLockedUntil());
	}

	private static short toShort(int value) {
		return (short) value;
	}

}
