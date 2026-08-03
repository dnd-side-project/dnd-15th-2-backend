package com.dnd.qello.account.domain;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public final class Account {

	private static final int REGION_CODE_MAX_LENGTH = 100;
	private static final int LOCALE_MAX_LENGTH = 35;
	private static final int TIMEZONE_MAX_LENGTH = 64;
	private static final int NICKNAME_MAX_LENGTH = 50;

	private final Long id;
	private final AccountRole role;
	private final AccountStatus status;
	private final String coarseRegionCode;
	private final String locale;
	private final String timezone;
	private final String nickname;
	private final Instant createdAt;
	private final Instant updatedAt;
	private final Instant deletedAt;

	private Account(
		Long id,
		AccountRole role,
		AccountStatus status,
		String coarseRegionCode,
		String locale,
		String timezone,
		String nickname,
		Instant createdAt,
		Instant updatedAt,
		Instant deletedAt
	) {
		this.id = validateId(id);
		this.role = Objects.requireNonNull(role, "role은 필수입니다");
		this.status = Objects.requireNonNull(status, "status는 필수입니다");
		this.coarseRegionCode = requireText(
			coarseRegionCode, "coarseRegionCode", REGION_CODE_MAX_LENGTH);
		this.locale = requireText(locale, "locale", LOCALE_MAX_LENGTH);
		this.timezone = requireTimezone(timezone);
		this.nickname = validateNickname(nickname);
		validateAuditTimestamps(createdAt, updatedAt);
		validateDeletionState(status, deletedAt);
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.deletedAt = deletedAt;
	}

	public static Account create(
		AccountRole role,
		String coarseRegionCode,
		String locale,
		String timezone,
		String nickname
	) {
		return new Account(
			null,
			role,
			AccountStatus.ACTIVE,
			coarseRegionCode,
			locale,
			timezone,
			nickname,
			null,
			null,
			null
		);
	}

	public static Account restore(
		Long id,
		AccountRole role,
		AccountStatus status,
		String coarseRegionCode,
		String locale,
		String timezone,
		String nickname,
		Instant createdAt,
		Instant updatedAt,
		Instant deletedAt
	) {
		return new Account(
			id,
			role,
			status,
			coarseRegionCode,
			locale,
			timezone,
			nickname,
			createdAt,
			updatedAt,
			deletedAt
		);
	}

	public Account updateProfile(
		String coarseRegionCode,
		String locale,
		String timezone,
		String nickname
	) {
		return new Account(
			id,
			role,
			status,
			coarseRegionCode,
			locale,
			timezone,
			nickname,
			createdAt,
			updatedAt,
			deletedAt
		);
	}

	public Account changeStatus(AccountStatus status, Instant deletedAt) {
		return new Account(
			id,
			role,
			status,
			coarseRegionCode,
			locale,
			timezone,
			nickname,
			createdAt,
			updatedAt,
			deletedAt
		);
	}

	public Long getId() {
		return id;
	}

	public AccountRole getRole() {
		return role;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public String getCoarseRegionCode() {
		return coarseRegionCode;
	}

	public String getLocale() {
		return locale;
	}

	public String getTimezone() {
		return timezone;
	}

	public String getNickname() {
		return nickname;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	private static Long validateId(Long id) {
		if (id != null && id <= 0) {
			throw new IllegalArgumentException("id는 양수여야 합니다");
		}
		return id;
	}

	private static String requireText(String value, String field, int maxLength) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + "은 비어 있을 수 없습니다");
		}
		if (codePointLength(value) > maxLength) {
			throw new IllegalArgumentException(
				field + "은 " + maxLength + "자를 초과할 수 없습니다");
		}
		return value;
	}

	private static String requireTimezone(String timezone) {
		String validated = requireText(timezone, "timezone", TIMEZONE_MAX_LENGTH);
		try {
			ZoneId.of(validated);
		} catch (DateTimeException exception) {
			throw new IllegalArgumentException("timezone은 유효한 IANA ID여야 합니다", exception);
		}
		return validated;
	}

	private static String validateNickname(String nickname) {
		if (nickname == null) {
			return null;
		}
		if (nickname.isBlank()) {
			throw new IllegalArgumentException("nickname은 공백일 수 없습니다");
		}
		if (codePointLength(nickname) > NICKNAME_MAX_LENGTH) {
			throw new IllegalArgumentException(
				"nickname은 " + NICKNAME_MAX_LENGTH + "자를 초과할 수 없습니다");
		}
		return nickname;
	}

	private static void validateAuditTimestamps(Instant createdAt, Instant updatedAt) {
		if ((createdAt == null) != (updatedAt == null)) {
			throw new IllegalArgumentException(
				"createdAt과 updatedAt은 함께 존재하거나 함께 비어 있어야 합니다");
		}
		if (createdAt != null && updatedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException("updatedAt은 createdAt보다 빠를 수 없습니다");
		}
	}

	private static void validateDeletionState(AccountStatus status, Instant deletedAt) {
		boolean deleted = status == AccountStatus.DELETED;
		if (deleted != (deletedAt != null)) {
			throw new IllegalArgumentException(
				"DELETED 상태와 deletedAt은 함께 설정되어야 합니다");
		}
	}

	private static int codePointLength(String value) {
		return value.codePointCount(0, value.length());
	}

}
