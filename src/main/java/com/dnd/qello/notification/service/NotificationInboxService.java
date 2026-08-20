package com.dnd.qello.notification.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.service.AccountEligibilityGate;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.repository.NotificationInboxQueryRepository;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.NotificationSeenStateRepository;
import com.dnd.qello.notification.view.NotificationCard;
import com.dnd.qello.notification.view.NotificationListing;
import com.dnd.qello.notification.view.NotificationTargetDecision;
import com.dnd.qello.notification.view.UnreadSignal;

import lombok.RequiredArgsConstructor;

/**
 * 알림함 읽기·상호작용 5개 경로의 application 경계다. 계정 자격 게이트는
 * {@link AccountEligibilityGate}를 직접 호출한다 — feed와 달리 notification은
 * 자기 오류 코드({@code NOT-APP-001}·{@code NOT-APP-002})로 번역할 어댑터가
 * 하나만 필요하므로 별도 package-private 래퍼를 두지 않는다.
 */
@Service
@RequiredArgsConstructor
public class NotificationInboxService {

	/** 기본값 20은 컨트롤러의 {@code @RequestParam(defaultValue)}가 채운다. */
	private static final int MAX_LIMIT = 50;

	private final AccountEligibilityGate accountEligibilityGate;
	private final NotificationInboxQueryRepository queryRepository;
	private final NotificationSeenStateRepository seenStateRepository;
	private final NotificationRepository notificationRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public NotificationListing list(long recipientId, Instant cursorCreatedAt, Long cursorNotificationId, int limit) {
		requireEligibleAccount(recipientId);
		int normalizedLimit = requireValidLimit(limit);
		NotificationListing.Cursor cursor = cursor(cursorCreatedAt, cursorNotificationId);
		return queryRepository.list(recipientId, cursor, normalizedLimit, clock.instant());
	}

	@Transactional(readOnly = true)
	public UnreadSignal unreadSignal(long recipientId) {
		requireEligibleAccount(recipientId);
		Instant seenAt = seenStateRepository.findSeenAt(recipientId).orElse(null);
		boolean hasUnseen = queryRepository.existsUnseen(recipientId, seenAt);
		long unreadCount = queryRepository.countUnread(recipientId);
		return new UnreadSignal(hasUnseen, unreadCount, seenAt);
	}

	/** 열람 기준선은 항상 서버 시각으로만 전진한다 — 클라이언트가 임의의 과거·미래 값을 밀어 넣을 수 없다. */
	@Transactional
	public Instant markSeen(long recipientId) {
		requireEligibleAccount(recipientId);
		return seenStateRepository.advance(recipientId, clock.instant());
	}

	/**
	 * 멱등이다. 이미 READ면 상태를 바꾸지 않고 현재 값을 반환한다 — 반복 호출이
	 * read_at을 흔들지 않는다. REVOKED 줄은 {@link Notification#markRead}가
	 * {@code NOT-DOM-003}으로 거부한다.
	 */
	@Transactional
	public NotificationCard markRead(long recipientId, long notificationId) {
		requireEligibleAccount(recipientId);
		Instant at = clock.instant();
		Notification notification = requireOwnedNotification(recipientId, notificationId);
		if (notification.status() != NotificationStatus.READ) {
			notificationRepository.update(notification.markRead(at));
		}
		return queryRepository.findCard(recipientId, notificationId, at)
			.orElseThrow(() -> new IllegalStateException("markRead 직후 알림을 다시 읽지 못했습니다"));
	}

	/** 알림함을 열고 수분 뒤 톱했을 수 있으므로 목록의 판정을 그대로 믿지 않고 재평가한다. */
	@Transactional(readOnly = true)
	public NotificationTargetDecision target(long recipientId, long notificationId) {
		requireEligibleAccount(recipientId);
		return queryRepository.findTargetDecision(recipientId, notificationId, clock.instant())
			.orElseThrow(() -> new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
	}

	private void requireEligibleAccount(long accountId) {
		accountEligibilityGate.requireActiveUser(
			accountId,
			() -> new NotificationException(NotificationErrorCode.ACCOUNT_NOT_FOUND),
			() -> new NotificationException(NotificationErrorCode.ACCOUNT_NOT_ELIGIBLE));
	}

	/** 존재하지 않는 알림과 남의 알림을 구분하지 않는다 — 둘 다 NOT-DOM-004다. */
	private Notification requireOwnedNotification(long recipientId, long notificationId) {
		return notificationRepository.findById(notificationId)
			.filter(notification -> notification.recipientId() == recipientId)
			.orElseThrow(() -> new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
	}

	private int requireValidLimit(int limit) {
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_LIMIT, "limit", "limit은 1 이상 " + MAX_LIMIT + " 이하여야 합니다");
		}
		return limit;
	}

	private NotificationListing.Cursor cursor(Instant cursorCreatedAt, Long cursorNotificationId) {
		if ((cursorCreatedAt == null) != (cursorNotificationId == null)) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_CURSOR, "cursor", "cursor 파라미터는 함께 지정하거나 함께 생략해야 합니다");
		}
		return cursorCreatedAt == null ? null : new NotificationListing.Cursor(cursorCreatedAt, cursorNotificationId);
	}
}
