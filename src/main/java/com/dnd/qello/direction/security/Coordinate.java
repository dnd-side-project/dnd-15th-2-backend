package com.dnd.qello.direction.security;

import java.math.BigDecimal;

public record Coordinate(BigDecimal latitude, BigDecimal longitude) {

	public Coordinate {
		if (latitude == null || longitude == null
				|| latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
				|| longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
			throw new CoordinateProtectionException(CoordinateProtectionException.Reason.INVALID_COORDINATE);
		}
	}

	@Override
	public String toString() {
		return "Coordinate[REDACTED]";
	}
}
