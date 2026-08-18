package com.dnd.qello.notification.slack;

// 제한된 관리자 채널로 Slack 메시지를 보내는 port다(#111). 실제 Incoming
// Webhook/Bot API 구현체는 이 이슈 범위 밖이다(선택 미결정). #113도 secret
// 저장·rotation을 제외했으므로 구현하지 않았다 — 후속
// production gate에서 실제 webhook/secret과 함께 배선한다.
@FunctionalInterface
public interface SlackNotifier {

	/**
	 * @throws SlackDeliveryException 전송에 실패했을 때. {@link SlackDeliveryException#retryable()}로
	 *     재시도 가능 여부를 표현한다.
	 */
	void send(SlackNotification notification);
}
