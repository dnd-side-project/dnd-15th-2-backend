/**
 * Created at: 2026-08-20T16:00:00+09:00
 * Source scenario: TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ-INT-001 through
 * INT-012, INT-022 through INT-026
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.repository.NotificationInboxQueryRepository;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.notification.view.NotificationCard;
import com.dnd.qello.notification.view.NotificationListing;
import com.dnd.qello.notification.view.NotificationTargetDecision;
import com.dnd.qello.notification.view.NotificationTargetKind;
import com.dnd.qello.notification.view.NotificationTargetState;

@SpringBootTest
@ActiveProfiles("test")
class NotificationInboxQueryIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-20T06:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NotificationRepository notifications;
	@Autowired
	private OutboxEventRepository outboxEvents;
	@Autowired
	private NotificationInboxQueryRepository queryRepository;

	private Notification176IntegrationFixtures fixtures;
	private long senderId;
	private long recipientId;
	private long outsiderId;

	@BeforeEach
	void resetFixtures() {
		fixtures = new Notification176IntegrationFixtures(jdbc, notifications, outboxEvents, NOW);
		fixtures.reset();
		senderId = fixtures.account("gh176-sender");
		recipientId = fixtures.account("gh176-recipient");
		outsiderId = fixtures.account("gh176-outsider");
	}

	@Test
	@DisplayName("INT-001 cursor로 3페이지를 순회하면 5건이 중복·누락 없이 나온다")
	void paginatesFiveNotificationsWithoutDuplicateOrGap() {
		long postId = fixtures.activePost(senderId, "int001", NOW.plusSeconds(3600));
		for (int i = 0; i < 5; i++) {
			fixtures.directionPostNotification(recipientId, postId, NOW.plusSeconds(i));
		}

		NotificationListing page1 = queryRepository.list(recipientId, null, 2, NOW);
		NotificationListing page2 = queryRepository.list(recipientId, page1.nextCursor(), 2, NOW);
		NotificationListing page3 = queryRepository.list(recipientId, page2.nextCursor(), 2, NOW);

		assertThat(page1.items()).hasSize(2);
		assertThat(page2.items()).hasSize(2);
		assertThat(page3.items()).hasSize(1);
		assertThat(page3.nextCursor()).isNull();
		List<Long> allIds = List.of(
			page1.items().get(0).notificationId(), page1.items().get(1).notificationId(),
			page2.items().get(0).notificationId(), page2.items().get(1).notificationId(),
			page3.items().get(0).notificationId());
		assertThat(allIds).doesNotHaveDuplicates().hasSize(5);
	}

	@Test
	@DisplayName("INT-002 같은 created_at 4건도 튜플 비교로 정확히 한 번씩만 나온다")
	void tupleComparisonAvoidsDuplicatesForSameCreatedAt() {
		long postId = fixtures.activePost(senderId, "int002", NOW.plusSeconds(3600));
		for (int i = 0; i < 4; i++) {
			fixtures.directionPostNotification(recipientId, postId, NOW);
		}

		NotificationListing page1 = queryRepository.list(recipientId, null, 2, NOW);
		NotificationListing page2 = queryRepository.list(recipientId, page1.nextCursor(), 2, NOW);

		assertThat(page1.items()).hasSize(2);
		assertThat(page2.items()).hasSize(2);
		List<Long> ids = List.of(
			page1.items().get(0).notificationId(), page1.items().get(1).notificationId(),
			page2.items().get(0).notificationId(), page2.items().get(1).notificationId());
		assertThat(ids).doesNotHaveDuplicates().hasSize(4);
	}

	@Test
	@DisplayName("INT-003 UNREAD·READ만 목록에 남고 DISMISSED·REVOKED는 제외된다")
	void excludesDismissedAndRevokedFromList() {
		long postId = fixtures.activePost(senderId, "int003", NOW.plusSeconds(3600));
		fixtures.directionPostNotification(recipientId, postId, NOW);
		fixtures.withStatus(
			fixtures.directionPostNotification(recipientId, postId, NOW.plusSeconds(1)),
			NotificationStatus.READ, NOW.plusSeconds(2));
		fixtures.withStatus(
			fixtures.directionPostNotification(recipientId, postId, NOW.plusSeconds(3)),
			NotificationStatus.DISMISSED, NOW.plusSeconds(4));
		fixtures.withStatus(
			fixtures.directionPostNotification(recipientId, postId, NOW.plusSeconds(5)),
			NotificationStatus.REVOKED, null);

		NotificationListing listing = queryRepository.list(recipientId, null, 20, NOW);

		assertThat(listing.items()).hasSize(2);
	}

	@Test
	@DisplayName("INT-004 타인의 알림은 목록에 없다")
	void excludesOthersNotifications() {
		long postId = fixtures.activePost(senderId, "int004", NOW.plusSeconds(3600));
		fixtures.directionPostNotification(recipientId, postId, NOW);
		fixtures.directionPostNotification(outsiderId, postId, NOW);
		fixtures.directionPostNotification(outsiderId, postId, NOW.plusSeconds(1));

		NotificationListing listing = queryRepository.list(recipientId, null, 20, NOW);

		assertThat(listing.items()).hasSize(1);
	}

	@Test
	@DisplayName("INT-005 반환 건수가 limit과 같을 때만 nextCursor가 채워진다")
	void fillsNextCursorOnlyWhenReturnedCountEqualsLimit() {
		long postId = fixtures.activePost(senderId, "int005", NOW.plusSeconds(3600));
		fixtures.directionPostNotification(recipientId, postId, NOW);
		fixtures.directionPostNotification(recipientId, postId, NOW.plusSeconds(1));

		NotificationListing full = queryRepository.list(recipientId, null, 2, NOW);
		NotificationListing next = queryRepository.list(recipientId, full.nextCursor(), 2, NOW);

		assertThat(full.nextCursor()).isNotNull();
		assertThat(next.items()).isEmpty();
		assertThat(next.nextCursor()).isNull();
	}

	@Test
	@DisplayName("INT-006 limit 50은 50건을 반환한다")
	void limitFiftyReturnsFiftyRows() {
		long postId = fixtures.activePost(senderId, "int006", NOW.plusSeconds(3600));
		for (int i = 0; i < 60; i++) {
			fixtures.directionPostNotification(recipientId, postId, NOW.plusSeconds(i));
		}

		NotificationListing listing = queryRepository.list(recipientId, null, 50, NOW);

		assertThat(listing.items()).hasSize(50);
	}

	@Test
	@DisplayName("INT-007 만료된 질문글 알림은 state가 EXPIRED이고 expiresAt이 없다")
	void expiredPostYieldsExpiredStateWithoutExpiresAt() {
		long postId = fixtures.expiredPost(senderId, "int007", NOW.minusSeconds(60));
		fixtures.directionPostNotification(recipientId, postId, NOW);

		NotificationCard card = onlyItem(queryRepository.list(recipientId, null, 20, NOW));

		assertThat(card.targetState()).isEqualTo(NotificationTargetState.EXPIRED);
		assertThat(card.expiresAt()).isNull();
	}

	@Test
	@DisplayName("INT-008 삭제된 질문글 알림은 state가 GONE이다")
	void deletedPostYieldsGoneState() {
		long postId = fixtures.deletedPost(senderId, "int008");
		fixtures.directionPostNotification(recipientId, postId, NOW);

		NotificationCard card = onlyItem(queryRepository.list(recipientId, null, 20, NOW));

		assertThat(card.targetState()).isEqualTo(NotificationTargetState.GONE);
	}

	@Test
	@DisplayName("INT-009 HIDDEN 답변과 미공개 답변 모두 state가 HIDDEN이다")
	void hiddenAndUnpublishedAnswersYieldHiddenState() {
		// uq_answer_one_per_recipient가 post_recipient당 non-REJECTED 답변 1건만 허용하므로
		// 답변 두 개를 만들려면 post_recipient 행도 각각 따로 있어야 한다.
		long hiddenPostId = fixtures.activePost(senderId, "int009-hidden-post", NOW.plusSeconds(3600));
		long hiddenPrId = fixtures.answeredRecipient(hiddenPostId, recipientId);
		long hiddenAnswerId = fixtures.hiddenAnswer(hiddenPrId, recipientId, "int009-hidden");

		long unpublishedPostId = fixtures.activePost(senderId, "int009-unpublished-post", NOW.plusSeconds(3600));
		long unpublishedPrId = fixtures.answeredRecipient(unpublishedPostId, recipientId);
		long unpublishedAnswerId = fixtures.unpublishedAnswer(unpublishedPrId, recipientId, "int009-unpublished");

		fixtures.answerNotification(recipientId, hiddenAnswerId, NOW);
		fixtures.answerNotification(recipientId, unpublishedAnswerId, NOW.plusSeconds(1));

		NotificationListing listing = queryRepository.list(recipientId, null, 20, NOW);

		assertThat(listing.items()).extracting(NotificationCard::targetState)
			.containsOnly(NotificationTargetState.HIDDEN);
	}

	@Test
	@DisplayName("INT-010 양방향 차단은 모두 state가 BLOCKED이고 해제된 차단은 AVAILABLE이다")
	void blockInEitherDirectionYieldsBlockedAndReleasedBlockDoesNot() {
		long forwardBlockPost = fixtures.activePost(senderId, "int010-forward", NOW.plusSeconds(3600));
		fixtures.activeBlock(recipientId, senderId);
		fixtures.directionPostNotification(recipientId, forwardBlockPost, NOW);

		long otherSenderId = fixtures.account("gh176-other-sender");
		long reverseBlockPost = fixtures.activePost(otherSenderId, "int010-reverse", NOW.plusSeconds(3600));
		fixtures.activeBlock(otherSenderId, recipientId);
		fixtures.directionPostNotification(recipientId, reverseBlockPost, NOW.plusSeconds(1));

		long releasedSenderId = fixtures.account("gh176-released-sender");
		long releasedBlockPost = fixtures.activePost(releasedSenderId, "int010-released", NOW.plusSeconds(3600));
		fixtures.releasedBlock(recipientId, releasedSenderId);
		fixtures.directionPostNotification(recipientId, releasedBlockPost, NOW.plusSeconds(2));

		NotificationListing listing = queryRepository.list(recipientId, null, 20, NOW);

		assertThat(cardFor(listing, forwardBlockPost).targetState()).isEqualTo(NotificationTargetState.BLOCKED);
		assertThat(cardFor(listing, reverseBlockPost).targetState()).isEqualTo(NotificationTargetState.BLOCKED);
		assertThat(cardFor(listing, releasedBlockPost).targetState()).isEqualTo(NotificationTargetState.AVAILABLE);
	}

	@Test
	@DisplayName("INT-011 삭제와 차단이 동시에 있으면 GONE이 BLOCKED보다 앞선다")
	void deletionOutranksBlockForGoneState() {
		long postId = fixtures.deletedPost(senderId, "int011");
		fixtures.activeBlock(recipientId, senderId);
		fixtures.directionPostNotification(recipientId, postId, NOW);

		NotificationCard card = onlyItem(queryRepository.list(recipientId, null, 20, NOW));

		assertThat(card.targetState()).isEqualTo(NotificationTargetState.GONE);
	}

	@Test
	@DisplayName("INT-012 만료와 차단이 동시에 있으면 BLOCKED가 EXPIRED보다 앞선다")
	void blockOutranksExpiryForBlockedState() {
		long postId = fixtures.expiredPost(senderId, "int012", NOW.minusSeconds(60));
		fixtures.activeBlock(recipientId, senderId);
		fixtures.directionPostNotification(recipientId, postId, NOW);

		NotificationCard card = onlyItem(queryRepository.list(recipientId, null, 20, NOW));

		assertThat(card.targetState()).isEqualTo(NotificationTargetState.BLOCKED);
	}

	@Test
	@DisplayName("INT-022 목록 조회 이후 시각이 지나면 진입 판정이 다시 평가되어 EXPIRED로 바뀐다")
	void reevaluatesTargetDecisionAtALaterInstant() {
		long postId = fixtures.activePost(senderId, "int022", NOW.plusSeconds(1800));
		Notification notification = fixtures.directionPostNotification(recipientId, postId, NOW);

		NotificationCard atListingTime = onlyItem(queryRepository.list(recipientId, null, 20, NOW));
		Optional<NotificationTargetDecision> laterDecision =
			queryRepository.findTargetDecision(recipientId, notification.id(), NOW.plusSeconds(3600));

		assertThat(atListingTime.targetState()).isEqualTo(NotificationTargetState.AVAILABLE);
		assertThat(laterDecision).isPresent();
		assertThat(laterDecision.get().navigable()).isFalse();
		assertThat(laterDecision.get().reason()).isEqualTo(NotificationTargetState.EXPIRED);
		assertThat(laterDecision.get().fallback()).isEqualTo(NotificationTargetDecision.Fallback.INBOX);
	}

	@Test
	@DisplayName("INT-023 살아 있는 대상은 fallback이 NONE이고 삭제·차단 대상은 FEED_HOME이다")
	void fallbackDiffersByTargetState() {
		long availablePostId = fixtures.activePost(senderId, "int023-available", NOW.plusSeconds(3600));
		Notification available = fixtures.directionPostNotification(recipientId, availablePostId, NOW);

		long deletedPostId = fixtures.deletedPost(senderId, "int023-deleted");
		Notification gone = fixtures.directionPostNotification(recipientId, deletedPostId, NOW.plusSeconds(1));

		NotificationTargetDecision availableDecision =
			queryRepository.findTargetDecision(recipientId, available.id(), NOW).orElseThrow();
		NotificationTargetDecision goneDecision =
			queryRepository.findTargetDecision(recipientId, gone.id(), NOW).orElseThrow();

		assertThat(availableDecision.fallback()).isEqualTo(NotificationTargetDecision.Fallback.NONE);
		assertThat(goneDecision.fallback()).isEqualTo(NotificationTargetDecision.Fallback.FEED_HOME);
	}

	@Test
	@DisplayName("INT-024 목록 쿼리 실행 계획이 notification_recipient_feed_idx를 사용한다")
	void listQueryUsesPartialIndex() {
		long postId = fixtures.activePost(senderId, "int024", NOW.plusSeconds(3600));
		for (int i = 0; i < 200; i++) {
			fixtures.directionPostNotification(recipientId, postId, NOW.plusSeconds(i));
		}

		List<String> plan = jdbc.queryForList("""
			EXPLAIN SELECT id FROM notification
			 WHERE recipient_id = ? AND status IN ('UNREAD', 'READ')
			 ORDER BY created_at DESC, id DESC LIMIT 20
			""", String.class, recipientId);

		assertThat(String.join("\n", plan)).contains("notification_recipient_feed_idx");
	}

	@Test
	@DisplayName("INT-025 응답에 질문·답변 본문, 닉네임, 계정 식별자, 좌표가 실리지 않는다")
	void doesNotExposeSensitiveFields() {
		long postId = fixtures.activePost(senderId, "int025", NOW.plusSeconds(3600));
		long prId = fixtures.answeredRecipient(postId, recipientId);
		long answerId = fixtures.publishedAnswer(prId, recipientId, "int025");
		fixtures.directionPostNotification(recipientId, postId, NOW);
		fixtures.answerNotification(recipientId, answerId, NOW.plusSeconds(1));

		NotificationListing listing = queryRepository.list(recipientId, null, 20, NOW);

		for (var field : NotificationCard.class.getRecordComponents()) {
			String name = field.getName().toLowerCase();
			assertThat(name).doesNotContain("body").doesNotContain("nickname")
				.doesNotContain("bearing").doesNotContain("distance").doesNotContain("region");
		}
		assertThat(listing.items()).isNotEmpty();
	}

	@Test
	@DisplayName("INT-026 6종 notification_type이 모두 목록에서 동일하게 처리되고 대상 없는 종류는 NONE이다")
	void handlesAllSixNotificationTypesUniformly() {
		long postId = fixtures.activePost(senderId, "int026", NOW.plusSeconds(3600));
		fixtures.directionPostNotification(recipientId, postId, NOW);
		int offset = 1;
		for (NotificationType type : List.of(
			NotificationType.ANSWER_REACTED, NotificationType.REPORT_RESOLVED,
			NotificationType.QUESTION_PROPOSAL_REVIEWED, NotificationType.QUESTION_RECOMMENDED,
			NotificationType.ANSWER_RECEIVED)) {
			fixtures.targetlessNotification(recipientId, type, NOW.plusSeconds(offset++));
		}

		NotificationListing listing = queryRepository.list(recipientId, null, 20, NOW);

		assertThat(listing.items()).hasSize(6);
		assertThat(listing.items()).filteredOn(card -> card.type() != NotificationType.DIRECTION_POST_RECEIVED)
			.extracting(NotificationCard::targetKind)
			.containsOnly(NotificationTargetKind.NONE);
	}

	private static NotificationCard onlyItem(NotificationListing listing) {
		assertThat(listing.items()).hasSize(1);
		return listing.items().get(0);
	}

	private static NotificationCard cardFor(NotificationListing listing, long directionPostId) {
		return listing.items().stream()
			.filter(card -> directionPostId == card.targetId())
			.findFirst()
			.orElseThrow(() -> new AssertionError("no card for post " + directionPostId));
	}
}
