/*
 * Created at: 2026-09-02T00:38:56+09:00
 * Source scenario: TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-009
 */
package com.dnd.qello.architecture.fixture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FieldInjectedService {

	@Autowired
	private Object dependency;
}
