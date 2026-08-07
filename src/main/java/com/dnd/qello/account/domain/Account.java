package com.dnd.qello.account.domain;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;

import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;

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
	private final Instant deletedAt;

	private Account(
		Long id,
		AccountRole role,
		AccountStatus status,
		String coarseRegionCode,
		String locale,
		String timezone,
		String nickname,
		Instant deletedAt
	) {
		this.id = validateId(id);
		this.role = requireValue(role, "role");
		this.status = requireValue(status, "status");
		this.coarseRegionCode = requireText(
			coarseRegionCode, "coarseRegionCode", REGION_CODE_MAX_LENGTH);
		this.locale = requireText(locale, "locale", LOCALE_MAX_LENGTH);
		this.timezone = requireTimezone(timezone);
		this.nickname = validateNickname(nickname);
		this.deletedAt = deletedAt;
		validateDeletionState(status, deletedAt);
	}

	/**
	 * 일반 사용자 생성. 비밀번호를 사용하지 않으며 role을 외부에서 지정할 수 없다.
	 */
	public static Account createUser(
		String coarseRegionCode,
		String locale,
		String timezone,
		String nickname
	) {
		return new Account(
			null,
			AccountRole.USER,
			AccountStatus.ACTIVE,
			coarseRegionCode,
			locale,
			timezone,
			nickname,
			null
		);
	}

	/**
	 * 관리자 계정 생성. 일반 가입 유스케이스에서는 호출할 수 없다.
	 *
	 * <p>자격증명은 이 애그리거트에 없다. 운영자 생성은 계정 생성과
	 * {@code operator_credential} 생성 두 단계로 나뉜다. 근거는
	 * {@code docs/adr/0006-split-operator-and-device-authentication.md}에 있다.
	 */
	public static Account createOperator(
		String coarseRegionCode,
		String locale,
		String timezone,
		String nickname
	) {
		return new Account(
			null,
			AccountRole.OPERATOR,
			AccountStatus.ACTIVE,
			coarseRegionCode,
			locale,
			timezone,
			nickname,
			null
		);
	}

	/**
	 * 영속화된 계정을 복원한다. id가 없는 상태는 신규 생성 경로와 구분되어야 하므로 허용하지 않는다.
	 */
	public static Account restore(
		Long id,
		AccountRole role,
		AccountStatus status,
		String coarseRegionCode,
		String locale,
		String timezone,
		String nickname,
		Instant deletedAt
	) {
		if (id == null) {
			throw new AccountException(
				AccountErrorCode.INVALID_ID, "id", "restore는 유효한 기존 id가 필요합니다");
		}
		return new Account(
			id,
			role,
			status,
			coarseRegionCode,
			locale,
			timezone,
			nickname,
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
			deletedAt
		);
	}

	public Account block() {
		if (status == AccountStatus.DELETED) {
			throw new AccountException(
				AccountErrorCode.INVALID_STATUS_TRANSITION, "status", "삭제된 계정은 차단할 수 없습니다");
		}
		return new Account(
			id, role, AccountStatus.BLOCKED, coarseRegionCode, locale, timezone, nickname, deletedAt);
	}

	public Account unblock() {
		if (status != AccountStatus.BLOCKED) {
			throw new AccountException(
				AccountErrorCode.INVALID_STATUS_TRANSITION, "status", "차단 상태인 계정만 차단 해제할 수 있습니다");
		}
		return new Account(
			id, role, AccountStatus.ACTIVE, coarseRegionCode, locale, timezone, nickname, deletedAt);
	}

	public Account delete(Instant deletedAt) {
		requireValue(deletedAt, "deletedAt");
		if (status == AccountStatus.DELETED) {
			throw new AccountException(
				AccountErrorCode.INVALID_STATUS_TRANSITION, "status", "이미 삭제된 계정입니다");
		}
		return new Account(
			id, role, AccountStatus.DELETED, coarseRegionCode, locale, timezone, nickname, deletedAt);
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

	public Instant getDeletedAt() {
		return deletedAt;
	}

	private static Long validateId(Long id) {
		if (id != null && id <= 0) {
			throw new AccountException(AccountErrorCode.INVALID_ID, "id", "id는 양수여야 합니다");
		}
		return id;
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new AccountException(
				AccountErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}

	private static String requireText(String value, String field, int maxLength) {
		if (value == null || value.isBlank()) {
			throw new AccountException(
				AccountErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 비어 있을 수 없습니다");
		}
		if (codePointLength(value) > maxLength) {
			throw new AccountException(
				AccountErrorCode.TEXT_TOO_LONG, field, field + "은 " + maxLength + "자를 초과할 수 없습니다");
		}
		return value;
	}

	private static String requireTimezone(String timezone) {
		String validated = requireText(timezone, "timezone", TIMEZONE_MAX_LENGTH);
		try {
			ZoneId.of(validated);
		} catch (DateTimeException exception) {
			throw new AccountException(
				AccountErrorCode.INVALID_TIMEZONE, "timezone", "timezone은 유효한 IANA ID여야 합니다", exception);
		}
		return validated;
	}

	private static String validateNickname(String nickname) {
		if (nickname == null) {
			return null;
		}
		if (nickname.isBlank()) {
			throw new AccountException(
				AccountErrorCode.REQUIRED_VALUE_MISSING, "nickname", "nickname은 공백일 수 없습니다");
		}
		if (codePointLength(nickname) > NICKNAME_MAX_LENGTH) {
			throw new AccountException(
				AccountErrorCode.TEXT_TOO_LONG,
				"nickname",
				"nickname은 " + NICKNAME_MAX_LENGTH + "자를 초과할 수 없습니다"
			);
		}
		return nickname;
	}

	private static void validateDeletionState(AccountStatus status, Instant deletedAt) {
		boolean deleted = status == AccountStatus.DELETED;
		if (deleted != (deletedAt != null)) {
			throw new AccountException(
				AccountErrorCode.INVALID_DELETION_STATE, "deletedAt", "DELETED 상태와 deletedAt은 함께 설정되어야 합니다");
		}
	}

	private static int codePointLength(String value) {
		return value.codePointCount(0, value.length());
	}

}
