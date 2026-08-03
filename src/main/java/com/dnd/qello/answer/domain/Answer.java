package com.dnd.qello.answer.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class Answer {

	private static final int REGION_MAX_LENGTH = 100;
	private static final int DISTANCE_BAND_MAX_LENGTH = 50;
	private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 200;

	private final Long id;
	private final long postRecipientId;
	private final long authorId;
	private final AnswerStatus status;
	private final String idempotencyKey;
	private final String bodyText;
	private final String coarseRegionCode;
	private final BigDecimal bearingFromSenderDegrees;
	private final String distanceBand;
	private final AnswerModerationStatus moderationStatus;
	private final Instant submittedAt;
	private final Instant publishedAt;
	private final Instant deletedAt;

	private Answer(Long id, long postRecipientId, long authorId, AnswerStatus status,
		String idempotencyKey, String bodyText, String coarseRegionCode,
		BigDecimal bearingFromSenderDegrees, String distanceBand,
		AnswerModerationStatus moderationStatus, Instant submittedAt,
		Instant publishedAt, Instant deletedAt) {
		this.id = validatePositiveOrNull(id, "id");
		this.postRecipientId = requirePositive(postRecipientId, "postRecipientId");
		this.authorId = requirePositive(authorId, "authorId");
		this.status = Objects.requireNonNull(status, "status는 필수입니다");
		this.idempotencyKey = requiredText(idempotencyKey, "idempotencyKey", IDEMPOTENCY_KEY_MAX_LENGTH);
		this.bodyText = optionalText(bodyText, "bodyText");
		this.coarseRegionCode = requiredText(coarseRegionCode, "coarseRegionCode", REGION_MAX_LENGTH);
		this.bearingFromSenderDegrees = requireBearing(bearingFromSenderDegrees);
		this.distanceBand = requiredText(distanceBand, "distanceBand", DISTANCE_BAND_MAX_LENGTH);
		this.moderationStatus = Objects.requireNonNull(moderationStatus, "moderationStatus는 필수입니다");
		this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt은 필수입니다");
		this.publishedAt = publishedAt;
		this.deletedAt = deletedAt;
		validateState();
	}

	public static Answer submit(long postRecipientId, long authorId, String idempotencyKey,
		String bodyText, String coarseRegionCode, BigDecimal bearingFromSenderDegrees,
		String distanceBand, Instant submittedAt) {
		return new Answer(null, postRecipientId, authorId, AnswerStatus.SUBMITTED, idempotencyKey,
			bodyText, coarseRegionCode, bearingFromSenderDegrees, distanceBand,
			AnswerModerationStatus.PENDING, submittedAt, null, null);
	}

	public static Answer restore(Long id, long postRecipientId, long authorId, AnswerStatus status,
		String idempotencyKey, String bodyText, String coarseRegionCode,
		BigDecimal bearingFromSenderDegrees, String distanceBand,
		AnswerModerationStatus moderationStatus, Instant submittedAt,
		Instant publishedAt, Instant deletedAt) {
		return new Answer(id, postRecipientId, authorId, status, idempotencyKey, bodyText,
			coarseRegionCode, bearingFromSenderDegrees, distanceBand, moderationStatus,
			submittedAt, publishedAt, deletedAt);
	}

	public Answer startSafetyCheck() {
		requireStatus(AnswerStatus.SUBMITTED, "안전 검사를 시작");
		return copy(AnswerStatus.SAFETY_CHECKING, moderationStatus, publishedAt, deletedAt);
	}

	public Answer publish(Instant at) {
		requireStatus(AnswerStatus.SAFETY_CHECKING, "공개");
		Objects.requireNonNull(at, "publishedAt은 필수입니다");
		if (moderationStatus != AnswerModerationStatus.PASSED) {
			throw new IllegalStateException("안전 검사 통과 답변만 공개할 수 있습니다");
		}
		return copy(AnswerStatus.PUBLISHED, moderationStatus, at, deletedAt);
	}

	public Answer markSafetyPassed() {
		if (status != AnswerStatus.SAFETY_CHECKING) {
			throw new IllegalStateException("SAFETY_CHECKING 답변만 통과 처리할 수 있습니다");
		}
		if (moderationStatus != AnswerModerationStatus.PENDING) {
			throw new IllegalStateException("PENDING 안전 상태의 답변만 통과 처리할 수 있습니다");
		}
		return copy(status, AnswerModerationStatus.PASSED, publishedAt, deletedAt);
	}

	public Answer rejectSafety() {
		if (status != AnswerStatus.SAFETY_CHECKING) {
			throw new IllegalStateException("SAFETY_CHECKING 답변만 반려할 수 있습니다");
		}
		return copy(AnswerStatus.REJECTED, AnswerModerationStatus.REJECTED, publishedAt, deletedAt);
	}

	public Answer delete(Instant at) {
		Objects.requireNonNull(at, "deletedAt은 필수입니다");
		return copy(AnswerStatus.DELETED, moderationStatus, publishedAt, at);
	}

	private Answer copy(AnswerStatus nextStatus, AnswerModerationStatus nextModeration,
		Instant nextPublishedAt, Instant nextDeletedAt) {
		return new Answer(id, postRecipientId, authorId, nextStatus, idempotencyKey, bodyText,
			coarseRegionCode, bearingFromSenderDegrees, distanceBand, nextModeration,
			submittedAt, nextPublishedAt, nextDeletedAt);
	}

	private void requireStatus(AnswerStatus expected, String action) {
		if (status != expected) {
			throw new IllegalStateException(status + " 상태에서는 " + action + "할 수 없습니다");
		}
	}

	private void validateState() {
		if (status == AnswerStatus.PUBLISHED && publishedAt == null) {
			throw new IllegalArgumentException("PUBLISHED 답변은 publishedAt이 필요합니다");
		}
		if ((status == AnswerStatus.DELETED) != (deletedAt != null)) {
			throw new IllegalArgumentException("DELETED 상태와 deletedAt은 함께 설정되어야 합니다");
		}
		if (bodyText == null && status == AnswerStatus.PUBLISHED) {
			// READY media 첨부가 있는 경우는 DB deferred trigger가 최종 판정한다.
			return;
		}
	}

	private static BigDecimal requireBearing(BigDecimal value) {
		if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.valueOf(360)) >= 0) {
			throw new IllegalArgumentException("bearingFromSenderDegrees는 [0, 360)이어야 합니다");
		}
		return value;
	}

	private static String optionalText(String value, String field) {
		if (value != null && value.isBlank()) {
			throw new IllegalArgumentException(field + "은 공백일 수 없습니다");
		}
		return value;
	}

	private static String requiredText(String value, String field, int maxLength) {
		if (value == null || value.isBlank() || value.length() > maxLength) {
			throw new IllegalArgumentException(field + " 값이 유효하지 않습니다");
		}
		return value;
	}

	private static Long validatePositiveOrNull(Long value, String field) {
		return value == null ? null : requirePositive(value, field);
	}

	private static long requirePositive(long value, String field) {
		if (value <= 0) throw new IllegalArgumentException(field + "는 양수여야 합니다");
		return value;
	}

	public Long getId() { return id; }
	public long getPostRecipientId() { return postRecipientId; }
	public long getAuthorId() { return authorId; }
	public AnswerStatus getStatus() { return status; }
	public String getIdempotencyKey() { return idempotencyKey; }
	public String getBodyText() { return bodyText; }
	public String getCoarseRegionCode() { return coarseRegionCode; }
	public BigDecimal getBearingFromSenderDegrees() { return bearingFromSenderDegrees; }
	public String getDistanceBand() { return distanceBand; }
	public AnswerModerationStatus getModerationStatus() { return moderationStatus; }
	public Instant getSubmittedAt() { return submittedAt; }
	public Instant getPublishedAt() { return publishedAt; }
	public Instant getDeletedAt() { return deletedAt; }
}
