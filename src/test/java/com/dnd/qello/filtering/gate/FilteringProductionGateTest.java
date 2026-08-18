/**
 * Created at: 2026-08-18T21:20:00+09:00
 * Source scenario: TEST-PLAN-GH-113-FILTERING-OBSERVABILITY-AND-GATE-UNIT-007 ~ UNIT-009,
 *                  UNIT-014, UNIT-015, INT-008, INT-009
 */
package com.dnd.qello.filtering.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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

	@Test
	@DisplayName("UNIT-015: boolean 리터럴과 짧은 임의 문자열은 승인 참조로 인정하지 않는다")
	void rejectsPlaceholderApprovalReferences() {
		for (String placeholder : new String[] {"true", "TRUE", "yes", "ok", "1", "N/A", "todo", "-"}) {
			FilteringProductionGateProperties properties = new FilteringProductionGateProperties(
				true, placeholder, "legal-2026-08", "legal-2026-08", "safety-2026-08", "security-2026-08");
			assertThat(properties.missingConfirmations())
				.as("placeholder %s", placeholder)
				.containsExactly("dataProcessingAgreement");
		}

		FilteringProductionGateProperties tooShort = new FilteringProductionGateProperties(
			true, "abc", "legal-2026-08", "legal-2026-08", "safety-2026-08", "security-2026-08");
		assertThat(tooShort.missingConfirmations()).containsExactly("dataProcessingAgreement");
	}

	@Test
	@DisplayName("INT-008: 확인 항목이 비어 있는데 활성화를 요청하면 컨텍스트 기동이 실패한다")
	void contextFailsToStartWhenConfirmationsAreMissing() {
		new ApplicationContextRunner()
			.withUserConfiguration(FilteringProductionGate.class)
			.withPropertyValues(
				"qello.filtering.production.enabled=true",
				"qello.filtering.production.data-processing-agreement=legal-2026-08")
			// 누락 항목 이름은 게이트가 던진 근본 원인에 있다. BeanCreationException의
			// 메시지에는 초기화 실패 사실만 남는다.
			.run(context -> assertThat(context)
				.hasFailed()
				.getFailure()
				.hasStackTraceContaining("dataResidency")
				.hasStackTraceContaining("retentionPolicy"));
	}

	@Test
	@DisplayName("INT-009: 확인 항목이 모두 채워지면 활성화 상태로 정상 기동한다")
	void contextStartsWhenEveryConfirmationIsPresent() {
		new ApplicationContextRunner()
			.withUserConfiguration(FilteringProductionGate.class)
			.withPropertyValues(
				"qello.filtering.production.enabled=true",
				"qello.filtering.production.data-processing-agreement=legal-2026-08-dpa",
				"qello.filtering.production.data-residency=legal-2026-08-residency",
				"qello.filtering.production.retention-policy=legal-2026-08-retention",
				"qello.filtering.production.content-safety-policy=safety-2026-08-policy",
				"qello.filtering.production.secret-handling=security-2026-08-secrets")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(FilteringProductionGate.class).isProductionEnabled()).isTrue();
			});
	}

	@Test
	@DisplayName("INT-009b: 비활성 기본값에서는 확인 항목이 비어도 정상 기동한다")
	void contextStartsWhenActivationIsNotRequested() {
		new ApplicationContextRunner()
			.withUserConfiguration(FilteringProductionGate.class)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(FilteringProductionGate.class).isProductionEnabled()).isFalse();
			});
	}

	private static FilteringProductionGateProperties confirmed(boolean enabled) {
		return new FilteringProductionGateProperties(enabled, "legal-2026-08", "legal-2026-08", "legal-2026-08",
			"safety-2026-08", "security-2026-08");
	}
}
