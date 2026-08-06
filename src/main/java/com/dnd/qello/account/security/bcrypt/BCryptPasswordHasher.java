package com.dnd.qello.account.security.bcrypt;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.dnd.qello.account.domain.PasswordHash;
import com.dnd.qello.account.security.PasswordHasher;
import com.dnd.qello.account.security.RawPassword;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@Override
	public PasswordHash hash(RawPassword rawPassword) {
		return new PasswordHash(passwordEncoder.encode(rawPassword.value()));
	}

	@Override
	public boolean matches(RawPassword rawPassword, PasswordHash passwordHash) {
		return passwordEncoder.matches(rawPassword.value(), passwordHash.value());
	}

}
