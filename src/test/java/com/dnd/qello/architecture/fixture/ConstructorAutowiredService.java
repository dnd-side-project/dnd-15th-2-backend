/*
 * Created at: 2026-09-02T17:42:43+09:00
 * Source scenario: TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-007
 */
package com.dnd.qello.architecture.fixture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConstructorAutowiredService {

	private final Object dependency;

	@Autowired
	ConstructorAutowiredService(Object dependency) {
		this.dependency = dependency;
	}
}
