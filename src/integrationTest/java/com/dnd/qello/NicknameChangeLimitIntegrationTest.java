/**
 * Created at: 2026-10-06T14:26:52+09:00
 * Source scenario: TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-006 through INT-010,
 * TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-INT-003
 */
package com.dnd.qello;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.service.NicknameRegistrationService;
import com.dnd.qello.filtering.moderation.NicknameModerationChecker;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome.Reason;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 닉네임 변경 주기(F01)와 사용자 단위 변경 시도 한도를 실제 저장소와 HTTP 경로로 검증한다(#315).
//
// 시도 한도는 2회/1일로 낮춘다. 카운터는 컨텍스트에 하나라 테스트마다 새 계정을 만들어 격리한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "account-persistence"})
@TestPropertySource(properties = "qello.account.nickname-change.attempt-rate-limit.max-requests=2")
@Import(NicknameChangeLimitIntegrationTest.TestDoubles.class)
class NicknameChangeLimitIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String COUNTRY_CODE = "KR";
	private static final Duration COOLDOWN = Duration.ofDays(30);
	// 보이지 않는 문자는 리터럴이나 유니코드 이스케이프 대신 코드 포인트 상수로 만든다(#317).
	private static final String ZWSP = Character.toString(0x200B);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private NicknameRegistrationService nicknameRegistrationService;

	@Autowired
	private NicknameModerationChecker moderationChecker;

	@Autowired
	private MutableClock clock;

	private final List<Boolean> transactionActiveAtModeration = new CopyOnWriteArrayList<>();
	private int installationSequence;

	@BeforeEach
	void resetFixtures() {
		clock.reset();
		jdbcTemplate.update("DELETE FROM device_credential");
		jdbcTemplate.update("DELETE FROM user_account");
		jdbcTemplate.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES (?, NULL, 'Korea', 'COUNTRY')
				ON CONFLICT (code) DO NOTHING
				""", COUNTRY_CODE);
		transactionActiveAtModeration.clear();
		reset(moderationChecker);
		doAnswer(invocation -> {
			transactionActiveAtModeration.add(TransactionSynchronizationManager.isActualTransactionActive());
			return NicknameModerationOutcome.allowed();
		}).when(moderationChecker).check(anyString(), any());
	}

	@Test
	@DisplayName("INT-006: 닉네임을 지정해 가입해도 마지막 닉네임 변경 시각은 비어 있다")
	void registrationNicknameDoesNotStartCooldown() throws Exception {
		Registered user = register("가입닉네임");

		assertThat(nicknameChangedAt(user.userId())).isNull();
	}

	@Test
	@DisplayName("INT-007: 주기 안의 재변경은 429 ACC-APP-004이고 닉네임과 moderation 호출 수가 그대로이며, 주기가 지나면 다시 바뀐다")
	void enforcesCooldownWithoutCallingModeration() throws Exception {
		Registered user = register(null);

		mockMvc.perform(changeNickname(user, "첫변경")).andExpect(status().isOk());
		Instant firstChangedAt = clock.instant();
		mockMvc.perform(changeNickname(user, "둘째변경"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.errorDetail.code").value("ACC-APP-004"));

		assertThat(nickname(user.userId())).isEqualTo("첫변경");
		assertThat(nicknameChangedAt(user.userId()).toInstant()).isEqualTo(firstChangedAt);
		verify(moderationChecker, times(1)).check(anyString(), any());

		clock.advance(COOLDOWN);
		mockMvc.perform(changeNickname(user, "셋째변경")).andExpect(status().isOk());

		assertThat(nickname(user.userId())).isEqualTo("셋째변경");
		assertThat(nicknameChangedAt(user.userId()).toInstant()).isEqualTo(firstChangedAt.plus(COOLDOWN));
		verify(moderationChecker, times(2)).check(anyString(), any());
		assertThat(transactionActiveAtModeration).containsOnly(false);
	}

	@Test
	@DisplayName("INT-008: moderation 거절로 실패한 시도도 세어 세 번째 요청은 429 ACC-APP-003이고 moderation을 부르지 않는다")
	void countsRejectedAttemptsTowardLimit() throws Exception {
		when(moderationChecker.check(anyString(), any()))
			.thenReturn(NicknameModerationOutcome.rejected(Reason.BLOCKED_BY_PRIMARY));
		Registered user = register(null);

		mockMvc.perform(changeNickname(user, "거절닉네임1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("ACC-DOM-005"));
		mockMvc.perform(changeNickname(user, "거절닉네임2"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("ACC-DOM-005"));
		mockMvc.perform(changeNickname(user, "거절닉네임3"))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.errorDetail.code").value("ACC-APP-003"));

		verify(moderationChecker, times(2)).check(anyString(), any());
		assertThat(nicknameChangedAt(user.userId())).isNull();
	}

	@Test
	@DisplayName("#317 INT-003: 정규화하면 비는 닉네임은 HTTP 400 ACC-VAL-002이고 moderation을 부르지 않으며 닉네임이 그대로다")
	void rejectsNicknameThatBecomesEmptyOverHttp() throws Exception {
		Registered user = register(null);

		mockMvc.perform(changeNickname(user, ZWSP))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorDetail.code").value("ACC-VAL-002"));

		verify(moderationChecker, never()).check(anyString(), any());
		assertThat(nickname(user.userId())).isNull();
		assertThat(nicknameChangedAt(user.userId())).isNull();
	}

	@Test
	@DisplayName("INT-009: V31 컬럼은 nullable timestamptz이고 이전 형태로 넣은 행은 NULL이다")
	void migrationAddsNullableTimestampColumn() {
		var column = jdbcTemplate.queryForMap("""
				SELECT data_type, is_nullable
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = 'user_account'
				  AND column_name = 'nickname_changed_at'
				""");
		Long id = jdbcTemplate.queryForObject("""
				INSERT INTO user_account (role, status, country_code, coarse_region_code, locale, timezone, nickname)
				VALUES ('USER', 'ACTIVE', ?, ?, 'ko-KR', 'Asia/Seoul', '이전형태')
				RETURNING id
				""", Long.class, COUNTRY_CODE, COUNTRY_CODE);

		assertThat(column).containsEntry("data_type", "timestamp with time zone").containsEntry("is_nullable", "YES");
		assertThat(nicknameChangedAt(id)).isNull();
	}

	@Test
	@DisplayName("INT-010: 같은 사용자가 동시에 닉네임을 바꾸면 하나만 반영되고 다른 하나는 주기 또는 낙관적 잠금으로 거절된다")
	void serializesConcurrentChangesForSameUser() throws Exception {
		Registered user = register(null);
		CyclicBarrier bothPassedPrecheck = new CyclicBarrier(2);
		doAnswer(invocation -> {
			bothPassedPrecheck.await(10, TimeUnit.SECONDS);
			return NicknameModerationOutcome.allowed();
		}).when(moderationChecker).check(anyString(), any());

		List<Attempt> attempts = race(
				() -> nicknameRegistrationService.changeNickname(user.userId(), "동시변경A"),
				() -> nicknameRegistrationService.changeNickname(user.userId(), "동시변경B"));

		List<Attempt> succeeded = attempts.stream().filter(attempt -> attempt.failure() == null).toList();
		List<Attempt> failed = attempts.stream().filter(attempt -> attempt.failure() != null).toList();
		assertThat(succeeded).hasSize(1);
		assertThat(failed).hasSize(1);
		Throwable failure = failed.getFirst().failure();
		if (failure instanceof AccountException accountException) {
			assertThat(accountException.getErrorCode()).isEqualTo(AccountErrorCode.NICKNAME_CHANGE_TOO_SOON);
		} else {
			assertThat(failure).isInstanceOf(OptimisticLockingFailureException.class);
		}
		assertThat(nickname(user.userId())).isEqualTo(succeeded.getFirst().account().getNickname());
	}

	private Registered register(String nickname) throws Exception {
		installationSequence++;
		String nicknameField = nickname == null ? "" : ",\n  \"nickname\": \"%s\"".formatted(nickname);
		MvcResult result = mockMvc.perform(post("/api/v1/auth/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "installationId": "nickname-limit-%d",
						  "platform": "IOS",
						  "countryCode": "%s",
						  "locale": "ko-KR",
						  "timezone": "Asia/Seoul"%s
						}
						""".formatted(installationSequence, COUNTRY_CODE, nicknameField)))
				.andExpect(status().isCreated())
				.andReturn();
		JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
		return new Registered(data.get("userId").asLong(), data.get("accessToken").asText());
	}

	private MockHttpServletRequestBuilder changeNickname(Registered user, String nickname) {
		return patch("/api/v1/users/me/nickname")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nickname\":\"%s\"}".formatted(nickname));
	}

	private String nickname(long userId) {
		return jdbcTemplate.queryForObject("SELECT nickname FROM user_account WHERE id = ?", String.class, userId);
	}

	private Timestamp nicknameChangedAt(long userId) {
		return jdbcTemplate.queryForObject(
				"SELECT nickname_changed_at FROM user_account WHERE id = ?", Timestamp.class, userId);
	}

	private static List<Attempt> race(Callable<Account> first, Callable<Account> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Attempt> firstFuture = executor.submit(() -> attempt(first, start));
			Future<Attempt> secondFuture = executor.submit(() -> attempt(second, start));
			start.countDown();
			return List.of(firstFuture.get(30, TimeUnit.SECONDS), secondFuture.get(30, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
		}
	}

	private static Attempt attempt(Callable<Account> action, CountDownLatch start) {
		try {
			start.await(5, TimeUnit.SECONDS);
			return new Attempt(action.call(), null);
		} catch (Throwable failure) {
			return new Attempt(null, failure);
		}
	}

	private record Registered(long userId, String accessToken) {
	}

	private record Attempt(Account account, Throwable failure) {
	}

	@TestConfiguration
	static class TestDoubles {

		@Bean
		@Primary
		NicknameModerationChecker nicknameModerationChecker() {
			NicknameModerationChecker checker = mock(NicknameModerationChecker.class);
			when(checker.check(anyString(), any())).thenReturn(NicknameModerationOutcome.allowed());
			return checker;
		}

		@Bean
		@Primary
		MutableClock nicknameLimitTestClock() {
			return new MutableClock(Instant.now().truncatedTo(ChronoUnit.MICROS));
		}
	}

	// 실제 현재 시각에서 시작한다. 앱 액세스 토큰 검증은 시스템 시계를 쓰므로 주기만큼 앞당겨도 발급한 토큰이 유효하다.
	static final class MutableClock extends Clock {

		private final Instant base;
		private final AtomicReference<Instant> current;

		MutableClock(Instant base) {
			this.base = base;
			this.current = new AtomicReference<>(base);
		}

		void reset() {
			current.set(base);
		}

		void advance(Duration duration) {
			current.updateAndGet(instant -> instant.plus(duration));
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return current.get();
		}
	}
}
