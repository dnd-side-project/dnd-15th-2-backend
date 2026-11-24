package com.dnd.qello.direction.gate;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

// 위치기반서비스사업 신고 수리 전 실제 좌표 수집 차단 게이트(#379, 점검표 B1).
//
// fail-closed다. 기본값은 꺼짐이고, 켜려는데 신고 수리 참조가 없으면 기동을 실패시킨다.
// 경고만 남기고 뜨면 그 사이 실제 사용자 좌표가 저장된다.
//
// dev·local은 테스트 기기 좌표로 기능을 검증하는 비운영 환경이라 참조 검사를 생략한다.
// 별도 플래그가 아니라 활성 프로필로 판단하는 이유는, 운영 설정에 플래그가 잘못 들어가도
// 검사를 건너뛸 수 없게 하기 위해서다(테스트 계획 D4).
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LocationCollectionGateProperties.class)
public class LocationCollectionGate {

	private static final Profiles NON_PRODUCTION_PROFILES = Profiles.of("dev", "local");

	private final LocationCollectionGateProperties properties;
	private final Environment environment;

	public LocationCollectionGate(LocationCollectionGateProperties properties, Environment environment) {
		this.properties = properties;
		this.environment = environment;
	}

	@PostConstruct
	void verifyRegistrationReference() {
		if (!properties.enabled() || environment.acceptsProfiles(NON_PRODUCTION_PROFILES)) {
			return;
		}
		if (!properties.hasTraceableRegistrationReference()) {
			throw new IllegalStateException(
					"위치 수집을 켜려면 qello.location.collection.lbs-registration-reference에 "
							+ "위치기반서비스사업 신고 수리 참조가 있어야 합니다. "
							+ "docs/location-collection-gate.md의 조건을 확인한 뒤 값을 채워야 합니다.");
		}
	}

	public boolean isEnabled() {
		return properties.enabled();
	}

	/** 게이트가 꺼져 있으면 좌표를 받거나 읽는 요청을 거부한다. */
	public void ensureEnabled() {
		if (!properties.enabled()) {
			throw new DirectionException(DirectionErrorCode.LOCATION_COLLECTION_DISABLED, null,
					"위치 수집 게이트가 꺼져 있습니다");
		}
	}
}
