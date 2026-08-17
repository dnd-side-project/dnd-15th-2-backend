package com.dnd.qello.notification.slack;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

// SlackNotifier에 전달되는 유일한 데이터다(#111). 필드가 caseId·adminLinkPath
// 두 개뿐이라 답변 원문·user ID·닉네임·이메일·신고 세부 같은 비허용 필드는
// 애초에 표현할 방법이 없다(INV-SLK-003, INV-SLK-004).
public record SlackNotification(long caseId, String adminLinkPath) {

	public SlackNotification {
		if (caseId <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_ID, "caseId", "caseId가 유효하지 않습니다");
		}
		if (adminLinkPath == null || adminLinkPath.isBlank()) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_TEXT, "adminLinkPath", "adminLinkPath가 유효하지 않습니다");
		}
	}
}
