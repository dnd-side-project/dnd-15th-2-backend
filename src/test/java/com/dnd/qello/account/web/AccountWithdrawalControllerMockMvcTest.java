/*
 * Created at: 2026-10-09T18:14:46+09:00
 * Source scenario: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-021
 */
package com.dnd.qello.account.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.service.AccountWithdrawal;
import com.dnd.qello.account.service.AccountWithdrawalService;
import com.dnd.qello.common.web.MockMvcTestSupport;
import com.dnd.qello.common.web.response.ApiResponseFactory;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountWithdrawalControllerMockMvcTest {

	private static final Instant NOW = Instant.parse("2026-10-09T09:00:00Z");
	private static final Instant SCHEDULED_DELETION_AT = Instant.parse("2026-11-08T09:00:00Z");
	private static final long USER_ID = 11L;
	private static final String PATH = "/api/v1/users/me/withdrawal";

	@Mock
	private AccountWithdrawalService withdrawalService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = buildMockMvc(true);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-021: 탈퇴 요청은 인증 subject로 서비스를 부르고 200과 상태·삭제 예정 시각을 돌려준다")
	void requestWithdrawalReturnsPendingStatusAndScheduledDeletion() throws Exception {
		when(withdrawalService.request(USER_ID)).thenReturn(
			new AccountWithdrawal(AccountStatus.WITHDRAWAL_PENDING, SCHEDULED_DELETION_AT));

		mockMvc.perform(post(PATH))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("WITHDRAWAL_PENDING"))
			.andExpect(jsonPath("$.data.scheduledDeletionAt").value("2026-11-08T09:00:00Z"))
			.andExpect(jsonPath("$.data.length()").value(2));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-021: 탈퇴 철회는 200과 ACTIVE, 삭제 예정 시각 null을 돌려준다")
	void cancelWithdrawalReturnsActiveStatus() throws Exception {
		when(withdrawalService.cancel(USER_ID)).thenReturn(new AccountWithdrawal(AccountStatus.ACTIVE, null));

		mockMvc.perform(delete(PATH))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("ACTIVE"))
			.andExpect(jsonPath("$.data.scheduledDeletionAt").value(nullValue()));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-021: 서비스가 상태 전이 오류를 던지면 요청·철회 모두 409와 ACC-DOM-004다")
	void returnsConflictForInvalidStatusTransition() throws Exception {
		when(withdrawalService.request(USER_ID)).thenThrow(new AccountException(
			AccountErrorCode.INVALID_STATUS_TRANSITION, "status", "활성 계정만 탈퇴를 요청할 수 있습니다"));
		when(withdrawalService.cancel(USER_ID)).thenThrow(new AccountException(
			AccountErrorCode.INVALID_STATUS_TRANSITION, "status", "탈퇴 유예 기간이 끝났습니다"));

		mockMvc.perform(post(PATH))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorDetail.code").value("ACC-DOM-004"));
		mockMvc.perform(delete(PATH))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorDetail.code").value("ACC-DOM-004"));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-021: 인증 정보가 없으면 요청·철회 모두 401이고 서비스를 호출하지 않는다")
	void requiresAuthentication() throws Exception {
		MockMvc unauthenticated = buildMockMvc(false);

		unauthenticated.perform(post(PATH)).andExpect(status().isUnauthorized());
		unauthenticated.perform(delete(PATH)).andExpect(status().isUnauthorized());

		verify(withdrawalService, never()).request(anyLong());
		verify(withdrawalService, never()).cancel(anyLong());
	}

	private MockMvc buildMockMvc(boolean authenticated) {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		return MockMvcTestSupport.standalone(
				new AccountWithdrawalController(withdrawalService, new ApiResponseFactory(clock)), authenticated,
				USER_ID,
				clock);
	}

}
