/*
 * Created at: 2026-08-19T02:10:00+09:00
 * Source scenario: TEST-PLAN-GH-168-NICKNAME-DUPLICATE-MODERATION-UNIT-003 through UNIT-007
 */
package com.dnd.qello.filtering.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

class MinimalModerationComponentsTest {

	@Test
	@DisplayName("UNIT-003: flagged 카테고리가 있으면 FlaggedCategoryPolicyEngine이 BLOCK을 반환한다")
	void policyEngineBlocksWhenFlagged() {
		PolicyEngine policyEngine = new FlaggedCategoryPolicyEngine();
		ModerationProviderResult flagged =
			new ModerationProviderResult(true, Map.of("harassment", true), Map.of("harassment", 0.9), "model-x");

		FilterVerdict verdict = policyEngine.decide(flagged, FilterTargetType.NICKNAME, ModerationLanguage.KO, "ref");

		assertThat(verdict).isEqualTo(FilterVerdict.BLOCK);
	}

	@Test
	@DisplayName("UNIT-004: flagged 카테고리가 없으면 FlaggedCategoryPolicyEngine이 ALLOW를 반환한다")
	void policyEngineAllowsWhenNotFlagged() {
		PolicyEngine policyEngine = new FlaggedCategoryPolicyEngine();
		ModerationProviderResult clean =
			new ModerationProviderResult(false, Map.of("harassment", false), Map.of("harassment", 0.01), "model-x");

		FilterVerdict verdict = policyEngine.decide(clean, FilterTargetType.NICKNAME, ModerationLanguage.KO, "ref");

		assertThat(verdict).isEqualTo(FilterVerdict.ALLOW);
	}

	@Test
	@DisplayName("UNIT-005: PassthroughTextNormalizer는 앞뒤 공백만 제거하고 원문을 그대로 반환한다")
	void textNormalizerOnlyTrims() {
		TextNormalizer normalizer = new PassthroughTextNormalizer();

		String normalized = normalizer.normalize("  닉네임후보  ", "normalization-ref");

		assertThat(normalized).isEqualTo("닉네임후보");
	}

	@Test
	@DisplayName("UNIT-006: NoMatchLocalRuleEngine은 어떤 입력에도 항상 noMatch를 반환한다")
	void localRuleEngineAlwaysNoMatch() {
		LocalRuleEngine ruleEngine = new NoMatchLocalRuleEngine();

		LocalRuleVerdict verdict = ruleEngine.evaluate("아무 내용이나", "ruleset-ref");

		assertThat(verdict).isEqualTo(LocalRuleVerdict.noMatch());
	}

	@Test
	@DisplayName("UNIT-007: UnavailableSecondaryModerationClient는 대기 없이 즉시 SECONDARY_MODERATOR_UNAVAILABLE을 던진다")
	void secondaryClientPlaceholderFailsImmediately() {
		SecondaryModerationClient client = new UnavailableSecondaryModerationClient();

		assertThatThrownBy(() -> client.moderate("닉네임", ModerationLanguage.KO))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.SECONDARY_MODERATOR_UNAVAILABLE);
	}

	@Test
	@DisplayName("NoOpNicknameModerationChecker는 항상 Allowed를 반환하고 예외를 던지지 않는다")
	void noOpCheckerAlwaysAllows() {
		NicknameModerationChecker checker = new NoOpNicknameModerationChecker();

		NicknameModerationOutcome outcome = checker.check("아무닉네임", ModerationLanguage.KO);

		assertThat(outcome).isEqualTo(NicknameModerationOutcome.allowed());
	}
}
