package com.dnd.qello.notification.push.policy;

import java.time.Instant;

import com.dnd.qello.notification.config.PushPolicyProperties;
import com.dnd.qello.notification.domain.NotificationType;

public final class PushGroupingPolicy {

	private final PushPolicyProperties properties;

	public PushGroupingPolicy(PushPolicyProperties properties) {
		if (properties == null) {
			throw new IllegalArgumentException("push policy properties는 필수입니다");
		}
		this.properties = properties;
	}

	public GroupingDecision decide(NotificationType type, long recipientId, long notificationId,
		Long recommendationCycleId, Instant createdAt) {
		requireInputs(type, recipientId, notificationId, createdAt);
		if (type == NotificationType.ANSWER_RECEIVED || type == NotificationType.ANSWER_REACTED) {
			return new GroupingDecision(GroupingMode.WINDOWED,
				"push-window:" + recipientId + ":" + type + ":" + createdAt.toEpochMilli(), createdAt,
				createdAt.plus(properties.bundleWindow()), createdAt.plus(properties.maxDelay()));
		}
		if (type == NotificationType.QUESTION_RECOMMENDED) {
			if (recommendationCycleId == null || recommendationCycleId <= 0) {
				throw new IllegalArgumentException("recommendation cycle ID는 필수입니다");
			}
			return new GroupingDecision(GroupingMode.RECOMMENDATION_CYCLE,
				"push-recommendation-cycle:" + recipientId + ":" + recommendationCycleId, createdAt, createdAt,
				createdAt.plus(properties.maxDelay()));
		}
		return new GroupingDecision(GroupingMode.SINGLETON, "push-notification:" + notificationId, createdAt,
			createdAt, createdAt.plus(properties.maxDelay()));
	}

	public boolean joins(OpenGroup openGroup, NotificationType type, long recipientId, Instant notificationCreatedAt) {
		return openGroup != null && type != null && notificationCreatedAt != null
			&& openGroup.recipientId() == recipientId && openGroup.notificationType() == type
			&& !notificationCreatedAt.isAfter(openGroup.collectUntil());
	}

	private static void requireInputs(NotificationType type, long recipientId, long notificationId, Instant createdAt) {
		if (type == null || recipientId <= 0 || notificationId <= 0 || createdAt == null) {
			throw new IllegalArgumentException("grouping 입력값이 올바르지 않습니다");
		}
	}

	public enum GroupingMode {
		WINDOWED,
		RECOMMENDATION_CYCLE,
		SINGLETON
	}

	public record GroupingDecision(GroupingMode mode, String aggregationKey, Instant windowStartedAt,
		Instant collectUntil, Instant policyExpiresAt) {
	}

	public record OpenGroup(long recipientId, NotificationType notificationType, Instant collectUntil) {

		public OpenGroup {
			if (recipientId <= 0 || notificationType == null || collectUntil == null) {
				throw new IllegalArgumentException("open group 값이 올바르지 않습니다");
			}
		}
	}
}
