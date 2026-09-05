package com.dnd.qello.auth.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// 관측 endpoint를 observability 프로필에서만 연다(#218).
//
// management.server.port로 포트를 분리해도 management child context는 부모의
// springSecurityFilterChain을 재사용한다. 따라서 이 체인이 없으면
// SecurityConfiguration의 fallback denyAll이 health와 prometheus까지 막는다.
// 반대로 custom SecurityFilterChain이 하나라도 있으면 Actuator 기본 보안
// 자동 설정은 back off하므로, 관리 포트의 인가 규칙은 이 체인이 단독으로 정한다.
// 근거는 docs/superpowers/specs/2026-09-05-observability-metrics-exposure-design.md
// DEC-B1-002에 있다.
@Configuration(proxyBeanMethods = false)
@Profile("observability")
public class ObservabilitySecurityConfiguration {

	public static final String HEALTH_ENDPOINT_ID = "health";
	public static final String PROMETHEUS_ENDPOINT_ID = "prometheus";

	// EndpointRequest.toAnyEndpoint()를 쓰지 않는다(DEC-B1-003). 그것을 쓰면 나중에
	// 다른 endpoint를 활성화하는 순간 그 endpoint까지 자동으로 permitAll 아래로
	// 들어온다. 노출 목록이 보안 체인과 따로 자라지 않도록 두 ID를 직접 적는다.
	//
	// SecurityConfiguration의 apiDocs 체인(@Order(0))보다 앞선다. 이 matcher에
	// 걸리지 않는 나머지 /actuator/**는 기존 fallback denyAll이 처리한다.
	@Bean
	@Order(-1)
	SecurityFilterChain observabilitySecurityFilterChain(HttpSecurity http) throws Exception {
		return http
				.securityMatcher(EndpointRequest.to(HEALTH_ENDPOINT_ID, PROMETHEUS_ENDPOINT_ID))
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.logout(logout -> logout.disable())
				.build();
	}
}
