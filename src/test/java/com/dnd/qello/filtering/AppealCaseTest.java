/**
 * Created at: 2026-08-17T19:20:00+09:00
 * Source scenario: TEST-PLAN-GH-112-AUTHOR-APPEAL-AND-MANUAL-RESTORE-UNIT-001 ~ UNIT-015
 */
package com.dnd.qello.filtering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.domain.AppealAcceptance;
import com.dnd.qello.filtering.domain.AppealAcceptanceReasonCode;
import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.domain.AppealCaseStatus;
import com.dnd.qello.filtering.domain.AppealDecision;
import com.dnd.qello.filtering.domain.AppealWindow;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

class AppealCaseTest {

	private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
	private static final Instant DECIDED_AT = Instant.parse("2026-03-01T00:00:00Z");
	private static final long TARGET_ID = 11L;
	private static final long FILTER_DECISION_ID = 99L;
	private static final long APPELLANT_USER_ID = 7L;
	private static final long OPERATOR_USER_ID = 3L;

	@Test
	@DisplayName("UNIT-001: 접수 기간을 6개월(184일)보다 짧게 만들 수 없다")
	void rejectsAcceptanceWindowShorterThanSixMonths() {
		assertThatThrownBy(() -> new AppealWindow(Duration.ofDays(183)))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);

		assertThatCode(() -> new AppealWindow(Duration.ofDays(184))).doesNotThrowAnyException();
		assertThatCode(() -> new AppealWindow(Duration.ofDays(365))).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("UNIT-002: 기산점으로부터 183일 시점의 접수는 기간 안으로 판정한다")
	void acceptsFilingWithinWindow() {
		AppealAcceptance acceptance = AppealWindow.GLOBAL.evaluate(DECIDED_AT, DECIDED_AT.plus(Duration.ofDays(183)));

		assertThat(acceptance.accepted()).isTrue();
		assertThat(acceptance.reasonCode()).isEqualTo(AppealAcceptanceReasonCode.WITHIN_WINDOW);
		assertThat(acceptance.effectiveWindowStartedAt()).isEqualTo(DECIDED_AT);
	}

	@Test
	@DisplayName("UNIT-003: 기산점으로부터 185일 시점의 접수는 기간 경과로 거절한다")
	void rejectsFilingAfterWindowElapsed() {
		AppealAcceptance acceptance = AppealWindow.GLOBAL.evaluate(DECIDED_AT, DECIDED_AT.plus(Duration.ofDays(185)));

		assertThat(acceptance.accepted()).isFalse();
		assertThat(acceptance.reasonCode()).isEqualTo(AppealAcceptanceReasonCode.WINDOW_ELAPSED);
	}

