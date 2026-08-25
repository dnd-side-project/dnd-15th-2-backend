package com.dnd.qello.notification.push.policy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.dnd.qello.notification.config.PushPolicyProperties;
import com.dnd.qello.notification.domain.NotificationType;

public final class PushBudgetPolicy {

	private final PushPolicyProperties properties;

	public PushBudgetPolicy(PushPolicyProperties properties) {
		if (properties == null) {
			throw new IllegalArgumentException("push policy properties는 필수입니다");
		}
		this.properties = properties;
	}

	public Decision decide(BudgetSnapshot snapshot, NotificationType type) {
		if (snapshot == null || type == null) {
			throw new IllegalArgumentException("budget 정책 입력값은 필수입니다");
		}
		if (type == NotificationType.DIRECTION_POST_RECEIVED) {
			return snapshot.consumedTotal() < properties.dailyLimit() ? Decision.ALLOW_PRIORITY : Decision.DENY;
		}
		int generalLimit = properties.dailyLimit() - properties.directionReserved();
		return snapshot.consumedGeneral() < generalLimit && snapshot.consumedTotal() < properties.dailyLimit()
			? Decision.ALLOW_GENERAL
			: Decision.DENY;
	}

	public LocalDate budgetDate(Instant at, ZoneId accountZone) {
		if (at == null || accountZone == null) {
			throw new IllegalArgumentException("budget date 입력값은 필수입니다");
		}
		return at.atZone(accountZone).toLocalDate();
	}

	public enum Decision {
		ALLOW_PRIORITY,
		ALLOW_GENERAL,
		DENY
	}

	public record BudgetSnapshot(int consumedTotal, int consumedGeneral) {

		public BudgetSnapshot {
			if (consumedTotal < 0 || consumedGeneral < 0 || consumedGeneral > consumedTotal) {
				throw new IllegalArgumentException("budget snapshot count가 올바르지 않습니다");
			}
		}
	}
}
