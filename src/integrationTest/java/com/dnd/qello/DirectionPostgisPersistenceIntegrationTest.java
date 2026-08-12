package com.dnd.qello;

/**
 * Created at: 2026-08-03T20:45:00+09:00
 * Source scenario: TEST-PLAN-GH-39-DIRECTION-POSTGIS-PERSISTENCE-INT-001 through INT-010,
 * TEST-PLAN-GH-118-DIRECTION-POST-SUBMISSION-INT-001
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.service.DirectionPostService;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DirectionPostgisPersistenceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRECTION-39";
	private static final Instant AT = Instant.parse("2026-08-03T12:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ActiveUserPresenceRepository presenceRepository;

	@Autowired
	private DirectionSchemeRepository schemeRepository;

	@Autowired
	private DirectionPostRepository postRepository;

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
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Direction Test Region', 'REGION')", REGION);
	}

	@Test
	@DisplayName("Flyway V1은 PostGIS와 방향 persistence table 및 trigger를 실제 컨테이너에 재현한다")
	void reproducesDirectionSchema() {
		Integer tableCount = jdbc.queryForObject("""
			SELECT count(*) FROM information_schema.tables
			WHERE table_schema = 'public' AND table_name IN
			('direction_scheme', 'direction_segment', 'active_user_presence',
			 'recipient_receive_state', 'direction_post', 'post_audience', 'post_recipient')
			""", Integer.class);
		Integer postgisCount = jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'postgis'", Integer.class);
		Integer triggerCount = jdbc.queryForObject("""
			SELECT count(*) FROM pg_trigger WHERE tgname IN
			('ct_direction_post_question_active', 'ct_post_recipient_not_sender', 'ct_post_recipient_capacity_release')
			""", Integer.class);

		assertThat(tableCount).isEqualTo(7);
		assertThat(postgisCount).isEqualTo(1);
		assertThat(triggerCount).isEqualTo(3);
	}

	@Test
	@DisplayName("PostGIS 후보 query는 현재이고 수신 허용이며 선택 sector 안의 사용자만 반환한다")
	void findsCurrentCandidatesWithoutExposingCoordinates() {
		long senderId = createUser("sender");
		long northeastId = createUser("northeast");
		long expiredId = createUser("expired");
		presenceRepository.save(ActiveUserPresence.create(senderId, BigDecimal.valueOf(37.5000), BigDecimal.valueOf(127.0000),
			null, REGION, BigDecimal.ONE, true, AT.minusSeconds(10), AT.plusSeconds(3600)));
		presenceRepository.save(ActiveUserPresence.create(northeastId, BigDecimal.valueOf(37.5010), BigDecimal.valueOf(127.0010),
			null, REGION, BigDecimal.ONE, true, AT.minusSeconds(10), AT.plusSeconds(3600)));
		presenceRepository.save(ActiveUserPresence.create(expiredId, BigDecimal.valueOf(37.5020), BigDecimal.valueOf(127.0000),
			null, REGION, BigDecimal.ONE, true, AT.minusSeconds(7200), AT.minusSeconds(1)));

		var candidates = presenceRepository.findCandidates(senderId, 37.5000, 127.0000, 0, 500,
			0, 45, AT, REGION);

		assertThat(candidates).extracting(candidate -> candidate.userId()).containsExactly(northeastId);
		assertThat(candidates.get(0).distanceMeters()).isGreaterThan(BigDecimal.ZERO);
		assertThat(candidates.get(0).bearingDegrees()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(45));
		// northeast는 위도·경도가 모두 sender와 다르다(발송자→후보 bearing이 약 38.4°).
		// 위도·경도가 같은 축(정북)이면 forward+180과 실제 역방위가 우연히 정확히 일치해
		// 단순 평면 근사와 ST_Azimuth 재계산을 구분하지 못한다. 여기서는 forward bearing과
		// 뚜렷이 구분되는 좁은 범위(약 218.4° 부근)로, 후보 위치를 원점으로 다시 계산한
		// 값이라는 것을 확인한다 — forward(38°)나 두 값의 평균(128°) 같은 오류값은 이 범위 밖이다.
		assertThat(candidates.get(0).inboundBearingDegrees()).isBetween(BigDecimal.valueOf(215), BigDecimal.valueOf(222));
	}

	@Test
	@DisplayName("send는 최신 presence로 audience만 snapshot하고 수신자 확정은 위임한다")
	void snapshotsAudienceAndDefersRecipients() {
		long senderId = createUser("send-sender");
		long recipientId = createUser("send-recipient");
		long questionId = createActiveQuestion(senderId);
		long schemeId = createEightSegmentScheme();
		presenceRepository.save(ActiveUserPresence.create(senderId, BigDecimal.valueOf(37.5000), BigDecimal.valueOf(127.0000),
			null, REGION, BigDecimal.ONE, true, AT.minusSeconds(10), AT.plusSeconds(3600)));
		presenceRepository.save(ActiveUserPresence.create(recipientId, BigDecimal.valueOf(37.5010), BigDecimal.valueOf(127.0000),
			null, REGION, BigDecimal.ONE, true, AT.minusSeconds(10), AT.plusSeconds(3600)));
		jdbc.update("""
			INSERT INTO recipient_receive_state (user_id, active_unhandled_count, recent_received_count, recent_window_started_at)
			VALUES (?, 0, 0, ?)
			""", recipientId, Timestamp.from(AT.minusSeconds(3600)));

		var result = postService.send(new DirectionPostService.SendCommand(senderId, questionId, schemeId, "S0",
			0, 500, REGION, "idempotency-39-1", "테스트 방향 글", AT, AT.plusSeconds(3600)));
		var retried = postService.send(new DirectionPostService.SendCommand(senderId, questionId, schemeId, "S0",
			0, 500, REGION, "idempotency-39-1", "테스트 방향 글", AT, AT.plusSeconds(3600)));

		assertThat(result.post().getStatus()).isEqualTo(com.dnd.qello.direction.domain.DirectionPostStatus.MATCHING);
		assertThat(result.audience().getOriginLatitude()).isEqualByComparingTo("37.5000");
		assertThat(result.recipients()).isEmpty();
		assertThat(retried.post().getId()).isEqualTo(result.post().getId());
		assertThat(jdbc.queryForObject("SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?", Integer.class, recipientId)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ?", Integer.class, result.post().getId())).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_audience WHERE post_id = ?", Integer.class, result.post().getId())).isEqualTo(1);
	}

	@Test
	@DisplayName("ACTIVE가 아닌 질문을 참조한 direction post는 deferred trigger에서 거절된다")
	void rejectsPostWithInactiveQuestionAtCommit() {
		long senderId = createUser("inactive-sender");
		long pendingQuestionId = jdbc.queryForObject("""
			INSERT INTO approved_question (source_type, status, question_text, answer_format)
			VALUES ('OPERATOR', 'PENDING_REVIEW', '대기 질문', 'TEXT') RETURNING id
			""", Long.class);

		assertThatThrownBy(() -> postRepository.save(DirectionPost.submit(senderId, pendingQuestionId,
			"inactive-post", "내용", REGION, AT, AT.plusSeconds(3600))))
			.isInstanceOf(DataAccessException.class);
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
			VALUES ('OPERATOR', 'ACTIVE', '방향 질문', 'TEXT', ?, ?, ?, ?) RETURNING id
			""", Long.class, Timestamp.from(AT.minusSeconds(1)), Timestamp.from(AT.plusSeconds(7200)), Timestamp.from(AT.minusSeconds(1)), approverId);
	}

	private long createEightSegmentScheme() {
		DirectionScheme scheme = schemeRepository.save(DirectionScheme.createEqual("TEST-39", 1, 8, BigDecimal.ZERO));
		IntStream.range(0, 8).forEach(index -> schemeRepository.saveSegment(DirectionSegment.create(scheme.getId(), "S" + index,
			"segment-" + index, BigDecimal.valueOf(index * 45L + 22.5), BigDecimal.valueOf(45), index)));
		return scheme.getId();
	}
}
