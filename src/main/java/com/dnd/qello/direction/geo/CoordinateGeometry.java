package com.dnd.qello.direction.geo;

import java.util.Optional;

import com.dnd.qello.direction.security.Coordinate;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

public final class CoordinateGeometry {

	private CoordinateGeometry() {
	}

	public static Optional<Measurement> inverse(Coordinate origin, Coordinate destination) {
		GeodesicData forward = Geodesic.WGS84.Inverse(origin.latitude().doubleValue(),
				origin.longitude().doubleValue(), destination.latitude().doubleValue(),
				destination.longitude().doubleValue());
		// PostGIS returns NULL azimuth for coincident points, including longitude
		// aliases at the poles.
		if (forward.s12 == 0) {
			return Optional.empty();
		}
		GeodesicData reverse = Geodesic.WGS84.Inverse(destination.latitude().doubleValue(),
				destination.longitude().doubleValue(), origin.latitude().doubleValue(),
				origin.longitude().doubleValue());
		return Optional.of(new Measurement(forward.s12, normalize(forward.azi1), normalize(reverse.azi1)));
	}

	public static boolean containsBearing(double bearing, double start, double end) {
		return start < end ? bearing >= start && bearing < end : bearing >= start || bearing < end;
	}

	private static double normalize(double bearing) {
		double result = bearing % 360;
		return result < 0 ? (result + 360) % 360 : result == 0 ? 0 : result;
	}

	public record Measurement(double distanceMeters, double bearingDegrees, double inboundBearingDegrees) {
	}
}
