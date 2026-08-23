package com.dnd.qello.notification.web.response;

import com.dnd.qello.notification.view.NotificationTargetKind;
import com.dnd.qello.notification.view.NotificationTargetState;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 알림이 가리키는 대상의 요약이다. 목록 카드와 진입 판정 응답이 함께 쓴다 —
 * kind가 NONE이면 id·state는 null이다.
 */
public record NotificationTargetSummaryResponse(
	@Schema(description = "알림이 가리키는 대상의 종류. 가리키는 대상이 없으면 NONE입니다",
		allowableValues = {"DIRECTION_POST", "ANSWER", "NONE"})
	String kind,
	@Schema(description = "대상 식별자. kind가 NONE이면 null입니다") Long id,
	@Schema(description = "대상의 현재 상태입니다. AVAILABLE이면 지금 볼 수 있고, EXPIRED는 기간이 끝난 질문글, "
		+ "HIDDEN은 숨겨진 글, BLOCKED는 차단한 상대의 글, GONE은 지워진 글입니다. kind가 NONE이면 null입니다",
		allowableValues = {"GONE", "BLOCKED", "HIDDEN", "EXPIRED", "AVAILABLE"})
	String state
) {

	static NotificationTargetSummaryResponse of(
		NotificationTargetKind kind, Long id, NotificationTargetState state) {
		return new NotificationTargetSummaryResponse(kind.name(), id, state == null ? null : state.name());
	}
}
