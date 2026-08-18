package com.dnd.qello.filtering.gate;

import java.util.Set;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

// 필터링 production 활성화 게이트(#113, INV-CMP-005·INV-CMP-006).
//
// fail-closed다. 활성화를 요청했는데 확인 항목이 하나라도 비어 있으면 기동
// 자체를 실패시킨다 — 경고 로그만 남기고 뜨면 아무도 보지 않고, 그 사이
// 사용자 콘텐츠가 외부 moderation 공급자로 나간다.
//
// 비활성 상태에서는 확인 항목을 검사하지 않는다. 게이트는 "켜려는 시도"에만
// 개입하며, 개발·테스트 환경이 빈 값 때문에 뜨지 못하는 일을 만들지 않는다.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FilteringProductionGateProperties.class)
public class FilteringProductionGate {

	private final FilteringProductionGateProperties properties;

	public FilteringProductionGate(FilteringProductionGateProperties properties) {
		this.properties = properties;
	}

	@PostConstruct
	void verifyConfirmations() {
		if (!properties.enabled()) {
			return;
		}
		Set<String> missing = properties.missingConfirmations();
		if (!missing.isEmpty()) {
			throw new IllegalStateException(
				"필터링 production 활성화 확인 항목이 비어 있습니다: " + String.join(", ", missing)
					+ ". docs/filtering-production-gate.md의 항목을 책임자가 확인한 뒤 값을 채워야 합니다.");
		}
	}

	public boolean isProductionEnabled() {
		return properties.enabled();
	}
}
