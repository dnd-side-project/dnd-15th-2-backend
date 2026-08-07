package com.dnd.qello.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.dnd.qello.auth.web.AuthEntryPoints;

// 백오피스와 앱 API의 보안 설정을 두 체인으로 나눈다.
//
// 한 체인으로 묶으면 앱 API를 위해 CSRF를 끄는 순간 백오피스가 함께 노출되고,
// 백오피스를 위해 세션을 만들면 앱 API가 상태를 갖는다. 경로로 갈라 두면 그 사고가
// 구조적으로 막힌다. 근거는 docs/adr/0006-split-operator-and-device-authentication.md에 있다.
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

	public static final String ADMIN_PATH = "/admin/**";
	public static final String LOGIN_PATH = "/admin/login";
	public static final String LOGOUT_PATH = "/admin/logout";
	public static final String CSRF_PATH = "/admin/csrf";
	public static final String API_PATH = "/api/**";

	private final AuthEntryPoints authEntryPoints;

	public SecurityConfiguration(AuthEntryPoints authEntryPoints) {
		this.authEntryPoints = authEntryPoints;
	}

	@Bean
	@Order(1)
	SecurityFilterChain backofficeSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
			.securityMatcher(ADMIN_PATH)
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
				// 로그인 성공 시 세션 ID를 새로 발급한다. 로그인 전에 심어진 세션 ID가
				// 인증 후에도 유효하면 session fixation 공격이 성립한다.
				.sessionFixation(fixation -> fixation.newSession()))
			.csrf(csrf -> csrf
				// 브라우저 클라이언트가 토큰을 읽어 헤더로 돌려보낼 수 있어야 한다.
				.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
			.authorizeHttpRequests(requests -> requests
				// 로그인 POST에도 CSRF를 적용하므로(login CSRF 방어) 토큰을 받아갈 GET이
				// 인증 없이 열려 있어야 한다. 이 둘이 세트다.
				.requestMatchers(HttpMethod.GET, CSRF_PATH).permitAll()
				.requestMatchers(HttpMethod.POST, LOGIN_PATH).permitAll()
				.anyRequest().hasRole("OPERATOR"))
			.exceptionHandling(handling -> handling
				.authenticationEntryPoint(authEntryPoints.unauthorized())
				.accessDeniedHandler(authEntryPoints.forbidden()))
			// 폼 로그인과 HTTP Basic을 쓰지 않는다. 인증은 OperatorLoginController만 담당한다.
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			.logout(logout -> logout.disable())
			.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain appApiSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
			.securityMatcher(API_PATH)
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// Bearer 토큰은 브라우저가 자동으로 실어 보내지 않으므로 CSRF 대상이 아니다.
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(requests -> requests
				// 기기 등록과 토큰 재발급은 #73에서 추가한다. 그때까지 열린 경로가 없다.
				.anyRequest().authenticated())
			.exceptionHandling(handling -> handling
				.authenticationEntryPoint(authEntryPoints.unauthorized())
				.accessDeniedHandler(authEntryPoints.forbidden()))
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			.logout(logout -> logout.disable())
			.build();
	}

	// 두 체인 어디에도 속하지 않는 경로. 매칭되는 체인이 없으면 Spring Security가
	// 요청을 그대로 통과시키므로, 남은 경로를 명시적으로 막는 체인을 마지막에 둔다.
	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	SecurityFilterChain fallbackSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(requests -> requests.anyRequest().denyAll())
			.exceptionHandling(handling -> handling
				.authenticationEntryPoint(authEntryPoints.unauthorized())
				.accessDeniedHandler(authEntryPoints.forbidden()))
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			.logout(logout -> logout.disable())
			.build();
	}

}
