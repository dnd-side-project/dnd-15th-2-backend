/**
 * Created at: 2026-10-05T18:10:06+09:00
 * Source scenario: TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-INT-001 through INT-006
 */
package com.dnd.qello;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.domain.PushDeviceStatus;
import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.fanout.NotificationFanOutWorker;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;
import com.dnd.qello.question.service.QuestionProposalApplicationService;
import com.dnd.qello.question.service.QuestionReviewService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class QuestionProposalDeleteMuteIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-QUESTION-310";
	private static final Instant REVIEWED_AT = Instant.parse("2026-10-05T00:00:00Z");
	private static final String WORKER_OWNER = "gh310-proposal-mute";

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private AccountRepository accountRepository;
	@Autowired
	private QuestionProposalApplicationService applicationService;
	@Autowired
	private QuestionReviewService reviewService;
	@Autowired
	private NotificationFanOutWorker worker;
	@Autowired
	private NotificationRepository notifications;

	private long proposerId;
	private long otherUserId;
	private long reviewerId;

	@BeforeEach
	void setUp() {
		cleanup();
		jdbc.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES ('KR', NULL, 'Korea', 'COUNTRY')
				ON CONFLICT (code) DO NOTHING
				""");
		jdbc.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES (?, 'KR', 'GH310 Proposal Delete Mute', 'REGION')
				ON CONFLICT (code) DO NOTHING
				""", REGION);
		proposerId = user("gh310-proposer");
		otherUserId = user("gh310-other");
		reviewerId = user("gh310-reviewer");
	}

	@AfterEach
	void tearDown() {
		cleanup();
	}

	@Test
	@DisplayName("삭제한 제안은 내 목록에서 빠지고 행과 판정 이력은 DB에 남는다")
	void deletedProposalLeavesListButKeepsRow() {
		QuestionProposal kept = applicationService.submit(proposerId, "남길 제안");
		QuestionProposal approved = approve(applicationService.submit(proposerId, "승인 후 삭제할 제안"));
		QuestionProposal pending = applicationService.submit(proposerId, "검토 전에 삭제할 제안");

		applicationService.delete(proposerId, approved.getId());
		applicationService.delete(proposerId, pending.getId());

		assertThat(applicationService.findMine(proposerId))
				.extracting(QuestionProposal::getId)
				.containsExactly(kept.getId());
		assertThat(deletedAt(approved.getId())).isNotNull();
		assertThat(deletedAt(pending.getId())).isNotNull();
		assertThat(count("SELECT count(*) FROM question_proposal_review WHERE proposal_id = ?", approved.getId()))
				.isEqualTo(1L);
		assertThat(count("SELECT count(*) FROM approved_question WHERE source_proposal_id = ?", approved.getId()))
				.isEqualTo(1L);
	}

	@Test
	@DisplayName("다른 사용자의 제안 삭제·알림 끄기는 PROPOSAL_NOT_FOUND이고 원래 제안은 그대로다")
	void otherUserCannotDeleteOrMute() {
		QuestionProposal proposal = applicationService.submit(proposerId, "내 제안");

		assertThatThrownBy(() -> applicationService.delete(otherUserId, proposal.getId()))
				.isInstanceOf(QuestionException.class)
				.hasFieldOrPropertyWithValue("errorCode", QuestionErrorCode.PROPOSAL_NOT_FOUND);
		assertThatThrownBy(() -> applicationService.changeNotificationMuted(otherUserId, proposal.getId(), true))
				.isInstanceOf(QuestionException.class)
				.hasFieldOrPropertyWithValue("errorCode", QuestionErrorCode.PROPOSAL_NOT_FOUND);

		assertThat(deletedAt(proposal.getId())).isNull();
		assertThat(muted(proposal.getId())).isFalse();
	}

	@Test
	@DisplayName("같은 제안을 두 번 삭제해도 두 번째 요청이 성공하고 최초 삭제 시각을 유지한다")
	void secondDeleteSucceedsAndKeepsFirstTime() {
		QuestionProposal proposal = applicationService.submit(proposerId, "두 번 삭제할 제안");

		applicationService.delete(proposerId, proposal.getId());
		Timestamp first = deletedAt(proposal.getId());
		applicationService.delete(proposerId, proposal.getId());

		assertThat(first).isNotNull();
		assertThat(deletedAt(proposal.getId())).isEqualTo(first);
	}

	@Test
	@DisplayName("검토 중에 삭제한 제안은 승인·반려가 409이고 이력·승인 질문·outbox가 생기지 않는다")
	void deletedUnderReviewProposalCannotBeJudged() {
		QuestionProposal proposal = applicationService.submit(proposerId, "검토 중 철회할 제안");
		reviewService.startReview(proposal.getId());
		applicationService.delete(proposerId, proposal.getId());

		assertThatThrownBy(() -> reviewService.approve(proposal.getId(), reviewerId, AnswerFormat.TEXT,
				REVIEWED_AT.plusSeconds(3600), REVIEWED_AT.plusSeconds(7200), REVIEWED_AT))
				.isInstanceOf(QuestionException.class)
				.hasFieldOrPropertyWithValue("errorCode", QuestionErrorCode.INVALID_PROPOSAL_STATUS);
		assertThatThrownBy(() -> reviewService.reject(proposal.getId(), reviewerId, "정책 사유", REVIEWED_AT))
				.isInstanceOf(QuestionException.class)
				.hasFieldOrPropertyWithValue("errorCode", QuestionErrorCode.INVALID_PROPOSAL_STATUS);

		assertThat(count("SELECT count(*) FROM question_proposal_review WHERE proposal_id = ?", proposal.getId()))
				.isZero();
		assertThat(count("SELECT count(*) FROM approved_question WHERE source_proposal_id = ?", proposal.getId()))
				.isZero();
		assertThat(count("SELECT count(*) FROM outbox_event WHERE dedup_key = ?",
				"question-proposal-reviewed:" + proposal.getId())).isZero();
	}

	@Test
	@DisplayName("알림을 끈 제안은 알림함 기록만 남고 push delivery가 없으며, 켠 제안은 delivery가 생긴다")
	void mutedProposalSkipsDeliveryOnly() {
		activeDevice(proposerId, "gh310-device");
		QuestionProposal muted = applicationService.submit(proposerId, "알림 끈 제안");
		QuestionProposal unmuted = applicationService.submit(proposerId, "알림 켠 제안");
		applicationService.changeNotificationMuted(proposerId, muted.getId(), true);
		approve(muted);
		approve(unmuted);

		worker.processBatch(command());

		assertThat(eventStatus(muted.getId())).isEqualTo("PROCESSED");
		assertThat(eventStatus(unmuted.getId())).isEqualTo("PROCESSED");
		assertThat(notificationCount(muted.getId())).isEqualTo(1L);
		assertThat(deliveryCount(muted.getId())).isZero();
		assertThat(notificationCount(unmuted.getId())).isEqualTo(1L);
		assertThat(deliveryCount(unmuted.getId())).isEqualTo(1L);
	}

	@Test
	@DisplayName("판정 직후 fan-out 전에 삭제한 제안의 event는 dead가 아니라 처리 완료되고 push는 없다")
	void proposalDeletedBeforeFanOutStillCompletesEvent() {
		activeDevice(proposerId, "gh310-device-deleted");
		QuestionProposal proposal = approve(applicationService.submit(proposerId, "판정 후 삭제할 제안"));
		applicationService.delete(proposerId, proposal.getId());

		worker.processBatch(command());

		assertThat(eventStatus(proposal.getId())).isEqualTo("PROCESSED");
		assertThat(notificationCount(proposal.getId())).isEqualTo(1L);
		assertThat(deliveryCount(proposal.getId())).isZero();
	}

	private QuestionProposal approve(QuestionProposal proposal) {
		reviewService.startReview(proposal.getId());
		reviewService.approve(proposal.getId(), reviewerId, AnswerFormat.TEXT,
				REVIEWED_AT.plusSeconds(3600), REVIEWED_AT.plusSeconds(7200), REVIEWED_AT);
		return proposal;
	}

	private long user(String nickname) {
		return accountRepository.save(Account.createUser("KR", REGION, "ko-KR", "Asia/Seoul", nickname)).getId();
	}

	private void activeDevice(long userId, String fingerprint) {
		notifications.saveDevice(new PushDevice(null, userId, PushPlatform.ANDROID, new byte[]{1, 2, 3},
				fingerprint, PushDeviceStatus.ACTIVE, REVIEWED_AT, null));
	}

	private Timestamp deletedAt(long proposalId) {
		return jdbc.queryForObject("SELECT deleted_at FROM question_proposal WHERE id = ?", Timestamp.class,
				proposalId);
	}

	private boolean muted(long proposalId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT notification_muted FROM question_proposal WHERE id = ?", Boolean.class, proposalId));
	}

	private long count(String sql, Object argument) {
		return jdbc.queryForObject(sql, Long.class, argument);
	}

	private String eventStatus(long proposalId) {
		return jdbc.queryForObject("SELECT status FROM outbox_event WHERE dedup_key = ?", String.class,
				"question-proposal-reviewed:" + proposalId);
	}

	private long notificationCount(long proposalId) {
		return count("SELECT count(*) FROM notification WHERE dedup_key = ?",
				"question-proposal-reviewed:" + proposalId);
	}

	private long deliveryCount(long proposalId) {
		return count("""
				SELECT count(*)
				FROM notification_delivery nd
				JOIN notification n ON n.id = nd.notification_id
				WHERE n.dedup_key = ?
				""", "question-proposal-reviewed:" + proposalId);
	}

	private NotificationFanOutWorker.BatchCommand command() {
		return new NotificationFanOutWorker.BatchCommand(50, WORKER_OWNER, REVIEWED_AT.plusSeconds(60),
				REVIEWED_AT.plusSeconds(120), new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1)));
	}

	private void cleanup() {
		String regionUsers = "SELECT id FROM user_account WHERE coarse_region_code = '" + REGION + "'";
		jdbc.update("DELETE FROM notification_delivery WHERE notification_id IN "
				+ "(SELECT id FROM notification WHERE recipient_id IN (" + regionUsers + "))");
		jdbc.update("DELETE FROM notification WHERE recipient_id IN (" + regionUsers + ")");
		jdbc.update("DELETE FROM push_device WHERE user_id IN (" + regionUsers + ")");
		jdbc.update("DELETE FROM outbox_event WHERE aggregate_type = 'QUESTION_PROPOSAL' AND aggregate_id IN "
				+ "(SELECT id FROM question_proposal WHERE proposer_id IN (" + regionUsers + "))");
		jdbc.update("DELETE FROM approved_question WHERE source_proposal_id IN "
				+ "(SELECT id FROM question_proposal WHERE proposer_id IN (" + regionUsers + "))");
		jdbc.update("DELETE FROM question_proposal_review WHERE proposal_id IN "
				+ "(SELECT id FROM question_proposal WHERE proposer_id IN (" + regionUsers + "))");
		jdbc.update("DELETE FROM question_proposal WHERE proposer_id IN (" + regionUsers + ")");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
	}
}
