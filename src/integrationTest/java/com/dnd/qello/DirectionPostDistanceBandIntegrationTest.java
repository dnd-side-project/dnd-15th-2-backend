package com.dnd.qello;

/**
 * Created at: 2026-08-10T20:05:00+09:00
 * Source scenario: TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-INT-001
 */

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DirectionPostDistanceBandIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DISTANCE-BAND-95";
	private static final Instant AT = Instant.parse("2026-08-10T11:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ActiveUserPresenceRepository presenceRepository;

	@Autowired
	private DirectionSchemeRepository schemeRepository;

	@Autowired
	private DirectionPostService postService;

	@BeforeEach
	void reset() {
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
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Distance Band Test Region', 'REGION')", REGION);
	}

	@Test
	@DisplayName("한 발송의 수신자는 각자의 실제 거리에서 distanceBand를 파생한다")
	void derivesDistanceBandPerCandidateDistance() {
		long senderId = createUser("distance-band-sender");
		long nearRecipientId = createUser("distance-band-near");
		long farRecipientId = createUser("distance-band-far");
		long questionId = createActiveQuestion(senderId);
		long schemeId = createEightSegmentScheme();

		presenceRepository.save(ActiveUserPresence.create(senderId, BigDecimal.valueOf(37.5000), BigDecimal.valueOf(127.0000),
			null, REGION, BigDecimal.ONE, true, AT.minusSeconds(10), AT.plusSeconds(3600)));
		presenceRepository.save(ActiveUserPresence.create(nearRecipientId, BigDecimal.valueOf(37.5200), BigDecimal.valueOf(127.0200),
			null, REGION, BigDecimal.ONE, true, AT.minusSeconds(10), AT.plusSeconds(3600)));
		presenceRepository.save(ActiveUserPresence.create(farRecipientId, BigDecimal.valueOf(43.2500), BigDecimal.valueOf(134.5000),
			null, REGION, BigDecimal.ONE, true, AT.minusSeconds(10), AT.plusSeconds(3600)));

		var result = postService.send(new DirectionPostService.SendCommand(senderId, questionId, schemeId, "S0",
			0, 1_100_000, REGION, "distance-band-95-1", "거리 band 테스트", AT, AT.plusSeconds(3600)));

		Map<Long, PostRecipient> recipientsByUser = result.recipients().stream()
			.collect(Collectors.toMap(recipient -> recipient.getRecipientId(), Function.identity()));

		assertThat(recipientsByUser).containsKeys(nearRecipientId, farRecipientId);
		assertThat(recipientsByUser.get(nearRecipientId).getDistanceM()).isBetween(2_000L, 4_000L);
		assertThat(recipientsByUser.get(farRecipientId).getDistanceM()).isBetween(900_000L, 1_100_000L);
		assertThat(recipientsByUser.get(nearRecipientId).getDistanceBand())
			.isEqualTo("10km 이내")
			.isNotEqualTo(recipientsByUser.get(farRecipientId).getDistanceBand());
		assertThat(recipientsByUser.get(farRecipientId).getDistanceBand()).isEqualTo("EXACT_DISTANCE");
	}

	private long createUser(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (country_code, coarse_region_code, locale, timezone, nickname)
			VALUES ('KR', ?, 'ko-KR', 'Asia/Seoul', ?) RETURNING id
			""", Long.class, REGION, nickname);
	}

	private long createActiveQuestion(long approverId) {
		return jdbc.queryForObject("""
			INSERT INTO approved_question
			(source_type, status, question_text, answer_format, active_from, active_until, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', '거리 질문', 'TEXT', ?, ?, ?, ?) RETURNING id
			""", Long.class, Timestamp.from(AT.minusSeconds(1)), Timestamp.from(AT.plusSeconds(7200)),
			Timestamp.from(AT.minusSeconds(1)), approverId);
	}

	private long createEightSegmentScheme() {
		DirectionScheme scheme = schemeRepository.save(DirectionScheme.createEqual("TEST-95", 1, 8, BigDecimal.ZERO));
		IntStream.range(0, 8).forEach(index -> schemeRepository.saveSegment(DirectionSegment.create(scheme.getId(),
			"S" + index, "segment-" + index, BigDecimal.valueOf(index * 45L + 22.5), BigDecimal.valueOf(45), index)));
		return scheme.getId();
	}
}
