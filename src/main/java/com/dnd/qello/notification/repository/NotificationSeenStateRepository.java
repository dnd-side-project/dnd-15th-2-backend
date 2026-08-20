package com.dnd.qello.notification.repository;

import java.time.Instant;
import java.util.Optional;

public interface NotificationSeenStateRepository {

	/** 한 번도 열람한 적이 없으면 빈 값이다. */
	Optional<Instant> findSeenAt(long userId);

	/**
	 * 열람 기준선을 GREATEST로만 전진시키고 최종 값을 반환한다. 반복·역순 호출이
	 * 기준선을 과거로 되돌리지 않는다.
	 */
	Instant advance(long userId, Instant at);
}
