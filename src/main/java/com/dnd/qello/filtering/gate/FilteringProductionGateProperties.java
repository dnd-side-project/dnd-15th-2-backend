package com.dnd.qello.filtering.gate;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 필터링을 production에서 켜기 전에 사람이 확인해야 하는 항목(#113,
// INV-CMP-005·INV-CMP-006).
//
// 각 항목은 "확인했다"는 사실이 아니라 그 확인을 추적할 수 있는 참조를 값으로
// 받는다. boolean으로 두지 않는 이유: true 한 글자는 누가 무엇을 근거로
// 확인했는지를 남기지 않아 나중에 책임자를 특정할 수 없다.
//
// 그래서 값이 비어 있지 않은 것만으로는 부족하다. `true`나 `ok` 같은 토큰을
// 넣으면 boolean으로 두는 것과 결과가 같아지므로 그런 값은 미확인으로 취급한다.
// 항목별 상세는 docs/filtering-production-gate.md에 있다.
@ConfigurationProperties(prefix = "qello.filtering.production")
public record FilteringProductionGateProperties(
	boolean enabled,
	String dataProcessingAgreement,
	String dataResidency,
	String retentionPolicy,
	String contentSafetyPolicy,
	String secretHandling
) {

	// 승인 참조로 인정하지 않는 값. 확인 사실을 기록하는 대신 게이트만 통과하려는
	// 값들이다.
	private static final Set<String> PLACEHOLDER_VALUES = Set.of(
		"true", "false", "yes", "no", "y", "n", "1", "0", "ok", "done", "na", "n/a", "todo", "tbd", "-");

	// 추적 가능한 참조라면 승인 문서 번호나 결재 식별자를 담게 되므로 이 정도
	// 길이는 넘는다. 짧은 임의 문자열을 걸러내는 최소 기준이다.
	private static final int MINIMUM_REFERENCE_LENGTH = 8;

	/** 값이 없거나 승인 참조로 인정할 수 없는 확인 항목의 이름. 게이트는 이 집합이 비어야 연다. */
	public Set<String> missingConfirmations() {
		Set<String> missing = new LinkedHashSet<>();
		addIfUnconfirmed(missing, "dataProcessingAgreement", dataProcessingAgreement);
		addIfUnconfirmed(missing, "dataResidency", dataResidency);
		addIfUnconfirmed(missing, "retentionPolicy", retentionPolicy);
		addIfUnconfirmed(missing, "contentSafetyPolicy", contentSafetyPolicy);
		addIfUnconfirmed(missing, "secretHandling", secretHandling);
		return missing;
	}

	private static void addIfUnconfirmed(Set<String> missing, String name, String value) {
		if (!isTraceableReference(value)) {
			missing.add(name);
		}
	}

	private static boolean isTraceableReference(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		if (PLACEHOLDER_VALUES.contains(normalized)) {
			return false;
		}
		return normalized.length() >= MINIMUM_REFERENCE_LENGTH;
	}
}
