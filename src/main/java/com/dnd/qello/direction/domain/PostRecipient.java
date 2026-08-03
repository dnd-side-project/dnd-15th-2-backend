package com.dnd.qello.direction.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

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
	private final Instant skippedAt;
	private final Instant capacityReleasedAt;
	private final Instant expiredAt;
	private final Instant blockedAt;

	private PostRecipient(Long id, Long postId, Long recipientId, PostRecipientStatus status,
		String distanceBand, BigDecimal matchedBearingDegrees, String matchedRegionCode,
		Instant matchedAt, Instant discoveredAt, Instant openedAt, Instant skippedAt,
		Instant capacityReleasedAt, Instant expiredAt, Instant blockedAt) {
		this.id = id;
		this.postId = requireId(postId, "postId");
		this.recipientId = requireId(recipientId, "recipientId");
		this.status = Objects.requireNonNull(status, "status는 필수입니다");
		this.distanceBand = requireText(distanceBand, "distanceBand", 50);
		this.matchedBearingDegrees = Objects.requireNonNull(matchedBearingDegrees, "matchedBearingDegrees는 필수입니다");
		if (matchedBearingDegrees.signum() < 0 || matchedBearingDegrees.compareTo(BigDecimal.valueOf(360)) >= 0) throw new IllegalArgumentException("matchedBearingDegrees는 [0, 360)이어야 합니다");
		this.matchedRegionCode = requireText(matchedRegionCode, "matchedRegionCode", 100);
		this.matchedAt = Objects.requireNonNull(matchedAt, "matchedAt은 필수입니다");
		this.discoveredAt = discoveredAt;
		this.openedAt = openedAt;
		this.skippedAt = skippedAt;
		this.capacityReleasedAt = capacityReleasedAt;
		this.expiredAt = expiredAt;
		this.blockedAt = blockedAt;
		validateTimestamp(discoveredAt, "discoveredAt");
		validateTimestamp(openedAt, "openedAt");
		validateTimestamp(skippedAt, "skippedAt");
		validateTimestamp(capacityReleasedAt, "capacityReleasedAt");
		validateTimestamp(expiredAt, "expiredAt");
		validateTimestamp(blockedAt, "blockedAt");
		if ((status == PostRecipientStatus.SKIPPED) != (skippedAt != null)
			|| (status == PostRecipientStatus.EXPIRED) != (expiredAt != null)
			|| (status == PostRecipientStatus.BLOCKED) != (blockedAt != null)) {
			throw new IllegalArgumentException("terminal status와 timestamp가 일치하지 않습니다");
		}
		if ((status == PostRecipientStatus.DISCOVERED || status == PostRecipientStatus.OPENED || status == PostRecipientStatus.ANSWERED)
			&& discoveredAt == null) throw new IllegalArgumentException("DISCOVERED 이후에는 discoveredAt이 필요합니다");
		if ((status == PostRecipientStatus.OPENED || status == PostRecipientStatus.ANSWERED) && openedAt == null) throw new IllegalArgumentException("OPENED 이후에는 openedAt이 필요합니다");
		if ((status == PostRecipientStatus.ANSWERED || status == PostRecipientStatus.SKIPPED || status == PostRecipientStatus.EXPIRED || status == PostRecipientStatus.BLOCKED) != (capacityReleasedAt != null)) {
			throw new IllegalArgumentException("terminal 상태와 capacityReleasedAt이 일치해야 합니다");
		}
	}

	public static PostRecipient available(Long postId, Long recipientId, String distanceBand,
		BigDecimal matchedBearingDegrees, String matchedRegionCode, Instant matchedAt) {
		return new PostRecipient(null, postId, recipientId, PostRecipientStatus.AVAILABLE,
			distanceBand, matchedBearingDegrees, matchedRegionCode, matchedAt, null, null, null, null, null, null);
	}

	public static PostRecipient restore(Long id, Long postId, Long recipientId, PostRecipientStatus status,
		String distanceBand, BigDecimal matchedBearingDegrees, String matchedRegionCode, Instant matchedAt,
		Instant discoveredAt, Instant openedAt, Instant skippedAt, Instant capacityReleasedAt,
		Instant expiredAt, Instant blockedAt) {
		return new PostRecipient(id, postId, recipientId, status, distanceBand, matchedBearingDegrees,
			matchedRegionCode, matchedAt, discoveredAt, openedAt, skippedAt, capacityReleasedAt,
			expiredAt, blockedAt);
	}

	private void validateTimestamp(Instant value, String field) { if (value != null && value.isBefore(matchedAt)) throw new IllegalArgumentException(field + "은 matchedAt보다 빠를 수 없습니다"); }
	private static Long requireId(Long value, String field) { if (value == null || value <= 0) throw new IllegalArgumentException(field + "는 양수여야 합니다"); return value; }
	private static String requireText(String value, String field, int max) { if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(field + "이 유효하지 않습니다"); return value; }

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
	public Instant getSkippedAt() { return skippedAt; }
	public Instant getCapacityReleasedAt() { return capacityReleasedAt; }
	public Instant getExpiredAt() { return expiredAt; }
	public Instant getBlockedAt() { return blockedAt; }
}
