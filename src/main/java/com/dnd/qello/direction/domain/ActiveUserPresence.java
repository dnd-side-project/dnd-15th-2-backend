package com.dnd.qello.direction.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

public final class ActiveUserPresence {

	private final Long userId;
	private final BigDecimal latitude;
	private final BigDecimal longitude;
	private final String coarseCellId;
	private final String coarseRegionCode;
	private final BigDecimal accuracyMeters;
	private final boolean receiveAllowed;
	private final Instant locationAt;
	private final Instant expiresAt;

	private ActiveUserPresence(Long userId, BigDecimal latitude, BigDecimal longitude, String coarseCellId,
		String coarseRegionCode, BigDecimal accuracyMeters, boolean receiveAllowed,
		Instant locationAt, Instant expiresAt) {
		if (userId == null || userId <= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_ID, "userId", "userId는 양수여야 합니다");
		}
		this.userId = userId;
		if ((latitude == null) != (longitude == null)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_COORDINATE, "latitude", "위도와 경도는 함께 지정해야 합니다");
		}
		if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_COORDINATE, "latitude", "latitude 범위가 유효하지 않습니다");
		}
		if (longitude != null && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_COORDINATE, "longitude", "longitude 범위가 유효하지 않습니다");
		}
		if (latitude == null && (coarseCellId == null || coarseCellId.isBlank())) {
			throw new DirectionException(
				DirectionErrorCode.LOCATION_REQUIRED, "coarseCellId", "position 또는 coarseCellId가 필요합니다");
		}
		this.latitude = latitude;
		this.longitude = longitude;
		this.coarseCellId = coarseCellId;
		this.coarseRegionCode = requireText(coarseRegionCode, "coarseRegionCode", 100);
		if (accuracyMeters != null && accuracyMeters.signum() < 0) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_VALUE_RANGE, "accuracyMeters", "accuracyMeters는 음수일 수 없습니다");
		}
		this.accuracyMeters = accuracyMeters;
		this.receiveAllowed = receiveAllowed;
		this.locationAt = requireValue(locationAt, "locationAt");
		this.expiresAt = requireValue(expiresAt, "expiresAt");
		if (!expiresAt.isAfter(locationAt)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_TIME_ORDER, "expiresAt", "expiresAt은 locationAt보다 늦어야 합니다");
		}
	}

	public static ActiveUserPresence create(Long userId, BigDecimal latitude, BigDecimal longitude,
		String coarseCellId, String coarseRegionCode, BigDecimal accuracyMeters, boolean receiveAllowed,
		Instant locationAt, Instant expiresAt) {
		return new ActiveUserPresence(userId, latitude, longitude, coarseCellId, coarseRegionCode,
			accuracyMeters, receiveAllowed, locationAt, expiresAt);
	}

	public boolean isCurrentAt(Instant at) {
		return isReceiveEligibleAt(at);
	}

	public boolean hasCurrentLocationAt(Instant at) {
		requireValue(at, "at");
		return latitude != null && longitude != null && !at.isBefore(locationAt) && at.isBefore(expiresAt);
	}

	public boolean isReceiveEligibleAt(Instant at) {
		return receiveAllowed && hasCurrentLocationAt(at);
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new DirectionException(
				DirectionErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}

	private static String requireText(String value, String field, int max) {
		if (value == null || value.isBlank() || value.length() > max) {
			throw new DirectionException(DirectionErrorCode.INVALID_TEXT, field, field + "이 유효하지 않습니다");
		}
		return value;
	}

	public Long getUserId() { return userId; }
	public BigDecimal getLatitude() { return latitude; }
	public BigDecimal getLongitude() { return longitude; }
	public String getCoarseCellId() { return coarseCellId; }
	public String getCoarseRegionCode() { return coarseRegionCode; }
	public BigDecimal getAccuracyMeters() { return accuracyMeters; }
	public boolean isReceiveAllowed() { return receiveAllowed; }
	public Instant getLocationAt() { return locationAt; }
	public Instant getExpiresAt() { return expiresAt; }
}
