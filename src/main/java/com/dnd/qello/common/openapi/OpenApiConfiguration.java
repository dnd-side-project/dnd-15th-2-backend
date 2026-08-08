package com.dnd.qello.common.openapi;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

// OpenAPI 문서의 메타데이터와 인증 방식.
//
// 스펙 엔드포인트가 꺼진 환경(운영 기본값)에서는 이 Bean도 만들지 않는다. 스펙은
// docs/api/openapi.json으로 커밋하고 CI가 최신성을 검사한다. 근거는
// docs/api-response.md에 있다.
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
public class OpenApiConfiguration {

	// 백오피스는 세션 쿠키로, 앱 API는 bearer 토큰으로 인증한다. 두 영역의 인증이
	// 다르므로 scheme도 나눠 선언한다. 근거는 ADR-0006에 있다.
	public static final String OPERATOR_SESSION_SCHEME = "operatorSession";
	public static final String APP_ACCESS_TOKEN_SCHEME = "appAccessToken";

	private static final String SESSION_COOKIE_NAME = "SESSION";

	@Bean
	OpenAPI qelloOpenApi() {
		return new OpenAPI()
			.info(new Info()
				.title("Qello API")
				.version("v1")
				.description("""
					Qello 백엔드 API. 모든 응답은 docs/api-response.md의 공통 계약을 따른다.
					성공은 status/data/timestamp, 실패는 status/message/errorDetail/timestamp를 갖는다.
					오류 코드 목록은 docs/error-codes.md에 있다."""))
			.components(new Components()
				.addSecuritySchemes(OPERATOR_SESSION_SCHEME, new SecurityScheme()
					.type(SecurityScheme.Type.APIKEY)
					.in(SecurityScheme.In.COOKIE)
					.name(SESSION_COOKIE_NAME)
					.description("백오피스 세션 쿠키. POST /admin/login 성공 시 발급된다."))
				.addSecuritySchemes(APP_ACCESS_TOKEN_SCHEME, new SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")
					.description("앱 액세스 토큰. 기기 등록 또는 토큰 재발급으로 받는다.")));
	}

	// 컨트롤러가 늘어나도 공통 규칙을 반복해 적지 않게 한다. 새 엔드포인트는 자동으로
	// 같은 오류 응답과 content type을 갖는다.
	@Bean
	OpenApiCustomizer qelloConventionCustomizer() {
		return new OpenApiConventionCustomizer();
	}

}
