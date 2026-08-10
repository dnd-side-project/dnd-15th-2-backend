/**
 * Created at: 2026-08-10T23:14:20+09:00
 * Source scenario: TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-INT-001 through INT-004, INT-006
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.config.DirectionRecipientSelectionProperties;
import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionCandidate;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.service.DirectionPostService;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DirectionRecipientSelectionIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRECTION-97";
	private static final Instant AT = Instant.parse("2026-08-10T12:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ActiveUserPresenceRepository presenceRepository;

	@Autowired
	private DirectionSchemeRepository schemeRepository;

	@Autowired
	private DirectionPostService postService;

	@Autowired
	private DirectionRecipientSelectionProperties selectionProperties;

	@BeforeEach
	void reset() {
		jdbc.update("DELETE FROM user_block");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM recipient_receive_state");
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM direction_segment");
		jdbc.update("DELETE FROM direction_scheme");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Recipient Selection Test Region', 'REGION')", REGION);
	}

	@Test
	@DisplayName("후보 조회는 ACTIVE 계정과 양방향 차단이 해제된 관계만 반환한다")
	void excludesInactiveAndBidirectionallyBlockedCandidates() {
		long senderId = account("sender", "ACTIVE");
		long normalId = account("normal", "ACTIVE");
		long blockedBySenderId = account("blocked-by-sender", "ACTIVE");
		long blockedSenderId = account("blocked-sender", "ACTIVE");
		long releasedBlockId = account("released-block", "ACTIVE");
		long blockedAccountId = account("blocked-account", "BLOCKED");
		long deletedAccountId = account("deleted-account", "DELETED");

		presence(normalId, 37.5010, 127.0001);
		presence(blockedBySenderId, 37.5011, 127.0001);
		presence(blockedSenderId, 37.5012, 127.0001);
		presence(releasedBlockId, 37.5013, 127.0001);
		presence(blockedAccountId, 37.5014, 127.0001);
		presence(deletedAccountId, 37.5015, 127.0001);
		jdbc.update("INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, ?)", senderId, blockedBySenderId, Timestamp.from(AT));
		jdbc.update("INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, ?)", blockedSenderId, senderId, Timestamp.from(AT));
		jdbc.update("INSERT INTO user_block (blocker_id, blocked_id, created_at, released_at) VALUES (?, ?, ?, ?)",
			senderId, releasedBlockId, Timestamp.from(AT.minusSeconds(20)), Timestamp.from(AT.minusSeconds(10)));

		List<DirectionCandidate> candidates = presenceRepository.findCandidates(senderId, 37.5000, 127.0000,
			0, 2_000, 0, 360, AT, REGION);

		assertThat(candidates).extracting(DirectionCandidate::userId)
			.containsExactly(normalId, releasedBlockId);
	}

	@Test
	@DisplayName("후보 조회는 최근 수신 횟수와 마지막 수신 시각을 우선하고 거리를 tie-break로 사용한다")
	void ordersCandidatesByFairnessThenDistance() {
		long senderId = account("ordering-sender", "ACTIVE");
		long noHistoryId = account("no-history", "ACTIVE");
		long oldHistoryId = account("old-history", "ACTIVE");
		long recentHistoryId = account("recent-history", "ACTIVE");
		presence(noHistoryId, 37.5010, 127.0000);
		presence(oldHistoryId, 37.5011, 127.0000);
		presence(recentHistoryId, 37.5012, 127.0000);
		receiveState(oldHistoryId, 0, AT.minusSeconds(600));
		receiveState(recentHistoryId, 1, AT.minusSeconds(60));

		List<DirectionCandidate> candidates = presenceRepository.findCandidates(senderId, 37.5000, 127.0000,
			0, 2_000, 0, 360, AT, REGION);

		assertThat(candidates).extracting(DirectionCandidate::userId)
			.containsExactly(noHistoryId, oldHistoryId, recentHistoryId);
	}

	@Test
	@DisplayName("발송은 설정된 최대 10명의 예약 성공자까지만 recipient를 확정한다")
	void sendStopsAfterTenSuccessfulReservations() {
		long senderId = account("limit-sender", "ACTIVE");
		long questionId = activeQuestion(senderId);
		long schemeId = eightSegmentScheme();
		presence(senderId, 37.5000, 127.0000);
		List<Long> candidateIds = IntStream.range(0, 12)
			.mapToObj(index -> account("limit-candidate-" + index, "ACTIVE"))
			.toList();
		IntStream.range(0, 12).forEach(index -> presence(candidateIds.get(index), 37.5010 + index * 0.0001, 127.0000));
		candidateIds.forEach((id) -> receiveState(id, candidateIds.indexOf(id), AT.minusSeconds(candidateIds.indexOf(id) + 1L)));

		var result = postService.send(new DirectionPostService.SendCommand(senderId, questionId, schemeId, "S0",
			0, 5_000, REGION, "gh97-limit-10", "본문", AT, AT.plusSeconds(3600)));

		assertThat(selectionProperties.maxRecipientsPerPost()).isEqualTo(10);
		assertThat(result.recipients()).hasSize(10)
			.extracting(recipient -> recipient.getRecipientId())
			.containsExactlyElementsOf(candidateIds.subList(0, 10));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ?", Integer.class, result.post().getId()))
			.isEqualTo(10);
	}

	@Test
	@DisplayName("슬롯 예약에 실패한 상위 후보가 있어도 후순위 후보로 최대 10명을 채운다")
	void sendSkipsFullRecipientsWithoutConsumingPostLimit() {
		long senderId = account("full-slot-sender", "ACTIVE");
		long questionId = activeQuestion(senderId);
		long schemeId = eightSegmentScheme();
		presence(senderId, 37.5000, 127.0000);
		List<Long> candidateIds = IntStream.range(0, 12)
			.mapToObj(index -> account("full-slot-candidate-" + index, "ACTIVE"))
			.toList();
		IntStream.range(0, 12).forEach(index -> presence(candidateIds.get(index), 37.5010 + index * 0.0001, 127.0000));
		candidateIds.forEach((id) -> receiveState(id, 0, null));
		IntStream.range(0, 3).forEach(index -> jdbc.update(
			"UPDATE recipient_receive_state SET active_unhandled_count = 5 WHERE user_id = ?", candidateIds.get(index)));

		var result = postService.send(new DirectionPostService.SendCommand(senderId, questionId, schemeId, "S0",
			0, 5_000, REGION, "gh97-full-slot-10", "본문", AT, AT.plusSeconds(3600)));

		assertThat(result.recipients()).hasSize(9)
			.extracting(recipient -> recipient.getRecipientId())
			.containsExactlyElementsOf(candidateIds.subList(3, 12));
	}

	private long account(String nickname, String status) {
		if ("DELETED".equals(status)) {
			return jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname, deleted_at)
				VALUES ('USER', 'KR', ?, ?, 'ko-KR', 'Asia/Seoul', ?, ?)
				RETURNING id
				""", Long.class, status, REGION, nickname, Timestamp.from(AT));
		}
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', ?, ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, status, REGION, nickname);
	}

	private void presence(long userId, double latitude, double longitude) {
		presenceRepository.save(ActiveUserPresence.create(userId, BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude),
			null, REGION, BigDecimal.ONE, true, AT.minusSeconds(10), AT.plusSeconds(3600)));
	}

	private void receiveState(long userId, int recentCount, Instant lastReceivedAt) {
		jdbc.update("""
			INSERT INTO recipient_receive_state
				(user_id, active_unhandled_count, recent_received_count, recent_window_started_at, last_received_at, updated_at)
			VALUES (?, 0, ?, ?, ?, ?)
			""", userId, recentCount, Timestamp.from(AT.minusSeconds(3600)),
			lastReceivedAt == null ? null : Timestamp.from(lastReceivedAt), Timestamp.from(AT));
	}

	private long activeQuestion(long approverId) {
		return jdbc.queryForObject("""
			INSERT INTO approved_question
			(source_type, status, question_text, answer_format, active_from, active_until, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', '방향 질문', 'TEXT', ?, ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(AT.minusSeconds(1)), Timestamp.from(AT.plusSeconds(7200)),
			Timestamp.from(AT.minusSeconds(1)), approverId);
	}

	private long eightSegmentScheme() {
		DirectionScheme scheme = schemeRepository.save(DirectionScheme.createEqual("TEST-97", 1, 8, BigDecimal.ZERO));
		IntStream.range(0, 8).forEach(index -> schemeRepository.saveSegment(DirectionSegment.create(scheme.getId(), "S" + index,
			"segment-" + index, BigDecimal.valueOf(index * 45L + 22.5), BigDecimal.valueOf(45), index)));
		return scheme.getId();
	}
}
