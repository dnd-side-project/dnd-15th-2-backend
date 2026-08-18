package com.dnd.qello.filtering.observability;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

// 필터링 지표가 쓸 수 있는 tag의 허용목록(#113, INV-CMP-001·INV-CMP-002).
//
// 키만 제한하면 부족하다. 허용된 키에 임의 문자열을 넣을 수 있으면 이메일이나
// 답변 조각이 그대로 tag 값이 된다. 그래서 키마다 값의 형태까지 고정한다 —
// 자유 텍스트가 통과할 수 있는 키가 하나도 없어야 한다.
//
// 금지목록이 아니라 허용목록인 이유는 같다. 금지목록은 새 필드가 생길 때마다
// 구멍이 나지만, 허용된 것만 통과시키면 실수로 원문·식별자를 싣는 경로 자체가
// 없어진다.
public final class FilteringMetricTags {

	// 판정 경로·언어·판정 결과처럼 코드에서 정의한 열거값이 들어가는 키.
	// 대문자와 밑줄만 허용하므로 사람이 쓴 문장이나 이메일은 통과할 수 없다.
	private static final Pattern ENUM_TOKEN = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");

	// filter_release 식별자. 숫자만 허용한다.
	private static final Pattern NUMERIC_ID = Pattern.compile("[0-9]{1,19}");

	// 공급자가 보고한 model snapshot. 소문자·숫자와 구분자만 허용한다.
	private static final Pattern MODEL_SNAPSHOT = Pattern.compile("[a-z0-9][a-z0-9._-]{0,59}");

	// 허용된 키와 그 키가 받을 수 있는 값의 형태. 여기 없는 키는 쓸 수 없고,
	// 형태에 맞지 않는 값도 쓸 수 없다. 새 tag가 필요하면 이 표에 추가하면서
	// "이 값이 원문이나 개인 식별자가 될 수 있는가"를 먼저 검토한다.
	private static final Map<String, Pattern> ALLOWED = Map.of(
		"path", ENUM_TOKEN,          // ANSWER | NICKNAME 등 판정 경로
		"language", ENUM_TOKEN,      // ModerationLanguage
		"release", NUMERIC_ID,       // filter_release id
		"model", MODEL_SNAPSHOT,     // 공급자가 보고한 actual model snapshot
		"verdict", ENUM_TOKEN,       // ALLOW | BLOCK
		"decision", ENUM_TOKEN,      // 수동 결정 종류
		"outcome", ENUM_TOKEN,       // SUCCESS | TIMEOUT | ERROR 등 결과 분류
		"reason_code", ENUM_TOKEN,   // 정해진 집합의 사유 코드
		"band", ENUM_TOKEN           // 수동 검토 우선순위 band
	);

	private FilteringMetricTags() { }

	public static Tags of(String... keyValues) {
		if (keyValues.length % 2 != 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_TEXT, "tags", "tag는 키와 값의 쌍이어야 합니다");
		}
		Tags tags = Tags.empty();
		for (int i = 0; i < keyValues.length; i += 2) {
			tags = tags.and(tag(keyValues[i], keyValues[i + 1]));
		}
		return tags;
	}

	public static Tag tag(String key, String value) {
		Pattern allowedForm = ALLOWED.get(key);
		if (allowedForm == null) {
			throw new FilteringException(FilteringErrorCode.INVALID_TEXT, "tagKey",
				"허용되지 않은 metric tag 키입니다: " + key);
		}
		if (value == null || !allowedForm.matcher(value).matches()) {
			// 값 자체를 예외 메시지에 넣지 않는다. 거절된 값이 바로 원문이나
			// 식별자일 수 있고, 그것을 로그로 흘리면 막으려던 유출이 그대로 일어난다.
			throw new FilteringException(FilteringErrorCode.INVALID_TEXT, "tagValue",
				"metric tag 값이 허용된 형태가 아닙니다: " + key);
		}
		return Tag.of(key, value);
	}

	public static Set<String> allowedKeys() {
		return ALLOWED.keySet();
	}
}
