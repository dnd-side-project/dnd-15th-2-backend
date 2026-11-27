/**
 * Created at: 2026-11-24T17:50:00Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-INT-006, INT-007
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.service.DirectionPostModerationVerdictApplier;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CoordinateMigrationRecoveryIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-COORDINATE-RECOVERY-388";
	private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

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
	private DirectionMatchingWorker worker;
	@Autowired
	private DirectionPostModerationVerdictApplier verdictApplier;

	private long senderId;
	private long questionId;
	private long schemeId;

	@BeforeEach
	void setUp() {
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
				VALUES (?, 'KR', 'Coordinate recovery', 'REGION')
				""", REGION);
		senderId = account("recovery-sender");
		AnswerModerationReleaseTestFixture.promotedRelease(releaseRegistryService, senderId);
		long candidate = account("recovery-candidate");
		for (long userId : List.of(senderId, candidate)) {
			presenceRepository.save(ActiveUserPresence.create(userId,
					BigDecimal.valueOf(userId == senderId ? 37.5 : 37.501), BigDecimal.valueOf(127),
					null, REGION, BigDecimal.ONE, true, NOW.minusSeconds(60), NOW.plusSeconds(7200)));
		}
		questionId = jdbc.queryForObject("""
				INSERT INTO approved_question
					(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
				VALUES ('OPERATOR', 'ACTIVE', 'Recovery question', 'TEXT', ?, ?, ?) RETURNING id
				""", Long.class, Timestamp.from(NOW.minusSeconds(120)), Timestamp.from(NOW.minusSeconds(120)),
				senderId);
		DirectionScheme scheme = schemeRepository.save(DirectionScheme.createEqual("RECOVERY-388", 1, 8,
				BigDecimal.ZERO));
		schemeId = scheme.getId();
		for (int index = 0; index < 8; index++) {
			schemeRepository.saveSegment(DirectionSegment.create(schemeId, "S" + index, "Recovery " + index,
					BigDecimal.valueOf(index * 45 + 22.5), BigDecimal.valueOf(45), index));
		}
	}

	@ParameterizedTest
	@CsvSource({"false,false", "false,true", "true,false", "true,true"})
	@DisplayName("원점을 폐기한 MATCH_FAILED 글은 due·reclaim 및 만료 전후의 늦은 판정에도 다시 매칭되지 않는다")
	void doesNotRematchDiscardedOrigin(boolean expired, boolean reclaimed) {
		long postId = discardedPost("closed-post");
		Instant at = expired ? NOW.plusSeconds(3601) : NOW;
		if (reclaimed) {
			jdbc.update("""
					UPDATE outbox_event SET status = 'PROCESSING', lease_owner = 'abandoned',
						lease_generation = 1, lease_expires_at = ?
					WHERE aggregate_id = ? AND event_type = 'RECIPIENT_MATCH_REQUESTED'
					""", Timestamp.from(at.minusSeconds(1)), postId);
		}
		var metadata = jdbc.queryForMap("SELECT * FROM post_audience WHERE post_id = ?", postId);

		verdictApplier.applyDeadlineElapsed(postId, at);
		verdictApplier.applyVerdict(postId, FilterVerdict.ALLOW, at);
		DirectionMatchingWorker.BatchResult result = worker.processBatch(
				new DirectionMatchingWorker.BatchCommand(10, "recovery-388", at, at.plusSeconds(60),
						new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1))));

		assertThat(result.claimed()).isEqualTo(1);
		assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		assertThat(jdbc.queryForMap("SELECT status, moderation_status FROM direction_post WHERE id = ?", postId))
				.containsEntry("status", "MATCH_FAILED").containsEntry("moderation_status", "PENDING");
		assertThat(jdbc.queryForMap("SELECT * FROM post_audience WHERE post_id = ?", postId)).isEqualTo(metadata);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM recipient_receive_state", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Long.class)).isZero();
		assertThat(jdbc.queryForList("SELECT event_type FROM outbox_event", String.class))
				.containsExactlyInAnyOrder("MODERATION_EXECUTION_REQUESTED", "RECIPIENT_MATCH_REQUESTED");
		assertThat(jdbc.queryForObject(
				"SELECT status FROM outbox_event WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id = ?",
				String.class, postId)).isEqualTo("PROCESSED");
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("원점·셀 없는 실패 글은 fingerprint 유무와 무관하게 동일 의도만 재생하고 새 작업을 만들지 않는다")
	void replaysDiscardedAudienceWithoutReconstructingOrigin(boolean legacy) {
		long postId = discardedPost("replay-post");
		String fingerprint = jdbc.queryForObject(
				"SELECT request_fingerprint FROM direction_post WHERE id = ?", String.class, postId);
		if (legacy) {
			jdbc.update("UPDATE direction_post SET request_fingerprint = NULL WHERE id = ?", postId);
		}
		var metadata = jdbc.queryForMap("SELECT * FROM post_audience WHERE post_id = ?", postId);
		assertThatThrownBy(() -> postService.replayIfExists(senderId, "replay-post", questionId, schemeId,
				"S1", "Coordinate recovery", List.of())).isInstanceOf(DirectionException.class)
				.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.IDEMPOTENCY_KEY_REUSED);
		assertThat(jdbc.queryForObject("SELECT request_fingerprint FROM direction_post WHERE id = ?",
				String.class, postId)).isEqualTo(legacy ? null : fingerprint);

		var replay = postService
				.replayIfExists(senderId, "replay-post", questionId, schemeId, "S0", "Coordinate recovery", List.of())
				.orElseThrow();
		var directReplay = postService.send(command("replay-post"));

		assertThat(replay.post().getId()).isEqualTo(postId);
		assertThat(directReplay.post().getId()).isEqualTo(postId);
		assertThat(replay.post().getStatus().name()).isEqualTo("MATCH_FAILED");
		assertThat(replay.audience().getOriginLatitude()).isNull();
		assertThat(replay.audience().getOriginLongitude()).isNull();
		assertThat(replay.audience().getOriginCellId()).isNull();
		assertThat(jdbc.queryForObject("SELECT request_fingerprint FROM direction_post WHERE id = ?",
				String.class, postId)).isEqualTo(fingerprint);
		assertThat(jdbc.queryForMap("SELECT * FROM post_audience WHERE post_id = ?", postId)).isEqualTo(metadata);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM direction_post", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Long.class)).isEqualTo(2);
	}

	private long discardedPost(String key) {
		long postId = postService.send(command(key)).post().getId();
		jdbc.update(
				"UPDATE post_audience SET origin_position_ciphertext = NULL, origin_cell_id = NULL WHERE post_id = ?",
				postId);
		jdbc.update("UPDATE direction_post SET status = 'MATCH_FAILED', moderation_status = 'PENDING' WHERE id = ?",
				postId);
		return postId;
	}

	private DirectionPostService.SendCommand command(String key) {
		return new DirectionPostService.SendCommand(senderId, questionId, schemeId, "S0", 0, 5000,
				REGION, key, "Coordinate recovery", NOW, NOW.plusSeconds(3600));
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
				VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?) RETURNING id
				""", Long.class, REGION, nickname);
	}
}
