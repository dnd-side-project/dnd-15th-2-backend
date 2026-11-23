/**
 * Created at: 2026-11-23T13:41:12Z
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-UNIT-010
 */
package com.dnd.qello.direction.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.AccountLockRepository;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.common.web.MockMvcTestSupport;
import com.dnd.qello.common.web.response.ApiResponseFactory;
import com.dnd.qello.direction.consent.LocationConsent;
import com.dnd.qello.direction.consent.LocationConsentAction;
import com.dnd.qello.direction.consent.LocationConsentProperties;
import com.dnd.qello.direction.consent.LocationConsentRepository;
import com.dnd.qello.direction.consent.LocationConsentService;
import com.dnd.qello.direction.gate.LocationCollectionGate;
import com.dnd.qello.direction.gate.LocationCollectionGateProperties;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocationConsentControllerMockMvcTest {

	private static final long USER_ID = 7L;
	private static final Instant NOW = Instant.parse("2026-11-23T13:41:12Z");
	private static final String PATH = "/api/v1/location-consents";
	private final AccountRepository accounts = mock(AccountRepository.class);
	private final AccountLockRepository locks = mock(AccountLockRepository.class);
	private final LocationConsentRepository history = mock(LocationConsentRepository.class);
	private final ActiveUserPresenceRepository presences = mock(ActiveUserPresenceRepository.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = buildMvc(true);
	}

	@Test
	@DisplayName("동의 요청의 외부 userId와 시각을 무시하고 인증 subject와 서버 시각으로 200 공통 응답을 만든다")
	void consentUsesAuthenticatedSubjectAndServerTime() throws Exception {
		when(locks.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(activeUser()));
		appendGeneratedId();

		mvc.perform(post(PATH).param("userId", "999").param("recordedAt", "2000-01-01T00:00:00Z")
				.contentType(MediaType.APPLICATION_JSON).content("{\"termsVersion\":\"test-v2\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("success"))
				.andExpect(jsonPath("$.timestamp").value(NOW.toString()))
				.andExpect(jsonPath("$.data.currentTermsVersion").value("test-v2"))
				.andExpect(jsonPath("$.data.consented").value(true))
				.andExpect(jsonPath("$.data.lastAction").value("CONSENT"))
				.andExpect(jsonPath("$.data.lastTermsVersion").value("test-v2"))
				.andExpect(jsonPath("$.data.recordedAt").value(NOW.toString()));

		ArgumentCaptor<LocationConsent> event = ArgumentCaptor.forClass(LocationConsent.class);
		verify(history).append(event.capture());
		assertThat(event.getValue()).isEqualTo(
				new LocationConsent(null, USER_ID, "test-v2", LocationConsentAction.CONSENT, NOW));
		verify(locks).findByIdForUpdate(USER_ID);
		verifyNoMoreInteractions(locks);
		verifyNoInteractions(accounts, presences);
	}

	@Test
	@DisplayName("철회는 외부 대상 대신 인증 본인 이력을 추가하고 본인 위치만 삭제해 200을 반환한다")
	void withdrawalIgnoresExternalTarget() throws Exception {
		when(locks.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(activeUser()));
		when(history.findLatestByUserId(USER_ID)).thenReturn(Optional.of(
				new LocationConsent(1L, USER_ID, "test-v1", LocationConsentAction.CONSENT, NOW.minusSeconds(60))));
		appendGeneratedId();

		mvc.perform(post(PATH + "/withdrawals").param("userId", "999"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("success"))
				.andExpect(jsonPath("$.data.consented").value(false))
				.andExpect(jsonPath("$.data.lastAction").value("WITHDRAW"))
				.andExpect(jsonPath("$.data.lastTermsVersion").value("test-v1"))
				.andExpect(jsonPath("$.data.recordedAt").value(NOW.toString()));

		ArgumentCaptor<LocationConsent> event = ArgumentCaptor.forClass(LocationConsent.class);
		verify(history).append(event.capture());
		assertThat(event.getValue()).isEqualTo(
				new LocationConsent(null, USER_ID, "test-v1", LocationConsentAction.WITHDRAW, NOW));
		verify(presences).deleteByUserId(USER_ID);
		verifyNoMoreInteractions(presences);
		verify(locks).findByIdForUpdate(USER_ID);
		verifyNoMoreInteractions(locks);
	}

	@Test
	@DisplayName("현재 상태 조회는 인증 본인만 조회하고 무이력 nullable 필드를 유지하며 이력을 추가하지 않는다")
	void currentReadsOnlyAuthenticatedSubjectWithoutAppend() throws Exception {
		when(accounts.findById(USER_ID)).thenReturn(Optional.of(activeUser()));

		mvc.perform(get(PATH + "/current").param("userId", "999"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("success"))
				.andExpect(jsonPath("$.data.currentTermsVersion").value("test-v2"))
				.andExpect(jsonPath("$.data.consented").value(false))
				.andExpect(jsonPath("$.data.lastAction").value(nullValue()))
				.andExpect(jsonPath("$.data.lastTermsVersion").value(nullValue()))
				.andExpect(jsonPath("$.data.recordedAt").value(nullValue()));

		verify(accounts).findById(USER_ID);
		verify(history).findLatestByUserId(USER_ID);
		verifyNoMoreInteractions(accounts, history);
		verifyNoInteractions(locks, presences);
	}

	@Test
	@DisplayName("세 동의 API 모두 인증이 없으면 401이며 이력·계정·위치 저장소에 접근하지 않는다")
	void rejectsUnauthenticatedRequestsAcrossAllRoutes() throws Exception {
		MockMvc unauthenticated = buildMvc(false);

		unauthenticated.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
				.content("{\"termsVersion\":\"test-v2\"}")).andExpect(status().isUnauthorized());
		unauthenticated.perform(post(PATH + "/withdrawals")).andExpect(status().isUnauthorized());
		unauthenticated.perform(get(PATH + "/current")).andExpect(status().isUnauthorized());

		verifyNoInteractions(accounts, locks, history, presences);
	}

	private void appendGeneratedId() {
		when(history.append(any())).thenAnswer(invocation -> {
			LocationConsent event = invocation.getArgument(0);
			return new LocationConsent(2L, event.userId(), event.termsVersion(), event.action(), event.recordedAt());
		});
	}

	private MockMvc buildMvc(boolean authenticated) {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		LocationConsentService service = new LocationConsentService(history, accounts, locks, presences,
				new LocationConsentProperties("test-v2"),
				new LocationCollectionGate(new LocationCollectionGateProperties(true, null), new StandardEnvironment()),
				clock);
		return MockMvcTestSupport.standalone(
				new LocationConsentController(service, new ApiResponseFactory(clock)), authenticated, USER_ID, clock);
	}

	private static Account activeUser() {
		return Account.createUser("KR", "TEST-REGION", "ko-KR", "Asia/Seoul", "test");
	}
}
