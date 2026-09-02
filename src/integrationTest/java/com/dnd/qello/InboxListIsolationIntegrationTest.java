/**
 * Created at: 2026-09-02T19:28:00+09:00
 * Source scenario: TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-INT-001 through INT-003
 */
package com.dnd.qello;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;
import com.dnd.qello.feed.repository.jdbc.JdbcInboxQueryRepository;
import com.dnd.qello.feed.service.InboxApplicationService;
import com.dnd.qello.feed.view.DirectionChip;
import com.dnd.qello.feed.view.InboxCard;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.view.InboxListing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
@Import(Inbox124TestClockConfiguration.class)
class InboxListIsolationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-16T06:00:00.123456Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private InboxApplicationService inbox;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@MockitoSpyBean
	private JdbcInboxQueryRepository queryRepository;

	private Inbox124IntegrationFixtures fixtures;
	private long senderId;
	private long recipientId;

	@BeforeEach
	void resetFixtures() {
		reset(queryRepository);
		fixtures = new Inbox124IntegrationFixtures(jdbc, NOW);
		fixtures.reset();
		senderId = fixtures.account("inbox212-sender");
		recipientId = fixtures.account("inbox212-recipient");
	}

	@Test
	@DisplayName("INT-001 목록 SELECT 이후 커밋된 같은 방향 항목은 같은 호출의 칩에 섞이지 않는다")
	void chipCountIgnoresRowsCommittedAfterFindInbox() {
		long existingPost = fixtures.post(senderId, "int001-existing", NOW.plusSeconds(3600), "ACTIVE", null);
		long existingRecipient = fixtures.available(existingPost, recipientId, NOW.minusSeconds(10), 0);
		commitNewItemAfterFindInbox(0);

		InboxListing listing = inbox.list(recipientId, InboxCategory.UNANSWERED, null);

		assertThat(listing.cards()).extracting(InboxCard::postRecipientId).containsExactly(existingRecipient);
		assertThat(nChipCount(listing)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-002 방향 필터를 건 호출의 칩도 같은 snapshot만 보고 새 S 항목을 넣지 않는다")
	void filteredListChipsStayOnTheSameSnapshot() {
		long northPost = fixtures.post(senderId, "int002-north", NOW.plusSeconds(3600), "ACTIVE", null);
		long northRecipient = fixtures.available(northPost, recipientId, NOW.minusSeconds(10), 0);
		commitNewItemAfterFindInbox(180);

		InboxListing listing = inbox.list(recipientId, InboxCategory.UNANSWERED, "N");

		assertThat(listing.cards()).extracting(InboxCard::postRecipientId).containsExactly(northRecipient);
		assertThat(listing.chips()).extracting(DirectionChip::segmentKey).containsExactly("N");
		assertThat(nChipCount(listing)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-003 상세 projection 실패는 OPENED 전이를 롤백한다")
	void detailProjectionFailureRollsBackOpened() {
		long postId = fixtures.post(senderId, "int003-open", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
		doReturn(Optional.empty()).when(queryRepository)
				.findDetail(eq(recipientId), eq(postRecipientId), any());

		assertThatThrownBy(() -> inbox.detail(recipientId, postRecipientId))
				.isInstanceOf(FeedException.class)
				.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INBOX_ITEM_NOT_FOUND);
		assertThat(fixtures.status(postRecipientId)).isEqualTo("AVAILABLE");
	}

	private void commitNewItemAfterFindInbox(int inboundBearing) {
		doAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			List<InboxCard> cards = (List<InboxCard>) invocation.callRealMethod();
			commitInSeparateTransaction(() -> {
				long postId = fixtures.post(
						senderId, "after-snapshot-" + inboundBearing, NOW.plusSeconds(3600), "ACTIVE", null);
				fixtures.available(postId, recipientId, NOW.minusSeconds(1), inboundBearing);
			});
			return cards;
		}).when(queryRepository).findInbox(eq(recipientId), eq(InboxCategory.UNANSWERED), any(), any());
	}

	private void commitInSeparateTransaction(Runnable work) {
		TransactionTemplate separate = new TransactionTemplate(transactionManager);
		separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		separate.executeWithoutResult(status -> work.run());
	}

	private long nChipCount(InboxListing listing) {
		return listing.chips().stream()
				.filter(chip -> "N".equals(chip.segmentKey()))
				.mapToLong(DirectionChip::count)
				.findFirst()
				.orElse(0L);
	}
}
