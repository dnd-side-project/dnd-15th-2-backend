package com.dnd.qello.auth.repository.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.dnd.qello.common.persistence.JpaAuditableEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "operator_credential")
@Getter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatorCredentialJpaEntity extends JpaAuditableEntity {

	// user_account.id를 그대로 쓴다. 자격증명은 계정당 하나뿐이다.
	@Id
	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "login_id", nullable = false, length = 50)
	private String loginId;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(name = "failed_attempt_count", nullable = false)
	private short failedAttemptCount;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "password_updated_at", nullable = false)
	private Instant passwordUpdatedAt;

	@Column(name = "password_change_required", nullable = false)
	private boolean passwordChangeRequired;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	OperatorCredentialJpaEntity(
			Long userId,
			String loginId,
			String passwordHash,
			short failedAttemptCount,
			Instant lockedUntil,
			Instant passwordUpdatedAt,
			boolean passwordChangeRequired,
			Instant lastLoginAt) {
		this.userId = userId;
		this.loginId = loginId;
		this.passwordHash = passwordHash;
		this.failedAttemptCount = failedAttemptCount;
		this.lockedUntil = lockedUntil;
		this.passwordUpdatedAt = passwordUpdatedAt;
		this.passwordChangeRequired = passwordChangeRequired;
		this.lastLoginAt = lastLoginAt;
	}

	// role 컬럼은 매핑하지 않는다. DEFAULT 'OPERATOR'로 채워지고 CHECK가 값을 고정하므로
	// 애플리케이션이 쓸 일이 없다. 매핑하면 실수로 바꿀 수 있는 통로만 생긴다.

	void updateLoginState(
			short failedAttemptCount,
			Instant lockedUntil,
			Instant lastLoginAt) {
		this.failedAttemptCount = failedAttemptCount;
		this.lockedUntil = lockedUntil;
		this.lastLoginAt = lastLoginAt;
	}

	// 비밀번호를 바꾸면 실패 기록과 잠금도 함께 지운다(OperatorCredential.changePassword).
	// 다섯 값이 한 UPDATE로 나가야 새 비밀번호에 이전 잠금이 남지 않는다.
	void updatePassword(
			String passwordHash,
			Instant passwordUpdatedAt,
			boolean passwordChangeRequired,
			short failedAttemptCount,
			Instant lockedUntil) {
		this.passwordHash = passwordHash;
		this.passwordUpdatedAt = passwordUpdatedAt;
		this.passwordChangeRequired = passwordChangeRequired;
		this.failedAttemptCount = failedAttemptCount;
		this.lockedUntil = lockedUntil;
	}

}
