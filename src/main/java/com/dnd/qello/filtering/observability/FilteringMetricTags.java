package com.dnd.qello.filtering.observability;

import java.util.Set;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

// 필터링 지표가 쓸 수 있는 tag의 허용목록(#113, INV-CMP-001·INV-CMP-002).
//
// 금지목록이 아니라 허용목록인 이유: 금지목록은 새 필드가 생길 때마다 구멍이
// 난다. 허용된 키만 통과시키면 "실수로 원문이나 식별자를 tag에 실었다"가
// 구조적으로 불가능해진다.
//
// 값 길이 상한도 둔다. 허용된 키라도 자유 텍스트가 들어오면 관측 백엔드가
// 콘텐츠의 2차 사본이 되고, cardinality가 폭발해 지표 자체가 못 쓰게 된다.
public final class FilteringMetricTags {

	// 여기 없는 키는 쓸 수 없다. 새 tag가 필요하면 이 목록에 먼저 추가하면서
	// "이 값이 원문이나 개인 식별자가 될 수 있는가"를 검토한다.
	private static final Set<String> ALLOWED_KEYS = Set.of(
		"path",         // ANSWER | NICKNAME 등 판정 경로
		"language",     // ModerationLanguage
		"release",      // filter_release id
		"model",        // 공급자가 보고한 actual model snapshot
		"verdict",      // ALLOW | BLOCK
		"decision",     // 수동 결정 종류
		"outcome",      // SUCCESS | TIMEOUT | ERROR 등 결과 분류
		"reason_code",  // 정해진 집합의 사유 코드
		"band"          // 수동 검토 우선순위 band
	);

	private static final int MAX_VALUE_LENGTH = 60;

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
		if (!ALLOWED_KEYS.contains(key)) {
			throw new FilteringException(FilteringErrorCode.INVALID_TEXT, "tagKey",
				"허용되지 않은 metric tag 키입니다: " + key);
		}
		if (value == null || value.isBlank()) {
			throw new FilteringException(FilteringErrorCode.INVALID_TEXT, "tagValue",
				"metric tag 값이 비어 있습니다: " + key);
		}
		if (value.length() > MAX_VALUE_LENGTH) {
			throw new FilteringException(FilteringErrorCode.INVALID_TEXT, "tagValue",
				"metric tag 값이 너무 깁니다: " + key);
		}
		return Tag.of(key, value);
	}

	public static Set<String> allowedKeys() {
		return ALLOWED_KEYS;
	}
}
