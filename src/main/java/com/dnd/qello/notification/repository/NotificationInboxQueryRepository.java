package com.dnd.qello.notification.repository;

import java.time.Instant;
import java.util.Optional;

import com.dnd.qello.notification.view.NotificationCard;
import com.dnd.qello.notification.view.NotificationListing;
import com.dnd.qello.notification.view.NotificationTargetDecision;

public interface NotificationInboxQueryRepository {

	/**
	 * recipientId 소유의 UNREAD·READ 줄만 최신순으로 반환한다. cursor가 null이면
	 * 첫 페이지다. 반환 건수가 limit과 같을 때만 nextCursor를 채우고, 그보다 적으면
	 * 마지막 페이지이므로 null로 둔다.
	 */
	NotificationListing list(long recipientId, NotificationListing.Cursor cursor, int limit, Instant at);

	/**
	 * 알림 한 줄을 대상 상태와 함께 다시 읽는다. 상태 필터를 걸지 않으므로 REVOKED나
	 * DISMISSED 줄도(예: 읽음 처리 응답을 만들기 위해) 조회할 수 있다. recipientId
	 * 소유가 아니거나 존재하지 않으면 빈 값이다.
	 */
	Optional<NotificationCard> findCard(long recipientId, long notificationId, Instant at);

	/**
	 * 알림 하나의 진입 판정을 재평가한다. recipientId 소유가 아니거나 존재하지
	 * 않으면 빈 값이다 — 두 경우를 구분하지 않아 존재 여부를 노출하지 않는다.
	 */
	Optional<NotificationTargetDecision> findTargetDecision(long recipientId, long notificationId, Instant at);

	/** recipientId의 UNREAD 줄 개수. REVOKED·DISMISSED는 세지 않는다. */
	long countUnread(long recipientId);

	/** seenAt 이후 생성된 UNREAD 줄이 있는지. seenAt이 null이면 UNREAD 존재 자체로 판정한다. */
	boolean existsUnseen(long recipientId, Instant seenAt);
}
