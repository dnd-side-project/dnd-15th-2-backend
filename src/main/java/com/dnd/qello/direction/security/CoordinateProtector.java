package com.dnd.qello.direction.security;

public interface CoordinateProtector {

	byte[] protect(Coordinate coordinate, CoordinatePurpose purpose, long rowId);

	Coordinate decrypt(byte[] envelope, CoordinatePurpose purpose, long rowId);
}
