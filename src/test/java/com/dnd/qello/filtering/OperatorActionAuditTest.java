/**
 * Created at: 2026-08-18T21:10:00+09:00
 * Source scenario: TEST-PLAN-GH-113-FILTERING-OBSERVABILITY-AND-GATE-UNIT-004 ~ UNIT-006
 */
package com.dnd.qello.filtering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.domain.OperatorActionAudit;
import com.dnd.qello.filtering.domain.OperatorActionTargetType;
import com.dnd.qello.filtering.domain.OperatorActionType;
import com.dnd.qello.filtering.domain.OperatorReason;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

class OperatorActionAuditTest {

	private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

	@Test
	@DisplayName("UNIT-004: actor·행위·대상·근거·정책 버전·시간을 모두 필수로 검증한다")
	void requiresAllFourAuditElements() {
		assertThatCode(OperatorActionAuditTest::audit).doesNotThrowAnyException();

		assertThatThrownBy(() -> OperatorActionAudit.record(0L, OperatorActionType.RELEASE_PROMOTE,
			OperatorActionTargetType.FILTER_RELEASE, "1", "CODE", "근거", "v1", NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);

		assertThatThrownBy(() -> OperatorActionAudit.record(9L, null,
			OperatorActionTargetType.FILTER_RELEASE, "1", "CODE", "근거", "v1", NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.REQUIRED_VALUE_MISSING);

		assertThatThrownBy(() -> OperatorActionAudit.record(9L, OperatorActionType.RELEASE_PROMOTE,
			null, "1", "CODE", "근거", "v1", NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.REQUIRED_VALUE_MISSING);

		assertThatThrownBy(() -> OperatorActionAudit.record(9L, OperatorActionType.RELEASE_PROMOTE,
			OperatorActionTargetType.FILTER_RELEASE, "1", "CODE", "근거", "v1", null))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.REQUIRED_VALUE_MISSING);
	}

	@Test
	@DisplayName("UNIT-005: 공백뿐인 근거와 정책 버전은 거절한다")
	void rejectsBlankReasonAndPolicyVersion() {
		assertThatThrownBy(() -> OperatorActionAudit.record(9L, OperatorActionType.RELEASE_PROMOTE,
			OperatorActionTargetType.FILTER_RELEASE, "1", "CODE", "   ", "v1", NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_TEXT);

		assertThatThrownBy(() -> OperatorActionAudit.record(9L, OperatorActionType.RELEASE_PROMOTE,
			OperatorActionTargetType.FILTER_RELEASE, "1", "  ", "근거", "v1", NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_TEXT);

		assertThatThrownBy(() -> OperatorActionAudit.record(9L, OperatorActionType.RELEASE_PROMOTE,
			OperatorActionTargetType.FILTER_RELEASE, "1", "CODE", "근거", " ", NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_TEXT);

		// OperatorReason도 같은 계약을 갖는다 — 경계 계층에서 걸러지지 않은 값이
		// 도메인까지 내려와도 여기서 막힌다.
		assertThatThrownBy(() -> new OperatorReason("CODE", "  "))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_TEXT);
	}

	@Test
	@DisplayName("UNIT-006: 길이 상한을 넘는 근거와 대상 키는 거절한다")
	void rejectsOverlongText() {
		String tooLongText = "가".repeat(501);
		String tooLongKey = "k".repeat(201);

		assertThatThrownBy(() -> OperatorActionAudit.record(9L, OperatorActionType.RELEASE_PROMOTE,
			OperatorActionTargetType.FILTER_RELEASE, "1", "CODE", tooLongText, "v1", NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_TEXT);

		assertThatThrownBy(() -> OperatorActionAudit.record(9L, OperatorActionType.RELEASE_PROMOTE,
			OperatorActionTargetType.FILTER_RELEASE, tooLongKey, "CODE", "근거", "v1", NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_TEXT);

		assertThat(audit().reasonText()).isEqualTo("근거");
	}

	private static OperatorActionAudit audit() {
		return OperatorActionAudit.record(9L, OperatorActionType.RELEASE_PROMOTE,
			OperatorActionTargetType.FILTER_RELEASE, "1", "CODE", "근거", "v1", NOW);
	}
}
