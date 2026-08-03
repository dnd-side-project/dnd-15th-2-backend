package com.dnd.qello.question.domain;

import java.time.Instant;

public final class QuestionAssignment {

	private final Long id;
	private final Long cycleId;
	private final Long approvedQuestionId;
	private final int displayOrder;
	private final Instant assignedAt;
	private final Instant firstViewedAt;
	private final Instant usedAt;

	private QuestionAssignment(
		Long id, Long cycleId, Long approvedQuestionId, int displayOrder,
		Instant assignedAt, Instant firstViewedAt, Instant usedAt
	) {
		this.id = validateId(id, "id");
		this.cycleId = requireId(cycleId, "cycleId");
		this.approvedQuestionId = requireId(approvedQuestionId, "approvedQuestionId");
		if (displayOrder <= 0) throw new IllegalArgumentException("displayOrder는 양수여야 합니다");
		this.displayOrder = displayOrder;
		this.assignedAt = java.util.Objects.requireNonNull(assignedAt, "assignedAt은 필수입니다");
		this.firstViewedAt = firstViewedAt;
		this.usedAt = usedAt;
		validateTimestamp(firstViewedAt, "firstViewedAt");
		validateTimestamp(usedAt, "usedAt");
	}

	public static QuestionAssignment create(Long cycleId, Long approvedQuestionId, int displayOrder, Instant assignedAt) {
		return new QuestionAssignment(null, cycleId, approvedQuestionId, displayOrder, assignedAt, null, null);
	}

	public static QuestionAssignment restore(
		Long id, Long cycleId, Long approvedQuestionId, int displayOrder,
		Instant assignedAt, Instant firstViewedAt, Instant usedAt
	) {
		return new QuestionAssignment(id, cycleId, approvedQuestionId, displayOrder,
			assignedAt, firstViewedAt, usedAt);
	}

	private void validateTimestamp(Instant value, String field) {
		if (value != null && value.isBefore(assignedAt)) {
			throw new IllegalArgumentException(field + "은 assignedAt보다 빠를 수 없습니다");
		}
	}

	private static Long validateId(Long value, String field) {
		if (value != null && value <= 0) throw new IllegalArgumentException(field + "는 양수여야 합니다");
		return value;
	}

	private static long requireId(Long value, String field) {
		if (value == null || value <= 0) throw new IllegalArgumentException(field + "는 양수여야 합니다");
		return value;
	}

	public Long getId() { return id; }
	public Long getCycleId() { return cycleId; }
	public Long getApprovedQuestionId() { return approvedQuestionId; }
	public int getDisplayOrder() { return displayOrder; }
	public Instant getAssignedAt() { return assignedAt; }
	public Instant getFirstViewedAt() { return firstViewedAt; }
	public Instant getUsedAt() { return usedAt; }
}
