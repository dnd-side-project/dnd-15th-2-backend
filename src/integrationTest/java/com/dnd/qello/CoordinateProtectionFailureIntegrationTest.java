/**
 * Created at: 2026-11-24T17:52:00Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-INT-008, INT-019, INT-024
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.security.CoordinateProtector;
import com.dnd.qello.direction.security.CoordinatePurpose;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.direction.service.DirectionPresenceService;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CoordinateProtectionFailureIntegrationTest.ProtectionFailureConfiguration.class)
class CoordinateProtectionFailureIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-COORDINATE-FAILURE-388";
	private static final String LATITUDE = "37.51234567";
	private static final String LONGITUDE = "127.09876543";
	private static final AtomicReference<CoordinatePurpose> FAIL_PROTECT = new AtomicReference<>();

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ActiveUserPresenceRepository presenceRepository;
	@Autowired
	private DirectionSchemeRepository schemeRepository;
	@Autowired
	private DirectionPostService postService;
	@Autowired
	private FilterReleaseRegistryService releaseRegistryService;
	@Autowired
	private DirectionPresenceService presenceService;
	@Autowired
	private DirectionMatchingWorker worker;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private Clock clock;
	@Autowired
	private Environment environment;

	private long senderId;
	private long candidateId;
	private long questionId;
	private long schemeId;
	private Instant at;

	@BeforeEach
	void setUp() {
		FAIL_PROTECT.set(null);
		for (String table : List.of("notification_delivery", "notification", "outbox_event", "post_recipient",
				"user_block", "post_audience", "direction_post", "recipient_receive_state", "active_user_presence",
				"direction_segment", "direction_scheme", "approved_question")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING
				""");
		jdbc.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES (?, 'KR', 'Coordinate failure', 'REGION')
				""", REGION);
		at = clock.instant().truncatedTo(ChronoUnit.SECONDS);
		senderId = account("protection-sender");
		AnswerModerationReleaseTestFixture.promotedRelease(releaseRegistryService, senderId);
		candidateId = account("protection-candidate");
		long healthyCandidate = account("protection-healthy");
		presence(senderId, LATITUDE, LONGITUDE);
		presence(candidateId, "37.51334567", LONGITUDE);
		presence(healthyCandidate, "37.51434567", LONGITUDE);
		questionId = jdbc.queryForObject("""
				INSERT INTO approved_question
					(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
				VALUES ('OPERATOR', 'ACTIVE', 'Protection question', 'TEXT', ?, ?, ?) RETURNING id
				""", Long.class, Timestamp.from(at.minusSeconds(120)), Timestamp.from(at.minusSeconds(120)), senderId);
		schemeId = schemeRepository.save(DirectionScheme.createEqual("PROTECTION-388", 1, 8, BigDecimal.ZERO)).getId();
		for (int index = 0; index < 8; index++) {
			schemeRepository.saveSegment(DirectionSegment.create(schemeId, "S" + index, "Protection " + index,
					BigDecimal.valueOf(index * 45 + 22.5), BigDecimal.valueOf(45), index));
		}
	}

	@AfterEach
	void removeFailureInjection() {
		FAIL_PROTECT.set(null);
		jdbc.execute("DROP TRIGGER IF EXISTS gh388_presence_bind_failure ON active_user_presence");
		jdbc.execute("DROP FUNCTION IF EXISTS gh388_presence_bind_failure()");
	}

	@ParameterizedTest
	@CsvSource({"true,false", "true,true", "false,false", "false,true"})
	@DisplayName("원점·후보의 변조 또는 모르는 키는 정상 후보까지 부분 확정하지 않고 transaction을 rollback하고 DEAD로 끝낸다")
	void deadLettersCorruptCoordinatesWithoutPartialRecipients(boolean audience, boolean unknownKey) {
		long postId = postService.send(command("corrupt-coordinate")).post().getId();
		jdbc.update("UPDATE direction_post SET moderation_status = 'PASSED' WHERE id = ?", postId);
		String table = audience ? "post_audience" : "active_user_presence";
		String column = audience ? "origin_position_ciphertext" : "position_ciphertext";
		String idColumn = audience ? "post_id" : "user_id";
		long id = audience ? postId : candidateId;
		byte[] envelope = jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE " + idColumn + " = ?",
				byte[].class, id);
		corruptEnvelope(envelope, unknownKey);
		jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE " + idColumn + " = ?", envelope, id);
		Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		ListAppender<ILoggingEvent> logs = new ListAppender<>();
		logs.start();
		logger.addAppender(logs);
		try {
			DirectionMatchingWorker.BatchResult result = worker.processBatch(new DirectionMatchingWorker.BatchCommand(
					10, "protection-388", at, at.plusSeconds(60),
					new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1))));

			assertThat(result.claimed()).isEqualTo(1);
			assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.DEAD);
			assertThat(jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, postId))
					.isEqualTo("MATCHING");
			assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient", Long.class)).isZero();
			assertThat(jdbc.queryForObject("SELECT count(*) FROM recipient_receive_state", Long.class)).isZero();
			assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Long.class)).isZero();
			assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Long.class)).isEqualTo(2);
			assertThat(jdbc.queryForObject(
					"SELECT status FROM outbox_event WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id = ?",
					String.class, postId)).isEqualTo("DEAD");
			assertPrivateValuesAbsent(result + logText(logs) + jdbc.queryForObject(
					"SELECT payload::text FROM outbox_event WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id = ?",
					String.class, postId));
		} finally {
			logger.detachAppender(logs);
			logs.stop();
		}
	}

	@Test
	@DisplayName("원점 암호화 실패는 실제 발송 서비스가 생성한 post·audience·outbox를 모두 rollback한다")
	void rollsBackSubmissionWhenAudienceProtectionFails() {
		FAIL_PROTECT.set(CoordinatePurpose.POST_AUDIENCE);

		Throwable failure = catchThrowable(() -> postService.send(command("protect-failure")));

		assertThat(failure).isInstanceOf(InvalidDataAccessApiUsageException.class)
				.hasRootCauseInstanceOf(IllegalStateException.class);
		assertPrivateValuesAbsent(causeChain(failure));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM direction_post", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_audience", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Long.class)).isZero();
	}

	@Test
	@DisplayName("presence 암호화 실패는 실제 갱신 서비스에서 이전 암호문과 메타데이터 전체를 보존한다")
	void preservesPresenceWhenProtectionFails() {
		byte[] ciphertext = jdbc.queryForObject(
				"SELECT position_ciphertext FROM active_user_presence WHERE user_id = ?", byte[].class, senderId);
		var before = jdbc.queryForMap("""
				SELECT accuracy_m, receive_allowed, location_at, expires_at, coarse_region_code, coarse_cell_id
				FROM active_user_presence WHERE user_id = ?
				""", senderId);
		FAIL_PROTECT.set(CoordinatePurpose.ACTIVE_USER_PRESENCE);

		Throwable failure = catchThrowable(() -> presenceService.update(senderId,
				new DirectionPresenceService.UpdateCommand(new BigDecimal("37.51678901"),
						new BigDecimal("127.09654321"), BigDecimal.TEN, false, at)));

		assertThat(failure).isInstanceOf(InvalidDataAccessApiUsageException.class)
				.hasRootCauseInstanceOf(IllegalStateException.class);
		assertPrivateValuesAbsent(causeChain(failure));
		assertThat(jdbc.queryForObject("SELECT position_ciphertext FROM active_user_presence WHERE user_id = ?",
				byte[].class, senderId)).isEqualTo(ciphertext);
		assertThat(jdbc.queryForMap("""
				SELECT accuracy_m, receive_allowed, location_at, expires_at, coarse_region_code, coarse_cell_id
				FROM active_user_presence WHERE user_id = ?
				""", senderId)).isEqualTo(before);
	}

	@Test
	@DisplayName("실제 JDBC bind 실패의 HTTP 응답·cause chain·앱 및 JDBC 로그는 좌표와 키를 노출하지 않는다")
	void redactsSqlFailureAcrossHttpExceptionAndLogs() throws Exception {
		jdbc.execute("""
				CREATE FUNCTION gh388_presence_bind_failure() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN
					RAISE EXCEPTION 'synthetic coordinate write failure' USING ERRCODE = '23514';
				END $$
				""");
		jdbc.execute("""
				CREATE TRIGGER gh388_presence_bind_failure BEFORE INSERT OR UPDATE ON active_user_presence
				FOR EACH ROW EXECUTE FUNCTION gh388_presence_bind_failure()
				""");
		Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		Logger jdbcLogger = (Logger) LoggerFactory.getLogger("org.springframework.jdbc.core");
		Level previousLevel = jdbcLogger.getLevel();
		jdbcLogger.setLevel(Level.TRACE);
		ListAppender<ILoggingEvent> logs = new ListAppender<>();
		logs.start();
		logger.addAppender(logs);
		try {
			var result = mockMvc.perform(put("/api/v1/direction/presence")
					.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
							.jwt(token -> token.subject(Long.toString(senderId))))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"latitude":%s,"longitude":%s,"accuracyMeters":10,"receiveAllowed":true,"observedAt":"%s"}
							""".formatted(LATITUDE, LONGITUDE, at)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errorDetail.code").exists())
					.andReturn();
			assertThat(result.getResolvedException()).isNotNull();
			assertPrivateValuesAbsent(result.getResponse().getContentAsString()
					+ causeChain(result.getResolvedException())
					+ logText(logs));
		} finally {
			logger.detachAppender(logs);
			logs.stop();
			jdbcLogger.setLevel(previousLevel);
		}
	}

	private void corruptEnvelope(byte[] envelope, boolean unknownKey) {
		if (unknownKey) {
			int keyOffset = new String(envelope, StandardCharsets.ISO_8859_1).indexOf("coordinate-fixture-current");
			assertThat(keyOffset).as("fixture key id must be present in the envelope").isNotNegative();
			byte[] missing = "coordinate-fixture-missing".getBytes(StandardCharsets.UTF_8);
			System.arraycopy(missing, 0, envelope, keyOffset, missing.length);
		} else {
			envelope[envelope.length - 1] ^= 1;
		}
	}

	private String logText(ListAppender<ILoggingEvent> logs) {
		return logs.list.stream().map(event -> event.getFormattedMessage()
				+ (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy())))
				.collect(Collectors.joining("\n"));
	}

	private void assertPrivateValuesAbsent(String observed) {
		String key = environment.getRequiredProperty("qello.direction.coordinate-protection.current-key-base64");
		List<String> secrets = List.of(LATITUDE, LONGITUDE, "37.51678901", "127.09654321",
				key, new String(Base64.getDecoder().decode(key), StandardCharsets.UTF_8));
		assertThat(secrets.stream().noneMatch(observed::contains))
				.as("private coordinate and key material must not cross the observation boundary").isTrue();
	}

	private String causeChain(Throwable failure) {
		StringBuilder result = new StringBuilder();
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			result.append(cause.getClass().getName()).append(':').append(cause.getMessage()).append('\n');
		}
		return result.toString();
	}

	private void presence(long userId, String latitude, String longitude) {
		presenceRepository.save(ActiveUserPresence.create(userId, new BigDecimal(latitude), new BigDecimal(longitude),
				null, REGION, BigDecimal.ONE, true, at.minusSeconds(60), at.plusSeconds(7200)));
	}

	private DirectionPostService.SendCommand command(String key) {
		return new DirectionPostService.SendCommand(senderId, questionId, schemeId, "S0", 0, 5000,
				REGION, key, "Coordinate protection", at, at.plusSeconds(3600));
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
				VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?) RETURNING id
				""", Long.class, REGION, nickname);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ProtectionFailureConfiguration {
		@Bean
		static BeanPostProcessor coordinateFailureBoundary() {
			return new BeanPostProcessor() {
				@Override
				public Object postProcessAfterInitialization(Object bean, String beanName) {
					if (!(bean instanceof CoordinateProtector)) {
						return bean;
					}
					ProxyFactory proxy = new ProxyFactory(bean);
					proxy.setInterfaces(CoordinateProtector.class);
					proxy.addAdvice((MethodInterceptor) invocation -> {
						if (invocation.getMethod().getName().equals("protect")
								&& invocation.getArguments()[1] == FAIL_PROTECT.get()) {
							throw new IllegalStateException("synthetic coordinate protection failure");
						}
						return invocation.proceed();
					});
					return proxy.getProxy();
				}
			};
		}
	}
}
