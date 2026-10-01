/*
 * Created at: 2026-10-01T16:05:00+09:00
 * Source scenario: TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-008 through UNIT-013
 */
package com.dnd.qello.filtering.moderation;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class ResourceLocalRuleEngineTest {

	private static final String TEST_REF = "test-rules";

	private final ClassLoader classLoader = getClass().getClassLoader();
	private ListAppender<ILoggingEvent> logAppender;
	private Logger engineLogger;

	@BeforeEach
	void attachLogAppender() {
		engineLogger = (Logger) LoggerFactory.getLogger(ResourceLocalRuleEngine.class);
		logAppender = new ListAppender<>();
		logAppender.start();
		engineLogger.addAppender(logAppender);
	}

	@AfterEach
	void detachLogAppender() {
		engineLogger.detachAppender(logAppender);
	}

	@Test
	@DisplayName("UNIT-008: 규칙에 걸리는 입력은 해당 ruleId로 BLOCK을 반환한다")
	void blocksWithRuleIdWhenRuleMatches() {
		LocalRuleEngine engine = ResourceLocalRuleEngine.load(classLoader, List.of(TEST_REF));

		LocalRuleVerdict verdict = engine.evaluate("이건 zzbadwordzz 입니다", TEST_REF);

		assertThat(verdict).isEqualTo(LocalRuleVerdict.block("R-TEST-1"));
	}

	@Test
	@DisplayName("UNIT-009: 대소문자·구분자·전각 문자로 변형한 우회 입력도 같은 규칙에 적중한다")
	void matchesObfuscatedVariants() {
		LocalRuleEngine engine = ResourceLocalRuleEngine.load(classLoader, List.of(TEST_REF));

		for (String variant : new String[]{"ZZ-BadWord-ZZ", "z z b a d w o r d z z", "ｚｚbad_word．ｚｚ", "가.나_다 라"}) {
			assertThat(engine.evaluate(variant, TEST_REF).blocked()).as(variant).isTrue();
		}
	}

	@Test
	@DisplayName("UNIT-009: 규칙과 무관한 입력은 noMatch를 반환한다")
	void returnsNoMatchForUnrelatedContent() {
		LocalRuleEngine engine = ResourceLocalRuleEngine.load(classLoader, List.of(TEST_REF));

		assertThat(engine.evaluate("안녕하세요 반갑습니다", TEST_REF)).isEqualTo(LocalRuleVerdict.noMatch());
	}

	@Test
	@DisplayName("UNIT-010: 형식이 깨진 규칙 리소스는 로드 시점에 예외로 실패하고 빈 규칙으로 대체하지 않는다")
	void failsToLoadBrokenRuleResource() {
		assertThatThrownBy(() -> ResourceLocalRuleEngine.load(classLoader, List.of("broken-rules")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("broken-rules.rules:1")
				.satisfies(e -> assertThat(e.getMessage()).doesNotContain("R-BROKEN"));
	}

	@Test
	@DisplayName("UNIT-010: 존재하지 않는 규칙 리소스는 로드 시점에 예외로 실패한다")
	void failsToLoadMissingRuleResource() {
		assertThatThrownBy(() -> ResourceLocalRuleEngine.load(classLoader, List.of("no-such-rules")))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("UNIT-011: 지원하지 않는 localRulesetRef는 INVALID_TEXT 예외를 던진다")
	void rejectsUnsupportedRulesetRef() {
		LocalRuleEngine engine = ResourceLocalRuleEngine.load(classLoader, List.of(TEST_REF));

		for (String ref : new String[]{null, "", "other-rules", "latest"}) {
			assertThatThrownBy(() -> engine.evaluate("내용", ref))
					.isInstanceOf(FilteringException.class)
					.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_TEXT);
		}
	}

	@Test
	@DisplayName("UNIT-011: null 입력은 REQUIRED_VALUE_MISSING 예외를 던지고 noMatch로 새지 않는다")
	void rejectsNullContent() {
		LocalRuleEngine engine = ResourceLocalRuleEngine.load(classLoader, List.of(TEST_REF));

		assertThatThrownBy(() -> engine.evaluate(null, TEST_REF))
				.isInstanceOf(FilteringException.class)
				.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.REQUIRED_VALUE_MISSING);
	}

	@Test
	@DisplayName("UNIT-012: 적중 시 로그와 예외 메시지에 ruleId만 남고 규칙 원문·입력 원문은 없다")
	void logsOnlyRuleIdOnMatch() {
		LocalRuleEngine engine = ResourceLocalRuleEngine.load(classLoader, List.of(TEST_REF));
		String content = "내 닉네임은 zzbadwordzz 이다";

		engine.evaluate(content, TEST_REF);
		assertThatThrownBy(() -> engine.evaluate(content, "unsupported-ref")).isInstanceOf(FilteringException.class);

		List<String> messages = logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).contains("R-TEST-1").doesNotContain("zzbadwordzz").doesNotContain("닉네임");
	}

	@Test
	@DisplayName("UNIT-013: 운영 기본 규칙 집합은 항목이 없어 어떤 입력에도 noMatch를 반환한다")
	void defaultRulesetHasNoRules() {
		LocalRuleEngine engine = ResourceLocalRuleEngine.loadDefault();

		assertThat(engine.evaluate("zzbadwordzz 가나다라 아무 내용", ResourceLocalRuleEngine.LOCAL_RULES_V1))
				.isEqualTo(LocalRuleVerdict.noMatch());
	}
}
