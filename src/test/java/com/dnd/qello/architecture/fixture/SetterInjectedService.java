/*
 * Created at: 2026-09-02T17:42:43+09:00
 * Source scenario: TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-006
 */
package com.dnd.qello.architecture.fixture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SetterInjectedService {

	private Object dependency;

	@Autowired
	public void setDependency(Object dependency) {
		this.dependency = dependency;
	}
}
