package com.dnd.qello.filtering.moderation;

import java.text.Normalizer;
import java.util.Set;
import java.util.regex.Pattern;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// TextNormalizer 실제 구현체(#287). normalizationRef가 가리키는 규칙 집합은 V1 하나다.
// 규칙은 공급자에 보내는 텍스트를 훼손하지 않는 범위로 제한한다 — NFKC, zero-width·제어
// 문자 제거, 연속 공백 축소, trim. 구분자·동형 문자 처리는 LocalRuleEngine 구현체가 비교용
// 접기로만 수행한다.
//
// 판정할 수 없는 입력은 빈 문자열로 통과시키지 않는다. null·정규화 후 빈 문자열과 지원하지
// 않는 ref는 예외로 끝낸다 — 공급자 호출 이전 단계에서 ALLOW로 새는 경로를 만들지 않는다.
// 예외 메시지에는 입력 원문을 담지 않는다.
public class UnicodeTextNormalizer implements TextNormalizer {

	public static final String NORMALIZATION_V1 = "normalization-v1";

	private static final Set<String> SUPPORTED_REFS = Set.of(NORMALIZATION_V1);
	// Cc(제어), Cf(서식: zero-width·방향 제어 포함). \t \n 같은 공백류는 제거하지 않고 공백 축소에서 다룬다.
	private static final Pattern HIDDEN_CHARACTERS = Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\\s]]");
	private static final Pattern WHITESPACE_RUN = Pattern.compile("(?U)\\s+");

	@Override
	public String normalize(String rawContent, String normalizationRef) {
		if (normalizationRef == null || !SUPPORTED_REFS.contains(normalizationRef)) {
			throw new FilteringException(FilteringErrorCode.INVALID_TEXT, "normalizationRef",
					"지원하지 않는 정규화 규칙 참조입니다");
		}
		if (rawContent == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "rawContent");
		}
		String compatibility = Normalizer.normalize(rawContent, Normalizer.Form.NFKC);
		String visible = HIDDEN_CHARACTERS.matcher(compatibility).replaceAll("");
		String normalized = WHITESPACE_RUN.matcher(visible).replaceAll(" ").strip();
		if (normalized.isEmpty()) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "rawContent");
		}
		return normalized;
	}
}