	@Test
	@DisplayName("UNIT-004: 기산점을 알 수 없으면 거절하지 않고 접수 시각을 기산점으로 삼아 허용한다")
	void acceptsFilingWhenWindowStartIsUnknown() {
		AppealAcceptance acceptance = AppealWindow.GLOBAL.evaluate(null, NOW);

		assertThat(acceptance.accepted()).isTrue();
		assertThat(acceptance.reasonCode()).isEqualTo(AppealAcceptanceReasonCode.WINDOW_UNVERIFIABLE);
		assertThat(acceptance.effectiveWindowStartedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("UNIT-005: 기산점이 현재보다 미래면 정합성이 깨진 것으로 보고 접수를 허용한다")
	void acceptsFilingWhenWindowStartIsInTheFuture() {
		AppealAcceptance acceptance = AppealWindow.GLOBAL.evaluate(NOW.plusSeconds(1), NOW);

		assertThat(acceptance.accepted()).isTrue();
		assertThat(acceptance.reasonCode()).isEqualTo(AppealAcceptanceReasonCode.WINDOW_UNVERIFIABLE);
		assertThat(acceptance.effectiveWindowStartedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("UNIT-006: 접수하면 만료 시각이 기산점 + 184일로 고정된다")
	void fixesExpiryAtFilingTime() {
		AppealCase filed = file(AppealWindow.GLOBAL.evaluate(DECIDED_AT, NOW));

		assertThat(filed.status()).isEqualTo(AppealCaseStatus.OPEN);
		assertThat(filed.windowStartedAt()).isEqualTo(DECIDED_AT);
		assertThat(filed.expiresAt()).isEqualTo(DECIDED_AT.plus(Duration.ofDays(184)));
		assertThat(filed.acceptanceReasonCode()).isEqualTo(AppealAcceptanceReasonCode.WITHIN_WINDOW);
		assertThat(filed.decision()).isNull();
	}

	@Test
	@DisplayName("UNIT-007: fallback으로 접수하면 접수 시각을 기산점으로 두고 만료를 그 기준으로 고정한다")
	void fixesExpiryFromFilingTimeOnFallback() {
		AppealCase filed = file(AppealWindow.GLOBAL.evaluate(null, NOW));

		assertThat(filed.windowStartedAt()).isEqualTo(NOW);
		assertThat(filed.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(184)));
		assertThat(filed.acceptanceReasonCode()).isEqualTo(AppealAcceptanceReasonCode.WINDOW_UNVERIFIABLE);
	}

	@Test
	@DisplayName("UNIT-008: 거절된 접수는 case로 만들 수 없다")
	void refusesToFileRejectedAcceptance() {
		AppealAcceptance elapsed = AppealWindow.GLOBAL.evaluate(DECIDED_AT, DECIDED_AT.plus(Duration.ofDays(185)));

		assertThatThrownBy(() -> file(elapsed))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.APPEAL_WINDOW_ELAPSED);
	}

	@Test
	@DisplayName("UNIT-009: 이미 종료된 case는 다시 결정할 수 없다")
	void refusesToDecideResolvedCase() {
		AppealCase resolved = file(withinWindow()).decide(AppealDecision.UPHOLD_HIDDEN, OPERATOR_USER_ID, NOW, null);

		assertThatThrownBy(() -> resolved.decide(AppealDecision.OVERTURN_HIDDEN, OPERATOR_USER_ID, NOW, null))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_APPEAL_CASE_STATUS);
	}

	@Test
	@DisplayName("UNIT-010: UPHOLD_HIDDEN으로 종결하면 결정 필드가 함께 채워지고 복원 콜백 대상이 아니다")
	void resolvesWithUpholdDecision() {
		AppealCase resolved = file(withinWindow()).decide(AppealDecision.UPHOLD_HIDDEN, OPERATOR_USER_ID, NOW, null);

		assertThat(resolved.status()).isEqualTo(AppealCaseStatus.RESOLVED);
		assertThat(resolved.decision()).isEqualTo(AppealDecision.UPHOLD_HIDDEN);
		assertThat(resolved.decidedAt()).isEqualTo(NOW);
		assertThat(resolved.decidedByOperatorUserId()).isEqualTo(OPERATOR_USER_ID);
		assertThat(resolved.requiresRestoreCallback()).isFalse();
	}

	@Test
	@DisplayName("UNIT-011: OVERTURN_HIDDEN은 복원 차단 사유가 없을 때만 복원 콜백 대상이 된다")
	void requiresRestoreCallbackOnlyWhenNotBlocked() {
		AppealCase overturned = file(withinWindow()).decide(AppealDecision.OVERTURN_HIDDEN, OPERATOR_USER_ID, NOW, null);
		AppealCase blocked = file(withinWindow())
			.decide(AppealDecision.OVERTURN_HIDDEN, OPERATOR_USER_ID, NOW, "ACCOUNT_BLOCKED");

		assertThat(overturned.requiresRestoreCallback()).isTrue();
		assertThat(blocked.requiresRestoreCallback()).isFalse();
		assertThat(blocked.restoreBlockedReasonCode()).isEqualTo("ACCOUNT_BLOCKED");
	}

	@Test
	@DisplayName("UNIT-012: 복원 차단 사유는 UPHOLD_HIDDEN 결정에 붙일 수 없다")
	void refusesRestoreBlockedReasonOnUphold() {
		AppealCase open = file(withinWindow());

		assertThatThrownBy(() -> open.decide(AppealDecision.UPHOLD_HIDDEN, OPERATOR_USER_ID, NOW, "ACCOUNT_BLOCKED"))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_APPEAL_CASE_STATUS);
	}

