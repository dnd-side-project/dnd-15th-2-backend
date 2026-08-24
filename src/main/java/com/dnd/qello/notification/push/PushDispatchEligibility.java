package com.dnd.qello.notification.push;

import org.springframework.stereotype.Service;

import com.dnd.qello.notification.domain.DeliveryStatus;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.PushDeviceStatus;

/**
 * provider 호출 전 최신 snapshot을 제한된 decision으로 바꾼다.
 * 정책 억제는 CANCELLED, snapshot 무결성 위반은 DEAD로 구분한다.
 */
@Service
public class PushDispatchEligibility {

	public Evaluation evaluate(PushDispatchContext context) {
		if (context == null) {
			return new Evaluation(PushDispatchDecision.DEAD, ReasonCode.INVALID_SNAPSHOT);
		}

		if (context.delivery().status() != DeliveryStatus.PROCESSING
			|| context.delivery().id() == null
			|| context.notification().id() == null
			|| context.device().id() == null
			|| context.delivery().notificationId() != context.notification().id()
			|| context.delivery().pushDeviceId() != context.device().id()) {
			return new Evaluation(PushDispatchDecision.DEAD, ReasonCode.DELIVERY_SNAPSHOT_MISMATCH);
		}

		if (context.device().userId() != context.notification().recipientId()) {
			return new Evaluation(PushDispatchDecision.DEAD, ReasonCode.OWNER_MISMATCH);
		}

		if (context.device().status() != PushDeviceStatus.ACTIVE) {
			return new Evaluation(PushDispatchDecision.CANCELLED, ReasonCode.DEVICE_INACTIVE);
		}
		if (context.notification().status() == NotificationStatus.REVOKED) {
			return new Evaluation(PushDispatchDecision.CANCELLED, ReasonCode.NOTIFICATION_REVOKED);
		}

		PushDispatchContext.DispatchValiditySnapshot validity = context.targetValidity();
		if (!validity.recipientActive()) {
			return new Evaluation(PushDispatchDecision.CANCELLED, ReasonCode.RECIPIENT_INACTIVE);
		}
		if (context.actorId() != null && !validity.actorActive()) {
			return new Evaluation(PushDispatchDecision.CANCELLED, ReasonCode.ACTOR_INACTIVE);
		}
		if (!validity.preferenceEnabled()) {
			return new Evaluation(PushDispatchDecision.CANCELLED, ReasonCode.PREFERENCE_DISABLED);
		}
		if (validity.blockedInEitherDirection()) {
			return new Evaluation(PushDispatchDecision.CANCELLED, ReasonCode.BLOCKED_ACTOR);
		}
		if (!validity.targetValid()) {
			return new Evaluation(PushDispatchDecision.CANCELLED, ReasonCode.TARGET_INVALID);
		}

		return new Evaluation(PushDispatchDecision.SEND, ReasonCode.ELIGIBLE);
	}

	/** 이유 코드가 필요하면 evaluate를 사용한다. */
	public PushDispatchDecision decide(PushDispatchContext context) {
		return evaluate(context).decision();
	}

	public record Evaluation(PushDispatchDecision decision, ReasonCode reasonCode) {
		public Evaluation {
			if (decision == null || reasonCode == null) {
				throw new IllegalArgumentException("dispatch evaluation은 decision과 reasonCode가 필요합니다");
			}
		}
	}

	/** 외부 식별자 없이 운영·테스트에서 안전하게 사용할 수 있는 이유 코드. */
	public enum ReasonCode {
		ELIGIBLE,
		INVALID_SNAPSHOT,
		DELIVERY_SNAPSHOT_MISMATCH,
		OWNER_MISMATCH,
		DEVICE_INACTIVE,
		NOTIFICATION_REVOKED,
		RECIPIENT_INACTIVE,
		ACTOR_INACTIVE,
		PREFERENCE_DISABLED,
		BLOCKED_ACTOR,
		TARGET_INVALID
	}
}
