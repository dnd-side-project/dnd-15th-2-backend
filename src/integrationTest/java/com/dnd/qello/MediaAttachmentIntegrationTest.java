/**
 * Created at: 2026-08-07T04:20:00+09:00
 * Source scenario: TEST-PLAN-GH-70-MEDIA-ASSET-SERVICE-INT-006 through INT-009
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.domain.MediaAttachment;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.MediaAssetRepository;
import com.dnd.qello.answer.repository.MediaAttachmentRepository;
import com.dnd.qello.answer.service.MediaAttachmentService;
import com.dnd.qello.answer.service.MediaAttachmentService.AttachCommand;

@SpringBootTest
@ActiveProfiles("test")
class MediaAttachmentIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-MEDIA-ATTACH";
	private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private MediaAssetRepository mediaAssetRepository;
	@Autowired
	private MediaAttachmentRepository mediaAttachmentRepository;
	@Autowired
	private MediaAttachmentService mediaAttachmentService;

	private long ownerId;
	private long strangerId;
	private long postId;

	@BeforeEach
	void resetFixtures() {
		// direction_post를 먼저 지우면 ON DELETE CASCADE로 media_attachment가 같은 statement
		// 안에서 함께 사라지고, 그 시점에 post도 이미 없으므로 deferred
		// ct_media_attachment_preserves_content가 통과한다. media_attachment를 먼저 지우면
		// (아직 존재하는) 본문 없는 post의 유일한 미디어를 떼어내는 모양이 되어 같은 trigger가
		// 정리 자체를 막는다.
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM media_attachment");
		jdbc.update("DELETE FROM media_asset");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, display_name, level) VALUES (?, 'Media Attach Test', 'COUNTRY')", REGION);

		ownerId = account("attach-owner");
		strangerId = account("attach-stranger");
		// 본문이 있는 기본 fixture — 소유권 검증 시나리오는 콘텐츠 불변식과 무관하므로
		// 단일 INSERT(autocommit)로 만들어도 deferred trigger에 걸리지 않는다.
		postId = activePost(ownerId, "기본 본문");
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}

	private long activePost(long senderId, String bodyText) {
		long questionId = jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', '오늘 뭐 하고 있나요?', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW), senderId);
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', ?, ?, ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, "attach-post-" + senderId + "-" + System.nanoTime(), bodyText,
			REGION, Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));
	}

	private long readyMedia(long forOwnerId) {
		MediaAsset uploading = mediaAssetRepository.save(
			MediaAsset.upload(forOwnerId, "media/" + forOwnerId + "/" + System.nanoTime(), "image/jpeg", 10L, "checksum", NOW));
		return mediaAssetRepository.save(uploading.ready()).getId();
	}

	/**
	 * 본문 없는 ACTIVE post에 READY 미디어 1건을 붙여 만든다. post 생성과 media 첨부를 한
	 * transaction으로 묶지 않으면, 각 raw JDBC 호출이 개별 autocommit되어 post insert
	 * 시점에 media가 아직 없는 상태로 deferred trigger가 즉시(그 자신의 commit에서) 실패한다.
	 */
	private long[] postWithSoleReadyMedia() {
		long[] ids = new long[2];
		transactionTemplate.executeWithoutResult(status -> {
			ids[0] = activePost(ownerId, null);
			MediaAsset ready = MediaAsset.upload(ownerId, "media/" + ownerId + "/" + System.nanoTime(),
				"image/jpeg", 10L, "checksum", NOW).ready();
			MediaAsset saved = mediaAssetRepository.save(ready);
			ids[1] = saved.getId();
			mediaAttachmentRepository.save(new MediaAttachment(saved.getId(), ownerId, ids[0], null, 0));
		});
		return ids;
	}

	@Test
	@DisplayName("남의 미디어로 attach를 시도하면 DB에 도달하기 전에 애플리케이션에서 거부된다")
	void attachRejectsStrangerMediaBeforeReachingDatabase() {
		long myMediaId = readyMedia(ownerId);
		MediaAttachment saved = mediaAttachmentService.attach(new AttachCommand(ownerId, myMediaId, postId, null, 0));
		assertThat(saved.mediaId()).isEqualTo(myMediaId);

		long strangerMediaId = readyMedia(strangerId);
		assertThatThrownBy(() -> mediaAttachmentService.attach(
			new AttachCommand(ownerId, strangerMediaId, postId, null, 1)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.MEDIA_NOT_FOUND);
		assertThat(mediaAttachmentRepository.findByMediaId(strangerMediaId)).isEmpty();
	}

	@Test
	@DisplayName("서비스 사전 검증을 우회해 소유자가 다른 attachment를 직접 저장하면 복합 FK 위반으로 실패한다")
	void bypassingServiceViolatesOwnerCompositeForeignKey() {
		long strangerMediaId = readyMedia(strangerId);

		assertThatThrownBy(() -> mediaAttachmentRepository.save(
			new MediaAttachment(strangerMediaId, ownerId, postId, null, 0)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("서비스를 우회해 본문 없는 공개 질문글의 유일한 READY 미디어를 해제하면 commit 시점에 deferred trigger가 막는다")
	void bypassingServiceDetachIsBlockedByDeferredTriggerAtCommit() {
		long mediaId = postWithSoleReadyMedia()[1];

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
			status -> mediaAttachmentRepository.deleteByMediaId(mediaId)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("서비스를 통한 detach는 DB commit 전에 명확한 오류 코드로 거부하고 첨부를 그대로 보존한다")
	void serviceDetachFailsFastWithoutMutatingState() {
		long mediaId = postWithSoleReadyMedia()[1];

		assertThatThrownBy(() -> mediaAttachmentService.detach(mediaId, ownerId))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.MEDIA_CONTENT_REQUIRED);
		assertThat(mediaAttachmentRepository.findByMediaId(mediaId)).isPresent();
	}
}
