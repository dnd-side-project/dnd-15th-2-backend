package com.dnd.qello.auth.repository.jpa;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dnd.qello.auth.domain.CredentialStatus;
import com.dnd.qello.auth.domain.DevicePlatform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// device_credential은 updated_at 컬럼이 없다(V7). JpaAuditableEntity는 created_at과
// updated_at을 짝으로 요구하므로 상속하지 않고 created_at을 애플리케이션이 직접 관리한다.
@Entity
@Table(name = "device_credential")
@Getter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceCredentialJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "installation_id", nullable = false, length = 64)
	private String installationId;

	// V7은 secret_hash를 고정 길이 CHAR(64)로 선언한다. columnDefinition은 DDL 생성에만
	// 쓰이고 startup validate는 매핑된 JDBC 타입으로 비교하므로, String 기본 매핑(VARCHAR)이
	// 아니라 JdbcTypeCode로 CHAR를 명시해야 bpchar와 일치한다.
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "secret_hash", nullable = false, length = 64)
	private String secretHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "platform", nullable = false, length = 20)
	private DevicePlatform platform;

	@Enumerated(EnumType.STRING)
	@Column(name = "credential_status", nullable = false, length = 20)
	private CredentialStatus status;

	@Column(name = "last_used_at", nullable = false)
	private Instant lastUsedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	DeviceCredentialJpaEntity(
		Long userId,
		String installationId,
		String secretHash,
		DevicePlatform platform,
		CredentialStatus status,
		Instant lastUsedAt,
		Instant createdAt,
		Instant revokedAt
	) {
		this.userId = userId;
		this.installationId = installationId;
		this.secretHash = secretHash;
		this.platform = platform;
		this.status = status;
		this.lastUsedAt = lastUsedAt;
		this.createdAt = createdAt;
		this.revokedAt = revokedAt;
	}

	void updateLastUsedAt(Instant lastUsedAt) {
		this.lastUsedAt = lastUsedAt;
	}

}
