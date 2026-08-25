package com.dnd.qello.notification.push.policy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRules;
import java.util.Comparator;
import java.util.List;

import com.dnd.qello.notification.config.PushPolicyProperties;
import com.dnd.qello.notification.domain.NotificationPreferenceSnapshot;
import com.dnd.qello.notification.domain.NotificationQuietHours;
import com.dnd.qello.notification.domain.NotificationType;

public final class PushSuppressionPolicy {

	private final PushPolicyProperties properties;
	@SuppressWarnings("unused")
	private final Clock clock;

	public PushSuppressionPolicy(PushPolicyProperties properties, Clock clock) {
		if (properties == null || clock == null) {
			throw new IllegalArgumentException("push suppression 정책 의존성은 필수입니다");
		}
		this.properties = properties;
		this.clock = clock;
	}

	public Decision evaluate(NotificationPreferenceSnapshot preference, NotificationType type, Instant at,
		Instant policyExpiresAt, Instant lastRecommendationAttemptAt) {
		if (preference == null || type == null || at == null || policyExpiresAt == null) {
			throw new IllegalArgumentException("push suppression 입력값은 필수입니다");
		}
		if (!preference.pushEnabled()) {
			return Decision.cancel(Reason.GLOBAL_OFF);
		}
		if (!preference.typeEnabled().get(type)) {
			return Decision.cancel(Reason.TYPE_OFF);
		}
		if (preference.quietHours() != null) {
			Instant quietEnd = nextQuietEnd(preference.quietHours(), at);
			if (quietEnd != null) {
				return quietEnd.isAfter(policyExpiresAt)
					? Decision.cancel(Reason.MAX_DELAY_EXCEEDED)
					: Decision.defer(quietEnd);
			}
		}
		if (type == NotificationType.QUESTION_RECOMMENDED && lastRecommendationAttemptAt != null
			&& at.isBefore(lastRecommendationAttemptAt.plus(properties.recommendationMinInterval()))) {
			return Decision.cancel(Reason.RECOMMENDATION_INTERVAL);
		}
		return Decision.send();
	}

	private static Instant nextQuietEnd(NotificationQuietHours quietHours, Instant at) {
		ZoneId zoneId = quietHours.zoneId();
		ZonedDateTime localNow = at.atZone(zoneId);
		LocalTime localTime = localNow.toLocalTime();
		if (!isQuiet(quietHours, localTime)) {
			return null;
		}
		LocalDate endDate = quietEndDate(quietHours, localNow.toLocalDate(), localTime);
		return resolveFutureInstant(endDate, quietHours.end(), zoneId, at);
	}

	private static boolean isQuiet(NotificationQuietHours quietHours, LocalTime localTime) {
		if (quietHours.start().isBefore(quietHours.end())) {
			return !localTime.isBefore(quietHours.start()) && localTime.isBefore(quietHours.end());
		}
		return !localTime.isBefore(quietHours.start()) || localTime.isBefore(quietHours.end());
	}

	private static LocalDate quietEndDate(NotificationQuietHours quietHours, LocalDate date, LocalTime localTime) {
		return quietHours.start().isAfter(quietHours.end()) && !localTime.isBefore(quietHours.start())
			? date.plusDays(1)
			: date;
	}

	private static Instant resolveFutureInstant(LocalDate endDate, LocalTime end, ZoneId zoneId, Instant at) {
		ZoneRules rules = zoneId.getRules();
		for (int dayOffset = 0; dayOffset < 3; dayOffset++) {
			LocalDateTime localEnd = LocalDateTime.of(endDate.plusDays(dayOffset), end);
			List<ZoneOffset> offsets = rules.getValidOffsets(localEnd);
			List<Instant> futureInstants = offsets.isEmpty()
				? List.of(localEnd.atZone(zoneId).toInstant())
				: offsets.stream().map(offset -> ZonedDateTime.ofLocal(localEnd, zoneId, offset).toInstant()).toList();
			Instant future = futureInstants.stream().filter(candidate -> candidate.isAfter(at))
				.min(Comparator.naturalOrder()).orElse(null);
			if (future != null) {
				return future;
			}
		}
		throw new IllegalStateException("다음 quiet 종료 시각을 계산할 수 없습니다");
	}

	public enum Action {
		SEND,
		DEFER,
		CANCEL
	}

	public enum Reason {
		ELIGIBLE,
		GLOBAL_OFF,
		TYPE_OFF,
		QUIET_HOURS,
		MAX_DELAY_EXCEEDED,
		RECOMMENDATION_INTERVAL
	}

	public record Decision(Action action, Reason reason, Instant nextAttemptAt) {

		private static Decision send() {
			return new Decision(Action.SEND, Reason.ELIGIBLE, null);
		}

		private static Decision defer(Instant nextAttemptAt) {
			return new Decision(Action.DEFER, Reason.QUIET_HOURS, nextAttemptAt);
		}

		private static Decision cancel(Reason reason) {
			return new Decision(Action.CANCEL, reason, null);
		}
	}
}
