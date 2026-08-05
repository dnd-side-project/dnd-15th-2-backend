package com.dnd.qello.direction.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

public final class PostRecipient {

	private final Long id;
	private final Long postId;
	private final Long recipientId;
	private final PostRecipientStatus status;
	private final String distanceBand;
	private final BigDecimal matchedBearingDegrees;
	private final String matchedRegionCode;
	private final Instant matchedAt;
	private final Instant discoveredAt;
	private final Instant openedAt;
	private final Instant skipRequestedAt;
	private final Instant skippedAt;
	private final Instant capacityReleasedAt;
	private final Instant expiredAt;
	private final Instant blockedAt;

	private PostRecipient(Long id, Long postId, Long recipientId, PostRecipientStatus status,
		String distanceBand, BigDecimal matchedBearingDegrees, String matchedRegionCode,
		Instant matchedAt, Instant discoveredAt, Instant openedAt, Instant skipRequestedAt, Instant skippedAt,
		Instant capacityReleasedAt, Instant expiredAt, Instant blockedAt) {
		this.id = id;
		this.postId = requireId(postId, "postId");
		this.recipientId = requireId(recipientId, "recipientId");
		this.status = requireValue(status, "status");
		this.distanceBand = requireText(distanceBand, "distanceBand", 50);
		this.matchedBearingDegrees = requireValue(matchedBearingDegrees, "matchedBearingDegrees");
		if (matchedBearingDegrees.signum() < 0 || matchedBearingDegrees.compareTo(BigDecimal.valueOf(360)) >= 0) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_BEARING, "matchedBearingDegrees", "matchedBearingDegrees는 [0, 360)이어야 합니다");
		}
		this.matchedRegionCode = requireText(matchedRegionCode, "matchedRegionCode", 100);
		this.matchedAt = requireValue(matchedAt, "matchedAt");
		this.discoveredAt = discoveredAt;
		this.openedAt = openedAt;
		this.skipRequestedAt = skipRequestedAt;
		this.skippedAt = skippedAt;
		this.capacityReleasedAt = capacityReleasedAt;
		this.expiredAt = expiredAt;
		this.blockedAt = blockedAt;
		validateTimestamp(discoveredAt, "discoveredAt");
		validateTimestamp(openedAt, "openedAt");
		validateTimestamp(skipRequestedAt, "skipRequestedAt");
		validateTimestamp(skippedAt, "skippedAt");
		validateTimestamp(capacityReleasedAt, "capacityReleasedAt");
		validateTimestamp(expiredAt, "expiredAt");
		validateTimestamp(blockedAt, "blockedAt");
		if ((status == PostRecipientStatus.SKIP_PENDING) != (skipRequestedAt != null && skippedAt == null)) {
			throw new IllegalArgumentException("SKIP_PENDING은 넘김 요청만 있고 확정이 없는 상태여야 합니다");
		}
		if (skipRequestedAt == null && skippedAt != null) {
			throw new IllegalArgumentException("확정된 넘김에는 skipRequestedAt이 필요합니다");
		}
		if (skippedAt != null && skippedAt.isBefore(skipRequestedAt)) {
			throw new IllegalArgumentException("skippedAt은 skipRequestedAt보다 빠를 수 없습니다");
		}
		if ((status == PostRecipientStatus.SKIPPED) != (skippedAt != null)
			|| (status == PostRecipientStatus.EXPIRED) != (expiredAt != null)
			|| (status == PostRecipientStatus.BLOCKED) != (blockedAt != null)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "status", "terminal status와 timestamp가 일치하지 않습니다");
		}
		if ((status == PostRecipientStatus.DISCOVERED || status == PostRecipientStatus.OPENED || status == PostRecipientStatus.ANSWERED)
			&& discoveredAt == null) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "discoveredAt", "DISCOVERED 이후에는 discoveredAt이 필요합니다");
		}
		if ((status == PostRecipientStatus.OPENED || status == PostRecipientStatus.ANSWERED) && openedAt == null) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE, "openedAt", "OPENED 이후에는 openedAt이 필요합니다");
		}
		if ((status == PostRecipientStatus.ANSWERED || status == PostRecipientStatus.SKIPPED || status == PostRecipientStatus.EXPIRED || status == PostRecipientStatus.BLOCKED) != (capacityReleasedAt != null)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_RECIPIENT_STATE,
				"capacityReleasedAt",
				"terminal 상태와 capacityReleasedAt이 일치해야 합니다"
			);
		}
	}

	public static PostRecipient available(Long postId, Long recipientId, String distanceBand,
		BigDecimal matchedBearingDegrees, String matchedRegionCode, Instant matchedAt) {
		return new PostRecipient(null, postId, recipientId, PostRecipientStatus.AVAILABLE,
			distanceBand, matchedBearingDegrees, matchedRegionCode, matchedAt, null, null, null, null, null, null, null);
	}

	public static PostRecipient restore(Long id, Long postId, Long recipientId, PostRecipientStatus status,
		String distanceBand, BigDecimal matchedBearingDegrees, String matchedRegionCode, Instant matchedAt,
		Instant discoveredAt, Instant openedAt, Instant skipRequestedAt, Instant skippedAt,
		Instant capacityReleasedAt, Instant expiredAt, Instant blockedAt) {
		return new PostRecipient(id, postId, recipientId, status, distanceBand, matchedBearingDegrees,
			matchedRegionCode, matchedAt, discoveredAt, openedAt, skipRequestedAt, skippedAt,
			capacityReleasedAt, expiredAt, blockedAt);
	}

	private void validateTimestamp(Instant value, String field) {
		if (value != null && value.isBefore(matchedAt)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_TIME_ORDER, field, field + "은 matchedAt보다 빠를 수 없습니다");
		}
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new DirectionException(
				DirectionErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}

	private static Long requireId(Long value, String field) {
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

	public PostRecipient requestSkip(Instant at) {
		Objects.requireNonNull(at, "at은 필수입니다");
		if (status != PostRecipientStatus.AVAILABLE && status != PostRecipientStatus.DISCOVERED
			&& status != PostRecipientStatus.OPENED) {
			throw new IllegalStateException("넘김을 요청할 수 없는 상태입니다: " + status);
		}
		return new PostRecipient(id, postId, recipientId, PostRecipientStatus.SKIP_PENDING, distanceBand,
			matchedBearingDegrees, matchedRegionCode, matchedAt, discoveredAt, openedAt, at, null,
			null, null, null);
	}

	public PostRecipient revertSkip() {
		if (status != PostRecipientStatus.SKIP_PENDING) {
			throw new IllegalStateException("되돌릴 수 있는 상태가 아닙니다: " + status);
		}
		return new PostRecipient(id, postId, recipientId, previousStatus(), distanceBand,
			matchedBearingDegrees, matchedRegionCode, matchedAt, discoveredAt, openedAt, null, null,
			null, null, null);
	}

	public PostRecipient confirmSkip(Instant at) {
		Objects.requireNonNull(at, "at은 필수입니다");
		if (status != PostRecipientStatus.SKIP_PENDING) {
			throw new IllegalStateException("확정할 수 있는 상태가 아닙니다: " + status);
		}
		// capacityReleasedAt은 이 객체 상태에만 반영된다. RecipientReceiveStateRepository의 실제 용량
		// 카운터 해제는 후속 SKIP_CONFIRMATION_DUE 워커의 몫이며 이 메서드 범위 밖이다.
		return new PostRecipient(id, postId, recipientId, PostRecipientStatus.SKIPPED, distanceBand,
			matchedBearingDegrees, matchedRegionCode, matchedAt, discoveredAt, openedAt, skipRequestedAt, at,
			at, null, null);
	}

	private PostRecipientStatus previousStatus() {
		if (openedAt != null) return PostRecipientStatus.OPENED;
		if (discoveredAt != null) return PostRecipientStatus.DISCOVERED;
		return PostRecipientStatus.AVAILABLE;
	}

	public Long getId() { return id; }
	public Long getPostId() { return postId; }
	public Long getRecipientId() { return recipientId; }
	public PostRecipientStatus getStatus() { return status; }
	public String getDistanceBand() { return distanceBand; }
	public BigDecimal getMatchedBearingDegrees() { return matchedBearingDegrees; }
	public String getMatchedRegionCode() { return matchedRegionCode; }
	public Instant getMatchedAt() { return matchedAt; }
	public Instant getDiscoveredAt() { return discoveredAt; }
	public Instant getOpenedAt() { return openedAt; }
	public Instant getSkipRequestedAt() { return skipRequestedAt; }
	public Instant getSkippedAt() { return skippedAt; }
	public Instant getCapacityReleasedAt() { return capacityReleasedAt; }
	public Instant getExpiredAt() { return expiredAt; }
	public Instant getBlockedAt() { return blockedAt; }
}
