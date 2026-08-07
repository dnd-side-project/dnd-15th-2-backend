package com.dnd.qello.account.repository.jpa;

import java.time.Instant;

import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.common.persistence.JpaAuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_account")
@Getter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountJpaEntity extends JpaAuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private AccountRole role;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private AccountStatus status;

	@Column(name = "coarse_region_code", nullable = false, length = 100)
	private String coarseRegionCode;

	@Column(name = "locale", nullable = false, length = 35)
	private String locale;

	@Column(name = "timezone", nullable = false, length = 64)
	private String timezone;

	//TODO(#48): Nickname Null 허용 유무 논의
	@Column(name = "nickname", length = 50)
	private String nickname;

	@Column(name = "password_hash", length = 255)
	private String passwordHash;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	// 같은 계정을 동시에 수정한 요청 중 뒤늦은 쪽을 거절한다. 값은 Hibernate가 관리하므로
	// 도메인이나 Mapper가 읽거나 쓰지 않는다.
	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	AccountJpaEntity(
		AccountRole role,
		AccountStatus status,
		String coarseRegionCode,
		String locale,
		String timezone,
		String nickname,
		String passwordHash
	) {
		this.role = role;
		this.status = status;
		this.coarseRegionCode = coarseRegionCode;
		this.locale = locale;
		this.timezone = timezone;
		//TODO(#48): Nickname Null 허용 유무 논의
		this.nickname = nickname;
		this.passwordHash = passwordHash;
	}

	void updateProfile(String coarseRegionCode, String locale, String timezone, String nickname) {
		this.coarseRegionCode = coarseRegionCode;
		this.locale = locale;
		this.timezone = timezone;
		this.nickname = nickname;
	}

	void updateStatus(AccountStatus status, Instant deletedAt) {
		this.status = status;
		this.deletedAt = deletedAt;
	}

}
