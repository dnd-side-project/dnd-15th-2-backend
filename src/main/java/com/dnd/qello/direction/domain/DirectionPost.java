package com.dnd.qello.direction.domain;

import java.time.Instant;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

import lombok.Getter;

@Getter
public final class DirectionPost {

	private final Long id;
	private final Long senderId;
	private final Long approvedQuestionId;
	private final DirectionPostStatus status;
	private final String idempotencyKey;
	private final DirectionRequestFingerprint requestFingerprint;
	private final String bodyText;
	private final String coarseRegionCode;
	private final DirectionPostModerationStatus moderationStatus;
	private final Instant submittedAt;
	private final Instant publishedAt;
	private final Instant expiresAt;
	private final Instant answersReadAt;
	private final Instant deletedAt;

	private DirectionPost(Long id, Long senderId, Long approvedQuestionId, DirectionPostStatus status,
		String idempotencyKey, DirectionRequestFingerprint requestFingerprint, String bodyText, String coarseRegionCode,
		DirectionPostModerationStatus moderationStatus, Instant submittedAt, Instant publishedAt,
		Instant expiresAt, Instant answersReadAt, Instant deletedAt) {
		this.id = validateId(id, "id");
		this.senderId = requireId(senderId, "senderId");
		this.approvedQuestionId = requireId(approvedQuestionId, "approvedQuestionId");
		this.status = requireValue(status, "status");
		this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 200);
		this.requestFingerprint = requestFingerprint;
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
		// 기존 호출부와 legacy fixture를 위한 nullable 복원 호환 경로다. 신규 service 제출은
		// fingerprint를 받는 아래 overload만 사용한다.
		return new DirectionPost(null, senderId, approvedQuestionId, DirectionPostStatus.MATCHING,
			idempotencyKey, null, bodyText, coarseRegionCode, DirectionPostModerationStatus.PENDING,
			submittedAt, null, expiresAt, null, null);
	}

	public static DirectionPost submit(Long senderId, Long approvedQuestionId,
		DirectionRequestFingerprint requestFingerprint, String idempotencyKey, String bodyText,
		String coarseRegionCode, Instant submittedAt, Instant expiresAt) {
		if (requestFingerprint == null) {
			throw new DirectionException(
				DirectionErrorCode.REQUIRED_VALUE_MISSING, "requestFingerprint", "새 제출에는 requestFingerprint가 필요합니다");
		}
		return new DirectionPost(null, senderId, approvedQuestionId, DirectionPostStatus.MATCHING,
			idempotencyKey, requestFingerprint, bodyText, coarseRegionCode, DirectionPostModerationStatus.PENDING,
			submittedAt, null, expiresAt, null, null);
	}

	public static DirectionPost restore(Long id, Long senderId, Long approvedQuestionId,
		DirectionPostStatus status, String idempotencyKey, String bodyText, String coarseRegionCode,
		DirectionPostModerationStatus moderationStatus, Instant submittedAt, Instant publishedAt,
		Instant expiresAt, Instant answersReadAt, Instant deletedAt) {
		return restore(id, senderId, approvedQuestionId, null, status, idempotencyKey, bodyText,
			coarseRegionCode, moderationStatus, submittedAt, publishedAt, expiresAt, answersReadAt, deletedAt);
	}

	public static DirectionPost restore(Long id, Long senderId, Long approvedQuestionId,
		DirectionRequestFingerprint requestFingerprint, DirectionPostStatus status, String idempotencyKey,
		String bodyText, String coarseRegionCode, DirectionPostModerationStatus moderationStatus,
		Instant submittedAt, Instant publishedAt, Instant expiresAt, Instant answersReadAt, Instant deletedAt) {
		return new DirectionPost(id, senderId, approvedQuestionId, status, idempotencyKey, requestFingerprint, bodyText,
			coarseRegionCode, moderationStatus, submittedAt, publishedAt, expiresAt, answersReadAt, deletedAt);
	}

	/** 질문자가 답변 목록을 읽은 시각을 기록한다. `새로운 답변 n개` 배지는 이 시각 이후 공개된 답변만 센다. */
	public DirectionPost markAnswersRead(Instant at) {
		requireValue(at, "answersReadAt");
		return new DirectionPost(id, senderId, approvedQuestionId, status, idempotencyKey, requestFingerprint, bodyText,
			coarseRegionCode, moderationStatus, submittedAt, publishedAt, expiresAt, at, deletedAt);
	}

	/**
	 * 매칭 워커가 실행 시점에 다시 확인하는 fail-closed gate다.
	 * 제출 시점의 preview나 Outbox payload가 아니라 현재 post 상태만 신뢰한다.
	 */
	public boolean canMatchAt(Instant at) {
		requireValue(at, "at");
		return status == DirectionPostStatus.MATCHING
			&& moderationStatus == DirectionPostModerationStatus.PASSED
			&& expiresAt.isAfter(at);
	}

	/** MATCHING/PASSED 질문글을 수신함에 보이는 ACTIVE 상태로 확정한다. */
	public DirectionPost activate(Instant at) {
		requireValue(at, "publishedAt");
		if (!canMatchAt(at)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_POST_STATE, "status", "현재 질문글은 매칭을 활성화할 수 없습니다");
		}
		return copy(DirectionPostStatus.ACTIVE, at, expiresAt, answersReadAt, deletedAt);
	}

	/** 선택 A deadline 정책에 따라 만료 시각 이후에만 질문글을 EXPIRED로 닫는다. */
	public DirectionPost expire(Instant at) {
		requireValue(at, "expiredAt");
		if (expiresAt.isAfter(at)
			|| (status != DirectionPostStatus.MATCHING
				&& status != DirectionPostStatus.SAFETY_CHECKING
				&& status != DirectionPostStatus.ACTIVE)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_POST_STATE, "status", "현재 질문글은 만료 처리할 수 없습니다");
		}
		return copy(DirectionPostStatus.EXPIRED, publishedAt, expiresAt, answersReadAt, deletedAt);
	}

	private DirectionPost copy(DirectionPostStatus nextStatus, Instant nextPublishedAt, Instant nextExpiresAt,
		Instant nextAnswersReadAt, Instant nextDeletedAt) {
		return new DirectionPost(id, senderId, approvedQuestionId, nextStatus, idempotencyKey, requestFingerprint,
			bodyText, coarseRegionCode, moderationStatus, submittedAt, nextPublishedAt, nextExpiresAt,
			nextAnswersReadAt, nextDeletedAt);
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

}
