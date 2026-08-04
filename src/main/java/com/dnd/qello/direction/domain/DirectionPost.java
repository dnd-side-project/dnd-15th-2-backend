package com.dnd.qello.direction.domain;

import java.time.Instant;
import java.util.Objects;

public final class DirectionPost {

	private final Long id;
	private final Long senderId;
	private final Long approvedQuestionId;
	private final DirectionPostStatus status;
	private final String idempotencyKey;
	private final String bodyText;
	private final String coarseRegionCode;
	private final DirectionPostModerationStatus moderationStatus;
	private final Instant submittedAt;
	private final Instant publishedAt;
	private final Instant expiresAt;
	private final Instant answersReadAt;
	private final Instant deletedAt;

	private DirectionPost(Long id, Long senderId, Long approvedQuestionId, DirectionPostStatus status,
		String idempotencyKey, String bodyText, String coarseRegionCode,
		DirectionPostModerationStatus moderationStatus, Instant submittedAt, Instant publishedAt,
		Instant expiresAt, Instant answersReadAt, Instant deletedAt) {
		this.id = validateId(id, "id");
		this.senderId = requireId(senderId, "senderId");
		this.approvedQuestionId = requireId(approvedQuestionId, "approvedQuestionId");
		this.status = Objects.requireNonNull(status, "status는 필수입니다");
		this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 200);
		if (bodyText != null && bodyText.isBlank()) throw new IllegalArgumentException("bodyText는 공백일 수 없습니다");
		this.bodyText = bodyText;
		this.coarseRegionCode = requireText(coarseRegionCode, "coarseRegionCode", 100);
		this.moderationStatus = Objects.requireNonNull(moderationStatus, "moderationStatus는 필수입니다");
		this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt은 필수입니다");
		this.publishedAt = publishedAt;
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt은 필수입니다");
		if (!expiresAt.isAfter(submittedAt)) throw new IllegalArgumentException("expiresAt은 submittedAt보다 늦어야 합니다");
		if (answersReadAt != null && answersReadAt.isBefore(submittedAt)) throw new IllegalArgumentException("answersReadAt은 submittedAt보다 빠를 수 없습니다");
		this.answersReadAt = answersReadAt;
		this.deletedAt = deletedAt;
		if (status == DirectionPostStatus.ACTIVE && publishedAt == null) throw new IllegalArgumentException("ACTIVE post에는 publishedAt이 필요합니다");
		if ((status == DirectionPostStatus.DELETED) != (deletedAt != null)) throw new IllegalArgumentException("DELETED 상태와 deletedAt이 일치해야 합니다");
	}

	public static DirectionPost submit(Long senderId, Long approvedQuestionId, String idempotencyKey,
		String bodyText, String coarseRegionCode, Instant submittedAt, Instant expiresAt) {
		return new DirectionPost(null, senderId, approvedQuestionId, DirectionPostStatus.MATCHING,
			idempotencyKey, bodyText, coarseRegionCode, DirectionPostModerationStatus.PENDING,
			submittedAt, null, expiresAt, null, null);
	}

	public static DirectionPost restore(Long id, Long senderId, Long approvedQuestionId,
		DirectionPostStatus status, String idempotencyKey, String bodyText, String coarseRegionCode,
		DirectionPostModerationStatus moderationStatus, Instant submittedAt, Instant publishedAt,
		Instant expiresAt, Instant answersReadAt, Instant deletedAt) {
		return new DirectionPost(id, senderId, approvedQuestionId, status, idempotencyKey, bodyText,
			coarseRegionCode, moderationStatus, submittedAt, publishedAt, expiresAt, answersReadAt, deletedAt);
	}

	private static Long validateId(Long value, String field) {
		if (value != null && value <= 0) throw new IllegalArgumentException(field + "는 양수여야 합니다");
		return value;
	}
	private static long requireId(Long value, String field) {
		if (value == null || value <= 0) throw new IllegalArgumentException(field + "는 양수여야 합니다");
		return value;
	}
	private static String requireText(String value, String field, int max) {
		if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(field + "이 유효하지 않습니다");
		return value;
	}

	public Long getId() { return id; }
	public Long getSenderId() { return senderId; }
	public Long getApprovedQuestionId() { return approvedQuestionId; }
	public DirectionPostStatus getStatus() { return status; }
	public String getIdempotencyKey() { return idempotencyKey; }
	public String getBodyText() { return bodyText; }
	public String getCoarseRegionCode() { return coarseRegionCode; }
	public DirectionPostModerationStatus getModerationStatus() { return moderationStatus; }
	public Instant getSubmittedAt() { return submittedAt; }
	public Instant getPublishedAt() { return publishedAt; }
	public Instant getExpiresAt() { return expiresAt; }
	public Instant getAnswersReadAt() { return answersReadAt; }
	public Instant getDeletedAt() { return deletedAt; }
}
