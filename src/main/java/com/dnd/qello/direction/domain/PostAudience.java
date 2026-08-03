package com.dnd.qello.direction.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class PostAudience {

	private final Long postId;
	private final Long directionSchemeId;
	private final String selectedSegmentKey;
	private final BigDecimal centerBearingDegrees;
	private final BigDecimal angularWidthDegrees;
	private final long minDistanceMeters;
	private final long maxDistanceMeters;
	private final BigDecimal originLatitude;
	private final BigDecimal originLongitude;
	private final String originCellId;
	private final Instant snapshottedAt;

	private PostAudience(Long postId, Long directionSchemeId, String selectedSegmentKey,
		BigDecimal centerBearingDegrees, BigDecimal angularWidthDegrees, long minDistanceMeters,
		long maxDistanceMeters, BigDecimal originLatitude, BigDecimal originLongitude,
		String originCellId, Instant snapshottedAt) {
		this.postId = requireId(postId, "postId");
		this.directionSchemeId = requireId(directionSchemeId, "directionSchemeId");
		this.selectedSegmentKey = requireText(selectedSegmentKey, "selectedSegmentKey", 20);
		this.centerBearingDegrees = requireBearing(centerBearingDegrees, "centerBearingDegrees");
		this.angularWidthDegrees = Objects.requireNonNull(angularWidthDegrees, "angularWidthDegrees는 필수입니다");
		if (angularWidthDegrees.signum() <= 0 || angularWidthDegrees.compareTo(BigDecimal.valueOf(360)) > 0) throw new IllegalArgumentException("angularWidthDegrees 범위가 유효하지 않습니다");
		if (minDistanceMeters < 0 || maxDistanceMeters <= minDistanceMeters) throw new IllegalArgumentException("거리 범위가 유효하지 않습니다");
		this.minDistanceMeters = minDistanceMeters;
		this.maxDistanceMeters = maxDistanceMeters;
		if ((originLatitude == null) != (originLongitude == null)) throw new IllegalArgumentException("origin 위치는 위도와 경도를 함께 지정해야 합니다");
		if (originLatitude == null && (originCellId == null || originCellId.isBlank())) throw new IllegalArgumentException("origin 위치 또는 cell이 필요합니다");
		this.originLatitude = originLatitude;
		this.originLongitude = originLongitude;
		this.originCellId = originCellId;
		this.snapshottedAt = Objects.requireNonNull(snapshottedAt, "snapshottedAt은 필수입니다");
	}

	public static PostAudience create(Long postId, Long directionSchemeId, String selectedSegmentKey,
		BigDecimal centerBearingDegrees, BigDecimal angularWidthDegrees, long minDistanceMeters,
		long maxDistanceMeters, BigDecimal originLatitude, BigDecimal originLongitude,
		String originCellId, Instant snapshottedAt) {
		return new PostAudience(postId, directionSchemeId, selectedSegmentKey, centerBearingDegrees,
			angularWidthDegrees, minDistanceMeters, maxDistanceMeters, originLatitude,
			originLongitude, originCellId, snapshottedAt);
	}

	private static Long requireId(Long value, String field) {
		if (value == null || value <= 0) throw new IllegalArgumentException(field + "는 양수여야 합니다");
		return value;
	}
	private static String requireText(String value, String field, int max) {
		if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(field + "이 유효하지 않습니다");
		return value;
	}
	private static BigDecimal requireBearing(BigDecimal value, String field) {
		Objects.requireNonNull(value, field + "은 필수입니다");
		if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(360)) >= 0) throw new IllegalArgumentException(field + "는 [0, 360)이어야 합니다");
		return value;
	}

	public Long getPostId() { return postId; }
	public Long getDirectionSchemeId() { return directionSchemeId; }
	public String getSelectedSegmentKey() { return selectedSegmentKey; }
	public BigDecimal getCenterBearingDegrees() { return centerBearingDegrees; }
	public BigDecimal getAngularWidthDegrees() { return angularWidthDegrees; }
	public long getMinDistanceMeters() { return minDistanceMeters; }
	public long getMaxDistanceMeters() { return maxDistanceMeters; }
	public BigDecimal getOriginLatitude() { return originLatitude; }
	public BigDecimal getOriginLongitude() { return originLongitude; }
	public String getOriginCellId() { return originCellId; }
	public Instant getSnapshottedAt() { return snapshottedAt; }
}
