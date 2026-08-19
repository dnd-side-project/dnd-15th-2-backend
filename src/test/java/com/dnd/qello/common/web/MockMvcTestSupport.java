/**
 * Created at: 2026-08-19T16:22:15+09:00
 * Source scenario: TEST-PLAN-GH-170-FEED-READ-INTERACTION-API-UNIT-013
 */
package com.dnd.qello.common.web;

import java.time.Clock;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.dnd.qello.common.error.ApiErrorResponseFactory;
import com.dnd.qello.common.error.ConstraintExceptionMapper;

/**
 * standalone MockMvc 컨트롤러 테스트가 공유하는 배선이다. Authentication 인자 해석,
 * Jackson 시각 포맷, GlobalExceptionHandler 연결이 컨트롤러마다 동일하게 반복돼 왔다 —
 * 이 한 곳에서만 조립한다.
 */
public final class MockMvcTestSupport {

	private MockMvcTestSupport() {
	}

	public static MockMvc standalone(Object controller, boolean authenticated, long userId, Clock clock) {
		ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return MockMvcBuilders.standaloneSetup(controller)
			.setCustomArgumentResolvers(authenticationResolver(authenticated, userId))
			.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
			.setControllerAdvice(new GlobalExceptionHandler(
				new ApiErrorResponseFactory(clock), new ConstraintExceptionMapper()))
			.build();
	}

	private static HandlerMethodArgumentResolver authenticationResolver(boolean authenticated, long userId) {
		return new AuthenticationResolver(authenticated, userId);
	}

	private record AuthenticationResolver(boolean authenticated, long userId) implements HandlerMethodArgumentResolver {

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return Authentication.class.isAssignableFrom(parameter.getParameterType());
		}

		@Override
		public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
			return authenticated
				? UsernamePasswordAuthenticationToken.authenticated(Long.toString(userId), null, List.of())
				: null;
		}
	}
}
