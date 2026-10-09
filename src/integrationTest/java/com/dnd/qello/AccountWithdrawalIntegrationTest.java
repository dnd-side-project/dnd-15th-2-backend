/**
 * Created at: 2026-10-09T18:36:18+09:00
 * Source scenario: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-002, INT-003, INT-005, INT-007 through INT-009,
 * INT-016
 */
package com.dnd.qello;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.service.AccountStatusService;
import com.dnd.qello.account.sweep.AccountWithdrawalSweepWorker;
import com.dnd.qello.notification.domain.DeliveryStatus;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.domain.PushDeviceStatus;
import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.push.security.AesGcmPushTokenProtector;
import com.dnd.qello.notification.push.security.ProtectedPushToken;
import com.dnd.qello.notification.push.security.PushToken;
import com.dnd.qello.notification.push.security.PushTokenKeyRing;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 앱 탈퇴 요청·철회·재발급·차단을 실제 HTTP 경로와 PostGIS로 검증한다(#337).
//
// 시계는 실제 현재 시각에서 시작한다. 앱 액세스 토큰 검증은 시스템 시계를 쓰므로 유예 기간만큼 앞당겨도 발급한 토큰이
// 유효하다. 테스트마다 처음 시각으로 되돌린다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AccountWithdrawalIntegrationTest.TestClockConfiguration.class)
class AccountWithdrawalIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String COUNTRY_CODE = "KR";
	private static final Duration GRACE_PERIOD = Duration.ofDays(30);
	private static final String CURRENT_KEY_ID = "gh337-current";
	private static final byte[] ENCRYPTION_KEY = fixedKey((byte) 0x62);
	private static final byte[] FINGERPRINT_KEY = fixedKey((byte) 0x72);
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private OutboxEventRepository outboxEvents;

	@Autowired
	private AccountStatusService accountStatusService;

	@Autowired
	private AccountWithdrawalSweepWorker sweepWorker;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void resetFixtures() {
		clock.reset();
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM push_device");
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM device_credential");
		jdbc.update("DELETE FROM user_account");
		jdbc.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES (?, NULL, 'Korea', 'COUNTRY')
				ON CONFLICT DO NOTHING
				""", COUNTRY_CODE);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-002: 탈퇴 요청은 계정을 유예 중으로 바꾸고 푸시 기기 전부 해지, 미발송 전달 취소, 위치 삭제를 한 번에 하며 자격증명과 닉네임은 남긴다")
	void requestWithdrawalCleansUpPushAndPresenceButKeepsCredentials() throws Exception {
		Registered user = register("탈퇴요청");
		mockMvc.perform(updatePresence(user.accessToken())).andExpect(status().isOk());
		PushDevice ios = pushDevice(user.userId(), PushPlatform.IOS, "gh337-int002-ios");
		PushDevice android = pushDevice(user.userId(), PushPlatform.ANDROID, "gh337-int002-android");
		long pending = delivery(user.userId(), ios.id(), DeliveryStatus.PENDING);
		long failed = delivery(user.userId(), android.id(), DeliveryStatus.FAILED);
		long sent = delivery(user.userId(), ios.id(), DeliveryStatus.SENT);
		Instant requestedAt = clock.instant();

		MvcResult result = mockMvc.perform(requestWithdrawal(user.accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("success"))
				.andExpect(jsonPath("$.data.status").value("WITHDRAWAL_PENDING"))
				.andReturn();

		assertThat(scheduledDeletionAt(result)).isEqualTo(requestedAt.plus(GRACE_PERIOD));
		Map<String, Object> account = account(user.userId());
		assertThat(account.get("status")).isEqualTo("WITHDRAWAL_PENDING");
		assertThat(instant(account.get("withdrawal_requested_at"))).isEqualTo(requestedAt);
		assertThat(account.get("nickname")).isEqualTo("탈퇴요청");
		assertThat(account.get("deleted_at")).isNull();
		assertThat(jdbc.queryForList("SELECT device_status FROM push_device WHERE user_id = ?", String.class,
				user.userId())).containsOnly("REVOKED").hasSize(2);
		assertThat(jdbc.queryForList("SELECT revoked_at FROM push_device WHERE user_id = ?", OffsetDateTime.class,
				user.userId())).extracting(OffsetDateTime::toInstant).containsOnly(requestedAt);
		assertThat(deliveryStatus(pending)).isEqualTo("CANCELLED");
		assertThat(deliveryStatus(failed)).isEqualTo("CANCELLED");
		assertThat(deliveryStatus(sent)).isEqualTo("SENT");
		assertThat(presenceRows(user.userId())).isZero();
		assertThat(credentialStatuses(user.userId())).containsExactly("ACTIVE");
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-003: 탈퇴 요청 후 같은 액세스 토큰의 질문글 제출·답변 제출·위치 갱신은 403이고 행을 만들지 않는다")
	void pendingAccountCannotWriteWithExistingAccessToken() throws Exception {
		Registered user = register("쓰기차단");
		mockMvc.perform(requestWithdrawal(user.accessToken())).andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/direction/posts")
				.header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
				.header("Idempotency-Key", "gh337-int003-post")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"approvedQuestionId":1,"schemeId":1,"segmentKey":"S0","bodyText":"탈퇴 중 질문"}
						"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorDetail.code").value("DIR-APP-007"));
		mockMvc.perform(post("/api/v1/direction/inbox/{postRecipientId}/answers", 1L)
				.header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
				.header("Idempotency-Key", "gh337-int003-answer")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"bodyText":"탈퇴 중 답변"}
						"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorDetail.code").value("ANS-APP-003"));
		mockMvc.perform(updatePresence(user.accessToken()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorDetail.code").value("DIR-APP-007"));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM direction_post WHERE sender_id = ?", Integer.class,
				user.userId())).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM answer WHERE author_id = ?", Integer.class,
				user.userId())).isZero();
		assertThat(presenceRows(user.userId())).isZero();
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-005: 유예 중 계정의 기기 재발급은 200이고 응답 accountStatus가 WITHDRAWAL_PENDING이다")
	void reissuesTokenForPendingAccountWithStatus() throws Exception {
		Registered user = register("재발급유예");
		mockMvc.perform(requestWithdrawal(user.accessToken())).andExpect(status().isOk());

		mockMvc.perform(reissue(user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.accountStatus").value("WITHDRAWAL_PENDING"));
		assertThat(credentialStatuses(user.userId())).containsExactly("ACTIVE");
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-007: 유예 중 재발급한 토큰으로 철회하면 ACTIVE와 같은 닉네임으로 돌아오고 다음 재발급은 ACTIVE, 위치 갱신은 200이다")
	void cancelsWithdrawalWithReissuedTokenAndRestoresWrites() throws Exception {
		Registered user = register("철회복귀");
		mockMvc.perform(requestWithdrawal(user.accessToken())).andExpect(status().isOk());
		String pendingToken = accessToken(mockMvc.perform(reissue(user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountStatus").value("WITHDRAWAL_PENDING"))
				.andReturn());

		mockMvc.perform(cancelWithdrawal(pendingToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.scheduledDeletionAt").doesNotExist());

		Map<String, Object> account = account(user.userId());
		assertThat(account.get("status")).isEqualTo("ACTIVE");
		assertThat(account.get("withdrawal_requested_at")).isNull();
		assertThat(account.get("nickname")).isEqualTo("철회복귀");
		String activeToken = accessToken(mockMvc.perform(reissue(user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
				.andReturn());
		mockMvc.perform(updatePresence(activeToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.applied").value(true));
		assertThat(presenceRows(user.userId())).isEqualTo(1);
		assertThat(credentialStatuses(user.userId())).containsExactly("ACTIVE");
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-008: 유예 중 하루 뒤 다시 탈퇴를 요청해도 200이고 삭제 예정 시각과 요청 시각, 행 version이 처음 그대로다")
	void repeatedWithdrawalRequestKeepsFirstSchedule() throws Exception {
		Registered user = register("재요청");
		Instant firstRequestedAt = clock.instant();
		Instant firstScheduled = scheduledDeletionAt(mockMvc.perform(requestWithdrawal(user.accessToken()))
				.andExpect(status().isOk())
				.andReturn());
		Map<String, Object> before = account(user.userId());

		clock.advance(Duration.ofDays(1));
		MvcResult again = mockMvc.perform(requestWithdrawal(user.accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("WITHDRAWAL_PENDING"))
				.andReturn();

		assertThat(firstScheduled).isEqualTo(firstRequestedAt.plus(GRACE_PERIOD));
		assertThat(scheduledDeletionAt(again)).isEqualTo(firstScheduled);
		Map<String, Object> after = account(user.userId());
		assertThat(instant(after.get("withdrawal_requested_at"))).isEqualTo(firstRequestedAt);
		assertThat(after.get("version")).isEqualTo(before.get("version"));
		assertThat(after.get("updated_at")).isEqualTo(before.get("updated_at"));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-009: 유예 중 닉네임은 다른 기기가 등록할 수 없고(409 ACC-APP-002) 유예 만료 처리 뒤에는 등록된다")
	void nicknameStaysReservedDuringGraceAndIsReleasedAfterCompletion() throws Exception {
		Registered owner = register("묶인닉네임");
		mockMvc.perform(requestWithdrawal(owner.accessToken())).andExpect(status().isOk());

		mockMvc.perform(registerRequest("gh337-int009-newcomer", "묶인닉네임"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorDetail.code").value("ACC-APP-002"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM user_account", Integer.class)).isEqualTo(1);

		clock.advance(GRACE_PERIOD);
		AccountWithdrawalSweepWorker.BatchResult sweep = sweepWorker.processBatch(
				new AccountWithdrawalSweepWorker.BatchCommand(10, null));
		assertThat(sweep.completed()).isEqualTo(1);

		MvcResult registered = mockMvc.perform(registerRequest("gh337-int009-newcomer", "묶인닉네임"))
				.andExpect(status().isCreated())
				.andReturn();
		long newcomerId = data(registered).get("userId").asLong();
		assertThat(account(newcomerId).get("nickname")).isEqualTo("묶인닉네임");
		Map<String, Object> previousOwner = account(owner.userId());
		assertThat(previousOwner.get("status")).isEqualTo("DELETED");
		assertThat(previousOwner.get("nickname")).isNull();
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-016: 차단하면 재발급이 403 AUT-APP-003이고 자격증명은 ACTIVE로 남으며 해제 후 같은 기기로 재발급되고, 유예 중 계정 차단은 409로 상태를 바꾸지 않는다")
	void blockAndUnblockGateReissueWithoutRevokingCredentials() throws Exception {
		Registered user = register("차단대상");

		assertThat(accountStatusService.block(user.userId())).isEqualTo(AccountStatus.BLOCKED);
		mockMvc.perform(reissue(user))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-003"));
		assertThat(credentialStatuses(user.userId())).containsExactly("ACTIVE");
		assertThat(accountStatusService.unblock(user.userId())).isEqualTo(AccountStatus.ACTIVE);
		mockMvc.perform(reissue(user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"));

		Registered pending = register("유예차단");
		mockMvc.perform(requestWithdrawal(pending.accessToken())).andExpect(status().isOk());
		Map<String, Object> before = account(pending.userId());
		assertThatThrownBy(() -> accountStatusService.block(pending.userId()))
				.isInstanceOfSatisfying(AccountException.class, exception -> {
					assertThat(exception.getErrorCode()).isEqualTo(AccountErrorCode.INVALID_STATUS_TRANSITION);
					assertThat(exception.getErrorCode().httpStatus().value()).isEqualTo(409);
				});
		Map<String, Object> after = account(pending.userId());
		assertThat(after.get("status")).isEqualTo("WITHDRAWAL_PENDING");
		assertThat(after.get("withdrawal_requested_at")).isEqualTo(before.get("withdrawal_requested_at"));
		assertThat(after.get("version")).isEqualTo(before.get("version"));
	}

	private Registered register(String nickname) throws Exception {
		String installationId = "gh337-withdrawal-" + SEQUENCE.incrementAndGet();
		MvcResult result = mockMvc.perform(registerRequest(installationId, nickname))
				.andExpect(status().isCreated())
				.andReturn();
		JsonNode data = data(result);
		return new Registered(data.get("userId").asLong(), installationId, data.get("deviceSecret").asText(),
				data.get("accessToken").asText());
	}

	private MockHttpServletRequestBuilder registerRequest(String installationId, String nickname) {
		return post("/api/v1/auth/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"installationId":"%s","platform":"IOS","countryCode":"%s",
						 "locale":"ko-KR","timezone":"Asia/Seoul","nickname":"%s"}
						""".formatted(installationId, COUNTRY_CODE, nickname));
	}

	private MockHttpServletRequestBuilder reissue(Registered user) {
		return post("/api/v1/auth/token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"installationId":"%s","deviceSecret":"%s"}
						""".formatted(user.installationId(), user.deviceSecret()));
	}

	private MockHttpServletRequestBuilder requestWithdrawal(String accessToken) {
		return post("/api/v1/users/me/withdrawal").header(HttpHeaders.AUTHORIZATION, bearer(accessToken));
	}

	private MockHttpServletRequestBuilder cancelWithdrawal(String accessToken) {
		return delete("/api/v1/users/me/withdrawal").header(HttpHeaders.AUTHORIZATION, bearer(accessToken));
	}

	private MockHttpServletRequestBuilder updatePresence(String accessToken) {
		return put("/api/v1/direction/presence")
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"latitude":37.5,"longitude":127.0,"accuracyMeters":10,"receiveAllowed":true,
						 "observedAt":"%s"}
						""".formatted(clock.instant()));
	}

	private PushDevice pushDevice(long userId, PushPlatform platform, String token) {
		ProtectedPushToken protectedToken = protector().protect(PushToken.of(token));
		return notifications.saveDevice(new PushDevice(null, userId, platform, protectedToken.envelope(),
				protectedToken.fingerprint(), PushDeviceStatus.ACTIVE, clock.instant(), null));
	}

	private long delivery(long userId, long deviceId, DeliveryStatus status) {
		int sequence = SEQUENCE.incrementAndGet();
		Instant at = clock.instant();
		OutboxEvent outboxEvent = outboxEvents.save(OutboxEvent.pending(OutboxAggregateType.POST_RECIPIENT, userId,
				OutboxEventType.RECIPIENTS_CONFIRMED, "gh337-" + userId + "-" + sequence, "{\"source\":\"gh337\"}",
				at));
		Notification notification = notifications.save(new Notification(null, userId, outboxEvent.id(),
				NotificationType.DIRECTION_POST_RECEIVED, "gh337-notification-" + userId + "-" + sequence, null, null,
				null, NotificationStatus.UNREAD, at, null));
		NotificationDelivery saved = switch (status) {
			case PENDING -> notifications.saveDelivery(NotificationDelivery.pending(notification.id(), deviceId, at));
			case FAILED -> notifications.saveDelivery(new NotificationDelivery(null, notification.id(), deviceId,
					DeliveryStatus.FAILED, 1, at, at, null, null));
			case SENT -> notifications.saveDelivery(new NotificationDelivery(null, notification.id(), deviceId,
					DeliveryStatus.SENT, 1, at, at, at, "provider-message"));
			default -> throw new IllegalArgumentException("unsupported fixture status " + status);
		};
		return saved.id();
	}

	private String deliveryStatus(long deliveryId) {
		return jdbc.queryForObject("SELECT status FROM notification_delivery WHERE id = ?", String.class, deliveryId);
	}

	private int presenceRows(long userId) {
		return jdbc.queryForObject("SELECT count(*) FROM active_user_presence WHERE user_id = ?", Integer.class,
				userId);
	}

	private List<String> credentialStatuses(long userId) {
		return jdbc.queryForList("SELECT credential_status FROM device_credential WHERE user_id = ? ORDER BY id",
				String.class, userId);
	}

	private Map<String, Object> account(long userId) {
		return jdbc.queryForMap("""
				SELECT status, nickname, withdrawal_requested_at, deleted_at, version, updated_at
				FROM user_account WHERE id = ?
				""", userId);
	}

	private Instant scheduledDeletionAt(MvcResult result) throws Exception {
		return Instant.parse(data(result).get("scheduledDeletionAt").asText());
	}

	private String accessToken(MvcResult result) throws Exception {
		return data(result).get("accessToken").asText();
	}

	private JsonNode data(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
	}

	private static Instant instant(Object timestamp) {
		return ((Timestamp) timestamp).toInstant();
	}

	private static String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}

	private static AesGcmPushTokenProtector protector() {
		return new AesGcmPushTokenProtector(
				new PushTokenKeyRing(CURRENT_KEY_ID, Map.of(CURRENT_KEY_ID, ENCRYPTION_KEY), FINGERPRINT_KEY));
	}

	private static byte[] fixedKey(byte value) {
		byte[] key = new byte[32];
		Arrays.fill(key, value);
		return key;
	}

	private record Registered(long userId, String installationId, String deviceSecret, String accessToken) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfiguration {

		@Bean
		@Primary
		MutableClock accountWithdrawalTestClock() {
			return new MutableClock(Instant.now().truncatedTo(ChronoUnit.MICROS));
		}
	}

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
