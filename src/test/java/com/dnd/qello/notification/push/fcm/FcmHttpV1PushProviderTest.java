/**
 * Created at: 2026-08-24T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-013 through UNIT-014
 */
package com.dnd.qello.notification.push.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FcmHttpV1PushProviderTest {

	private static final String PROVIDER = "com.dnd.qello.notification.push.fcm.FcmHttpV1PushProvider";
	private static final String COMMAND = "com.dnd.qello.notification.push.PushSendCommand";
	private static final String PAYLOAD = "com.dnd.qello.notification.push.PushPayload";
	private static final String RESULT = "com.dnd.qello.notification.push.PushProviderResult";

	@Test
	@DisplayName("UNIT-013: PushSendCommand는 decrypted token과 allowlisted payload만 받는다")
	void exposesOnlyTokenAndPayloadInSendCommand() {
		Class<?> commandType = requiredClass(COMMAND);

		assertThat(commandType.isRecord()).isTrue();
		assertThat(recordComponentNames(commandType)).containsExactly("token", "payload");
		assertThat(commandType.getRecordComponents()[0].getType().getSimpleName()).isEqualTo("PushToken");
		assertThat(commandType.getRecordComponents()[1].getType().getSimpleName()).isEqualTo("PushPayload");
		assertThat(commandType.getRecordComponents()[1].getType().getName()).isEqualTo(PAYLOAD);
	}

	@Test
	@DisplayName("UNIT-013: FCM provider는 PushSendCommand를 받아 PushProviderResult를 돌려준다")
	void declaresSendBoundaryAgainstPushSendCommand() {
		Class<?> providerType = requiredClass(PROVIDER);
		Class<?> commandType = requiredClass(COMMAND);
		Class<?> resultType = requiredClass(RESULT);

		assertThat(Arrays.stream(providerType.getMethods())
			.anyMatch(method -> method.getName().equals("send")
				&& method.getParameterCount() == 1
				&& method.getParameterTypes()[0].equals(commandType)
				&& method.getReturnType().equals(resultType)))
			.isTrue();
	}

	@Test
	@DisplayName("UNIT-013: provider 결과 hierarchy는 Accepted, InvalidToken, RetryableFailure, PermanentFailure를 노출한다")
	void exposesClosedProviderResultHierarchy() {
		Class<?> resultType = requiredClass(RESULT);

		assertThat(Arrays.stream(resultType.getDeclaredClasses()).map(Class::getSimpleName))
			.containsExactlyInAnyOrder("Accepted", "InvalidToken", "RetryableFailure", "PermanentFailure");
	}

	private static Class<?> requiredClass(String className) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new AssertionError("Missing production API: " + className, e);
		}
	}

	private static List<String> recordComponentNames(Class<?> type) {
		return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
	}
}
