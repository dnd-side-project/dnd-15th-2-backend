package com.dnd.qello.auth.config;

import java.util.List;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.dnd.qello.auth.web.AuthEntryPoints;

// 백오피스와 앱 API의 보안 설정을 두 체인으로 나눈다.
//
// 한 체인으로 묶으면 앱 API를 위해 CSRF를 끄는 순간 백오피스가 함께 노출되고,
// 백오피스를 위해 세션을 만들면 앱 API가 상태를 갖는다. 경로로 갈라 두면 그 사고가
// 구조적으로 막힌다. 근거는 docs/adr/0006-split-operator-and-device-authentication.md에 있다.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfiguration {

	public static final String ADMIN_PATH = "/admin/**";
	public static final String LOGIN_PATH = "/admin/login";
	public static final String LOGOUT_PATH = "/admin/logout";
	public static final String CSRF_PATH = "/admin/csrf";
	public static final String API_PATH = "/api/**";
	public static final String OPERATOR_API_PATH = "/api/v1/operator/**";
	public static final String DEVICE_REGISTRATION_PATH = "/api/v1/auth/devices";
	public static final String DEVICE_TOKEN_PATH = "/api/v1/auth/token";
	public static final String API_DOCS_PATH = "/v3/api-docs";
	public static final String API_DOCS_SUBPATH = "/v3/api-docs/**";

	private final AuthEntryPoints authEntryPoints;

	public SecurityConfiguration(AuthEntryPoints authEntryPoints) {
		this.authEntryPoints = authEntryPoints;
	}

	// #229 EC2 테스트 서버를 프론트가 브라우저에서 호출하려면 CORS가 필요하다.
	// 허용 origin은 qello.web.allowed-origins로만 주입하고 코드에 고정하지
	// 않는다. /api/** 체인에만 연결해(appApiSecurityFilterChain) 세션 기반
	// 체인(/admin/**, /api/v1/operator/**)에는 영향을 주지 않는다.
	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		// 디바이스 인증은 Authorization 헤더의 Bearer 토큰만 쓰고 쿠키를 쓰지
		// 않는다. Access-Control-Allow-Credentials는 필요 없다.

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration(API_PATH, configuration);
		return source;
	}

	// 운영 기본값(management.endpoints.enabled-by-default=false, exposure
	// include="")에서는 health endpoint 자체가 없어 EndpointRequest.to(...)가
	// 아무 경로도 매칭하지 않는다 — 이 체인이 있어도 기존 프로필의 동작은
	// 바뀌지 않는다. dev 프로필처럼 health를 노출한 환경에서만 실제로 열린다
	// (#230). ObservabilitySecurityConfiguration과 달리 관리 포트를 분리하지
	// 않으므로(#229 보안 그룹이 8080만 연다) 같은 dispatcher의 이 체인 하나로
	// 충분하다.
	@Bean
	@Order(-1)
	SecurityFilterChain actuatorHealthSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
				.securityMatcher(EndpointRequest.to("health"))
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.logout(logout -> logout.disable())
				.build();
	}

	// OpenAPI 스펙 엔드포인트. springdoc이 켜진 환경에만 존재한다.
	//
	// 운영 기본값은 springdoc.api-docs.enabled=false라 라우트 자체가 생기지 않으므로,
	// 이 체인이 없으면 fallback denyAll이 받는다. 인증 규칙이 아니라 기능 비활성으로
	// 노출을 막는 것이 이 설정의 의도다. 스펙은 docs/api/openapi.json으로 커밋한다.
	@Bean
	@Order(0)
	@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
	SecurityFilterChain apiDocsSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
				.securityMatcher(API_DOCS_PATH, API_DOCS_SUBPATH)
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.logout(logout -> logout.disable())
				.build();
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

	// 이슈 #156 본문이 명시한 대로 /api/v1/operator/**를 쓰지만, 인증은 /api/**의
	// JWT bearer(appApiSecurityFilterChain)가 아니라 백오피스와 같은 세션 기반이다
	// (TASK.md Scope decision 2). API_PATH("/api/**")보다 더 구체적인 매처를 먼저
	// 두지 않으면 appApiSecurityFilterChain이 이 경로까지 삼켜 앱 JWT로 운영자
	// API에 접근할 수 있게 된다 — 그래서 @Order를 appApiSecurityFilterChain보다
	// 앞세운다.
	@Bean
	@Order(2)
	SecurityFilterChain operatorReportCaseSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
				.securityMatcher(OPERATOR_API_PATH)
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				.csrf(csrf -> csrf
						// /admin/login이 발급한 세션의 CSRF 토큰을 그대로 재사용한다 — 별도
						// 로그인 엔드포인트가 이 체인에는 없다.
						.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
				.authorizeHttpRequests(requests -> requests.anyRequest().hasRole("OPERATOR"))
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(authEntryPoints.unauthorized())
						.accessDeniedHandler(authEntryPoints.forbidden()))
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.logout(logout -> logout.disable())
				.build();
	}

	@Bean
	@Order(3)
	SecurityFilterChain appApiSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
				.securityMatcher(API_PATH)
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// Bearer 토큰은 브라우저가 자동으로 실어 보내지 않으므로 CSRF 대상이 아니다.
				.csrf(csrf -> csrf.disable())
				// corsConfigurationSource 빈을 자동으로 찾아 쓴다.
				.cors(Customizer.withDefaults())
				.authorizeHttpRequests(requests -> requests
						// 브라우저가 Authorization 헤더를 실어 보내는 요청 앞에 보내는 CORS
						// preflight는 자격 증명을 갖지 않는다. 인증이 필요한 경로 앞에서도
						// 막히면 실제 요청 자체가 나가지 못하므로 OPTIONS는 전부 연다.
						.requestMatchers(HttpMethod.OPTIONS, API_PATH).permitAll()
						// 등록과 재발급은 액세스 토큰을 아직 갖지 못한 상태에서 호출해야 하므로
						// 인증 없이 연다. 나머지 /api/**는 유효한 액세스 토큰이 있어야 한다.
						.requestMatchers(HttpMethod.POST, DEVICE_REGISTRATION_PATH, DEVICE_TOKEN_PATH).permitAll()
						.anyRequest().authenticated())
				// NimbusJwtDecoder(HS256)로 액세스 토큰을 검증한다. role 클레임 기반 인가는
				// 다음 앱 API 이슈에서 다룬다.
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
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
