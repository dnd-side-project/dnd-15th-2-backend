package com.dnd.qello.direction.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

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
		if (userId == null || userId <= 0) throw new IllegalArgumentException("userId는 양수여야 합니다");
		this.userId = userId;
		if ((latitude == null) != (longitude == null)) throw new IllegalArgumentException("위도와 경도는 함께 지정해야 합니다");
		if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0)) {
			throw new IllegalArgumentException("latitude 범위가 유효하지 않습니다");
		}
		if (longitude != null && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
			throw new IllegalArgumentException("longitude 범위가 유효하지 않습니다");
		}
		if (latitude == null && (coarseCellId == null || coarseCellId.isBlank())) {
			throw new IllegalArgumentException("position 또는 coarseCellId가 필요합니다");
		}
		this.latitude = latitude;
		this.longitude = longitude;
		this.coarseCellId = coarseCellId;
		this.coarseRegionCode = requireText(coarseRegionCode, "coarseRegionCode", 100);
		if (accuracyMeters != null && accuracyMeters.signum() < 0) throw new IllegalArgumentException("accuracyMeters는 음수일 수 없습니다");
		this.accuracyMeters = accuracyMeters;
		this.receiveAllowed = receiveAllowed;
		this.locationAt = Objects.requireNonNull(locationAt, "locationAt은 필수입니다");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt은 필수입니다");
		if (!expiresAt.isAfter(locationAt)) throw new IllegalArgumentException("expiresAt은 locationAt보다 늦어야 합니다");
	}

	public static ActiveUserPresence create(Long userId, BigDecimal latitude, BigDecimal longitude,
		String coarseCellId, String coarseRegionCode, BigDecimal accuracyMeters, boolean receiveAllowed,
		Instant locationAt, Instant expiresAt) {
		return new ActiveUserPresence(userId, latitude, longitude, coarseCellId, coarseRegionCode,
			accuracyMeters, receiveAllowed, locationAt, expiresAt);
	}

	public boolean isCurrentAt(Instant at) {
		Objects.requireNonNull(at, "at은 필수입니다");
		return receiveAllowed && !at.isBefore(locationAt) && at.isBefore(expiresAt);
	}

	private static String requireText(String value, String field, int max) {
		if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(field + "이 유효하지 않습니다");
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
