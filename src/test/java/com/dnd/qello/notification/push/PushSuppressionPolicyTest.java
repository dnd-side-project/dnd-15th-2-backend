/**
 * Created at: 2026-08-25T13:14:21+09:00
 * Source scenario: TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-006, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-007, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-008, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-009, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-013
 */
package com.dnd.qello.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.domain.NotificationPreferenceSnapshot;
import com.dnd.qello.notification.domain.NotificationQuietHours;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.push.policy.PushSuppressionPolicy;
import com.dnd.qello.notification.push.policy.PushSuppressionPolicy.Action;
import com.dnd.qello.notification.push.policy.PushSuppressionPolicy.Reason;

class PushSuppressionPolicyTest {

	private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
	private final PushSuppressionPolicy policy = new PushSuppressionPolicy(
		PushPolicyPropertiesTest.validProperties(), Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	@DisplayName("UNIT-006: global OFF는 type OFF와 quiet보다 먼저 취소하고 quiet snapshot을 보존한다")
	void globalOffOutranksTypeAndQuietWithoutMutatingQuietHours() {
		NotificationQuietHours quietHours = quiet(LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("Asia/Seoul"));
		NotificationPreferenceSnapshot preference = preference(false, NotificationType.ANSWER_RECEIVED, false, quietHours);

		var decision = policy.evaluate(preference, NotificationType.ANSWER_RECEIVED, NOW, NOW.plusSeconds(3600), null);

		assertThat(decision.action()).isEqualTo(Action.CANCEL);
		assertThat(decision.reason()).isEqualTo(Reason.GLOBAL_OFF);
		assertThat(preference.quietHours()).isSameAs(quietHours);
	}

	@Test
	@DisplayName("UNIT-007: quiet가 없으면 즉시 전송하고 overnight quiet 안이면 다음 종료까지 지연한다")
	void sendsOutsideQuietAndDefersUntilOvernightQuietEnd() {
		assertThat(policy.evaluate(preference(true, NotificationType.ANSWER_RECEIVED, true, null),
			NotificationType.ANSWER_RECEIVED, NOW, NOW.plusSeconds(3600), null).action()).isEqualTo(Action.SEND);

		ZoneId seoul = ZoneId.of("Asia/Seoul");
		Instant duringQuiet = LocalDateTime.of(2026, 8, 25, 23, 0).atZone(seoul).toInstant();
		Instant expectedEnd = LocalDateTime.of(2026, 8, 26, 7, 0).atZone(seoul).toInstant();
		var decision = policy.evaluate(preference(true, NotificationType.ANSWER_RECEIVED, true,
			quiet(LocalTime.of(22, 0), LocalTime.of(7, 0), seoul)), NotificationType.ANSWER_RECEIVED,
			duringQuiet, expectedEnd.plusSeconds(1), null);

		assertThat(decision.action()).isEqualTo(Action.DEFER);
		assertThat(decision.reason()).isEqualTo(Reason.QUIET_HOURS);
		assertThat(decision.nextAttemptAt()).isEqualTo(expectedEnd);
	}

	@Test
	@DisplayName("UNIT-008: DST gap과 overlap의 quiet 종료는 항상 앞으로 진행하는 실제 Instant다")
	void resolvesDstQuietEndToFutureInstant() {
		ZoneId newYork = ZoneId.of("America/New_York");
		Instant springDuringQuiet = LocalDateTime.of(2026, 3, 8, 1, 30).atZone(newYork).toInstant();
		var spring = policy.evaluate(preference(true, NotificationType.ANSWER_RECEIVED, true,
			quiet(LocalTime.of(1, 0), LocalTime.of(2, 30), newYork)), NotificationType.ANSWER_RECEIVED,
			springDuringQuiet, springDuringQuiet.plusSeconds(20_000), null);

		ZonedDateTime laterOverlap = ZonedDateTime.ofLocal(LocalDateTime.of(2026, 11, 1, 1, 0), newYork,
			newYork.getRules().getValidOffsets(LocalDateTime.of(2026, 11, 1, 1, 0)).getLast());
		Instant fallDuringQuiet = laterOverlap.toInstant();
		var fall = policy.evaluate(preference(true, NotificationType.ANSWER_RECEIVED, true,
			quiet(LocalTime.of(0, 30), LocalTime.of(1, 30), newYork)), NotificationType.ANSWER_RECEIVED,
			fallDuringQuiet, fallDuringQuiet.plusSeconds(20_000), null);

		assertThat(spring.action()).isEqualTo(Action.DEFER);
		assertThat(spring.nextAttemptAt()).isAfter(springDuringQuiet);
		assertThat(fall.action()).isEqualTo(Action.DEFER);
		assertThat(fall.nextAttemptAt()).isAfter(fallDuringQuiet);
	}

	@Test
	@DisplayName("UNIT-009: quiet 종료가 max delay 정각 이하면 지연하고 이후면 취소한다")
	void cancelsWhenQuietEndExceedsPolicyExpiry() {
		ZoneId seoul = ZoneId.of("Asia/Seoul");
		Instant duringQuiet = LocalDateTime.of(2026, 8, 25, 23, 0).atZone(seoul).toInstant();
		Instant quietEnd = LocalDateTime.of(2026, 8, 26, 7, 0).atZone(seoul).toInstant();
		NotificationPreferenceSnapshot preference = preference(true, NotificationType.ANSWER_RECEIVED, true,
			quiet(LocalTime.of(22, 0), LocalTime.of(7, 0), seoul));

		assertThat(policy.evaluate(preference, NotificationType.ANSWER_RECEIVED, duringQuiet, quietEnd, null).action())
			.isEqualTo(Action.DEFER);
		var expired = policy.evaluate(preference, NotificationType.ANSWER_RECEIVED, duringQuiet,
			quietEnd.minusNanos(1), null);
		assertThat(expired.action()).isEqualTo(Action.CANCEL);
		assertThat(expired.reason()).isEqualTo(Reason.MAX_DELAY_EXCEEDED);
	}

	@Test
	@DisplayName("UNIT-013: 추천 최소 간격은 실제 최근 시도에만 적용하고 정각부터 허용한다")
	void appliesRecommendationIntervalOnlyBeforeBoundary() {
		Instant lastAttempt = NOW.minusSeconds(86_400);
		NotificationPreferenceSnapshot preference = preference(true, NotificationType.QUESTION_RECOMMENDED, true, null);

		assertThat(policy.evaluate(preference, NotificationType.QUESTION_RECOMMENDED, NOW, NOW.plusSeconds(1), lastAttempt)
			.action()).isEqualTo(Action.SEND);
		var tooSoon = policy.evaluate(preference, NotificationType.QUESTION_RECOMMENDED, NOW.minusNanos(1),
			NOW.plusSeconds(1), lastAttempt);
		assertThat(tooSoon.action()).isEqualTo(Action.CANCEL);
		assertThat(tooSoon.reason()).isEqualTo(Reason.RECOMMENDATION_INTERVAL);
		assertThat(policy.evaluate(preference, NotificationType.QUESTION_RECOMMENDED, NOW, NOW.plusSeconds(1), null)
			.action()).isEqualTo(Action.SEND);
	}

	private static NotificationPreferenceSnapshot preference(boolean pushEnabled, NotificationType disabledType,
		boolean disabledTypeEnabled, NotificationQuietHours quietHours) {
		EnumMap<NotificationType, Boolean> typeEnabled = new EnumMap<>(NotificationType.class);
		for (NotificationType type : NotificationType.values()) {
			typeEnabled.put(type, true);
		}
		typeEnabled.put(disabledType, disabledTypeEnabled);
		return new NotificationPreferenceSnapshot(1L, pushEnabled, quietHours, typeEnabled);
	}

	private static NotificationQuietHours quiet(LocalTime start, LocalTime end, ZoneId zoneId) {
		return new NotificationQuietHours(start, end, zoneId);
	}
}
