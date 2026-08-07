package com.dnd.qello.account.security;

import com.dnd.qello.account.domain.PasswordHash;

public interface PasswordHasher {

	PasswordHash hash(RawPassword rawPassword);

	boolean matches(RawPassword rawPassword, PasswordHash passwordHash);

}
