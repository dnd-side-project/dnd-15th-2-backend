package com.dnd.qello.notification.web.response;

import com.dnd.qello.notification.view.NotificationTargetKind;
import com.dnd.qello.notification.view.NotificationTargetState;

/**
 * 알림이 가리키는 대상의 요약이다. 목록 카드와 진입 판정 응답이 함께 쓴다 —
 * kind가 NONE이면 id·state는 null이다.
 */
public record NotificationTargetSummaryResponse(String kind, Long id, String state) {

	static NotificationTargetSummaryResponse of(
		NotificationTargetKind kind, Long id, NotificationTargetState state) {
		return new NotificationTargetSummaryResponse(kind.name(), id, state == null ? null : state.name());
	}
}
