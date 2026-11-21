/**
 * Created at: 2026-11-21T08:45:04Z
 * Source scenario: TEST-PLAN-GH-365-PUSH-FID-PROVIDER-UNIT-001 through UNIT-002
 */
package com.dnd.qello.notification.web.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.push.security.PushToken;
import com.dnd.qello.notification.service.PushDeviceCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class PushDeviceRequestTest {

	private static final String FID = "test-only-fid-sentinel";

	@ParameterizedTest
	@EnumSource(PushPlatform.class)
	@DisplayName("UNIT-001: 각 플랫폼의 FID는 기존 PushToken command에 손실 없이 전달된다")
	void convertsFidToExistingTokenCommand(PushPlatform platform) {
		PushDeviceRequest request = new PushDeviceRequest(platform.name(), FID);

		PushDeviceCommand command = request.toCommand();

		assertThat(command.platform()).isEqualTo(platform);
		assertThat(command.token()).isEqualTo(PushToken.of(FID));
	}

	@Test
	@DisplayName("UNIT-002: 요청 문자열은 FID 원문을 노출하지 않는다")
	void redactsFidFromStringRepresentation() {
		PushDeviceRequest request = new PushDeviceRequest("ANDROID", FID);

		String rendered = request.toString();

		assertThat(rendered).doesNotContain(FID).contains("[REDACTED]");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t", "\n"})
	@DisplayName("UNIT-002: null 또는 공백 FID는 기존 요청 오류 코드로 거절한다")
	void rejectsMissingOrBlankFid(String fid) {
		PushDeviceRequest request = new PushDeviceRequest("ANDROID", fid);

		assertThatExceptionOfType(NotificationException.class).isThrownBy(request::toCommand)
				.satisfies(exception -> assertThat(exception.getErrorCode())
						.isEqualTo(NotificationErrorCode.INVALID_PUSH_DEVICE_REQUEST));
	}

	@ParameterizedTest
	@ValueSource(strings = {"a", "가"})
	@DisplayName("UNIT-002: UTF-8 4096 bytes를 넘는 FID는 원문 없는 기존 요청 오류로 거절한다")
	void rejectsFidExceedingUtf8ByteLimit(String character) {
		String fid = character.repeat(character.equals("a") ? 4097 : 1366);
		PushDeviceRequest request = new PushDeviceRequest("ANDROID", fid);

		assertThatExceptionOfType(NotificationException.class).isThrownBy(request::toCommand)
				.satisfies(exception -> {
					assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.INVALID_PUSH_DEVICE_REQUEST);
					assertThat(exception.toString()).doesNotContain(fid);
					assertThat(exception.getReason()).doesNotContain(fid);
				});
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "android", "WINDOWS"})
	@DisplayName("UNIT-002: 잘못된 플랫폼은 FID를 노출하지 않고 기존 요청 오류 코드로 거절한다")
	void rejectsInvalidPlatform(String platform) {
		PushDeviceRequest request = new PushDeviceRequest(platform, FID);

		assertThatExceptionOfType(NotificationException.class).isThrownBy(request::toCommand)
				.satisfies(exception -> {
					assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.INVALID_PUSH_DEVICE_REQUEST);
					assertThat(exception.toString()).doesNotContain(FID);
					assertThat(exception.getReason()).doesNotContain(FID);
				});
	}
}
