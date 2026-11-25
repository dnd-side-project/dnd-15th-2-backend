package com.dnd.qello.direction.security;

import java.math.BigDecimal;
import java.nio.ByteBuffer;

final class CoordinateCodec {

	static final int PAYLOAD_LENGTH_BYTES = 2 * Double.BYTES;

	private CoordinateCodec() {
	}

	static byte[] encode(Coordinate coordinate) {
		if (coordinate == null) {
			throw new CoordinateProtectionException(CoordinateProtectionException.Reason.INVALID_COORDINATE);
		}
		// PostGIS geography receives float8; retain those bits without decimal
		// rounding.
		return ByteBuffer.allocate(PAYLOAD_LENGTH_BYTES)
				.putDouble(coordinate.latitude().doubleValue())
				.putDouble(coordinate.longitude().doubleValue())
				.array();
	}

	static Coordinate decode(byte[] payload) {
		if (payload == null || payload.length != PAYLOAD_LENGTH_BYTES) {
			throw new CoordinateProtectionException(CoordinateProtectionException.Reason.INVALID_ENVELOPE);
		}
		ByteBuffer buffer = ByteBuffer.wrap(payload);
		double latitude = buffer.getDouble();
		double longitude = buffer.getDouble();
		if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90
				|| Math.abs(longitude) > 180) {
			throw new CoordinateProtectionException(CoordinateProtectionException.Reason.INVALID_ENVELOPE);
		}
		return new Coordinate(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
	}
}
