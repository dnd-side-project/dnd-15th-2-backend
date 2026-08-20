/**
 * Created at: 2026-08-20T16:40:00+09:00
 * Source scenario: TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ-UNIT-004 through
 * UNIT-013
 */
package com.dnd.qello.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.service.AccountEligibilityGate;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.repository.NotificationInboxQueryRepository;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.NotificationSeenStateRepository;
import com.dnd.qello.notification.view.NotificationCard;
import com.dnd.qello.notification.view.NotificationListing;
import com.dnd.qello.notification.view.NotificationTargetDecision;
import com.dnd.qello.notification.view.NotificationTargetKind;
import com.dnd.qello.notification.view.NotificationTargetState;

@ExtendWith(MockitoExtension.class)
class NotificationInboxServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-20T06:00:00Z");
	private static final long RECIPIENT_ID = 11L;
	private static final long NOTIFICATION_ID = 501L;

	@Mock private AccountEligibilityGate accountEligibilityGate;
	@Mock private NotificationInboxQueryRepository queryRepository;
	@Mock private NotificationSeenStateRepository seenStateRepository;
	@Mock private NotificationRepository notificationRepository;

	private NotificationInboxService service;

	@BeforeEach
	void setUp() {
		service = new NotificationInboxService(
			accountEligibilityGate, queryRepository, seenStateRepository, notificationRepository,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("UNIT-004 계정이 없으면 5개 경로 모두 NOT-APP-001이고 repository는 호출되지 않는다")
	void rejectsUnknownAccountAcrossAllFiveEntryPoints() {
		stubGateToInvoke(1);

		assertRejectsWithAccountError(NotificationErrorCode.ACCOUNT_NOT_FOUND);
		verifyNoInteractions(queryRepository, seenStateRepository, notificationRepository);
	}

	@Test
	@DisplayName("UNIT-005 자격 없는 계정은 5개 경로 모두 NOT-APP-002다")
	void rejectsIneligibleAccountAcrossAllFiveEntryPoints() {
		stubGateToInvoke(2);

		assertRejectsWithAccountError(NotificationErrorCode.ACCOUNT_NOT_ELIGIBLE);
		verifyNoInteractions(queryRepository, seenStateRepository, notificationRepository);
	}

	private void assertRejectsWithAccountError(NotificationErrorCode expected) {
		assertThatThrownBy(() -> service.list(RECIPIENT_ID, null, null, 20))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", expected);
		assertThatThrownBy(() -> service.unreadSignal(RECIPIENT_ID))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", expected);
		assertThatThrownBy(() -> service.markSeen(RECIPIENT_ID))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", expected);
		assertThatThrownBy(() -> service.markRead(RECIPIENT_ID, NOTIFICATION_ID))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", expected);
		assertThatThrownBy(() -> service.target(RECIPIENT_ID, NOTIFICATION_ID))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", expected);
	}

	@Test
	@DisplayName("UNIT-006 limit이 0, -1, 51이면 NOT-VAL-006이고 null은 20으로, 50은 그대로 통과한다")
	void validatesLimitRange() {
		stubEligibleAccount();
		NotificationListing listing = new NotificationListing(List.of(), null);
		when(queryRepository.list(eq(RECIPIENT_ID), eq((NotificationListing.Cursor)null), eq(50), eq(NOW)))
			.thenReturn(listing);

		assertThatThrownBy(() -> service.list(RECIPIENT_ID, null, null, 0))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_LIMIT);
		assertThatThrownBy(() -> service.list(RECIPIENT_ID, null, null, -1))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_LIMIT);
		assertThatThrownBy(() -> service.list(RECIPIENT_ID, null, null, 51))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_LIMIT);
		assertThat(service.list(RECIPIENT_ID, null, null, 50)).isSameAs(listing);
	}

	@Test
	@DisplayName("UNIT-007 cursor 파라미터를 한쪽만 지정하면 NOT-VAL-007이고 둘 다 생략하거나 지정하면 통과한다")
	void validatesCursorPairing() {
		stubEligibleAccount();
		when(queryRepository.list(eq(RECIPIENT_ID), any(), eq(20), eq(NOW)))
			.thenReturn(new NotificationListing(List.of(), null));

		assertThatThrownBy(() -> service.list(RECIPIENT_ID, NOW, null, 20))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_CURSOR);
		assertThatThrownBy(() -> service.list(RECIPIENT_ID, null, 5L, 20))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_CURSOR);

		service.list(RECIPIENT_ID, null, null, 20);
		service.list(RECIPIENT_ID, NOW, 5L, 20);

		verify(queryRepository).list(RECIPIENT_ID, null, 20, NOW);
		verify(queryRepository).list(RECIPIENT_ID, new NotificationListing.Cursor(NOW, 5L), 20, NOW);
	}

	@Test
	@DisplayName("UNIT-009 목록과 seen 전진은 서버 Clock에서 한 번 읽은 같은 시각을 쓴다")
	void usesSingleServerInstant() {
		stubEligibleAccount();
		when(queryRepository.list(RECIPIENT_ID, null, 20, NOW)).thenReturn(new NotificationListing(List.of(), null));
		when(seenStateRepository.advance(RECIPIENT_ID, NOW)).thenReturn(NOW);

		service.list(RECIPIENT_ID, null, null, 20);
		Instant seenAt = service.markSeen(RECIPIENT_ID);

		verify(queryRepository).list(RECIPIENT_ID, null, 20, NOW);
		verify(seenStateRepository).advance(RECIPIENT_ID, NOW);
		assertThat(seenAt).isEqualTo(NOW);
	}

	@Test
	@DisplayName("UNIT-010 이미 READ인 알림은 markRead를 두 번 호출해도 상태를 바꾸지 않는다")
	void markReadIsIdempotentForAlreadyReadNotification() {
		stubEligibleAccount();
		Notification alreadyRead = notification(NotificationStatus.READ, NOW.minusSeconds(60));
		when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(alreadyRead));
		NotificationCard card = card();
		when(queryRepository.findCard(RECIPIENT_ID, NOTIFICATION_ID, NOW)).thenReturn(Optional.of(card));

		NotificationCard result = service.markRead(RECIPIENT_ID, NOTIFICATION_ID);

		assertThat(result).isSameAs(card);
		verify(notificationRepository, never()).update(any());
	}

	@Test
	@DisplayName("UNIT-011 REVOKED 알림을 읽음 처리하면 NOT-DOM-003을 던지고 상태를 바꾸지 않는다")
	void markReadRejectsRevokedNotification() {
		stubEligibleAccount();
		Notification revoked = notification(NotificationStatus.REVOKED, null);
		when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(revoked));

		assertThatThrownBy(() -> service.markRead(RECIPIENT_ID, NOTIFICATION_ID))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_NOTIFICATION_STATUS);
		verify(notificationRepository, never()).update(any());
	}

	@Test
	@DisplayName("UNIT-012 존재하지 않는 알림과 남의 알림은 markRead·target 모두 NOT-DOM-004다")
	void treatsMissingAndUnownedNotificationsIdentically() {
		stubEligibleAccount();
		when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.empty());
		when(queryRepository.findTargetDecision(RECIPIENT_ID, NOTIFICATION_ID, NOW)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.markRead(RECIPIENT_ID, NOTIFICATION_ID))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.NOTIFICATION_NOT_FOUND);
		assertThatThrownBy(() -> service.target(RECIPIENT_ID, NOTIFICATION_ID))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.NOTIFICATION_NOT_FOUND);

		Notification unowned = notification(NotificationStatus.UNREAD, null);
		when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(
			new Notification(unowned.id(), 999L, unowned.outboxEventId(), unowned.notificationType(),
				unowned.dedupKey(), unowned.directionPostId(), unowned.answerId(), unowned.reportId(), unowned.status(),
				unowned.createdAt(), unowned.readAt())));

		assertThatThrownBy(() -> service.markRead(RECIPIENT_ID, NOTIFICATION_ID))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.NOTIFICATION_NOT_FOUND);
	}

	@Test
	@DisplayName("UNIT-013 markSeen은 repository의 upsert 결과를 그대로 반환한다")
	void markSeenReturnsRepositoryResult() {
		stubEligibleAccount();
		Instant advanced = NOW.minusSeconds(100);
		when(seenStateRepository.advance(RECIPIENT_ID, NOW)).thenReturn(advanced);

		Instant result = service.markSeen(RECIPIENT_ID);

		assertThat(result).isEqualTo(advanced);
	}

	@Test
	@DisplayName("UNIT-008 nextCursor 채움은 repository에 위임하고 service는 그대로 반환한다")
	void delegatesNextCursorFillingToRepository() {
		stubEligibleAccount();
		NotificationListing full = new NotificationListing(List.of(), new NotificationListing.Cursor(NOW, 9L));
		when(queryRepository.list(RECIPIENT_ID, null, 20, NOW)).thenReturn(full);

		assertThat(service.list(RECIPIENT_ID, null, null, 20)).isSameAs(full);
	}

	private void stubEligibleAccount() {
		Account account = Account.restore(
			RECIPIENT_ID, AccountRole.USER, AccountStatus.ACTIVE, "KR", "KR-11", "ko-KR", "Asia/Seoul", "nick", null);
		when(accountEligibilityGate.requireActiveUser(eq(RECIPIENT_ID), any(), any())).thenReturn(account);
	}

	@SuppressWarnings("unchecked")
	private void stubGateToInvoke(int supplierIndex) {
		doAnswer(invocation -> {
			Supplier<RuntimeException> supplier = invocation.getArgument(supplierIndex);
			throw supplier.get();
		}).when(accountEligibilityGate).requireActiveUser(eq(RECIPIENT_ID), any(), any());
	}

	private static Notification notification(NotificationStatus status, Instant readAt) {
		return new Notification(NOTIFICATION_ID, RECIPIENT_ID, 1L, NotificationType.DIRECTION_POST_RECEIVED,
			"gh176-unit-dedup", 771L, null, null, status, NOW.minusSeconds(120), readAt);
	}

	private static NotificationCard card() {
		return new NotificationCard(NOTIFICATION_ID, NotificationType.DIRECTION_POST_RECEIVED,
			NOW.minusSeconds(120), NOW, false, NotificationTargetKind.DIRECTION_POST, 771L,
			NotificationTargetState.AVAILABLE, NOW.plusSeconds(3600));
	}
}
