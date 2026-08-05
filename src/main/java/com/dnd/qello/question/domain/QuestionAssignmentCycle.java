package com.dnd.qello.question.domain;

import java.time.Instant;

import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;

public final class QuestionAssignmentCycle {

	private static final int TEXT_MAX_LENGTH = 100;

	private final Long id;
	private final Long userId;
	private final String cycleKey;
	private final String poolVersion;
	private final QuestionAssignmentCycleStatus status;
	private final Instant startsAt;
	private final Instant endsAt;
	private final Instant createdAt;

	private QuestionAssignmentCycle(
		Long id, Long userId, String cycleKey, String poolVersion,
		QuestionAssignmentCycleStatus status, Instant startsAt, Instant endsAt,
		Instant createdAt
	) {
		this.id = validateId(id, "id");
		this.userId = requireId(userId, "userId");
		this.cycleKey = requireText(cycleKey, "cycleKey");
		this.poolVersion = requireText(poolVersion, "poolVersion");
		this.status = requireValue(status, "status");
		this.startsAt = requireValue(startsAt, "startsAt");
		this.endsAt = requireValue(endsAt, "endsAt");
		this.createdAt = createdAt;
		if (!endsAt.isAfter(startsAt)) {
			throw new QuestionException(
				QuestionErrorCode.INVALID_TIME_ORDER, "endsAt", "endsAt은 startsAt보다 늦어야 합니다");
		}
	}

	public static QuestionAssignmentCycle create(
		Long userId, String cycleKey, String poolVersion, Instant startsAt, Instant endsAt
	) {
		return new QuestionAssignmentCycle(null, userId, cycleKey, poolVersion,
			QuestionAssignmentCycleStatus.ACTIVE, startsAt, endsAt, null);
	}

	public static QuestionAssignmentCycle restore(
		Long id, Long userId, String cycleKey, String poolVersion,
		QuestionAssignmentCycleStatus status, Instant startsAt, Instant endsAt,
		Instant createdAt
	) {
		return new QuestionAssignmentCycle(id, userId, cycleKey, poolVersion, status,
			startsAt, endsAt, createdAt);
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new QuestionException(QuestionErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}

	private static Long validateId(Long value, String field) {
		if (value != null && value <= 0) {
			throw new QuestionException(QuestionErrorCode.INVALID_ID, field, field + "는 양수여야 합니다");
		}
		return value;
	}

	private static long requireId(Long value, String field) {
		if (value == null || value <= 0) {
			throw new QuestionException(QuestionErrorCode.INVALID_ID, field, field + "는 양수여야 합니다");
		}
		return value;
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new QuestionException(
				QuestionErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 비어 있을 수 없습니다");
		}
		if (value.length() > TEXT_MAX_LENGTH) {
			throw new QuestionException(
				QuestionErrorCode.TEXT_TOO_LONG, field, field + "은 " + TEXT_MAX_LENGTH + "자를 초과할 수 없습니다");
		}
		return value;
	}

	public Long getId() { return id; }
	public Long getUserId() { return userId; }
	public String getCycleKey() { return cycleKey; }
	public String getPoolVersion() { return poolVersion; }
	public QuestionAssignmentCycleStatus getStatus() { return status; }
	public Instant getStartsAt() { return startsAt; }
	public Instant getEndsAt() { return endsAt; }
	public Instant getCreatedAt() { return createdAt; }
}
