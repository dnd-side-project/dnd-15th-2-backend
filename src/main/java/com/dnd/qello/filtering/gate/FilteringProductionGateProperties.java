package com.dnd.qello.filtering.gate;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 필터링을 production에서 켜기 전에 사람이 확인해야 하는 항목(#113,
// INV-CMP-005·INV-CMP-006).
//
// 각 항목은 "확인했다"는 사실을 남긴 확인자 식별 문자열을 값으로 받는다.
// boolean으로 두지 않는 이유: true 한 글자는 누가 확인했는지를 남기지 않아
// 나중에 책임자를 특정할 수 없다. 항목별 상세는
// docs/filtering-production-gate.md에 있다.
@ConfigurationProperties(prefix = "qello.filtering.production")
public record FilteringProductionGateProperties(
	boolean enabled,
	String dataProcessingAgreement,
	String dataResidency,
	String retentionPolicy,
	String contentSafetyPolicy,
	String secretHandling
) {

	// 값이 비어 있는 확인 항목의 이름. 게이트는 이 집합이 비어 있을 때만 연다.
	public Set<String> missingConfirmations() {
		Set<String> missing = new java.util.LinkedHashSet<>();
		addIfBlank(missing, "dataProcessingAgreement", dataProcessingAgreement);
		addIfBlank(missing, "dataResidency", dataResidency);
		addIfBlank(missing, "retentionPolicy", retentionPolicy);
		addIfBlank(missing, "contentSafetyPolicy", contentSafetyPolicy);
		addIfBlank(missing, "secretHandling", secretHandling);
		return missing;
	}

	private static void addIfBlank(Set<String> missing, String name, String value) {
		if (value == null || value.isBlank()) {
			missing.add(name);
		}
	}
}
