package com.dnd.qello.direction.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

import lombok.Getter;

@Getter
public final class DirectionPost {

	private static final Set<DirectionPostModerationStatus> UNDECIDED_MODERATION = Set.of(
			DirectionPostModerationStatus.PENDING, DirectionPostModerationStatus.REVIEW_HELD);

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
			String idempotencyKey, DirectionRequestFingerprint requestFingerprint, String bodyText,
			String coarseRegionCode,
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
		// 기존 호출부와 fixture를 위한 nullable 복원 호환 경로다. 신규 service 제출은
		// fingerprint를 받는 아래 overload만 사용한다.
		return new DirectionPost(null, senderId, approvedQuestionId, DirectionPostStatus.MATCHING,
				idempotencyKey, null, bodyText, coarseRegionCode, DirectionPostModerationStatus.PENDING,
				submittedAt, null, expiresAt, null, null);
	}

	/**
	 * 본문이 있는 질문글은 텍스트 moderation 판정 전까지 PENDING이다. 본문이 없는 질문글(미디어 단독)은 MVP에 적용할 검사가
	 * 없어 PASSED로 만든다(#137). 이 PASSED는 미디어 자체의 안전성을 검증했다는 뜻이 아니며, 미디어 검사가 생기면 이 규칙을
	 * 바꾼다.
	 */
	public static DirectionPost submit(Long senderId, Long approvedQuestionId,
			DirectionRequestFingerprint requestFingerprint, String idempotencyKey, String bodyText,
			String coarseRegionCode, Instant submittedAt, Instant expiresAt) {
		if (requestFingerprint == null) {
			throw new DirectionException(
					DirectionErrorCode.REQUIRED_VALUE_MISSING, "requestFingerprint",
					"새 제출에는 requestFingerprint가 필요합니다");
		}
		DirectionPostModerationStatus moderationStatus = bodyText == null
				? DirectionPostModerationStatus.PASSED
				: DirectionPostModerationStatus.PENDING;
		return new DirectionPost(null, senderId, approvedQuestionId, DirectionPostStatus.MATCHING,
				idempotencyKey, requestFingerprint, bodyText, coarseRegionCode, moderationStatus,
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
	 * 매칭 워커가 실행 시점에 다시 확인하는 fail-closed gate다. 제출 시점의 preview나 Outbox payload가 아니라
	 * 현재 post 상태만 신뢰한다.
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

	/** 텍스트 moderation ALLOW를 반영한다. 판정 전(PENDING·REVIEW_HELD)이 아니면 바꾸지 않는다. */
	public Optional<DirectionPost> passModeration(Instant at) {
		return moderate(DirectionPostModerationStatus.PASSED, UNDECIDED_MODERATION, at);
	}

	/**
	 * 텍스트 moderation BLOCK을 반영한다. `status`는 바꾸지 않고 매칭 워커가 REJECTED를 보고 매칭하지 않는다.
	 */
	public Optional<DirectionPost> rejectModeration(Instant at) {
		return moderate(DirectionPostModerationStatus.REJECTED, UNDECIDED_MODERATION, at);
	}

	/**
	 * deadline까지 판정이 오지 않았음을 반영한다. 이 신호는 승인이 아니므로 매칭 보류를 유지하고, 이후 도착한 판정(수동 검토 포함)이
	 * PASSED·REJECTED로 바꿀 수 있다.
	 */
	public Optional<DirectionPost> holdModerationForReview(Instant at) {
		return moderate(DirectionPostModerationStatus.REVIEW_HELD, Set.of(DirectionPostModerationStatus.PENDING), at);
	}

	// 판정 이벤트는 중복되거나 순서가 바뀌어 도착할 수 있다. 이미 확정된 판정은 되돌리지 않고, 만료가
	// 판정보다 우선하므로 매칭 대기 중이 아니거나 만료 시각이 지난 질문글은 바꾸지 않는다.
	private Optional<DirectionPost> moderate(DirectionPostModerationStatus next,
			Set<DirectionPostModerationStatus> allowedFrom, Instant at) {
		requireValue(at, "at");
		if (status != DirectionPostStatus.MATCHING || !expiresAt.isAfter(at)
				|| !allowedFrom.contains(moderationStatus)) {
			return Optional.empty();
		}
		return Optional.of(new DirectionPost(id, senderId, approvedQuestionId, status, idempotencyKey,
				requestFingerprint, bodyText, coarseRegionCode, next, submittedAt, publishedAt, expiresAt,
				answersReadAt,
				deletedAt));
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
