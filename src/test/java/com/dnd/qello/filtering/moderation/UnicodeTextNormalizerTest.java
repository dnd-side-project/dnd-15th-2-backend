/*
 * Created at: 2026-10-01T15:31:17+09:00
 * Source scenario: TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-001 through UNIT-007,
 * TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400-UNIT-001 through UNIT-002 (added 2026-10-07T11:01:09+09:00)
 */
package com.dnd.qello.filtering.moderation;

import java.text.Normalizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.error.EmptyNormalizedTextException;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnicodeTextNormalizerTest {

	private static final String ZWSP = String.valueOf((char) 0x200B);
	private static final String ZWNJ = String.valueOf((char) 0x200C);
	private static final String ZWJ = String.valueOf((char) 0x200D);
	private static final String WORD_JOINER = String.valueOf((char) 0x2060);
	private static final String BOM = String.valueOf((char) 0xFEFF);
	private static final String BELL = String.valueOf((char) 0x0007);
	private static final String IDEOGRAPHIC_SPACE = String.valueOf((char) 0x3000);
	private static final String REF = UnicodeTextNormalizer.NORMALIZATION_V1;

	private final TextNormalizer normalizer = new UnicodeTextNormalizer();

	@Test
	@DisplayName("UNIT-001: 전각·호환 문자는 NFKC로 정규화된다")
	void appliesNfkc() {
		assertThat(normalizer.normalize("ＡＢＣ１２３", REF)).isEqualTo("ABC123");
		assertThat(normalizer.normalize("ﬁ", REF)).isEqualTo("fi");
	}

	@Test
	@DisplayName("UNIT-002: zero-width·제어 문자는 제거되고 연속 공백은 하나로 줄며 앞뒤는 trim된다")
	void removesHiddenCharactersAndCollapsesWhitespace() {
		String raw = "  a" + ZWSP + "b\u0007c \t\n d" + WORD_JOINER + BOM + "  ";

		assertThat(normalizer.normalize(raw, REF)).isEqualTo("abc d");
	}

	@Test
	@DisplayName("UNIT-002: 내부 구분자와 동형 문자는 변경하지 않는다")
	void keepsSeparatorsAndConfusables() {
		assertThat(normalizer.normalize("a.b-c_d", REF)).isEqualTo("a.b-c_d");
		assertThat(normalizer.normalize("раураl", REF)).isEqualTo("раураl");
	}

	@Test
	@DisplayName("UNIT-003: null 입력은 REQUIRED_VALUE_MISSING 예외를 던진다")
	void rejectsNullContent() {
		assertThatThrownBy(() -> normalizer.normalize(null, REF))
				.isInstanceOf(FilteringException.class)
				.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.REQUIRED_VALUE_MISSING);
	}

	@Test
	@DisplayName("UNIT-004: 공백·zero-width 문자만 있는 입력은 빈 결과가 되어 예외를 던진다")
	void rejectsContentThatNormalizesToEmpty() {
		for (String raw : new String[]{"", "   ", ZWSP + ZWNJ + ZWJ, " \t\n" + IDEOGRAPHIC_SPACE + WORD_JOINER + " "}) {
			assertThatThrownBy(() -> normalizer.normalize(raw, REF))
					.isInstanceOf(FilteringException.class)
					.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.REQUIRED_VALUE_MISSING);
		}
	}

	@Test
	@DisplayName("UNIT-005: 지원하지 않는 normalizationRef는 예외를 던지고 메시지에 입력 원문이 없다")
	void rejectsUnsupportedRefWithoutLeakingContent() {
		String secretContent = "민감한닉네임원문";

		for (String ref : new String[]{null, "", "normalization-v2", "latest"}) {
			assertThatThrownBy(() -> normalizer.normalize(secretContent, ref))
					.isInstanceOf(FilteringException.class)
					.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_TEXT)
					.satisfies(e -> assertThat(String.valueOf(e.getMessage())).doesNotContain(secretContent));
		}
	}

	@Test
	@DisplayName("UNIT-006: 분리된 한글 자모(NFD)는 조합형 한글로 정규화된다")
	void composesDecomposedHangul() {
		String decomposed = Normalizer.normalize("한글", Normalizer.Form.NFD);

		assertThat(decomposed).isNotEqualTo("한글");
		assertThat(normalizer.normalize(decomposed, REF)).isEqualTo("한글");
	}

	@Test
	@DisplayName("UNIT-007: 이미 정규화된 입력을 다시 정규화해도 결과가 같다")
	void isIdempotent() {
		String once = normalizer.normalize(" Ａ" + ZWSP + "b  한글 ", REF);

		assertThat(normalizer.normalize(once, REF)).isEqualTo(once);
	}

	@Test
	@DisplayName("#318 UNIT-001: 정규화 후 빈 입력은 입력 오류 전용 예외이고 오류 코드·field는 그대로이며 메시지에 원문이 없다")
	void throwsDedicatedExceptionWhenNormalizedContentIsEmpty() {
		for (String raw : new String[]{"", "   ", ZWSP, BOM, IDEOGRAPHIC_SPACE, ZWSP + ZWNJ + ZWJ}) {
			assertThatThrownBy(() -> normalizer.normalize(raw, REF))
					.isInstanceOf(EmptyNormalizedTextException.class)
					.isInstanceOf(FilteringException.class)
					.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.REQUIRED_VALUE_MISSING)
					.hasFieldOrPropertyWithValue("field", "rawContent")
					.hasMessage(FilteringErrorCode.REQUIRED_VALUE_MISSING.message());
		}
	}

	@Test
	@DisplayName("#318 UNIT-002: null 입력과 지원하지 않는 normalizationRef는 입력 오류 전용 예외가 아니다")
	void nullContentAndUnsupportedRefAreNotInputErrors() {
		assertThatThrownBy(() -> normalizer.normalize(null, REF))
				.isInstanceOf(FilteringException.class)
				.isNotInstanceOf(EmptyNormalizedTextException.class)
				.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.REQUIRED_VALUE_MISSING);

		// 내용이 비게 될 입력이라도 ref 오류(서버 설정 문제)가 먼저 판정된다.
		for (String ref : new String[]{null, "norm-v1"}) {
			assertThatThrownBy(() -> normalizer.normalize(ZWSP, ref))
					.isInstanceOf(FilteringException.class)
					.isNotInstanceOf(EmptyNormalizedTextException.class)
					.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_TEXT);
		}
	}
}