	@Test
	@DisplayName("UNIT-013: 만료 시각은 앞당길 수 없고 늦출 수만 있다")
	void extendsExpiryOnly() {
		AppealCase filed = file(withinWindow());
		Instant current = filed.expiresAt();

		assertThatThrownBy(() -> filed.extendExpiry(current.minus(Duration.ofDays(1))))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.APPEAL_EXPIRY_NOT_EXTENDABLE);
		assertThatThrownBy(() -> filed.extendExpiry(current))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.APPEAL_EXPIRY_NOT_EXTENDABLE);

		assertThat(filed.extendExpiry(current.plus(Duration.ofDays(30))).expiresAt())
			.isEqualTo(current.plus(Duration.ofDays(30)));
	}

	@Test
	@DisplayName("UNIT-014: 필수 식별자와 상태 조합을 검증한다")
	void validatesRequiredValues() {
		AppealAcceptance acceptance = withinWindow();

		assertThatThrownBy(() -> AppealCase.file(
			FilterTargetType.ANSWER, TARGET_ID, FILTER_DECISION_ID, 0L, acceptance, AppealWindow.GLOBAL, NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);

		assertThatThrownBy(() -> AppealCase.file(
			null, TARGET_ID, FILTER_DECISION_ID, APPELLANT_USER_ID, acceptance, AppealWindow.GLOBAL, NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.REQUIRED_VALUE_MISSING);
	}

	@Test
	@DisplayName("UNIT-015: 상태와 결정 필드가 어긋난 조합, 6개월 미만 만료, 거절 사유 코드는 복원할 수 없다")
	void refusesInconsistentRestoredState() {
		Instant expiresAt = DECIDED_AT.plus(Duration.ofDays(184));

		// RESOLVED인데 결정 필드가 비어 있다.
		assertThatThrownBy(() -> AppealCase.restore(1L, FilterTargetType.ANSWER, TARGET_ID, FILTER_DECISION_ID,
			APPELLANT_USER_ID, AppealCaseStatus.RESOLVED, DECIDED_AT, expiresAt,
			AppealAcceptanceReasonCode.WITHIN_WINDOW, null, null, null, null, NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_APPEAL_CASE_STATUS);

		// 만료가 기산점으로부터 6개월보다 이르다.
		assertThatThrownBy(() -> AppealCase.restore(1L, FilterTargetType.ANSWER, TARGET_ID, FILTER_DECISION_ID,
			APPELLANT_USER_ID, AppealCaseStatus.OPEN, DECIDED_AT, DECIDED_AT.plus(Duration.ofDays(183)),
			AppealAcceptanceReasonCode.WITHIN_WINDOW, null, null, null, null, NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);

		// 거절 결과인 WINDOW_ELAPSED는 저장된 case의 사유가 될 수 없다.
		assertThatThrownBy(() -> AppealCase.restore(1L, FilterTargetType.ANSWER, TARGET_ID, FILTER_DECISION_ID,
			APPELLANT_USER_ID, AppealCaseStatus.OPEN, DECIDED_AT, expiresAt,
			AppealAcceptanceReasonCode.WINDOW_ELAPSED, null, null, null, null, NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);
	}

	private static AppealAcceptance withinWindow() {
		return AppealWindow.GLOBAL.evaluate(DECIDED_AT, NOW);
	}

	private static AppealCase file(AppealAcceptance acceptance) {
		return AppealCase.file(FilterTargetType.ANSWER, TARGET_ID, FILTER_DECISION_ID, APPELLANT_USER_ID, acceptance,
			AppealWindow.GLOBAL, NOW);
	}
}
