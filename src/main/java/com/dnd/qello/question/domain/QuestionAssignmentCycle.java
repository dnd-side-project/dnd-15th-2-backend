package com.dnd.qello.question.domain;

import java.time.Instant;
import java.util.Objects;

public final class QuestionAssignmentCycle {

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
		this.status = Objects.requireNonNull(status, "status는 필수입니다");
		this.startsAt = Objects.requireNonNull(startsAt, "startsAt은 필수입니다");
		this.endsAt = Objects.requireNonNull(endsAt, "endsAt은 필수입니다");
		this.createdAt = createdAt;
		if (!endsAt.isAfter(startsAt)) throw new IllegalArgumentException("endsAt은 startsAt보다 늦어야 합니다");
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

	private static Long validateId(Long value, String field) {
		if (value != null && value <= 0) throw new IllegalArgumentException(field + "는 양수여야 합니다");
		return value;
	}

	private static long requireId(Long value, String field) {
		if (value == null || value <= 0) throw new IllegalArgumentException(field + "는 양수여야 합니다");
		return value;
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "은 비어 있을 수 없습니다");
		if (value.length() > 100) throw new IllegalArgumentException(field + "이 너무 깁니다");
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
