package com.dnd.qello.direction.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

public final class DirectionScheme {

	private final Long id;
	private final String code;
	private final int version;
	private final DirectionSchemeType type;
	private final Integer segmentCount;
	private final BigDecimal startOffsetDegrees;
	private final DirectionSchemeStatus status;

	private DirectionScheme(Long id, String code, int version, DirectionSchemeType type,
		Integer segmentCount, BigDecimal startOffsetDegrees, DirectionSchemeStatus status) {
		this.id = validateId(id, "id");
		this.code = requireText(code, "code", 50);
		if (version <= 0) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_VALUE_RANGE, "version", "version은 양수여야 합니다");
		}
		this.version = version;
		this.type = requireValue(type, "type");
		this.segmentCount = segmentCount;
		if (type == DirectionSchemeType.EQUAL_SEGMENTS && (segmentCount == null || segmentCount <= 0)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_SCHEME_CONFIGURATION, "segmentCount", "EQUAL_SEGMENTS는 segmentCount가 필요합니다");
		}
		if (type == DirectionSchemeType.CONTINUOUS && segmentCount != null) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_SCHEME_CONFIGURATION, "segmentCount", "CONTINUOUS는 segmentCount를 가질 수 없습니다");
		}
		this.startOffsetDegrees = normalize(startOffsetDegrees, "startOffsetDegrees");
		this.status = requireValue(status, "status");
	}

	public static DirectionScheme createEqual(String code, int version, int segmentCount,
		BigDecimal startOffsetDegrees) {
		return new DirectionScheme(null, code, version, DirectionSchemeType.EQUAL_SEGMENTS,
			segmentCount, startOffsetDegrees, DirectionSchemeStatus.ACTIVE);
	}

	public static DirectionScheme restore(Long id, String code, int version, DirectionSchemeType type,
		Integer segmentCount, BigDecimal startOffsetDegrees, DirectionSchemeStatus status) {
		return new DirectionScheme(id, code, version, type, segmentCount, startOffsetDegrees, status);
	}

	public DirectionSegment selectSegment(double bearingDegrees, List<DirectionSegment> segments) {
		requireValue(segments, "segments");
		double bearing = normalize(bearingDegrees);
		return segments.stream()
			.filter(segment -> segment.contains(bearing))
			.findFirst()
			.orElseThrow(() -> new DirectionException(
				DirectionErrorCode.SEGMENT_NOT_FOUND, "bearingDegrees", "bearing을 포함하는 sector가 없습니다"));
	}

	public void validateCoverage(List<DirectionSegment> segments) {
		requireValue(segments, "segments");
		if (type == DirectionSchemeType.EQUAL_SEGMENTS && segments.size() != segmentCount) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_SCHEME_CONFIGURATION, "segments", "segmentCount와 실제 segment 수가 다릅니다");
		}
		for (int degree = 0; degree < 360; degree++) {
			double sample = degree + 0.0001;
			long matches = segments.stream().filter(segment -> segment.contains(sample)).count();
			if (matches != 1) {
				throw new DirectionException(
					DirectionErrorCode.INVALID_SCHEME_CONFIGURATION, "segments", "sector coverage가 겹치거나 비어 있습니다");
			}
		}
	}

	public static double normalize(double value) {
		if (!Double.isFinite(value)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_BEARING, "bearingDegrees", "bearing은 유한한 값이어야 합니다");
		}
		double normalized = value % 360.0;
		return normalized < 0 ? normalized + 360.0 : normalized;
	}

	private static BigDecimal normalize(BigDecimal value, String field) {
		requireValue(value, field);
		if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(360)) >= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_BEARING, field, field + "는 [0, 360)이어야 합니다");
		}
		return value.setScale(3, RoundingMode.UNNECESSARY);
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

	private static String requireText(String value, String field, int max) {
		if (value == null || value.isBlank() || value.length() > max) {
			throw new DirectionException(DirectionErrorCode.INVALID_TEXT, field, field + "이 유효하지 않습니다");
		}
		return value;
	}

	public Long getId() { return id; }
	public String getCode() { return code; }
	public int getVersion() { return version; }
	public DirectionSchemeType getType() { return type; }
	public Integer getSegmentCount() { return segmentCount; }
	public BigDecimal getStartOffsetDegrees() { return startOffsetDegrees; }
	public DirectionSchemeStatus getStatus() { return status; }
}
