package com.dnd.qello.direction.domain;

import java.math.BigDecimal;
import java.util.Objects;

public final class DirectionSegment {

	private final Long id;
	private final Long schemeId;
	private final String segmentKey;
	private final String displayName;
	private final BigDecimal centerBearingDegrees;
	private final BigDecimal angularWidthDegrees;
	private final int sortOrder;

	private DirectionSegment(Long id, Long schemeId, String segmentKey, String displayName,
		BigDecimal centerBearingDegrees, BigDecimal angularWidthDegrees, int sortOrder) {
		this.id = id;
		this.schemeId = requireId(schemeId, "schemeId");
		this.segmentKey = requireText(segmentKey, "segmentKey", 20);
		this.displayName = requireText(displayName, "displayName", 50);
		this.centerBearingDegrees = requireRange(centerBearingDegrees, "centerBearingDegrees", false);
		this.angularWidthDegrees = requireRange(angularWidthDegrees, "angularWidthDegrees", true);
		if (sortOrder < 0) throw new IllegalArgumentException("sortOrder는 0 이상이어야 합니다");
		this.sortOrder = sortOrder;
	}

	public static DirectionSegment create(Long schemeId, String segmentKey, String displayName,
		BigDecimal centerBearingDegrees, BigDecimal angularWidthDegrees, int sortOrder) {
		return new DirectionSegment(null, schemeId, segmentKey, displayName,
			centerBearingDegrees, angularWidthDegrees, sortOrder);
	}

	public static DirectionSegment restore(Long id, Long schemeId, String segmentKey, String displayName,
		BigDecimal centerBearingDegrees, BigDecimal angularWidthDegrees, int sortOrder) {
		return new DirectionSegment(id, schemeId, segmentKey, displayName,
			centerBearingDegrees, angularWidthDegrees, sortOrder);
	}

	public boolean contains(double bearingDegrees) {
		double bearing = DirectionScheme.normalize(bearingDegrees);
		double center = centerBearingDegrees.doubleValue();
		double half = angularWidthDegrees.doubleValue() / 2.0;
		double start = DirectionScheme.normalize(center - half);
		double end = DirectionScheme.normalize(center + half);
		if (start < end) return bearing >= start && bearing < end;
		return bearing >= start || bearing < end;
	}

	private static BigDecimal requireRange(BigDecimal value, String field, boolean positive) {
		Objects.requireNonNull(value, field + "은 필수입니다");
		if ((positive && value.signum() <= 0) || (!positive && (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(360)) >= 0))) {
			throw new IllegalArgumentException(field + " 범위가 유효하지 않습니다");
		}
		if (positive && value.compareTo(BigDecimal.valueOf(360)) > 0) throw new IllegalArgumentException(field + "은 360 이하이어야 합니다");
		return value;
	}

	private static Long requireId(Long value, String field) {
		if (value == null || value <= 0) throw new IllegalArgumentException(field + "는 양수여야 합니다");
		return value;
	}

	private static String requireText(String value, String field, int max) {
		if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(field + "이 유효하지 않습니다");
		return value;
	}

	public Long getId() { return id; }
	public Long getSchemeId() { return schemeId; }
	public String getSegmentKey() { return segmentKey; }
	public String getDisplayName() { return displayName; }
	public BigDecimal getCenterBearingDegrees() { return centerBearingDegrees; }
	public BigDecimal getAngularWidthDegrees() { return angularWidthDegrees; }
	public int getSortOrder() { return sortOrder; }
}
