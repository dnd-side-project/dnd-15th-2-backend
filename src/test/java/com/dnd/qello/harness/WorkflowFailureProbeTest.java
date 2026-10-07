/*
 * Created at: 2026-10-07T17:40:00+09:00
 * Source scenario: GH-313-FAILING-TEST-PROBE
 */
package com.dnd.qello.harness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowFailureProbeTest {

	@Test
	@DisplayName("#313 실험용: 실패한 test check가 PR 제목 수정 뒤 어떻게 표시되는지 보려고 일부러 실패한다")
	void failsOnPurpose() {
		assertThat(false).isTrue();
	}
}
