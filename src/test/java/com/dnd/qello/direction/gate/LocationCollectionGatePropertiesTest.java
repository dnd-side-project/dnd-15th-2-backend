/**
 * Created at: 2026-11-23T16:43:12+09:00
 * Source scenario: TEST-PLAN-GH-379-LOCATION-GATE-UNIT-001
 */
package com.dnd.qello.direction.gate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LocationCollectionGatePropertiesTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "true", "false", "yes", "no", "y", "n", "1", "0", "ok", "done", "na", "n/a",
			"todo", "tbd", "-", "OK", " TRUE ", "Done", "N/A", "1234567", "  abcdefg  "})
	@DisplayName("비어 있거나 placeholder이거나 8자 미만인 신고 수리 참조는 미확인으로 판정한다")
	void rejectsUntraceableReferences(String reference) {
		LocationCollectionGateProperties properties = new LocationCollectionGateProperties(true, reference);

		assertThat(properties.hasTraceableRegistrationReference()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {"12345678", "LBS-2026-000123", "  LBS-2026-000123  "})
	@DisplayName("8자 이상이고 placeholder가 아닌 신고 수리 참조만 확인으로 판정한다")
	void acceptsTraceableReferences(String reference) {
		LocationCollectionGateProperties properties = new LocationCollectionGateProperties(true, reference);

		assertThat(properties.hasTraceableRegistrationReference()).isTrue();
	}
}
