package com.dnd.qello.auth.security;


public interface PasswordHasher {

	PasswordHash hash(RawPassword rawPassword);

	boolean matches(RawPassword rawPassword, PasswordHash passwordHash);

}
