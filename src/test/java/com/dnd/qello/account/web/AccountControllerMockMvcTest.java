/*
 * Created at: 2026-08-19T03:30:00+09:00
 * Source scenario: TEST-PLAN-GH-168-NICKNAME-DUPLICATE-MODERATION-UNIT-016 through UNIT-021
 */
package com.dnd.qello.account.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.service.NicknameRegistrationService;
import com.dnd.qello.common.error.ApiErrorResponseFactory;
import com.dnd.qello.common.error.ConstraintExceptionMapper;
import com.dnd.qello.common.web.GlobalExceptionHandler;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class AccountControllerMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-08-19T05:00:00Z");
	private static final long USER_ID = 11L;

	@Mock
	private NicknameRegistrationService nicknameRegistrationService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = buildMockMvc(true);
	}

	private MockMvc buildMockMvc(boolean authenticated) {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		AccountController controller = new AccountController(nicknameRegistrationService, new ApiResponseFactory(clock));
		ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return MockMvcBuilders.standaloneSetup(controller)
			.setCustomArgumentResolvers(new AuthenticationResolver(authenticated))
			.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
			.setValidator(new LocalValidatorFactoryBean())
			.setControllerAdvice(new GlobalExceptionHandler(
				new ApiErrorResponseFactory(clock), new ConstraintExceptionMapper()))
			.build();
	}

	@Test
	@DisplayName("UNIT-016: 인증 정보가 없으면 401이며 서비스를 호출하지 않는다")
	void changeNicknameRequiresAuthentication() throws Exception {
		buildMockMvc(false).perform(patch("/api/v1/users/me/nickname")
				.contentType("application/json")
				.content("{\"nickname\":\"새닉네임\"}"))
			.andExpect(status().isUnauthorized());

		verify(nicknameRegistrationService, never()).changeNickname(anyLong(), any());
	}

	@Test
	@DisplayName("UNIT-017: 닉네임 필드가 blank면 400이며 서비스를 호출하지 않는다")
	void changeNicknameRejectsBlankNickname() throws Exception {
		mockMvc.perform(patch("/api/v1/users/me/nickname")
				.contentType("application/json")
				.content("{\"nickname\":\"  \"}"))
			.andExpect(status().isBadRequest());

		verify(nicknameRegistrationService, never()).changeNickname(anyLong(), any());
	}

	@Test
	@DisplayName("UNIT-018: 정상 요청은 인증 subject와 새 닉네임을 서비스로 전달하고 200과 새 닉네임만 반환한다")
	void changeNicknameReturnsOkWithNewNickname() throws Exception {
		when(nicknameRegistrationService.changeNickname(eq(USER_ID), eq("새닉네임"))).thenReturn(sampleAccount("새닉네임"));

		mockMvc.perform(patch("/api/v1/users/me/nickname")
				.contentType("application/json")
				.content("{\"nickname\":\"새닉네임\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.nickname").value("새닉네임"))
			.andExpect(jsonPath("$.data.length()").value(1));
	}

	@Test
	@DisplayName("UNIT-019: 서비스가 DUPLICATED_NICKNAME을 던지면 409다")
	void changeNicknameReturnsConflictForDuplicate() throws Exception {
		when(nicknameRegistrationService.changeNickname(eq(USER_ID), eq("중복닉네임"))).thenThrow(
			new AccountException(AccountErrorCode.DUPLICATED_NICKNAME, "nickname", "이미 사용 중인 닉네임입니다"));

		mockMvc.perform(patch("/api/v1/users/me/nickname")
				.contentType("application/json")
				.content("{\"nickname\":\"중복닉네임\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorDetail.code").value(AccountErrorCode.DUPLICATED_NICKNAME.code()));
	}

	@Test
	@DisplayName("UNIT-020: 서비스가 moderation 거부 오류를 던지면 400이다")
	void changeNicknameReturnsBadRequestForModerationRejection() throws Exception {
		when(nicknameRegistrationService.changeNickname(eq(USER_ID), eq("부적절한닉네임"))).thenThrow(
			new AccountException(AccountErrorCode.NICKNAME_REJECTED_BY_MODERATION, "nickname", "닉네임이 정책을 위반했습니다"));

		mockMvc.perform(patch("/api/v1/users/me/nickname")
				.contentType("application/json")
				.content("{\"nickname\":\"부적절한닉네임\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value(AccountErrorCode.NICKNAME_REJECTED_BY_MODERATION.code()));
	}

	@Test
	@DisplayName("UNIT-021: 서비스가 moderation UNAVAILABLE 오류를 던지면 503이며 400과 코드로 구분된다")
	void changeNicknameReturnsServiceUnavailableForModerationOutage() throws Exception {
		when(nicknameRegistrationService.changeNickname(eq(USER_ID), eq("닉네임"))).thenThrow(
			new AccountException(AccountErrorCode.NICKNAME_MODERATION_UNAVAILABLE, "nickname", "닉네임 검증 서비스를 사용할 수 없습니다"));

		mockMvc.perform(patch("/api/v1/users/me/nickname")
				.contentType("application/json")
				.content("{\"nickname\":\"닉네임\"}"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.errorDetail.code").value(AccountErrorCode.NICKNAME_MODERATION_UNAVAILABLE.code()));
	}

	private static Account sampleAccount(String nickname) {
		return Account.restore(USER_ID, AccountRole.USER, AccountStatus.ACTIVE, "KR", "KR-11", "ko-KR", "Asia/Seoul",
			nickname, null);
	}

	private static final class AuthenticationResolver implements HandlerMethodArgumentResolver {
		private final boolean authenticated;

		private AuthenticationResolver(boolean authenticated) {
			this.authenticated = authenticated;
		}

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return Authentication.class.isAssignableFrom(parameter.getParameterType());
		}

		@Override
		public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
			return authenticated
				? UsernamePasswordAuthenticationToken.authenticated(String.valueOf(USER_ID), null, List.of())
				: null;
		}
	}
}
