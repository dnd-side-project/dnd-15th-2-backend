package com.dnd.qello.direction.domain;

import java.time.Instant;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

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
		this.status = requireValue(status, "status");
		this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 200);
		if (bodyText != null && bodyText.isBlank()) {
			throw new DirectionException(DirectionErrorCode.INVALID_TEXT, "bodyText", "bodyText는 공백일 수 없습니다");
		}
		this.bodyText = bodyText;
		this.coarseRegionCode = requireText(coarseRegionCode, "coarseRegionCode", 100);
		this.moderationStatus = requireValue(moderationStatus, "moderationStatus");
		this.submittedAt = requireValue(submittedAt, "submittedAt");
		this.publishedAt = publishedAt;
		this.expiresAt = requireValue(expiresAt, "expiresAt");
		if (!expiresAt.isAfter(submittedAt)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_TIME_ORDER, "expiresAt", "expiresAt은 submittedAt보다 늦어야 합니다");
		}
		if (answersReadAt != null && answersReadAt.isBefore(submittedAt)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_TIME_ORDER, "answersReadAt", "answersReadAt은 submittedAt보다 빠를 수 없습니다");
		}
		this.answersReadAt = answersReadAt;
		this.deletedAt = deletedAt;
		if (status == DirectionPostStatus.ACTIVE && publishedAt == null) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_POST_STATE, "publishedAt", "ACTIVE post에는 publishedAt이 필요합니다");
		}
		if ((status == DirectionPostStatus.DELETED) != (deletedAt != null)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_POST_STATE, "deletedAt", "DELETED 상태와 deletedAt이 일치해야 합니다");
		}
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

	/** 질문자가 답변 목록을 읽은 시각을 기록한다. `새로운 답변 n개` 배지는 이 시각 이후 공개된 답변만 센다. */
	public DirectionPost markAnswersRead(Instant at) {
		requireValue(at, "answersReadAt");
		return new DirectionPost(id, senderId, approvedQuestionId, status, idempotencyKey, bodyText,
			coarseRegionCode, moderationStatus, submittedAt, publishedAt, expiresAt, at, deletedAt);
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new DirectionException(
				DirectionErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}
	private static Long validateId(Long value, String field) {
		if (value != null && value <= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_ID, field, field + "는 양수여야 합니다");
		}
		return value;
	}
	private static long requireId(Long value, String field) {
		if (value == null || value <= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_ID, field, field + "는 양수여야 합니다");
		}
		return value;
	}
	private static String requireText(String value, String field, int max) {
		if (value == null || value.isBlank() || value.length() > max) {
			throw new DirectionException(DirectionErrorCode.INVALID_TEXT, field, field + "이 유효하지 않습니다");
		}
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
