/**
 * Created at: 2026-08-18T21:20:00+09:00
 * Source scenario: TEST-PLAN-GH-113-FILTERING-OBSERVABILITY-AND-GATE-UNIT-007 ~ UNIT-009
 */
package com.dnd.qello.filtering.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FilteringProductionGateTest {

	@Test
	@DisplayName("UNIT-007: 확인 항목이 전부 채워지면 활성화를 허용한다")
	void allowsActivationWhenEveryConfirmationIsPresent() {
		FilteringProductionGate gate = new FilteringProductionGate(confirmed(true));

		assertThatCode(gate::verifyConfirmations).doesNotThrowAnyException();
		assertThat(gate.isProductionEnabled()).isTrue();
	}

	@Test
	@DisplayName("UNIT-008: 확인 항목이 하나라도 비면 활성화를 거부한다")
	void refusesActivationWhenAnyConfirmationIsMissing() {
		FilteringProductionGateProperties missingResidency = new FilteringProductionGateProperties(
			true, "legal-2026-08", "  ", "legal-2026-08", "safety-2026-08", "security-2026-08");
		FilteringProductionGate gate = new FilteringProductionGate(missingResidency);

		assertThatThrownBy(gate::verifyConfirmations)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("dataResidency");
	}

	@Test
	@DisplayName("UNIT-009: 활성화를 요청하지 않으면 확인 항목이 비어도 기동을 막지 않는다")
	void doesNotBlockWhenActivationIsNotRequested() {
		FilteringProductionGate gate = new FilteringProductionGate(
			new FilteringProductionGateProperties(false, null, null, null, null, null));

		assertThatCode(gate::verifyConfirmations).doesNotThrowAnyException();
		assertThat(gate.isProductionEnabled()).isFalse();
	}

	@Test
	@DisplayName("UNIT-014: 누락된 확인 항목을 모두 모아 보고한다")
	void reportsEveryMissingConfirmation() {
		FilteringProductionGateProperties empty =
			new FilteringProductionGateProperties(true, null, null, null, null, null);

		assertThat(empty.missingConfirmations()).containsExactly(
			"dataProcessingAgreement", "dataResidency", "retentionPolicy", "contentSafetyPolicy", "secretHandling");
		assertThat(confirmed(true).missingConfirmations()).isEmpty();
	}

	private static FilteringProductionGateProperties confirmed(boolean enabled) {
		return new FilteringProductionGateProperties(enabled, "legal-2026-08", "legal-2026-08", "legal-2026-08",
			"safety-2026-08", "security-2026-08");
	}
}
