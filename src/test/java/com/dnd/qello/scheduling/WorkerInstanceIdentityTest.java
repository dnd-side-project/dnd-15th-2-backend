/**
 * Created at: 2026-08-27T14:22:03+09:00
 * Source scenario: TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-005
 */
package com.dnd.qello.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkerInstanceIdentityTest {

	@Test
	@DisplayName("UNIT-005: 한 identity는 안정적이고 서로 새로 만든 identity는 다르다")
	void ownerIsStablePerIdentityAndUniqueAcrossIdentities() {
		WorkerInstanceIdentity first = WorkerInstanceIdentity.random();
		WorkerInstanceIdentity second = WorkerInstanceIdentity.random();

		assertThat(first.owner()).isEqualTo(first.owner());
		assertThat(first.owner()).isNotBlank().hasSizeLessThanOrEqualTo(100);
		assertThat(second.owner()).isNotEqualTo(first.owner());
	}
}
